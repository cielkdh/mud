package com.imsi.mud.simulation

import com.imsi.mud.content.ContentSnapshot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import java.util.Base64

enum class SavePortFaultPoint { BEFORE_COMMIT, DURING_COMMIT, AFTER_COMMIT }

data class SavePortConformanceImage(
    val stateVersion: StateVersion,
    val world: AuthoritativeWorldState,
    val rngState: RngState,
    val aggregatePayloads: Map<EntityId, String>,
    val aggregateStates: Map<EntityId, AggregateState>,
    val encodedEventsBase64: List<String>,
    val receipts: Map<Pair<SessionEpoch, CommandId>, PersistedReceipt>,
    val writeCount: Int
)

/** Test-only adapter hook. Production runtime must not register this type. */
interface SavePortConformanceHarness {
    val port: SavePort
    fun image(): SavePortConformanceImage
    fun armFault(point: SavePortFaultPoint)
    fun restart(): SavePortConformanceHarness
}

fun interface SavePortConformanceFactory {
    fun create(): SavePortConformanceHarness
}

/** JUnit-free so Phase 3 can rerun the same contract by supplying its Room-backed factory. */
object SavePortConformanceSuite {
    suspend fun verify(factory: SavePortConformanceFactory) {
        verifyAtomicityAndIdempotency(factory)
        verifySegmentCursorAndContinuation(factory)
        verifyCompleteOrPrevious(factory)
        verifyRestartCutpoints(factory)
        verifySessionPublicationAndLifecycle(factory)
    }

    private suspend fun verifyAtomicityAndIdempotency(factory: SavePortConformanceFactory) {
        val harness = factory.create()
        val before = harness.image()
        val envelope = envelope("atomic", "same", before.stateVersion)
        val delta = atomicDelta(before, envelope, 1)

        val first = harness.port.commit(envelope, delta)
        val committed = harness.image()
        check(first.stateVersion == StateVersion(1) && committed.writeCount == before.writeCount + 1)
        check(committed.world.clock.minute == minute(1))
        check(committed.rngState.streams.single().drawCounter == 1L)
        check(committed.aggregatePayloads[entity("aggregate")] == "{\"value\":1}")
        checkEvents(committed, listOf(atomicEvent(envelope, StateVersion(1))))

        check(harness.port.commit(envelope, delta) == first)
        check(harness.image() == committed)

        val conflict = envelope("atomic", "different", before.stateVersion)
        val rejected = runCatching { harness.port.commit(conflict, delta) }
        check(rejected.isFailure || (rejected.getOrNull()?.result as? CommandResult.Rejected)?.error is DomainError.IdempotencyKeyReuse)
        check(harness.image() == committed)
    }

    private suspend fun verifySegmentCursorAndContinuation(factory: SavePortConformanceFactory) {
        val harness = factory.create()
        val initial = harness.image()
        val envelope = advanceEnvelope("segments", 2, initial.stateVersion)
        val running = advanceState(envelope, 0, null).copy(nextEventSequence = 1)
        val admission = segmentDelta(initial, running, 1, 0)

        val segment0 = harness.port.commitSegment(envelope, 0, admission, running, null)
        check(segment0.receipt.lifecycleStatus == ReceiptLifecycle.RUNNING)
        check(segment0.receipt.lastCommittedSegmentNo == 0)
        val after0 = harness.image()
        checkEvents(after0, listOf(segmentEvent(initial, running, 0)))
        check(harness.port.commitSegment(envelope, 0, admission, running, null) == segment0)
        check(harness.image() == after0)

        val skipped = running.copy(segmentNo = 2)
        check(runCatching { harness.port.commitSegment(envelope, 2, segmentDelta(after0, skipped, 1, 1), skipped, null) }.isFailure)
        check(harness.image() == after0)

        val interrupted = running.copy(status = TimeAdvanceResult.INTERRUPTED, segmentNo = 1, nextEventSequence = 2)
        val secondDelta = segmentDelta(after0, interrupted, 1, 1)
        harness.armFault(SavePortFaultPoint.AFTER_COMMIT)
        check(runCatching {
            harness.port.commitSegment(envelope, 1, secondDelta, interrupted, TimeAdvanceResult.INTERRUPTED)
        }.isFailure)
        val after1 = harness.image()
        checkEvents(
            after1,
            listOf(segmentEvent(initial, running, 0), segmentEvent(after0, interrupted, 1))
        )
        val reconciled = checkNotNull(harness.port.findReceipt(envelope.sessionEpoch, envelope.commandId))
        check(reconciled.lifecycleStatus == ReceiptLifecycle.INTERRUPTED)
        check(reconciled.lastCommittedSegmentNo == 1)
        val replay = harness.port.commitSegment(
            envelope, 1, secondDelta, interrupted, TimeAdvanceResult.INTERRUPTED
        )
        check(replay.receipt.stateVersion == reconciled.stateVersion)
        check(harness.image() == after1)

        verifyContinuation(factory)
    }

