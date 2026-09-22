package com.imsi.mud

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ComposeView
import com.imsi.mud.ui.AppRoot
import com.imsi.mud.ui.AppShellState
import com.imsi.mud.ui.Phase2TimeEntry

open class MainActivity : ComponentActivity() {
    /** Android tests may inject a session-backed entry; the default app has no P3 SavePort yet. */
    protected open fun phase2TimeEntry(): Phase2TimeEntry? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(ComposeView(this).apply {
            setContent {
                MaterialTheme {
                    phase2TimeEntry()?.let { entry ->
                        MainActivityContent(entry, onBack = ::finish)
                    } ?: AppRoot(
                        state = AppShellState.Blocked(
                            screenId = "SCR-START-001",
                            reason = "Local save is not available yet. Time advance will be enabled after save setup."
                        ),
                        onRetry = {},
                        onBack = ::finish
                    )
                }
            }
        })
    }
}

@Composable
fun MainActivityContent(phase2TimeEntry: Phase2TimeEntry, onBack: () -> Unit) {
    MaterialTheme {
        AppRoot(
            state = AppShellState.Ready,
            onRetry = {},
            phase2TimeEntry = phase2TimeEntry,
            onBack = onBack
        )
    }
}
