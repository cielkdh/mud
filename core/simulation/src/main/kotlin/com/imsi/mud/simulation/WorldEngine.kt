package com.imsi.mud.simulation

import java.nio.charset.StandardCharsets

fun interface DecisionSelectionEvaluator {
    fun apply(snapshot: WorldTraversalSnapshot, selection: DecisionSelection): WorldTraversalSnapshot
}

/** Pure action-local calculation; it cannot commit, publish, or see caller-supplied outcomes. */
internal fun interface AtomicElapsedOutcomeCalculator {
    fun calculate(
        snapshot: WorldTraversalSnapshot,
        payload: AtomicElapsedActionPayload,
        actionRng: RngStreamState
    ): Checked<AtomicElapsedOutcomeResult>
}

internal data class AtomicElapsedOutcomeResult(
    val outcome: SealedElapsedOutcome,
    val actionRng: RngStreamState
)

/** Same-module proof hook; production domains provide their concrete aggregate applier inside the outer command planner. */
internal fun interface AtomicElapsedOutcomeApplier {
    fun apply(snapshot: WorldTraversalSnapshot, outcome: SealedElapsedOutcome): WorldTraversalSnapshot
}

private sealed interface AtomicElapsedPreflight {
    data class Rejected(val error: DomainError) : AtomicElapsedPreflight
    data class Completed(val result: TimeTraversalResult) : AtomicElapsedPreflight
    data class Decision(val result: TimeTraversalResult, val gate: BoundaryCandidate) : AtomicElapsedPreflight
}

