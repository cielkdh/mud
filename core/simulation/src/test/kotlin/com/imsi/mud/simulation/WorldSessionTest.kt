package com.imsi.mud.simulation

import com.imsi.mud.content.ContentSnapshot
import kotlinx.coroutines.async
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
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

private fun worldSessionTestMinute(value: Long): GameMinute = when (val checked = GameMinute.of(value)) {
    is Checked.Value -> checked.value
    is Checked.Rejected -> error(checked.error.toString())
}

class WorldSessionTest {
    @Test
    fun `P2-UT-001 spends 40 gold once and increments the committed version once`() = runBlocking {
        val savePort = RecordingSavePort()
        val session = moneySession(savePort)
        val envelope = moneyEnvelope("p2-ut-001")

        assertAccepted(session.execute(envelope), 1)
        assertAccepted(session.execute(envelope), 1)

        assertEquals(60L, savePort.deltas.single().worldChange!!.after.calendar.resources.owned[gold])
        assertEquals(1, savePort.receiptCount)
        assertEquals(StateVersion(1), savePort.stateVersion)
        assertEquals(1, savePort.commandIds.size)
        session.close()
    }

    @Test
    fun `P2-BT-001 rejects stale commands and authoritative world precondition mismatches without mutation`() = runBlocking {
        val savePort = RecordingSavePort()
        val session = moneySession(savePort)
        assertAccepted(session.execute(moneyEnvelope("p2-bt-001-first")), 1)
        val before = session.inMemoryStateHash()

        val stale = session.execute(moneyEnvelope("p2-bt-001-stale", expectedVersion = StateVersion(0)))
        assertEquals(DomainError.VersionConflict(StateVersion(0), StateVersion(1)), (stale as CommandResult.Rejected).error)
        assertEquals(before, session.inMemoryStateHash())
        assertEquals(1, savePort.commandIds.size)
        val worldChange = checkNotNull(savePort.deltas.single().worldChange)
        assertEquals(moneySnapshot().world, worldChange.before)
        assertEquals(AuthoritativeWorldState.WORLD_ID, worldChange.before.worldId)
        assertEquals(AuthoritativeWorldState.WORLD_ID, worldChange.after.worldId)
        session.close()

        val mismatchSavePort = RecordingSavePort()
        val mismatchWorld = moneySnapshot().world.copy(clock = WorldClock(checkedValue(GameMinute.of(1))))
        val mismatchSession = WorldSession(
            SessionEpoch(1),
            mismatchSavePort,
            this,
            ContentSnapshot.emptyForTest(),
            moneySnapshot(),
            Dispatchers.Unconfined
        ) { _, _, rngState, submissionSequence ->
            DomainDelta(
                aggregateChanges = emptyList(),
                rngState = rngState,
                worldChange = WorldStateChange(mismatchWorld, mismatchWorld),
                events = emptyList(),
                result = CommandResult.Accepted(submissionSequence)
            )
        }
        val mismatchBefore = mismatchSession.inMemoryStateHash()
        val mismatch = mismatchSession.execute(unsupportedEnvelope("p2-bt-001-world", "world"))
        assertEquals(
            DomainError.InvariantViolation("authoritative world state precondition failed"),
            (mismatch as CommandResult.Rejected).error
        )
        assertEquals(mismatchBefore, mismatchSession.inMemoryStateHash())
        assertEquals(0, mismatchSavePort.commandIds.size)
        assertEquals(null, mismatchSession.publications.value)
        mismatchSession.close()
    }

    @Test
    fun `P2-FT-001 preserves state hash RNG and receipt when commit fails`() = runBlocking {
        val savePort = FailingSavePort()
        val session = WorldSession(SessionEpoch(1), savePort, this, ContentSnapshot.emptyForTest(), Dispatchers.Unconfined, testDeltaFactory())
        val envelope = mutationEnvelope("p2-ft-001", "aggregate-failure", "before", rngState(42, 1))
        val before = session.inMemoryStateHash()

        val result = session.execute(envelope)

        assertTrue((result as CommandResult.Rejected).error is DomainError.PersistenceFailure)
        assertEquals(before, session.inMemoryStateHash())
        assertEquals(null, savePort.findReceipt(SessionEpoch(1), CommandId("p2-ft-001")))
        assertEquals(null, session.publications.value)
        session.close()
    }

    @Test
    fun `P2-CT-001 makes same payload idempotent and rejects payload reuse`() = runBlocking {
        val savePort = RecordingSavePort()
        val session = moneySession(savePort)
        val envelope = moneyEnvelope("p2-ct-001")

        assertAccepted(session.execute(envelope), 1)
        assertAccepted(session.execute(envelope), 1)
        val reused = session.execute(moneyEnvelope("p2-ct-001", actionId = "different-action", expectedVersion = StateVersion(1)))

        assertTrue((reused as CommandResult.Rejected).error is DomainError.IdempotencyKeyReuse)
        assertEquals(60L, savePort.deltas.single().worldChange!!.after.calendar.resources.owned[gold])
        assertEquals(StateVersion(1), savePort.stateVersion)
        assertEquals(1, savePort.receiptCount)
        assertEquals(1, savePort.commandIds.size)
        session.close()
    }

