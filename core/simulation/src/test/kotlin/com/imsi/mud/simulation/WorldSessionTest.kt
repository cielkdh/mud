package com.imsi.mud.simulation

import kotlinx.coroutines.async
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import kotlin.coroutines.CoroutineContext
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WorldSessionTest {
    @Test
    fun `value objects keep units and reject invalid ranges`() {
        val sixty = checkedValue(
            checkedValue(Money.of(100))
                .plus(checkedValue(Money.of(40)))
                .let(::checkedValue)
                .debit(checkedValue(Money.of(80)))
        )
        assertEquals(60, sixty.amount)
        assertTrue(checkedValue(Money.of(Long.MAX_VALUE)).plus(checkedValue(Money.of(1))) is Checked.Rejected)
        assertTrue(BasisPoint.of(10_001) is Checked.Rejected)
        assertTrue(ProbabilityPpm.of(1_000_001) is Checked.Rejected)

        val streams = RngState(
            listOf(
                RngStreamState(RngStreamKey("world"), "pcg32.v1", 1, 1),
                RngStreamState(RngStreamKey("encounter"), "pcg32.v1", 2, 3)
            )
        )
        assertEquals(2, streams.streams.size)
        assertTrue(
            runCatching {
                RngState(listOf(streams.streams.first(), streams.streams.first()))
            }.isFailure
        )
    }

    @Test
    fun `command and event codecs are deterministic and reject unknown codecs`() {
        val payload = UnsupportedFeaturePayload("phase-1")
        val expectedHash = javaClass.getResource("/unsupported-feature-v1.sha256")!!.readText().trim()
        assertEquals(expectedHash, CommandPayloadCodec.hash(payload).value)
        assertEquals(payload, checkedValue(CommandPayloadCodec.decode(payload.codecId, payload.canonicalJson())))
        assertTrue(CommandPayloadCodec.decode("future.v2", payload.canonicalJson()) is Checked.Rejected)
        assertTrue(CommandPayloadCodec.decode(payload.codecId, "{\"feature\":\"\"}") is Checked.Rejected)
        assertTrue(CommandPayloadCodec.decode(payload.codecId, "{\"feature\":\"a\"b\"}") is Checked.Rejected)

        val event = DomainEvent(
            eventId = EventId("event-1"),
            sourceId = checkedValue(EntityId.of("actor-1")),
            sourceEventId = null,
            sourceEpoch = SessionEpoch(1),
            sourceCommandId = CommandId("command-1"),
            sourceVersion = StateVersion(0),
            gameMinute = checkedValue(GameMinute.of(0)),
            subMinuteMs = SubMinuteMillis(0),
            eventSequence = EventSequence(0),
            visibility = EventVisibility.SYSTEM,
            importance = EventImportance.NORMAL,
            payload = UnsupportedFeatureEventPayload("phase-event")
        )
        val encoded = DomainEventCodec.encode(event)
        val goldenBytes = java.util.Base64.getDecoder().decode(
            javaClass.getResource("/unsupported-feature-event-v1.base64")!!.readText().trim()
        )
        assertArrayEquals(goldenBytes, encoded)
        assertEquals(event, checkedValue(DomainEventCodec.decode(encoded)))
        assertTrue(DomainEventPayloadCodec.decode("future.v2", event.payload.canonicalJson()) is Checked.Rejected)
        assertTrue(DomainEventPayloadCodec.decode(event.payload.codecId, "{\"feature\":\"a\"b\"}") is Checked.Rejected)
    }

    @Test
    fun `canonical payload normalizes NFC before hashing`() {
        val nfc = UnsupportedFeaturePayload("é")
        val nfd = UnsupportedFeaturePayload("e\u0301")

        assertEquals(nfc.canonicalJson(), nfd.canonicalJson())
        assertEquals(CommandPayloadCodec.hash(nfc), CommandPayloadCodec.hash(nfd))
    }

    @Test
    fun `world session serializes rejected receipts stale requests and close draining`() = runBlocking {
        repeat(100) {
            val savePort = RecordingSavePort()
            val session = WorldSession(SessionEpoch(1), savePort, this)
            val firstEnvelope = unsupportedEnvelope("command-a", "phase-a")
            val secondEnvelope = unsupportedEnvelope("command-b", "phase-b")
            val first = async(start = CoroutineStart.UNDISPATCHED) { session.execute(firstEnvelope) }
            yield()
            val second = async(start = CoroutineStart.UNDISPATCHED) { session.execute(secondEnvelope) }

            assertUnsupported(first.await(), 1)
            assertUnsupported(second.await(), 2)
            assertEquals(first.await(), session.execute(firstEnvelope))
            assertEquals(listOf("command-a", "command-b"), savePort.commandIds)

            val stale = firstEnvelope.copy(sessionEpoch = SessionEpoch(2))
            assertTrue(session.execute(stale) is CommandResult.Rejected)
            session.close()
            assertEquals(DomainError.SessionClosed, (session.execute(secondEnvelope) as CommandResult.Rejected).error)
        }
    }

    @Test
    fun `accepted command drains after its caller is cancelled`() = runBlocking {
        withTimeout(5_000) {
            val savePort = RecordingSavePort()
            val dispatcher = PausedDispatcher()
            val session = WorldSession(SessionEpoch(1), savePort, this, dispatcher)
            val first = async(start = CoroutineStart.UNDISPATCHED) {
                session.execute(unsupportedEnvelope("command-a", "phase-a"))
            }
            val second = async(start = CoroutineStart.UNDISPATCHED) {
                session.execute(unsupportedEnvelope("command-b", "phase-b"))
            }
            val firstClose = async(start = CoroutineStart.UNDISPATCHED) { session.close() }
            val secondClose = async(start = CoroutineStart.UNDISPATCHED) { session.close() }

            first.cancel()
            assertFalse(firstClose.isCompleted)
            assertFalse(secondClose.isCompleted)
            dispatcher.runUntilIdle()
            assertUnsupported(second.await(), 2)
            firstClose.await()
            secondClose.await()
            assertEquals(listOf("command-a", "command-b"), savePort.commandIds)
        }
    }

    @Test
    fun `pre enqueue cancellation does not consume a submission sequence`() = runBlocking {
        val session = WorldSession(SessionEpoch(1), RecordingSavePort(), this)
        val cancelled = async(start = CoroutineStart.LAZY) {
            session.execute(unsupportedEnvelope("command-cancelled", "phase-cancelled"))
        }

        cancelled.cancel()
        cancelled.join()
        assertTrue(cancelled.isCancelled)
        assertUnsupported(session.execute(unsupportedEnvelope("command-after-cancel", "phase-after-cancel")), 1)
        session.close()
    }

    @Test
    fun `rejected command does not persist or advance state version`() = runBlocking {
        val savePort = RecordingSavePort()
        val session = WorldSession(SessionEpoch(1), savePort, this)

        assertUnsupported(session.execute(unsupportedEnvelope("command-a", "phase-a")), 1)
        assertUnsupported(session.execute(unsupportedEnvelope("command-b", "phase-b")), 2)
        assertUnsupported(session.execute(unsupportedEnvelope("command-c", "phase-c")), 3)
        assertEquals(listOf("command-a", "command-b", "command-c"), savePort.commandIds)
        session.close()
    }

    @Test
    fun `accepted delta commits before publishing state and a failed commit rolls back`() = runBlocking {
        val savePort = RecordingSavePort()
        val session = WorldSession(SessionEpoch(1), savePort, this, Dispatchers.Unconfined, testDeltaFactory())
        val before = session.inMemoryStateHash()

        val result = session.execute(mutationEnvelope("command-accepted", "aggregate-a", "a", rngState(42, 1)))

        assertAccepted(result, 1)
        assertEquals(1, savePort.deltas.size)
        assertEquals(
            AggregateChange(entityId("aggregate-a"), null, TestAggregate(entityId("aggregate-a"), "a")),
            savePort.deltas.single().aggregateChanges.single()
        )
        assertEquals(rngState(42, 1), savePort.deltas.single().rngState)
        val event = savePort.deltas.single().events.single()
        assertEquals(StateVersion(1), event.sourceVersion)
        assertEquals(EventSequence(0), event.eventSequence)
        assertNotEquals(before, session.inMemoryStateHash())
        assertTrue(session.execute(unsupportedEnvelope("stale", "phase", StateVersion(0))) is CommandResult.Rejected)
        session.close()

        val failed = WorldSession(SessionEpoch(1), FailingSavePort(), this, Dispatchers.Unconfined, testDeltaFactory())
        val failedBefore = failed.inMemoryStateHash()
        val failure = failed.execute(mutationEnvelope("command-failed", "aggregate-a", "a", rngState(42, 1)))
        assertTrue((failure as CommandResult.Rejected).error is DomainError.PersistenceFailure)
        assertEquals(failedBefore, failed.inMemoryStateHash())
        failed.close()
    }

    @Test
    fun `invalid event metadata is rejected before persistence`() = runBlocking {
        val savePort = RecordingSavePort()
        val session = WorldSession(SessionEpoch(1), savePort, this, Dispatchers.Unconfined, testDeltaFactory(validEventMetadata = false))
        val before = session.inMemoryStateHash()

        val result = session.execute(mutationEnvelope("command-invalid-event", "aggregate-a", "a", rngState(42, 1)))

        assertTrue((result as CommandResult.Rejected).error is DomainError.InvariantViolation)
        assertTrue(savePort.commandIds.isEmpty())
        assertEquals(before, session.inMemoryStateHash())
        session.close()
    }

    @Test
    fun `delta result submission sequence must match the assigned sequence before persistence`() = runBlocking {
        listOf(2L, null).forEachIndexed { index, invalidSequence ->
            val savePort = RecordingSavePort()
            val session = WorldSession(
                SessionEpoch(1),
                savePort,
                this,
                Dispatchers.Unconfined
            ) { envelope, receiptVersion, currentRngState, _ ->
                testDeltaFactory()(envelope, receiptVersion, currentRngState, 0L).copy(
                    result = CommandResult.Accepted(invalidSequence)
                )
            }
            val before = session.inMemoryStateHash()

            val result = session.execute(
                mutationEnvelope("command-invalid-sequence-$index", "aggregate-a", "a", rngState(42, 1))
            )

            val rejected = result as CommandResult.Rejected
            assertTrue(rejected.error is DomainError.InvariantViolation)
            assertEquals(1L, rejected.submissionSequence)
            assertTrue(savePort.commandIds.isEmpty())
            assertEquals(before, session.inMemoryStateHash())
            session.close()
        }
    }

    @Test
    fun `A through F sequence produces one deterministic state hash across 100 runs`() = runBlocking {
        var expectedHash: PayloadHash? = null
        repeat(100) {
            val dispatcher = PausedDispatcher()
            val savePort = RecordingSavePort()
            val session = WorldSession(SessionEpoch(1), savePort, this, dispatcher, testDeltaFactory())
            val a = async(start = CoroutineStart.UNDISPATCHED) {
                session.execute(mutationEnvelope("command-a", "aggregate-a", "a", rngState(1, 1)))
            }
            val b = async(start = CoroutineStart.UNDISPATCHED) {
                session.execute(mutationEnvelope("command-b", "aggregate-b", "b", rngState(2, 2), expectedVersion = null))
            }
            val stale = async(start = CoroutineStart.UNDISPATCHED) {
                session.execute(
                    mutationEnvelope(
                        "command-c",
                        "aggregate-c",
                        "c",
                        rngState(3, 3),
                        sessionEpoch = SessionEpoch(2)
                    )
                )
            }
            val preEnqueue = async(start = CoroutineStart.LAZY) {
                session.execute(mutationEnvelope("command-d", "aggregate-d", "d", rngState(4, 4)))
            }
            preEnqueue.cancel()
            val postEnqueue = async(start = CoroutineStart.UNDISPATCHED) {
                session.execute(mutationEnvelope("command-e", "aggregate-e", "e", rngState(5, 5), expectedVersion = null))
            }
            postEnqueue.cancel()
            val close = async(start = CoroutineStart.UNDISPATCHED) { session.close() }
            val afterClose = session.execute(mutationEnvelope("command-f", "aggregate-f", "f", rngState(6, 6)))

            assertEquals(DomainError.SessionClosed, (afterClose as CommandResult.Rejected).error)
            dispatcher.runUntilIdle()
            assertAccepted(a.await(), 1)
            assertAccepted(b.await(), 2)
            assertTrue((stale.await() as CommandResult.Rejected).error is DomainError.StaleSession)
            assertTrue(postEnqueue.isCancelled)
            close.await()
            assertEquals(listOf("command-a", "command-b", "command-e"), savePort.commandIds)
            val actualHash = session.inMemoryStateHash()
            if (expectedHash == null) expectedHash = actualHash else assertEquals(expectedHash, actualHash)
        }
    }

    @Test
    fun `durable receipt lookup restores an evicted accepted command before version validation`() = runBlocking {
        val savePort = RecordingSavePort()
        val session = WorldSession(SessionEpoch(1), savePort, this, Dispatchers.Unconfined, testDeltaFactory())
        val first = mutationEnvelope("command-0", "aggregate-0", "a", rngState(1, 1))

        assertAccepted(session.execute(first), 1)
        repeat(WorldSession.RECEIPT_CACHE_MAX_ENTRIES) { index ->
            assertUnsupported(
                session.execute(unsupportedEnvelope("rejected-$index", "phase-$index", expectedVersion = null)),
                index + 2L
            )
        }
        assertAccepted(session.execute(first), 1)
        assertEquals(WorldSession.RECEIPT_CACHE_MAX_ENTRIES + 1, savePort.commandIds.size)
        assertEquals(1, savePort.commandIds.count { it == "command-0" })
        session.close()
    }

    private fun unsupportedEnvelope(
        commandId: String,
        feature: String,
        expectedVersion: StateVersion? = StateVersion(0)
    ): CommandEnvelope<UnsupportedFeaturePayload> =
        CommandEnvelope.create(
            commandId = CommandId(commandId),
            sessionEpoch = SessionEpoch(1),
            expectedVersion = expectedVersion,
            actorId = checkedValue(EntityId.of("actor-1")),
            payload = UnsupportedFeaturePayload(feature)
        )

    private fun assertUnsupported(result: CommandResult, expectedSequence: Long) {
        val rejected = result as CommandResult.Rejected
        assertTrue(rejected.error is DomainError.UnsupportedFeature)
        assertEquals(expectedSequence, rejected.submissionSequence)
    }

    private fun assertAccepted(result: CommandResult, expectedSequence: Long) {
        assertEquals(CommandResult.Accepted(expectedSequence), result)
    }

    private fun mutationEnvelope(
        commandId: String,
        aggregateId: String,
        aggregateHash: String,
        rngState: RngState,
        expectedVersion: StateVersion? = StateVersion(0),
        sessionEpoch: SessionEpoch = SessionEpoch(1)
    ): CommandEnvelope<UnsupportedFeaturePayload> = CommandEnvelope.create(
        commandId = CommandId(commandId),
        sessionEpoch = sessionEpoch,
        expectedVersion = expectedVersion,
        actorId = entityId("actor-1"),
        payload = UnsupportedFeaturePayload(
            "$aggregateId|$aggregateHash|${rngState.streams.single().state}|${rngState.streams.single().counter}"
        )
    )

    private fun testDeltaFactory(validEventMetadata: Boolean = true): (
        CommandEnvelope<out WorldCommandPayload>,
        StateVersion,
        RngState,
        Long
    ) -> DomainDelta = { envelope, receiptVersion, currentRngState, submissionSequence ->
        val parts = (envelope.payload as? UnsupportedFeaturePayload)?.feature?.split('|')
        if (parts == null || parts.size != 4) {
            DomainDelta(
                aggregateChanges = emptyList(),
                rngState = currentRngState,
                events = emptyList(),
                result = CommandResult.Rejected(DomainError.UnsupportedFeature(envelope.payload.codecId), submissionSequence)
            )
        } else {
            val aggregateId = entityId(parts[0])
            val rngState = rngState(parts[2].toLong(), parts[3].toLong())
            DomainDelta(
                aggregateChanges = listOf(AggregateChange(aggregateId, null, TestAggregate(aggregateId, parts[1]))),
                rngState = rngState,
                events = listOf(
                    DomainEvent(
                        eventId = EventId("event-${envelope.commandId.value}"),
                        sourceId = envelope.actorId,
                        sourceEventId = null,
                        sourceEpoch = envelope.sessionEpoch,
                        sourceCommandId = envelope.commandId,
                        sourceVersion = if (validEventMetadata) receiptVersion else StateVersion(0),
                        gameMinute = checkedValue(GameMinute.of(0)),
                        subMinuteMs = SubMinuteMillis(0),
                        eventSequence = EventSequence(if (validEventMetadata) 0 else 1),
                        visibility = EventVisibility.SYSTEM,
                        importance = EventImportance.NORMAL,
                        payload = UnsupportedFeatureEventPayload("test-mutation")
                    )
                ),
                result = CommandResult.Accepted(submissionSequence)
            )
        }
    }

    private fun entityId(value: String): EntityId = checkedValue(EntityId.of(value))

    private fun rngState(state: Long, counter: Long): RngState = RngState(
        listOf(RngStreamState(RngStreamKey("world"), "phase0-test.v1", state, counter))
    )

    private data class TestAggregate(
        override val aggregateId: EntityId,
        private val marker: String
    ) : AggregateState {
        override val aggregateType: String = "phase0-test.v1"

        override fun canonicalJson(): String = "{\"marker\":\"$marker\"}"
    }

    private fun <T> checkedValue(value: Checked<T>): T = when (value) {
        is Checked.Value -> value.value
        is Checked.Rejected -> error(value.error.toString())
    }

    private class RecordingSavePort : SavePort {
        val commandIds = mutableListOf<String>()
        val deltas = mutableListOf<DomainDelta>()
        private var committedVersion = StateVersion(0)
        private val receipts = mutableMapOf<Pair<SessionEpoch, CommandId>, PersistedReceipt>()

        override suspend fun findReceipt(sessionEpoch: SessionEpoch, commandId: CommandId): PersistedReceipt? =
            receipts[sessionEpoch to commandId]

        override suspend fun commit(
            envelope: CommandEnvelope<out WorldCommandPayload>,
            delta: DomainDelta
        ): CommitReceipt {
            commandIds += envelope.commandId.value
            deltas += delta
            if (delta.result is CommandResult.Accepted) {
                committedVersion = StateVersion(committedVersion.value + 1)
            }
            val receipt = CommitReceipt(committedVersion, delta.result)
            receipts[envelope.sessionEpoch to envelope.commandId] = PersistedReceipt(
                envelope.payloadHash,
                receipt.stateVersion,
                receipt.result
            )
            return receipt
        }
    }

    private class FailingSavePort : SavePort {
        override suspend fun findReceipt(sessionEpoch: SessionEpoch, commandId: CommandId): PersistedReceipt? = null

        override suspend fun commit(
            envelope: CommandEnvelope<out WorldCommandPayload>,
            delta: DomainDelta
        ): CommitReceipt = error("in-memory persistence failure")
    }

    private class PausedDispatcher : CoroutineDispatcher() {
        private val queued = java.util.ArrayDeque<Runnable>()

        override fun dispatch(context: CoroutineContext, block: Runnable) {
            queued.addLast(block)
        }

        fun runUntilIdle() {
            while (queued.isNotEmpty()) {
                queued.removeFirst().run()
            }
        }
    }
}
