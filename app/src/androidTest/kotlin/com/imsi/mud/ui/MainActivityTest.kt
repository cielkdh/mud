package com.imsi.mud.ui

import android.content.ComponentName
import android.content.Intent
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.rules.ActivityScenarioRule
import com.imsi.mud.MainActivity
import org.junit.Rule
import org.junit.Test

class MainActivityTest {
    @get:Rule
    val activity = ActivityScenarioRule<MainActivity>(
        Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LAUNCHER)
            .setComponent(ComponentName("com.imsi.mud", MainActivity::class.java.name))
    )

    @get:Rule
    val compose = createEmptyComposeRule()

    @Test
    fun mainLauncherBlocksTimeAdvanceUntilLocalSaveIsAvailable() {
        compose.waitUntil(5_000) {
            runCatching { compose.onNodeWithTag("app-shell-blocked-title").assertIsDisplayed() }.isSuccess
        }
        compose.onNodeWithTag("app-shell-blocked-title").assertIsDisplayed()
        compose.onNodeWithText("SCR-START-001").assertIsDisplayed()
        compose.onNodeWithText("Local save is not available yet. Time advance will be enabled after save setup.")
            .assertIsDisplayed()
        compose.onNodeWithTag("app-shell-blocked-back").assertIsDisplayed()
        compose.onAllNodesWithTag("app-shell-ready-title").assertCountEquals(0)
        compose.onAllNodesWithTag("phase2-time-screen").assertCountEquals(0)
        compose.onAllNodesWithTag("phase2-time-start").assertCountEquals(0)
        compose.onAllNodesWithTag("phase2-time-publication").assertCountEquals(0)
        compose.onAllNodesWithTag("phase2-time-status-summary").assertCountEquals(0)
    }
}
