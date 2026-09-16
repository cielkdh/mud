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
import com.imsi.mud.simulation.EntityId
import com.imsi.mud.simulation.PayloadHash
import com.imsi.mud.simulation.ProgressionMode
import com.imsi.mud.simulation.SessionRuntimeState
import com.imsi.mud.simulation.ScheduleReservePayload
import com.imsi.mud.simulation.ScheduleResolution as CoreScheduleResolution
import com.imsi.mud.simulation.ScheduleResolveConflictPayload
import com.imsi.mud.simulation.StateVersion
import com.imsi.mud.simulation.TimeAdvanceContinuation
import com.imsi.mud.simulation.TimeAdvanceGoal
import com.imsi.mud.simulation.TimeAdvanceInterruptPolicy
import com.imsi.mud.simulation.TimeTraversalLimits
import com.imsi.mud.simulation.WorldCommandPayload
import com.imsi.mud.simulation.WorldSession
import java.util.UUID
import kotlinx.coroutines.launch

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
}

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

data class Phase2AdvanceRequest(
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
    val request: Phase2AdvanceRequest,
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
    val elapsed: String,
    val stopReason: String,
    val majorEvents: List<String>,
    val completedWork: List<String>,
    val resourceWarnings: List<String>,
    val importantChanges: List<String>,
    val bundleCount: Int,
    val unknownImportantCount: Int,
    val nextActionLabel: String?
)

data class Phase2TimeViewState(
    val status: TimeAdvanceStatus,
    val goalLabel: String = "",
    val continuationOfCommandId: String? = null,
    val resumeRequest: Phase2AdvanceRequest? = null,
    val decisionRequired: DecisionRequiredViewState? = null,
    val startRequest: Phase2AdvanceRequest? = null,
    val advanceInProgress: AdvanceInProgressViewState? = null,
    val summary: TimeAdvanceSummary? = null,
    val conflict: ScheduleConflictViewState? = null
) {
    init {
        require((status == TimeAdvanceStatus.DECISION_REQUIRED) == (decisionRequired != null))
        require(status != TimeAdvanceStatus.INTERRUPTED || continuationOfCommandId != null && resumeRequest != null)
        require(
            (status != TimeAdvanceStatus.DECISION_REQUIRED && status != TimeAdvanceStatus.INTERRUPTED) ||
                advanceInProgress == null
        )
    }
}

sealed interface Phase2TimeUiAction {
    data object Pause : Phase2TimeUiAction
    data object Cancel : Phase2TimeUiAction
    data class ResumeInterrupted(
        val continuationOfCommandId: String,
        val request: Phase2AdvanceRequest
    ) : Phase2TimeUiAction
    data class StartAdvance(val request: Phase2AdvanceRequest) : Phase2TimeUiAction
    data class Continue(
        val continuationOfCommandId: String,
        val gateId: String,
        val choiceId: String,
        val codec: String,
        val canonicalPayload: String,
        val payloadHash: String,
        val request: Phase2AdvanceRequest,
        val predecessorEpoch: Long,
        val pendingSuffixHash: String,
        val sealedOutcomeHash: String?
    ) : Phase2TimeUiAction
    data class ConfirmConflict(
        val conflictId: String,
        val resolution: ScheduleResolution,
        val expectedRowVersions: List<ExpectedScheduleRowVersion>,
        val reservation: ScheduleReservePayload,
        val previewCodec: String,
        val previewHash: String,
        val previewToken: String
    ) : Phase2TimeUiAction
    data class RefreshPreview(val conflictId: String) : Phase2TimeUiAction
}

sealed interface Phase2DispatchResult {
    data class Command(val result: CommandResult) : Phase2DispatchResult
    data class Control(val result: ControlRequestResult) : Phase2DispatchResult
    data class RefreshRequested(val conflictId: String) : Phase2DispatchResult
    data object NoActiveAdvance : Phase2DispatchResult
}

