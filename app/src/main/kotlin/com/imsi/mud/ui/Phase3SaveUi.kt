package com.imsi.mud.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.unit.dp

/** Public, user-facing reasons. Internal DB paths, ids, and exception text never cross this boundary. */
enum class Phase3LoadingReason {
    OPENING_SESSION,
    WAITING_FOR_PREVIOUS_SESSION,
    SAVING,
    LOADING,
    RECOVERING,
    IMPORTING,
    EXPORTING
}

enum class Phase3SaveError {
    STORAGE_UNAVAILABLE,
    VERSION_UNSUPPORTED,
    CORRUPTED,
    PERMISSION_DENIED,
    UNKNOWN
}

enum class Phase3BlockedReason {
    SAVE_NOT_CONFIGURED,
    FEATURE_NOT_AVAILABLE,
    SESSION_UNAVAILABLE
}

data class Phase3SaveSlot(
    val label: String,
    val dateLabel: String,
    val characterLabel: String,
    val progressLabel: String,
    val locationLabel: String,
    val statusLabel: String = "Ready"
) {
    init {
        require(label.isNotBlank())
        require(dateLabel.isNotBlank())
        require(characterLabel.isNotBlank())
        require(progressLabel.isNotBlank())
        require(locationLabel.isNotBlank())
        require(statusLabel.isNotBlank())
    }
}

data class Phase3RecoveryCandidate(
    val slot: Phase3SaveSlot,
    val recoveryLabel: String,
    val warningLabel: String? = null
) {
    init {
        require(recoveryLabel.isNotBlank())
    }
}

sealed interface Phase3SaveState {
    data class Loading(
        val reason: Phase3LoadingReason,
        val canCancel: Boolean = false
    ) : Phase3SaveState

    data class Ready(
        val current: Phase3SaveSlot?,
        val manualSlots: List<Phase3SaveSlot> = emptyList(),
        val recoveryCandidates: List<Phase3RecoveryCandidate> = emptyList()
    ) : Phase3SaveState {
        init {
            require(current != null || manualSlots.isNotEmpty() || recoveryCandidates.isNotEmpty())
        }
    }

    data object Empty : Phase3SaveState

    data class DirtyConfirmation(val destinationLabel: String) : Phase3SaveState {
        init {
            require(destinationLabel.isNotBlank())
        }
    }

    data class Recovery(val candidates: List<Phase3RecoveryCandidate>) : Phase3SaveState

    data class Error(val issue: Phase3SaveError) : Phase3SaveState

    data class Blocked(val reason: Phase3BlockedReason) : Phase3SaveState
}

sealed interface Phase3SaveAction {
    data object Continue : Phase3SaveAction
    data object NewGame : Phase3SaveAction
    data object Import : Phase3SaveAction
    data object Export : Phase3SaveAction
    data object OpenRecovery : Phase3SaveAction
    data class Load(val slot: Phase3SaveSlot) : Phase3SaveAction
    data object SaveAndContinue : Phase3SaveAction
    data object ContinueWithoutSaving : Phase3SaveAction
    data object Cancel : Phase3SaveAction
    data class Restore(val candidate: Phase3RecoveryCandidate) : Phase3SaveAction
    data object Retry : Phase3SaveAction
    data object Back : Phase3SaveAction
}

@Composable
fun Phase3SaveRoute(
    state: Phase3SaveState,
    onAction: (Phase3SaveAction) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
            .testTag("phase3-save-screen"),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        when (state) {
            is Phase3SaveState.Loading -> LoadingPanel(state, onAction)
            is Phase3SaveState.Ready -> ReadyPanel(state, onAction)
            Phase3SaveState.Empty -> EmptyPanel(onAction)
            is Phase3SaveState.DirtyConfirmation -> DirtyPanel(state, onAction)
            is Phase3SaveState.Recovery -> RecoveryPanel(state, onAction)
            is Phase3SaveState.Error -> ErrorPanel(state, onAction)
            is Phase3SaveState.Blocked -> BlockedPanel(state, onAction)
        }
    }
}

@Composable
private fun LoadingPanel(state: Phase3SaveState.Loading, onAction: (Phase3SaveAction) -> Unit) {
    val title = "Preparing your saves"
    val body = state.reason.publicMessage()
    CircularProgressIndicator(
        modifier = Modifier
            .testTag("phase3-save-loading")
            .semantics { contentDescription = "$title: $body" }
    )
    Text(title, modifier = phase3Text("phase3-save-loading-title", title, 0f), style = MaterialTheme.typography.headlineSmall)
    Text(
        body,
        modifier = phase3Text("phase3-save-loading-body", body, 1f)
            .semantics { liveRegion = LiveRegionMode.Polite }
    )
    if (state.canCancel) {
        Phase3Button(
            tag = "phase3-save-cancel",
            label = "Cancel",
            description = "Cancel the current save operation",
            index = 2f,
            outlined = true,
            onClick = { onAction(Phase3SaveAction.Cancel) }
        )
    }
}

