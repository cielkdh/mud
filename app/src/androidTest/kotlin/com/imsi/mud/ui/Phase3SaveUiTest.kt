package com.imsi.mud.ui

import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class Phase3SaveUiTest {
    @get:Rule
    val compose = createComposeRule()

    private val slot = Phase3SaveSlot(
        label = "Manual save",
        dateLabel = "Year 142, April 12, 18:40",
        characterLabel = "Minseo Gong · Lv.112 · Swordsman",
        progressLabel = "Return 3/5 · Playtime 183:42",
        locationLabel = "Golden Lion Guild"
    )

    @Test
    fun readySeparatesContinueManualImportAndExportActions() {
        val actions = mutableListOf<Phase3SaveAction>()
        compose.setContent {
            Phase3SaveRoute(
                Phase3SaveState.Ready(current = slot, manualSlots = listOf(slot)),
                onAction = actions::add
            )
        }

        compose.onNodeWithTag("phase3-save-ready-title").assertIsDisplayed()
        compose.onNodeWithTag("phase3-save-continue-heading").assertIsDisplayed()
        compose.onNodeWithTag("phase3-save-new-game").performClick()
        compose.onNodeWithTag("phase3-save-import").performClick()
        compose.onNodeWithTag("phase3-save-export").performClick()
        compose.onNodeWithTag("phase3-save-manual-0-action").performClick()

        assertEquals(
            listOf(
                Phase3SaveAction.NewGame,
                Phase3SaveAction.Import,
                Phase3SaveAction.Export,
                Phase3SaveAction.Load(slot)
            ),
            actions
        )
    }

    @Test
    fun emptyStateOffersNewGameImportAndBackWithoutPretendingReady() {
        val actions = mutableListOf<Phase3SaveAction>()
        compose.setContent { Phase3SaveRoute(Phase3SaveState.Empty, actions::add) }

        compose.onNodeWithTag("phase3-save-empty-title").assertIsDisplayed()
        compose.onNodeWithTag("phase3-save-ready-title").assertDoesNotExist()
        compose.onNodeWithTag("phase3-save-empty-new-game").performClick()
        compose.onNodeWithTag("phase3-save-empty-import").performClick()
        compose.onNodeWithTag("phase3-save-empty-back").performClick()

        assertEquals(
            listOf(Phase3SaveAction.NewGame, Phase3SaveAction.Import, Phase3SaveAction.Back),
            actions
        )
    }

    @Test
    fun loadingExplainsWhyItIsWaitingAndExposesCancelOnlyWhenAllowed() {
        val actions = mutableListOf<Phase3SaveAction>()
        compose.setContent {
            Phase3SaveRoute(
                Phase3SaveState.Loading(Phase3LoadingReason.WAITING_FOR_PREVIOUS_SESSION, canCancel = true),
                actions::add
            )
        }

        compose.onNodeWithTag("phase3-save-loading-body")
            .assertTextEquals("Waiting for the previous session to finish closing safely.")
        compose.onNodeWithTag("phase3-save-cancel")
            .assertHeightIsAtLeast(48.dp)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.TraversalIndex, 2f))
            .performClick()
        assertEquals(listOf(Phase3SaveAction.Cancel), actions)
    }

    @Test
    fun dirtyConfirmationMakesDataLossChoiceExplicit() {
        val actions = mutableListOf<Phase3SaveAction>()
        compose.setContent {
            Phase3SaveRoute(Phase3SaveState.DirtyConfirmation("a different save"), actions::add)
        }

        compose.onNodeWithTag("phase3-save-dirty-body")
            .assertTextEquals("Your current progress has not been saved. Choose how to continue to a different save.")
        compose.onNodeWithTag("phase3-save-dirty-save").performClick()
        compose.onNodeWithTag("phase3-save-dirty-discard").performClick()
        compose.onNodeWithTag("phase3-save-dirty-cancel").performClick()
        assertEquals(
            listOf(
                Phase3SaveAction.SaveAndContinue,
                Phase3SaveAction.ContinueWithoutSaving,
                Phase3SaveAction.Cancel
            ),
            actions
        )
    }

    @Test
    fun recoveryUsesPublicMetadataAndDoesNotExposeInternalIds() {
        val candidate = Phase3RecoveryCandidate(slot, "Verified 3 minutes ago", "Older progress")
        val actions = mutableListOf<Phase3SaveAction>()
        compose.setContent { Phase3SaveRoute(Phase3SaveState.Recovery(listOf(candidate)), actions::add) }

        compose.onNodeWithTag("phase3-save-recovery-0-action").performClick()
        compose.onNodeWithTag("phase3-save-recovery-0").assertIsDisplayed()
        compose.onNodeWithText("Manual save").assertIsDisplayed()
        compose.onNodeWithText("Older progress").assertIsDisplayed()
        assertEquals(listOf(Phase3SaveAction.Restore(candidate)), actions)
    }

    @Test
    fun errorsArePublicAndOfferRecoveryActions() {
        val actions = mutableListOf<Phase3SaveAction>()
        compose.setContent { Phase3SaveRoute(Phase3SaveState.Error(Phase3SaveError.CORRUPTED), actions::add) }

        compose.onNodeWithTag("phase3-save-error-body")
            .assertTextEquals("This save is damaged. Choose a verified recovery candidate or try again.")
        compose.onNodeWithTag("phase3-save-error-retry").performClick()
        compose.onNodeWithTag("phase3-save-error-back").performClick()
        assertEquals(listOf(Phase3SaveAction.Retry, Phase3SaveAction.Back), actions)
    }
}
