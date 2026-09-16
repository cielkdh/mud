package com.imsi.mud.simulation

import com.imsi.mud.content.ContentSnapshot
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

class Phase2ConformanceTest {
    @Test
    fun `P2-IT-001 uses the public session with InMemory and FaultInjecting SavePorts`() = runBlocking {
        SavePortConformanceSuite.verify(SavePortConformanceFactory { TestHarness() })

        val harness = TestHarness(InMemorySavePort(initialGold = 100L))
        val before = harness.image()
        val session = WorldSession(
            SessionEpoch(1),
            harness.port,
            this,
            ContentSnapshot.emptyForTest(),
            WorldSnapshot(before.stateVersion, before.world, before.rngState, before.aggregateStates),
            WorldEngine(emptyList()),
            kotlinx.coroutines.Dispatchers.Unconfined
        )
        val envelope = CommandEnvelope.create(
            CommandId("p2-it-001"),
            SessionEpoch(1),
            StateVersion(0),
            entity("actor-1"),
            moneyPayload()
        )

        assertEquals(CommandResult.Accepted(1), session.execute(envelope))
        assertEquals(CommandResult.Accepted(1), session.execute(envelope))
        val committed = harness.image()
        assertEquals(60L, committed.world.calendar.resources.owned[gold])
        assertEquals(1, committed.receipts.size)
        assertEquals(StateVersion(1), committed.stateVersion)
        assertEquals(1, committed.writeCount)
        assertTrue(session.publications.value != null)
        session.close()
    }

    private val gold = ResourceIdentity("CURRENCY", "gold")

    private fun entity(value: String): EntityId = when (val checked = EntityId.of(value)) {
        is Checked.Value -> checked.value
        is Checked.Rejected -> error(checked.error.toString())
    }

    private fun moneyPayload(): ScheduleReservePayload = ScheduleReservePayload(
        entity("purchase"),
        "economy.purchase",
        ScheduledActionPayload(
            "PLAYER",
            entity("actor-1"),
            listOf(ScheduledEntityRef("PLAYER", entity("actor-1"))),
            SchedulePriority.PERSONAL_COMMITMENT,
            listOf(ResourceClaim(gold, 40L, ResourceClaimPolicy.CONSUME_ON_RESERVE)),
            ActionKindPolicyProfile(
                resumable = true,
                progressBasis = "MINUTE",
                interruptionPolicy = ActionInterruptionPolicy(ActionInterruptionResult.CONTINUE),
                cancellationPolicy = ActionCancellationPolicy(
                    CancellationStage.entries.associateWith { ActionCancellationRule(0, 0, true) }
                ),
                consequenceEventCodec = "economy.purchase.cancelled.v1"
            ),
            "economy.purchase.completed",
            "economy.purchase.completed.v1",
            canBePreempted = false
        ),
        minute(1),
        minute(2),
        minute(0)
    )

    private fun minute(value: Long): GameMinute = when (val checked = GameMinute.of(value)) {
        is Checked.Value -> checked.value
        is Checked.Rejected -> error(checked.error.toString())
    }

    @Test
    fun `in memory and fault injecting ports satisfy the reusable contract`() = runBlocking {
        SavePortConformanceSuite.verify(SavePortConformanceFactory { TestHarness() })
    }

    private class TestHarness(
        private val store: InMemorySavePort = InMemorySavePort()
    ) : SavePortConformanceHarness {
        private val faults = FaultInjectingSavePort(store)

        override val port: SavePort = faults
        override fun image(): SavePortConformanceImage = store.image()
        override fun armFault(point: SavePortFaultPoint) = faults.arm(point)
        override fun restart(): SavePortConformanceHarness = TestHarness(store.restartedCopy())
    }

    private class FaultInjectingSavePort(private val delegate: InMemorySavePort) : SavePort by delegate {
        private var fault: SavePortFaultPoint? = null

