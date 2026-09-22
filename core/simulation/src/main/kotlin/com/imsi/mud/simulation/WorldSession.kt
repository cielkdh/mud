package com.imsi.mud.simulation

import com.imsi.mud.content.ContentSnapshot
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.LinkedHashMap

class WorldSession private constructor(
    private val contentSnapshot: ContentSnapshot,
    private val epoch: SessionEpoch,
    private val savePort: SavePort,
    parentScope: CoroutineScope,
    dispatcher: CoroutineDispatcher,
    private val initialSnapshot: WorldSnapshot?,
    private val engine: WorldEngine?,
    private val deltaFactory: (
        CommandEnvelope<out WorldCommandPayload>,
        StateVersion,
        RngState,
        Long
    ) -> DomainDelta,
    summaryVisibleEventVisibilities: Set<EventVisibility>,
    @Suppress("UNUSED_PARAMETER") constructorMarker: Unit
) {
    internal constructor(
        epoch: SessionEpoch,
        savePort: SavePort,
        parentScope: CoroutineScope,
        contentSnapshot: ContentSnapshot,
        dispatcher: CoroutineDispatcher = Dispatchers.Default
    ) : this(
        contentSnapshot,
        epoch,
        savePort,
        parentScope,
        dispatcher,
        null,
        null,
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
        setOf(EventVisibility.PUBLIC),
        Unit
    )

    internal constructor(
        epoch: SessionEpoch,
        savePort: SavePort,
        parentScope: CoroutineScope,
        contentSnapshot: ContentSnapshot,
        dispatcher: CoroutineDispatcher,
        deltaFactory: (
            CommandEnvelope<out WorldCommandPayload>,
            StateVersion,
            RngState,
            Long
        ) -> DomainDelta
    ) : this(
        contentSnapshot,
        epoch,
        savePort,
        parentScope,
        dispatcher,
        null,
        null,
        deltaFactory,
        setOf(EventVisibility.PUBLIC),
        Unit
    )

    internal constructor(
        epoch: SessionEpoch,
        savePort: SavePort,
        parentScope: CoroutineScope,
        contentSnapshot: ContentSnapshot,
        dispatcher: CoroutineDispatcher,
        summaryVisibleEventVisibilities: Set<EventVisibility>,
        deltaFactory: (
            CommandEnvelope<out WorldCommandPayload>,
            StateVersion,
            RngState,
            Long
        ) -> DomainDelta
    ) : this(
        contentSnapshot,
        epoch,
        savePort,
        parentScope,
        dispatcher,
        null,
        null,
        deltaFactory,
        summaryVisibleEventVisibilities,
        Unit
    )

    internal constructor(
        epoch: SessionEpoch,
        savePort: SavePort,
        parentScope: CoroutineScope,
        contentSnapshot: ContentSnapshot,
        initialSnapshot: WorldSnapshot,
        dispatcher: CoroutineDispatcher,
        deltaFactory: (
            CommandEnvelope<out WorldCommandPayload>,
            StateVersion,
            RngState,
            Long
        ) -> DomainDelta
    ) : this(
        contentSnapshot,
        epoch,
        savePort,
        parentScope,
        dispatcher,
        initialSnapshot,
        null,
        deltaFactory,
        setOf(EventVisibility.PUBLIC),
        Unit
    )

    internal constructor(
        epoch: SessionEpoch,
        savePort: SavePort,
        parentScope: CoroutineScope,
        contentSnapshot: ContentSnapshot,
        initialSnapshot: WorldSnapshot,
        dispatcher: CoroutineDispatcher,
        summaryVisibleEventVisibilities: Set<EventVisibility>,
        deltaFactory: (
            CommandEnvelope<out WorldCommandPayload>,
            StateVersion,
            RngState,
            Long
        ) -> DomainDelta
    ) : this(
        contentSnapshot,
        epoch,
        savePort,
        parentScope,
        dispatcher,
        initialSnapshot,
        null,
        deltaFactory,
        summaryVisibleEventVisibilities,
        Unit
    )

    internal constructor(
        epoch: SessionEpoch,
        savePort: SavePort,
        parentScope: CoroutineScope,
        contentSnapshot: ContentSnapshot,
        initialSnapshot: WorldSnapshot,
        engine: WorldEngine,
        dispatcher: CoroutineDispatcher = Dispatchers.Default,
        summaryVisibleEventVisibilities: Set<EventVisibility> = setOf(EventVisibility.PUBLIC)
    ) : this(
        contentSnapshot,
        epoch,
        savePort,
        parentScope,
        dispatcher,
        initialSnapshot,
        engine,
        { envelope, _, rngState, submissionSequence ->
            DomainDelta(emptyList(), rngState, emptyList(), CommandResult.Rejected(DomainError.UnsupportedFeature(envelope.payload.codecId), submissionSequence))
        },
        summaryVisibleEventVisibilities,
        Unit
    )

    init {
        require(EventVisibility.PUBLIC in summaryVisibleEventVisibilities)
        require(EventVisibility.SYSTEM_HIDDEN !in summaryVisibleEventVisibilities)
    }

    private val allowedSummaryEventVisibilities = summaryVisibleEventVisibilities.toSet()

    private val sessionJob = SupervisorJob(parentScope.coroutineContext[Job])
    private val scope = CoroutineScope(parentScope.coroutineContext + sessionJob + dispatcher)
    private val queue = Channel<QueuedCommand>(capacity = 64)
    private val lifecycleMutex = Mutex()
    private val receipts = object : LinkedHashMap<ReceiptKey, PersistedReceipt>(RECEIPT_CACHE_MAX_ENTRIES + 1, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<ReceiptKey, PersistedReceipt>?): Boolean =
            size > RECEIPT_CACHE_MAX_ENTRIES
    }
    private var lifecycle = SessionLifecycle.OPEN
    private var nextSubmissionSequence = 0L
    private var stateVersion = initialSnapshot?.stateVersion ?: StateVersion(0)
    private var rngState = initialSnapshot?.rngState ?: RngState(emptyList())
    private val aggregateStates = linkedMapOf<EntityId, AggregateState>()
    private var authoritativeWorld: AuthoritativeWorldState? = initialSnapshot?.world
    private var latestPublication: CommittedPublication? = null
    private val publicationFlow = MutableStateFlow<CommittedPublication?>(null)
    private val timeAdvanceSummaryInputs = mutableMapOf<ReceiptKey, TimeAdvanceSummaryInput>()
    private var activeAdvanceCommandId: CommandId? = null
    private var restoredAdvanceAwaitingResume = false
    private var systemHaltedReason: String? = null
    private var pendingControl: ControlRequest? = null
    private var closeCompletion: CompletableDeferred<CloseResult>? = null
    private val runtimeStateFlow = MutableStateFlow(SessionRuntimeState(epoch, lifecycle, activeAdvanceCommandId))

    val publications: StateFlow<CommittedPublication?> = publicationFlow.asStateFlow()
    val runtimeState: StateFlow<SessionRuntimeState> = runtimeStateFlow.asStateFlow()

    suspend fun refreshTimeAdvanceSummary(commandId: CommandId): TimeAdvanceSummaryView? = lifecycleMutex.withLock {
        val publication = latestPublication
            ?.takeIf { it.sourceCommandId == commandId && it.timeAdvanceTerminal != null }
            ?: return@withLock null
        publication.timeAdvanceSummary?.let { return@withLock it }
        val key = ReceiptKey(epoch, commandId)
        val input = timeAdvanceSummaryInputs[key] ?: return@withLock null
        val world = authoritativeWorld ?: return@withLock null
        val terminal = world.timeAdvance
            ?.takeIf { it.commandEpoch == epoch && it.commandId == commandId && it.status != null }
            ?: return@withLock null
        val summary = runCatching { buildTimeAdvanceSummary(input, world, terminal) }.getOrNull()
            ?: return@withLock null
        timeAdvanceSummaryInputs.remove(key)
        latestPublication = publication.copy(timeAdvanceSummary = summary)
        publicationFlow.value = latestPublication
        summary
    }

    private fun updateRuntimeStateLocked() {
        runtimeStateFlow.value = SessionRuntimeState(epoch, lifecycle, activeAdvanceCommandId)
    }

    init {
        initialSnapshot?.aggregates?.let(aggregateStates::putAll)
        initialSnapshot?.world?.timeAdvance?.takeIf { it.status == null && it.commandId != null }?.let {
            activeAdvanceCommandId = it.commandId
            restoredAdvanceAwaitingResume = true
        }
        updateRuntimeStateLocked()
        sessionJob.invokeOnCompletion { cause ->
            val cancellation = cause as? kotlinx.coroutines.CancellationException ?: return@invokeOnCompletion
            queue.close(cancellation)
            while (true) {
                val queued = queue.tryReceive().getOrNull() ?: break
                queued.reply.completeExceptionally(cancellation)
            }
        }
    }

    private val consumer = scope.launch {
        for (queued in queue) {
            var processedResult: CommandResult? = null
            try {
                val haltedBeforeProcess = lifecycleMutex.withLock { systemHaltedReason }
                if (haltedBeforeProcess != null) {
                    processedResult = CommandResult.Rejected(
                        DomainError.SystemHalted(haltedBeforeProcess),
                        queued.accepted.await()
                    )
                    lifecycleMutex.withLock {
                        markSystemHaltedLocked(queued.envelope.commandId, queued.envelope.payload, haltedBeforeProcess, false)
                    }
                    queued.reply.complete(processedResult!!)
                    continue
                }
                processedResult = process(queued.envelope, queued.accepted.await())
                val haltReason = processedResult?.systemHaltReason()
                if (haltReason != null) {
                    lifecycleMutex.withLock {
                        markSystemHaltedLocked(
                            queued.envelope.commandId,
                            queued.envelope.payload,
                            haltReason,
                            hasDurableRunningAdvance(queued.envelope)
                        )
                    }
                }
                queued.reply.complete(processedResult!!)
            } catch (error: kotlinx.coroutines.CancellationException) {
                queued.reply.completeExceptionally(error)
                if (!sessionJob.isActive) throw error
            } finally {
                lifecycleMutex.withLock {
                    val haltReason = processedResult?.systemHaltReason()
                    if (haltReason == null && queued.envelope.payload is AdvanceTimePayload) {
                        activeAdvanceCommandId = null
                        pendingControl = null
                        if (lifecycle == SessionLifecycle.PAUSING) lifecycle = SessionLifecycle.PAUSED
                    }
                    updateRuntimeStateLocked()
                }
            }
        }
    }

    private fun CommandResult.systemHaltReason(): String? =
        (this as? CommandResult.Rejected)?.error?.let { it as? DomainError.SystemHalted }?.reason

    private fun CommandResult.isSystemHalted(): Boolean = systemHaltReason() != null

    private fun markSystemHaltedLocked(
        commandId: CommandId,
        payload: WorldCommandPayload,
        reason: String,
        preserveAdvance: Boolean
    ) {
        // A halt is session-wide, including atomic elapsed/decision commands.
        // Keep the session paused and reject further gameplay until it is closed.
        systemHaltedReason = reason
        pendingControl = null
        if (payload is AdvanceTimePayload) {
            if (preserveAdvance) {
                // Keep the durable RUNNING command visible for operator recovery/cancel; do not silently release it.
                restoredAdvanceAwaitingResume = true
            } else if (activeAdvanceCommandId == commandId) {
                activeAdvanceCommandId = null
                restoredAdvanceAwaitingResume = false
            }
        }
        if (lifecycle == SessionLifecycle.OPEN || lifecycle == SessionLifecycle.PAUSING) {
            lifecycle = SessionLifecycle.PAUSED
        }
        updateRuntimeStateLocked()
    }

    private fun hasDurableRunningAdvance(envelope: CommandEnvelope<out WorldCommandPayload>): Boolean {
        if (envelope.payload !is AdvanceTimePayload) return false
        val matchesCommand = { state: TimeAdvanceState ->
            state.status == null && state.commandEpoch == envelope.sessionEpoch && state.commandId == envelope.commandId
        }
        if (authoritativeWorld?.timeAdvance?.let(matchesCommand) == true) return true
        return receipts[ReceiptKey(envelope.sessionEpoch, envelope.commandId)]
            ?.takeIf { it.lifecycleStatus == ReceiptLifecycle.RUNNING }
            ?.timeAdvanceState
            ?.let(matchesCommand) == true
    }

    suspend fun execute(envelope: CommandEnvelope<out WorldCommandPayload>): CommandResult {
        val (queued, admissionError) = lifecycleMutex.withLock {
            systemHaltedReason?.let { reason ->
                return@withLock null to DomainError.SystemHalted(reason)
            }
            var resumesRestoredAdvance = false
            activeAdvanceCommandId?.let { active ->
                resumesRestoredAdvance = restoredAdvanceAwaitingResume && envelope.payload is AdvanceTimePayload && envelope.commandId == active
                if (!resumesRestoredAdvance) {
                    return@withLock null to DomainError.AdvanceInProgress(active, ADVANCE_CONTROLS)
                }
            }
            if (lifecycle != SessionLifecycle.OPEN) return@withLock null to DomainError.SessionClosed
            val reply = CompletableDeferred<CommandResult>()
            val item = QueuedCommand(
                envelope = envelope,
                reply = reply
            )
            val enqueue = queue.trySend(item)
            if (enqueue.isSuccess) {
                withContext(NonCancellable) {
                    item.accepted.complete(++nextSubmissionSequence)
                }
                if (resumesRestoredAdvance) restoredAdvanceAwaitingResume = false
                if (envelope.payload is AdvanceTimePayload) {
                    activeAdvanceCommandId = envelope.commandId
                    updateRuntimeStateLocked()
                }
                item to null
            } else {
                val closeCause = enqueue.exceptionOrNull()
                if (closeCause is kotlinx.coroutines.CancellationException) throw closeCause
                null to if (enqueue.isClosed) DomainError.SessionClosed
                else DomainError.ValidationError("commandQueue", "capacity exceeded")
            }
        }
        return queued?.reply?.await()
            ?: CommandResult.Rejected(admissionError ?: DomainError.SessionClosed, submissionSequence = null)
    }

    suspend fun requestControl(request: ControlRequest): ControlRequestResult = lifecycleMutex.withLock {
        systemHaltedReason?.let { return@withLock ControlRequestResult.Rejected(DomainError.SystemHalted(it)) }
        val active = activeAdvanceCommandId
            ?: return@withLock ControlRequestResult.Rejected(DomainError.SessionClosed)
        if (request.sessionEpoch != epoch) {
            return@withLock ControlRequestResult.Rejected(DomainError.StaleSession(epoch, request.sessionEpoch))
        }
        if (request.expectedActiveCommandId != active) {
            return@withLock ControlRequestResult.Rejected(DomainError.AdvanceInProgress(active, ADVANCE_CONTROLS))
        }
        latchControlLocked(request)
        ControlRequestResult.Accepted(active)
    }

    suspend fun pause(): SessionRuntimeState = lifecycleMutex.withLock {
        if (lifecycle == SessionLifecycle.OPEN) {
            activeAdvanceCommandId?.let { active ->
                lifecycle = SessionLifecycle.PAUSING
                latchControlLocked(ControlRequest(epoch, active, AdvanceControl.PAUSE, allowCommitDrain = true))
            } ?: run {
                lifecycle = SessionLifecycle.PAUSED
            }
            updateRuntimeStateLocked()
        }
        runtimeStateFlow.value
    }

    suspend fun resume(): SessionRuntimeState = lifecycleMutex.withLock {
        if (lifecycle == SessionLifecycle.PAUSED && systemHaltedReason == null) {
            lifecycle = SessionLifecycle.OPEN
            updateRuntimeStateLocked()
        }
        runtimeStateFlow.value
    }

    suspend fun close() {
        close(CloseReason.APPLICATION_REQUEST)
    }

    suspend fun close(reason: CloseReason): CloseResult {
        val (completion, startsDrain) = lifecycleMutex.withLock {
            closeCompletion?.let { return@withLock it to false }
            CompletableDeferred<CloseResult>().also {
                closeCompletion = it
                lifecycle = SessionLifecycle.CLOSING
                activeAdvanceCommandId?.let { active ->
                    latchControlLocked(ControlRequest(epoch, active, AdvanceControl.CLOSE, allowCommitDrain = true))
                }
                updateRuntimeStateLocked()
                queue.close()
            } to true
        }
        if (startsDrain) {
            withContext(NonCancellable) {
                try {
                    consumer.join()
                } finally {
                    val result = lifecycleMutex.withLock {
                        lifecycle = SessionLifecycle.CLOSED
                        updateRuntimeStateLocked()
                        CloseResult(epoch, stateVersion, outstandingJobs = 0)
                    }
                    sessionJob.cancel()
                    completion.complete(result)
                }
            }
        }
        return completion.await()
    }

    internal fun inMemoryStateHash(): PayloadHash {
        val canonical = buildString {
            append("P0-IN-MEMORY-STATE\n")
            authoritativeWorld?.let { appendAuthoritativeWorld(it) }
            rngState.streams
                .sortedBy { it.streamKey.value }
                .forEach { stream ->
                    append("rng=").append(stream.streamKey.value).append(':')
                        .append(stream.algorithmVersion).append(':').append(stream.state).append(':')
                        .append(stream.increment).append(':').append(stream.drawCounter).append('\n')
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

    private fun StringBuilder.appendAuthoritativeWorld(world: AuthoritativeWorldState) {
        append("world.clock=").append(world.clock.minute.value).append(':').append(world.clock.subMinuteMs.value).append('\n')
        append("world.binding=").append(world.boundaryBinding.engineOrderVersion).append(':')
            .append(CanonicalJson.string(world.boundaryBinding.boundaryOrderVersion)).append('\n')
        world.boundaryBinding.sourceIds.forEach { append("world.source=").append(CanonicalJson.string(it)).append('\n') }
        world.boundaryBinding.candidateCodecs.sorted().forEach { append("world.codec=").append(CanonicalJson.string(it)).append('\n') }
        world.calendar.actions.sortedBy { it.actionId.value }.forEach { action ->
            append("action=").append(CanonicalJson.string(action.actionId.value)).append(':').append(CanonicalJson.string(action.actionKind)).append(':')
                .append(CanonicalJson.string(action.payload.canonicalJson())).append(':').append(action.payload.hash.value).append(':').append(action.status.name).append(':')
                .append(action.startMinute.value).append(':').append(action.dueMinute.value).append(':')
                .append(action.priority.name).append(':').append(action.rowVersion.value).append(':').append(action.canBePreempted).append('\n')
            action.participants.forEach {
                append("action.participant=").append(CanonicalJson.string(it.entityType)).append(':')
                    .append(CanonicalJson.string(it.entityId.value)).append('\n')
            }
            action.claims.forEach { claim ->
                append("action.claim=").append(CanonicalJson.string(claim.resource.kind)).append(':')
                    .append(CanonicalJson.string(claim.resource.id)).append(':').append(claim.quantity).append(':').append(claim.policy.name).append('\n')
            }
        }
        appendResourceMap("ledger.owned", world.calendar.resources.owned)
        appendResourceMap("ledger.held", world.calendar.resources.held)
        appendResourceMap("ledger.consumed", world.calendar.resources.consumed)
        world.calendar.resources.claimStates.entries.sortedWith(compareBy({ it.key.actionId.value }, { it.key.resource.kind }, { it.key.resource.id })).forEach { (key, state) ->
            append("ledger.claim=").append(CanonicalJson.string(key.actionId.value)).append(':')
                .append(CanonicalJson.string(key.resource.kind)).append(':').append(CanonicalJson.string(key.resource.id)).append(':').append(state.name).append('\n')
        }
        world.timeAdvance?.let { appendTimeAdvanceState(it) }
    }

    private fun StringBuilder.appendResourceMap(prefix: String, values: Map<ResourceIdentity, Long>) {
        values.entries.sortedWith(compareBy({ it.key.kind }, { it.key.id })).forEach { (resource, quantity) ->
            append(prefix).append('=').append(CanonicalJson.string(resource.kind)).append(':')
                .append(CanonicalJson.string(resource.id)).append(':').append(quantity).append('\n')
        }
    }

    private fun StringBuilder.appendTimeAdvanceState(state: TimeAdvanceState) {
        append("advance.goal=").append(CanonicalJson.string(TimeAdvanceGoalCodec.encode(state.goal))).append('\n')
        append("advance.status=").append(state.status?.name ?: "RUNNING").append(':').append(state.processedBoundaryCount).append(':')
            .append(state.segmentNo).append(':').append(state.nextEventSequence).append('\n')
        append("advance.command=").append(state.commandEpoch?.value ?: -1).append(':').append(CanonicalJson.string(state.commandId?.value.orEmpty())).append(':')
            .append(CanonicalJson.string(state.continuationOfCommandId?.value.orEmpty())).append(':').append(state.progressionMode.name).append('\n')
        append("advance.gate=").append(CanonicalJson.string(state.pendingDecisionGateId.orEmpty())).append('\n')
        state.pendingDecisionChoiceIds.forEach { append("advance.choice=").append(CanonicalJson.string(it)).append('\n') }
        append("advance.selectedChoice=").append(CanonicalJson.string(state.selectedDecisionChoiceId.orEmpty())).append('\n')
        state.limits?.let { limits ->
            append("advance.limits=").append(limits.maxAdvanceMinute.value).append(':').append(limits.maxBoundaryCount).append(':').append(limits.maxCandidatesPerBatch).append('\n')
        }
        state.interruptPolicy?.let { policy -> append("advance.policy=").append(CanonicalJson.string(policy.canonicalJson())).append(':').append(policy.hash.value).append('\n') }
        state.cursor?.let { cursor -> append("advance.cursor=").appendBoundaryKey(cursor.lastCompletedKey).append('\n') }
        state.pendingSuffix?.candidates?.forEach { candidate -> append("advance.suffix=").appendBoundaryCandidate(candidate).append('\n') }
        state.sealedElapsedOutcome?.let { outcome ->
            append("advance.sealed=").append(CanonicalJson.string(outcome.codec)).append(':').append(CanonicalJson.string(outcome.canonicalPayload)).append(':')
                .append(outcome.effectiveMinute.value).append(':').append(CanonicalJson.string(outcome.domainResultId.value)).append(':').append(outcome.hash.value).append('\n')
        }
    }

    private fun StringBuilder.appendBoundaryCandidate(candidate: BoundaryCandidate): StringBuilder = appendBoundaryKey(candidate.key).append(':')
        .append(CanonicalJson.string(candidate.candidateKind)).append(':').append(CanonicalJson.string(candidate.payloadCodec)).append(':')
        .append(CanonicalJson.string(candidate.canonicalPayload)).append(':').append(candidate.payloadHash.value).append(':').append(candidate.disposition.name).append(':')
        .append(CanonicalJson.string(candidate.eventType.orEmpty())).append(':').append(candidate.importance?.name.orEmpty()).append(':')
        .append(CanonicalJson.string(candidate.subjectId?.value.orEmpty())).append(':').append(candidate.publicSafetyStop).append(':')
        .append(candidate.decisionChoiceIds.joinToString(",") { CanonicalJson.string(it) })

    private fun StringBuilder.appendBoundaryKey(key: BoundaryKey): StringBuilder = append(key.boundaryTime.value).append(':').append(key.category.name).append(':')
        .append(key.priority).append(':').append(key.domainSequence).append(':').append(CanonicalJson.string(key.stableEntityId)).append(':')
        .append(CanonicalJson.string(key.stableSubKey)).append(':').append(CanonicalJson.string(key.sourceId))

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
        var runningReceipt: PersistedReceipt? = null
        receipts[receiptKey]?.let { stored ->
            if (stored.payloadHash != envelope.payloadHash) {
                return CommandResult.Rejected(DomainError.IdempotencyKeyReuse(envelope.commandId), submissionSequence)
            }
            if (stored.lifecycleStatus != ReceiptLifecycle.RUNNING || envelope.payload !is AdvanceTimePayload) return stored.result
            runningReceipt = stored
        }

        val persistedReceipt = if (runningReceipt != null) null else try {
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
            if (stored.payloadHash != envelope.payloadHash) {
                return CommandResult.Rejected(DomainError.IdempotencyKeyReuse(envelope.commandId), submissionSequence)
            } else if (stored.lifecycleStatus == ReceiptLifecycle.RUNNING && envelope.payload is AdvanceTimePayload) {
                receipts[receiptKey] = stored
                runningReceipt = stored
            } else {
                receipts[receiptKey] = stored
                return stored.result
            }
        }

        if (engine != null && envelope.payload is AdvanceTimePayload) {
            return processAdvance(envelope, submissionSequence, receiptKey, runningReceipt)
        }
        if (engine != null && envelope.payload is AtomicElapsedDecisionPayload) {
            validateAtomicContinuation(envelope, envelope.payload)?.let { return CommandResult.Rejected(it, submissionSequence) }
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
        val plan = try {
            engine?.let { worldEngine ->
                val world = authoritativeWorld ?: return CommandResult.Rejected(DomainError.InvariantViolation("P2 engine requires authoritative world state"), submissionSequence)
                worldEngine.plan(
                    envelope,
                    WorldSnapshot(stateVersion, world, rngState, aggregateStates.toMap()),
                    candidateAcceptedVersion,
                    submissionSequence
                )
            } ?: ExecutionPlan.Atomic(deltaFactory(envelope, candidateAcceptedVersion, rngState, submissionSequence))
        } catch (error: kotlinx.coroutines.CancellationException) {
            throw error
        } catch (error: Exception) {
            return CommandResult.Rejected(DomainError.InvariantViolation(error.message ?: error::class.simpleName.orEmpty()), submissionSequence)
        }
        val delta = when (plan) {
            is ExecutionPlan.Atomic -> plan.delta
            is ExecutionPlan.Segment -> plan.delta
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
        delta.worldChange?.let { change ->
            if (change.before != authoritativeWorld) {
                return CommandResult.Rejected(DomainError.InvariantViolation("authoritative world state precondition failed"), submissionSequence)
            }
        }
        if (delta.result is CommandResult.Rejected &&
            (delta.aggregateChanges.isNotEmpty() || delta.rngState != rngState || delta.worldChange != null)
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
        if ((delta.result as? CommandResult.Rejected)?.error is DomainError.SystemHalted) return delta.result

        val receipt = try {
            when (plan) {
                is ExecutionPlan.Atomic -> savePort.commit(envelope, delta)
                is ExecutionPlan.Segment -> return try {
                    commitSegmentAndReconcile(envelope, delta, receiptKey, candidateAcceptedVersion, nextAggregateStates, plan).receipt.result
                } catch (error: kotlinx.coroutines.CancellationException) {
                    throw error
                } catch (error: Exception) {
                    CommandResult.Rejected(DomainError.PersistenceFailure(error.message ?: error::class.simpleName.orEmpty()), submissionSequence)
                }
            }
        } catch (error: kotlinx.coroutines.CancellationException) {
            recoverCancelledCommit(envelope, delta, receiptKey, candidateAcceptedVersion, nextAggregateStates)
            throw error
        } catch (error: Exception) {
            return CommandResult.Rejected(DomainError.PersistenceFailure(error.message ?: error::class.simpleName.orEmpty()), submissionSequence)
        }
        applyCommittedReceipt(receipt, envelope, delta, receiptKey, candidateAcceptedVersion, nextAggregateStates)
            ?.let { reason ->
                return CommandResult.Rejected(DomainError.PersistenceFailure(reason), submissionSequence)
            }
        return receipt.result
    }

    private suspend fun processAdvance(
        envelope: CommandEnvelope<out WorldCommandPayload>,
        submissionSequence: Long,
        receiptKey: ReceiptKey,
        runningReceipt: PersistedReceipt?
    ): CommandResult {
        val payload = envelope.payload as AdvanceTimePayload
        if (runningReceipt == null) {
            envelope.expectedVersion?.let { expected ->
                if (expected != stateVersion) return CommandResult.Rejected(DomainError.VersionConflict(expected, stateVersion), submissionSequence)
            }
            validateContinuation(envelope, payload)?.let { return CommandResult.Rejected(it, submissionSequence) }
        } else {
            validateRunningReceipt(envelope, runningReceipt)?.let { return CommandResult.Rejected(DomainError.PersistenceFailure(it), submissionSequence) }
        }

        while (true) {
            val eventSequenceStart = authoritativeWorld?.timeAdvance
                ?.takeIf { it.commandEpoch == envelope.sessionEpoch && it.commandId == envelope.commandId }
                ?.nextEventSequence ?: 0L
            val candidateAcceptedVersion = try {
                StateVersion(Math.addExact(stateVersion.value, 1))
            } catch (_: ArithmeticException) {
                return CommandResult.Rejected(DomainError.InvariantViolation("state version overflow"), submissionSequence)
            }
            val control = lifecycleMutex.withLock {
                pendingControl?.takeIf { it.sessionEpoch == epoch && it.expectedActiveCommandId == envelope.commandId }?.kind
            }
            val plan = try {
                engine!!.plan(
                    envelope,
                    WorldSnapshot(stateVersion, authoritativeWorld ?: return CommandResult.Rejected(
                        DomainError.InvariantViolation("P2 engine requires authoritative world state"), submissionSequence
                    ), rngState, aggregateStates.toMap()),
                    candidateAcceptedVersion,
                    submissionSequence,
                    control
                )
            } catch (error: kotlinx.coroutines.CancellationException) {
                throw error
            } catch (error: Exception) {
                return CommandResult.Rejected(DomainError.InvariantViolation(error.message ?: error::class.simpleName.orEmpty()), submissionSequence)
            }
            val segmentPlan = plan as? ExecutionPlan.Segment
                ?: return (plan as ExecutionPlan.Atomic).delta.result
            val delta = segmentPlan.delta
            if (delta.result.submissionSequence != submissionSequence || delta.result !is CommandResult.Accepted) {
                return CommandResult.Rejected(DomainError.InvariantViolation("advance segment must be accepted with the assigned submission sequence"), submissionSequence)
            }
            val nextAggregateStates = aggregateStates.toMutableMap()
            for (change in delta.aggregateChanges) {
                if (!sameAggregateState(nextAggregateStates[change.aggregateId], change.before)) {
                    return CommandResult.Rejected(DomainError.InvariantViolation("aggregate state precondition failed: ${change.aggregateId.value}"), submissionSequence)
                }
                if (change.after == null) nextAggregateStates.remove(change.aggregateId) else nextAggregateStates[change.aggregateId] = change.after
            }
            if (delta.worldChange?.before != authoritativeWorld) {
                return CommandResult.Rejected(DomainError.InvariantViolation("authoritative world state precondition failed"), submissionSequence)
            }
            validateEventMetadata(envelope, delta.events, candidateAcceptedVersion, eventSequenceStart)?.let { reason ->
                return CommandResult.Rejected(DomainError.InvariantViolation(reason), submissionSequence)
            }
            if (segmentPlan.timeAdvanceState.nextEventSequence != eventSequenceStart + delta.events.size) {
                return CommandResult.Rejected(DomainError.InvariantViolation("advance event sequence cursor mismatch"), submissionSequence)
            }

            val segment = try {
                commitSegmentAndReconcile(
                    envelope, delta, receiptKey, candidateAcceptedVersion, nextAggregateStates, segmentPlan
                )
            } catch (error: kotlinx.coroutines.CancellationException) {
                throw error
            } catch (error: Exception) {
                return CommandResult.Rejected(DomainError.PersistenceFailure(error.message ?: error::class.simpleName.orEmpty()), submissionSequence)
            }
            if (segmentPlan.terminalResult != null) return segment.receipt.result
        }
    }

    private suspend fun commitSegmentAndReconcile(
        envelope: CommandEnvelope<out WorldCommandPayload>,
        delta: DomainDelta,
        receiptKey: ReceiptKey,
        candidateAcceptedVersion: StateVersion,
        nextAggregateStates: Map<EntityId, AggregateState>,
        plan: ExecutionPlan.Segment
    ): SegmentCommitReceipt {
        var cancelled: kotlinx.coroutines.CancellationException? = null
        val segment = try {
            savePort.commitSegment(
                envelope,
                plan.expectedSegmentNo,
                delta,
                plan.timeAdvanceState,
                plan.terminalResult,
                plan.continuation
            )
        } catch (error: kotlinx.coroutines.CancellationException) {
            cancelled = error
            reconcileSegment(envelope, plan) ?: run {
                stopAfterUncertainCommit()
                throw error
            }
        } catch (error: Exception) {
            reconcileSegment(envelope, plan) ?: throw error
        }
        val mismatch = applySegmentReceipt(segment, envelope, delta, receiptKey, candidateAcceptedVersion, nextAggregateStates, plan)
        if (mismatch != null) {
            if (cancelled != null) stopAfterUncertainCommit()
            throw IllegalStateException(mismatch)
        }
        cacheSegmentReceipt(receiptKey, envelope, segment, plan.continuation)
        cancelled?.let { throw it }
        return segment
    }

    private fun applySegmentReceipt(
        segment: SegmentCommitReceipt,
        envelope: CommandEnvelope<out WorldCommandPayload>,
        delta: DomainDelta,
        receiptKey: ReceiptKey,
        candidateAcceptedVersion: StateVersion,
        nextAggregateStates: Map<EntityId, AggregateState>,
        plan: ExecutionPlan.Segment
    ): String? {
        if (segment.segmentNo != plan.expectedSegmentNo || segment.timeAdvanceState != plan.timeAdvanceState ||
            segment.receipt.lastCommittedSegmentNo != plan.expectedSegmentNo ||
            segment.receipt.lifecycleStatus != segmentLifecycle(plan.terminalResult)
        ) return "segment receipt watermark or lifecycle mismatch"
        return applyCommittedReceipt(segment.receipt, envelope, delta, receiptKey, candidateAcceptedVersion, nextAggregateStates)
    }

    private fun cacheSegmentReceipt(
        receiptKey: ReceiptKey,
        envelope: CommandEnvelope<out WorldCommandPayload>,
        segment: SegmentCommitReceipt,
        continuation: TimeAdvanceContinuation?
    ) {
        receipts[receiptKey] = PersistedReceipt(
            envelope.payloadHash,
            segment.receipt.stateVersion,
            segment.receipt.result,
            segment.receipt.lifecycleStatus,
            segment.receipt.lastCommittedSegmentNo,
            segment.timeAdvanceState,
            continuation
        )
    }

    private suspend fun reconcileSegment(
        envelope: CommandEnvelope<out WorldCommandPayload>,
        plan: ExecutionPlan.Segment
    ): SegmentCommitReceipt? {
        val persisted = try {
            withContext(NonCancellable) { savePort.findReceipt(envelope.sessionEpoch, envelope.commandId) }
        } catch (_: Exception) {
            return null
        } ?: return null
        if (persisted.payloadHash != envelope.payloadHash || persisted.lastCommittedSegmentNo != plan.expectedSegmentNo ||
            persisted.lifecycleStatus != segmentLifecycle(plan.terminalResult) || persisted.timeAdvanceState != plan.timeAdvanceState ||
            persisted.continuation != plan.continuation
        ) return null
        return SegmentCommitReceipt(
            CommitReceipt(persisted.stateVersion, persisted.result, persisted.lifecycleStatus, persisted.lastCommittedSegmentNo),
            persisted.lastCommittedSegmentNo,
            persisted.timeAdvanceState
        )
    }

    private fun validateRunningReceipt(envelope: CommandEnvelope<out WorldCommandPayload>, receipt: PersistedReceipt): String? {
        val state = receipt.timeAdvanceState ?: return "running receipt is missing time advance state"
        if (state.status != null || state.commandEpoch != envelope.sessionEpoch || state.commandId != envelope.commandId ||
            receipt.lastCommittedSegmentNo != state.segmentNo || receipt.stateVersion != stateVersion || authoritativeWorld?.timeAdvance != state
        ) return "running receipt does not match the restored authoritative snapshot"
        return null
    }

    private suspend fun validateContinuation(
        envelope: CommandEnvelope<out WorldCommandPayload>,
        payload: AdvanceTimePayload
    ): DomainError? {
        val continuation = payload.continuation ?: return null
        if (continuation.childCommandId != envelope.commandId) return DomainError.ValidationError("continuation.childCommandId", "must equal commandId")
        val predecessor = try {
            savePort.findReceipt(continuation.predecessorEpoch, continuation.predecessorCommandId)
        } catch (error: kotlinx.coroutines.CancellationException) {
            throw error
        } catch (error: Exception) {
            return DomainError.PersistenceFailure(error.message ?: error::class.simpleName.orEmpty())
        } ?: return DomainError.SystemHalted("continuation predecessor receipt is missing")
        val state = predecessor.timeAdvanceState
            ?: return DomainError.SystemHalted("continuation predecessor state is missing")
        if (predecessor.lifecycleStatus != ReceiptLifecycle.INTERRUPTED || state.status != TimeAdvanceResult.DECISION_REQUIRED ||
            state.commandEpoch != continuation.predecessorEpoch || state.commandId != continuation.predecessorCommandId ||
            state.pendingSuffix?.hash != continuation.pendingSuffixHash || state.sealedElapsedOutcome?.hash != continuation.sealedOutcomeHash ||
            state.pendingDecisionGateId != continuation.selection.gateId ||
            continuation.selection.choiceId !in state.pendingDecisionChoiceIds ||
            authoritativeWorld?.timeAdvance != state
        ) return DomainError.SystemHalted("continuation predecessor does not match the authoritative terminal state")
        return null
    }

    private suspend fun validateAtomicContinuation(
        envelope: CommandEnvelope<out WorldCommandPayload>,
        payload: AtomicElapsedDecisionPayload
    ): DomainError? {
        val predecessor = try {
            savePort.findReceipt(payload.predecessorEpoch, payload.predecessorCommandId)
        } catch (error: kotlinx.coroutines.CancellationException) {
            throw error
        } catch (error: Exception) {
            return DomainError.PersistenceFailure(error.message ?: error::class.simpleName.orEmpty())
        } ?: return DomainError.SystemHalted("atomic continuation predecessor receipt is missing")
        val state = predecessor.timeAdvanceState
            ?: return DomainError.SystemHalted("atomic continuation predecessor state is missing")
        if (predecessor.lifecycleStatus != ReceiptLifecycle.INTERRUPTED || state.status != TimeAdvanceResult.DECISION_REQUIRED ||
            state.commandEpoch != payload.predecessorEpoch || state.commandId != payload.predecessorCommandId ||
            state.pendingSuffix?.hash != payload.pendingSuffixHash || state.sealedElapsedOutcome?.hash != payload.sealedOutcomeHash ||
            state.pendingDecisionGateId != payload.gateId || payload.choiceId !in state.pendingDecisionChoiceIds ||
            authoritativeWorld?.timeAdvance != state || envelope.commandId == payload.predecessorCommandId
        ) return DomainError.SystemHalted("atomic continuation predecessor does not match the authoritative terminal state")
        return null
    }

    private fun segmentLifecycle(result: TimeAdvanceResult?): ReceiptLifecycle = when (result) {
        null -> ReceiptLifecycle.RUNNING
        TimeAdvanceResult.INTERRUPTED, TimeAdvanceResult.DECISION_REQUIRED, TimeAdvanceResult.FAILED -> ReceiptLifecycle.INTERRUPTED
        else -> ReceiptLifecycle.COMMITTED
    }

    private fun controlRank(kind: AdvanceControl): Int = when (kind) {
        AdvanceControl.CANCEL_ADVANCE -> 3
        AdvanceControl.CLOSE, AdvanceControl.APP_BACKGROUND -> 2
        AdvanceControl.PAUSE -> 1
    }

    private fun latchControlLocked(request: ControlRequest) {
        if (pendingControl == null || controlRank(request.kind) > controlRank(pendingControl!!.kind)) pendingControl = request
    }

    private suspend fun recoverCancelledCommit(
        envelope: CommandEnvelope<out WorldCommandPayload>,
        delta: DomainDelta,
        receiptKey: ReceiptKey,
        candidateAcceptedVersion: StateVersion,
        nextAggregateStates: Map<EntityId, AggregateState>
    ) {
        val persistedReceipt = try {
            withContext(NonCancellable) { savePort.findReceipt(envelope.sessionEpoch, envelope.commandId) }
        } catch (_: Exception) {
            stopAfterUncertainCommit()
            return
        }
        if (persistedReceipt == null) return
        if (persistedReceipt.payloadHash != envelope.payloadHash ||
            applyCommittedReceipt(
                CommitReceipt(persistedReceipt.stateVersion, persistedReceipt.result),
                envelope,
                delta,
                receiptKey,
                candidateAcceptedVersion,
                nextAggregateStates
            ) != null
        ) {
            stopAfterUncertainCommit()
        }
    }

    private fun applyCommittedReceipt(
        receipt: CommitReceipt,
        envelope: CommandEnvelope<out WorldCommandPayload>,
        delta: DomainDelta,
        receiptKey: ReceiptKey,
        candidateAcceptedVersion: StateVersion,
        nextAggregateStates: Map<EntityId, AggregateState>
    ): String? {
        if (receipt.result != delta.result) return "commit receipt result mismatch"
        when (delta.result) {
            is CommandResult.Accepted -> {
                if (receipt.stateVersion != candidateAcceptedVersion) return "commit receipt state version mismatch"
                aggregateStates.clear()
                aggregateStates.putAll(nextAggregateStates)
                rngState = delta.rngState
                stateVersion = receipt.stateVersion
                delta.worldChange?.let { authoritativeWorld = it.after }
                publishCommitted(envelope, delta, updateTimeAdvanceSummary(envelope, delta, receiptKey))
            }

            is CommandResult.Rejected -> if (receipt.stateVersion != stateVersion) return "rejected receipt changed state version"
        }
        receipts[receiptKey] = PersistedReceipt(envelope.payloadHash, receipt.stateVersion, receipt.result)
        return null
    }

    private fun publishCommitted(
        envelope: CommandEnvelope<out WorldCommandPayload>,
        delta: DomainDelta,
        summary: TimeAdvanceSummaryView?
    ) {
        val world = authoritativeWorld ?: return
        val terminal = world.timeAdvance
            ?.takeIf { it.commandEpoch == envelope.sessionEpoch && it.commandId == envelope.commandId && it.status != null }
            ?.let { state ->
                val result = checkNotNull(state.status)
                val decision = result == TimeAdvanceResult.DECISION_REQUIRED
                PublicTimeAdvanceTerminal(
                    commandId = envelope.commandId,
                    result = result,
                    gateId = state.pendingDecisionGateId.takeIf { decision },
                    choices = state.pendingDecisionChoiceIds.takeIf { decision }.orEmpty().map(PublicTimeAdvanceChoice::fromChoiceId),
                    pendingSuffixHash = state.pendingSuffix?.hash.takeIf { decision },
                    sealedOutcomeHash = state.sealedElapsedOutcome?.hash.takeIf { decision }
                )
            }
            latestPublication = CommittedPublication(
            PublicSnapshot(epoch, stateVersion, world.clock),
            delta.events.asSequence()
                .filter(::isSummaryVisible)
                .map { event -> PublicDomainEvent(event.eventId, event.gameMinute, event.payload.codecId) }
                .toList(),
            envelope.commandId,
            terminal,
            summary
        )
        publicationFlow.value = latestPublication
    }

    private fun updateTimeAdvanceSummary(
        envelope: CommandEnvelope<out WorldCommandPayload>,
        delta: DomainDelta,
        receiptKey: ReceiptKey
    ): TimeAdvanceSummaryView? {
        if (envelope.payload !is AdvanceTimePayload) return null
        val before = delta.worldChange?.before ?: return null
        val context = timeAdvanceSummaryInputs[receiptKey]
            ?: TimeAdvanceSummaryInput(before, envelope.actorId).also { timeAdvanceSummaryInputs[receiptKey] = it }
        context.visibleEvents += delta.events.filter(::isSummaryVisible)

        val world = authoritativeWorld ?: return null
        val terminal = world.timeAdvance
            ?.takeIf { it.commandEpoch == envelope.sessionEpoch && it.commandId == envelope.commandId && it.status != null }
            ?: return null
        val summary = runCatching { buildTimeAdvanceSummary(context, world, terminal) }.getOrNull()
        if (summary != null) timeAdvanceSummaryInputs.remove(receiptKey)
        return summary
    }

    private fun buildTimeAdvanceSummary(
        input: TimeAdvanceSummaryInput,
        terminalWorld: AuthoritativeWorldState,
        terminal: TimeAdvanceState
    ): TimeAdvanceSummaryView {
        val terminalReason = checkNotNull(terminal.status)
        val publicEvents = input.visibleEvents.sortedWith(
            compareBy<DomainEvent<out DomainEventPayload>> { it.gameMinute.value }
                .thenBy { it.subMinuteMs.value }
                .thenBy { it.eventSequence.value }
        )
        val knownEvents = publicEvents.filter(::isKnownSummaryEvent)
        val majorEvents = knownEvents.filter { it.importance.ordinal >= EventImportance.HIGH.ordinal }
            .map { PublicTimeAdvanceEvent(it.payload.codecId, it.gameMinute) }
        val importantChanges = knownEvents.filter { it.importance == EventImportance.NORMAL }
            .map { PublicTimeAdvanceEvent(it.payload.codecId, it.gameMinute) }
        val lowBundles = publicEvents.filter { it.importance == EventImportance.LOW }
            .groupBy { it.payload.codecId }
            .entries
            .sortedBy { entry -> publicEvents.indexOfFirst { it.payload.codecId == entry.key } }
            .map { PublicLowImportanceBundle(it.key, it.value.size) }
        val visibleActions = terminalWorld.calendar.actions.filter { action ->
            input.observerId != null &&
                ((action.payload.ownerType == "PLAYER" && action.payload.ownerId == input.observerId) ||
                    action.participants.any { it.entityType == "PLAYER" && it.entityId == input.observerId })
        }
        val completedWork = visibleActions
            .asSequence()
            .filter { action ->
                action.status == ScheduledActionStatus.COMPLETED &&
                    input.startWorld.calendar.actions.firstOrNull { it.actionId == action.actionId }?.status != ScheduledActionStatus.COMPLETED
            }
            .sortedBy { it.actionId.value }
            .map { PublicCompletedWork(it.actionKind, it.dueMinute) }
            .toList()
        val resourceWarnings = visibleActions.flatMap { action -> action.claims.map(ResourceClaim::resource) }
            .distinct()
            .sortedWith(compareBy<ResourceIdentity> { it.kind }.thenBy { it.id })
            .filter { resource ->
                input.startWorld.calendar.available(resource) > 0L && terminalWorld.calendar.available(resource) == 0L
            }
            .map { resource -> PublicResourceWarning(resource.kind, terminalWorld.calendar.available(resource)) }
        val continuation = when (terminalReason) {
            TimeAdvanceResult.INTERRUPTED -> PublicTimeAdvanceContinuation.RESUME
            TimeAdvanceResult.DECISION_REQUIRED -> PublicTimeAdvanceContinuation.DECISION
            else -> null
        }
        val nextAction = when (terminalReason) {
            TimeAdvanceResult.INTERRUPTED -> PublicTimeAdvanceNextAction.CONTINUE
            TimeAdvanceResult.DECISION_REQUIRED -> PublicTimeAdvanceNextAction.CHOOSE_DECISION
            TimeAdvanceResult.FAILED -> PublicTimeAdvanceNextAction.RETRY
            else -> PublicTimeAdvanceNextAction.ACKNOWLEDGE
        }
        return TimeAdvanceSummaryView(
            elapsedMinutes = Math.subtractExact(terminalWorld.clock.minute.value, input.startWorld.clock.minute.value),
            terminalReason = terminalReason,
            majorEvents = majorEvents.take(TimeAdvanceSummaryView.MAX_SECTION_ITEMS),
            majorEventsOverflowCount = (majorEvents.size - TimeAdvanceSummaryView.MAX_SECTION_ITEMS).coerceAtLeast(0),
            completedWork = completedWork.take(TimeAdvanceSummaryView.MAX_SECTION_ITEMS),
            completedWorkOverflowCount = (completedWork.size - TimeAdvanceSummaryView.MAX_SECTION_ITEMS).coerceAtLeast(0),
            resourceWarnings = resourceWarnings.take(TimeAdvanceSummaryView.MAX_SECTION_ITEMS),
            resourceWarningsOverflowCount = (resourceWarnings.size - TimeAdvanceSummaryView.MAX_SECTION_ITEMS).coerceAtLeast(0),
            importantChanges = importantChanges.take(TimeAdvanceSummaryView.MAX_SECTION_ITEMS),
            importantChangesOverflowCount = (importantChanges.size - TimeAdvanceSummaryView.MAX_SECTION_ITEMS).coerceAtLeast(0),
            lowImportanceBundles = lowBundles.take(TimeAdvanceSummaryView.MAX_SECTION_ITEMS),
            lowImportanceBundlesOverflowCount = (lowBundles.size - TimeAdvanceSummaryView.MAX_SECTION_ITEMS).coerceAtLeast(0),
            unknownImportantEventCount = publicEvents.count {
                it.importance.ordinal >= EventImportance.HIGH.ordinal && !isKnownSummaryEvent(it)
            },
            continuation = continuation,
            nextAction = nextAction
        )
    }

    private fun isKnownSummaryEvent(event: DomainEvent<out DomainEventPayload>): Boolean =
        event.payload.codecId == ElapsedActionAppliedEventPayload.CODEC_ID ||
            BoundaryDomainEventType.forCodec(event.payload.codecId) != null

    private fun isSummaryVisible(event: DomainEvent<out DomainEventPayload>): Boolean =
        event.visibility != EventVisibility.SYSTEM_HIDDEN && event.visibility in allowedSummaryEventVisibilities

    private fun stopAfterUncertainCommit() {
        sessionJob.cancel(kotlinx.coroutines.CancellationException("commit outcome cannot be reconciled"))
    }

    private fun sameAggregateState(actual: AggregateState?, expected: AggregateState?): Boolean =
        actual?.aggregateId == expected?.aggregateId &&
            actual?.aggregateType == expected?.aggregateType &&
            actual?.canonicalJson() == expected?.canonicalJson()

    private fun validateEventMetadata(
        envelope: CommandEnvelope<out WorldCommandPayload>,
        events: List<DomainEvent<out DomainEventPayload>>,
        expectedVersion: StateVersion,
        expectedSequenceStart: Long = 0
    ): String? = events.withIndex().firstOrNull { (index, event) ->
        event.sourceEpoch != envelope.sessionEpoch ||
            event.sourceCommandId != envelope.commandId ||
            event.sourceVersion != expectedVersion ||
            event.eventSequence != EventSequence(Math.addExact(expectedSequenceStart, index.toLong()))
    }?.let { (index, _) -> "event metadata mismatch at index $index" }

    private data class TimeAdvanceSummaryInput(
        val startWorld: AuthoritativeWorldState,
        val observerId: EntityId?,
        val visibleEvents: MutableList<DomainEvent<out DomainEventPayload>> = mutableListOf()
    )

    private data class QueuedCommand(
        val envelope: CommandEnvelope<out WorldCommandPayload>,
        val reply: CompletableDeferred<CommandResult>,
        val accepted: CompletableDeferred<Long> = CompletableDeferred()
    )

    private data class ReceiptKey(val epoch: SessionEpoch, val commandId: CommandId)

    internal companion object {
        const val RECEIPT_CACHE_MAX_ENTRIES = 256
        val ADVANCE_CONTROLS = setOf(AdvanceControl.PAUSE, AdvanceControl.CANCEL_ADVANCE, AdvanceControl.APP_BACKGROUND, AdvanceControl.CLOSE)
    }
}

/**
 * Process-local owner for the one authoritative [WorldSession] writer.
 * A new lease is refused until the previous session's close drain has completed.
 */
object ProcessWorldSessionCoordinator {
    internal data class Lease(
        val generation: Long,
        val session: WorldSession,
        var closeCompletion: CompletableDeferred<CloseResult>? = null
    )

    // ponytail: one global world writer; use keyed coordination only when multiple worlds are supported.
    private val mutex = Mutex()
    private var nextGeneration = 0L
    private var activeLease: Lease? = null

    suspend fun acquire(
        epoch: SessionEpoch,
        savePort: SavePort,
        parentScope: CoroutineScope,
        contentSnapshot: ContentSnapshot,
        initialSnapshot: WorldSnapshot,
        engine: WorldEngine,
        dispatcher: CoroutineDispatcher = Dispatchers.Default,
        summaryVisibleEventVisibilities: Set<EventVisibility> = setOf(EventVisibility.PUBLIC)
    ): WorldSessionHandle? = acquireForTest {
        WorldSession(
            epoch,
            savePort,
            parentScope,
            contentSnapshot,
            initialSnapshot,
            engine,
            dispatcher,
            summaryVisibleEventVisibilities
        )
    }

    internal suspend fun acquireForTest(create: () -> WorldSession): WorldSessionHandle? = mutex.withLock {
        if (activeLease != null) return@withLock null
        val lease = Lease(++nextGeneration, create())
        activeLease = lease
        WorldSessionHandle(this, lease)
    }

    private suspend fun close(lease: Lease, reason: CloseReason): CloseResult? {
        val (completion, startsClose) = mutex.withLock {
            lease.closeCompletion?.let { return@withLock it to false }
            if (activeLease !== lease) return null
            CompletableDeferred<CloseResult>().also { lease.closeCompletion = it } to true
        }

        if (startsClose) {
            withContext(NonCancellable) {
                try {
                    val result = lease.session.close(reason)
                    mutex.withLock {
                        if (activeLease === lease) activeLease = null
                    }
                    completion.complete(result)
                } catch (failure: Throwable) {
                    // Keep the lease active: allowing another writer after an uncertain close is unsafe.
                    completion.completeExceptionally(failure)
                }
            }
        }
        return completion.await()
    }

    class WorldSessionHandle internal constructor(
        private val coordinator: ProcessWorldSessionCoordinator,
        private val lease: Lease
    ) {
        val publications: StateFlow<CommittedPublication?>
            get() = lease.session.publications
        val runtimeState: StateFlow<SessionRuntimeState>
            get() = lease.session.runtimeState

        suspend fun execute(envelope: CommandEnvelope<out WorldCommandPayload>): CommandResult =
            lease.session.execute(envelope)

        suspend fun requestControl(request: ControlRequest): ControlRequestResult =
            lease.session.requestControl(request)

        suspend fun refreshTimeAdvanceSummary(commandId: CommandId): TimeAdvanceSummaryView? =
            lease.session.refreshTimeAdvanceSummary(commandId)

        suspend fun pause(): SessionRuntimeState = lease.session.pause()

        suspend fun resume(): SessionRuntimeState = lease.session.resume()

        suspend fun close(reason: CloseReason = CloseReason.APPLICATION_REQUEST): CloseResult? =
            coordinator.close(lease, reason)
    }
}