    @Test
    fun `P2-UT-005 lifecycle pause resume close is idempotent and does not persist directly`() = runBlocking {
        val savePort = RecordingSavePort()
        val session = WorldSession(SessionEpoch(1), savePort, this, ContentSnapshot.emptyForTest(), Dispatchers.Unconfined)

        SavePortConformanceSuite.assertNoLifecyclePersistence(savePort) {
            assertEquals(SessionLifecycle.PAUSED, session.pause().lifecycle)
            assertTrue((session.execute(unsupportedEnvelope("paused", "phase")) as CommandResult.Rejected).error is DomainError.SessionClosed)
            assertEquals(SessionLifecycle.OPEN, session.resume().lifecycle)
            assertEquals(CloseResult(SessionEpoch(1), StateVersion(0), 0), session.close(CloseReason.APPLICATION_REQUEST))
            assertEquals(SessionLifecycle.CLOSED, session.runtimeState.value.lifecycle)
            assertEquals(SessionLifecycle.CLOSED, session.resume().lifecycle)
        }
    }

    @Test
    fun `P2-BT-005 stale epoch response is discarded without write or publication`() = runBlocking {
        withTimeout(5_000) {
            val owner = EpochOwnerFixture(SessionEpoch(1))
            val delayedPort = DelayedEpochSavePort(owner, SessionEpoch(1))
            val epochA = WorldSession(
                SessionEpoch(1), delayedPort, this, ContentSnapshot.emptyForTest(), moneySnapshot(),
                Dispatchers.Unconfined, testDeltaFactory()
            )
            val delayed = async(start = CoroutineStart.UNDISPATCHED) {
                epochA.execute(mutationEnvelope("epoch-a-delayed", "aggregate-a", "a", rngState(7, 1)))
            }
            delayedPort.commitStarted.await()

            owner.activate(SessionEpoch(2))
            val epochBPort = RecordingSavePort()
            val epochB = WorldSession(
                SessionEpoch(2), epochBPort, this, ContentSnapshot.emptyForTest(), moneySnapshot(),
                Dispatchers.Unconfined, testDeltaFactory()
            )
            assertAccepted(epochB.execute(mutationEnvelope(
                "epoch-b-committed", "aggregate-b", "b", rngState(11, 1), sessionEpoch = SessionEpoch(2)
            )), 1)
            val publicationB = checkNotNull(epochB.publications.value)
            val hashB = epochB.inMemoryStateHash()

            delayedPort.releaseCommit.complete(Unit)
            val stale = delayed.await() as CommandResult.Rejected
            assertTrue(stale.error is DomainError.PersistenceFailure)
            assertEquals(0, delayedPort.commitCount)
            assertEquals(null, epochA.publications.value)
            assertEquals(SessionEpoch(2), owner.activeEpoch)
            assertEquals(StateVersion(1), epochBPort.stateVersion)
            assertEquals(1, epochBPort.receiptCount)
            assertEquals(publicationB, epochB.publications.value)
            assertEquals(hashB, epochB.inMemoryStateHash())
            epochA.close()
            epochB.close()
        }
    }

    @Test
    fun `P2-IT-005 actual long advance accepts pause and cancel without duplicate receipt or events`() = runBlocking {
        listOf(
            AdvanceControl.PAUSE to TimeAdvanceResult.INTERRUPTED,
            AdvanceControl.CANCEL_ADVANCE to TimeAdvanceResult.CANCELLED
        ).forEach { (control, terminalStatus) ->
            val source = SequentialAdvanceSource(200)
            val initial = activeAdvanceSnapshot(source)
            val port = ControlledAdvancePort(initial)
            val session = WorldSession(
                SessionEpoch(1),
                port,
                this,
                ContentSnapshot.emptyForTest(),
                initial,
                WorldEngine(listOf(source), BoundaryEvaluator { snapshot, candidate ->
                    BoundaryEvaluation(snapshot, listOf(candidate.eventDraft()))
                }),
                Dispatchers.Unconfined
            )
            val commandId = CommandId("actual-${control.name.lowercase()}")
            val payload = AdvanceTimePayload(
                TimeAdvanceGoal.UntilMinute(worldSessionTestMinute(200)),
                ProgressionMode.FAST_FORWARD,
                TimeTraversalLimits(worldSessionTestMinute(200), maxBoundaryCount = 200)
            )
            val execution = async(start = CoroutineStart.UNDISPATCHED) {
                session.execute(CommandEnvelope.create(commandId, SessionEpoch(1), StateVersion(0), null, payload))
            }

            withTimeout(5_000) { port.firstSegmentEntered.await() }
            assertEquals(commandId, session.runtimeState.value.activeAdvanceCommandId)
            assertEquals(
                ControlRequestResult.Accepted(commandId),
                session.requestControl(ControlRequest(SessionEpoch(1), commandId, control, allowCommitDrain = true))
            )

            port.releaseFirstSegment.complete(Unit)
            assertEquals(CommandResult.Accepted(1), execution.await())
            val receipt = port.receipts.values.single()
            assertEquals(terminalStatus, receipt.timeAdvanceState?.status)
            val publication = checkNotNull(session.publications.value)
            assertEquals(commandId, publication.sourceCommandId)
            assertEquals(PublicTimeAdvanceTerminal(commandId, terminalStatus), publication.timeAdvanceTerminal)
            assertEquals(1, port.receipts.size)
            val events = port.committedEvents.flatten()
            assertTrue(events.isNotEmpty())
            assertEquals(events.size, events.map { it.eventId }.distinct().size)
            assertTrue(events.all { it.sourceCommandId == commandId })
            assertEquals(null, session.runtimeState.value.activeAdvanceCommandId)
            session.close()
        }

        val source = SequentialAdvanceSource(200)
        val initial = activeAdvanceSnapshot(source)
        val port = ControlledAdvancePort(initial)
        val session = WorldSession(
            SessionEpoch(1), port, this, ContentSnapshot.emptyForTest(), initial,
            WorldEngine(listOf(source), BoundaryEvaluator { snapshot, candidate ->
                BoundaryEvaluation(snapshot, listOf(candidate.eventDraft()))
            }), Dispatchers.Unconfined
        )
        val commandId = CommandId("actual-close")
        val execution = async(start = CoroutineStart.UNDISPATCHED) {
            session.execute(
                CommandEnvelope.create(
                    commandId, SessionEpoch(1), StateVersion(0), null,
                    AdvanceTimePayload(
                        TimeAdvanceGoal.UntilMinute(worldSessionTestMinute(200)),
                        ProgressionMode.FAST_FORWARD,
                        TimeTraversalLimits(worldSessionTestMinute(200), maxBoundaryCount = 200)
                    )
                )
            )
        }
        withTimeout(5_000) { port.firstSegmentEntered.await() }
        val close = async(start = CoroutineStart.UNDISPATCHED) { session.close(CloseReason.APPLICATION_REQUEST) }
        assertFalse(close.isCompleted)
        port.releaseFirstSegment.complete(Unit)
        assertEquals(CommandResult.Accepted(1), execution.await())
        assertEquals(TimeAdvanceResult.INTERRUPTED, port.receipts.values.single().timeAdvanceState?.status)
        assertEquals(1, port.receipts.size)
        assertEquals(0, close.await().outstandingJobs)
        val committedHash = session.inMemoryStateHash()

        val restored = WorldSession(
            SessionEpoch(2), port, this, ContentSnapshot.emptyForTest(), port.restoredSnapshot(),
            WorldEngine(listOf(source), BoundaryEvaluator { snapshot, candidate ->
                BoundaryEvaluation(snapshot, listOf(candidate.eventDraft()))
            }), Dispatchers.Unconfined
        )
        assertEquals(null, restored.runtimeState.value.activeAdvanceCommandId)
        assertEquals(committedHash, restored.inMemoryStateHash())
        restored.close()
    }