class Phase2TimeController internal constructor(
    private val execute: suspend (CommandEnvelope<out WorldCommandPayload>) -> CommandResult,
    private val requestControl: suspend (ControlRequest) -> ControlRequestResult,
    private val runtimeState: () -> SessionRuntimeState,
    private val currentVersion: () -> StateVersion?,
    private val newCommandId: () -> CommandId
) {
    constructor(
        session: WorldSession,
        newCommandId: () -> CommandId = { CommandId(UUID.randomUUID().toString()) }
    ) : this(
        execute = session::execute,
        requestControl = session::requestControl,
        runtimeState = { session.runtimeState.value },
        currentVersion = { session.publications.value?.snapshot?.stateVersion },
        newCommandId = newCommandId
    )

    suspend fun dispatch(
        action: Phase2TimeUiAction,
        currentState: Phase2TimeViewState? = null
    ): Phase2DispatchResult = when (action) {
        Phase2TimeUiAction.Pause -> control(CoreAdvanceControl.PAUSE)
        Phase2TimeUiAction.Cancel -> control(CoreAdvanceControl.CANCEL_ADVANCE)
        is Phase2TimeUiAction.StartAdvance -> executeAdvance(action.request)
        is Phase2TimeUiAction.Continue -> continueDecision(action)
        is Phase2TimeUiAction.ResumeInterrupted -> executeAdvance(
            request = action.request,
            resumeOfCommandId = CommandId(action.continuationOfCommandId)
        )
        is Phase2TimeUiAction.ConfirmConflict -> confirmConflict(action, currentState?.conflict)
        is Phase2TimeUiAction.RefreshPreview -> Phase2DispatchResult.RefreshRequested(action.conflictId)
    }

    private suspend fun executeAdvance(
        request: Phase2AdvanceRequest,
        continuation: TimeAdvanceContinuation? = null,
        resumeOfCommandId: CommandId? = null,
        commandId: CommandId = newCommandId()
    ): Phase2DispatchResult.Command {
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

    private suspend fun continueDecision(action: Phase2TimeUiAction.Continue): Phase2DispatchResult.Command {
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

    private suspend fun control(kind: CoreAdvanceControl): Phase2DispatchResult {
        val runtime = runtimeState()
        val active = runtime.activeAdvanceCommandId ?: return Phase2DispatchResult.NoActiveAdvance
        return Phase2DispatchResult.Control(requestControl(ControlRequest(runtime.epoch, active, kind, allowCommitDrain = true)))
    }

    private suspend fun confirmConflict(
        action: Phase2TimeUiAction.ConfirmConflict,
        current: ScheduleConflictViewState?
    ): Phase2DispatchResult {
        if (current == null || current.previewStale || current.conflictId != action.conflictId ||
            current.previewToken != action.previewToken || current.previewCodec != action.previewCodec ||
            current.previewHash != action.previewHash || current.expectedRowVersions != action.expectedRowVersions ||
            current.reservation != action.reservation
        ) {
            return Phase2DispatchResult.RefreshRequested(current?.conflictId ?: action.conflictId)
        }
        val rowVersions = action.expectedRowVersions.sortedBy { it.actionId }.associate {
            val actionId = when (val checked = EntityId.of(it.actionId)) {
                is com.imsi.mud.simulation.Checked.Value -> checked.value
                is com.imsi.mud.simulation.Checked.Rejected -> return Phase2DispatchResult.RefreshRequested(action.conflictId)
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
        return executePayload(newCommandId(), null, payload)
    }

    private suspend fun executePayload(
        commandId: CommandId,
        actorId: EntityId?,
        payload: WorldCommandPayload
    ): Phase2DispatchResult.Command {
        val runtime = runtimeState()
        val envelope = CommandEnvelope.create(commandId, runtime.epoch, currentVersion(), actorId, payload)
        return Phase2DispatchResult.Command(execute(envelope))
    }
}

data class Phase2TimeEntry(
    val state: Phase2TimeViewState,
    val controller: Phase2TimeController,
    val onResult: (Phase2DispatchResult) -> Unit = {}
)

@Composable
fun Phase2TimeRoute(entry: Phase2TimeEntry) {
    val scope = rememberCoroutineScope()
    var dispatching by remember { mutableStateOf(false) }
    Phase2TimeScreen(
        state = entry.state,
        onAction = { action ->
            if (dispatching) return@Phase2TimeScreen
            dispatching = true
            scope.launch {
                try {
                    entry.onResult(entry.controller.dispatch(action, entry.state))
                } finally {
                    dispatching = false
                }
            }
        },
        interactionEnabled = !dispatching
    )
}

@Composable
fun Phase2TimeScreen(
    state: Phase2TimeViewState,
    onAction: (Phase2TimeUiAction) -> Unit,
    interactionEnabled: Boolean = true
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

        if (state.status == TimeAdvanceStatus.IDLE) {
            state.startRequest?.let { request ->
                Phase2Button(
                    tag = "phase2-time-start",
                    label = "Start time advance",
                    description = "Start time advance",
                    index = 3f,
                    enabled = interactionEnabled,
                    onClick = { onAction(Phase2TimeUiAction.StartAdvance(request)) }
                )
            }
        }

        state.advanceInProgress?.let { progress ->
            AdvanceInProgressPanel(progress, onAction, interactionEnabled)
        }

        if (state.status == TimeAdvanceStatus.INTERRUPTED) {
            state.continuationOfCommandId?.let { commandId ->
                state.resumeRequest?.let { request ->
                    Phase2Button(
                        tag = "phase2-time-continue",
                        label = "Continue",
                        description = "Continue time advance",
                        index = 10f,
                        enabled = interactionEnabled,
                        onClick = { onAction(Phase2TimeUiAction.ResumeInterrupted(commandId, request)) }
                    )
                }
            }
        }

        state.decisionRequired?.let { decision ->
            DecisionPanel(decision, onAction, interactionEnabled)
        }

        state.summary?.let { summary ->
            SummaryPanel(summary)
        }

        state.conflict?.let { conflict ->
            ConflictPanel(
                conflict = conflict,
                armedRiskResolution = armedRiskResolution,
                interactionEnabled = interactionEnabled,
                onArmRisk = { armedRiskResolution = it },
                onRefresh = { onAction(Phase2TimeUiAction.RefreshPreview(it)) }
            ) { resolution ->
                armedRiskResolution = null
                onAction(
                    Phase2TimeUiAction.ConfirmConflict(
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
    onAction: (Phase2TimeUiAction) -> Unit,
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
                        Phase2TimeUiAction.Continue(
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
    onAction: (Phase2TimeUiAction) -> Unit,
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
                    label = "Pause",
                    description = "Request pause at the next safe boundary",
                    index = 20f,
                    enabled = interactionEnabled,
                    onClick = { onAction(Phase2TimeUiAction.Pause) }
                )
            }
            if (AdvanceControl.CANCEL in progress.allowedControls) {
                Phase2Button(
                    tag = "phase2-time-control-cancel",
                    label = "Cancel",
                    description = "Request cancellation at the next safe boundary",
                    index = 21f,
                    enabled = interactionEnabled,
                    onClick = { onAction(Phase2TimeUiAction.Cancel) }
                )
            }
        }
    }
}

@Composable
private fun SummaryPanel(summary: TimeAdvanceSummary) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("phase2-time-summary")
            .semantics { contentDescription = "Time advance summary" },
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text("Summary", style = MaterialTheme.typography.titleMedium)
        Text("Elapsed: ${summary.elapsed}")
        Text("Stopped: ${summary.stopReason}")
        SummaryItems("Major events", "major-events", summary.majorEvents)
        SummaryItems("Completed", "completed", summary.completedWork)
        SummaryItems("Resource warnings", "resource-warnings", summary.resourceWarnings)
        SummaryItems("Important changes", "important-changes", summary.importantChanges)
        Text("Bundles: ${summary.bundleCount}")
        Text("Unknown important items: ${summary.unknownImportantCount}")
        summary.nextActionLabel?.let { Text("Next: $it") }
    }
}

@Composable
private fun SummaryItems(label: String, tag: String, items: List<String>) {
    items.take(SUMMARY_ITEM_LIMIT).forEachIndexed { index, item ->
        Text("$label: $item", modifier = Modifier.testTag("phase2-time-summary-$tag-$index"))
    }
    val remaining = items.size - SUMMARY_ITEM_LIMIT
    if (remaining > 0) {
        Text("$label: $remaining more", modifier = Modifier.testTag("phase2-time-summary-$tag-overflow"))
    }
}

@Composable
private fun ConflictPanel(
    conflict: ScheduleConflictViewState,
    armedRiskResolution: ScheduleResolution?,
    interactionEnabled: Boolean,
    onArmRisk: (ScheduleResolution) -> Unit,
    onRefresh: (String) -> Unit,
    onConfirm: (ScheduleResolution) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("phase2-time-conflict")
            .semantics { contentDescription = "Schedule conflict ${conflict.conflictId}" },
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("Schedule conflict", style = MaterialTheme.typography.titleMedium)
        conflict.conflictingSchedules.forEachIndexed { index, schedule ->
            Text(schedule, modifier = textModifier("phase2-time-conflict-$index", schedule, 30f + index))
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
                enabled = interactionEnabled,
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
                enabled = interactionEnabled && !conflict.previewStale,
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

private const val SUMMARY_ITEM_LIMIT = 5

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
