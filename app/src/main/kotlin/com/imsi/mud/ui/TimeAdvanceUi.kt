package com.imsi.mud.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.unit.dp
import com.imsi.mud.simulation.AdvanceControl as CoreAdvanceControl
import com.imsi.mud.simulation.AdvanceTimePayload
import com.imsi.mud.simulation.CommandEnvelope
import com.imsi.mud.simulation.CommandId
import com.imsi.mud.simulation.CommandResult
import com.imsi.mud.simulation.ControlRequest
import com.imsi.mud.simulation.ControlRequestResult
import com.imsi.mud.simulation.DecisionSelection
import com.imsi.mud.simulation.DomainError
import com.imsi.mud.simulation.EntityId
import com.imsi.mud.simulation.PayloadHash
import com.imsi.mud.simulation.ProcessWorldSessionCoordinator
import com.imsi.mud.simulation.PublicConsequencePreview as CorePublicConsequencePreview
import com.imsi.mud.simulation.PublicField as CorePublicField
import com.imsi.mud.simulation.PublicValueState as CorePublicValueState
import com.imsi.mud.simulation.ScheduleConflictResult as CoreScheduleConflictResult
import com.imsi.mud.simulation.ProgressionMode
import com.imsi.mud.simulation.SessionRuntimeState
import com.imsi.mud.simulation.SessionLifecycle
import com.imsi.mud.simulation.ScheduleReservePayload
import com.imsi.mud.simulation.ScheduleResolution as CoreScheduleResolution
import com.imsi.mud.simulation.ScheduleResolveConflictPayload
import com.imsi.mud.simulation.StateVersion
import com.imsi.mud.simulation.TimeAdvanceContinuation
import com.imsi.mud.simulation.TimeAdvanceGoal
import com.imsi.mud.simulation.TimeAdvanceInterruptPolicy
import com.imsi.mud.simulation.TimeAdvanceResult
import com.imsi.mud.simulation.TimeAdvanceSummaryView
import com.imsi.mud.simulation.TimeTraversalLimits
import com.imsi.mud.simulation.WorldCommandPayload
import com.imsi.mud.simulation.WorldSession
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class TimeAdvanceStatus {
    IDLE,
    ADVANCING,
    PAUSE_REQUESTED,
    CANCEL_REQUESTED,
    INTERRUPTED,
    DECISION_REQUIRED,
    COMPLETED,
    CANCELLED,
    UNREACHABLE,
    LIMIT_REACHED,
    FAILED,
    ADVANCE_IN_PROGRESS,
    SUMMARY
}

enum class AdvanceControl { PAUSE, CANCEL }

enum class ScheduleResolution { KEEP_EXISTING, PREEMPT, PAUSE_AND_INSERT, CANCEL_AND_INSERT, RESCHEDULE_REQUIRED }

enum class PublicValueStatus { KNOWN, NONE, UNDETERMINED, UNKNOWN, NOT_APPLICABLE }

enum class PublicRiskReason {
    FINANCIAL_LOSS,
    RESOURCE_LOSS,
    PROGRESS_LOSS,
    PUBLIC_RELATIONSHIP_IMPACT,
    PUBLIC_REPUTATION_IMPACT,
    NEAR_FINAL_BOUNDARY,
    RUNNING_ACTION_CANCEL
}

data class PublicConsequenceField(
    val label: String,
    val value: String,
    val status: PublicValueStatus
)

data class PublicConsequencePreview(
    val currentSchedule: PublicConsequenceField,
    val proposedSchedule: PublicConsequenceField,
    val cancelledSchedule: PublicConsequenceField,
    val refundOrLoss: PublicConsequenceField,
    val resourceSettlement: PublicConsequenceField,
    val progressLoss: PublicConsequenceField,
    val relationshipImpact: PublicConsequenceField,
    val reputationImpact: PublicConsequenceField,
    val rescheduleAvailability: PublicConsequenceField,
    val riskReasons: List<PublicRiskReason> = emptyList(),
    val hasLoss: Boolean = false,
    val nearFinalBoundary: Boolean = false,
    val runningActionCancel: Boolean = false
) {
    val fields: List<Pair<String, PublicConsequenceField>>
        get() = listOf(
            "current-schedule" to currentSchedule,
            "proposed-schedule" to proposedSchedule,
            "cancelled-schedule" to cancelledSchedule,
            "refund-or-loss" to refundOrLoss,
            "resource-settlement" to resourceSettlement,
            "progress-loss" to progressLoss,
            "relationship-impact" to relationshipImpact,
            "reputation-impact" to reputationImpact,
            "reschedule-availability" to rescheduleAvailability
        )
}

data class ExpectedScheduleRowVersion(val actionId: String, val rowVersion: Long) {
    init {
        require(actionId.isNotBlank())
        require(rowVersion >= 0)
    }
}

data class ScheduleConflictViewState(
    val conflictId: String,
    val previewToken: String,
    val previewCodec: String,
    val previewHash: String,
    val expectedRowVersions: List<ExpectedScheduleRowVersion>,
    val reservation: ScheduleReservePayload,
    val conflictingSchedules: List<String>,
    val allowedResolutions: List<ScheduleResolution>,
    val preview: PublicConsequencePreview,
    val previewStale: Boolean = false
) {
    init {
        require(conflictId.isNotBlank() && previewToken.isNotBlank() && previewCodec.isNotBlank() && previewHash.isNotBlank())
        PayloadHash(previewHash)
        require(expectedRowVersions.map { it.actionId }.distinct().size == expectedRowVersions.size)
    }

    companion object {
        fun from(
            result: CoreScheduleConflictResult,
            reservation: ScheduleReservePayload,
            conflictId: String,
            previewToken: String = result.consequencePreview.hash.value
        ): ScheduleConflictViewState = ScheduleConflictViewState(
            conflictId = conflictId,
            previewToken = previewToken,
            previewCodec = CorePublicConsequencePreview.CODEC_ID,
            previewHash = result.consequencePreview.hash.value,
            expectedRowVersions = result.conflictingRowVersions.entries
                .sortedBy { it.key.value }
                .map { ExpectedScheduleRowVersion(it.key.value, it.value.value) },
            reservation = reservation,
            conflictingSchedules = result.consequencePreview.currentSchedules
                .map { it.actionKind }
                .ifEmpty { result.conflictingActionIds.map { "Scheduled action" } },
            allowedResolutions = result.allowedResolutions
                .map { ScheduleResolution.valueOf(it.name) }
                .sortedBy { it.ordinal },
            preview = result.consequencePreview.toUiPreview()
        )
    }
}