/** Pure P2 command planner. Persistence and publication remain in WorldSession. */
class WorldEngine(
    private val sources: List<BoundarySource>,
    customEvaluator: BoundaryEvaluator? = null,
    private val selectionEvaluator: DecisionSelectionEvaluator? = null
) {
    private var atomicElapsedOutcomeCalculator: AtomicElapsedOutcomeCalculator = AtomicElapsedOutcomeCalculator { _, payload, _ ->
        Checked.Rejected(DomainError.UnsupportedFeature("atomic elapsed outcome calculator for ${payload.actionKind}"))
    }
    private var atomicElapsedOutcomeApplier: AtomicElapsedOutcomeApplier = AtomicElapsedOutcomeApplier { _, _ ->
        throw IllegalStateException("atomic elapsed outcome applier is not registered")
    }
    private val scheduleService = ScheduleService()
    private val defaultEvaluator = BoundaryEvaluator { snapshot, candidate ->
        val updated = when (candidate.candidateKind) {
            "scheduled.action.start.v1" -> scheduleService.start(requiredEntityId(candidate.key.stableEntityId), snapshot.calendar, candidate.key.boundaryTime)
                .updatedSnapshot(snapshot)
            "scheduled.action.complete.v1" -> scheduleService.complete(requiredEntityId(candidate.key.stableEntityId), snapshot.calendar)
                .updatedSnapshot(snapshot, completed = requiredEntityId(candidate.key.stableEntityId))
            "calendar.day.start.v1", "calendar.month.start.v1", "calendar.year.start.v1" -> snapshot
            else if candidate.disposition == BoundaryDisposition.DECISION_GATE -> snapshot
            else -> throw IllegalArgumentException("no evaluator is registered for ${candidate.candidateKind}")
        }
        // Built-in boundaries must materialize their canonical event; otherwise UntilEvent
        // can settle an action while observing no completion event.
        val events = if (candidate.candidateKind in DEFAULT_CANDIDATE_KINDS) listOf(candidate.eventDraft()) else emptyList()
        BoundaryEvaluation(updated, events)
    }
    private val evaluator = customEvaluator ?: defaultEvaluator

    init {
        require(sources.map(BoundarySource::sourceId) == sources.map(BoundarySource::sourceId).sorted()) { "boundary sources must be canonical sorted" }
        require(sources.map(BoundarySource::sourceId).distinct().size == sources.size) { "boundary sources must be unique" }
        require(sources.none { it.sourceId == SYNTHETIC_ELAPSED_SOURCE_ID }) { "elapsed action source is engine-owned" }
    }

    internal fun useAtomicElapsedOutcomeCalculatorForConformance(calculator: AtomicElapsedOutcomeCalculator): WorldEngine = apply {
        atomicElapsedOutcomeCalculator = calculator
    }

    internal fun useAtomicElapsedOutcomeApplierForConformance(applier: AtomicElapsedOutcomeApplier): WorldEngine = apply {
        atomicElapsedOutcomeApplier = applier
    }

    fun plan(
        envelope: CommandEnvelope<out WorldCommandPayload>,
        before: WorldSnapshot,
        nextVersion: StateVersion,
        submissionSequence: Long,
        controlAtSafeBoundary: AdvanceControl? = null
    ): ExecutionPlan = when (val payload = envelope.payload) {
        is AdvanceTimePayload -> planAdvance(envelope, payload, before, nextVersion, submissionSequence, controlAtSafeBoundary)
        is AtomicElapsedActionPayload -> planAtomicElapsed(envelope, payload, before, nextVersion, submissionSequence)
        is AtomicElapsedDecisionPayload -> planAtomicElapsedDecision(envelope, payload, before, nextVersion, submissionSequence)
        is ScheduleGameplayPayload -> planSchedule(payload, before, submissionSequence)
        is UnsupportedFeaturePayload -> ExecutionPlan.Atomic(rejectedDelta(before, DomainError.UnsupportedFeature(payload.feature), submissionSequence))
    }

    private fun planSchedule(
        payload: ScheduleGameplayPayload,
        before: WorldSnapshot,
        submissionSequence: Long
    ): ExecutionPlan.Atomic {
        val calendar = before.world.calendar
        val updated = when (payload) {
            is ScheduleReservePayload -> {
                if (payload.effectiveMinute != before.world.clock.minute) {
                    return ExecutionPlan.Atomic(rejectedDelta(before, DomainError.ValidationError("effectiveMinute", "must equal current world minute"), submissionSequence))
                }
                when (val result = scheduleService.reserve(payload.request(), calendar)) {
                is ReservationResult.Accepted -> result.calendar
                is ReservationResult.Conflict -> return ExecutionPlan.Atomic(rejectedDelta(before, DomainError.ScheduleConflict(result.details.conflictingActionIds), submissionSequence))
                is ReservationResult.Rejected -> return ExecutionPlan.Atomic(rejectedDelta(before, result.error, submissionSequence))
                }
            }
            is ScheduleCancelPayload -> {
                val action = calendar.actions.firstOrNull { it.actionId == payload.actionId }
                    ?: return ExecutionPlan.Atomic(rejectedDelta(before, DomainError.ValidationError("actionId", "does not exist"), submissionSequence))
                if (action.rowVersion != payload.expectedRowVersion) {
                    return ExecutionPlan.Atomic(rejectedDelta(before, DomainError.VersionConflict(payload.expectedRowVersion, action.rowVersion), submissionSequence))
                }
                when (val result = scheduleService.cancel(payload.actionId, calendar, payload.stage)) {
                    is Checked.Value -> result.value.calendar
                    is Checked.Rejected -> return ExecutionPlan.Atomic(rejectedDelta(before, result.error, submissionSequence))
                }
            }
            is ScheduleResolveConflictPayload -> {
                if (payload.reservation.effectiveMinute != before.world.clock.minute) {
                    return ExecutionPlan.Atomic(rejectedDelta(before, DomainError.ValidationError("effectiveMinute", "must equal current world minute"), submissionSequence))
                }
                when (val result = scheduleService.resolveConflict(
                    payload.reservation.request(), calendar, payload.selected, payload.expectedRowVersions, payload.previewCodec, payload.previewHash
                )) {
                is ResolutionResult.Applied -> result.calendar
                is ResolutionResult.RescheduleRequired -> return ExecutionPlan.Atomic(rejectedDelta(before, DomainError.ScheduleConflict(result.conflict.conflictingActionIds), submissionSequence))
                is ResolutionResult.Rejected -> return ExecutionPlan.Atomic(rejectedDelta(before, result.error, submissionSequence))
                }
            }
        }
        return ExecutionPlan.Atomic(acceptedDelta(before, before.world.copy(calendar = updated), submissionSequence))
    }

    private fun planAtomicElapsed(
        envelope: CommandEnvelope<out WorldCommandPayload>,
        payload: AtomicElapsedActionPayload,
        before: WorldSnapshot,
        nextVersion: StateVersion,
        submissionSequence: Long
    ): ExecutionPlan {
        atomicBindingError(before)?.let { return ExecutionPlan.Atomic(rejectedDelta(before, it, submissionSequence)) }
        if (payload.targetMinute.value <= before.world.clock.minute.value || payload.targetMinute.value > payload.limits.maxAdvanceMinute.value) {
            return ExecutionPlan.Atomic(rejectedDelta(before, DomainError.ScheduledActionRequired(payload.mode), submissionSequence))
        }
        val streamKey = actionRngKey(payload)
        val actionRng = before.rngState.streams.firstOrNull { it.streamKey == streamKey }
            ?: return ExecutionPlan.Atomic(rejectedDelta(before, DomainError.SystemHalted("missing action-local RNG stream: ${streamKey.value}"), submissionSequence))
        val calculated = when (val result = atomicElapsedOutcomeCalculator.calculate(traversalSnapshot(before), payload, actionRng)) {
            is Checked.Value -> result.value
            is Checked.Rejected -> return ExecutionPlan.Atomic(rejectedDelta(before, result.error, submissionSequence))
        }
        validateCalculatedOutcome(payload, actionRng, calculated)?.let { error ->
            return ExecutionPlan.Atomic(rejectedDelta(before, error, submissionSequence))
        }
        val nextRng = before.rngState.withReplacedStream(calculated.actionRng)
        return when (val preflight = preflightAtomicElapsed(before, payload, calculated.outcome)) {
            is AtomicElapsedPreflight.Rejected -> ExecutionPlan.Atomic(rejectedDelta(before, preflight.error, submissionSequence))
            is AtomicElapsedPreflight.Completed -> {
                val drafts = preflight.result.eventDrafts
                val state = atomicState(
                    payload = payload,
                    envelope = envelope,
                    cursor = preflight.result.cursor,
                    processed = preflight.result.processedBoundaryCount,
                    status = TimeAdvanceResult.COMPLETED,
                    outcome = calculated.outcome
                ).copy(nextEventSequence = drafts.size.toLong())
                ExecutionPlan.Atomic(
                    acceptedDelta(
                        before,
                        before.world.copy(clock = preflight.result.snapshot.clock, calendar = preflight.result.snapshot.calendar, timeAdvance = state),
                        submissionSequence,
                        nextRng,
                        preflight.result.snapshot.aggregates,
                        materializeEvents(envelope, nextVersion, 0, drafts)
                    )
                )
            }
            is AtomicElapsedPreflight.Decision -> {
                val drafts = preflight.result.eventDrafts
                val state = atomicState(
                    payload = payload,
                    envelope = envelope,
                    cursor = preflight.result.cursor,
                    processed = preflight.result.processedBoundaryCount,
                    status = TimeAdvanceResult.DECISION_REQUIRED,
                    outcome = calculated.outcome,
                    gate = preflight.gate,
                    suffix = preflight.result.pendingSuffix
                ).copy(nextEventSequence = drafts.size.toLong())
                ExecutionPlan.Segment(
                    0,
                    acceptedDelta(
                        before,
                        before.world.copy(clock = preflight.result.snapshot.clock, calendar = preflight.result.snapshot.calendar, timeAdvance = state),
                        submissionSequence,
                        nextRng,
                        preflight.result.snapshot.aggregates,
                        materializeEvents(envelope, nextVersion, 0, drafts)
                    ),
                    state,
                    TimeAdvanceResult.DECISION_REQUIRED
                )
            }
        }
    }

    private fun planAtomicElapsedDecision(
        envelope: CommandEnvelope<out WorldCommandPayload>,
        payload: AtomicElapsedDecisionPayload,
        before: WorldSnapshot,
        nextVersion: StateVersion,
        submissionSequence: Long
    ): ExecutionPlan {
        atomicBindingError(before)?.let { return ExecutionPlan.Atomic(rejectedDelta(before, it, submissionSequence)) }
        val prior = before.world.timeAdvance
            ?: return ExecutionPlan.Atomic(rejectedDelta(before, DomainError.SystemHalted("atomic decision predecessor state is missing"), submissionSequence))
        val sealedOutcome = prior.sealedElapsedOutcome
            ?: return ExecutionPlan.Atomic(rejectedDelta(before, DomainError.SystemHalted("atomic decision predecessor outcome is missing"), submissionSequence))
        val pendingSuffix = prior.pendingSuffix
            ?: return ExecutionPlan.Atomic(rejectedDelta(before, DomainError.SystemHalted("atomic decision predecessor suffix is missing"), submissionSequence))
        if (SealedElapsedOutcome.decode(sealedOutcome.encode(), sealedOutcome.hash) != Checked.Value(sealedOutcome) ||
            PendingBoundarySuffix.decode(pendingSuffix.canonicalPayload, pendingSuffix.hash) != Checked.Value(pendingSuffix)
        ) {
            return ExecutionPlan.Atomic(rejectedDelta(before, DomainError.SystemHalted("atomic decision predecessor payload is corrupt"), submissionSequence))
        }
        val restoredBytes = pendingSuffix.canonicalPayload.toByteArray(StandardCharsets.UTF_8).size +
            sealedOutcome.encode().toByteArray(StandardCharsets.UTF_8).size
        if (restoredBytes > WorldTimeTraversal.MAX_PENDING_BATCH_BYTES) {
            return ExecutionPlan.Atomic(rejectedDelta(before, DomainError.SystemHalted("atomic decision predecessor payload exceeds byte cap"), submissionSequence))
        }
        if (prior.status != TimeAdvanceResult.DECISION_REQUIRED ||
            prior.commandEpoch != payload.predecessorEpoch ||
            prior.commandId != payload.predecessorCommandId ||
            prior.pendingDecisionGateId != payload.gateId ||
            payload.choiceId !in prior.pendingDecisionChoiceIds ||
            prior.pendingSuffix?.hash != payload.pendingSuffixHash ||
            prior.sealedElapsedOutcome?.hash != payload.sealedOutcomeHash ||
            payload.selectionCodec != ATOMIC_ELAPSED_DECISION_SELECTION_CODEC ||
            payload.canonicalSelectionPayload != atomicSelectionPayload(payload.choiceId)
        ) {
            return ExecutionPlan.Atomic(rejectedDelta(before, DomainError.SystemHalted("invalid atomic elapsed decision continuation"), submissionSequence))
        }
        val goal = prior.goal as? TimeAdvanceGoal.UntilMinute
            ?: return ExecutionPlan.Atomic(rejectedDelta(before, DomainError.SystemHalted("atomic elapsed predecessor target is missing"), submissionSequence))
        val limits = prior.limits
            ?: return ExecutionPlan.Atomic(rejectedDelta(before, DomainError.SystemHalted("atomic elapsed predecessor limits are missing"), submissionSequence))
        val continuation = TimeAdvanceContinuation(
            payload.predecessorEpoch,
            payload.predecessorCommandId,
            envelope.commandId,
            payload.pendingSuffixHash,
            payload.sealedOutcomeHash,
            payload.selection
        )
        val continuationResult = continueAtomicElapsed(
            before = traversalSnapshot(before),
            goal = goal,
            limits = limits,
            cursor = prior.cursor,
            suffix = pendingSuffix,
            selection = payload.selection,
            outcome = sealedOutcome,
            approvedCodecs = before.world.boundaryBinding.candidateCodecs,
            mode = prior.progressionMode
        )
        val result = when (continuationResult) {
            is Checked.Value -> continuationResult.value
            is Checked.Rejected -> return ExecutionPlan.Atomic(rejectedDelta(before, continuationResult.error, submissionSequence))
        }
        val drafts = listOf(decisionSelectionDraft(payload, before.world.clock.minute)) + result.eventDrafts
        val state = prior.copy(
            goal = goal,
            cursor = result.cursor,
            processedBoundaryCount = prior.processedBoundaryCount + result.processedBoundaryCount,
            status = TimeAdvanceResult.COMPLETED,
            commandEpoch = envelope.sessionEpoch,
            commandId = envelope.commandId,
            continuationOfCommandId = payload.predecessorCommandId,
            pendingDecisionGateId = null,
            pendingDecisionChoiceIds = emptyList(),
            selectedDecisionChoiceId = payload.choiceId,
            pendingSuffix = null,
            segmentNo = 0,
            nextEventSequence = drafts.size.toLong()
        )
        return ExecutionPlan.Segment(
            0,
            acceptedDelta(
                before,
                before.world.copy(clock = result.snapshot.clock, calendar = result.snapshot.calendar, timeAdvance = state),
                submissionSequence,
                afterAggregates = result.snapshot.aggregates,
                events = materializeEvents(envelope, nextVersion, 0, drafts)
            ),
            state,
            TimeAdvanceResult.COMPLETED,
            continuation
        )
    }

    private fun preflightAtomicElapsed(
        before: WorldSnapshot,
        payload: AtomicElapsedActionPayload,
        outcome: SealedElapsedOutcome
    ): AtomicElapsedPreflight {
        val goal = TimeAdvanceGoal.UntilMinute(payload.targetMinute)
        val traversal = atomicTraversal(before.world.boundaryBinding.candidateCodecs)
        val initial = traversalSnapshot(before)
        val first = traversal.traverse(initial, payload.targetMinute, goal, payload.limits, atomicSources(outcome))
        first.failure?.let { return AtomicElapsedPreflight.Rejected(first.error ?: DomainError.SystemHalted("atomic elapsed preflight halted")) }
        return when (first.result) {
            TimeAdvanceResult.COMPLETED -> AtomicElapsedPreflight.Completed(first)
            TimeAdvanceResult.DECISION_REQUIRED -> {
                val gate = first.stopCandidate
                    ?: return AtomicElapsedPreflight.Rejected(DomainError.SystemHalted("atomic elapsed preflight gate is missing"))
                val choices = gate.decisionChoiceIds
                if (choices.size !in 1..8 || choices != choices.sorted() || choices.distinct().size != choices.size || selectionEvaluator == null) {
                    return AtomicElapsedPreflight.Rejected(DomainError.ScheduledActionRequired(payload.mode))
                }
                val suffixBytes = first.pendingSuffix?.canonicalPayload?.toByteArray(StandardCharsets.UTF_8)?.size ?: 0
                val outcomeBytes = outcome.encode().toByteArray(StandardCharsets.UTF_8).size
                if (suffixBytes + outcomeBytes > WorldTimeTraversal.MAX_PENDING_BATCH_BYTES) {
                    return AtomicElapsedPreflight.Rejected(DomainError.BoundaryLimitReached("sealed elapsed continuation exceeds pending byte cap"))
                }
                val branchBudget = payload.limits.maxBoundaryCount - first.processedBoundaryCount
                if (branchBudget <= 0) return AtomicElapsedPreflight.Rejected(DomainError.ScheduledActionRequired(payload.mode))
                val branchStates = choices.map { choice ->
                    val branch = continueAtomicElapsed(
                        first.snapshot,
                        goal,
                        payload.limits.copy(maxBoundaryCount = branchBudget),
                        first.cursor,
                        first.pendingSuffix,
                        atomicSelection(gate, choice),
                        outcome,
                        before.world.boundaryBinding.candidateCodecs,
                        payload.mode
                    )
                    when (branch) {
                        is Checked.Value -> branch.value.also {
                            if (first.processedBoundaryCount + it.processedBoundaryCount > payload.limits.maxBoundaryCount) {
                                return AtomicElapsedPreflight.Rejected(DomainError.ScheduledActionRequired(payload.mode))
                            }
                        }.snapshot
                        is Checked.Rejected -> return AtomicElapsedPreflight.Rejected(DomainError.ScheduledActionRequired(payload.mode))
                    }
                }
                check(branchStates.size == choices.size)
                AtomicElapsedPreflight.Decision(first, gate)
            }
            else -> AtomicElapsedPreflight.Rejected(DomainError.ScheduledActionRequired(payload.mode))
        }
    }

    private fun continueAtomicElapsed(
        before: WorldTraversalSnapshot,
        goal: TimeAdvanceGoal.UntilMinute,
        limits: TimeTraversalLimits,
        cursor: BoundaryCursor?,
        suffix: PendingBoundarySuffix?,
        selection: DecisionSelection,
        outcome: SealedElapsedOutcome,
        approvedCodecs: Set<String>,
        mode: ProgressionMode
    ): Checked<TimeTraversalResult> {
        val evaluator = selectionEvaluator
            ?: return Checked.Rejected(DomainError.SystemHalted("atomic elapsed continuation has no selection evaluator"))
        val selected = try {
            evaluator.apply(before, selection)
        } catch (error: Exception) {
            return Checked.Rejected(DomainError.SystemHalted("atomic elapsed decision evaluator failed: ${error.message ?: error::class.simpleName}"))
        }
        if (selected.clock != before.clock) {
            return Checked.Rejected(DomainError.SystemHalted("atomic elapsed selection must not change world clock"))
        }
        if (introducesPastOrCurrentAction(before, selected, before.clock.minute)) {
            return Checked.Rejected(DomainError.SystemHalted("atomic elapsed selection introduced a past or current scheduled action"))
        }
        var current = selected
        var currentCursor = cursor
        var processed = 0
        val drafts = mutableListOf<DomainEventDraft>()
        if (suffix != null) {
            val frozen = atomicTraversal(approvedCodecs).resumeFrozenBatch(
                current,
                goal,
                currentCursor ?: return Checked.Rejected(DomainError.SystemHalted("atomic elapsed suffix cursor is missing")),
                suffix
            )
            if (frozen.failure != null || frozen.result == TimeAdvanceResult.DECISION_REQUIRED) {
                return Checked.Rejected(frozen.error ?: DomainError.ScheduledActionRequired(mode))
            }
            current = frozen.snapshot
            currentCursor = frozen.cursor
            processed += frozen.processedBoundaryCount
            drafts += frozen.eventDrafts
        }
        if (goal.isSatisfied(current)) {
            return if (current.hasApplied(outcome)) Checked.Value(TimeTraversalResult(TimeAdvanceResult.COMPLETED, current, currentCursor, processed, null, eventDrafts = drafts))
            else Checked.Rejected(DomainError.SystemHalted("sealed elapsed outcome was not applied exactly once"))
        }
        val remaining = limits.maxBoundaryCount - processed
        if (remaining <= 0) return Checked.Rejected(DomainError.ScheduledActionRequired(mode))
        val tail = atomicTraversal(approvedCodecs).traverse(
            current,
            goal.minute,
            goal,
            limits.copy(maxBoundaryCount = remaining),
            atomicSources(outcome),
            currentCursor
        )
        if (tail.failure != null || tail.result != TimeAdvanceResult.COMPLETED) {
            return Checked.Rejected(tail.error ?: DomainError.ScheduledActionRequired(mode))
        }
        if (!tail.snapshot.hasApplied(outcome)) {
            return Checked.Rejected(DomainError.SystemHalted("sealed elapsed outcome was not applied exactly once"))
        }
        return Checked.Value(tail.copy(
            processedBoundaryCount = processed + tail.processedBoundaryCount,
            eventDrafts = drafts + tail.eventDrafts
        ))
    }

    private fun atomicState(
        payload: AtomicElapsedActionPayload,
        envelope: CommandEnvelope<out WorldCommandPayload>,
        cursor: BoundaryCursor?,
        processed: Int,
        status: TimeAdvanceResult,
        outcome: SealedElapsedOutcome,
        gate: BoundaryCandidate? = null,
        suffix: PendingBoundarySuffix? = null
    ) = TimeAdvanceState(
        goal = TimeAdvanceGoal.UntilMinute(payload.targetMinute),
        cursor = cursor,
        processedBoundaryCount = processed,
        status = status,
        commandEpoch = envelope.sessionEpoch,
        commandId = envelope.commandId,
        pendingDecisionGateId = gate?.key?.stableEntityId,
        pendingDecisionChoiceIds = gate?.decisionChoiceIds.orEmpty(),
        pendingSuffix = suffix,
        sealedElapsedOutcome = outcome,
        progressionMode = payload.mode,
        limits = payload.limits,
        interruptPolicy = TimeAdvanceInterruptPolicy()
    )

    private fun planAdvance(
        envelope: CommandEnvelope<out WorldCommandPayload>,
        payload: AdvanceTimePayload,
        before: WorldSnapshot,
        nextVersion: StateVersion,
        submissionSequence: Long,
        controlAtSafeBoundary: AdvanceControl?
    ): ExecutionPlan {
        if (payload.mode == ProgressionMode.TRAVEL) {
            return ExecutionPlan.Atomic(rejectedDelta(before, DomainError.ScheduledActionRequired(payload.mode), submissionSequence))
        }
        if (payload.mode != ProgressionMode.FAST_FORWARD) {
            return ExecutionPlan.Atomic(rejectedDelta(before, DomainError.AtomicElapsedActionRequired(payload.mode), submissionSequence))
        }
        if (before.world.boundaryBinding.sourceIds.filterNot { it == SYNTHETIC_ELAPSED_SOURCE_ID } != sources.map(BoundarySource::sourceId) ||
            before.world.boundaryBinding.boundaryOrderVersion != BOUNDARY_ORDER_VERSION ||
            SYNTHETIC_ELAPSED_SOURCE_ID in before.world.boundaryBinding.sourceIds && SealedElapsedOutcome.CODEC_ID !in before.world.boundaryBinding.candidateCodecs
        ) {
            return ExecutionPlan.Atomic(rejectedDelta(before, DomainError.SystemHalted("boundary registry binding mismatch"), submissionSequence))
        }
        val prior = before.world.timeAdvance
        if (prior != null && prior.status == null) {
            if (prior.commandEpoch != null && (prior.commandEpoch != envelope.sessionEpoch || prior.commandId != envelope.commandId)) {
                return ExecutionPlan.Atomic(rejectedDelta(
                    before,
                    DomainError.AdvanceInProgress(prior.commandId ?: envelope.commandId, ALL_ADVANCE_CONTROLS),
                    submissionSequence
                ))
            }
            return planRunningAdvance(envelope, payload, before, nextVersion, submissionSequence, prior, controlAtSafeBoundary)
        }
        payload.continuation?.let { continuation ->
            return planContinuation(envelope, payload, before, nextVersion, submissionSequence, prior, continuation, controlAtSafeBoundary)
        }
        payload.resumeOfCommandId?.let { predecessor ->
            return planInterruptedResume(envelope, payload, before, nextVersion, submissionSequence, prior, predecessor)
        }
        preflightError(before)?.let { error ->
            return ExecutionPlan.Atomic(rejectedDelta(before, error, submissionSequence))
        }
        return planAdmission(envelope, payload, before, nextVersion, submissionSequence)
    }

    private fun preflightError(before: WorldSnapshot): DomainError? = try {
        val snapshot = traversalSnapshot(before)
        val time = sources.mapNotNull { it.nextTimeAfter(snapshot, null) }.minByOrNull(GameMinute::value) ?: return null
        for (source in sources) {
            for (candidate in source.candidatesAt(snapshot, time)) {
                if (candidate.key.sourceId != source.sourceId || candidate.key.boundaryTime != time) {
                    return DomainError.SystemHalted("boundary source returned an invalid preflight candidate")
                }
                if (before.world.boundaryBinding.candidateCodecs.isNotEmpty() && candidate.payloadCodec !in before.world.boundaryBinding.candidateCodecs) {
                    return DomainError.SystemHalted("boundary candidate codec is not registered")
                }
                if (customEvaluatorMissingFor(candidate)) return DomainError.SystemHalted("no evaluator is registered for ${candidate.candidateKind}")
            }
        }
        null
    } catch (error: Exception) {
        DomainError.SystemHalted("boundary preflight failed: ${error.message ?: error::class.simpleName}")
    }

    private fun customEvaluatorMissingFor(candidate: BoundaryCandidate): Boolean =
        evaluator === defaultEvaluator && candidate.candidateKind !in DEFAULT_CANDIDATE_KINDS && candidate.disposition != BoundaryDisposition.DECISION_GATE

    private fun atomicBindingError(before: WorldSnapshot): DomainError? =
        if (before.world.boundaryBinding.sourceIds != (sources.map(BoundarySource::sourceId) + SYNTHETIC_ELAPSED_SOURCE_ID).sorted() ||
            before.world.boundaryBinding.boundaryOrderVersion != BOUNDARY_ORDER_VERSION
        ) DomainError.SystemHalted("boundary registry binding mismatch")
        else if (SealedElapsedOutcome.CODEC_ID !in before.world.boundaryBinding.candidateCodecs) {
            DomainError.SystemHalted("sealed elapsed outcome codec is not registered")
        } else null

    private fun atomicTraversal(approvedCodecs: Set<String>) = WorldTimeTraversal(
        BoundaryEvaluator { snapshot, candidate ->
            if (candidate.candidateKind == ELAPSED_ACTION_APPLY_KIND) {
                BoundaryEvaluation(applyElapsedOutcome(snapshot, candidate), listOf(candidate.eventDraft()))
            } else evaluator.evaluate(snapshot, candidate)
        },
        { candidate -> if (candidate.disposition == BoundaryDisposition.DECISION_GATE) BoundaryDisposition.DECISION_GATE else BoundaryDisposition.CONTINUE },
        approvedCodecs
    )

    private fun actionRngKey(payload: AtomicElapsedActionPayload): RngStreamKey = when (payload.actionKind) {
        AtomicElapsedActionKind.COMBAT -> canonicalRngStreamKey(RngLeaf.COMBAT_HIT, payload.actionId.value)
        AtomicElapsedActionKind.DUNGEON -> canonicalRngStreamKey(RngLeaf.DUNGEON, payload.actionId.value)
        AtomicElapsedActionKind.NORMAL -> canonicalRngStreamKey(RngLeaf.WORLD_EVENT)
    }

    private fun validateCalculatedOutcome(
        payload: AtomicElapsedActionPayload,
        beforeRng: RngStreamState,
        calculated: AtomicElapsedOutcomeResult
    ): DomainError? = when {
        calculated.outcome.effectiveMinute != payload.targetMinute -> DomainError.SystemHalted("atomic elapsed outcome target mismatch")
        calculated.outcome.domainResultId != payload.actionId -> DomainError.SystemHalted("atomic elapsed outcome action id mismatch")
        calculated.actionRng.streamKey != beforeRng.streamKey ||
            calculated.actionRng.algorithmVersion != beforeRng.algorithmVersion ||
            calculated.actionRng.increment != beforeRng.increment ||
            calculated.actionRng.drawCounter < beforeRng.drawCounter -> DomainError.SystemHalted("atomic elapsed calculator returned an invalid action-local RNG state")
        else -> null
    }

    private fun atomicSelection(gate: BoundaryCandidate, choiceId: String): DecisionSelection =
        DecisionSelection(gate.key.stableEntityId, choiceId, ATOMIC_ELAPSED_DECISION_SELECTION_CODEC, atomicSelectionPayload(choiceId))

    private fun atomicSources(outcome: SealedElapsedOutcome): List<BoundarySource> =
        (sources + AtomicElapsedOutcomeSource(outcome)).sortedBy(BoundarySource::sourceId)

    private fun applyElapsedOutcome(snapshot: WorldTraversalSnapshot, candidate: BoundaryCandidate): WorldTraversalSnapshot {
        val expectedHash = canonicalCodecPayloadHash(SealedElapsedOutcome.CODEC_ID, candidate.canonicalPayload)
        val outcome = when (val decoded = SealedElapsedOutcome.decode(candidate.canonicalPayload, expectedHash)) {
            is Checked.Value -> decoded.value
            is Checked.Rejected -> throw IllegalArgumentException(decoded.error.toString())
        }
        require(outcome.effectiveMinute == candidate.key.boundaryTime && outcome.domainResultId.value == candidate.key.stableEntityId) {
            "sealed elapsed outcome candidate identity mismatch"
        }
        require(snapshot.observedEvents.none { it.eventType == ELAPSED_ACTION_APPLY_KIND && it.subjectId == outcome.domainResultId }) {
            "sealed elapsed outcome was already applied"
        }
        val applied = atomicElapsedOutcomeApplier.apply(snapshot, outcome)
        require(applied.clock == snapshot.clock && applied.calendar == snapshot.calendar &&
            applied.completedActionIds == snapshot.completedActionIds && applied.selectedActionIdsBySelector == snapshot.selectedActionIdsBySelector &&
            applied.satisfiedConditionHashes == snapshot.satisfiedConditionHashes && applied.observedEvents == snapshot.observedEvents
        ) { "atomic elapsed outcome applier may change only authoritative aggregates" }
        require(aggregateChanges(snapshot.aggregates, applied.aggregates).isNotEmpty()) {
            "atomic elapsed outcome applier produced no authoritative aggregate change"
        }
        return applied.copy(observedEvents = applied.observedEvents + ObservedBoundaryEvent(ELAPSED_ACTION_APPLY_KIND, EventImportance.NORMAL, outcome.domainResultId))
    }

    private fun decisionSelectionDraft(
        payload: AtomicElapsedDecisionPayload,
        minute: GameMinute
    ) = decisionSelectionDraft(payload.selection, minute)

    private fun decisionSelectionDraft(
        selection: DecisionSelection,
        minute: GameMinute
    ) = DomainEventDraft(
        stableEventKey = "decision/${selection.gateId}/${selection.choiceId}",
        sourceId = null,
        gameMinute = minute,
        visibility = EventVisibility.SYSTEM_HIDDEN,
        importance = EventImportance.NORMAL,
        payload = DecisionSelectionAppliedEventPayload(
            selection.gateId,
            selection.choiceId,
            selection.codec,
            selection.payloadHash
        )
    )

    private fun materializeEvents(
        envelope: CommandEnvelope<out WorldCommandPayload>,
        nextVersion: StateVersion,
        startSequence: Long,
        drafts: List<DomainEventDraft>
    ): List<DomainEvent<out DomainEventPayload>> = drafts.mapIndexed { index, draft ->
        val sequence = Math.addExact(startSequence, index.toLong())
        DomainEvent(
            eventId = EventId("${envelope.commandId.value}/$sequence/${draft.stableEventKey}"),
            sourceId = draft.sourceId,
            sourceEventId = null,
            sourceEpoch = envelope.sessionEpoch,
            sourceCommandId = envelope.commandId,
            sourceVersion = nextVersion,
            gameMinute = draft.gameMinute,
            subMinuteMs = SubMinuteMillis(0),
            eventSequence = EventSequence(sequence),
            visibility = draft.visibility,
            importance = draft.importance,
            payload = draft.payload
        )
    }

    private fun introducesPastOrCurrentAction(
        before: WorldTraversalSnapshot,
        after: WorldTraversalSnapshot,
        boundaryTime: GameMinute
    ): Boolean {
        val existing = before.calendar.actions.associateBy(ScheduledAction::actionId)
        return after.calendar.actions.any { action ->
            val prior = existing[action.actionId]
            prior == null && (action.startMinute.value <= boundaryTime.value || action.dueMinute.value <= boundaryTime.value) ||
                prior != null && (prior.startMinute != action.startMinute && action.startMinute.value <= boundaryTime.value ||
                prior.dueMinute != action.dueMinute && action.dueMinute.value <= boundaryTime.value)
        }
    }

    private fun planInterruptedResume(
        envelope: CommandEnvelope<out WorldCommandPayload>,
        payload: AdvanceTimePayload,
        before: WorldSnapshot,
        nextVersion: StateVersion,
        submissionSequence: Long,
        prior: TimeAdvanceState?,
        predecessor: CommandId
    ): ExecutionPlan {
        if (prior?.status != TimeAdvanceResult.INTERRUPTED || prior.commandId != predecessor || prior.pendingSuffix != null ||
            prior.goal != payload.goal || prior.progressionMode != payload.mode || prior.limits != payload.limits || prior.interruptPolicy != payload.interruptPolicy
        ) return ExecutionPlan.Atomic(rejectedDelta(before, DomainError.SystemHalted("invalid interrupted time advance resume"), submissionSequence))

        val state = TimeAdvanceState(
            goal = prior.goal,
            cursor = prior.cursor,
            processedBoundaryCount = 0,
            status = null,
            commandEpoch = envelope.sessionEpoch,
            commandId = envelope.commandId,
            continuationOfCommandId = predecessor,
            segmentNo = 0,
            progressionMode = prior.progressionMode,
            limits = prior.limits,
            interruptPolicy = prior.interruptPolicy
        )
        return segment(envelope, nextVersion, 0, before, traversalSnapshot(before), state, null, submissionSequence)
    }

    private fun planAdmission(
        envelope: CommandEnvelope<out WorldCommandPayload>,
        payload: AdvanceTimePayload,
        before: WorldSnapshot,
        nextVersion: StateVersion,
        submissionSequence: Long
    ): ExecutionPlan {
        val initial = traversalSnapshot(before)
        val terminal = TimeAdvanceResult.COMPLETED.takeIf { payload.goal.isSatisfied(initial) }
        val state = TimeAdvanceState(
            goal = payload.goal,
            cursor = null,
            processedBoundaryCount = 0,
            status = terminal,
            commandEpoch = envelope.sessionEpoch,
            commandId = envelope.commandId,
            progressionMode = payload.mode,
            limits = payload.limits,
            interruptPolicy = payload.interruptPolicy
        )
        return segment(envelope, nextVersion, 0, before, initial, state, terminal, submissionSequence)
    }

    private fun planRunningAdvance(
        envelope: CommandEnvelope<out WorldCommandPayload>,
        payload: AdvanceTimePayload,
        before: WorldSnapshot,
        nextVersion: StateVersion,
        submissionSequence: Long,
        prior: TimeAdvanceState,
        controlAtSafeBoundary: AdvanceControl?
    ): ExecutionPlan {
        if (prior.goal != payload.goal || prior.progressionMode != payload.mode || prior.limits != payload.limits || prior.interruptPolicy != payload.interruptPolicy) {
            return ExecutionPlan.Atomic(rejectedDelta(before, DomainError.InvariantViolation("running advance payload differs from its durable request"), submissionSequence))
        }
        val limits = prior.limits ?: return ExecutionPlan.Atomic(rejectedDelta(before, DomainError.SystemHalted("running advance is missing limits"), submissionSequence))
        val policy = prior.interruptPolicy ?: return ExecutionPlan.Atomic(rejectedDelta(before, DomainError.SystemHalted("running advance is missing interrupt policy"), submissionSequence))
        val remaining = limits.maxBoundaryCount - prior.processedBoundaryCount
        if (remaining <= 0) return ExecutionPlan.Segment(
            prior.segmentNo + 1,
            acceptedDelta(before, before.world.copy(timeAdvance = prior.copy(status = TimeAdvanceResult.LIMIT_REACHED)), submissionSequence),
            prior.copy(status = TimeAdvanceResult.LIMIT_REACHED, segmentNo = prior.segmentNo + 1),
            TimeAdvanceResult.LIMIT_REACHED
        )
        // A latched control is observed after the next deterministic boundary batch, never after a whole slice.
        val sliceLimit = 1
        val initial = traversalSnapshot(before)
        val fromFrozenSuffix = prior.pendingSuffix != null
        val result = if (fromFrozenSuffix) {
            traversal(policy, before.world.boundaryBinding.candidateCodecs).resumeFrozenBatch(initial, payload.goal, prior.cursor ?: return ExecutionPlan.Atomic(
                rejectedDelta(before, DomainError.SystemHalted("pending suffix is missing its cursor"), submissionSequence)
            ), prior.pendingSuffix!!)
        } else {
            val target = when (val goal = payload.goal) {
                is TimeAdvanceGoal.UntilMinute -> goal.minute
                else -> limits.maxAdvanceMinute
            }
            traversal(policy, before.world.boundaryBinding.candidateCodecs).traverse(initial, target, payload.goal, limits.copy(maxBoundaryCount = sliceLimit), sources, prior.cursor)
        }
        if (result.failure == TraversalFailure.SYSTEM_HALT) {
            return ExecutionPlan.Atomic(rejectedDelta(before, result.error ?: DomainError.SystemHalted("time traversal halted"), submissionSequence))
        }
        val sliceExhausted = if (fromFrozenSuffix) {
            result.result == TimeAdvanceResult.LIMIT_REACHED
        } else {
            result.result == TimeAdvanceResult.LIMIT_REACHED && result.processedBoundaryCount == sliceLimit && sliceLimit < remaining
        }
        val terminal = terminalResult(result.result, sliceExhausted, controlAtSafeBoundary)
        val state = TimeAdvanceState(
            goal = prior.goal,
            cursor = result.cursor,
            processedBoundaryCount = prior.processedBoundaryCount + result.processedBoundaryCount,
            status = terminal,
            commandEpoch = envelope.sessionEpoch,
            commandId = envelope.commandId,
            continuationOfCommandId = prior.continuationOfCommandId,
            pendingDecisionGateId = result.stopCandidate?.takeIf { result.result == TimeAdvanceResult.DECISION_REQUIRED }?.key?.stableEntityId,
            pendingDecisionChoiceIds = result.stopCandidate?.takeIf { result.result == TimeAdvanceResult.DECISION_REQUIRED }?.decisionChoiceIds.orEmpty(),
            pendingSuffix = result.pendingSuffix,
            sealedElapsedOutcome = prior.sealedElapsedOutcome,
            segmentNo = prior.segmentNo + 1,
            nextEventSequence = Math.addExact(prior.nextEventSequence, result.eventDrafts.size.toLong()),
            progressionMode = prior.progressionMode,
            limits = limits,
            interruptPolicy = policy
        )
        return segment(
            envelope,
            nextVersion,
            state.segmentNo,
            before,
            result.snapshot,
            state,
            terminal,
            submissionSequence,
            eventSequenceStart = prior.nextEventSequence,
            drafts = result.eventDrafts
        )
    }

    private fun planContinuation(
        envelope: CommandEnvelope<out WorldCommandPayload>,
        payload: AdvanceTimePayload,
        before: WorldSnapshot,
        nextVersion: StateVersion,
        submissionSequence: Long,
        prior: TimeAdvanceState?,
        continuation: TimeAdvanceContinuation,
        controlAtSafeBoundary: AdvanceControl?
    ): ExecutionPlan {
        if (continuation.childCommandId != envelope.commandId || prior?.status != TimeAdvanceResult.DECISION_REQUIRED ||
            prior.commandEpoch != continuation.predecessorEpoch || prior.commandId != continuation.predecessorCommandId ||
            prior.pendingSuffix?.hash != continuation.pendingSuffixHash || prior.sealedElapsedOutcome?.hash != continuation.sealedOutcomeHash ||
            prior.pendingDecisionGateId != continuation.selection.gateId ||
            prior.goal != payload.goal || prior.progressionMode != payload.mode || prior.limits != payload.limits || prior.interruptPolicy != payload.interruptPolicy ||
            continuation.selection.choiceId !in prior.pendingDecisionChoiceIds
        ) {
            return ExecutionPlan.Atomic(rejectedDelta(before, DomainError.SystemHalted("invalid time advance continuation"), submissionSequence))
        }
        val selectedSnapshot = (selectionEvaluator ?: return ExecutionPlan.Atomic(
            rejectedDelta(before, DomainError.SystemHalted("decision continuation has no selection evaluator"), submissionSequence)
        )).apply(traversalSnapshot(before), continuation.selection)
        val suffix = prior.pendingSuffix ?: return ExecutionPlan.Atomic(rejectedDelta(before, DomainError.SystemHalted("continuation is missing its suffix"), submissionSequence))
        val result = if (suffix.candidates.isEmpty()) {
            if (prior.goal.isSatisfied(selectedSnapshot)) {
                TimeTraversalResult(TimeAdvanceResult.COMPLETED, selectedSnapshot, prior.cursor, 0, null)
            } else {
                val limits = prior.limits ?: return ExecutionPlan.Atomic(rejectedDelta(before, DomainError.SystemHalted("continuation is missing limits"), submissionSequence))
                val target = when (val goal = prior.goal) {
                    is TimeAdvanceGoal.UntilMinute -> goal.minute
                    else -> limits.maxAdvanceMinute
                }
                traversal(prior.interruptPolicy ?: return ExecutionPlan.Atomic(rejectedDelta(before, DomainError.SystemHalted("continuation is missing interrupt policy"), submissionSequence)), before.world.boundaryBinding.candidateCodecs).traverse(
                    selectedSnapshot,
                    target,
                    prior.goal,
                    limits.copy(maxBoundaryCount = 1),
                    sources,
                    prior.cursor
                )
            }
        } else {
            traversal(prior.interruptPolicy ?: return ExecutionPlan.Atomic(rejectedDelta(before, DomainError.SystemHalted("continuation is missing interrupt policy"), submissionSequence)), before.world.boundaryBinding.candidateCodecs).resumeFrozenBatch(
                selectedSnapshot,
                prior.goal,
                prior.cursor ?: return ExecutionPlan.Atomic(rejectedDelta(before, DomainError.SystemHalted("continuation is missing its cursor"), submissionSequence)),
                suffix
            )
        }
        if (result.failure == TraversalFailure.SYSTEM_HALT) {
            return ExecutionPlan.Atomic(rejectedDelta(before, result.error ?: DomainError.SystemHalted("time traversal halted"), submissionSequence))
        }
        val sliceExhausted = result.result == TimeAdvanceResult.LIMIT_REACHED &&
            (suffix.candidates.isNotEmpty() || result.processedBoundaryCount == 1)
        val terminal = terminalResult(result.result, sliceExhausted, controlAtSafeBoundary)
        val drafts = listOf(decisionSelectionDraft(continuation.selection, before.world.clock.minute)) + result.eventDrafts
        val state = TimeAdvanceState(
            goal = prior.goal,
            cursor = result.cursor,
            processedBoundaryCount = prior.processedBoundaryCount + result.processedBoundaryCount,
            status = terminal,
            commandEpoch = envelope.sessionEpoch,
            commandId = envelope.commandId,
            continuationOfCommandId = continuation.predecessorCommandId,
            pendingDecisionGateId = result.stopCandidate?.takeIf { result.result == TimeAdvanceResult.DECISION_REQUIRED }?.key?.stableEntityId,
            pendingDecisionChoiceIds = result.stopCandidate?.takeIf { result.result == TimeAdvanceResult.DECISION_REQUIRED }?.decisionChoiceIds.orEmpty(),
            selectedDecisionChoiceId = continuation.selection.choiceId,
            pendingSuffix = result.pendingSuffix,
            sealedElapsedOutcome = prior.sealedElapsedOutcome,
            segmentNo = 0,
            nextEventSequence = drafts.size.toLong(),
            progressionMode = prior.progressionMode,
            limits = prior.limits,
            interruptPolicy = prior.interruptPolicy
        )
        return segment(envelope, nextVersion, 0, before, result.snapshot, state, terminal, submissionSequence, continuation, drafts = drafts)
    }

    private fun terminalResult(result: TimeAdvanceResult, sliceExhausted: Boolean, control: AdvanceControl?): TimeAdvanceResult? = when {
        result == TimeAdvanceResult.DECISION_REQUIRED -> result
        result == TimeAdvanceResult.FAILED -> result
        control == AdvanceControl.CANCEL_ADVANCE -> TimeAdvanceResult.CANCELLED
        control != null -> TimeAdvanceResult.INTERRUPTED
        !sliceExhausted -> result
        else -> null
    }

    private fun traversal(policy: TimeAdvanceInterruptPolicy, approvedCodecs: Set<String>) = WorldTimeTraversal(
        evaluator,
        { candidate -> TimeAdvanceInterruptPolicyEvaluator.disposition(candidate, policy) },
        approvedCodecs
    )

    private fun segment(
        envelope: CommandEnvelope<out WorldCommandPayload>,
        nextVersion: StateVersion,
        segmentNo: Int,
        before: WorldSnapshot,
        result: WorldTraversalSnapshot,
        state: TimeAdvanceState,
        terminal: TimeAdvanceResult?,
        submissionSequence: Long,
        continuation: TimeAdvanceContinuation? = null,
        eventSequenceStart: Long = 0,
        drafts: List<DomainEventDraft> = emptyList()
    ): ExecutionPlan.Segment = ExecutionPlan.Segment(
        segmentNo,
        acceptedDelta(
            before,
            before.world.copy(clock = result.clock, calendar = result.calendar, timeAdvance = state),
            submissionSequence,
            afterAggregates = result.aggregates,
            events = materializeEvents(envelope, nextVersion, eventSequenceStart, drafts)
        ),
        state,
        terminal,
        continuation
    )

    private fun acceptedDelta(
        before: WorldSnapshot,
        after: AuthoritativeWorldState,
        submissionSequence: Long,
        nextRng: RngState = before.rngState,
        afterAggregates: Map<EntityId, AggregateState> = before.aggregates,
        events: List<DomainEvent<out DomainEventPayload>> = emptyList()
    ) = DomainDelta(
        aggregateChanges = aggregateChanges(before.aggregates, afterAggregates),
        rngState = nextRng,
        events = events,
        result = CommandResult.Accepted(submissionSequence),
        worldChange = WorldStateChange(before.world, after)
    )

    private fun traversalSnapshot(snapshot: WorldSnapshot) = WorldTraversalSnapshot(
        clock = snapshot.world.clock,
        calendar = snapshot.world.calendar,
        completedActionIds = snapshot.world.calendar.actions.filter { it.status == ScheduledActionStatus.COMPLETED }.mapTo(linkedSetOf()) { it.actionId },
        aggregates = snapshot.aggregates
    )

    private fun aggregateChanges(
        before: Map<EntityId, AggregateState>,
        after: Map<EntityId, AggregateState>
    ): List<AggregateChange> = (before.keys + after.keys).sortedBy(EntityId::value).mapNotNull { id ->
        val old = before[id]
        val new = after[id]
        if (sameAggregate(old, new)) null else AggregateChange(id, old, new)
    }

    private fun sameAggregate(first: AggregateState?, second: AggregateState?): Boolean =
        first === second || first != null && second != null && first.aggregateId == second.aggregateId &&
            first.aggregateType == second.aggregateType && first.canonicalJson() == second.canonicalJson()

    private fun rejectedDelta(before: WorldSnapshot, error: DomainError, submissionSequence: Long) = DomainDelta(
        aggregateChanges = emptyList(),
        rngState = before.rngState,
        events = emptyList(),
        result = CommandResult.Rejected(error, submissionSequence)
    )

}