        fun arm(point: SavePortFaultPoint) {
            check(fault == null) { "a fault is already armed" }
            fault = point
        }

        override suspend fun commit(
            envelope: CommandEnvelope<out WorldCommandPayload>,
            delta: DomainDelta
        ): CommitReceipt = withFault { delegate.commit(envelope, delta) }

        override suspend fun commitSegment(
            envelope: CommandEnvelope<out WorldCommandPayload>,
            expectedSegmentNo: Int,
            delta: DomainDelta,
            timeAdvanceState: TimeAdvanceState,
            terminalResult: TimeAdvanceResult?,
            continuation: TimeAdvanceContinuation?
        ): SegmentCommitReceipt = withFault {
            delegate.commitSegment(
                envelope, expectedSegmentNo, delta, timeAdvanceState, terminalResult, continuation
            )
        }

        private suspend fun <T> withFault(operation: suspend () -> T): T {
            val point = fault
            fault = null
            return when (point) {
                SavePortFaultPoint.BEFORE_COMMIT -> error("injected BEFORE_COMMIT")
                SavePortFaultPoint.DURING_COMMIT -> {
                    val preTransaction = delegate.restartedCopy()
                    try {
                        operation()
                    } finally {
                        delegate.restoreFrom(preTransaction)
                    }
                    error("injected DURING_COMMIT after staged write rollback")
                }
                SavePortFaultPoint.AFTER_COMMIT -> {
                    operation()
                    error("injected AFTER_COMMIT")
                }
                null -> operation()
            }
        }
    }

    private class InMemorySavePort(private val initialGold: Long? = null) : SavePort {
        private data class SeedAggregate(override val aggregateId: EntityId, val value: Int) : AggregateState {
            override val aggregateType: String = "save-port-conformance.v1"
            override fun canonicalJson(): String = "{\"value\":$value}"
        }

        private var stateVersion = StateVersion(0)
        private var world = AuthoritativeWorldState(
            WorldClock(minute(0)),
            ScheduleCalendar(emptyList(), initialGold?.let { mapOf(gold to it) } ?: emptyMap()),
            null,
            BoundaryRegistryBinding(1, emptyList())
        )
        private var rngState = RngState(
            listOf(RngStreamState(RngStreamKey("world/event"), "pcg32-xsh-rr.v1", 1, 3, 0))
        )
        // Keep a typed aggregate in every image so restart conformance cannot pass by
        // comparing two empty aggregate maps.
        private val aggregates = linkedMapOf<EntityId, AggregateState>()

        init {
            val id = when (val checked = EntityId.of("aggregate")) {
                is Checked.Value -> checked.value
                is Checked.Rejected -> error(checked.error.toString())
            }
            aggregates[id] = SeedAggregate(id, 0)
        }
        private val eventBytes = mutableListOf<ByteArray>()
        private val receipts = linkedMapOf<Pair<SessionEpoch, CommandId>, PersistedReceipt>()
        private val segments = linkedMapOf<Pair<CommandId, Int>, SegmentCommitReceipt>()
        private val continuationClaims = mutableMapOf<Pair<SessionEpoch, CommandId>, CommandId>()
        private var writeCount = 0

        override suspend fun findReceipt(sessionEpoch: SessionEpoch, commandId: CommandId): PersistedReceipt? =
            receipts[sessionEpoch to commandId]