private fun CorePublicConsequencePreview.toUiPreview(): PublicConsequencePreview {
    val resourceValue = resourceEffects.joinToString("; ") { effect ->
        val settlement = effect.settlement?.name?.replace('_', ' ')?.lowercase()
        "${effect.resource.kind}:${effect.resource.id} x${effect.quantity}: ${effect.state.uiLabel()}" +
            (settlement?.let { " ($it)" } ?: "")
    }.ifBlank { "No impact" }
    val financialFields = listOf(refundAmount, lossAmount)
    val financialStatus = financialFields.map { it.state }.toUiStatus()
    val riskReasons = buildList {
        if (lossAmount.state in setOf(CorePublicValueState.KNOWN, CorePublicValueState.UNKNOWN)) {
            add(PublicRiskReason.FINANCIAL_LOSS)
        }
        if (resourceEffects.any { it.state !in setOf(CorePublicValueState.NONE, CorePublicValueState.NOT_APPLICABLE) }) {
            add(PublicRiskReason.RESOURCE_LOSS)
        }
        if (progressLoss.state in setOf(CorePublicValueState.KNOWN, CorePublicValueState.UNKNOWN)) {
            add(PublicRiskReason.PROGRESS_LOSS)
        }
        if (relationshipImpact.state !in setOf(CorePublicValueState.NONE, CorePublicValueState.NOT_APPLICABLE)) {
            add(PublicRiskReason.PUBLIC_RELATIONSHIP_IMPACT)
        }
        if (reputationImpact.state !in setOf(CorePublicValueState.NONE, CorePublicValueState.NOT_APPLICABLE)) {
            add(PublicRiskReason.PUBLIC_REPUTATION_IMPACT)
        }
    }
    return PublicConsequencePreview(
        currentSchedule = schedulesField("Current schedule", currentSchedules),
        proposedSchedule = schedulesField("New schedule", listOf(proposedSchedule)),
        cancelledSchedule = schedulesField("Cancelled schedule", cancelledSchedules),
        refundOrLoss = PublicConsequenceField("Refund or loss", financialFields.joinToString("; ") { it.uiLabel() }, financialStatus),
        resourceSettlement = PublicConsequenceField("Resources", resourceValue, resourceEffects.map { it.state }.toUiStatus()),
        progressLoss = field("Progress loss", progressLoss),
        relationshipImpact = field("Relationship", relationshipImpact),
        reputationImpact = field("Reputation", reputationImpact),
        rescheduleAvailability = field("Reschedule", rescheduleAvailability),
        riskReasons = riskReasons,
        hasLoss = PublicRiskReason.FINANCIAL_LOSS in riskReasons || PublicRiskReason.RESOURCE_LOSS in riskReasons || PublicRiskReason.PROGRESS_LOSS in riskReasons
    )
}

private fun CorePublicConsequencePreview.schedulesField(
    label: String,
    schedules: List<com.imsi.mud.simulation.PublicScheduleEffect>
): PublicConsequenceField = PublicConsequenceField(
    label,
    schedules.joinToString("; ") { "${it.actionKind}: ${it.state.uiLabel()}" }.ifBlank { "No impact" },
    schedules.map { it.state }.toUiStatus()
)

private fun field(label: String, value: CorePublicField): PublicConsequenceField =
    PublicConsequenceField(label, value.uiLabel(), value.state.toUiStatus())

private fun CorePublicField.uiLabel(): String = value ?: state.uiLabel()

private fun CorePublicValueState.uiLabel(): String = name.replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() }

private fun List<CorePublicValueState>.toUiStatus(): PublicValueStatus = when {
    any { it == CorePublicValueState.UNKNOWN } -> PublicValueStatus.UNKNOWN
    any { it == CorePublicValueState.UNDETERMINED } -> PublicValueStatus.UNDETERMINED
    any { it == CorePublicValueState.KNOWN } -> PublicValueStatus.KNOWN
    any { it == CorePublicValueState.NONE } -> PublicValueStatus.NONE
    else -> PublicValueStatus.NOT_APPLICABLE
}

private fun CorePublicValueState.toUiStatus(): PublicValueStatus = listOf(this).toUiStatus()

data class PublicDecisionChoice(
    val choiceId: String,
    val label: String,
    val codec: String,
    val canonicalPayload: String,
    val payloadHash: String
) {
    init {
        require(choiceId.isNotBlank() && label.isNotBlank() && codec.isNotBlank() && payloadHash.isNotBlank())
    }
}

data class TimeAdvanceRequest(
    val goal: TimeAdvanceGoal,
    val mode: ProgressionMode,
    val limits: TimeTraversalLimits,
    val interruptPolicy: TimeAdvanceInterruptPolicy,
    val actorId: EntityId? = null
)

data class DecisionRequiredViewState(
    val continuationOfCommandId: String,
    val gateId: String,
    val choices: List<PublicDecisionChoice>,
    val request: TimeAdvanceRequest,
    val predecessorEpoch: Long,
    val pendingSuffixHash: String,
    val sealedOutcomeHash: String? = null
) {
    init {
        require(continuationOfCommandId.isNotBlank() && gateId.isNotBlank())
        require(predecessorEpoch >= 0)
        PayloadHash(pendingSuffixHash)
        sealedOutcomeHash?.let(::PayloadHash)
        require(choices.size in 1..8)
        require(choices.map { it.choiceId }.distinct().size == choices.size)
        choices.forEach { choice ->
            DecisionSelection(
                gateId,
                choice.choiceId,
                choice.codec,
                choice.canonicalPayload,
                PayloadHash(choice.payloadHash)
            )
        }
    }
}

data class AdvanceInProgressViewState(
    val goalLabel: String,
    val lastCommittedCursor: String,
    val allowedControls: Set<AdvanceControl>
)

data class TimeAdvanceSummary(
    val projection: TimeAdvanceSummaryView
)

data class TimeAdvanceViewState(
    val status: TimeAdvanceStatus,
    val goalLabel: String = "",
    val continuationOfCommandId: String? = null,
    val resumeRequest: TimeAdvanceRequest? = null,
    val decisionRequired: DecisionRequiredViewState? = null,
    val startRequest: TimeAdvanceRequest? = null,
    val scheduleReservation: ScheduleReservePayload? = null,
    val advanceInProgress: AdvanceInProgressViewState? = null,
    val summary: TimeAdvanceSummary? = null,
    val conflict: ScheduleConflictViewState? = null,
    val publication: TimeAdvanceCommittedSnapshot? = null,
    val errorMessage: String? = null,
    val controlRequest: AdvanceControl? = null,
    val feedbackMessage: String? = null
) {
    init {
        require(decisionRequired == null || status == TimeAdvanceStatus.DECISION_REQUIRED)
        require((status == TimeAdvanceStatus.DECISION_REQUIRED) == (decisionRequired != null))
        require(status != TimeAdvanceStatus.INTERRUPTED || continuationOfCommandId != null && resumeRequest != null)
        require(
            (status != TimeAdvanceStatus.DECISION_REQUIRED && status != TimeAdvanceStatus.INTERRUPTED) ||
                advanceInProgress == null
        )
    }
}

data class TimeAdvanceCommittedSnapshot(
    val stateVersion: Long,
    val clockMinute: Long,
    val eventCount: Int,
    val sourceCommandId: String,
    val timeAdvanceTerminal: TimeAdvanceResult?,
    val decisionGateId: String? = null,
    val decisionChoices: List<PublicDecisionChoice> = emptyList(),
    val predecessorEpoch: Long? = null,
    val pendingSuffixHash: String? = null,
    val sealedOutcomeHash: String? = null,
    val timeAdvanceSummary: TimeAdvanceSummaryView? = null
)