    @Test
    fun `P2-CN-001 serializes gameplay FIFO and applies pause cancel only at a safe boundary`() = runBlocking {
        withTimeout(5_000) {
            val savePort = RecordingSavePort()
            val dispatcher = PausedDispatcher()
            val session = WorldSession(SessionEpoch(1), savePort, this, ContentSnapshot.emptyForTest(), dispatcher)
            val queued = (0 until 3).map { index ->
                async(start = CoroutineStart.UNDISPATCHED) {
                    session.execute(unsupportedEnvelope("cn-$index", "cn-$index", expectedVersion = null))
                }
            }
            dispatcher.runUntilIdle()
            queued.forEachIndexed { index, result -> assertUnsupported(result.await(), index + 1L) }
            assertEquals(listOf("cn-0", "cn-1", "cn-2"), savePort.commandIds)
            val close = async(start = CoroutineStart.UNDISPATCHED) { session.close() }
            dispatcher.runUntilIdle()
            close.await()

            listOf(
                AdvanceControl.PAUSE to TimeAdvanceResult.INTERRUPTED,
                AdvanceControl.CANCEL_ADVANCE to TimeAdvanceResult.CANCELLED
            ).forEach { (control, terminalStatus) ->
                val source = SequentialAdvanceSource(2)
                val initial = activeAdvanceSnapshot(source)
                val port = ControlledAdvancePort(initial)
                val active = activeAdvanceSession(this, source, initial, port)
                val commandId = CommandId("cn-${control.name.lowercase()}")
                val execution = async(start = CoroutineStart.UNDISPATCHED) {
                    active.execute(advanceEnvelope(commandId, SessionEpoch(1)))
                }
                port.firstSegmentEntered.await()
                assertEquals(
                    ControlRequestResult.Accepted(commandId),
                    active.requestControl(ControlRequest(SessionEpoch(1), commandId, control, allowCommitDrain = true))
                )
                port.releaseFirstSegment.complete(Unit)
                assertAccepted(execution.await(), 1)
                assertEquals(terminalStatus, port.receipts.values.single().timeAdvanceState?.status)
                assertEquals(commandId, checkNotNull(active.publications.value).sourceCommandId)
                val events = port.committedEvents.flatten()
                assertEquals(events.size, events.map { it.eventId }.distinct().size)
                assertTrue(events.all { it.sourceCommandId == commandId })
                assertEquals(1, port.receipts.size)
                active.close()
            }
        }
    }

    @Test
    fun `P2-CT-005 duplicate pause close and resume do not persist directly`() = runBlocking {
        val savePort = RecordingSavePort()
        val session = WorldSession(SessionEpoch(1), savePort, this, ContentSnapshot.emptyForTest(), Dispatchers.Unconfined)

        SavePortConformanceSuite.assertNoLifecyclePersistence(savePort) {
            assertEquals(SessionLifecycle.PAUSED, session.pause().lifecycle)
            assertEquals(SessionLifecycle.PAUSED, session.pause().lifecycle)
            assertEquals(SessionLifecycle.OPEN, session.resume().lifecycle)
            assertEquals(SessionLifecycle.OPEN, session.resume().lifecycle)
            val firstClose = session.close(CloseReason.APPLICATION_REQUEST)
            assertEquals(firstClose, session.close(CloseReason.PARENT_SCOPE))
            assertEquals(SessionLifecycle.CLOSED, session.resume().lifecycle)
        }
    }