        override suspend fun commit(
            envelope: CommandEnvelope<out WorldCommandPayload>,
            delta: DomainDelta
        ): CommitReceipt {
            existing(envelope)?.let { return it }
            validateVersion(envelope)
            val applied = prepare(delta)
            apply(applied)
            return CommitReceipt(stateVersion, delta.result).also { receipt ->
                receipts[envelope.sessionEpoch to envelope.commandId] = PersistedReceipt(
                    envelope.payloadHash, receipt.stateVersion, receipt.result, receipt.lifecycleStatus
                )
                writeCount++
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
            segments[envelope.commandId to expectedSegmentNo]?.let { existing ->
                require(receipts[envelope.sessionEpoch to envelope.commandId]?.payloadHash == envelope.payloadHash) {
                    "segment payload hash mismatch"
                }
                return existing
            }
            val prior = receipts[envelope.sessionEpoch to envelope.commandId]
            if (prior == null) {
                require(expectedSegmentNo == 0) { "first segment must be zero" }
                validateVersion(envelope)
            } else {
                require(prior.payloadHash == envelope.payloadHash) { "segment payload hash mismatch" }
                require(prior.lifecycleStatus == ReceiptLifecycle.RUNNING) { "terminal receipt cannot advance" }
                require(expectedSegmentNo == prior.lastCommittedSegmentNo + 1) { "segment watermark gap" }
            }
            require(timeAdvanceState.segmentNo == expectedSegmentNo) { "time advance watermark mismatch" }
            require(timeAdvanceState.commandEpoch == envelope.sessionEpoch && timeAdvanceState.commandId == envelope.commandId) {
                "time advance command identity mismatch"
            }
            validateContinuation(envelope, continuation)
            val applied = prepare(delta)
            require(applied.world.timeAdvance == timeAdvanceState) { "world and receipt time advance state differ" }

            apply(applied)
            val lifecycle = lifecycle(terminalResult)
            val receipt = CommitReceipt(stateVersion, delta.result, lifecycle, expectedSegmentNo)
            val segment = SegmentCommitReceipt(receipt, expectedSegmentNo, timeAdvanceState)
            receipts[envelope.sessionEpoch to envelope.commandId] = PersistedReceipt(
                envelope.payloadHash, stateVersion, delta.result, lifecycle, expectedSegmentNo, timeAdvanceState, continuation
            )
            segments[envelope.commandId to expectedSegmentNo] = segment
            continuation?.let { continuationClaims[it.predecessorEpoch to it.predecessorCommandId] = it.childCommandId }
            writeCount++
            return segment
        }

        fun image(): SavePortConformanceImage = SavePortConformanceImage(
            stateVersion,
            world,
            rngState,
            aggregates.mapValues { it.value.canonicalJson() },
            aggregates.toMap(),
            eventBytes.map(Base64.getEncoder()::encodeToString),
            receipts.toMap(),
            writeCount
        )

        fun restartedCopy(): InMemorySavePort = InMemorySavePort(initialGold).also { restarted ->
            restarted.restoreFrom(this)
        }

        fun restoreFrom(source: InMemorySavePort) {
            stateVersion = source.stateVersion
            world = source.world
            rngState = source.rngState
            aggregates.clear()
            aggregates.putAll(source.aggregates)
            eventBytes.clear()
            eventBytes += source.eventBytes.map { it.copyOf() }
            receipts.clear()
            receipts.putAll(source.receipts)
            segments.clear()
            segments.putAll(source.segments)
            continuationClaims.clear()
            continuationClaims.putAll(source.continuationClaims)
            writeCount = source.writeCount
        }

        private fun existing(envelope: CommandEnvelope<out WorldCommandPayload>): CommitReceipt? {
            val receipt = receipts[envelope.sessionEpoch to envelope.commandId] ?: return null
            if (receipt.payloadHash == envelope.payloadHash) {
                return CommitReceipt(receipt.stateVersion, receipt.result, receipt.lifecycleStatus, receipt.lastCommittedSegmentNo)
            }
            return CommitReceipt(
                stateVersion,
                CommandResult.Rejected(DomainError.IdempotencyKeyReuse(envelope.commandId), null),
                ReceiptLifecycle.REJECTED
            )
        }

        private fun validateVersion(envelope: CommandEnvelope<out WorldCommandPayload>) {
            require(envelope.expectedVersion == null || envelope.expectedVersion == stateVersion) { "state version mismatch" }
        }

        private fun validateContinuation(
            envelope: CommandEnvelope<out WorldCommandPayload>,
            continuation: TimeAdvanceContinuation?
        ) {
            if (continuation == null) return
            require(continuation.childCommandId == envelope.commandId) { "continuation child mismatch" }
            val key = continuation.predecessorEpoch to continuation.predecessorCommandId
            require(continuationClaims[key] == null) { "continuation predecessor already claimed" }
            val predecessor = receipts[key] ?: error("continuation predecessor missing")
            val state = predecessor.timeAdvanceState ?: error("continuation state missing")
            require(predecessor.lifecycleStatus == ReceiptLifecycle.INTERRUPTED)
            require(state.status == TimeAdvanceResult.DECISION_REQUIRED)
            require(state.pendingSuffix?.hash == continuation.pendingSuffixHash)
            require(state.sealedElapsedOutcome?.hash == continuation.sealedOutcomeHash)
            require(state.pendingDecisionGateId == continuation.selection.gateId)
            require(continuation.selection.choiceId in state.pendingDecisionChoiceIds)
        }

        private fun prepare(delta: DomainDelta): Applied {
            val accepted = delta.result is CommandResult.Accepted
            val nextVersion = if (accepted) StateVersion(stateVersion.value + 1) else stateVersion
            val change = delta.worldChange
            require(change == null || change.before == world) { "world precondition mismatch" }
            val nextAggregates = aggregates.toMutableMap()
            delta.aggregateChanges.forEach { aggregateChange ->
                require(same(nextAggregates[aggregateChange.aggregateId], aggregateChange.before)) { "aggregate precondition mismatch" }
                aggregateChange.after?.let { nextAggregates[aggregateChange.aggregateId] = it }
                    ?: nextAggregates.remove(aggregateChange.aggregateId)
            }
            delta.events.zipWithNext().forEach { (current, next) ->
                require(next.eventSequence.value == current.eventSequence.value + 1) { "event sequence gap" }
            }
            val encoded = delta.events.map { event ->
                require(event.sourceVersion == nextVersion) { "event source version mismatch" }
                DomainEventCodec.encode(event).also { bytes ->
                    val decoded = checked(DomainEventCodec.decode(bytes))
                    require(DomainEventCodec.encode(decoded).contentEquals(bytes)) { "event codec round trip mismatch" }
                }
            }
            return Applied(nextVersion, change?.after ?: world, delta.rngState, nextAggregates, encoded)
        }

        private fun apply(applied: Applied) {
            stateVersion = applied.version
            world = applied.world
            rngState = applied.rng
            aggregates.clear()
            aggregates.putAll(applied.aggregates)
            eventBytes += applied.events
        }

        private fun same(actual: AggregateState?, expected: AggregateState?): Boolean =
            actual?.aggregateId == expected?.aggregateId && actual?.aggregateType == expected?.aggregateType &&
                actual?.canonicalJson() == expected?.canonicalJson()

        private fun lifecycle(result: TimeAdvanceResult?): ReceiptLifecycle = when (result) {
            null -> ReceiptLifecycle.RUNNING
            TimeAdvanceResult.INTERRUPTED, TimeAdvanceResult.DECISION_REQUIRED, TimeAdvanceResult.FAILED -> ReceiptLifecycle.INTERRUPTED
            else -> ReceiptLifecycle.COMMITTED
        }

        private data class Applied(
            val version: StateVersion,
            val world: AuthoritativeWorldState,
            val rng: RngState,
            val aggregates: Map<EntityId, AggregateState>,
            val events: List<ByteArray>
        )

        private companion object {
            val gold = ResourceIdentity("CURRENCY", "gold")
            fun minute(value: Long): GameMinute = checked(GameMinute.of(value))
            fun <T> checked(value: Checked<T>): T = when (value) {
                is Checked.Value -> value.value
                is Checked.Rejected -> error(value.error.toString())
            }
        }
    }
}