sealed interface TimeAdvanceUiAction {
    data object Pause : TimeAdvanceUiAction
    data object Cancel : TimeAdvanceUiAction
    data class ResumeInterrupted(
        val continuationOfCommandId: String,
        val request: TimeAdvanceRequest
    ) : TimeAdvanceUiAction
    data class StartAdvance(val request: TimeAdvanceRequest) : TimeAdvanceUiAction
    data class ReserveSchedule(val reservation: ScheduleReservePayload) : TimeAdvanceUiAction
    data class Continue(
        val continuationOfCommandId: String,
        val gateId: String,
        val choiceId: String,
        val codec: String,
        val canonicalPayload: String,
        val payloadHash: String,
        val request: TimeAdvanceRequest,
        val predecessorEpoch: Long,
        val pendingSuffixHash: String,
        val sealedOutcomeHash: String?
    ) : TimeAdvanceUiAction
    data class ConfirmConflict(
        val conflictId: String,
        val resolution: ScheduleResolution,
        val expectedRowVersions: List<ExpectedScheduleRowVersion>,
        val reservation: ScheduleReservePayload,
        val previewCodec: String,
        val previewHash: String,
        val previewToken: String
    ) : TimeAdvanceUiAction
    data class RefreshPreview(val conflictId: String) : TimeAdvanceUiAction
}

sealed interface TimeAdvanceDispatchResult {
    data class Command(val result: CommandResult, val kind: TimeAdvanceCommandKind, val commandId: CommandId) : TimeAdvanceDispatchResult
    data class Control(val result: ControlRequestResult) : TimeAdvanceDispatchResult
    data class RefreshRequested(val conflictId: String) : TimeAdvanceDispatchResult
    data class DecisionStale(val continuationOfCommandId: String) : TimeAdvanceDispatchResult
    data object NoActiveAdvance : TimeAdvanceDispatchResult
}

enum class TimeAdvanceCommandKind { TIME_ADVANCE, SCHEDULE_RESERVATION, SCHEDULE_CONFLICT_RESOLUTION }

private fun com.imsi.mud.simulation.CommittedPublication.toTimeAdvanceCommittedSnapshot() =
    TimeAdvanceCommittedSnapshot(
        stateVersion = snapshot.stateVersion.value,
        clockMinute = snapshot.clock.minute.value,
        eventCount = events.size,
        sourceCommandId = sourceCommandId.value,
        timeAdvanceTerminal = timeAdvanceTerminal?.result,
        decisionGateId = timeAdvanceTerminal?.gateId,
        decisionChoices = timeAdvanceTerminal?.choices.orEmpty().map { choice ->
            PublicDecisionChoice(
                choice.choiceId,
                choice.label,
                choice.codec,
                choice.canonicalPayload,
                choice.payloadHash.value
            )
        },
        predecessorEpoch = snapshot.sessionEpoch.value,
        pendingSuffixHash = timeAdvanceTerminal?.pendingSuffixHash?.value,
        sealedOutcomeHash = timeAdvanceTerminal?.sealedOutcomeHash?.value,
        timeAdvanceSummary = timeAdvanceSummary
    )

