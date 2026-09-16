package com.imsi.mud

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.platform.ComposeView
import com.imsi.mud.ui.AppRoot
import com.imsi.mud.ui.AppShellState
import com.imsi.mud.ui.Phase2TimeEntry
import com.imsi.mud.ui.Phase2TimeRoute
import com.imsi.mud.ui.Phase2TimeViewState
import com.imsi.mud.ui.TimeAdvanceStatus

open class MainActivity : ComponentActivity() {
    protected open fun phase2TimeEntry(): Phase2TimeEntry? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(ComposeView(this).apply {
            setContent {
                MainActivityContent(phase2TimeEntry(), onBack = ::finish)
            }
        })
    }
}

@Composable
fun MainActivityContent(phase2TimeEntry: Phase2TimeEntry?, onBack: () -> Unit) {
    MaterialTheme {
        if (phase2TimeEntry == null) {
            Phase2TimeRoute(
                state = Phase2TimeViewState(status = TimeAdvanceStatus.IDLE),
                onAction = {}
            )
        } else {
            AppRoot(
                state = AppShellState.Ready,
                onRetry = {},
                phase2TimeEntry = phase2TimeEntry,
                onBack = onBack
            )
        }
    }
}
