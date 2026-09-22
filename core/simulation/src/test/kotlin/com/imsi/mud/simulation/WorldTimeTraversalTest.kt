package com.imsi.mud.simulation

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WorldTimeTraversalTest {
    private val traversal = WorldTimeTraversal(boundaryEvaluator { snapshot, candidate ->
        if (candidate.candidateKind == "scheduled.action.complete.v1") {
            snapshot.copy(completedActionIds = snapshot.completedActionIds + entity(candidate.key.stableEntityId))
        } else {
            snapshot
        }
    })

    @Test
    fun `interrupt policy precedence keeps gate critical cancel forced safety and ignore deterministic`() {
        fun policyCandidate(
            disposition: BoundaryDisposition = BoundaryDisposition.CONTINUE,
            eventType: String = "ordinary",
            importance: EventImportance? = null,
            safety: Boolean = false
        ) = candidate("policy-$eventType", 1, BoundaryCategory.WORLD_EVENT, disposition = disposition).copy(
            eventType = eventType,
            importance = importance,
            publicSafetyStop = safety
        )
        val ignore = TimeAdvanceInterruptPolicy(explicitIgnoreEventTypes = setOf("ordinary", "critical", "forced"), summaryOnly = true)
        val critical = ignore.copy(importanceThreshold = EventImportance.CRITICAL)

        assertEquals(BoundaryDisposition.TIME_ADVANCE_STOP, TimeAdvanceInterruptPolicyEvaluator.disposition(policyCandidate(eventType = "critical", importance = EventImportance.CRITICAL), critical))
        assertEquals(TimeAdvanceResult.DECISION_REQUIRED, TimeAdvanceInterruptPolicyEvaluator.terminal(policyCandidate(BoundaryDisposition.DECISION_GATE, "critical"), critical, AdvanceControl.CANCEL_ADVANCE))
        assertEquals(TimeAdvanceResult.CANCELLED, TimeAdvanceInterruptPolicyEvaluator.terminal(policyCandidate(eventType = "ordinary"), ignore, AdvanceControl.CANCEL_ADVANCE))
        assertEquals(BoundaryDisposition.TIME_ADVANCE_STOP, TimeAdvanceInterruptPolicyEvaluator.disposition(policyCandidate(eventType = "forced"), ignore.copy(forcedStopEventTypes = setOf("forced"))))
        assertEquals(BoundaryDisposition.TIME_ADVANCE_STOP, TimeAdvanceInterruptPolicyEvaluator.disposition(policyCandidate(eventType = "safety", safety = true), TimeAdvanceInterruptPolicy(summaryOnly = true)))
        assertEquals(BoundaryDisposition.CONTINUE, TimeAdvanceInterruptPolicyEvaluator.disposition(policyCandidate(eventType = "ordinary"), ignore))
    }

    @Test
    fun `runtime traversal halts an unregistered candidate codec`() {
        val result = WorldTimeTraversal(boundaryEvaluator { snapshot, _ -> snapshot }, approvedCodecs = setOf("registered.v1")).traverse(
            snapshot(0), minute(1), TimeAdvanceGoal.UntilMinute(minute(1)), TimeTraversalLimits(minute(2), 8),
            listOf(FixedSource("world", listOf(candidate("unknown", 1, BoundaryCategory.WORLD_EVENT).copy(payloadCodec = "unknown.v1"))))
        )

        assertEquals(TimeAdvanceResult.FAILED, result.result)
        assertTrue(result.error is DomainError.SystemHalted)
        assertEquals(TraversalFailure.SYSTEM_HALT, result.failure)
    }

    @Test
    fun `pending suffix hash distinguishes fields that contain separators`() {
        val left = candidate("a|b", 1, BoundaryCategory.WORLD_EVENT).copy(key = candidate("a|b", 1, BoundaryCategory.WORLD_EVENT).key.copy(stableSubKey = "c"))
        val right = candidate("a", 1, BoundaryCategory.WORLD_EVENT).copy(key = candidate("a", 1, BoundaryCategory.WORLD_EVENT).key.copy(stableSubKey = "b|c"))

        assertTrue(PendingBoundarySuffix(listOf(left)).hash != PendingBoundarySuffix(listOf(right)).hash)
    }

    @Test
    fun `combat elapsed from 1029 to 1031 folds the 1030 schedule boundary`() {
        val source = FixedSource(
            "schedule",
            listOf(candidate("treatment", 630, BoundaryCategory.SCHEDULED_ACTION_COMPLETE, kind = "scheduled.action.complete.v1"))
        )

        val result = traversal.traverse(
            snapshot(629), minute(631), TimeAdvanceGoal.UntilMinute(minute(631)), TimeTraversalLimits(minute(700), 8), listOf(source)
        )

        assertEquals(TimeAdvanceResult.COMPLETED, result.result)
        assertEquals(631, result.snapshot.clock.minute.value)
        assertTrue(entity("treatment") in result.snapshot.completedActionIds)
        assertEquals(1, result.processedBoundaryCount)
    }

    @Test
    fun `same time candidates use BoundaryOrder instead of source order`() {
        val source = FixedSource(
            "schedule",
            listOf(
                candidate("complete", 630, BoundaryCategory.SCHEDULED_ACTION_COMPLETE),
                candidate("start", 630, BoundaryCategory.SCHEDULED_ACTION_START)
            )
        )
        val seen = mutableListOf<String>()
        val orderedTraversal = WorldTimeTraversal(boundaryEvaluator { state, candidate ->
            seen += candidate.key.stableEntityId
            state
        })

        orderedTraversal.traverse(snapshot(629), minute(630), TimeAdvanceGoal.UntilMinute(minute(630)), TimeTraversalLimits(minute(700), 8), listOf(source))

        assertEquals(listOf("start", "complete"), seen)
    }

    @Test
    fun `decision gate commits its prefix and leaves a terminal continuation state`() {
        val gate = candidate("gate", 630, BoundaryCategory.COMBAT_CRISIS, disposition = BoundaryDisposition.DECISION_GATE)
        val suffix = candidate("economy", 630, BoundaryCategory.ECONOMY_SETTLEMENT)
        val result = traversal.traverse(
            snapshot(629),
            minute(631),
            TimeAdvanceGoal.UntilMinute(minute(631)),
            TimeTraversalLimits(minute(700), 8),
            listOf(FixedSource("combat", listOf(gate)), FixedSource("world", listOf(suffix)))
        )

        assertEquals(TimeAdvanceResult.DECISION_REQUIRED, result.result)
        assertEquals(630, result.snapshot.clock.minute.value)
        assertEquals(gate, result.stopCandidate)
        assertEquals(630, result.cursor!!.boundaryTime.value)
        assertEquals(listOf(suffix), result.pendingSuffix!!.candidates)
    }

    @Test
    fun `invalid same time source and byte overflow fail before a batch is applied`() {
        val cursor = BoundaryCursor(minute(630), candidate("past", 630, BoundaryCategory.WORLD_EVENT).key)
        val past = FixedSource("bad", listOf(candidate("past", 630, BoundaryCategory.WORLD_EVENT)))
        assertTrue(BoundarySourceConformanceSuite.validate(past, snapshot(630), cursor, minute(630)) is Checked.Rejected)

        val initial = snapshot(629)
        val oversized = candidate("large", 630, BoundaryCategory.WORLD_EVENT, payload = "x".repeat(65_537))
        val result = traversal.traverse(
            initial,
            minute(631),
            TimeAdvanceGoal.UntilMinute(minute(631)),
            TimeTraversalLimits(minute(700), 8),
            listOf(FixedSource("world", listOf(oversized)))
        )
        assertEquals(TimeAdvanceResult.LIMIT_REACHED, result.result)
        assertTrue(result.error is DomainError.BoundaryLimitReached)
        assertEquals(TraversalFailure.DOMAIN_FAILED, result.failure)
        assertEquals(initial, result.snapshot)
        assertEquals(null, result.cursor)
        assertTrue(result.eventDrafts.isEmpty())

        val countCapped = traversal.traverse(
            initial, minute(631), TimeAdvanceGoal.UntilMinute(minute(631)), TimeTraversalLimits(minute(700), 8, 1),
            listOf(
                FixedSource("world", listOf(
                    candidate("one", 630, BoundaryCategory.WORLD_EVENT),
                    candidate("two", 630, BoundaryCategory.ECONOMY_SETTLEMENT)
                ))
            )
        )
        assertEquals(TimeAdvanceResult.LIMIT_REACHED, countCapped.result)
        assertTrue(countCapped.error is DomainError.BoundaryLimitReached)
        assertEquals(initial, countCapped.snapshot)
        assertEquals(null, countCapped.cursor)
        assertTrue(countCapped.eventDrafts.isEmpty())
        val retry = traversal.traverse(
            countCapped.snapshot, minute(631), TimeAdvanceGoal.UntilMinute(minute(631)), TimeTraversalLimits(minute(700), 8, 2),
            listOf(FixedSource("world", listOf(
                candidate("one", 630, BoundaryCategory.WORLD_EVENT),
                candidate("two", 630, BoundaryCategory.ECONOMY_SETTLEMENT)
            )))
        )
        assertEquals(TimeAdvanceResult.COMPLETED, retry.result)
        assertEquals(631, retry.snapshot.clock.minute.value)
    }

    @Test
    fun `boundary conformance rejects unstable duplicate unknown codec and candidate count sources`() {
        val candidate = candidate("one", 630, BoundaryCategory.WORLD_EVENT)
        val unstable = object : BoundarySource {
            override val sourceId = "world"
            private var flip = false
            override fun nextTimeAfter(snapshot: WorldTraversalSnapshot, cursor: BoundaryCursor?) = minute(630)
            override fun candidatesAt(snapshot: WorldTraversalSnapshot, time: GameMinute): List<BoundaryCandidate> = if (flip.also { flip = !flip }) emptyList() else listOf(candidate)
        }
        assertTrue(BoundarySourceConformanceSuite.validate(unstable, snapshot(629), null, minute(630)) is Checked.Rejected)

        val duplicate = FixedSource("world", listOf(candidate, candidate))
        assertTrue(BoundarySourceConformanceSuite.validate(duplicate, snapshot(629), null, minute(630)) is Checked.Rejected)

        val unknownCodec = candidate.copy(payloadCodec = "unknown.v1", payloadHash = canonicalPayloadHash(candidate.canonicalPayload))
        assertTrue(BoundarySourceConformanceSuite.validate(FixedSource("world", listOf(unknownCodec)), snapshot(629), null, minute(630), setOf("registered.v1")) is Checked.Rejected)
        assertTrue(BoundarySourceConformanceSuite.validate(FixedSource("world", listOf(candidate)), snapshot(629), null, minute(630), maxCandidatesPerBatch = 0) is Checked.Rejected)
        assertTrue(BoundarySourceConformanceSuite.validate(FixedSource("world", listOf(candidate("one", 630, BoundaryCategory.WORLD_EVENT, payload = "12"), candidate("two", 630, BoundaryCategory.INFORMATION, payload = "34"))), snapshot(629), null, minute(630), maxPendingBatchBytes = 3) is Checked.Rejected)

        val currentTime = object : BoundarySource {
            override val sourceId = "world"
            override fun nextTimeAfter(snapshot: WorldTraversalSnapshot, cursor: BoundaryCursor?) = snapshot.clock.minute
            override fun candidatesAt(snapshot: WorldTraversalSnapshot, time: GameMinute) = emptyList<BoundaryCandidate>()
        }
        assertTrue(BoundarySourceConformanceSuite.validate(currentTime, snapshot(629), null, minute(630)) is Checked.Rejected)
    }

    @Test
    fun `unreachable action goal and explicit boundary limit keep a deterministic cursor`() {
        val noSource = traversal.traverse(
            snapshot(10),
            minute(12),
            TimeAdvanceGoal.UntilFirstActionCompleted(ActionSelector.ActionId(entity("never"))),
            TimeTraversalLimits(minute(20), 1),
            emptyList()
        )
        assertEquals(TimeAdvanceResult.UNREACHABLE, noSource.result)

        val twoBoundaries = FixedSource(
            "world",
            listOf(candidate("one", 11, BoundaryCategory.WORLD_EVENT), candidate("two", 12, BoundaryCategory.WORLD_EVENT))
        )
        val limited = traversal.traverse(
            snapshot(10), minute(13), TimeAdvanceGoal.UntilMinute(minute(13)), TimeTraversalLimits(minute(20), 1), listOf(twoBoundaries)
        )
        assertEquals(TimeAdvanceResult.LIMIT_REACHED, limited.result)
        assertEquals(11, limited.cursor!!.boundaryTime.value)

        val advanceCapped = traversal.traverse(
            snapshot(10), minute(30), TimeAdvanceGoal.UntilMinute(minute(30)), TimeTraversalLimits(minute(20), 8), emptyList()
        )
        assertEquals(TimeAdvanceResult.LIMIT_REACHED, advanceCapped.result)
        assertEquals(20, advanceCapped.snapshot.clock.minute.value)
    }

    @Test
    fun `empty durable suffix is a valid continuation point for the next source boundary`() {
        val gate = BoundaryKey(minute(1), BoundaryCategory.MANDATORY_DECISION_PREREQUISITE, 0, 0, "gate", "", "world")
        val resumed = traversal.resumeFrozenBatch(
            snapshot(1),
            TimeAdvanceGoal.UntilMinute(minute(2)),
            BoundaryCursor(minute(1), gate),
            PendingBoundarySuffix(emptyList())
        )
        assertEquals(TimeAdvanceResult.LIMIT_REACHED, resumed.result)
        val next = traversal.traverse(
            resumed.snapshot,
            minute(2),
            TimeAdvanceGoal.UntilMinute(minute(2)),
            TimeTraversalLimits(minute(2), 8),
            listOf(FixedSource("world", listOf(candidate("next", 2, BoundaryCategory.WORLD_EVENT))))
        )
        assertEquals(TimeAdvanceResult.COMPLETED, next.result)
    }

    @Test
    fun `persisted suffix codec corruption is a system halt rather than a new limit`() {
        val candidate = candidate("corrupt", 1, BoundaryCategory.WORLD_EVENT).copy(payloadCodec = "unknown.v1")
        val suffix = PendingBoundarySuffix(listOf(candidate))
        val cursor = BoundaryCursor(minute(1), candidate("previous", 1, BoundaryCategory.WORLD_EVENT).key)
        val strictTraversal = WorldTimeTraversal(boundaryEvaluator { state, _ -> state }, approvedCodecs = setOf("registered.v1"))

        val result = strictTraversal.resumeFrozenBatch(snapshot(1), TimeAdvanceGoal.UntilMinute(minute(2)), cursor, suffix)

        assertEquals(TimeAdvanceResult.FAILED, result.result)
        assertTrue(result.error is DomainError.SystemHalted)
        assertEquals(TraversalFailure.SYSTEM_HALT, result.failure)
    }

    @Test
    fun `in memory segment port returns the existing receipt and fault leaves the previous segment intact`() = runBlocking {
        val port = InMemorySegmentSavePort()
        val envelope = CommandEnvelope.create(
            CommandId("advance-1"), SessionEpoch(1), StateVersion(0), entity("actor"), UnsupportedFeaturePayload("advance")
        )
        val state = TimeAdvanceState(TimeAdvanceGoal.UntilMinute(minute(10)), null, 0, null)
        val delta = DomainDelta(emptyList(), RngState(emptyList()), emptyList(), CommandResult.Accepted(1))

        val committed = port.commitSegment(envelope, 0, delta, state, null, null)
        assertEquals(committed, port.commitSegment(envelope, 0, delta, state, null, null))
        assertEquals(1, port.segmentCount(envelope.commandId))

        val fault = FaultInjectingSegmentSavePort(port)
        assertTrue(runCatching { fault.commitSegment(envelope, 1, delta, state, TimeAdvanceResult.INTERRUPTED, null) }.isFailure)
        assertEquals(1, port.segmentCount(envelope.commandId))

        val continuation = TimeAdvanceContinuation(
            SessionEpoch(1), envelope.commandId, CommandId("advance-child"), PendingBoundarySuffix(emptyList()).hash, null,
            DecisionSelection("gate", "continue", "DecisionSelection.v1", "{}")
        )
        port.commitSegment(envelope, 1, delta, state, TimeAdvanceResult.INTERRUPTED, continuation)
        assertTrue(runCatching { port.commitSegment(envelope, 2, delta, state, TimeAdvanceResult.INTERRUPTED, continuation.copy(childCommandId = CommandId("advance-other-child"))) }.isFailure)
    }

    @Test
    fun `all durable time advance goal variants round trip through their canonical codec`() {
        val goals = listOf(
            TimeAdvanceGoal.UntilMinute(minute(10)),
            TimeAdvanceGoal.UntilFirstActionCompleted(ActionSelector.ActionId(entity("a"))),
            TimeAdvanceGoal.UntilAllActionsCompleted(ActionSelector.ActionId(entity("b"))),
            TimeAdvanceGoal.UntilCondition(ConditionRef("condition.v1", "sunrise")),
            TimeAdvanceGoal.UntilEvent(EventSelector("scheduled.action.complete.v1"))
        )

        goals.forEach { goal ->
            val encoded = TimeAdvanceGoalCodec.encode(goal)
            assertTrue(encoded.contains("\"goalPayload\":"))
            assertEquals(Checked.Value(goal), TimeAdvanceGoalCodec.decode(encoded))
            assertEquals(TimeAdvanceGoalCodec.hash(goal), canonicalPayloadHash(encoded))
        }
        assertTrue(TimeAdvanceGoalCodec.decode("{\"codec\":\"TIME_ADVANCE_GOAL.v1\",\"targetType\":\"UNTIL_MINUTE\",\"targetMinute\":10}") is Checked.Rejected)
    }

    @Test
    fun `until all actions waits for every action selected by a canonical selector`() {
        val selector = ActionSelector.Canonical("group.v1", "healers")
        val a = entity("a")
        val b = entity("b")
        val selected = mapOf(canonicalPayloadHash(selector.canonicalJson()) to setOf(a, b))

        assertTrue(TimeAdvanceGoal.UntilFirstActionCompleted(selector).isSatisfied(snapshot(10).copy(completedActionIds = setOf(a), selectedActionIdsBySelector = selected)))
        assertTrue(!TimeAdvanceGoal.UntilAllActionsCompleted(selector).isSatisfied(snapshot(10).copy(completedActionIds = setOf(a), selectedActionIdsBySelector = selected)))
        assertTrue(TimeAdvanceGoal.UntilAllActionsCompleted(selector).isSatisfied(snapshot(10).copy(completedActionIds = setOf(a, b), selectedActionIdsBySelector = selected)))
    }

    @Test
    fun `until event completes when a boundary candidate is observed`() {
        val action = entity("treatment")
        val complete = candidate("treatment", 630, BoundaryCategory.SCHEDULED_ACTION_COMPLETE, kind = "scheduled.action.complete.v1").copy(
            eventType = "scheduled.action.complete.v1",
            importance = EventImportance.NORMAL,
            subjectId = action
        )
        val result = traversal.traverse(
            snapshot(629),
            minute(631),
            TimeAdvanceGoal.UntilEvent(EventSelector("scheduled.action.completed.v1", subjectId = action)),
            TimeTraversalLimits(minute(700), 8),
            listOf(FixedSource("schedule", listOf(complete)))
        )

        assertEquals(TimeAdvanceResult.COMPLETED, result.result)
        assertTrue(result.snapshot.observedEvents.any { it.eventType == "scheduled.action.completed.v1" && it.subjectId == action })
    }

    @Test
    fun `calendar source is stateless and emits day month year in boundary order`() {
        val source = CalendarBoundarySource()
        val beforeMonth = snapshot(518_399)
        assertEquals(minute(518_400), source.nextTimeAfter(beforeMonth, null))
        val candidates = source.candidatesAt(beforeMonth, minute(518_400))
        assertEquals(
            listOf("calendar.day.start.v1", "calendar.month.start.v1", "calendar.year.start.v1"),
            candidates.map(BoundaryCandidate::candidateKind)
        )
        assertEquals(listOf(0L, 1L, 2L), candidates.map { it.key.domainSequence })
        assertTrue(BoundarySourceConformanceSuite.validate(source, beforeMonth, null, minute(518_400), setOf("CalendarBoundaryPayload.v1")) is Checked.Value)
    }

    @Test
    fun `thirty day clock jump and ten thousand boundary batches remain deterministic`() {
        val thirtyDays = 30L * 24L * 60L
        val empty = traversal.traverse(snapshot(0), minute(thirtyDays), TimeAdvanceGoal.UntilMinute(minute(thirtyDays)), TimeTraversalLimits(minute(thirtyDays), 1), emptyList())
        assertEquals(TimeAdvanceResult.COMPLETED, empty.result)

        val tenThousand = traversal.traverse(snapshot(0), minute(10_000), TimeAdvanceGoal.UntilMinute(minute(10_000)), TimeTraversalLimits(minute(10_000), 10_000), listOf(SequentialSource(10_000)))
        assertEquals(TimeAdvanceResult.COMPLETED, tenThousand.result)
        assertEquals(10_000, tenThousand.processedBoundaryCount)
    }

    @Test
    fun `P2-PT-001 keeps long traversal deterministic and rejects count and byte overflow before mutation`() {
        val thirtyDays = 30L * 24L * 60L
        val empty = traversal.traverse(
            snapshot(0), minute(thirtyDays), TimeAdvanceGoal.UntilMinute(minute(thirtyDays)),
            TimeTraversalLimits(minute(thirtyDays), 1), emptyList()
        )
        assertEquals(TimeAdvanceResult.COMPLETED, empty.result)

        val tenThousand = traversal.traverse(
            snapshot(0), minute(10_000), TimeAdvanceGoal.UntilMinute(minute(10_000)),
            TimeTraversalLimits(minute(10_000), 10_000), listOf(SequentialSource(10_000))
        )
        assertEquals(TimeAdvanceResult.COMPLETED, tenThousand.result)
        assertEquals(10_000, tenThousand.processedBoundaryCount)

        val countCapped = traversal.traverse(
            snapshot(0), minute(2), TimeAdvanceGoal.UntilMinute(minute(2)),
            TimeTraversalLimits(minute(2), 1, 1), listOf(
                FixedSource(
                    "world",
                    listOf(candidate("one", 1, BoundaryCategory.WORLD_EVENT), candidate("two", 1, BoundaryCategory.ECONOMY_SETTLEMENT))
                )
            )
        )
        assertEquals(TimeAdvanceResult.LIMIT_REACHED, countCapped.result)
        assertTrue(countCapped.error is DomainError.BoundaryLimitReached)
        assertEquals(snapshot(0), countCapped.snapshot)
        assertTrue(countCapped.eventDrafts.isEmpty())

        val oversized = traversal.traverse(
            snapshot(0), minute(2), TimeAdvanceGoal.UntilMinute(minute(2)),
            TimeTraversalLimits(minute(2), 8), listOf(
                FixedSource("world", listOf(candidate("large", 1, BoundaryCategory.WORLD_EVENT, payload = "x".repeat(65_537))))
            )
        )
        assertEquals(TimeAdvanceResult.LIMIT_REACHED, oversized.result)
        assertTrue(oversized.error is DomainError.BoundaryLimitReached)
        assertEquals(snapshot(0), oversized.snapshot)
        assertTrue(oversized.eventDrafts.isEmpty())
    }

    @Test
    fun `P2-UT-004 processes four same timestamp candidates in canonical order`() {
        val lifecycle = orderedCandidate("lifecycle-z", BoundaryCategory.LIFECYCLE, priority = 99, domainSequence = 9)
        val startA = orderedCandidate("start-a", BoundaryCategory.SCHEDULED_ACTION_START, priority = 1, domainSequence = 0)
        val startB = orderedCandidate("start-b", BoundaryCategory.SCHEDULED_ACTION_START, priority = 1, domainSequence = 0)
        val startLaterPriority = orderedCandidate("start-a", BoundaryCategory.SCHEDULED_ACTION_START, priority = 2, domainSequence = 0)
        val candidates = listOf(startLaterPriority, startB, lifecycle, startA)
        val initialRng = deterministicRng()
        val ordered = orderingTraversal().traverse(
            snapshot(0), minute(1), TimeAdvanceGoal.UntilMinute(minute(1)), TimeTraversalLimits(minute(1), 8),
            listOf(FixedSource("fixture", candidates))
        )
        val reversed = orderingTraversal().traverse(
            snapshot(0), minute(1), TimeAdvanceGoal.UntilMinute(minute(1)), TimeTraversalLimits(minute(1), 8),
            listOf(FixedSource("fixture", candidates.reversed()))
        )

        val expectedOrder = listOf("lifecycle-z", "start-a", "start-b", "start-a")
        assertEquals(TimeAdvanceResult.COMPLETED, ordered.result)
        assertEquals(1, ordered.processedBoundaryCount)
        assertEquals(expectedOrder, ordering(ordered.snapshot))
        assertEquals(expectedOrder, ordered.eventDrafts.map { it.stableEventKey.substringAfter("fixture/").substringBefore('/') })
        assertTrue(WorldTimeTraversal.BOUNDARY_ORDER.compare(startA, startLaterPriority) < 0)
        assertTrue(WorldTimeTraversal.BOUNDARY_ORDER.compare(startA, startB) < 0)
        assertTrue(WorldTimeTraversal.BOUNDARY_ORDER.compare(
            startA,
            startA.copy(key = startA.key.copy(domainSequence = 1))
        ) < 0)
        assertEquals(traversalStateHash(ordered.snapshot, initialRng, ordered.eventDrafts), traversalStateHash(reversed.snapshot, initialRng, reversed.eventDrafts))
    }

    @Test
    fun `P2-BT-004 continuous and bounded traversal preserve world rng and event order`() {
        val source = FixedSource(
            "fixture",
            listOf(
                orderedCandidate("first", BoundaryCategory.WORLD_EVENT, time = 1),
                orderedCandidate("second", BoundaryCategory.WORLD_EVENT, time = 2)
            )
        )
        val initial = snapshot(0)
        val rng = deterministicRng()
        val continuous = orderingTraversal().traverse(
            initial, minute(2), TimeAdvanceGoal.UntilMinute(minute(2)), TimeTraversalLimits(minute(2), 8), listOf(source)
        )
        val firstSegment = orderingTraversal().traverse(
            initial, minute(1), TimeAdvanceGoal.UntilMinute(minute(1)), TimeTraversalLimits(minute(2), 8), listOf(source)
        )
        val bounded = orderingTraversal().traverse(
            firstSegment.snapshot, minute(2), TimeAdvanceGoal.UntilMinute(minute(2)), TimeTraversalLimits(minute(2), 8), listOf(source), firstSegment.cursor
        )
        val boundedEvents = firstSegment.eventDrafts + bounded.eventDrafts

        assertEquals(TimeAdvanceResult.COMPLETED, continuous.result)
        assertEquals(TimeAdvanceResult.COMPLETED, bounded.result)
        assertEquals(continuous.snapshot, bounded.snapshot)
        assertEquals(continuous.eventDrafts.map(DomainEventDraft::stableEventKey), boundedEvents.map(DomainEventDraft::stableEventKey))
        assertEquals(rng, deterministicRng())
        assertEquals(rng.streams.single().drawCounter, deterministicRng().streams.single().drawCounter)
        val continuousHash = traversalStateHash(continuous.snapshot, rng, continuous.eventDrafts)
        val boundedHash = traversalStateHash(bounded.snapshot, rng, boundedEvents)
        val repeated = orderingTraversal().traverse(
            initial,
            minute(2),
            TimeAdvanceGoal.UntilMinute(minute(2)),
            TimeTraversalLimits(minute(2), 8),
            listOf(source)
        )
        val repeatedHash = traversalStateHash(repeated.snapshot, rng, repeated.eventDrafts)
        println("P2-BT-004 continuousHash=${continuousHash.value} boundedHash=${boundedHash.value} repeatedHash=${repeatedHash.value} minute=${continuous.snapshot.clock.minute.value} drawCounter=${rng.streams.single().drawCounter} events=${continuous.eventDrafts.map(DomainEventDraft::stableEventKey)}")
        assertEquals(continuousHash, boundedHash)
        assertEquals(continuous.snapshot, repeated.snapshot)
        assertEquals(continuous.eventDrafts, repeated.eventDrafts)
        assertEquals(continuousHash, repeatedHash)
    }

    private fun orderingTraversal() = WorldTimeTraversal(BoundaryEvaluator { state, candidate ->
        val existing = state.aggregates[ORDER_AGGREGATE_ID] as? OrderedBoundaryAggregate
        val next = OrderedBoundaryAggregate(ORDER_AGGREGATE_ID, (existing?.ids ?: emptyList()) + candidate.key.stableEntityId)
        BoundaryEvaluation(state.copy(aggregates = state.aggregates + (ORDER_AGGREGATE_ID to next)), listOf(candidate.eventDraft()))
    })

    private fun ordering(snapshot: WorldTraversalSnapshot): List<String> =
        (snapshot.aggregates[ORDER_AGGREGATE_ID] as OrderedBoundaryAggregate).ids

    private fun traversalStateHash(snapshot: WorldTraversalSnapshot, rng: RngState, events: List<DomainEventDraft>): PayloadHash =
        canonicalPayloadHash(buildString {
            append(snapshot.clock.minute.value).append('|')
            snapshot.aggregates.toSortedMap(compareBy(EntityId::value)).forEach { (id, aggregate) -> append(id.value).append(':').append(aggregate.canonicalJson()).append('|') }
            rng.streams.sortedBy { it.streamKey.value }.forEach { stream -> append(stream.streamKey.value).append(':').append(stream.state).append(':').append(stream.drawCounter).append('|') }
            events.forEach { append(it.stableEventKey).append('|') }
        })

    private data class OrderedBoundaryAggregate(
        override val aggregateId: EntityId,
        val ids: List<String>
    ) : AggregateState {
        override val aggregateType: String = "p2.boundary-order.v1"
        override fun canonicalJson(): String = ids.joinToString(prefix = "[", postfix = "]") { CanonicalJson.string(it) }
    }

    private class FixedSource(
        override val sourceId: String,
        private val candidates: List<BoundaryCandidate>
    ) : BoundarySource {
        override fun nextTimeAfter(snapshot: WorldTraversalSnapshot, cursor: BoundaryCursor?): GameMinute? = candidates
            .map { it.key.boundaryTime }
            .filter { it.value > snapshot.clock.minute.value }
            .minByOrNull(GameMinute::value)

        override fun candidatesAt(snapshot: WorldTraversalSnapshot, time: GameMinute): List<BoundaryCandidate> =
            candidates.filter { it.key.boundaryTime == time }
    }

    private class SequentialSource(private val lastMinute: Long) : BoundarySource {
        override val sourceId = "world"
        override fun nextTimeAfter(snapshot: WorldTraversalSnapshot, cursor: BoundaryCursor?): GameMinute? =
            (snapshot.clock.minute.value + 1).takeIf { it <= lastMinute }?.let(::minute)

        override fun candidatesAt(snapshot: WorldTraversalSnapshot, time: GameMinute): List<BoundaryCandidate> =
            listOf(candidate("event-${time.value}", time.value, BoundaryCategory.WORLD_EVENT))
    }

    private class InMemorySegmentSavePort : SavePort {
        private val segments = mutableMapOf<CommandId, SegmentCommitReceipt>()
        private val claimedPredecessors = mutableSetOf<Pair<SessionEpoch, CommandId>>()

        override suspend fun findReceipt(sessionEpoch: SessionEpoch, commandId: CommandId): PersistedReceipt? = null

        override suspend fun commit(
            envelope: CommandEnvelope<out WorldCommandPayload>,
            delta: DomainDelta
        ): CommitReceipt = CommitReceipt(StateVersion(0), delta.result)

        override suspend fun commitSegment(
            envelope: CommandEnvelope<out WorldCommandPayload>,
            expectedSegmentNo: Int,
            delta: DomainDelta,
            timeAdvanceState: TimeAdvanceState,
            terminalResult: TimeAdvanceResult?,
            continuation: TimeAdvanceContinuation?
        ): SegmentCommitReceipt {
            continuation?.let {
                require(claimedPredecessors.add(it.predecessorEpoch to it.predecessorCommandId)) { "continuation already claimed" }
            }
            val existing = segments[envelope.commandId]
            if (existing != null && expectedSegmentNo <= existing.segmentNo) return existing
            require(existing == null || expectedSegmentNo == existing.segmentNo + 1) { "stale segment" }
            return SegmentCommitReceipt(
                CommitReceipt(StateVersion((expectedSegmentNo + 1).toLong()), delta.result),
                expectedSegmentNo,
                timeAdvanceState.copy(status = terminalResult)
            ).also { segments[envelope.commandId] = it }
        }

        fun segmentCount(commandId: CommandId): Int = if (segments.containsKey(commandId)) 1 else 0

    }

    private class FaultInjectingSegmentSavePort(private val delegate: SavePort) : SavePort by delegate {
        override suspend fun commitSegment(
            envelope: CommandEnvelope<out WorldCommandPayload>,
            expectedSegmentNo: Int,
            delta: DomainDelta,
            timeAdvanceState: TimeAdvanceState,
            terminalResult: TimeAdvanceResult?,
            continuation: TimeAdvanceContinuation?
        ): SegmentCommitReceipt = error("injected segment fault")
    }

    private companion object {
        val ORDER_AGGREGATE_ID: EntityId = entity("boundary-order")

        fun orderedCandidate(
            id: String,
            category: BoundaryCategory,
            time: Long = 1,
            priority: Int = 0,
            domainSequence: Long = 0
        ): BoundaryCandidate = candidate(id, time, category).copy(
            key = BoundaryKey(minute(time), category, priority, domainSequence, id, "", "fixture")
        )

        fun deterministicRng(): RngState = RngState(
            listOf(RngStreamState(RngStreamKey("world"), "pcg32.v1", 42, 55, 7))
        )

        fun candidate(
            id: String,
            time: Long,
            category: BoundaryCategory,
            kind: String? = null,
            disposition: BoundaryDisposition = BoundaryDisposition.CONTINUE,
            payload: String = "{}"
        ) = BoundaryCandidate(
            key = BoundaryKey(minute(time), category, 0, 0, id, "", when (category) {
                BoundaryCategory.SCHEDULED_ACTION_COMPLETE, BoundaryCategory.SCHEDULED_ACTION_START -> "schedule"
                BoundaryCategory.COMBAT_CRISIS -> "combat"
                else -> "world"
            }),
            candidateKind = kind ?: when (category) {
                BoundaryCategory.SCHEDULED_ACTION_START -> "scheduled.action.start.v1"
                BoundaryCategory.SCHEDULED_ACTION_COMPLETE -> "scheduled.action.complete.v1"
                BoundaryCategory.COMBAT_CRISIS -> "decision.required.v1"
                BoundaryCategory.ECONOMY_SETTLEMENT -> "economy.settlement.v1"
                else -> "calendar.day.start.v1"
            },
            payloadCodec = "test.payload.v1",
            canonicalPayload = payload,
            disposition = disposition
        )

        fun snapshot(minute: Long) = WorldTraversalSnapshot(WorldClock(minute(minute)))
        fun minute(value: Long): GameMinute = checked(GameMinute.of(value))
        fun entity(value: String): EntityId = checked(EntityId.of(value))
        fun <T> checked(value: Checked<T>): T = when (value) {
            is Checked.Value -> value.value
            is Checked.Rejected -> error(value.error.toString())
        }
    }
}