private const val BOUNDARY_ORDER_VERSION = "BoundaryOrder.v1"
private const val ATOMIC_ELAPSED_DECISION_SELECTION_CODEC = "AtomicElapsedDecisionSelection.v1"
private const val ELAPSED_ACTION_APPLY_KIND = "elapsed.action.apply.v1"
private const val SYNTHETIC_ELAPSED_SOURCE_ID = "elapsed.action"
private val ALL_ADVANCE_CONTROLS = setOf(AdvanceControl.PAUSE, AdvanceControl.CANCEL_ADVANCE, AdvanceControl.APP_BACKGROUND, AdvanceControl.CLOSE)
private val DEFAULT_CANDIDATE_KINDS = setOf(
    "calendar.day.start.v1", "calendar.month.start.v1", "calendar.year.start.v1",
    "scheduled.action.start.v1", "scheduled.action.complete.v1"
)

private fun Checked<ActionTransition>.updatedSnapshot(snapshot: WorldTraversalSnapshot, completed: EntityId? = null): WorldTraversalSnapshot = when (this) {
    is Checked.Value -> snapshot.copy(
        calendar = value.calendar,
        completedActionIds = completed?.let { snapshot.completedActionIds + it } ?: snapshot.completedActionIds
    )
    is Checked.Rejected -> throw IllegalStateException(error.toString())
}