    private suspend fun verifyContinuation(factory: SavePortConformanceFactory) {
        val harness = factory.create()
        val initial = harness.image()
        val parentEnvelope = advanceEnvelope("parent", 2, initial.stateVersion)
        val suffix = PendingBoundarySuffix(emptyList())
        val parentState = advanceState(parentEnvelope, 0, null).copy(
            status = TimeAdvanceResult.DECISION_REQUIRED,
            pendingDecisionGateId = "gate",
            pendingDecisionChoiceIds = listOf("yes"),
            pendingSuffix = suffix
        )
        harness.port.commitSegment(
            parentEnvelope, 0, segmentDelta(initial, parentState, 1), parentState, TimeAdvanceResult.DECISION_REQUIRED
        )

        val afterParent = harness.image()
        val selection = DecisionSelection("gate", "yes", "decision.v1", "{}")
        val invalidChildEnvelope = advanceEnvelope("invalid-child", 2, afterParent.stateVersion)
        val invalidContinuation = TimeAdvanceContinuation(
            SessionEpoch(1),
            parentEnvelope.commandId,
            invalidChildEnvelope.commandId,
            PayloadHash("0".repeat(64)),
            null,
            selection
        )
        val invalidChildState = advanceState(invalidChildEnvelope, 0, TimeAdvanceResult.COMPLETED).copy(
            continuationOfCommandId = parentEnvelope.commandId,
            selectedDecisionChoiceId = "yes"
        )
        check(runCatching {
            harness.port.commitSegment(
                invalidChildEnvelope,
                0,
                segmentDelta(afterParent, invalidChildState, 2),
                invalidChildState,
                TimeAdvanceResult.COMPLETED,
                invalidContinuation
            )
        }.isFailure)
        check(harness.image() == afterParent)

        val childEnvelope = advanceEnvelope("child", 2, afterParent.stateVersion)
        val continuation = TimeAdvanceContinuation(
            SessionEpoch(1), parentEnvelope.commandId, childEnvelope.commandId, suffix.hash, null, selection
        )
        val childState = advanceState(childEnvelope, 0, TimeAdvanceResult.COMPLETED).copy(
            continuationOfCommandId = parentEnvelope.commandId,
            selectedDecisionChoiceId = "yes"
        )
        val childDelta = segmentDelta(afterParent, childState, 2)
        val child = harness.port.commitSegment(
            childEnvelope, 0, childDelta, childState, TimeAdvanceResult.COMPLETED, continuation
        )
        val afterChild = harness.image()
        check(harness.port.commitSegment(childEnvelope, 0, childDelta, childState, TimeAdvanceResult.COMPLETED, continuation) == child)
        check(harness.image() == afterChild)

        val otherEnvelope = advanceEnvelope("other-child", 2, afterChild.stateVersion)
        val otherContinuation = continuation.copy(childCommandId = otherEnvelope.commandId)
        check(runCatching {
            harness.port.commitSegment(otherEnvelope, 0, childDelta, childState.copy(commandId = otherEnvelope.commandId), TimeAdvanceResult.COMPLETED, otherContinuation)
        }.isFailure)
        check(harness.image() == afterChild)
    }