class TimeAdvanceController internal constructor(
    private val execute: suspend (CommandEnvelope<out WorldCommandPayload>) -> CommandResult,
    private val requestControl: suspend (ControlRequest) -> ControlRequestResult,
    private val runtimeState: () -> SessionRuntimeState,
    private val currentVersion: () -> StateVersion?,
    private val newCommandId: () -> CommandId,
    private val currentPublication: () -> TimeAdvanceCommittedSnapshot? = { null },
    private val refreshSummary: suspend (CommandId) -> Unit = {}
) {
    constructor(
        session: WorldSession,
        newCommandId: () -> CommandId = { CommandId(UUID.randomUUID().toString()) }
    ) : this(
        execute = session::execute,
        requestControl = session::requestControl,
        runtimeState = { session.runtimeState.value },
        currentVersion = { session.publications.value?.snapshot?.stateVersion },
        newCommandId = newCommandId,
        currentPublication = { session.publications.value?.toTimeAdvanceCommittedSnapshot() },
        refreshSummary = { session.refreshTimeAdvanceSummary(it) }
    )

    constructor(
        session: ProcessWorldSessionCoordinator.WorldSessionHandle,
        newCommandId: () -> CommandId = { CommandId(UUID.randomUUID().toString()) }
    ) : this(
        execute = session::execute,
        requestControl = session::requestControl,
        runtimeState = { session.runtimeState.value },
        currentVersion = { session.publications.value?.snapshot?.stateVersion },
        newCommandId = newCommandId,
        currentPublication = { session.publications.value?.toTimeAdvanceCommittedSnapshot() },
        refreshSummary = { session.refreshTimeAdvanceSummary(it) }
    )

    fun currentCommittedSnapshot(): TimeAdvanceCommittedSnapshot? = currentPublication()

    suspend fun refreshCommittedSummary(commandId: CommandId) {
        try {
            refreshSummary(commandId)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // A read projection failure must not turn a committed command into a gameplay retry.
        }
    }

    fun currentRuntimeState(): SessionRuntimeState = runtimeState()

    suspend fun dispatch(
        action: TimeAdvanceUiAction,
        currentState: TimeAdvanceViewState? = null
    ): TimeAdvanceDispatchResult = when (action) {
        TimeAdvanceUiAction.Pause -> control(CoreAdvanceControl.PAUSE)
        TimeAdvanceUiAction.Cancel -> control(CoreAdvanceControl.CANCEL_ADVANCE)
        is TimeAdvanceUiAction.StartAdvance -> executeAdvance(action.request)
        is TimeAdvanceUiAction.ReserveSchedule -> executePayload(
            newCommandId(),
            null,
            action.reservation,
            TimeAdvanceCommandKind.SCHEDULE_RESERVATION
        )
        is TimeAdvanceUiAction.Continue -> if (decisionIsCurrent(action, currentState)) {
            continueDecision(action)
        } else {
            TimeAdvanceDispatchResult.DecisionStale(action.continuationOfCommandId)
        }
        is TimeAdvanceUiAction.ResumeInterrupted -> executeAdvance(
            request = action.request,
            resumeOfCommandId = CommandId(action.continuationOfCommandId)
        )
        is TimeAdvanceUiAction.ConfirmConflict -> confirmConflict(action, currentState?.conflict)
        is TimeAdvanceUiAction.RefreshPreview -> TimeAdvanceDispatchResult.RefreshRequested(action.conflictId)
    }

    private suspend fun executeAdvance(
        request: TimeAdvanceRequest,
        continuation: TimeAdvanceContinuation? = null,
        resumeOfCommandId: CommandId? = null,
        commandId: CommandId = newCommandId()
    ): TimeAdvanceDispatchResult.Command {
        val runtime = runtimeState()
        val payload = AdvanceTimePayload(
            goal = request.goal,
            mode = request.mode,
            limits = request.limits,
            continuation = continuation,
            resumeOfCommandId = resumeOfCommandId,
            interruptPolicy = request.interruptPolicy
        )
        return executePayload(commandId, request.actorId, payload)
    }

    private suspend fun continueDecision(action: TimeAdvanceUiAction.Continue): TimeAdvanceDispatchResult.Command {
        val childCommandId = newCommandId()
        val selection = DecisionSelection(
            action.gateId,
            action.choiceId,
            action.codec,
            action.canonicalPayload,
            PayloadHash(action.payloadHash)
        )
        val continuation = TimeAdvanceContinuation(
            predecessorEpoch = com.imsi.mud.simulation.SessionEpoch(action.predecessorEpoch),
            predecessorCommandId = CommandId(action.continuationOfCommandId),
            childCommandId = childCommandId,
            pendingSuffixHash = PayloadHash(action.pendingSuffixHash),
            sealedOutcomeHash = action.sealedOutcomeHash?.let(::PayloadHash),
            selection = selection
        )
        return executeAdvance(
            request = action.request,
            continuation = continuation,
            commandId = childCommandId
        )
    }

    private fun decisionIsCurrent(
        action: TimeAdvanceUiAction.Continue,
        currentState: TimeAdvanceViewState?
    ): Boolean {
        val required = currentState?.decisionRequired
        if (required != null) {
            return required.continuationOfCommandId == action.continuationOfCommandId &&
                required.gateId == action.gateId &&
                required.predecessorEpoch == action.predecessorEpoch &&
                required.pendingSuffixHash == action.pendingSuffixHash &&
                required.sealedOutcomeHash == action.sealedOutcomeHash &&
                required.choices.any { choice ->
                    choice.choiceId == action.choiceId &&
                        choice.codec == action.codec &&
                        choice.canonicalPayload == action.canonicalPayload &&
                        choice.payloadHash == action.payloadHash
                }
        }
        val publication = currentPublication()
        val gateId = publication?.decisionGateId ?: return currentState?.status != TimeAdvanceStatus.DECISION_REQUIRED
        return publication.timeAdvanceTerminal == TimeAdvanceResult.DECISION_REQUIRED &&
            publication.sourceCommandId == action.continuationOfCommandId &&
            gateId == action.gateId &&
            publication.predecessorEpoch == action.predecessorEpoch &&
            publication.pendingSuffixHash == action.pendingSuffixHash &&
            publication.sealedOutcomeHash == action.sealedOutcomeHash &&
            publication.decisionChoices.any { choice ->
                choice.choiceId == action.choiceId &&
                    choice.codec == action.codec &&
                    choice.canonicalPayload == action.canonicalPayload &&
                    choice.payloadHash == action.payloadHash
            }
    }

    private suspend fun control(kind: CoreAdvanceControl): TimeAdvanceDispatchResult {
        val runtime = runtimeState()
        val active = runtime.activeAdvanceCommandId ?: return TimeAdvanceDispatchResult.NoActiveAdvance
        return TimeAdvanceDispatchResult.Control(requestControl(ControlRequest(runtime.epoch, active, kind, allowCommitDrain = true)))
    }

    private suspend fun confirmConflict(
        action: TimeAdvanceUiAction.ConfirmConflict,
        current: ScheduleConflictViewState?
    ): TimeAdvanceDispatchResult {
        if (current == null || current.previewStale || current.conflictId != action.conflictId ||
            current.previewToken != action.previewToken || current.previewCodec != action.previewCodec ||
            current.previewHash != action.previewHash || current.expectedRowVersions != action.expectedRowVersions ||
            current.reservation != action.reservation
        ) {
            return TimeAdvanceDispatchResult.RefreshRequested(current?.conflictId ?: action.conflictId)
        }
        val rowVersions = action.expectedRowVersions.sortedBy { it.actionId }.associate {
            val actionId = when (val checked = EntityId.of(it.actionId)) {
                is com.imsi.mud.simulation.Checked.Value -> checked.value
                is com.imsi.mud.simulation.Checked.Rejected -> return TimeAdvanceDispatchResult.RefreshRequested(action.conflictId)
            }
            actionId to StateVersion(it.rowVersion)
        }
        val payload = ScheduleResolveConflictPayload(
            reservation = action.reservation,
            selected = CoreScheduleResolution.valueOf(action.resolution.name),
            expectedRowVersions = rowVersions,
            previewCodec = action.previewCodec,
            previewHash = PayloadHash(action.previewHash)
        )
        return executePayload(newCommandId(), null, payload, TimeAdvanceCommandKind.SCHEDULE_CONFLICT_RESOLUTION)
    }

    private suspend fun executePayload(
        commandId: CommandId,
        actorId: EntityId?,
        payload: WorldCommandPayload,
        kind: TimeAdvanceCommandKind = TimeAdvanceCommandKind.TIME_ADVANCE
    ): TimeAdvanceDispatchResult.Command {
        val runtime = runtimeState()
        val envelope = CommandEnvelope.create(commandId, runtime.epoch, currentVersion(), actorId, payload)
        val result = execute(envelope)
        if (kind == TimeAdvanceCommandKind.TIME_ADVANCE && result is CommandResult.Accepted) {
            refreshCommittedSummary(commandId)
        }
        return TimeAdvanceDispatchResult.Command(result, kind, commandId)
    }
}

data class TimeAdvanceEntry(
    val state: TimeAdvanceViewState,
    val controller: TimeAdvanceController,
    val onResult: (TimeAdvanceDispatchResult) -> Unit = {}
)