private fun ScheduleReservePayload.request() = ReservationRequest(
    actionId, actionKind, scheduledPayload, startMinute, dueMinute, effectiveMinute
)

private fun requiredEntityId(value: String): EntityId = when (val checked = EntityId.of(value)) {
    is Checked.Value -> checked.value
    is Checked.Rejected -> error(checked.error.toString())
}

private fun atomicSelectionPayload(choiceId: String): String = "{\"choiceId\":${CanonicalJson.string(choiceId)}}"

private fun RngState.withReplacedStream(replacement: RngStreamState): RngState = RngState(
    streams.map { if (it.streamKey == replacement.streamKey) replacement else it }
)

private fun WorldTraversalSnapshot.hasApplied(outcome: SealedElapsedOutcome): Boolean =
    observedEvents.count { it.eventType == ELAPSED_ACTION_APPLY_KIND && it.subjectId == outcome.domainResultId } == 1

private class AtomicElapsedOutcomeSource(private val outcome: SealedElapsedOutcome) : BoundarySource {
    override val sourceId: String = "elapsed.action"

    override fun nextTimeAfter(snapshot: WorldTraversalSnapshot, cursor: BoundaryCursor?): GameMinute? =
        outcome.effectiveMinute.takeIf { minute ->
            minute.value > snapshot.clock.minute.value || minute == snapshot.clock.minute && cursor != null &&
                WorldTimeTraversal.BOUNDARY_KEY_ORDER.compare(candidate().key, cursor.lastCompletedKey) > 0
        }

