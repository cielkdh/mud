package com.imsi.mud.ui

import android.content.ComponentName
import android.content.Intent
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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
    fun mainLauncherIntentOpensPhase2RouteWithStartAction() {
        compose.onNodeWithTag("phase2-time-screen").assertIsDisplayed()
        compose.onNodeWithText("Start time advance").assertIsDisplayed()
        compose.onAllNodesWithTag("app-shell-ready-title").assertCountEquals(0)
        compose.onNodeWithTag("phase2-time-start").performClick()
        compose.waitForIdle()
    }
}