@Composable
fun TimeAdvanceRoute(entry: TimeAdvanceEntry) {
    val scope = rememberCoroutineScope()
    var renderedState by remember(entry) { mutableStateOf(entry.state) }
    var mutationDispatching by remember { mutableStateOf(false) }
    var controlDispatching by remember { mutableStateOf(false) }
    var controlOutcomeOwned by remember { mutableStateOf(false) }
    TimeAdvanceScreen(
        state = renderedState,
        onAction = { action ->
            val isControl = action is TimeAdvanceUiAction.Pause || action is TimeAdvanceUiAction.Cancel
            val control = when (action) {
                TimeAdvanceUiAction.Pause -> AdvanceControl.PAUSE
                TimeAdvanceUiAction.Cancel -> AdvanceControl.CANCEL
                else -> null
            }
            val activeCommandId = if (isControl) entry.controller.currentRuntimeState().activeAdvanceCommandId else null
            if (action is TimeAdvanceUiAction.StartAdvance ||
                action is TimeAdvanceUiAction.Continue ||
                action is TimeAdvanceUiAction.ResumeInterrupted
            ) {
                controlOutcomeOwned = false
            }
            if (isControl) {
                if (controlDispatching) return@TimeAdvanceScreen
                controlDispatching = true
            } else {
                if (mutationDispatching) return@TimeAdvanceScreen
                mutationDispatching = true
            }
            val stateAtDispatch = renderedState
            renderedState = optimisticState(stateAtDispatch, action)
            scope.launch {
                try {
                    val result = entry.controller.dispatch(action, stateAtDispatch)
                    entry.onResult(result)
                    val acceptedControl = control != null &&
                        result is TimeAdvanceDispatchResult.Control &&
                        result.result is ControlRequestResult.Accepted &&
                        activeCommandId != null
                    if (acceptedControl) controlOutcomeOwned = true
                    if (!(result is TimeAdvanceDispatchResult.Command &&
                            result.kind == TimeAdvanceCommandKind.TIME_ADVANCE &&
                            (controlDispatching || controlOutcomeOwned))) {
                        renderedState = reduceState(renderedState, result, entry.controller.currentCommittedSnapshot())
                    }
                    if (acceptedControl) {
                        val acceptedCommandId = checkNotNull(activeCommandId)
                        val acceptedControlKind = checkNotNull(control)
                        awaitControlCommit(entry.controller, acceptedCommandId, acceptedControlKind, renderedState) { next ->
                            withContext(Dispatchers.Main.immediate) {
                                renderedState = next
                            }
                        }
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Throwable) {
                    renderedState = renderedState.copy(
                        status = TimeAdvanceStatus.FAILED,
                        advanceInProgress = null,
                        controlRequest = null,
                        feedbackMessage = null,
                        errorMessage = if (isControl) {
                            "The control request could not be completed."
                        } else {
                            "The request could not be completed."
                        }
                    )
                } finally {
                    if (isControl) {
                        controlDispatching = false
                    } else {
                        mutationDispatching = false
                    }
                }
            }
        },
        mutationEnabled = !mutationDispatching,
        controlEnabled = !controlDispatching
    )
}

private suspend fun awaitControlCommit(
    controller: TimeAdvanceController,
    commandId: CommandId,
    control: AdvanceControl,
    state: TimeAdvanceViewState,
    onCommitted: suspend (TimeAdvanceViewState) -> Unit
) {
    var processingShown = false
    var inactiveSinceNanos: Long? = null
    val processingDeadlineNanos = System.nanoTime() + CONTROL_COMMIT_TIMEOUT_NANOS
    while (true) {
        var publication = controller.currentCommittedSnapshot()
        val runtime = controller.currentRuntimeState()
        if (publication?.sourceCommandId == commandId.value && publication.timeAdvanceTerminal != null && publication.timeAdvanceSummary == null) {
            controller.refreshCommittedSummary(commandId)
            publication = controller.currentCommittedSnapshot()
        }
        val terminal = publication?.timeAdvanceTerminal
        val summary = publication?.timeAdvanceSummary
        if (publication?.sourceCommandId == commandId.value && terminal != null && summary != null && summary.terminalReason == terminal) {
            onCommitted(
                state.copy(
                    status = terminal.uiStatus(),
                    advanceInProgress = null,
                    controlRequest = null,
                    decisionRequired = null,
                    summary = TimeAdvanceSummary(summary),
                    publication = publication,
                    continuationOfCommandId = if (terminal == TimeAdvanceResult.INTERRUPTED) commandId.value else null,
                    resumeRequest = if (terminal == TimeAdvanceResult.INTERRUPTED) state.startRequest else null,
                    feedbackMessage = when (control) {
                        AdvanceControl.PAUSE -> "Pause applied at the next committed safe time boundary."
                        AdvanceControl.CANCEL -> "Cancellation applied at the next committed safe time boundary."
                    }
                )
            )
            return
        }
        if (publication?.sourceCommandId == commandId.value && terminal != null) {
            onCommitted(
                state.copy(
                    status = TimeAdvanceStatus.FAILED,
                    advanceInProgress = null,
                    controlRequest = null,
                    summary = null,
                    publication = publication,
                    startRequest = null,
                    errorMessage = "A committed time advance summary was not available."
                )
            )
            return
        }
        val stillRunning = runtime.activeAdvanceCommandId != null &&
            runtime.lifecycle != SessionLifecycle.CLOSING && runtime.lifecycle != SessionLifecycle.CLOSED
        if (!stillRunning) {
            val now = System.nanoTime()
            val firstInactive = inactiveSinceNanos ?: now.also { inactiveSinceNanos = it }
            if (now - firstInactive >= CONTROL_PUBLICATION_GRACE_NANOS) {
                onCommitted(controlCommitFailureState(state))
                return
            }
        } else {
            inactiveSinceNanos = null
            if (!processingShown && System.nanoTime() >= processingDeadlineNanos) {
                onCommitted(controlCommitProcessingState(state))
                processingShown = true
            }
        }
        withContext(Dispatchers.Default) { delay(10) }
    }
}

private fun controlCommitProcessingState(state: TimeAdvanceViewState): TimeAdvanceViewState = state.copy(
    feedbackMessage = "Processing. Waiting for the next committed safe time boundary.",
    errorMessage = null
)

private fun controlCommitFailureState(state: TimeAdvanceViewState): TimeAdvanceViewState = state.copy(
    status = TimeAdvanceStatus.FAILED,
    advanceInProgress = null,
    controlRequest = null,
    summary = null,
    feedbackMessage = null,
    errorMessage = "The control request was accepted, but its commit could not be confirmed. Retry the time advance."
)

private const val CONTROL_COMMIT_TIMEOUT_NANOS = 5_000_000_000L
private const val CONTROL_PUBLICATION_GRACE_NANOS = 1_000_000_000L

private fun optimisticState(state: TimeAdvanceViewState, action: TimeAdvanceUiAction): TimeAdvanceViewState = when (action) {
    is TimeAdvanceUiAction.StartAdvance,
    is TimeAdvanceUiAction.Continue,
    is TimeAdvanceUiAction.ResumeInterrupted -> state.copy(
        status = TimeAdvanceStatus.ADVANCE_IN_PROGRESS,
        goalLabel = "Time advance",
        advanceInProgress = AdvanceInProgressViewState(
            goalLabel = "Time advance",
            lastCommittedCursor = "Awaiting commit",
            allowedControls = setOf(AdvanceControl.PAUSE, AdvanceControl.CANCEL)
        ),
        decisionRequired = null,
        summary = null,
        errorMessage = null,
        controlRequest = null,
        feedbackMessage = null
    )
    is TimeAdvanceUiAction.ReserveSchedule -> state.copy(errorMessage = null)
    TimeAdvanceUiAction.Pause -> state.copy(
        status = TimeAdvanceStatus.PAUSE_REQUESTED,
        controlRequest = AdvanceControl.PAUSE,
        feedbackMessage = "Pause request accepted. It will take effect at the next committed safe time boundary."
    )
    TimeAdvanceUiAction.Cancel -> state.copy(
        status = TimeAdvanceStatus.CANCEL_REQUESTED,
        controlRequest = AdvanceControl.CANCEL,
        feedbackMessage = "Cancellation request accepted. It will take effect at the next committed safe time boundary."
    )
    is TimeAdvanceUiAction.ConfirmConflict,
    is TimeAdvanceUiAction.RefreshPreview -> state
}

private fun reduceState(
    state: TimeAdvanceViewState,
    result: TimeAdvanceDispatchResult,
    publication: TimeAdvanceCommittedSnapshot?
): TimeAdvanceViewState = when (result) {
    is TimeAdvanceDispatchResult.Command -> when (result.result) {
        is CommandResult.Accepted -> if (result.kind == TimeAdvanceCommandKind.TIME_ADVANCE) {
            val terminalSnapshot = publication.timeAdvanceTerminalSnapshotFor(result.commandId)
            val terminal = terminalSnapshot?.timeAdvanceTerminal
            val summary = terminalSnapshot?.timeAdvanceSummary
            if (terminal == null || summary == null || summary.terminalReason != terminal) {
                state.copy(
                    status = TimeAdvanceStatus.FAILED,
                    advanceInProgress = null,
                    summary = null,
                    startRequest = null,
                    errorMessage = "A committed time advance summary was not available."
                )
            } else {
                val decisionRequired = if (terminal == TimeAdvanceResult.DECISION_REQUIRED) {
                    state.startRequest?.let { request -> terminalSnapshot.toDecisionRequired(request) }
                } else {
                    null
                }
                if (terminal == TimeAdvanceResult.DECISION_REQUIRED && decisionRequired == null) {
                    return@reduceState state.copy(
                        status = TimeAdvanceStatus.FAILED,
                        advanceInProgress = null,
                        decisionRequired = null,
                        summary = null,
                        startRequest = null,
                        errorMessage = "A committed decision gate was missing its public choices."
                    )
                }
                state.copy(
                    status = terminal.uiStatus(),
                    advanceInProgress = null,
                    summary = TimeAdvanceSummary(summary),
                    publication = publication,
                    continuationOfCommandId = if (terminal == TimeAdvanceResult.INTERRUPTED) result.commandId.value else null,
                    resumeRequest = if (terminal == TimeAdvanceResult.INTERRUPTED) state.startRequest else null,
                    decisionRequired = decisionRequired,
                    errorMessage = null
                )
            }
        } else {
            state.copy(
                status = TimeAdvanceStatus.IDLE,
                scheduleReservation = null,
                conflict = null,
                publication = publication,
                errorMessage = null
            )
        }
        is CommandResult.Rejected -> when (result.result.error) {
            is DomainError.ScheduleConflict -> state.copy(
                status = TimeAdvanceStatus.IDLE,
                advanceInProgress = null,
                errorMessage = "Schedule conflict. Review the public impact before confirming."
            )
            is DomainError.StaleConsequencePreview -> state.copy(
                status = TimeAdvanceStatus.IDLE,
                advanceInProgress = null,
                conflict = state.conflict?.copy(previewStale = true),
                errorMessage = "Schedule preview is stale. Refresh before confirming."
            )
            else -> if (result.kind == TimeAdvanceCommandKind.TIME_ADVANCE &&
                state.status == TimeAdvanceStatus.FAILED &&
                state.errorMessage != null
            ) {
                state
            } else {
                state.copy(
                    status = if (result.kind == TimeAdvanceCommandKind.TIME_ADVANCE) TimeAdvanceStatus.FAILED else TimeAdvanceStatus.IDLE,
                    advanceInProgress = null,
                    summary = null,
                    errorMessage = if (result.kind == TimeAdvanceCommandKind.TIME_ADVANCE) {
                        "Time advance could not be completed."
                    } else {
                        "Schedule change could not be completed."
                    }
                )
            }
        }
    }
    is TimeAdvanceDispatchResult.Control -> when (result.result) {
        is ControlRequestResult.Accepted -> state
        is ControlRequestResult.Rejected -> state.copy(
            status = TimeAdvanceStatus.FAILED,
            advanceInProgress = null,
            controlRequest = null,
            feedbackMessage = null,
            errorMessage = "The control request could not be completed."
        )
    }
        is TimeAdvanceDispatchResult.RefreshRequested -> state.copy(
        conflict = state.conflict?.let { conflict ->
            if (conflict.conflictId == result.conflictId) conflict.copy(previewStale = true) else conflict
        },
        errorMessage = "Schedule preview is stale. Refresh before confirming."
    )
    is TimeAdvanceDispatchResult.DecisionStale -> state.copy(
        status = TimeAdvanceStatus.FAILED,
        decisionRequired = null,
        advanceInProgress = null,
        summary = null,
        errorMessage = "Decision is stale. Refresh before choosing."
    )
    TimeAdvanceDispatchResult.NoActiveAdvance -> state.copy(
        status = TimeAdvanceStatus.FAILED,
        advanceInProgress = null,
        controlRequest = null,
        errorMessage = "No active time advance is available."
    )
}

internal fun TimeAdvanceCommittedSnapshot?.timeAdvanceTerminalSnapshotFor(commandId: CommandId): TimeAdvanceCommittedSnapshot? =
    takeIf { it?.sourceCommandId == commandId.value && it.timeAdvanceTerminal != null }

internal fun TimeAdvanceCommittedSnapshot?.timeAdvanceTerminalFor(commandId: CommandId): TimeAdvanceResult? =
    timeAdvanceTerminalSnapshotFor(commandId)?.timeAdvanceTerminal

private fun TimeAdvanceCommittedSnapshot.toDecisionRequired(request: TimeAdvanceRequest): DecisionRequiredViewState? {
    val gateId = decisionGateId ?: return null
    val predecessorEpoch = predecessorEpoch ?: return null
    val pendingSuffixHash = pendingSuffixHash ?: return null
    if (timeAdvanceTerminal != TimeAdvanceResult.DECISION_REQUIRED || decisionChoices.isEmpty()) return null
    return DecisionRequiredViewState(
        continuationOfCommandId = sourceCommandId,
        gateId = gateId,
        choices = decisionChoices,
        request = request,
        predecessorEpoch = predecessorEpoch,
        pendingSuffixHash = pendingSuffixHash,
        sealedOutcomeHash = sealedOutcomeHash
    )
}

internal fun TimeAdvanceResult.uiStatus(): TimeAdvanceStatus = when (this) {
    TimeAdvanceResult.COMPLETED -> TimeAdvanceStatus.COMPLETED
    TimeAdvanceResult.INTERRUPTED -> TimeAdvanceStatus.INTERRUPTED
    TimeAdvanceResult.DECISION_REQUIRED -> TimeAdvanceStatus.DECISION_REQUIRED
    TimeAdvanceResult.CANCELLED -> TimeAdvanceStatus.CANCELLED
    TimeAdvanceResult.UNREACHABLE -> TimeAdvanceStatus.UNREACHABLE
    TimeAdvanceResult.LIMIT_REACHED -> TimeAdvanceStatus.LIMIT_REACHED
    TimeAdvanceResult.FAILED -> TimeAdvanceStatus.FAILED
}

@Composable
fun TimeAdvanceScreen(
    state: TimeAdvanceViewState,
    onAction: (TimeAdvanceUiAction) -> Unit,
    mutationEnabled: Boolean = true,
    controlEnabled: Boolean = true
) {
    var armedRiskResolution by remember(
        state.conflict?.conflictId,
        state.conflict?.previewToken,
        state.conflict?.previewHash,
        state.conflict?.expectedRowVersions,
        state.conflict?.allowedResolutions
    ) { mutableStateOf<ScheduleResolution?>(null) }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
            .testTag("phase2-time-screen"),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Time advance",
            modifier = textModifier("phase2-time-title", "Time advance", 0f),
            style = MaterialTheme.typography.headlineSmall
        )
        Text(
            text = statusLabel(state.status),
            modifier = textModifier("phase2-time-status-${state.status.name.lowercase()}", statusLabel(state.status), 1f)
        )
        if (state.goalLabel.isNotBlank()) {
            Text(state.goalLabel, modifier = textModifier("phase2-time-goal", state.goalLabel, 2f))
        }
        state.feedbackMessage?.let { message ->
            Text(message, modifier = textModifier("phase2-time-control-feedback", message, 2.5f))
        }

        if (state.startRequest != null &&
            (state.status == TimeAdvanceStatus.IDLE || state.advanceInProgress != null)
        ) {
            val request = state.startRequest
            Phase2Button(
                tag = "phase2-time-start",
                label = "Start time advance",
                description = "Start time advance",
                index = 3f,
                enabled = mutationEnabled && state.status == TimeAdvanceStatus.IDLE,
                onClick = { onAction(TimeAdvanceUiAction.StartAdvance(request)) }
            )
        }

        if (state.status == TimeAdvanceStatus.FAILED && state.startRequest != null) {
            Phase2Button(
                tag = "phase2-time-retry",
                label = "Retry",
                description = "Retry time advance",
                index = 4f,
                enabled = mutationEnabled,
                onClick = { onAction(TimeAdvanceUiAction.StartAdvance(state.startRequest)) }
            )
        }

        state.scheduleReservation?.let { reservation ->
            if (state.status == TimeAdvanceStatus.IDLE) {
                Phase2Button(
                    tag = "phase2-time-reserve",
                    label = "Reserve schedule",
                    description = "Reserve the proposed schedule",
                    index = 4f,
                    enabled = mutationEnabled,
                    onClick = { onAction(TimeAdvanceUiAction.ReserveSchedule(reservation)) }
                )
            }
        }

        state.advanceInProgress?.let { progress ->
            AdvanceInProgressPanel(progress, state.controlRequest, onAction, controlEnabled)
        }

        if (state.status == TimeAdvanceStatus.INTERRUPTED) {
            state.continuationOfCommandId?.let { commandId ->
                state.resumeRequest?.let { request ->
                    Phase2Button(
                        tag = "phase2-time-continue",
                        label = "Continue",
                        description = "Continue time advance",
                        index = 10f,
                        enabled = mutationEnabled,
                        onClick = { onAction(TimeAdvanceUiAction.ResumeInterrupted(commandId, request)) }
                    )
                }
            }
        }

        state.decisionRequired?.let { decision ->
            DecisionPanel(decision, onAction, mutationEnabled)
        }

        state.summary?.let { summary ->
            SummaryPanel(summary)
        }

        state.publication?.let { publication ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("phase2-time-publication")
                    .semantics { contentDescription = "Committed snapshot" },
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text("Committed snapshot", style = MaterialTheme.typography.titleMedium)
                Text("State version: ${publication.stateVersion}")
                Text("Clock minute: ${publication.clockMinute}")
                Text("Events committed: ${publication.eventCount}")
            }
        }

        state.errorMessage?.let { message ->
            Text(message, modifier = textModifier("phase2-time-error", message, 80f))
        }

        state.conflict?.let { conflict ->
            ConflictPanel(
                conflict = conflict,
                armedRiskResolution = armedRiskResolution,
                mutationEnabled = mutationEnabled,
                onArmRisk = { armedRiskResolution = it },
                onRefresh = { onAction(TimeAdvanceUiAction.RefreshPreview(it)) }
            ) { resolution ->
                armedRiskResolution = null
                onAction(
                    TimeAdvanceUiAction.ConfirmConflict(
                        conflictId = conflict.conflictId,
                        resolution = resolution,
                        expectedRowVersions = conflict.expectedRowVersions,
                        reservation = conflict.reservation,
                        previewCodec = conflict.previewCodec,
                        previewHash = conflict.previewHash,
                        previewToken = conflict.previewToken
                    )
                )
            }
        }
    }
}