    private suspend fun verifyCompleteOrPrevious(factory: SavePortConformanceFactory) {
        for (fault in listOf(SavePortFaultPoint.BEFORE_COMMIT, SavePortFaultPoint.DURING_COMMIT)) {
            val harness = factory.create()
            val before = harness.image()
            val envelope = envelope("fault-$fault", "payload", before.stateVersion)
            harness.armFault(fault)
            check(runCatching { harness.port.commit(envelope, atomicDelta(before, envelope, 1)) }.isFailure)
            check(harness.image() == before)
            val restarted = harness.restart()
            check(restarted.image() == before)
            check(restarted.port.findReceipt(envelope.sessionEpoch, envelope.commandId) == null)
        }

        val afterCommit = factory.create()
        val before = afterCommit.image()
        val envelope = envelope("fault-after", "payload", before.stateVersion)
        afterCommit.armFault(SavePortFaultPoint.AFTER_COMMIT)
        check(runCatching { afterCommit.port.commit(envelope, atomicDelta(before, envelope, 1)) }.isFailure)
        val committed = afterCommit.image()
        check(committed.writeCount == before.writeCount + 1)
        check(committed.stateVersion == StateVersion(before.stateVersion.value + 1))
        check(committed.world.clock.minute == minute(1))
        check(committed.rngState.streams.single().drawCounter == 1L)
        check(committed.aggregatePayloads[entity("aggregate")] == "{\"value\":1}")
        checkEvents(committed, listOf(atomicEvent(envelope, committed.stateVersion)))
        check(afterCommit.port.findReceipt(SessionEpoch(1), envelope.commandId)?.stateVersion == committed.stateVersion)
    }