    override fun candidatesAt(snapshot: WorldTraversalSnapshot, time: GameMinute): List<BoundaryCandidate> =
        if (time == outcome.effectiveMinute) listOf(candidate()) else emptyList()

    private fun candidate() = BoundaryCandidate(
        key = BoundaryKey(
            outcome.effectiveMinute,
            BoundaryCategory.COMBAT_CRISIS,
            0,
            0,
            outcome.domainResultId.value,
            "combat",
            sourceId
        ),
        candidateKind = ELAPSED_ACTION_APPLY_KIND,
        payloadCodec = SealedElapsedOutcome.CODEC_ID,
        canonicalPayload = outcome.encode(),
        eventType = ELAPSED_ACTION_APPLY_KIND,
        importance = EventImportance.NORMAL,
        subjectId = outcome.domainResultId
    )
}

data class AdvanceTimePayload(
    val goal: TimeAdvanceGoal,
    val mode: ProgressionMode,
    val limits: TimeTraversalLimits,
    val continuation: TimeAdvanceContinuation? = null,
    val resumeOfCommandId: CommandId? = null,
    val interruptPolicy: TimeAdvanceInterruptPolicy = TimeAdvanceInterruptPolicy()
) : WorldCommandPayload {
    init {
        require(continuation == null || resumeOfCommandId == null) { "decision and interrupted continuations are mutually exclusive" }
    }
    override val codecId: String = CODEC_ID

    override fun canonicalJson(): String = "{\"continuation\":${continuation?.canonicalJson() ?: "null"},\"goal\":${CanonicalJson.string(TimeAdvanceGoalCodec.encode(goal))},\"interruptPolicy\":${interruptPolicy.canonicalJson()},\"limits\":{\"maxAdvanceMinute\":${limits.maxAdvanceMinute.value},\"maxBoundaryCount\":${limits.maxBoundaryCount},\"maxCandidatesPerBatch\":${limits.maxCandidatesPerBatch}},\"mode\":${CanonicalJson.string(mode.name)},\"resumeOfCommandId\":${resumeOfCommandId?.let { CanonicalJson.string(it.value) } ?: "null"}}"

    companion object {
        const val CODEC_ID = "advance-time.v1"

        fun decode(canonicalJson: String): Checked<AdvanceTimePayload> = try {
            val goal = TimeAdvanceGoalCodec.decode(stringField(canonicalJson, "goal") ?: return incompatible())
                .valueOrNull() ?: return incompatible()
            val mode = ProgressionMode.valueOf(stringField(canonicalJson, "mode") ?: return incompatible())
            val limitsJson = objectField(canonicalJson, "limits") ?: return incompatible()
            val limits = TimeTraversalLimits(
                GameMinute.of(longField(limitsJson, "maxAdvanceMinute") ?: return incompatible()).valueOrNull() ?: return incompatible(),
                (longField(limitsJson, "maxBoundaryCount") ?: return incompatible()).toInt(),
                (longField(limitsJson, "maxCandidatesPerBatch") ?: return incompatible()).toInt()
            )
            val policy = decodePolicy(objectField(canonicalJson, "interruptPolicy") ?: return incompatible()) ?: return incompatible()
            val continuation = objectField(canonicalJson, "continuation")?.let(::decodeContinuation) ?: if (nullField(canonicalJson, "continuation")) null else return incompatible()
            val resumeOfCommandId = nullableStringField(canonicalJson, "resumeOfCommandId")?.let(::CommandId)
            val payload = AdvanceTimePayload(goal, mode, limits, continuation, resumeOfCommandId, policy)
            if (payload.canonicalJson() == canonicalJson) Checked.Value(payload) else incompatible()
        } catch (_: IllegalArgumentException) {
            incompatible()
        }

        private fun decodePolicy(json: String): TimeAdvanceInterruptPolicy? {
            val favoriteSubjectIds = stringArrayField(json, "favoriteSubjectIds").map {
                EntityId.of(it).valueOrNull() ?: return null
            }.toSet()
            return TimeAdvanceInterruptPolicy(
                importanceThreshold = nullableStringField(json, "importanceThreshold")?.let(EventImportance::valueOf),
                favoriteSubjectIds = favoriteSubjectIds,
                favoriteImportanceBoost = (longField(json, "favoriteImportanceBoost") ?: return null).toInt(),
                forcedStopEventTypes = stringArrayField(json, "forcedStopEventTypes").toSet(),
                explicitStopEventTypes = stringArrayField(json, "explicitStopEventTypes").toSet(),
                explicitIgnoreEventTypes = stringArrayField(json, "explicitIgnoreEventTypes").toSet(),
                summaryOnly = booleanField(json, "summaryOnly") ?: return null
            ).takeIf { it.canonicalJson() == json }
        }

        private fun decodeContinuation(json: String): TimeAdvanceContinuation? {
            val selection = objectField(json, "selection") ?: return null
            return TimeAdvanceContinuation(
                SessionEpoch(longField(json, "predecessorEpoch") ?: return null),
                CommandId(stringField(json, "predecessorCommandId") ?: return null),
                CommandId(stringField(json, "childCommandId") ?: return null),
                PayloadHash(stringField(json, "pendingSuffixHash") ?: return null),
                nullableStringField(json, "sealedOutcomeHash")?.let(::PayloadHash),
                DecisionSelection(
                    stringField(selection, "gateId") ?: return null,
                    stringField(selection, "choiceId") ?: return null,
                    stringField(selection, "codec") ?: return null,
                    stringField(selection, "payload") ?: return null,
                    PayloadHash(stringField(selection, "payloadHash") ?: return null)
                )
            ).takeIf { it.canonicalJson() == json }
        }

        private fun stringField(json: String, name: String): String? = Regex("\\\"$name\\\":(\\\"(?:\\\\.|[^\\\"])*\\\")")
            .find(json)?.groupValues?.get(1)?.let(CanonicalJson::parseString)

        private fun nullableStringField(json: String, name: String): String? =
            stringField(json, name).takeIf { it != null } ?: if (nullField(json, name)) null else null

        private fun nullField(json: String, name: String): Boolean = Regex("\\\"$name\\\":null(?=,|})").containsMatchIn(json)

        private fun longField(json: String, name: String): Long? = Regex("\\\"$name\\\":(-?\\d+)(?=,|})")
            .find(json)?.groupValues?.get(1)?.toLongOrNull()

        private fun booleanField(json: String, name: String): Boolean? = Regex("\\\"$name\\\":(true|false)(?=,|})")
            .find(json)?.groupValues?.get(1)?.toBooleanStrictOrNull()

        private fun stringArrayField(json: String, name: String): List<String> {
            val raw = Regex("\\\"$name\\\":\\[(.*?)](?=,|})").find(json)?.groupValues?.get(1) ?: return emptyList()
            if (raw.isEmpty()) return emptyList()
            return raw.split(',').map { CanonicalJson.parseString(it) ?: throw IllegalArgumentException("invalid string array") }
        }

        private fun objectField(json: String, name: String): String? {
            val start = Regex("\\\"$name\\\":\\{").find(json)?.range?.last ?: return null
            var depth = 1
            var quoted = false
            var escaped = false
            for (index in start + 1 until json.length) {
                val char = json[index]
                if (quoted) {
                    if (escaped) escaped = false else if (char == '\\') escaped = true else if (char == '"') quoted = false
                } else when (char) {
                    '"' -> quoted = true
                    '{' -> depth++
                    '}' -> if (--depth == 0) return json.substring(start, index + 1)
                }
            }
            return null
        }

        private fun incompatible(): Checked<Nothing> = Checked.Rejected(DomainError.ContentCompatibilityError("invalid advance-time payload"))
        private fun <T> Checked<T>.valueOrNull(): T? = (this as? Checked.Value<T>)?.value
    }
}

private fun TimeAdvanceContinuation.canonicalJson(): String = "{\"childCommandId\":${CanonicalJson.string(childCommandId.value)},\"pendingSuffixHash\":${CanonicalJson.string(pendingSuffixHash.value)},\"predecessorCommandId\":${CanonicalJson.string(predecessorCommandId.value)},\"predecessorEpoch\":${predecessorEpoch.value},\"sealedOutcomeHash\":${sealedOutcomeHash?.let { CanonicalJson.string(it.value) } ?: "null"},\"selection\":${selection.canonicalJson()}}"

private fun DecisionSelection.canonicalJson(): String = "{\"choiceId\":${CanonicalJson.string(choiceId)},\"codec\":${CanonicalJson.string(codec)},\"gateId\":${CanonicalJson.string(gateId)},\"payload\":${CanonicalJson.string(canonicalPayload)},\"payloadHash\":${CanonicalJson.string(payloadHash.value)}}"