@Composable
private fun DecisionPanel(
    decision: DecisionRequiredViewState,
    onAction: (TimeAdvanceUiAction) -> Unit,
    interactionEnabled: Boolean
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Decision required", style = MaterialTheme.typography.titleMedium)
        decision.choices.forEachIndexed { index, choice ->
            Phase2Button(
                tag = "phase2-time-choice-${choice.choiceId}",
                label = choice.label,
                description = "Choose ${choice.label}",
                index = 10f + index,
                enabled = interactionEnabled,
                onClick = {
                    onAction(
                        TimeAdvanceUiAction.Continue(
                            continuationOfCommandId = decision.continuationOfCommandId,
                            gateId = decision.gateId,
                            choiceId = choice.choiceId,
                            codec = choice.codec,
                            canonicalPayload = choice.canonicalPayload,
                            payloadHash = choice.payloadHash,
                            request = decision.request,
                            predecessorEpoch = decision.predecessorEpoch,
                            pendingSuffixHash = decision.pendingSuffixHash,
                            sealedOutcomeHash = decision.sealedOutcomeHash
                        )
                    )
                }
            )
        }
    }
}

@Composable
private fun AdvanceInProgressPanel(
    progress: AdvanceInProgressViewState,
    controlRequest: AdvanceControl?,
    onAction: (TimeAdvanceUiAction) -> Unit,
    interactionEnabled: Boolean
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("phase2-time-progress")
            .semantics { contentDescription = "Advance in progress" },
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("Advance in progress")
        Text("Goal: ${progress.goalLabel}")
        Text("Last committed cursor: ${progress.lastCommittedCursor}")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (AdvanceControl.PAUSE in progress.allowedControls) {
                Phase2Button(
                    tag = "phase2-time-control-pause",
                    label = if (controlRequest == AdvanceControl.PAUSE) "Pause requested" else "Pause",
                    description = if (controlRequest == AdvanceControl.PAUSE) {
                        "Pause request accepted; it will apply at the next committed safe boundary"
                    } else {
                        "Request pause at the next safe boundary"
                    },
                    index = 20f,
                    enabled = interactionEnabled,
                    onClick = { onAction(TimeAdvanceUiAction.Pause) }
                )
            }
            if (AdvanceControl.CANCEL in progress.allowedControls) {
                Phase2Button(
                    tag = "phase2-time-control-cancel",
                    label = if (controlRequest == AdvanceControl.CANCEL) "Cancel requested" else "Cancel",
                    description = if (controlRequest == AdvanceControl.CANCEL) {
                        "Cancellation request accepted; it will apply at the next committed safe boundary"
                    } else {
                        "Request cancellation at the next safe boundary"
                    },
                    index = 21f,
                    enabled = interactionEnabled,
                    onClick = { onAction(TimeAdvanceUiAction.Cancel) }
                )
            }
        }
    }
}