    private suspend fun verifyRestartCutpoints(factory: SavePortConformanceFactory) = coroutineScope {
        val calculatedHarness = factory.create()
        val calculatedBefore = calculatedHarness.image()
        val calculatedEnvelope = advanceEnvelope("cutpoint-calculated", 2, calculatedBefore.stateVersion)
        val calculatedState = advanceState(calculatedEnvelope, 0, null).copy(nextEventSequence = 1)
        val calculatedDelta = segmentDelta(calculatedBefore, calculatedState, 1, 0)
        check(calculatedDelta.events.size == 1)
        val calculatedExpectedHarness = factory.create()
        val calculatedExpectedBefore = calculatedExpectedHarness.image()
        checkFullImage(calculatedBefore, calculatedExpectedBefore)
        val calculatedExpectedResult = executeSession(calculatedExpectedHarness, calculatedEnvelope)
        val calculatedExpectedAfter = calculatedExpectedHarness.image()
        val afterCalculationRestart = calculatedHarness.restart()
        checkFullImage(calculatedBefore, afterCalculationRestart.image())
        check(afterCalculationRestart.port.findReceipt(calculatedEnvelope.sessionEpoch, calculatedEnvelope.commandId) == null)
        val calculatedRecoveredResult = executeSession(afterCalculationRestart, calculatedEnvelope)
        check(calculatedRecoveredResult == calculatedExpectedResult)
        checkFullImage(calculatedExpectedAfter, afterCalculationRestart.image())

        val beforeCommitHarness = factory.create()
        val beforeCommitImage = beforeCommitHarness.image()
        val beforeCommitEnvelope = advanceEnvelope("cutpoint-before-commit", 2, beforeCommitImage.stateVersion)
        val beforeCommitState = advanceState(beforeCommitEnvelope, 0, null).copy(nextEventSequence = 1)
        val beforeCommitExpectedHarness = factory.create()
        val beforeCommitExpectedResult = executeSession(beforeCommitExpectedHarness, beforeCommitEnvelope)
        val beforeCommitExpectedAfter = beforeCommitExpectedHarness.image()
        beforeCommitHarness.armFault(SavePortFaultPoint.BEFORE_COMMIT)
        check(runCatching {
            beforeCommitHarness.port.commitSegment(
                beforeCommitEnvelope,
                0,
                segmentDelta(beforeCommitImage, beforeCommitState, 1, 0),
                beforeCommitState,
                null
            )
        }.isFailure)
        val afterBeforeCommitRestart = beforeCommitHarness.restart()
        checkFullImage(beforeCommitImage, afterBeforeCommitRestart.image())
        check(afterBeforeCommitRestart.port.findReceipt(beforeCommitEnvelope.sessionEpoch, beforeCommitEnvelope.commandId) == null)
        val beforeCommitRecoveredResult = executeSession(afterBeforeCommitRestart, beforeCommitEnvelope)
        check(beforeCommitRecoveredResult == beforeCommitExpectedResult)
        checkFullImage(beforeCommitExpectedAfter, afterBeforeCommitRestart.image())

        val afterCommitHarness = factory.create()
        val afterCommitBefore = afterCommitHarness.image()
        val afterCommitEnvelope = advanceEnvelope("cutpoint-after-commit", 2, afterCommitBefore.stateVersion)
        val afterCommitState = advanceState(afterCommitEnvelope, 0, null).copy(nextEventSequence = 1)
        val afterCommitDelta = segmentDelta(afterCommitBefore, afterCommitState, 1, 0)
        afterCommitHarness.armFault(SavePortFaultPoint.AFTER_COMMIT)
        check(runCatching {
            afterCommitHarness.port.commitSegment(
                afterCommitEnvelope, 0, afterCommitDelta, afterCommitState, null
            )
        }.isFailure)
        val afterCommitDurable = afterCommitHarness.image()
        val afterCommitExpectedHarness = factory.create()
        afterCommitExpectedHarness.port.commitSegment(
            afterCommitEnvelope, 0, afterCommitDelta, afterCommitState, null
        )
        val afterCommitExpectedDurable = afterCommitExpectedHarness.image()
        checkFullImage(afterCommitExpectedDurable, afterCommitDurable)
        checkEvents(afterCommitDurable, listOf(segmentEvent(afterCommitBefore, afterCommitState, 0)))
        val afterCommitRestart = afterCommitHarness.restart()
        val afterCommitExpectedRestart = afterCommitExpectedHarness.restart()
        checkFullImage(afterCommitDurable, afterCommitRestart.image())
        checkFullImage(afterCommitExpectedDurable, afterCommitExpectedRestart.image())
        val afterCommitReceipt = checkNotNull(
            afterCommitRestart.port.findReceipt(afterCommitEnvelope.sessionEpoch, afterCommitEnvelope.commandId)
        )
        check(afterCommitReceipt.lifecycleStatus == ReceiptLifecycle.RUNNING)
        check(afterCommitReceipt.lastCommittedSegmentNo == 0)
        val afterCommitRecoveredResult = executeSession(afterCommitRestart, afterCommitEnvelope)
        val afterCommitExpectedResult = executeSession(afterCommitExpectedRestart, afterCommitEnvelope)
        check(afterCommitRecoveredResult == afterCommitExpectedResult)
        check(afterCommitRestart.image().stateVersion == StateVersion(afterCommitDurable.stateVersion.value + 1))
        check(afterCommitRestart.port.findReceipt(afterCommitEnvelope.sessionEpoch, afterCommitEnvelope.commandId)?.lifecycleStatus == ReceiptLifecycle.COMMITTED)
        checkFullImage(afterCommitExpectedRestart.image(), afterCommitRestart.image())

        val beforeNextHarness = factory.create()
        val beforeNextInitial = beforeNextHarness.image()
        val beforeNextEnvelope = advanceEnvelope("cutpoint-before-next", 2, beforeNextInitial.stateVersion)
        val beforeNextRunning = advanceState(beforeNextEnvelope, 0, null).copy(nextEventSequence = 1)
        beforeNextHarness.port.commitSegment(
            beforeNextEnvelope,
            0,
            segmentDelta(beforeNextInitial, beforeNextRunning, 1, 0),
            beforeNextRunning,
            null
        )
        val beforeNextDurable = beforeNextHarness.image()
        val beforeNextExpectedHarness = factory.create()
        beforeNextExpectedHarness.port.commitSegment(
            beforeNextEnvelope,
            0,
            segmentDelta(beforeNextExpectedHarness.image(), beforeNextRunning, 1, 0),
            beforeNextRunning,
            null
        )
        val beforeNextExpectedDurable = beforeNextExpectedHarness.image()
        checkFullImage(beforeNextExpectedDurable, beforeNextDurable)
        val beforeNextRestart = beforeNextHarness.restart()
        val beforeNextExpectedRestart = beforeNextExpectedHarness.restart()
        checkFullImage(beforeNextDurable, beforeNextRestart.image())
        checkFullImage(beforeNextExpectedDurable, beforeNextExpectedRestart.image())
        val beforeNextReceipt = checkNotNull(
            beforeNextRestart.port.findReceipt(beforeNextEnvelope.sessionEpoch, beforeNextEnvelope.commandId)
        )
        check(beforeNextReceipt.lifecycleStatus == ReceiptLifecycle.RUNNING)
        check(beforeNextReceipt.lastCommittedSegmentNo == 0)
        val beforeNextRecoveredResult = executeSession(beforeNextRestart, beforeNextEnvelope)
        val beforeNextExpectedResult = executeSession(beforeNextExpectedRestart, beforeNextEnvelope)
        check(beforeNextRecoveredResult == beforeNextExpectedResult)
        checkFullImage(beforeNextExpectedRestart.image(), beforeNextRestart.image())
        val terminalReceipt = checkNotNull(
            beforeNextRestart.port.findReceipt(beforeNextEnvelope.sessionEpoch, beforeNextEnvelope.commandId)
        )
        check(terminalReceipt.lifecycleStatus == ReceiptLifecycle.COMMITTED)
        check(terminalReceipt.lastCommittedSegmentNo == 1)
    }