    @Test
    fun `writer lease contract waits for close drain and ignores stale or duplicate release`() = runBlocking {
        val port = CloseBarrierSavePort()
        lateinit var oldSession: WorldSession
        val oldHandle = checkNotNull(ProcessWorldSessionCoordinator.acquireForTest {
            WorldSession(
                SessionEpoch(1), port, this, ContentSnapshot.emptyForTest(), Dispatchers.Unconfined, testDeltaFactory()
            ).also { oldSession = it }
        })
        val before = oldSession.inMemoryStateHash()
        val execution = async(start = CoroutineStart.UNDISPATCHED) {
            oldHandle.execute(mutationEnvelope("p2-ct-005", "close-barrier", "before", rngState(42, 1)))
        }

        withTimeout(5_000) { port.commitStarted.await() }
        val firstClose = async(start = CoroutineStart.UNDISPATCHED) { oldHandle.close(CloseReason.APPLICATION_REQUEST) }
        val duplicateClose = async(start = CoroutineStart.UNDISPATCHED) { oldHandle.close(CloseReason.PARENT_SCOPE) }
        assertFalse(firstClose.isCompleted)
        assertFalse(duplicateClose.isCompleted)
        assertEquals(null, ProcessWorldSessionCoordinator.acquireForTest { error("must not construct while close drains") })
        assertEquals(1, port.commitCount)
        assertEquals(before, oldSession.inMemoryStateHash())
        assertEquals(null, oldSession.publications.value)

        port.allowCommit.complete(Unit)
        assertAccepted(execution.await(), 1)
        val oldCloseResult = firstClose.await()
        assertEquals(oldCloseResult, duplicateClose.await())

        val currentHandle = checkNotNull(ProcessWorldSessionCoordinator.acquireForTest {
            WorldSession(SessionEpoch(2), RecordingSavePort(), this, ContentSnapshot.emptyForTest(), Dispatchers.Unconfined)
        })
        assertEquals(oldCloseResult, oldHandle.close(CloseReason.APPLICATION_REQUEST))
        assertEquals(null, ProcessWorldSessionCoordinator.acquireForTest { error("stale close must not release current writer") })
        val currentClose = currentHandle.close()
        assertEquals(currentClose, currentHandle.close(CloseReason.PARENT_SCOPE))

        val nextHandle = checkNotNull(ProcessWorldSessionCoordinator.acquireForTest {
            WorldSession(SessionEpoch(3), RecordingSavePort(), this, ContentSnapshot.emptyForTest(), Dispatchers.Unconfined)
        })
        nextHandle.close()
        assertEquals(1, port.commitCount)
    }

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
                RngStreamState(RngStreamKey("world"), "pcg32.v1", 1, 1, 1),
                RngStreamState(RngStreamKey("encounter"), "pcg32.v1", 2, 3, 3)
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
            visibility = EventVisibility.SYSTEM_HIDDEN,
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
            val session = WorldSession(SessionEpoch(1), savePort, this, ContentSnapshot.emptyForTest())
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
            val session = WorldSession(SessionEpoch(1), savePort, this, ContentSnapshot.emptyForTest(), dispatcher)
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
    fun `full command queue rejects overflow without blocking pause or close`() = runBlocking {
        withTimeout(5_000) {
            val savePort = RecordingSavePort()
            val dispatcher = PausedDispatcher()
            val session = WorldSession(SessionEpoch(1), savePort, this, ContentSnapshot.emptyForTest(), dispatcher)
            val accepted = (0 until 64).map { index ->
                async(start = CoroutineStart.UNDISPATCHED) {
                    session.execute(unsupportedEnvelope("queued-$index", "phase-$index", expectedVersion = null))
                }
            }
            val overflow = session.execute(unsupportedEnvelope("queued-overflow", "phase-overflow", expectedVersion = null))

            assertEquals(DomainError.ValidationError("commandQueue", "capacity exceeded"), (overflow as CommandResult.Rejected).error)
            assertEquals(SessionLifecycle.PAUSED, session.pause().lifecycle)
            val close = async(start = CoroutineStart.UNDISPATCHED) { session.close() }
            dispatcher.runUntilIdle()
            accepted.forEachIndexed { index, command -> assertUnsupported(command.await(), index + 1L) }
            close.await()
            assertEquals((0 until 64).map { "queued-$it" }, savePort.commandIds)
        }
    }

    @Test
    fun `save port cancellation completes the current reply and preserves the consumer`() = runBlocking {
        withTimeout(5_000) {
            CancellationPoint.entries.forEach { point ->
                val savePort = CancellingSavePort(point)
                val session = WorldSession(SessionEpoch(1), savePort, this, ContentSnapshot.emptyForTest(), Dispatchers.Unconfined)

                val cancellation = runCatching {
                    session.execute(unsupportedEnvelope("command-cancelled-$point", "phase-cancelled"))
                }.exceptionOrNull()

                assertTrue(cancellation is CancellationException)
                assertUnsupported(
                    session.execute(unsupportedEnvelope("command-after-cancel-$point", "phase-after-cancel")),
                    2
                )
                assertEquals(listOf("command-after-cancel-$point"), savePort.commandIds)
                session.close()
            }
        }
    }

