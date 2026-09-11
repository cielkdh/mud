package com.imsi.mud.simulation

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ClosedSendChannelException
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.LinkedHashMap

class WorldSession private constructor(
    private val epoch: SessionEpoch,
    private val savePort: SavePort,
    parentScope: CoroutineScope,
    dispatcher: CoroutineDispatcher,
    private val deltaFactory: (
        CommandEnvelope<out WorldCommandPayload>,
        StateVersion,
        RngState,
        Long
    ) -> DomainDelta,
    @Suppress("UNUSED_PARAMETER") constructorMarker: Unit
) {
    constructor(
        epoch: SessionEpoch,
        savePort: SavePort,
        parentScope: CoroutineScope,
        dispatcher: CoroutineDispatcher = Dispatchers.Default
    ) : this(
        epoch,
        savePort,
        parentScope,
        dispatcher,
        { envelope, _, rngState, submissionSequence ->
            DomainDelta(
                aggregateChanges = emptyList(),
                rngState = rngState,
                events = emptyList(),
                result = CommandResult.Rejected(
                    DomainError.UnsupportedFeature(
                        (envelope.payload as? UnsupportedFeaturePayload)?.feature ?: envelope.payload.codecId
                    ),
                    submissionSequence
                )
            )
        },
        Unit
    )

    internal constructor(
        epoch: SessionEpoch,
        savePort: SavePort,
        parentScope: CoroutineScope,
        dispatcher: CoroutineDispatcher,
        deltaFactory: (
            CommandEnvelope<out WorldCommandPayload>,
            StateVersion,
            RngState,
            Long
        ) -> DomainDelta
    ) : this(epoch, savePort, parentScope, dispatcher, deltaFactory, Unit)

    private val sessionJob = SupervisorJob(parentScope.coroutineContext[Job])
    private val scope = CoroutineScope(parentScope.coroutineContext + sessionJob + dispatcher)
    private val queue = Channel<QueuedCommand>(capacity = 64)
    private val lifecycleMutex = Mutex()
    private val receipts = object : LinkedHashMap<ReceiptKey, PersistedReceipt>(RECEIPT_CACHE_MAX_ENTRIES + 1, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<ReceiptKey, PersistedReceipt>?): Boolean =
            size > RECEIPT_CACHE_MAX_ENTRIES
    }
    private var lifecycle = Lifecycle.OPEN
    private var nextSubmissionSequence = 0L
    private var stateVersion = StateVersion(0)
    private var rngState = RngState(emptyList())
    private val aggregateStates = linkedMapOf<EntityId, AggregateState>()
    private var closeCompletion: CompletableDeferred<Unit>? = null

    private val consumer = scope.launch {
        for (queued in queue) {
            queued.reply.complete(process(queued.envelope, queued.accepted.await()))
        }
    }

    suspend fun execute(envelope: CommandEnvelope<out WorldCommandPayload>): CommandResult {
        val queued = lifecycleMutex.withLock {
            if (lifecycle != Lifecycle.OPEN) return@withLock null
            val reply = CompletableDeferred<CommandResult>()
            val item = QueuedCommand(
                envelope = envelope,
                reply = reply
            )
            try {
                queue.send(item)
                withContext(NonCancellable) {
                    item.accepted.complete(++nextSubmissionSequence)
                }
                item
            } catch (_: ClosedSendChannelException) {
                null
            }
        }
        return queued?.reply?.await()
            ?: CommandResult.Rejected(DomainError.SessionClosed, submissionSequence = null)
    }

    suspend fun close() {
        val (completion, startsDrain) = lifecycleMutex.withLock {
            closeCompletion?.let { return@withLock it to false }
            CompletableDeferred<Unit>().also {
                closeCompletion = it
                lifecycle = Lifecycle.CLOSING
                queue.close()
            } to true
        }
        if (startsDrain) {
            try {
                withContext(NonCancellable) { consumer.join() }
            } finally {
                lifecycleMutex.withLock { lifecycle = Lifecycle.CLOSED }
                sessionJob.cancel()
                completion.complete(Unit)
            }
        }
        completion.await()
    }

    internal fun inMemoryStateHash(): PayloadHash {
        val canonical = buildString {
            append("P0-IN-MEMORY-STATE\n")
            rngState.streams
                .sortedBy { it.streamKey.value }
                .forEach { stream ->
                    append("rng=").append(stream.streamKey.value).append(':')
                        .append(stream.algorithmVersion).append(':').append(stream.state).append(':')
                        .append(stream.counter).append('\n')
                }
            aggregateStates.entries
                .sortedBy { (id, _) -> id.value }
                .forEach { (id, state) ->
                    append(id.value).append('=').append(state.aggregateType).append(':')
                        .append(state.canonicalJson()).append('\n')
                }
        }
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(canonical.toByteArray(StandardCharsets.UTF_8))
        return PayloadHash(digest.joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) })
    }

    private suspend fun process(
        envelope: CommandEnvelope<out WorldCommandPayload>,
        submissionSequence: Long
    ): CommandResult {
        if (envelope.sessionEpoch != epoch) {
            return CommandResult.Rejected(DomainError.StaleSession(epoch, envelope.sessionEpoch), submissionSequence)
        }
        if (CommandPayloadCodec.hash(envelope.payload) != envelope.payloadHash) {
            return CommandResult.Rejected(
                DomainError.ValidationError("payloadHash", "does not match the canonical payload"),
                submissionSequence
            )
        }

        val receiptKey = ReceiptKey(envelope.sessionEpoch, envelope.commandId)
        receipts[receiptKey]?.let { stored ->
            return if (stored.payloadHash == envelope.payloadHash) stored.result
            else CommandResult.Rejected(DomainError.IdempotencyKeyReuse(envelope.commandId), submissionSequence)
        }

        val persistedReceipt = try {
            savePort.findReceipt(envelope.sessionEpoch, envelope.commandId)
        } catch (error: kotlinx.coroutines.CancellationException) {
            throw error
        } catch (error: Exception) {
            return CommandResult.Rejected(
                DomainError.PersistenceFailure(error.message ?: error::class.simpleName.orEmpty()),
                submissionSequence
            )
        }
        persistedReceipt?.let { stored ->
            return if (stored.payloadHash == envelope.payloadHash) {
                receipts[receiptKey] = stored
                stored.result
            } else {
                CommandResult.Rejected(DomainError.IdempotencyKeyReuse(envelope.commandId), submissionSequence)
            }
        }

        envelope.expectedVersion?.let { expected ->
            if (expected != stateVersion) {
                return CommandResult.Rejected(DomainError.VersionConflict(expected, stateVersion), submissionSequence)
            }
        }

        val candidateAcceptedVersion = try {
            StateVersion(Math.addExact(stateVersion.value, 1))
        } catch (_: ArithmeticException) {
            return CommandResult.Rejected(DomainError.InvariantViolation("state version overflow"), submissionSequence)
        }
        val delta = try {
            deltaFactory(envelope, candidateAcceptedVersion, rngState, submissionSequence)
        } catch (error: kotlinx.coroutines.CancellationException) {
            throw error
        } catch (error: Exception) {
            return CommandResult.Rejected(DomainError.InvariantViolation(error.message ?: error::class.simpleName.orEmpty()), submissionSequence)
        }
        if (delta.result.submissionSequence != submissionSequence) {
            return CommandResult.Rejected(
                DomainError.InvariantViolation("delta result submission sequence mismatch"),
                submissionSequence
            )
        }
        val nextAggregateStates = aggregateStates.toMutableMap()
        for (change in delta.aggregateChanges) {
            if (!sameAggregateState(nextAggregateStates[change.aggregateId], change.before)) {
                return CommandResult.Rejected(
                    DomainError.InvariantViolation("aggregate state precondition failed: ${change.aggregateId.value}"),
                    submissionSequence
                )
            }
            if (change.after == null) nextAggregateStates.remove(change.aggregateId)
            else nextAggregateStates[change.aggregateId] = change.after
        }
        if (delta.result is CommandResult.Rejected &&
            (delta.aggregateChanges.isNotEmpty() || delta.rngState != rngState)
        ) {
            return CommandResult.Rejected(
                DomainError.InvariantViolation("rejected command must not change world state or RNG"),
                submissionSequence
            )
        }
        val eventVersion = if (delta.result is CommandResult.Accepted) candidateAcceptedVersion else stateVersion
        validateEventMetadata(envelope, delta.events, eventVersion)?.let { reason ->
            return CommandResult.Rejected(DomainError.InvariantViolation(reason), submissionSequence)
        }

        val receipt = try {
            savePort.commit(envelope, delta)
        } catch (error: kotlinx.coroutines.CancellationException) {
            throw error
        } catch (error: Exception) {
            return CommandResult.Rejected(DomainError.PersistenceFailure(error.message ?: error::class.simpleName.orEmpty()), submissionSequence)
        }
        if (receipt.result != delta.result) {
            return CommandResult.Rejected(DomainError.PersistenceFailure("commit receipt result mismatch"), submissionSequence)
        }

        when (delta.result) {
            is CommandResult.Accepted -> {
                if (receipt.stateVersion != candidateAcceptedVersion) {
                    return CommandResult.Rejected(DomainError.PersistenceFailure("commit receipt state version mismatch"), submissionSequence)
                }
                aggregateStates.clear()
                aggregateStates.putAll(nextAggregateStates)
                rngState = delta.rngState
                stateVersion = receipt.stateVersion
            }

            is CommandResult.Rejected -> if (receipt.stateVersion != stateVersion) {
                return CommandResult.Rejected(DomainError.PersistenceFailure("rejected receipt changed state version"), submissionSequence)
            }
        }
        receipts[receiptKey] = PersistedReceipt(envelope.payloadHash, receipt.stateVersion, receipt.result)
        return receipt.result
    }

    private fun sameAggregateState(actual: AggregateState?, expected: AggregateState?): Boolean =
        actual?.aggregateId == expected?.aggregateId &&
            actual?.aggregateType == expected?.aggregateType &&
            actual?.canonicalJson() == expected?.canonicalJson()

    private fun validateEventMetadata(
        envelope: CommandEnvelope<out WorldCommandPayload>,
        events: List<DomainEvent<out DomainEventPayload>>,
        expectedVersion: StateVersion
    ): String? = events.withIndex().firstOrNull { (index, event) ->
        event.sourceEpoch != envelope.sessionEpoch ||
            event.sourceCommandId != envelope.commandId ||
            event.sourceVersion != expectedVersion ||
            event.eventSequence != EventSequence(index.toLong())
    }?.let { (index, _) -> "event metadata mismatch at index $index" }

    private data class QueuedCommand(
        val envelope: CommandEnvelope<out WorldCommandPayload>,
        val reply: CompletableDeferred<CommandResult>,
        val accepted: CompletableDeferred<Long> = CompletableDeferred()
    )

    private data class ReceiptKey(val epoch: SessionEpoch, val commandId: CommandId)

    private enum class Lifecycle { OPEN, CLOSING, CLOSED }

    internal companion object {
        const val RECEIPT_CACHE_MAX_ENTRIES = 256
    }
}
