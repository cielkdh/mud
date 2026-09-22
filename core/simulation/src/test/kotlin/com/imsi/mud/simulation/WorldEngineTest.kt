package com.imsi.mud.simulation

import com.imsi.mud.content.ContentSnapshot
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class WorldEngineTest {
    @Test
    fun `advance codec round trips its interrupted resume lineage`() {
        val payload = AdvanceTimePayload(
            TimeAdvanceGoal.UntilMinute(minute(12)),
            ProgressionMode.FAST_FORWARD,
            TimeTraversalLimits(minute(20), 8),
            resumeOfCommandId = CommandId("interrupted-parent")
        )

        assertEquals(Checked.Value(payload), CommandPayloadCodec.decode(payload.codecId, payload.canonicalJson()))
    }

    @Test
    fun `travel advance is rejected until it is represented by a scheduled action`() {
        val engine = WorldEngine(emptyList())
        val payload = AdvanceTimePayload(TimeAdvanceGoal.UntilMinute(minute(1)), ProgressionMode.TRAVEL, TimeTraversalLimits(minute(1), 8))

        val plan = engine.plan(CommandEnvelope.create(CommandId("travel"), SessionEpoch(1), StateVersion(0), null, payload), snapshot(0).copy(
            world = snapshot(0).world.copy(boundaryBinding = BoundaryRegistryBinding(1, emptyList()))
        ), StateVersion(1), 1) as ExecutionPlan.Atomic

        assertEquals(CommandResult.Rejected(DomainError.ScheduledActionRequired(ProgressionMode.TRAVEL), 1), plan.delta.result)
    }

    @Test
    fun `advance payload plans against the supplied world snapshot without caller sources`() {
        val engine = WorldEngine(listOf(CalendarBoundarySource(), ScheduledActionBoundarySource()))
        val before = snapshot(1_439)
        val payload = AdvanceTimePayload(TimeAdvanceGoal.UntilMinute(minute(1_440)), ProgressionMode.FAST_FORWARD, TimeTraversalLimits(minute(1_500), 8))
        val envelope = CommandEnvelope.create(CommandId("advance"), SessionEpoch(1), StateVersion(0), null, payload)

        val plan = engine.plan(envelope, before, StateVersion(1), 1) as ExecutionPlan.Segment

        assertEquals(1_439, plan.delta.worldChange!!.after.clock.minute.value)
        assertEquals(ReceiptLifecycle.RUNNING, lifecycle(plan.terminalResult))
    }

    @Test
    fun `default evaluator materializes scheduled completion for until event goals`() = runBlocking {
        val action = ScheduledAction(
            entity("default-event-action"),
            "schedule.action",
            actionPayload(SchedulePriority.TREATMENT),
            minute(1),
            minute(2),
            ScheduledActionStatus.RESERVED
        )
        val base = snapshot(0)
        val initial = base.copy(world = base.world.copy(calendar = ScheduleCalendar(listOf(action), emptyMap())))
        val port = SegmentPort()
        val session = WorldSession(
            SessionEpoch(1), port, this, ContentSnapshot.emptyForTest(), initial,
            WorldEngine(listOf(CalendarBoundarySource(), ScheduledActionBoundarySource())),
            kotlinx.coroutines.Dispatchers.Unconfined
        )
        val payload = AdvanceTimePayload(
            TimeAdvanceGoal.UntilEvent(EventSelector("scheduled.action.completed.v1")),
            ProgressionMode.FAST_FORWARD,
            TimeTraversalLimits(minute(10), 8)
        )

        assertTrue(session.execute(CommandEnvelope.create(CommandId("until-scheduled-complete"), SessionEpoch(1), StateVersion(0), null, payload)) is CommandResult.Accepted)
        assertTrue(port.committedEvents.flatten().any { event ->
            (event.payload as? BoundaryDomainEventPayload)?.codecId == "scheduled.action.completed.v1"
        })
        session.close()
    }

    @Test
    fun `world session commits the engine segment before applying its authoritative world state`() = runBlocking {
        val port = SegmentPort()
        val engine = WorldEngine(listOf(CalendarBoundarySource(), ScheduledActionBoundarySource()))
        val session = WorldSession(SessionEpoch(1), port, this, ContentSnapshot.emptyForTest(), snapshot(1_439), engine)
        val payload = AdvanceTimePayload(TimeAdvanceGoal.UntilMinute(minute(1_440)), ProgressionMode.FAST_FORWARD, TimeTraversalLimits(minute(1_500), 8))

        val result = session.execute(CommandEnvelope.create(CommandId("advance"), SessionEpoch(1), StateVersion(0), null, payload))

        assertTrue(result is CommandResult.Accepted)
        assertEquals(2, port.segmentCommits) // admission segment 0 plus the completed boundary slice
        session.close()
    }

    @Test
    fun `fast forward commits consecutive timestamp batches separately with durable event sequences`() = runBlocking {
        val source = object : BoundarySource {
            override val sourceId = "consecutive"
            override fun nextTimeAfter(snapshot: WorldTraversalSnapshot, cursor: BoundaryCursor?): GameMinute? =
                listOf(1L, 2L).firstOrNull { it > snapshot.clock.minute.value }?.let(::minute)

            override fun candidatesAt(snapshot: WorldTraversalSnapshot, time: GameMinute) = listOf(
                BoundaryCandidate(
                    BoundaryKey(time, BoundaryCategory.WORLD_EVENT, 0, 0, "event-${time.value}", "", sourceId),
                    "calendar.day.start.v1",
                    "CalendarBoundaryPayload.v1",
                    "{\"minute\":${time.value}}"
                )
            )
        }
        val initial = snapshot(0).copy(world = snapshot(0).world.copy(
            boundaryBinding = BoundaryRegistryBinding(1, listOf(source.sourceId), candidateCodecs = setOf("CalendarBoundaryPayload.v1"))
        ))
        val port = SegmentPort()
        val engine = WorldEngine(listOf(source), boundaryEvaluator { state, _ -> state })
        val session = WorldSession(SessionEpoch(1), port, this, ContentSnapshot.emptyForTest(), initial, engine, kotlinx.coroutines.Dispatchers.Unconfined)
        val payload = AdvanceTimePayload(TimeAdvanceGoal.UntilMinute(minute(3)), ProgressionMode.FAST_FORWARD, TimeTraversalLimits(minute(3), 8))

        assertEquals(CommandResult.Accepted(1), session.execute(CommandEnvelope.create(CommandId("consecutive-advance"), SessionEpoch(1), StateVersion(0), null, payload)))
        assertEquals(listOf(0L, 1L, 2L, 3L), port.committedMinutes)
        assertEquals(listOf(emptyList(), listOf(0L), listOf(1L), emptyList()), port.committedEventSequences)
        port.committedEvents.flatten().forEach { event ->
            assertEquals(Checked.Value(event), DomainEventCodec.decode(DomainEventCodec.encode(event)))
        }
        session.close()
    }

    @Test
    fun `candidate cap commits no boundary effect and retries the same boundary`() = runBlocking {
        val source = object : BoundarySource {
            override val sourceId = "capped"
            override fun nextTimeAfter(snapshot: WorldTraversalSnapshot, cursor: BoundaryCursor?): GameMinute? =
                minute(1).takeIf { it.value > snapshot.clock.minute.value }

            override fun candidatesAt(snapshot: WorldTraversalSnapshot, time: GameMinute) = listOf(
                BoundaryCandidate(BoundaryKey(time, BoundaryCategory.WORLD_EVENT, 0, 0, "one", "", sourceId), "calendar.day.start.v1", "CappedPayload.v1", "{\"id\":\"one\"}"),
                BoundaryCandidate(BoundaryKey(time, BoundaryCategory.ECONOMY_SETTLEMENT, 0, 0, "two", "", sourceId), "economy.settlement.v1", "CappedPayload.v1", "{\"id\":\"two\"}")
            )
        }
        val base = snapshot(0)
        val initial = base.copy(world = base.world.copy(
            boundaryBinding = BoundaryRegistryBinding(1, listOf(source.sourceId), candidateCodecs = setOf("CappedPayload.v1"))
        ))
        val port = AtomicPort()
        val session = WorldSession(
            SessionEpoch(1), port, this, ContentSnapshot.emptyForTest(), initial,
            WorldEngine(listOf(source), boundaryEvaluator { state, _ -> state }), kotlinx.coroutines.Dispatchers.Unconfined
        )
        val capped = AdvanceTimePayload(TimeAdvanceGoal.UntilMinute(minute(1)), ProgressionMode.FAST_FORWARD, TimeTraversalLimits(minute(1), 8, 1))

        assertEquals(CommandResult.Accepted(1), session.execute(CommandEnvelope.create(CommandId("capped"), SessionEpoch(1), StateVersion(0), null, capped)))
        assertEquals(TimeAdvanceResult.LIMIT_REACHED, port.receipts.getValue(CommandId("capped")).timeAdvanceState!!.status)
        assertEquals(0, port.lastDelta!!.worldChange!!.after.clock.minute.value)
        assertEquals(initial.rngState, port.lastDelta!!.rngState)
        assertTrue(port.lastDelta!!.events.isEmpty())
        assertEquals(2, port.segmentWrites)

        val retry = capped.copy(limits = capped.limits.copy(maxCandidatesPerBatch = 2))
        assertEquals(CommandResult.Accepted(2), session.execute(CommandEnvelope.create(CommandId("capped-retry"), SessionEpoch(1), StateVersion(2), null, retry)))
        assertEquals(1, port.lastDelta!!.worldChange!!.after.clock.minute.value)
        assertEquals(4, port.segmentWrites)
        session.close()
    }

    @Test
    fun `P2-FT-004 system halt from a running traversal does not write a terminal segment`() = runBlocking {
        val source = object : BoundarySource {
            override val sourceId = "world"
            override fun nextTimeAfter(snapshot: WorldTraversalSnapshot, cursor: BoundaryCursor?) = minute(1)
            override fun candidatesAt(snapshot: WorldTraversalSnapshot, time: GameMinute) = listOf(
                BoundaryCandidate(
                    BoundaryKey(time, BoundaryCategory.WORLD_EVENT, 0, 0, "bad", "", sourceId),
                    "unknown.kind.v1",
                    "unregistered.v1",
                    "{}"
                )
            )
        }
        val payload = AdvanceTimePayload(TimeAdvanceGoal.UntilMinute(minute(1)), ProgressionMode.FAST_FORWARD, TimeTraversalLimits(minute(1), 8))
        val envelope = CommandEnvelope.create(CommandId("running"), SessionEpoch(1), StateVersion(0), null, payload)
        val running = TimeAdvanceState(
            payload.goal, null, 0, null,
            commandEpoch = envelope.sessionEpoch, commandId = envelope.commandId,
            progressionMode = payload.mode, limits = payload.limits, interruptPolicy = payload.interruptPolicy
        )
        val initial = WorldSnapshot(
            StateVersion(0),
            AuthoritativeWorldState(WorldClock(minute(0)), ScheduleCalendar(emptyList(), emptyMap()), running, BoundaryRegistryBinding(1, listOf("world"), candidateCodecs = setOf("registered.v1"))),
            RngState(emptyList()), emptyMap()
        )
        val port = SegmentPort()
        val session = WorldSession(SessionEpoch(1), port, this, ContentSnapshot.emptyForTest(), initial, WorldEngine(listOf(source)), kotlinx.coroutines.Dispatchers.Unconfined)
        val before = session.inMemoryStateHash()

        val result = session.execute(envelope)

        assertEquals(CommandResult.Rejected(DomainError.SystemHalted("boundary candidate codec is not registered"), 1), result)
        assertEquals(0, port.segmentCommits)
        assertEquals(null, session.publications.value)
        assertEquals(before, session.inMemoryStateHash())
        assertEquals(CommandId("running"), session.runtimeState.value.activeAdvanceCommandId)
        assertEquals(SessionLifecycle.PAUSED, session.runtimeState.value.lifecycle)
        session.close()
    }

    @Test
    fun `P2-CT-003 schedule gameplay payloads round trip and WorldSession commits each external mutation once`() = runBlocking {
        val reserve = reservePayload("treatment", 10, 20)
        val incumbent = ScheduledAction(entity("incumbent"), "schedule.action", actionPayload(SchedulePriority.TRAINING_ROUTINE, canBePreempted = true), minute(10), minute(20))
        val conflictRequest = reservePayload("rescue", 10, 20, SchedulePriority.EMERGENCY_RESCUE)
        val conflict = ScheduleService().reserve(
            ReservationRequest(
                conflictRequest.actionId, conflictRequest.actionKind, conflictRequest.scheduledPayload,
                conflictRequest.startMinute, conflictRequest.dueMinute, conflictRequest.effectiveMinute
            ),
            ScheduleCalendar(listOf(incumbent), emptyMap())
        ) as ReservationResult.Conflict
        val resolve = ScheduleResolveConflictPayload(
            conflictRequest, ScheduleResolution.PREEMPT, conflict.details.conflictingRowVersions,
            PublicConsequencePreview.CODEC_ID, conflict.details.consequencePreview.hash
        )
        assertTrue(resolve.canonicalJson().contains("[{\"actionId\":"))
        assertTrue(!resolve.canonicalJson().contains("[{\\\"actionId\":"))
        val cancel = ScheduleCancelPayload(reserve.actionId, StateVersion(0), CancellationStage.BEFORE_START)
        listOf(reserve, cancel, resolve).forEach { payload ->
            assertEquals(Checked.Value(payload), CommandPayloadCodec.decode(payload.codecId, payload.canonicalJson()))
            assertTrue(CommandPayloadCodec.decode(payload.codecId, payload.canonicalJson().dropLast(1)) is Checked.Rejected)
        }
        assertTrue(CommandPayloadCodec.decode("schedule.unknown.v1", reserve.canonicalJson()) is Checked.Rejected)

        val port = SchedulePort()
        val session = scheduleSession(port)
        val reserveEnvelope = CommandEnvelope.create(CommandId("reserve"), SessionEpoch(1), StateVersion(0), null, reserve)
        val before = session.inMemoryStateHash()
        assertEquals(CommandResult.Accepted(1), session.execute(reserveEnvelope))
        assertEquals(1, port.commits)
        assertTrue(session.publications.value != null)
        assertTrue(before != session.inMemoryStateHash())
        assertEquals(CommandResult.Accepted(1), session.execute(reserveEnvelope))
        assertEquals(1, port.commits)
        val reused = session.execute(CommandEnvelope.create(CommandId("reserve"), SessionEpoch(1), StateVersion(1), null, reserve.copy(actionId = entity("other"))))
        assertTrue((reused as CommandResult.Rejected).error is DomainError.IdempotencyKeyReuse)
        assertEquals(CommandResult.Accepted(4), session.execute(CommandEnvelope.create(CommandId("cancel"), SessionEpoch(1), StateVersion(1), null, cancel)))
        assertEquals(2, port.commits)
        session.close()

        val resolvePort = SchedulePort()
        val resolveSession = scheduleSession(resolvePort, ScheduleCalendar(listOf(incumbent), emptyMap()))
        assertEquals(CommandResult.Accepted(1), resolveSession.execute(CommandEnvelope.create(CommandId("resolve"), SessionEpoch(1), StateVersion(0), null, resolve)))
        assertEquals(1, resolvePort.commits)
        resolveSession.close()

        val failedPort = SchedulePort(failCommit = true)
        val failedSession = scheduleSession(failedPort)
        val failedBefore = failedSession.inMemoryStateHash()
        assertTrue((failedSession.execute(reserveEnvelope) as CommandResult.Rejected).error is DomainError.PersistenceFailure)
        assertEquals(0, failedPort.commits)
        assertEquals(null, failedSession.publications.value)
        assertEquals(failedBefore, failedSession.inMemoryStateHash())
        failedSession.close()
    }

    @Test
    fun `schedule reservation must use the authoritative world minute`() = runBlocking {
        val port = SchedulePort()
        val session = scheduleSession(port)
        val payload = reservePayload("future-schedule", 10, 20).copy(effectiveMinute = minute(1))
        val before = session.inMemoryStateHash()

        val result = session.execute(CommandEnvelope.create(CommandId("future-schedule"), SessionEpoch(1), StateVersion(0), null, payload))

        assertEquals(CommandResult.Rejected(DomainError.ValidationError("effectiveMinute", "must equal current world minute"), 1), result)
        assertEquals(1, port.commits)
        assertEquals(before, session.inMemoryStateHash())
        session.close()
    }

    @Test
    fun `internal atomic elapsed fixture is not decodable as a public gameplay command`() {
        val payload = atomicPayload("combat-private", 31)

        assertTrue(CommandPayloadCodec.decode(payload.codecId, payload.canonicalJson()) is Checked.Rejected)
    }

    @Test
    fun `P2-IT-002 outer combat command uses F002 result across boundary with one outer receipt`() = runBlocking {
        val actionId = "p2-it-002-combat"
        val outerCommandId = CommandId("p2-it-002-outer")
        val source = CalendarBoundarySource()
        val port = AtomicPort()
        val session = atomicSession(
            port,
            atomicEngine(listOf(source), numericOutcomePayload = true),
            atomicSnapshotWithWorldSeed(1_439, listOf(source), actionId, "0123456789abcdef")
        )
        val envelope = CommandEnvelope.create(
            outerCommandId,
            SessionEpoch(1),
            StateVersion(0),
            entity("actor"),
            atomicPayload(actionId, 1_441)
        )

        assertEquals(CommandResult.Accepted(1), session.execute(envelope))
        assertEquals(1, port.atomicWrites)
        assertEquals(0, port.segmentWrites)
        assertEquals(setOf(outerCommandId), port.receipts.keys)

        val delta = port.lastDelta!!
        assertEquals(1_441, delta.worldChange!!.after.clock.minute.value)
        assertEquals(1L, delta.rngState.streams.single().drawCounter)
        assertEquals(
            listOf("calendar.day.started.v1", "elapsed.action.applied.v1"),
            delta.events.map { it.payload.codecId }
        )
        assertEquals(listOf(1_440L, 1_441L), delta.events.map { it.gameMinute.value })
        assertTrue(delta.events.all { it.sourceCommandId == outerCommandId })
        assertTrue(delta.events.none { it.sourceCommandId == CommandId("p2-it-002-f002") })
        assertEquals(1, delta.aggregateChanges.size)
        assertEquals(1, (delta.aggregateChanges.single().after as AtomicResultAggregate).applyCount)
        assertEquals("ec37e79146e2347315c73c752e603b163069dfe35931d42367797efd5f88ce77", (delta.aggregateChanges.single().after as AtomicResultAggregate).outcomeHash!!.value)
        assertEquals(8889892628791201266L, delta.rngState.streams.single().state)
        assertEquals(4931366992596904975L, delta.rngState.streams.single().increment)
        session.close()
    }

    @Test
    fun `elapsed and boundary event payload codecs are strict canonical and versioned`() {
        val payload = ElapsedActionAppliedEventPayload(entity("result-1"), PayloadHash("1".repeat(64)))
        assertEquals(Checked.Value(payload), DomainEventPayloadCodec.decode(payload.codecId, payload.canonicalJson()))
        assertTrue(DomainEventPayloadCodec.decode(payload.codecId, payload.canonicalJson() + " ") is Checked.Rejected)
        assertTrue(DomainEventPayloadCodec.decode("elapsed.action.applied.v2", payload.canonicalJson()) is Checked.Rejected)
        BoundaryDomainEventType.entries.forEach { type ->
            if (type == BoundaryDomainEventType.DECISION_SELECTION_APPLIED) return@forEach
            val boundary = BoundaryDomainEventPayload(type, PayloadHash("2".repeat(64)), "entity-1")
            assertEquals(Checked.Value(boundary), DomainEventPayloadCodec.decode(boundary.codecId, boundary.canonicalJson()))
            assertTrue(DomainEventPayloadCodec.decode(boundary.codecId, boundary.canonicalJson() + " ") is Checked.Rejected)
        }
        val selection = DecisionSelectionAppliedEventPayload("gate-1", "choice-a", "DecisionSelection.v1", PayloadHash("3".repeat(64)))
        assertEquals(Checked.Value(selection), DomainEventPayloadCodec.decode(selection.codecId, selection.canonicalJson()))
        assertTrue(DomainEventPayloadCodec.decode(selection.codecId, selection.canonicalJson() + " ") is Checked.Rejected)
        assertTrue(DomainEventPayloadCodec.decode("boundary.applied.v1", "{}") is Checked.Rejected)
    }

    @Test
    fun `gate free short combat commits clock outcome and rng once`() = runBlocking {
        val port = AtomicPort()
        val payload = atomicPayload("combat-direct", 31)
        val engine = atomicEngine(emptyList())
        val session = atomicSession(port, engine, atomicSnapshot(29, emptyList(), "combat-direct"))
        val envelope = CommandEnvelope.create(CommandId("outer-combat"), SessionEpoch(1), StateVersion(0), entity("actor"), payload)

        assertEquals(CommandResult.Accepted(1), session.execute(envelope))
        assertEquals(1, port.atomicWrites)
        assertEquals(0, port.segmentWrites)
        assertEquals(31, port.lastDelta!!.worldChange!!.after.clock.minute.value)
        assertEquals(TimeAdvanceResult.COMPLETED, port.lastDelta!!.worldChange!!.after.timeAdvance!!.status)
        assertEquals(1, port.lastDelta!!.rngState.streams.single().drawCounter)
        assertEquals(1, port.lastDelta!!.aggregateChanges.size)
        assertEquals(1, (port.lastDelta!!.aggregateChanges.single().after as AtomicResultAggregate).applyCount)
        assertEquals(1, port.lastDelta!!.events.size)
        assertTrue(port.lastDelta!!.events.all { it.visibility == EventVisibility.SYSTEM_HIDDEN })
        assertTrue(session.publications.value!!.events.isEmpty())
        assertEquals(CommandResult.Accepted(1), session.execute(envelope))
        assertEquals(1, port.atomicWrites)
        session.close()
    }

    @Test
    fun `all short elapsed modes traverse and commit only their action rng once`() = runBlocking {
        AtomicElapsedActionKind.entries.forEach { kind ->
            val id = "${kind.name.lowercase()}-direct"
            val port = AtomicPort()
            val payload = atomicPayload(id, 31, kind = kind)
            val session = atomicSession(port, atomicEngine(emptyList()), atomicSnapshot(29, emptyList(), id, kind))
            val envelope = CommandEnvelope.create(CommandId("outer-${kind.name.lowercase()}"), SessionEpoch(1), StateVersion(0), entity("actor"), payload)

            assertEquals(CommandResult.Accepted(1), session.execute(envelope))
            assertEquals(1, port.atomicWrites)
            assertEquals(0, port.segmentWrites)
            assertEquals(31, port.lastDelta!!.worldChange!!.after.clock.minute.value)
            assertEquals(1, port.lastDelta!!.rngState.streams.single().drawCounter)
            assertEquals(1, port.lastDelta!!.events.count { it.payload is ElapsedActionAppliedEventPayload })
            session.close()
        }
    }

    @Test
    fun `combat gate persists prefix and sealed result then child applies suffix and outcome exactly once`() = runBlocking {
        val source = GateSource()
        val port = AtomicPort()
        val payload = atomicPayload("combat-gated", 31)
        val engine = atomicEngine(listOf(source), branchingSelection = true)
        val session = atomicSession(port, engine, atomicSnapshot(29, listOf(source), "combat-gated"))
        val parent = CommandEnvelope.create(CommandId("outer-combat-gated"), SessionEpoch(1), StateVersion(0), entity("actor"), payload)

        assertEquals(CommandResult.Accepted(1), session.execute(parent))
        val pending = port.receipts.getValue(parent.commandId).timeAdvanceState!!
        assertEquals(TimeAdvanceResult.DECISION_REQUIRED, pending.status)
        val publicTerminal = checkNotNull(session.publications.value?.timeAdvanceTerminal)
        assertEquals(parent.commandId, session.publications.value?.sourceCommandId)
        assertEquals(TimeAdvanceResult.DECISION_REQUIRED, publicTerminal.result)
        assertEquals("gate-30", publicTerminal.gateId)
        assertEquals(listOf("A", "B"), publicTerminal.choices.map { it.choiceId })
        assertTrue(publicTerminal.choices.all { it.codec == PublicTimeAdvanceChoice.CODEC_ID })
        assertEquals(pending.pendingSuffix!!.hash, publicTerminal.pendingSuffixHash)
        assertEquals("{\"choiceId\":\"A\"}", publicTerminal.choices.first().canonicalPayload)
        assertEquals(canonicalPayloadHash(publicTerminal.choices.first().canonicalPayload), publicTerminal.choices.first().payloadHash)
        assertEquals(30, port.lastDelta!!.worldChange!!.after.clock.minute.value)
        assertTrue(port.lastDelta!!.worldChange!!.after.calendar.actions.any { it.actionId == entity("prefix-proof") })
        assertEquals(1, port.lastDelta!!.rngState.streams.single().drawCounter)
        assertTrue(port.lastDelta!!.aggregateChanges.isEmpty())
        assertEquals(2, port.lastDelta!!.events.size)
        assertTrue(port.lastDelta!!.events.none { it.payload is ElapsedActionAppliedEventPayload })
        assertEquals(listOf("calendar.day.started.v1", "decision.required.v1"), port.lastDelta!!.events.map { it.payload.codecId })
        assertEquals(listOf(0L, 1L), port.lastDelta!!.events.map { it.eventSequence.value })
        assertEquals(Checked.Value(pending.pendingSuffix!!), PendingBoundarySuffix.decode(pending.pendingSuffix.canonicalPayload, pending.pendingSuffix.hash))
        assertEquals(Checked.Value(pending.sealedElapsedOutcome!!), SealedElapsedOutcome.decode(pending.sealedElapsedOutcome.encode(), pending.sealedElapsedOutcome.hash))

        val childPayload = AtomicElapsedDecisionPayload(
            SessionEpoch(1), parent.commandId, pending.pendingSuffix.hash, pending.sealedElapsedOutcome.hash,
            "gate-30", "A", "AtomicElapsedDecisionSelection.v1", "{\"choiceId\":\"A\"}"
        )
        val child = CommandEnvelope.create(CommandId("combat-choice-a"), SessionEpoch(1), StateVersion(1), entity("actor"), childPayload)
        assertEquals(CommandResult.Accepted(2), session.execute(child))
        assertEquals(31, port.lastDelta!!.worldChange!!.after.clock.minute.value)
        assertEquals(TimeAdvanceResult.COMPLETED, port.lastDelta!!.worldChange!!.after.timeAdvance!!.status)
        assertEquals("A", port.lastDelta!!.worldChange!!.after.timeAdvance!!.selectedDecisionChoiceId)
        assertEquals(1, port.lastDelta!!.rngState.streams.single().drawCounter)
        assertEquals(1, (port.lastDelta!!.aggregateChanges.single().after as AtomicResultAggregate).applyCount)
        assertEquals(3, port.lastDelta!!.events.size)
        assertEquals(1, port.lastDelta!!.events.count { it.payload is ElapsedActionAppliedEventPayload })
        assertEquals(listOf("decision.selection.applied.v1", "calendar.month.started.v1", "elapsed.action.applied.v1"), port.lastDelta!!.events.map { it.payload.codecId })
        val appliedSelection = port.lastDelta!!.events.first().payload as DecisionSelectionAppliedEventPayload
        assertEquals("gate-30", appliedSelection.gateId)
        assertEquals("A", appliedSelection.choiceId)
        assertEquals("AtomicElapsedDecisionSelection.v1", appliedSelection.selectionCodec)
        assertEquals(listOf(0L, 1L, 2L), port.lastDelta!!.events.map { it.eventSequence.value })
        assertEquals(2, port.segmentWrites)
        assertEquals(parent.commandId, port.lastContinuation!!.predecessorCommandId)
        assertEquals(CommandResult.Accepted(2), session.execute(child))
        assertEquals(2, port.segmentWrites)
        session.close()
    }

    @Test
    fun `sealed atomic continuation codecs reject noncanonical tampered and mismatched payloads`() = runBlocking {
        val source = GateSource()
        val port = AtomicPort()
        val payload = atomicPayload("combat-codec", 31)
        val session = atomicSession(port, atomicEngine(listOf(source), branchingSelection = true), atomicSnapshot(29, listOf(source), "combat-codec"))
        val parent = CommandEnvelope.create(CommandId("codec-parent"), SessionEpoch(1), StateVersion(0), null, payload)
        assertEquals(CommandResult.Accepted(1), session.execute(parent))
        val state = port.receipts.getValue(parent.commandId).timeAdvanceState!!
        val suffix = state.pendingSuffix!!
        val outcome = state.sealedElapsedOutcome!!

        assertTrue(PendingBoundarySuffix.decode(suffix.canonicalPayload + " ", suffix.hash) is Checked.Rejected)
        assertTrue(PendingBoundarySuffix.decode(suffix.canonicalPayload, PayloadHash("0".repeat(64))) is Checked.Rejected)
        assertTrue(SealedElapsedOutcome.decode(outcome.encode() + " ", outcome.hash) is Checked.Rejected)
        assertTrue(SealedElapsedOutcome.decode(outcome.encode(), PayloadHash("0".repeat(64))) is Checked.Rejected)
        session.close()
    }

    @Test
    fun `atomic decision branches are individually deterministic and produce different authoritative states`() = runBlocking {
        suspend fun execute(choice: String): Pair<PayloadHash, String> {
            val source = GateSource()
            val port = AtomicPort()
            val actionId = "combat-branch"
            val session = atomicSession(port, atomicEngine(listOf(source), branchingSelection = true), atomicSnapshot(29, listOf(source), actionId))
            val parent = CommandEnvelope.create(CommandId("branch-parent"), SessionEpoch(1), StateVersion(0), null, atomicPayload(actionId, 31))
            assertEquals(CommandResult.Accepted(1), session.execute(parent))
            val pending = port.receipts.getValue(parent.commandId).timeAdvanceState!!
            val childPayload = AtomicElapsedDecisionPayload(
                SessionEpoch(1), parent.commandId, pending.pendingSuffix!!.hash, pending.sealedElapsedOutcome!!.hash,
                "gate-30", choice, "AtomicElapsedDecisionSelection.v1", "{\"choiceId\":${CanonicalJson.string(choice)}}"
            )
            assertEquals(CommandResult.Accepted(2), session.execute(CommandEnvelope.create(CommandId("branch-child"), SessionEpoch(1), StateVersion(1), null, childPayload)))
            val stateHash = session.inMemoryStateHash()
            val aggregate = port.lastDelta!!.aggregateChanges.single().after!!.canonicalJson()
            session.close()
            return stateHash to aggregate
        }

        val a1 = execute("A")
        val a2 = execute("A")
        val b1 = execute("B")
        val b2 = execute("B")
        assertEquals(a1, a2)
        assertEquals(b1, b2)
        assertNotEquals(a1, b1)
        assertNotEquals(a1.second, b1.second)
    }

    @Test
    fun `gate free and gated completion apply the same sealed authoritative aggregate projection`() = runBlocking {
        val actionId = "combat-projection"
        val directPort = AtomicPort()
        val direct = atomicSession(directPort, atomicEngine(emptyList()), atomicSnapshot(29, emptyList(), actionId))
        assertEquals(CommandResult.Accepted(1), direct.execute(CommandEnvelope.create(
            CommandId("projection-direct"), SessionEpoch(1), StateVersion(0), null, atomicPayload(actionId, 31)
        )))
        val directAggregate = directPort.lastDelta!!.aggregateChanges.single().after!!.canonicalJson()
        direct.close()

        val source = GateSource()
        val gatedPort = AtomicPort()
        val gated = atomicSession(gatedPort, atomicEngine(listOf(source), branchingSelection = false), atomicSnapshot(29, listOf(source), actionId))
        val parent = CommandEnvelope.create(CommandId("projection-parent"), SessionEpoch(1), StateVersion(0), null, atomicPayload(actionId, 31))
        assertEquals(CommandResult.Accepted(1), gated.execute(parent))
        assertTrue(gatedPort.lastDelta!!.events.none { it.payload is ElapsedActionAppliedEventPayload })
        val pending = gatedPort.receipts.getValue(parent.commandId).timeAdvanceState!!
        val childPayload = AtomicElapsedDecisionPayload(SessionEpoch(1), parent.commandId, pending.pendingSuffix!!.hash, pending.sealedElapsedOutcome!!.hash, "gate-30", "A", "AtomicElapsedDecisionSelection.v1", "{\"choiceId\":\"A\"}")
        assertEquals(CommandResult.Accepted(2), gated.execute(CommandEnvelope.create(CommandId("projection-child"), SessionEpoch(1), StateVersion(1), null, childPayload)))
        assertEquals(directAggregate, gatedPort.lastDelta!!.aggregateChanges.single().after!!.canonicalJson())
        assertEquals(1, gatedPort.lastDelta!!.events.count { it.payload is ElapsedActionAppliedEventPayload })
        gated.close()
    }

    @Test
    fun `P2-IT-004 target timestamp lifecycle outcome action and economy drafts keep boundary order and commit provenance`() = runBlocking {
        val source = TargetBatchSource()
        val port = AtomicPort()
        val session = atomicSession(port, atomicEngine(listOf(source)), atomicSnapshot(29, listOf(source), "target-order"))
        val envelope = CommandEnvelope.create(CommandId("target-order-command"), SessionEpoch(1), StateVersion(0), null, atomicPayload("target-order", 31))
        assertEquals(CommandResult.Accepted(1), session.execute(envelope))

        val events = port.lastDelta!!.events
        assertEquals(listOf(0L, 1L, 2L, 3L), events.map { it.eventSequence.value })
        assertTrue(events.all { it.sourceCommandId == envelope.commandId && it.sourceVersion == StateVersion(1) })
        assertEquals(
            listOf("calendar.year.started.v1", "elapsed.action.applied.v1", "scheduled.action.completed.v1", "economy.settled.v1"),
            events.map { event -> when (val payload = event.payload) {
                is BoundaryDomainEventPayload -> payload.codecId
                is ElapsedActionAppliedEventPayload -> payload.codecId
                else -> error("unexpected payload")
            } }
        )
        session.close()
    }

    @Test
    fun `forged atomic child lineage hashes and choices halt with zero child effects`() = runBlocking {
        val source = GateSource()
        val port = AtomicPort()
        val session = atomicSession(port, atomicEngine(listOf(source), branchingSelection = true), atomicSnapshot(29, listOf(source), "combat-lineage"))
        val parent = CommandEnvelope.create(CommandId("lineage-parent"), SessionEpoch(1), StateVersion(0), null, atomicPayload("combat-lineage", 31))
        assertEquals(CommandResult.Accepted(1), session.execute(parent))
        val pending = port.receipts.getValue(parent.commandId).timeAdvanceState!!
        val before = session.inMemoryStateHash()
        val writes = port.segmentWrites
        val zeroHash = PayloadHash("0".repeat(64))
        val invalid = listOf(
            AtomicElapsedDecisionPayload(SessionEpoch(1), CommandId("missing-parent"), pending.pendingSuffix!!.hash, pending.sealedElapsedOutcome!!.hash, "gate-30", "A", "AtomicElapsedDecisionSelection.v1", "{\"choiceId\":\"A\"}"),
            AtomicElapsedDecisionPayload(SessionEpoch(1), parent.commandId, zeroHash, pending.sealedElapsedOutcome.hash, "gate-30", "A", "AtomicElapsedDecisionSelection.v1", "{\"choiceId\":\"A\"}"),
            AtomicElapsedDecisionPayload(SessionEpoch(1), parent.commandId, pending.pendingSuffix.hash, pending.sealedElapsedOutcome.hash, "gate-30", "Z", "AtomicElapsedDecisionSelection.v1", "{\"choiceId\":\"Z\"}"),
            AtomicElapsedDecisionPayload(SessionEpoch(1), parent.commandId, pending.pendingSuffix.hash, pending.sealedElapsedOutcome.hash, "gate-30", "A", "unknown.selection.v1", "{\"choiceId\":\"A\"}")
        )

        invalid.forEachIndexed { index, childPayload ->
            val result = session.execute(CommandEnvelope.create(CommandId("invalid-child-$index"), SessionEpoch(1), StateVersion(1), null, childPayload))
            assertTrue((result as CommandResult.Rejected).error is DomainError.SystemHalted)
            assertEquals(writes, port.segmentWrites)
            assertEquals(before, session.inMemoryStateHash())
        }
        session.close()
    }

    @Test
    fun `atomic branches are deterministic and enforce the whole prefix branch boundary budget`() {
        val source = GateSource(includeMinuteOne = true)
        val before = atomicSnapshot(0, listOf(source), "budget")
        val engine = atomicEngine(listOf(source), branchingSelection = true)

        fun result(limit: Int, choice: String): ExecutionPlan {
            val payload = atomicPayload("budget", 31, limit)
            return engine.plan(CommandEnvelope.create(CommandId("budget-$limit-$choice"), SessionEpoch(1), StateVersion(0), null, payload), before, StateVersion(1), 1)
        }

        assertTrue((result(2, "A") as ExecutionPlan.Atomic).delta.result is CommandResult.Rejected)
        assertTrue(result(3, "A") is ExecutionPlan.Segment)
        assertTrue(result(4, "A") is ExecutionPlan.Segment)
        val a = result(3, "A") as ExecutionPlan.Segment
        val b = result(3, "B") as ExecutionPlan.Segment
        assertEquals(a.timeAdvanceState.sealedElapsedOutcome, b.timeAdvanceState.sealedElapsedOutcome)
        assertEquals(a.delta.rngState, b.delta.rngState)
    }

    @Test
    fun `atomic sealed preflight accepts one through eight choices and rejects nine or a nested gate`() {
        fun plan(source: GateSource, id: String): ExecutionPlan {
            val engine = atomicEngine(listOf(source), branchingSelection = true)
            return engine.plan(
                CommandEnvelope.create(CommandId(id), SessionEpoch(1), StateVersion(0), null, atomicPayload(id, 31, 8)),
                atomicSnapshot(29, listOf(source), id), StateVersion(1), 1
            )
        }
        assertTrue(plan(GateSource(choices = listOf("A")), "choice-1") is ExecutionPlan.Segment)
        assertTrue(plan(GateSource(choices = (1..8).map { "C$it" }), "choice-8") is ExecutionPlan.Segment)
        assertThrows(IllegalArgumentException::class.java) {
            plan(GateSource(choices = (1..9).map { "C$it" }), "choice-9")
        }
        assertTrue((plan(GateSource(secondGate = true), "nested-gate") as ExecutionPlan.Atomic).delta.result is CommandResult.Rejected)
        val diamondSource = GateSource()
        assertTrue(atomicEngine(listOf(diamondSource), branchingSelection = false).plan(
            CommandEnvelope.create(CommandId("bounded-diamond"), SessionEpoch(1), StateVersion(0), null, atomicPayload("bounded-diamond", 31, 8)),
            atomicSnapshot(29, listOf(diamondSource), "bounded-diamond"), StateVersion(1), 1
        ) is ExecutionPlan.Segment)
    }

    @Test
    fun `missing rng and forged outcome halt without writing state or publication`() = runBlocking {
        val payload = atomicPayload("combat-invalid", 31)
        val cases = listOf(
            atomicEngine(emptyList()) to atomicSnapshot(29, emptyList(), "combat-invalid").copy(rngState = RngState(emptyList())),
            WorldEngine(emptyList()).useAtomicElapsedOutcomeCalculatorForConformance(AtomicElapsedOutcomeCalculator { _, request, rng ->
                Checked.Value(AtomicElapsedOutcomeResult(SealedElapsedOutcome("combat.result.v1", "{}", minute(request.targetMinute.value + 1), request.actionId), rng))
            }) to atomicSnapshot(29, emptyList(), "combat-invalid")
        )
        for ((engine, initial) in cases) {
            val port = AtomicPort()
            val session = atomicSession(port, engine, initial)
            val beforeHash = session.inMemoryStateHash()
            val result = session.execute(CommandEnvelope.create(CommandId("invalid-${port.hashCode()}"), SessionEpoch(1), StateVersion(0), null, payload))
            assertTrue((result as CommandResult.Rejected).error is DomainError.SystemHalted)
            assertEquals(0, port.atomicWrites + port.segmentWrites)
            assertEquals(beforeHash, session.inMemoryStateHash())
            assertEquals(null, session.publications.value)
            assertEquals(SessionLifecycle.PAUSED, session.runtimeState.value.lifecycle)
            assertTrue((session.execute(CommandEnvelope.create(
                CommandId("after-halt-${port.hashCode()}"), SessionEpoch(1), StateVersion(0), null,
                UnsupportedFeaturePayload("after-halt")
            )) as CommandResult.Rejected).error is DomainError.SystemHalted)
            assertEquals(SessionLifecycle.PAUSED, session.resume().lifecycle)
            session.close()
        }
    }

    @Test
    fun `queued atomic command is rejected after a prior system halt`() = runBlocking {
        val first = CommandEnvelope.create(
            CommandId("halt-queue-first"), SessionEpoch(1), StateVersion(0), null,
            AtomicElapsedDecisionPayload(
                SessionEpoch(1), CommandId("missing-parent"), PayloadHash("0".repeat(64)),
                PayloadHash("0".repeat(64)), "gate", "choice", "AtomicElapsedDecisionSelection.v1", "{\"choiceId\":\"choice\"}"
            )
        )
        val port = HaltBarrierPort(first.commandId)
        val session = WorldSession(
            SessionEpoch(1), port, this, ContentSnapshot.emptyForTest(),
            atomicSnapshot(0, emptyList(), "halt-queue"), WorldEngine(emptyList()), kotlinx.coroutines.Dispatchers.Default
        )
        val before = session.inMemoryStateHash()

        val firstJob = async(start = CoroutineStart.UNDISPATCHED) { session.execute(first) }
        port.entered.await()
        val atomicFollowerJob = async(start = CoroutineStart.UNDISPATCHED) {
            session.execute(CommandEnvelope.create(
                CommandId("halt-queue-second"), SessionEpoch(1), StateVersion(0), null,
                AtomicElapsedDecisionPayload(
                    SessionEpoch(1), CommandId("missing-follower-parent"), PayloadHash("0".repeat(64)),
                    PayloadHash("0".repeat(64)), "gate", "choice", "AtomicElapsedDecisionSelection.v1", "{\"choiceId\":\"choice\"}"
                )
            ))
        }
        val advanceFollower = CommandEnvelope.create(
            CommandId("halt-queue-advance"), SessionEpoch(1), StateVersion(0), null,
            AdvanceTimePayload(TimeAdvanceGoal.UntilMinute(minute(1)), ProgressionMode.FAST_FORWARD, TimeTraversalLimits(minute(1), 8))
        )
        val advanceFollowerJob = async(start = CoroutineStart.UNDISPATCHED) { session.execute(advanceFollower) }
        assertEquals(advanceFollower.commandId, session.runtimeState.value.activeAdvanceCommandId)
        port.release.complete(Unit)

        assertTrue((firstJob.await() as CommandResult.Rejected).error is DomainError.SystemHalted)
        assertTrue((session.requestControl(ControlRequest(
            SessionEpoch(1), CommandId("halt-queue-control"), AdvanceControl.PAUSE, allowCommitDrain = false
        )) as ControlRequestResult.Rejected).error is DomainError.SystemHalted)
        assertTrue((atomicFollowerJob.await() as CommandResult.Rejected).error is DomainError.SystemHalted)
        assertTrue((advanceFollowerJob.await() as CommandResult.Rejected).error is DomainError.SystemHalted)
        assertEquals(listOf(first.commandId, CommandId("missing-parent")), port.findReceiptCalls)
        assertEquals(before, session.inMemoryStateHash())
        assertEquals(0, port.writes)
        assertTrue(port.receipts.isEmpty())
        assertTrue(port.committedEvents.isEmpty())
        assertEquals(null, session.publications.value)
        assertEquals(null, session.runtimeState.value.activeAdvanceCommandId)
        assertEquals(SessionLifecycle.PAUSED, session.runtimeState.value.lifecycle)
        assertTrue((session.execute(CommandEnvelope.create(
            CommandId("halt-queue-after"), SessionEpoch(1), StateVersion(0), null, UnsupportedFeaturePayload("after-halt")
        )) as CommandResult.Rejected).error is DomainError.SystemHalted)
        session.close()
    }

    @Test
    fun `new advance preflight halt clears admission state before its reply`() = runBlocking {
        val source = object : BoundarySource {
            override val sourceId = "preflight"
            override fun nextTimeAfter(snapshot: WorldTraversalSnapshot, cursor: BoundaryCursor?) = minute(1)
            override fun candidatesAt(snapshot: WorldTraversalSnapshot, time: GameMinute) = listOf(
                BoundaryCandidate(
                    BoundaryKey(time, BoundaryCategory.WORLD_EVENT, 0, 0, "bad", "", sourceId),
                    "unknown.kind.v1", "unregistered.v1", "{}"
                )
            )
        }
        val payload = AdvanceTimePayload(TimeAdvanceGoal.UntilMinute(minute(1)), ProgressionMode.FAST_FORWARD, TimeTraversalLimits(minute(1), 8))
        val command = CommandEnvelope.create(CommandId("preflight-halt"), SessionEpoch(1), StateVersion(0), null, payload)
        val initial = snapshot(0).copy(world = snapshot(0).world.copy(
            boundaryBinding = BoundaryRegistryBinding(1, listOf(source.sourceId), candidateCodecs = setOf("registered.v1"))
        ))
        val port = HaltBarrierPort(CommandId("not-blocked"))
        val session = WorldSession(
            SessionEpoch(1), port, this, ContentSnapshot.emptyForTest(), initial,
            WorldEngine(listOf(source)), kotlinx.coroutines.Dispatchers.Default
        )
        val before = session.inMemoryStateHash()

        assertTrue((session.execute(command) as CommandResult.Rejected).error is DomainError.SystemHalted)
        assertEquals(listOf(command.commandId), port.findReceiptCalls)
        assertEquals(null, session.runtimeState.value.activeAdvanceCommandId)
        assertEquals(SessionLifecycle.PAUSED, session.runtimeState.value.lifecycle)
        assertEquals(SessionLifecycle.PAUSED, session.resume().lifecycle)
        assertTrue((session.requestControl(ControlRequest(
            SessionEpoch(1), command.commandId, AdvanceControl.PAUSE, allowCommitDrain = false
        )) as ControlRequestResult.Rejected).error is DomainError.SystemHalted)
        assertTrue((session.execute(CommandEnvelope.create(
            CommandId("preflight-after"), SessionEpoch(1), StateVersion(0), null, UnsupportedFeaturePayload("after-halt")
        )) as CommandResult.Rejected).error is DomainError.SystemHalted)
        assertEquals(before, session.inMemoryStateHash())
        assertEquals(0, port.writes)
        assertTrue(port.receipts.isEmpty())
        assertTrue(port.committedEvents.isEmpty())
        assertEquals(null, session.publications.value)
        session.close()
    }

    @Test
    fun `atomic synthetic source binding and sealed codec are mandatory and caller collision is rejected`() = runBlocking {
        val base = atomicSnapshot(29, emptyList(), "binding-proof")
        val invalidBindings = listOf(
            base.world.boundaryBinding.copy(sourceIds = emptyList()),
            base.world.boundaryBinding.copy(candidateCodecs = setOf("AtomicTestPayload.v1"))
        )
        invalidBindings.forEachIndexed { index, binding ->
            val port = AtomicPort()
            val session = atomicSession(port, atomicEngine(emptyList()), base.copy(world = base.world.copy(boundaryBinding = binding)))
            val result = session.execute(CommandEnvelope.create(CommandId("binding-$index"), SessionEpoch(1), StateVersion(0), null, atomicPayload("binding-proof", 31)))
            assertTrue((result as CommandResult.Rejected).error is DomainError.SystemHalted)
            assertEquals(0, port.atomicWrites + port.segmentWrites)
            session.close()
        }
        assertThrows(IllegalArgumentException::class.java) {
            base.world.boundaryBinding.copy(candidateCodecs = emptySet())
        }
        val collision = object : BoundarySource {
            override val sourceId = "elapsed.action"
            override fun nextTimeAfter(snapshot: WorldTraversalSnapshot, cursor: BoundaryCursor?) = null
            override fun candidatesAt(snapshot: WorldTraversalSnapshot, time: GameMinute) = emptyList<BoundaryCandidate>()
        }
        assertThrows(IllegalArgumentException::class.java) { WorldEngine(listOf(collision)) }
        Unit
    }

    @Test
    fun `selected decision choice participates in the authoritative in memory state hash`() = runBlocking {
        val base = atomicSnapshot(29, emptyList(), "choice-hash")
        fun withChoice(choice: String) = base.copy(world = base.world.copy(timeAdvance = TimeAdvanceState(
            TimeAdvanceGoal.UntilMinute(minute(31)), null, 0, TimeAdvanceResult.COMPLETED,
            selectedDecisionChoiceId = choice
        )))
        val first = atomicSession(AtomicPort(), atomicEngine(emptyList()), withChoice("A"))
        val second = atomicSession(AtomicPort(), atomicEngine(emptyList()), withChoice("B"))
        assertNotEquals(first.inMemoryStateHash(), second.inMemoryStateHash())
        first.close()
        second.close()
        Unit
    }

    @Test
    fun `P2-CT-004 atomic child response loss reconciles the exact continuation and does not double apply`() = runBlocking {
        val source = GateSource()
        val port = AtomicPort(cancelAfterChildCommit = true)
        val session = atomicSession(port, atomicEngine(listOf(source), branchingSelection = true), atomicSnapshot(29, listOf(source), "combat-reconcile"))
        val payload = atomicPayload("combat-reconcile", 31)
        val parent = CommandEnvelope.create(CommandId("reconcile-parent"), SessionEpoch(1), StateVersion(0), null, payload)
        assertEquals(CommandResult.Accepted(1), session.execute(parent))
        val pending = port.receipts.getValue(parent.commandId).timeAdvanceState!!
        val childPayload = AtomicElapsedDecisionPayload(SessionEpoch(1), parent.commandId, pending.pendingSuffix!!.hash, pending.sealedElapsedOutcome!!.hash, "gate-30", "A", "AtomicElapsedDecisionSelection.v1", "{\"choiceId\":\"A\"}")
        val child = CommandEnvelope.create(CommandId("reconcile-child"), SessionEpoch(1), StateVersion(1), null, childPayload)

        assertTrue(runCatching { session.execute(child) }.exceptionOrNull() is CancellationException)
        val afterLoss = session.inMemoryStateHash()
        assertEquals(31, session.publications.value!!.snapshot.clock.minute.value)
        assertEquals(1, (port.lastDelta!!.aggregateChanges.single().after as AtomicResultAggregate).applyCount)
        assertEquals(1, port.lastDelta!!.events.count { it.payload is ElapsedActionAppliedEventPayload })
        assertEquals(CommandResult.Accepted(2), session.execute(child))
        assertEquals(afterLoss, session.inMemoryStateHash())
        assertEquals(2, port.segmentWrites)
        session.close()
    }

    private class GateSource(
        private val includeMinuteOne: Boolean = false,
        private val choices: List<String> = listOf("A", "B"),
        private val secondGate: Boolean = false
    ) : BoundarySource {
        override val sourceId = "atomic-test"

        override fun nextTimeAfter(snapshot: WorldTraversalSnapshot, cursor: BoundaryCursor?): GameMinute? = listOfNotNull(
            minute(1).takeIf { includeMinuteOne && it.value > snapshot.clock.minute.value },
            minute(30).takeIf { it.value > snapshot.clock.minute.value },
            minute(31).takeIf { secondGate && it.value > snapshot.clock.minute.value }
        ).minByOrNull(GameMinute::value)

        override fun candidatesAt(snapshot: WorldTraversalSnapshot, time: GameMinute): List<BoundaryCandidate> = when (time.value) {
            1L -> listOf(candidate("warmup-1", 1, BoundaryCategory.WORLD_EVENT, 0, BoundaryDisposition.CONTINUE))
            30L -> listOf(
                candidate("prefix-30", 30, BoundaryCategory.MANDATORY_DECISION_PREREQUISITE, 0, BoundaryDisposition.CONTINUE),
                candidate("gate-30", 30, BoundaryCategory.MANDATORY_DECISION_PREREQUISITE, 1, BoundaryDisposition.DECISION_GATE, choices),
                candidate("suffix-30", 30, BoundaryCategory.INFORMATION, 0, BoundaryDisposition.CONTINUE)
            )
            31L -> if (secondGate) {
                listOf(candidate("gate-31", 31, BoundaryCategory.MANDATORY_DECISION_PREREQUISITE, 0, BoundaryDisposition.DECISION_GATE, listOf("X")))
            } else emptyList()
            else -> emptyList()
        }

        private fun candidate(
            id: String,
            at: Long,
            category: BoundaryCategory,
            priority: Int,
            disposition: BoundaryDisposition,
            choices: List<String> = listOf("continue")
        ) = BoundaryCandidate(
            BoundaryKey(minute(at), category, priority, 0, id, "", sourceId),
            if (id.startsWith("prefix")) "calendar.day.start.v1" else if (id.startsWith("gate")) "decision.required.v1" else "calendar.month.start.v1",
            "AtomicTestPayload.v1",
            "{\"id\":${CanonicalJson.string(id)}}",
            disposition = disposition,
            decisionChoiceIds = choices
        )
    }

    private class TargetBatchSource : BoundarySource {
        override val sourceId: String = "target-batch"
        override fun nextTimeAfter(snapshot: WorldTraversalSnapshot, cursor: BoundaryCursor?): GameMinute? =
            minute(31).takeIf { it.value > snapshot.clock.minute.value }

        override fun candidatesAt(snapshot: WorldTraversalSnapshot, time: GameMinute): List<BoundaryCandidate> = listOf(
            candidate("target-lifecycle", BoundaryCategory.LIFECYCLE, "calendar.year.start.v1"),
            candidate("target-action", BoundaryCategory.SCHEDULED_ACTION_COMPLETE, "scheduled.action.complete.v1"),
            candidate("target-economy", BoundaryCategory.ECONOMY_SETTLEMENT, "economy.settlement.v1")
        )

        private fun candidate(id: String, category: BoundaryCategory, kind: String) = BoundaryCandidate(
            BoundaryKey(minute(31), category, 0, 0, id, "", sourceId),
            kind,
            "AtomicTestPayload.v1",
            "{\"id\":${CanonicalJson.string(id)}}"
        )
    }

    private data class AtomicResultAggregate(
        override val aggregateId: EntityId,
        val applyCount: Int,
        val outcomeHash: PayloadHash?,
        val selectedChoice: String?
    ) : AggregateState {
        override val aggregateType: String = "atomic-result-conformance.v1"
        override fun canonicalJson(): String =
            "{\"aggregateId\":${CanonicalJson.string(aggregateId.value)},\"applyCount\":$applyCount,\"outcomeHash\":${outcomeHash?.let { CanonicalJson.string(it.value) } ?: "null"},\"selectedChoice\":${selectedChoice?.let(CanonicalJson::string) ?: "null"}}"
    }

    private class AtomicPort(private val cancelAfterChildCommit: Boolean = false) : SavePort {
        val receipts = mutableMapOf<CommandId, PersistedReceipt>()
        private val claimed = mutableSetOf<Pair<SessionEpoch, CommandId>>()
        var atomicWrites = 0
        var segmentWrites = 0
        var lastDelta: DomainDelta? = null
        var lastContinuation: TimeAdvanceContinuation? = null
        private var version = 0L
        private var cancelled = false

        override suspend fun findReceipt(sessionEpoch: SessionEpoch, commandId: CommandId): PersistedReceipt? = receipts[commandId]

        override suspend fun commit(envelope: CommandEnvelope<out WorldCommandPayload>, delta: DomainDelta): CommitReceipt {
            atomicWrites++
            lastDelta = delta
            if (delta.result is CommandResult.Accepted) version++
            return CommitReceipt(StateVersion(version), delta.result).also {
                receipts[envelope.commandId] = PersistedReceipt(envelope.payloadHash, it.stateVersion, it.result)
            }
        }

        override suspend fun commitSegment(
            envelope: CommandEnvelope<out WorldCommandPayload>,
            expectedSegmentNo: Int,
            delta: DomainDelta,
            timeAdvanceState: TimeAdvanceState,
            terminalResult: TimeAdvanceResult?,
            continuation: TimeAdvanceContinuation?
        ): SegmentCommitReceipt {
            continuation?.let {
                require(it.childCommandId == envelope.commandId && receipts[it.predecessorCommandId]?.lifecycleStatus == ReceiptLifecycle.INTERRUPTED)
                require(claimed.add(it.predecessorEpoch to it.predecessorCommandId))
            }
            segmentWrites++
            lastDelta = delta
            lastContinuation = continuation
            version++
            val lifecycle = when (terminalResult) {
                TimeAdvanceResult.DECISION_REQUIRED, TimeAdvanceResult.INTERRUPTED, TimeAdvanceResult.FAILED -> ReceiptLifecycle.INTERRUPTED
                null -> ReceiptLifecycle.RUNNING
                else -> ReceiptLifecycle.COMMITTED
            }
            val receipt = CommitReceipt(StateVersion(version), delta.result, lifecycle, expectedSegmentNo)
            val persisted = PersistedReceipt(envelope.payloadHash, receipt.stateVersion, receipt.result, lifecycle, expectedSegmentNo, timeAdvanceState, continuation)
            receipts[envelope.commandId] = persisted
            if (cancelAfterChildCommit && continuation != null && !cancelled) {
                cancelled = true
                throw CancellationException("lost atomic child response")
            }
            return SegmentCommitReceipt(receipt, expectedSegmentNo, timeAdvanceState)
        }
    }

    private class SegmentPort : SavePort {
        var segmentCommits = 0
        val committedMinutes = mutableListOf<Long>()
        val committedEventSequences = mutableListOf<List<Long>>()
        val committedEvents = mutableListOf<List<DomainEvent<out DomainEventPayload>>>()
        override suspend fun findReceipt(sessionEpoch: SessionEpoch, commandId: CommandId): PersistedReceipt? = null
        override suspend fun commit(envelope: CommandEnvelope<out WorldCommandPayload>, delta: DomainDelta): CommitReceipt = error("atomic commit was not expected")
        override suspend fun commitSegment(
            envelope: CommandEnvelope<out WorldCommandPayload>,
            expectedSegmentNo: Int,
            delta: DomainDelta,
            timeAdvanceState: TimeAdvanceState,
            terminalResult: TimeAdvanceResult?,
            continuation: TimeAdvanceContinuation?
        ): SegmentCommitReceipt {
            segmentCommits++
            committedMinutes += delta.worldChange!!.after.clock.minute.value
            committedEventSequences += delta.events.map { it.eventSequence.value }
            committedEvents += delta.events
            val lifecycle = if (terminalResult == null) ReceiptLifecycle.RUNNING else ReceiptLifecycle.COMMITTED
            return SegmentCommitReceipt(
                CommitReceipt(StateVersion((expectedSegmentNo + 1).toLong()), delta.result, lifecycle, expectedSegmentNo),
                expectedSegmentNo,
                timeAdvanceState
            )
        }
    }

    private class HaltBarrierPort(private val blockedCommandId: CommandId) : SavePort {
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val findReceiptCalls = mutableListOf<CommandId>()
        val receipts = mutableMapOf<CommandId, PersistedReceipt>()
        val committedEvents = mutableListOf<List<DomainEvent<out DomainEventPayload>>>()
        var writes = 0

        override suspend fun findReceipt(sessionEpoch: SessionEpoch, commandId: CommandId): PersistedReceipt? {
            findReceiptCalls += commandId
            if (commandId == blockedCommandId) {
                entered.complete(Unit)
                release.await()
            }
            return null
        }

        override suspend fun commit(envelope: CommandEnvelope<out WorldCommandPayload>, delta: DomainDelta): CommitReceipt {
            writes++
            committedEvents += delta.events
            return CommitReceipt(StateVersion(writes.toLong()), delta.result).also { receipt ->
                receipts[envelope.commandId] = PersistedReceipt(envelope.payloadHash, receipt.stateVersion, receipt.result)
            }
        }
    }

    private class SchedulePort(private val failCommit: Boolean = false) : SavePort {
        var commits = 0
        private var version = 0L
        override suspend fun findReceipt(sessionEpoch: SessionEpoch, commandId: CommandId): PersistedReceipt? = null
        override suspend fun commit(envelope: CommandEnvelope<out WorldCommandPayload>, delta: DomainDelta): CommitReceipt {
            if (failCommit) throw IllegalStateException("injected")
            commits++
            if (delta.result is CommandResult.Accepted) version++
            return CommitReceipt(StateVersion(version), delta.result)
        }
    }

    private companion object {
        fun atomicPayload(
            id: String,
            target: Long,
            maxBoundaries: Int = 8,
            kind: AtomicElapsedActionKind = AtomicElapsedActionKind.COMBAT
        ) = AtomicElapsedActionPayload(
            kind,
            entity(id),
            kind.mode,
            minute(target),
            "combat.input.v1",
            "{\"attack\":1}",
            TimeTraversalLimits(minute(target), maxBoundaries)
        )

        fun atomicEngine(sources: List<BoundarySource>, branchingSelection: Boolean = false, numericOutcomePayload: Boolean = false): WorldEngine {
            val evaluator = boundaryEvaluator { snapshot, candidate ->
                if (candidate.candidateKind == "calendar.day.start.v1") {
                    val action = ScheduledAction(
                        entity("prefix-proof"), "atomic.prefix.v1", actionPayload(SchedulePriority.OFFICIAL_OPERATION), minute(candidate.key.boundaryTime.value + 1), minute(candidate.key.boundaryTime.value + 2), ScheduledActionStatus.PLANNED
                    )
                    snapshot.copy(calendar = snapshot.calendar.copy(actions = snapshot.calendar.actions + action))
                } else snapshot
            }
            val selection = DecisionSelectionEvaluator { snapshot, selected ->
                if (!branchingSelection) snapshot else {
                    val aggregate = snapshot.aggregates.values.single() as AtomicResultAggregate
                    snapshot.copy(aggregates = snapshot.aggregates + (aggregate.aggregateId to aggregate.copy(selectedChoice = selected.choiceId)))
                }
            }
            return WorldEngine(sources, evaluator, selection).useAtomicElapsedOutcomeCalculatorForConformance(
                AtomicElapsedOutcomeCalculator { _, payload, rng ->
                    when (val draw = DeterministicRng.draw(rng, RngOperation.NextUInt32)) {
                        is Checked.Rejected -> draw
                        is Checked.Value -> Checked.Value(
                            AtomicElapsedOutcomeResult(
                                SealedElapsedOutcome(
                                    "combat.result.v1",
                                    if (numericOutcomePayload) draw.value.value.toString() else "{\"actionId\":${CanonicalJson.string(payload.actionId.value)},\"roll\":${draw.value.value}}",
                                    payload.targetMinute,
                                    payload.actionId
                                ),
                                draw.value.stream
                            )
                        )
                    }
                }
            ).useAtomicElapsedOutcomeApplierForConformance(AtomicElapsedOutcomeApplier { snapshot, outcome ->
                val current = snapshot.aggregates[outcome.domainResultId] as? AtomicResultAggregate
                    ?: throw IllegalStateException("missing atomic conformance aggregate")
                require(current.applyCount == 0 && current.outcomeHash == null)
                snapshot.copy(aggregates = snapshot.aggregates + (outcome.domainResultId to current.copy(applyCount = 1, outcomeHash = outcome.hash)))
            })
        }

        fun atomicSnapshot(
            at: Long,
            sources: List<BoundarySource>,
            actionId: String,
            kind: AtomicElapsedActionKind = AtomicElapsedActionKind.COMBAT
        ): WorldSnapshot {
            val leaf = when (kind) {
                AtomicElapsedActionKind.COMBAT -> RngLeaf.COMBAT_HIT
                AtomicElapsedActionKind.DUNGEON -> RngLeaf.DUNGEON
                AtomicElapsedActionKind.NORMAL -> RngLeaf.WORLD_EVENT
            }
            val key = if (leaf == RngLeaf.WORLD_EVENT) canonicalRngStreamKey(leaf) else canonicalRngStreamKey(leaf, actionId)
            return WorldSnapshot(
                StateVersion(0),
                AuthoritativeWorldState(
                    WorldClock(minute(at)), ScheduleCalendar(emptyList(), emptyMap()), null,
                    BoundaryRegistryBinding(1, (sources.map(BoundarySource::sourceId) + "elapsed.action").sorted(), candidateCodecs = setOf("AtomicTestPayload.v1", SealedElapsedOutcome.CODEC_ID))
                ),
                RngState(listOf(DeterministicRng.initialize(42, 54, key))),
                mapOf(entity(actionId) to AtomicResultAggregate(entity(actionId), 0, null, null))
            )
        }

        fun atomicSnapshotWithWorldSeed(at: Long, sources: List<BoundarySource>, actionId: String, worldSeed: String): WorldSnapshot {
            val key = canonicalRngStreamKey(RngLeaf.COMBAT_HIT, actionId)
            val stream = when (val seeded = DeterministicRng.seeded(worldSeed, key)) {
                is Checked.Value -> seeded.value
                is Checked.Rejected -> error(seeded.error.toString())
            }
            return WorldSnapshot(
                StateVersion(0),
                AuthoritativeWorldState(
                    WorldClock(minute(at)), ScheduleCalendar(emptyList(), emptyMap()), null,
                    BoundaryRegistryBinding(1, (sources.map(BoundarySource::sourceId) + "elapsed.action").sorted(), candidateCodecs = setOf("CalendarBoundaryPayload.v1", SealedElapsedOutcome.CODEC_ID))
                ),
                RngState(listOf(stream)),
                mapOf(entity(actionId) to AtomicResultAggregate(entity(actionId), 0, null, null))
            )
        }

        fun kotlinx.coroutines.CoroutineScope.atomicSession(port: SavePort, engine: WorldEngine, snapshot: WorldSnapshot) = WorldSession(
            SessionEpoch(1), port, this, ContentSnapshot.emptyForTest(), snapshot, engine, kotlinx.coroutines.Dispatchers.Unconfined
        )

        fun kotlinx.coroutines.CoroutineScope.scheduleSession(port: SavePort, calendar: ScheduleCalendar = ScheduleCalendar(emptyList(), emptyMap())) = WorldSession(
            SessionEpoch(1), port, this, ContentSnapshot.emptyForTest(),
            WorldSnapshot(StateVersion(0), AuthoritativeWorldState(WorldClock(minute(0)), calendar, null, BoundaryRegistryBinding(1, emptyList())), RngState(emptyList()), emptyMap()),
            WorldEngine(emptyList()), kotlinx.coroutines.Dispatchers.Unconfined
        )

        fun reservePayload(id: String, start: Long, due: Long, priority: SchedulePriority = SchedulePriority.TREATMENT) =
            ScheduleReservePayload(entity(id), "schedule.action", actionPayload(priority), minute(start), minute(due), minute(0))

        fun actionPayload(priority: SchedulePriority, canBePreempted: Boolean = false): ScheduledActionPayload {
            val actor = entity("rhea")
            val rules = CancellationStage.entries.associateWith { ActionCancellationRule(0, 0, true) }
            return ScheduledActionPayload(
                "MERCENARY", actor, listOf(ScheduledEntityRef("MERCENARY", actor)), priority, emptyList(),
                ActionKindPolicyProfile(true, "MINUTE", ActionInterruptionPolicy(ActionInterruptionResult.CONTINUE), ActionCancellationPolicy(rules), "schedule.effect.v1"),
                "schedule.completed", "schedule.completed.v1", canBePreempted
            )
        }

        fun snapshot(minute: Long): WorldSnapshot = WorldSnapshot(
            StateVersion(0),
            AuthoritativeWorldState(
                WorldClock(this.minute(minute)),
                ScheduleCalendar(emptyList(), emptyMap()),
                null,
                BoundaryRegistryBinding(1, listOf("calendar", "scheduled-action"), candidateCodecs = setOf("CalendarBoundaryPayload.v1", "ScheduledActionPayload.v1"))
            ),
            RngState(emptyList()),
            emptyMap()
        )

        fun minute(value: Long): GameMinute = when (val checked = GameMinute.of(value)) {
            is Checked.Value -> checked.value
            is Checked.Rejected -> error(checked.error.toString())
        }
        fun entity(value: String): EntityId = when (val checked = EntityId.of(value)) {
            is Checked.Value -> checked.value
            is Checked.Rejected -> error(checked.error.toString())
        }
        fun lifecycle(result: TimeAdvanceResult?): ReceiptLifecycle = if (result == null) ReceiptLifecycle.RUNNING else ReceiptLifecycle.COMMITTED
    }
}