@Composable
private fun ReadyPanel(state: Phase3SaveState.Ready, onAction: (Phase3SaveAction) -> Unit) {
    Text("Saves", modifier = phase3Text("phase3-save-ready-title", "Saves", 0f), style = MaterialTheme.typography.headlineSmall)
    state.current?.let { slot ->
        Text("Continue", modifier = phase3Text("phase3-save-continue-heading", "Continue", 1f), style = MaterialTheme.typography.titleMedium)
        SaveSlotCard(slot, "phase3-save-current", 2f, onClick = { onAction(Phase3SaveAction.Continue) })
    }
    if (state.manualSlots.isNotEmpty()) {
        Text("Manual saves", modifier = phase3Text("phase3-save-manual-heading", "Manual saves", 3f), style = MaterialTheme.typography.titleMedium)
        state.manualSlots.forEachIndexed { index, slot ->
            SaveSlotCard(slot, "phase3-save-manual-$index", 4f + index, onClick = { onAction(Phase3SaveAction.Load(slot)) })
        }
    }
    if (state.recoveryCandidates.isNotEmpty()) {
        Phase3Button(
            tag = "phase3-save-open-recovery",
            label = "Review recoverable saves",
            description = "Review recoverable saves before choosing one",
            index = 20f,
            outlined = true,
            onClick = { onAction(Phase3SaveAction.OpenRecovery) }
        )
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Phase3Button(
            tag = "phase3-save-new-game",
            label = "New game",
            description = "Start a new game",
            index = 30f,
            onClick = { onAction(Phase3SaveAction.NewGame) }
        )
        Phase3Button(
            tag = "phase3-save-import",
            label = "Import",
            description = "Import a save into a new slot",
            index = 31f,
            outlined = true,
            onClick = { onAction(Phase3SaveAction.Import) }
        )
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Phase3Button(
            tag = "phase3-save-export",
            label = "Export",
            description = "Export a copy of a save",
            index = 32f,
            outlined = true,
            onClick = { onAction(Phase3SaveAction.Export) }
        )
        Phase3Button(
            tag = "phase3-save-back",
            label = "Back",
            description = "Go back",
            index = 33f,
            outlined = true,
            onClick = { onAction(Phase3SaveAction.Back) }
        )
    }
}

@Composable
private fun EmptyPanel(onAction: (Phase3SaveAction) -> Unit) {
    Text("No saves yet", modifier = phase3Text("phase3-save-empty-title", "No saves yet", 0f), style = MaterialTheme.typography.headlineSmall)
    Text(
        "Start a new game or import a save. Existing saves are never overwritten by an import.",
        modifier = phase3Text("phase3-save-empty-body", "Start a new game or import a save. Existing saves are never overwritten by an import.", 1f)
    )
    Phase3Button("phase3-save-empty-new-game", "New game", "Start a new game", 2f) { onAction(Phase3SaveAction.NewGame) }
    Phase3Button("phase3-save-empty-import", "Import", "Import a save into a new slot", 3f, outlined = true) { onAction(Phase3SaveAction.Import) }
    Phase3Button("phase3-save-empty-back", "Back", "Go back", 4f, outlined = true) { onAction(Phase3SaveAction.Back) }
}

@Composable
private fun DirtyPanel(state: Phase3SaveState.DirtyConfirmation, onAction: (Phase3SaveAction) -> Unit) {
    Text("Unsaved progress", modifier = phase3Text("phase3-save-dirty-title", "Unsaved progress", 0f), style = MaterialTheme.typography.headlineSmall)
    val body = "Your current progress has not been saved. Choose how to continue to ${state.destinationLabel}."
    Text(body, modifier = phase3Text("phase3-save-dirty-body", body, 1f))
    Phase3Button("phase3-save-dirty-save", "Save and continue", "Save your progress, then continue", 2f) { onAction(Phase3SaveAction.SaveAndContinue) }
    Phase3Button("phase3-save-dirty-discard", "Continue without saving", "Continue without saving the current progress", 3f, outlined = true) { onAction(Phase3SaveAction.ContinueWithoutSaving) }
    Phase3Button("phase3-save-dirty-cancel", "Cancel", "Stay on the current screen", 4f, outlined = true) { onAction(Phase3SaveAction.Cancel) }
}

@Composable
private fun RecoveryPanel(state: Phase3SaveState.Recovery, onAction: (Phase3SaveAction) -> Unit) {
    Text("Recover a save", modifier = phase3Text("phase3-save-recovery-title", "Recover a save", 0f), style = MaterialTheme.typography.headlineSmall)
    Text("Choose a verified save. Your current save is kept until recovery succeeds.", modifier = phase3Text("phase3-save-recovery-body", "Choose a verified save. Your current save is kept until recovery succeeds.", 1f))
    if (state.candidates.isEmpty()) {
        Text("No recoverable saves are available.", modifier = phase3Text("phase3-save-recovery-empty", "No recoverable saves are available.", 2f))
    } else {
        state.candidates.forEachIndexed { index, candidate ->
            SaveSlotCard(candidate.slot, "phase3-save-recovery-$index", 3f + index, candidate.recoveryLabel, candidate.warningLabel) {
                onAction(Phase3SaveAction.Restore(candidate))
            }
        }
    }
    Phase3Button("phase3-save-recovery-back", "Back", "Return without restoring a save", 20f, outlined = true) { onAction(Phase3SaveAction.Back) }
}