    @Test
    fun `cancelled commit restores its durable receipt before propagating cancellation`() = runBlocking {
        withTimeout(5_000) {
            val savePort = CommitThenCancellingSavePort()
            val session = WorldSession(SessionEpoch(1), savePort, this, ContentSnapshot.emptyForTest(), Dispatchers.Unconfined, testDeltaFactory())
            val envelope = mutationEnvelope("command-cancelled-commit", "aggregate-a", "a", rngState(42, 1))
            val before = session.inMemoryStateHash()

            val cancellation = runCatching { session.execute(envelope) }.exceptionOrNull()

            assertTrue(cancellation is CancellationException)
            assertNotEquals(before, session.inMemoryStateHash())
            assertAccepted(session.execute(envelope), 1)
            assertUnsupported(
                session.execute(unsupportedEnvelope("command-after-cancelled-commit", "phase-after", StateVersion(1))),
                3
            )
            assertEquals(listOf("command-cancelled-commit", "command-after-cancelled-commit"), savePort.commandIds)
            session.close()
        }
    }

    @Test
    fun `P2-FT-005 control and commit failure preserve the prior state`() = runBlocking {
        withTimeout(5_000) {
            val source = SequentialAdvanceSource(2)
            val initial = activeAdvanceSnapshot(source)
            val faultPort = ControlledAdvancePort(initial, failFirstSegment = true)
            val session = activeAdvanceSession(this, source, initial, faultPort)
            val before = session.inMemoryStateHash()
            val commandId = CommandId("advance-faulted-pause")
            val execution = async(start = CoroutineStart.UNDISPATCHED) {
                session.execute(advanceEnvelope(commandId, SessionEpoch(1)))
            }
            faultPort.firstSegmentEntered.await()
            assertEquals(
                ControlRequestResult.Accepted(commandId),
                session.requestControl(ControlRequest(SessionEpoch(1), commandId, AdvanceControl.PAUSE, allowCommitDrain = true))
            )
            faultPort.releaseFirstSegment.complete(Unit)
            assertTrue((execution.await() as CommandResult.Rejected).error is DomainError.PersistenceFailure)
            assertEquals(before, session.inMemoryStateHash())
            assertEquals(null, session.publications.value)
            assertTrue(faultPort.receipts.isEmpty())
            assertTrue(faultPort.committedEvents.isEmpty())
            assertEquals(0, session.close(CloseReason.APPLICATION_REQUEST).outstandingJobs)

            val restartPort = ControlledAdvancePort(faultPort.restoredSnapshot())
            val restarted = activeAdvanceSession(this, source, restartPort.restoredSnapshot(), restartPort, SessionEpoch(2))
            val retry = async(start = CoroutineStart.UNDISPATCHED) {
                restarted.execute(advanceEnvelope(CommandId("advance-retry"), SessionEpoch(2)))
            }
            restartPort.firstSegmentEntered.await()
            restartPort.releaseFirstSegment.complete(Unit)
            assertAccepted(retry.await(), 1)
            assertEquals(1, restartPort.receipts.size)
            assertTrue(restarted.publications.value != null)
            restarted.close()

            val closePort = ControlledAdvancePort(initial)
            val closing = activeAdvanceSession(this, source, initial, closePort)
            val closeCommandId = CommandId("advance-close-drain")
            val protected = async(start = CoroutineStart.UNDISPATCHED) {
                closing.execute(advanceEnvelope(closeCommandId, SessionEpoch(1)))
            }
            closePort.firstSegmentEntered.await()
            val close = async(start = CoroutineStart.UNDISPATCHED) { closing.close(CloseReason.APPLICATION_REQUEST) }
            assertFalse(close.isCompleted)
            closePort.releaseFirstSegment.complete(Unit)
            assertAccepted(protected.await(), 1)
            assertEquals(TimeAdvanceResult.INTERRUPTED, closePort.receipts.values.single().timeAdvanceState?.status)
            assertEquals(1, closePort.receipts.size)
            assertEquals(closePort.committedEvents.flatten().size, closePort.committedEvents.flatten().map { it.eventId }.distinct().size)
            assertEquals(0, close.await().outstandingJobs)
        }
    }

    @Test
    fun `parent scope cancellation completes replies that were queued but not started`() = runBlocking {
        withTimeout(5_000) {
            val parentJob = SupervisorJob()
            val requestScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
            val dispatcher = PausedDispatcher()
            val session = WorldSession(
                SessionEpoch(1),
                RecordingSavePort(),
                CoroutineScope(parentJob + Dispatchers.Unconfined),
                ContentSnapshot.emptyForTest(),
                dispatcher
            )
            val first = requestScope.async(start = CoroutineStart.UNDISPATCHED) {
                session.execute(unsupportedEnvelope("command-parent-cancel-a", "phase-a"))
            }
            val second = requestScope.async(start = CoroutineStart.UNDISPATCHED) {
                session.execute(unsupportedEnvelope("command-parent-cancel-b", "phase-b"))
            }

            parentJob.cancel()
            dispatcher.runUntilIdle()

            assertTrue(runCatching { first.await() }.exceptionOrNull() is CancellationException)
            assertTrue(runCatching { second.await() }.exceptionOrNull() is CancellationException)
            session.close()
            requestScope.cancel()
        }
    }

