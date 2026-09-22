package com.imsi.mud

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ComposeView
import com.imsi.mud.ui.AppRoot
import com.imsi.mud.ui.AppShellState
import com.imsi.mud.ui.TimeAdvanceEntry

open class MainActivity : ComponentActivity() {
    /** Android tests may inject a session-backed entry; the default app has no P3 SavePort yet. */
    protected open fun timeAdvanceEntry(): TimeAdvanceEntry? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(ComposeView(this).apply {
            setContent {
                MaterialTheme {
                    timeAdvanceEntry()?.let { entry ->
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
fun MainActivityContent(timeAdvanceEntry: TimeAdvanceEntry, onBack: () -> Unit) {
    MaterialTheme {
        AppRoot(
            state = AppShellState.Ready,
            onRetry = {},
            timeAdvanceEntry = timeAdvanceEntry,
            onBack = onBack
        )
    }
}