@Composable
private fun ErrorPanel(state: Phase3SaveState.Error, onAction: (Phase3SaveAction) -> Unit) {
    val title = "We couldn't open your saves"
    Text(title, modifier = phase3Text("phase3-save-error-title", title, 0f), style = MaterialTheme.typography.headlineSmall)
    val body = state.issue.publicMessage()
    Text(
        body,
        modifier = phase3Text("phase3-save-error-body", body, 1f)
            .semantics { liveRegion = LiveRegionMode.Assertive }
    )
    Phase3Button("phase3-save-error-retry", "Try again", "Try opening your saves again", 2f) { onAction(Phase3SaveAction.Retry) }
    Phase3Button("phase3-save-error-back", "Back", "Go back without changing your saves", 3f, outlined = true) { onAction(Phase3SaveAction.Back) }
}

@Composable
private fun BlockedPanel(state: Phase3SaveState.Blocked, onAction: (Phase3SaveAction) -> Unit) {
    val title = "Saves unavailable"
    Text(title, modifier = phase3Text("phase3-save-blocked-title", title, 0f), style = MaterialTheme.typography.headlineSmall)
    val body = state.reason.publicMessage()
    Text(body, modifier = phase3Text("phase3-save-blocked-body", body, 1f))
    Phase3Button("phase3-save-blocked-back", "Back", "Go back", 2f, outlined = true) { onAction(Phase3SaveAction.Back) }
}

@Composable
private fun SaveSlotCard(
    slot: Phase3SaveSlot,
    tag: String,
    index: Float,
    actionLabel: String = "Load",
    warningLabel: String? = null,
    onClick: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth().testTag(tag)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(slot.label, style = MaterialTheme.typography.titleMedium)
            Text(slot.dateLabel)
            Text(slot.characterLabel)
            Text(slot.progressLabel)
            Text(slot.locationLabel)
            Text(slot.statusLabel)
            warningLabel?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Phase3Button(
                tag = "$tag-action",
                label = actionLabel,
                description = "$actionLabel ${slot.label}",
                index = index,
                onClick = onClick
            )
        }
    }
}

@Composable
private fun Phase3Button(
    tag: String,
    label: String,
    description: String,
    index: Float,
    outlined: Boolean = false,
    onClick: () -> Unit
) {
    val modifier = Modifier
        .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
        .testTag(tag)
        .semantics {
            contentDescription = description
            traversalIndex = index
        }
    if (outlined) {
        OutlinedButton(onClick = onClick, modifier = modifier) { Text(label) }
    } else {
        Button(onClick = onClick, modifier = modifier) { Text(label) }
    }
}

private fun phase3Text(tag: String, text: String, index: Float): Modifier = Modifier
    .testTag(tag)
    .semantics {
        contentDescription = text
        traversalIndex = index
    }

private fun Phase3LoadingReason.publicMessage(): String = when (this) {
    Phase3LoadingReason.OPENING_SESSION -> "Opening your saved world."
    Phase3LoadingReason.WAITING_FOR_PREVIOUS_SESSION -> "Waiting for the previous session to finish closing safely."
    Phase3LoadingReason.SAVING -> "Saving your latest progress."
    Phase3LoadingReason.LOADING -> "Loading the selected save."
    Phase3LoadingReason.RECOVERING -> "Verifying and restoring a safe save."
    Phase3LoadingReason.IMPORTING -> "Checking the selected file before adding a new save."
    Phase3LoadingReason.EXPORTING -> "Preparing a copy of your save."
}

private fun Phase3SaveError.publicMessage(): String = when (this) {
    Phase3SaveError.STORAGE_UNAVAILABLE -> "Your saves could not be opened. Check available storage, then try again."
    Phase3SaveError.VERSION_UNSUPPORTED -> "This save was made by a newer version and cannot be opened here."
    Phase3SaveError.CORRUPTED -> "This save is damaged. Choose a verified recovery candidate or try again."
    Phase3SaveError.PERMISSION_DENIED -> "File access was not granted. Choose the file again or go back."
    Phase3SaveError.UNKNOWN -> "The save could not be opened. Try again or go back."
}

private fun Phase3BlockedReason.publicMessage(): String = when (this) {
    Phase3BlockedReason.SAVE_NOT_CONFIGURED -> "Save support is not available in this build yet."
    Phase3BlockedReason.FEATURE_NOT_AVAILABLE -> "This feature is not available right now."
    Phase3BlockedReason.SESSION_UNAVAILABLE -> "The world session is unavailable. Return and try again."
}