    @Test
    fun `pre enqueue cancellation does not consume a submission sequence`() = runBlocking {
        val session = WorldSession(SessionEpoch(1), RecordingSavePort(), this, ContentSnapshot.emptyForTest())
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
        val session = WorldSession(SessionEpoch(1), savePort, this, ContentSnapshot.emptyForTest())

        assertUnsupported(session.execute(unsupportedEnvelope("command-a", "phase-a")), 1)
        assertUnsupported(session.execute(unsupportedEnvelope("command-b", "phase-b")), 2)
        assertUnsupported(session.execute(unsupportedEnvelope("command-c", "phase-c")), 3)
        assertEquals(listOf("command-a", "command-b", "command-c"), savePort.commandIds)
        session.close()
    }

    @Test
    fun `accepted delta commits before publishing state and a failed commit rolls back`() = runBlocking {
        val savePort = RecordingSavePort()
        val session = WorldSession(SessionEpoch(1), savePort, this, ContentSnapshot.emptyForTest(), Dispatchers.Unconfined, testDeltaFactory())
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

        val failed = WorldSession(SessionEpoch(1), FailingSavePort(), this, ContentSnapshot.emptyForTest(), Dispatchers.Unconfined, testDeltaFactory())
        val failedBefore = failed.inMemoryStateHash()
        val failure = failed.execute(mutationEnvelope("command-failed", "aggregate-a", "a", rngState(42, 1)))
        assertTrue((failure as CommandResult.Rejected).error is DomainError.PersistenceFailure)
        assertEquals(failedBefore, failed.inMemoryStateHash())
        failed.close()
    }

    @Test
    fun `authoritative state hash includes ledger binding and time advance state`() = runBlocking {
        val resource = ResourceIdentity("MATERIAL", "ore")
        val goal = TimeAdvanceGoal.UntilMinute(checkedValue(GameMinute.of(10)))
        val completed = TimeAdvanceState(
            goal, null, 0, TimeAdvanceResult.COMPLETED,
            commandEpoch = SessionEpoch(1), commandId = CommandId("advance-hash"),
            progressionMode = ProgressionMode.FAST_FORWARD,
            limits = TimeTraversalLimits(checkedValue(GameMinute.of(10)), 8)
        )
        suspend fun hash(owned: Long, bindingCodec: String, state: TimeAdvanceState): PayloadHash {
            val session = WorldSession(
                SessionEpoch(1),
                RecordingSavePort(),
                this,
                ContentSnapshot.emptyForTest(),
                WorldSnapshot(
                    StateVersion(3),
                    AuthoritativeWorldState(
                        WorldClock(checkedValue(GameMinute.of(7))),
                        ScheduleCalendar(emptyList(), ResourceLedger(mapOf(resource to owned))),
                        state,
                        BoundaryRegistryBinding(1, emptyList(), candidateCodecs = setOf(bindingCodec))
                    ),
                    rngState(42, 3),
                    emptyMap()
                ),
                WorldEngine(emptyList()),
                Dispatchers.Unconfined
            )
            return try {
                session.inMemoryStateHash()
            } finally {
                session.close()
            }
        }

        val base = hash(10, "TestPayload.v1", completed)
        assertNotEquals(base, hash(11, "TestPayload.v1", completed))
        assertNotEquals(base, hash(10, "OtherPayload.v1", completed))
        assertNotEquals(base, hash(10, "TestPayload.v1", completed.copy(status = TimeAdvanceResult.LIMIT_REACHED)))
    }