@Composable
private fun SummaryPanel(summary: TimeAdvanceSummary) {
    val projection = summary.projection
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("phase2-time-summary")
            .semantics { contentDescription = "Time advance summary" },
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text("Summary", style = MaterialTheme.typography.titleMedium)
        Text("Elapsed: ${projection.elapsedMinutes} minutes")
        Text("Stopped: ${projection.terminalReason.displayLabel()}")
        SummaryItems(
            "Major events",
            "major-events",
            projection.majorEvents.map { "${it.type} at minute ${it.gameMinute.value}" },
            projection.majorEventsOverflowCount
        )
        SummaryItems(
            "Completed",
            "completed",
            projection.completedWork.map { "${it.actionKind} at minute ${it.completedMinute.value}" },
            projection.completedWorkOverflowCount
        )
        SummaryItems(
            "Resource warnings",
            "resource-warnings",
            projection.resourceWarnings.map { "${it.resourceKind} (${it.availableQuantity} available)" },
            projection.resourceWarningsOverflowCount
        )
        SummaryItems(
            "Important changes",
            "important-changes",
            projection.importantChanges.map { "${it.type} at minute ${it.gameMinute.value}" },
            projection.importantChangesOverflowCount
        )
        SummaryItems(
            "Low importance bundles",
            "low-importance-bundles",
            projection.lowImportanceBundles.map { "${it.type}: ${it.count}" },
            projection.lowImportanceBundlesOverflowCount
        )
        Text("Unknown important items: ${projection.unknownImportantEventCount}")
        Text("Continuation: ${projection.continuation?.displayLabel() ?: "None"}")
        Text("Next: ${projection.nextAction.displayLabel()}")
    }
}

