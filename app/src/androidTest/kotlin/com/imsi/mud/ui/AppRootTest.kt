package com.imsi.mud.ui

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.Rule

class AppRootTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun rendersEachShellStateAndKeepsTheRegistryClosed() {
        val shellState = mutableStateOf<AppShellState>(AppShellState.Loading)
        compose.setContent { AppRoot(shellState.value, onRetry = {}) }

        listOf(
            Triple(AppShellState.Loading, "app-shell-loading", listOf("Loading", "Preparing the world session.")),
            Triple(AppShellState.Ready, "app-shell-ready", listOf("Ready", "The current world is ready for an implemented feature.")),
            Triple(AppShellState.Empty, "app-shell-empty", listOf("No world is available yet", "Create and import actions are introduced with Phase 3 save support.")),
            Triple(AppShellState.Error("temporary failure"), "app-shell-error", listOf("Unable to continue", "temporary failure")),
            Triple(AppShellState.placeholderFor("SCR-START-001"), "app-shell-blocked", listOf("Feature unavailable", "This screen is owned by a later phase."))
        ).forEach { (state, prefix, descriptions) ->
            compose.runOnIdle { shellState.value = state }
            if (state == AppShellState.Loading) {
                compose.onNodeWithTag(prefix)
                    .assertIsDisplayed()
                    .assert(SemanticsMatcher.expectValue(SemanticsProperties.ContentDescription, listOf("Loading")))
            }
            compose.onNodeWithTag("$prefix-title")
                .assertIsDisplayed()
                .assert(SemanticsMatcher.expectValue(SemanticsProperties.ContentDescription, listOf(descriptions[0])))
                .assert(SemanticsMatcher.expectValue(SemanticsProperties.TraversalIndex, 0f))
            compose.onNodeWithTag("$prefix-body")
                .assertIsDisplayed()
                .assert(SemanticsMatcher.expectValue(SemanticsProperties.ContentDescription, listOf(descriptions[1])))
                .assert(SemanticsMatcher.expectValue(SemanticsProperties.TraversalIndex, if (prefix == "app-shell-blocked") 2f else 1f))
        }

        compose.onNodeWithTag("app-shell-blocked-screen")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.ContentDescription, listOf("Unavailable screen SCR-START-001")))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.TraversalIndex, 1f))

        assertEquals(56, ScreenRegistry.count())
        compose.runOnIdle { shellState.value = AppShellState.placeholderFor("SCR-UNKNOWN-999") }
        compose.onNodeWithTag("app-shell-blocked-title")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.TraversalIndex, 0f))
        compose.onNodeWithTag("app-shell-blocked-body")
            .assertTextEquals("Unknown screen route: SCR-UNKNOWN-999")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.ContentDescription, listOf("Unknown screen route: SCR-UNKNOWN-999")))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.TraversalIndex, 1f))
    }

    @Test
    fun retryIsAccessibleAndDoesNotTurnAnErrorIntoSuccess() {
        var retryCount = 0
        compose.setContent {
            AppRoot(AppShellState.Error("retry failed")) {
                retryCount += 1
            }
        }

        compose.onNodeWithTag("app-shell-retry")
            .assertHeightIsAtLeast(48.dp)
            .assertWidthIsAtLeast(48.dp)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.TraversalIndex, 2f))
            .performClick()
        compose.onNodeWithContentDescription("Retry").assertIsDisplayed()
        compose.onNodeWithTag("app-shell-error-title")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.TraversalIndex, 0f))
        compose.onNodeWithTag("app-shell-error-body")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.TraversalIndex, 1f))
        assertEquals(1, retryCount)
    }
}