    private suspend fun verifySessionPublicationAndLifecycle(factory: SavePortConformanceFactory) = coroutineScope {
        val lifecycleHarness = factory.create()
        val lifecycleBefore = lifecycleHarness.image()
        val lifecycleSession = session(lifecycleHarness)
        lifecycleSession.pause()
        lifecycleSession.resume()
        lifecycleSession.close()
        check(lifecycleHarness.image() == lifecycleBefore)

        val failedHarness = factory.create()
        val failedBefore = failedHarness.image()
        failedHarness.armFault(SavePortFaultPoint.BEFORE_COMMIT)
        val failedSession = session(failedHarness)
        val failed = failedSession.execute(advanceEnvelope("publish-failed", 1, failedBefore.stateVersion))
        check((failed as? CommandResult.Rejected)?.error is DomainError.PersistenceFailure)
        check(failedSession.publications.value == null)
        check(failedHarness.image() == failedBefore)
        failedSession.close()

        val reconciledHarness = factory.create()
        val reconciledBefore = reconciledHarness.image()
        reconciledHarness.armFault(SavePortFaultPoint.AFTER_COMMIT)
        val reconciledSession = session(reconciledHarness)
        val command = advanceEnvelope("publish-reconciled", 1, reconciledBefore.stateVersion)
        check(reconciledSession.execute(command) is CommandResult.Accepted)
        val publication = checkNotNull(reconciledSession.publications.value)
        val committed = reconciledHarness.image()
        check(committed.world.clock.minute == minute(1))
        check(reconciledSession.execute(command) is CommandResult.Accepted)
        check(reconciledHarness.image() == committed)
        check(reconciledSession.publications.value === publication)
        reconciledSession.close()
    }

    private fun CoroutineScope.session(harness: SavePortConformanceHarness): WorldSession {
        val image = harness.image()
        return WorldSession(
            SessionEpoch(1), harness.port, this, ContentSnapshot.emptyForTest(),
            WorldSnapshot(image.stateVersion, image.world, image.rngState, image.aggregateStates),
            WorldEngine(emptyList()), Dispatchers.Unconfined
        )
    }

    private suspend fun CoroutineScope.executeSession(
        harness: SavePortConformanceHarness,
        envelope: CommandEnvelope<out WorldCommandPayload>
    ): CommandResult {
        val restoredSession = session(harness)
        return try {
            restoredSession.execute(envelope)
        } finally {
            restoredSession.close()
        }
    }

    private fun envelope(id: String, value: String, version: StateVersion) = CommandEnvelope.create(
        CommandId(id), SessionEpoch(1), version, entity("actor"), UnsupportedFeaturePayload(value)
    )

    private fun advanceEnvelope(id: String, target: Long, version: StateVersion) = CommandEnvelope.create(
        CommandId(id), SessionEpoch(1), version, entity("actor"),
        AdvanceTimePayload(
            TimeAdvanceGoal.UntilMinute(minute(target)), ProgressionMode.FAST_FORWARD,
            TimeTraversalLimits(minute(target), 4)
        )
    )

    private fun atomicDelta(
        before: SavePortConformanceImage,
        envelope: CommandEnvelope<out WorldCommandPayload>,
        submissionSequence: Long
    ): DomainDelta {
        val aggregateId = entity("aggregate")
        val current = before.aggregateStates[aggregateId]
        val nextValue = (current as? ProbeAggregate)?.value?.plus(1) ?: 1
        val nextVersion = StateVersion(before.stateVersion.value + 1)
        val nextRng = RngState(listOf(RngStreamState(RngStreamKey("world/event"), "pcg32-xsh-rr.v1", 2, 3, 1)))
        return DomainDelta(
            listOf(AggregateChange(aggregateId, current, ProbeAggregate(aggregateId, nextValue))),
            nextRng,
            listOf(atomicEvent(envelope, nextVersion)),
            CommandResult.Accepted(submissionSequence),
            WorldStateChange(before.world, before.world.copy(clock = WorldClock(minute(1))))
        )
    }

    private fun atomicEvent(
        envelope: CommandEnvelope<out WorldCommandPayload>,
        sourceVersion: StateVersion
    ): DomainEvent<UnsupportedFeatureEventPayload> = DomainEvent(
        EventId("event-${envelope.commandId.value}"),
        envelope.actorId,
        null,
        envelope.sessionEpoch,
        envelope.commandId,
        sourceVersion,
        minute(1),
        SubMinuteMillis(0),
        EventSequence(0),
        EventVisibility.PUBLIC,
        EventImportance.NORMAL,
        UnsupportedFeatureEventPayload("conformance")
    )

