package com.imsi.mud.ui

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import com.imsi.mud.MainActivity
import org.junit.Rule
import org.junit.Test

class MainActivityTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun coldLaunchOpensPhase2RouteInsteadOfReadyPlaceholder() {
        compose.onNodeWithTag("phase2-time-screen").assertIsDisplayed()
        compose.onAllNodesWithTag("app-shell-ready-title").assertCountEquals(0)
    }
}