@Composable
private fun SummaryItems(label: String, tag: String, items: List<String>, overflowCount: Int) {
    items.forEachIndexed { index, item ->
        Text("$label: $item", modifier = Modifier.testTag("phase2-time-summary-$tag-$index"))
    }
    if (overflowCount > 0) {
        Text("$label: $overflowCount more", modifier = Modifier.testTag("phase2-time-summary-$tag-overflow"))
    }
}

@Composable
private fun ConflictPanel(
    conflict: ScheduleConflictViewState,
    armedRiskResolution: ScheduleResolution?,
    mutationEnabled: Boolean,
    onArmRisk: (ScheduleResolution) -> Unit,
    onRefresh: (String) -> Unit,
    onConfirm: (ScheduleResolution) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("phase2-time-conflict")
            .semantics { contentDescription = "Schedule conflict" },
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("Schedule conflict", style = MaterialTheme.typography.titleMedium)
        conflict.conflictingSchedules.forEachIndexed { index, _ ->
            val description = "Conflicting scheduled action ${index + 1}"
            Text(description, modifier = textModifier("phase2-time-conflict-$index", description, 30f + index))
        }
        conflict.preview.fields.forEachIndexed { index, (key, field) ->
            Text(
                text = "${field.label}: ${field.value} (${field.status.label()})",
                modifier = textModifier("phase2-time-preview-$key", "${field.label}: ${field.value} (${field.status.label()})", 40f + index)
            )
        }
        conflict.preview.riskReasons.distinct().sortedBy { it.ordinal }.forEach { reason -> Text(reason.label()) }
        if (conflict.previewStale) {
            Text("Preview is stale. Reconfirm before submitting.", modifier = textModifier("phase2-time-preview-stale", "Preview is stale. Reconfirm before submitting.", 50f))
            Phase2Button(
                tag = "phase2-time-refresh-preview",
                label = "Refresh preview",
                description = "Refresh schedule impact preview",
                index = 51f,
                enabled = mutationEnabled,
                onClick = { onRefresh(conflict.conflictId) }
            )
        }
        conflict.allowedResolutions.forEachIndexed { index, resolution ->
            val risky = resolution == ScheduleResolution.PREEMPT ||
                resolution == ScheduleResolution.CANCEL_AND_INSERT ||
                conflict.preview.hasLoss ||
                conflict.preview.nearFinalBoundary ||
                conflict.preview.runningActionCancel ||
                conflict.preview.riskReasons.isNotEmpty()
            val armed = armedRiskResolution == resolution
            val label = when {
                risky && !armed -> "Review ${resolution.label()}"
                risky -> "Confirm ${resolution.label()}"
                else -> resolution.label()
            }
            Phase2Button(
                tag = "phase2-time-resolution-${resolution.name.lowercase()}",
                label = label,
                description = if (risky) "Risk confirmation for ${resolution.label()}" else resolution.label(),
                index = 60f + index,
                enabled = mutationEnabled && !conflict.previewStale,
                onClick = {
                    if (risky && !armed) onArmRisk(resolution) else onConfirm(resolution)
                }
            )
        }
    }
}

private fun PublicValueStatus.label(): String = when (this) {
    PublicValueStatus.KNOWN -> "Confirmed"
    PublicValueStatus.NONE -> "No impact"
    PublicValueStatus.UNDETERMINED -> "Not determined"
    PublicValueStatus.UNKNOWN -> "Unknown"
    PublicValueStatus.NOT_APPLICABLE -> "Not applicable"
}

private fun PublicRiskReason.label(): String = when (this) {
    PublicRiskReason.FINANCIAL_LOSS -> "Financial loss"
    PublicRiskReason.RESOURCE_LOSS -> "Resource loss"
    PublicRiskReason.PROGRESS_LOSS -> "Progress loss"
    PublicRiskReason.PUBLIC_RELATIONSHIP_IMPACT -> "Relationship impact"
    PublicRiskReason.PUBLIC_REPUTATION_IMPACT -> "Reputation impact"
    PublicRiskReason.NEAR_FINAL_BOUNDARY -> "Near completion"
    PublicRiskReason.RUNNING_ACTION_CANCEL -> "Cancels an activity in progress"
}

private fun ScheduleResolution.label(): String = name.replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() }

private fun statusLabel(status: TimeAdvanceStatus): String = status.name.replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() }

private fun TimeAdvanceResult.displayLabel(): String = name.replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() }

private fun Enum<*>.displayLabel(): String = name.replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() }

private fun textModifier(tag: String, description: String, index: Float): Modifier = Modifier
    .testTag(tag)
    .semantics {
        contentDescription = description
        traversalIndex = index
    }

@Composable
private fun Phase2Button(
    tag: String,
    label: String,
    description: String,
    index: Float,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
            .testTag(tag)
            .semantics {
                contentDescription = description
                traversalIndex = index
            }
    ) {
        Text(label)
    }
}