    private fun segmentDelta(
        before: SavePortConformanceImage,
        state: TimeAdvanceState,
        sequence: Long,
        eventSequence: Long? = null
    ): DomainDelta =
        DomainDelta(
            emptyList(),
            before.rngState,
            eventSequence?.let { listOf(segmentEvent(before, state, it)) } ?: emptyList(),
            CommandResult.Accepted(sequence),
            WorldStateChange(before.world, before.world.copy(clock = WorldClock(minute(state.segmentNo.toLong())), timeAdvance = state))
        )

    private fun segmentEvent(
        before: SavePortConformanceImage,
        state: TimeAdvanceState,
        eventSequence: Long
    ): DomainEvent<UnsupportedFeatureEventPayload> {
        val commandEpoch = checkNotNull(state.commandEpoch)
        val commandId = checkNotNull(state.commandId)
        return DomainEvent(
            EventId("segment-${commandId.value}-${state.segmentNo}"),
            entity("actor"),
            null,
            commandEpoch,
            commandId,
            StateVersion(before.stateVersion.value + 1),
            before.world.clock.minute,
            SubMinuteMillis(0),
            EventSequence(eventSequence),
            EventVisibility.PUBLIC,
            EventImportance.NORMAL,
            UnsupportedFeatureEventPayload("segment-${state.segmentNo}")
        )
    }

    private fun checkEvents(
        image: SavePortConformanceImage,
        expected: List<DomainEvent<out DomainEventPayload>>
    ) {
        val actual = image.encodedEventsBase64.map { encoded ->
            val bytes = Base64.getDecoder().decode(encoded)
            val event = checked(DomainEventCodec.decode(bytes))
            check(DomainEventCodec.encode(event).contentEquals(bytes))
            event
        }
        check(actual == expected)
    }

    private fun checkFullImage(expected: SavePortConformanceImage, actual: SavePortConformanceImage) {
        check(expected.stateVersion == actual.stateVersion) { "state version recovery mismatch" }
        check(expected.world == actual.world) { "world/time advance recovery mismatch" }
        check(expected.rngState == actual.rngState) { "rng recovery mismatch" }
        check(expected.aggregatePayloads == actual.aggregatePayloads) { "aggregate payload recovery mismatch" }
        check(typedAggregates(expected) == typedAggregates(actual)) { "typed aggregate recovery mismatch" }
        check(expected.encodedEventsBase64 == actual.encodedEventsBase64) { "event bytes/order recovery mismatch" }
        check(expected.receipts == actual.receipts) { "receipt watermark recovery mismatch" }
        check(expected.writeCount == actual.writeCount) { "write count recovery mismatch" }
        check(expected.aggregateStates.isNotEmpty() && actual.aggregateStates.isNotEmpty()) {
            "recovery oracle requires a non-empty typed aggregate image"
        }
    }

    private fun typedAggregates(image: SavePortConformanceImage): List<String> = image.aggregateStates
        .entries
        .sortedBy { it.key.value }
        .map { (id, aggregate) -> "${id.value}|${aggregate.aggregateType}|${aggregate.canonicalJson()}" }

    private fun advanceState(
        envelope: CommandEnvelope<out WorldCommandPayload>,
        segmentNo: Int,
        status: TimeAdvanceResult?
    ) = TimeAdvanceState(
        TimeAdvanceGoal.UntilMinute(minute(2)), null, segmentNo, status,
        commandEpoch = envelope.sessionEpoch, commandId = envelope.commandId, segmentNo = segmentNo,
        progressionMode = ProgressionMode.FAST_FORWARD, limits = TimeTraversalLimits(minute(2), 4),
        interruptPolicy = TimeAdvanceInterruptPolicy()
    )

    private data class ProbeAggregate(override val aggregateId: EntityId, val value: Int) : AggregateState {
        override val aggregateType: String = "save-port-conformance.v1"
        override fun canonicalJson(): String = "{\"value\":$value}"
    }

    private fun minute(value: Long): GameMinute = checked(GameMinute.of(value))
    private fun entity(value: String): EntityId = checked(EntityId.of(value))
    private fun <T> checked(value: Checked<T>): T = when (value) {
        is Checked.Value -> value.value
        is Checked.Rejected -> error(value.error.toString())
    }
}