    @Test
    fun `invalid event metadata is rejected before persistence`() = runBlocking {
        val savePort = RecordingSavePort()
        val session = WorldSession(SessionEpoch(1), savePort, this, ContentSnapshot.emptyForTest(), Dispatchers.Unconfined, testDeltaFactory(validEventMetadata = false))
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
                ContentSnapshot.emptyForTest(),
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
            val session = WorldSession(SessionEpoch(1), savePort, this, ContentSnapshot.emptyForTest(), dispatcher, testDeltaFactory())
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
        val session = WorldSession(SessionEpoch(1), savePort, this, ContentSnapshot.emptyForTest(), Dispatchers.Unconfined, testDeltaFactory())
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
            "$aggregateId|$aggregateHash|${rngState.streams.single().state}|${rngState.streams.single().drawCounter}"
        )
    )

    private fun moneySnapshot(): WorldSnapshot = WorldSnapshot(
        StateVersion(0),
        AuthoritativeWorldState(
            WorldClock(checkedValue(GameMinute.of(0))),
            ScheduleCalendar(emptyList(), mapOf(gold to 100L)),
            null,
            BoundaryRegistryBinding(1, emptyList())
        ),
        RngState(emptyList()),
        emptyMap()
    )

    private fun kotlinx.coroutines.CoroutineScope.moneySession(savePort: RecordingSavePort): WorldSession = WorldSession(
        SessionEpoch(1),
        savePort,
        this,
        ContentSnapshot.emptyForTest(),
        moneySnapshot(),
        WorldEngine(emptyList()),
        Dispatchers.Unconfined
    )

    private fun moneyEnvelope(
        commandId: String,
        actionId: String = "purchase",
        expectedVersion: StateVersion? = StateVersion(0)
    ): CommandEnvelope<ScheduleReservePayload> = CommandEnvelope.create(
        CommandId(commandId),
        SessionEpoch(1),
        expectedVersion,
        entityId("actor-1"),
        ScheduleReservePayload(
            entityId(actionId),
            "economy.purchase",
            ScheduledActionPayload(
                "PLAYER",
                entityId("actor-1"),
                listOf(ScheduledEntityRef("PLAYER", entityId("actor-1"))),
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
            checkedValue(GameMinute.of(1)),
            checkedValue(GameMinute.of(2)),
            checkedValue(GameMinute.of(0))
        )
    )

    private val gold = ResourceIdentity("CURRENCY", "gold")

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
                        visibility = EventVisibility.SYSTEM_HIDDEN,
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
        listOf(RngStreamState(RngStreamKey("world"), "phase0-test.v1", state, 1, counter))
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
        var receiptLookups = 0
        val callCount: Int get() = receiptLookups + commandIds.size
        private var committedVersion = StateVersion(0)
        private val receipts = mutableMapOf<Pair<SessionEpoch, CommandId>, PersistedReceipt>()
        val stateVersion: StateVersion get() = committedVersion
        val receiptCount: Int get() = receipts.size

        override suspend fun findReceipt(sessionEpoch: SessionEpoch, commandId: CommandId): PersistedReceipt? {
            receiptLookups++
            return receipts[sessionEpoch to commandId]
        }

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

    private enum class CancellationPoint { FIND_RECEIPT, COMMIT }

    private class CancellingSavePort(private val point: CancellationPoint) : SavePort {
        private val delegate = RecordingSavePort()
        private var cancelled = false

        val commandIds: List<String> get() = delegate.commandIds

        override suspend fun findReceipt(sessionEpoch: SessionEpoch, commandId: CommandId): PersistedReceipt? {
            cancelIfNeeded(CancellationPoint.FIND_RECEIPT)
            return delegate.findReceipt(sessionEpoch, commandId)
        }

        override suspend fun commit(
            envelope: CommandEnvelope<out WorldCommandPayload>,
            delta: DomainDelta
        ): CommitReceipt {
            cancelIfNeeded(CancellationPoint.COMMIT)
            return delegate.commit(envelope, delta)
        }

        private fun cancelIfNeeded(actualPoint: CancellationPoint) {
            if (!cancelled && point == actualPoint) {
                cancelled = true
                throw CancellationException("test $point cancellation")
            }
        }
    }

    private class CommitThenCancellingSavePort : SavePort {
        private val delegate = RecordingSavePort()
        private var cancelled = false

        val commandIds: List<String> get() = delegate.commandIds

        override suspend fun findReceipt(sessionEpoch: SessionEpoch, commandId: CommandId): PersistedReceipt? =
            delegate.findReceipt(sessionEpoch, commandId)

        override suspend fun commit(
            envelope: CommandEnvelope<out WorldCommandPayload>,
            delta: DomainDelta
        ): CommitReceipt {
            val receipt = delegate.commit(envelope, delta)
            if (!cancelled) {
                cancelled = true
                throw CancellationException("test cancellation after commit")
            }
            return receipt
        }
    }

    private class CommitThenFailingReceiptLookupSavePort : SavePort {
        private val delegate = RecordingSavePort()
        private var commitCancelled = false

        override suspend fun findReceipt(sessionEpoch: SessionEpoch, commandId: CommandId): PersistedReceipt? {
            if (commitCancelled) throw CancellationException("test receipt lookup cancellation")
            return delegate.findReceipt(sessionEpoch, commandId)
        }

        override suspend fun commit(
            envelope: CommandEnvelope<out WorldCommandPayload>,
            delta: DomainDelta
        ): CommitReceipt {
            delegate.commit(envelope, delta)
            commitCancelled = true
            throw CancellationException("test cancellation after commit")
        }
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

    private fun activeAdvanceSession(
        scope: CoroutineScope,
        source: BoundarySource,
        initial: WorldSnapshot,
        port: ControlledAdvancePort,
        epoch: SessionEpoch = SessionEpoch(1)
    ): WorldSession = WorldSession(
        epoch, port, scope, ContentSnapshot.emptyForTest(), initial,
        WorldEngine(listOf(source), BoundaryEvaluator { snapshot, candidate ->
            BoundaryEvaluation(snapshot, listOf(candidate.eventDraft()))
        }), Dispatchers.Unconfined
    )

    private fun advanceEnvelope(commandId: CommandId, epoch: SessionEpoch): CommandEnvelope<AdvanceTimePayload> =
        CommandEnvelope.create(
            commandId, epoch, StateVersion(0), null,
            AdvanceTimePayload(
                TimeAdvanceGoal.UntilMinute(worldSessionTestMinute(2)),
                ProgressionMode.FAST_FORWARD,
                TimeTraversalLimits(worldSessionTestMinute(2), maxBoundaryCount = 2)
            )
        )

    /** Test-only delayed epoch oracle; the production writer lease remains P2-TASK-023/024 scope. */
    private class EpochOwnerFixture(initialEpoch: SessionEpoch) {
        var activeEpoch: SessionEpoch? = initialEpoch
            private set

        fun activate(epoch: SessionEpoch) {
            activeEpoch = epoch
        }

        fun release(epoch: SessionEpoch) {
            if (activeEpoch == epoch) activeEpoch = null
        }
    }

    private class DelayedEpochSavePort(
        private val owner: EpochOwnerFixture,
        private val epoch: SessionEpoch
    ) : SavePort {
        val commitStarted = CompletableDeferred<Unit>()
        val releaseCommit = CompletableDeferred<Unit>()
        var commitCount = 0
            private set

        override suspend fun findReceipt(sessionEpoch: SessionEpoch, commandId: CommandId): PersistedReceipt? = null

        override suspend fun commit(
            envelope: CommandEnvelope<out WorldCommandPayload>,
            delta: DomainDelta
        ): CommitReceipt {
            commitStarted.complete(Unit)
            releaseCommit.await()
            if (owner.activeEpoch != epoch) {
                owner.release(epoch)
                error("stale delayed epoch response")
            }
            commitCount++
            return CommitReceipt(StateVersion(1), delta.result)
        }
    }

    private class ControlledAdvancePort(
        private var snapshot: WorldSnapshot,
        private val failFirstSegment: Boolean = false
    ) : SavePort {
        val firstSegmentEntered = CompletableDeferred<Unit>()
        val releaseFirstSegment = CompletableDeferred<Unit>()
        val receipts = mutableMapOf<Pair<SessionEpoch, CommandId>, PersistedReceipt>()
        val committedEvents = mutableListOf<List<DomainEvent<out DomainEventPayload>>>()
        private var stateVersion = 0L

        override suspend fun findReceipt(sessionEpoch: SessionEpoch, commandId: CommandId): PersistedReceipt? =
            receipts[sessionEpoch to commandId]

        override suspend fun commit(
            envelope: CommandEnvelope<out WorldCommandPayload>,
            delta: DomainDelta
        ): CommitReceipt = error("atomic commit was not expected")

        override suspend fun commitSegment(
            envelope: CommandEnvelope<out WorldCommandPayload>,
            expectedSegmentNo: Int,
            delta: DomainDelta,
            timeAdvanceState: TimeAdvanceState,
            terminalResult: TimeAdvanceResult?,
            continuation: TimeAdvanceContinuation?
        ): SegmentCommitReceipt {
            if (expectedSegmentNo == 0) {
                firstSegmentEntered.complete(Unit)
                releaseFirstSegment.await()
                if (failFirstSegment) error("injected active advance commit failure")
            }
            committedEvents += delta.events
            val lifecycle = when (terminalResult) {
                null -> ReceiptLifecycle.RUNNING
                TimeAdvanceResult.INTERRUPTED, TimeAdvanceResult.DECISION_REQUIRED, TimeAdvanceResult.FAILED -> ReceiptLifecycle.INTERRUPTED
                else -> ReceiptLifecycle.COMMITTED
            }
            val receipt = CommitReceipt(
                StateVersion(++stateVersion),
                delta.result,
                lifecycle,
                expectedSegmentNo
            )
            receipts[envelope.sessionEpoch to envelope.commandId] = PersistedReceipt(
                envelope.payloadHash,
                receipt.stateVersion,
                receipt.result,
                lifecycle,
                expectedSegmentNo,
                timeAdvanceState,
                continuation
            )
            val aggregates = snapshot.aggregates.toMutableMap()
            delta.aggregateChanges.forEach { change ->
                if (change.after == null) aggregates.remove(change.aggregateId) else aggregates[change.aggregateId] = change.after
            }
            snapshot = WorldSnapshot(receipt.stateVersion, delta.worldChange?.after ?: snapshot.world, delta.rngState, aggregates)
            return SegmentCommitReceipt(receipt, expectedSegmentNo, timeAdvanceState)
        }

        fun restoredSnapshot(): WorldSnapshot = snapshot
    }

    private class CloseBarrierSavePort : SavePort {
        val commitStarted = CompletableDeferred<Unit>()
        val allowCommit = CompletableDeferred<Unit>()
        var commitCount = 0
            private set

        override suspend fun findReceipt(sessionEpoch: SessionEpoch, commandId: CommandId): PersistedReceipt? = null

        override suspend fun commit(
            envelope: CommandEnvelope<out WorldCommandPayload>,
            delta: DomainDelta
        ): CommitReceipt {
            commitCount++
            commitStarted.complete(Unit)
            allowCommit.await()
            return CommitReceipt(StateVersion(1), delta.result)
        }
    }

    private class SequentialAdvanceSource(private val lastMinute: Long) : BoundarySource {
        override val sourceId: String = "qa-active-advance"

        override fun nextTimeAfter(snapshot: WorldTraversalSnapshot, cursor: BoundaryCursor?): GameMinute? =
            (snapshot.clock.minute.value + 1).takeIf { it <= lastMinute }?.let(::worldSessionTestMinute)

        override fun candidatesAt(snapshot: WorldTraversalSnapshot, time: GameMinute): List<BoundaryCandidate> = listOf(
            BoundaryCandidate(
                BoundaryKey(time, BoundaryCategory.WORLD_EVENT, 0, 0, "event-${time.value}", "", sourceId),
                "calendar.day.start.v1",
                "CalendarBoundaryPayload.v1",
                "{\"minute\":${time.value}}"
            )
        )
    }

    private fun activeAdvanceSnapshot(source: BoundarySource): WorldSnapshot = WorldSnapshot(
        StateVersion(0),
        AuthoritativeWorldState(
            WorldClock(worldSessionTestMinute(0)),
            ScheduleCalendar(emptyList(), emptyMap()),
            null,
            BoundaryRegistryBinding(1, listOf(source.sourceId), candidateCodecs = setOf("CalendarBoundaryPayload.v1"))
        ),
        RngState(emptyList()),
        emptyMap()
    )

    private object SavePortConformanceSuite {
        suspend fun assertNoLifecyclePersistence(port: RecordingSavePort, action: suspend () -> Unit) {
            val before = port.callCount
            action()
            assertEquals(before, port.callCount)
        }
    }
}
