package com.imsi.mud.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

sealed interface AppShellState {
    data object Loading : AppShellState
    data object Ready : AppShellState
    data object Empty : AppShellState
    data class Error(val reason: String) : AppShellState
    data class Blocked(val screenId: String?, val reason: String) : AppShellState

    companion object {
        fun placeholderFor(route: String): Blocked =
            if (ScreenRegistry.contains(route)) {
                Blocked(route, "This screen is owned by a later phase.")
            } else {
                Blocked(null, "Unknown screen route: $route")
            }
    }
}

object ScreenRegistry {
    private val ids = setOf(
        "SCR-START-001", "SCR-START-002", "SCR-HOME-001", "SCR-DUN-001", "SCR-DUN-002",
        "SCR-DUN-003", "SCR-DUN-004", "SCR-EVT-001", "SCR-CMB-001", "SCR-CMB-002",
        "SCR-PTY-001", "SCR-PTY-002", "SCR-PTY-003", "SCR-PTY-004", "SCR-MER-001",
        "SCR-MER-002", "SCR-REC-001", "SCR-REC-002", "SCR-QST-001", "SCR-QST-002",
        "SCR-DLG-001", "SCR-ITM-001", "SCR-ITM-002", "SCR-SKL-001", "SCR-SKL-002",
        "SCR-ENH-001", "SCR-RFN-001", "SCR-CRF-001", "SCR-CITY-001", "SCR-CITY-002",
        "SCR-MNT-001", "SCR-MKT-001", "SCR-AUC-001", "SCR-LOAN-001", "SCR-LOG-001",
        "SCR-HOU-001", "SCR-GIL-001", "SCR-GIL-002", "SCR-GIL-003", "SCR-GIL-004",
        "SCR-STR-001", "SCR-TIME-001", "SCR-TIME-002", "SCR-FAM-001", "SCR-FAM-002",
        "SCR-RECOR-001", "SCR-RECOR-002", "SCR-SEARCH-001", "SCR-NOTI-001", "SCR-SAVE-001",
        "SCR-SAVE-002", "SCR-RET-001", "SCR-RET-002", "SCR-SET-001", "SCR-VAL-001",
        "SCR-VAL-002"
    )

    fun contains(screenId: String): Boolean = screenId in ids

    fun count(): Int = ids.size
}

@Composable
fun AppRoot(state: AppShellState, onRetry: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        when (state) {
            AppShellState.Loading -> {
                CircularProgressIndicator(
                    Modifier
                        .testTag("app-shell-loading")
                        .semantics { contentDescription = "Loading" }
                )
                Text(
                    text = "Loading",
                    modifier = Modifier.testTag("app-shell-loading-title").semantics {
                        contentDescription = "Loading"
                        traversalIndex = 0f
                    },
                    style = MaterialTheme.typography.headlineSmall
                )
                Text(
                    text = "Preparing the world session.",
                    modifier = Modifier.testTag("app-shell-loading-body").semantics {
                        contentDescription = "Preparing the world session."
                        traversalIndex = 1f
                    }
                )
            }

            AppShellState.Ready -> {
                Text(
                    text = "Ready",
                    modifier = Modifier.testTag("app-shell-ready-title").semantics {
                        contentDescription = "Ready"
                        traversalIndex = 0f
                    },
                    style = MaterialTheme.typography.headlineSmall
                )
                Text(
                    text = "The current world is ready for an implemented feature.",
                    modifier = Modifier.testTag("app-shell-ready-body").semantics {
                        contentDescription = "The current world is ready for an implemented feature."
                        traversalIndex = 1f
                    }
                )
            }

            AppShellState.Empty -> {
                Text(
                    text = "No world is available yet",
                    modifier = Modifier.testTag("app-shell-empty-title").semantics {
                        contentDescription = "No world is available yet"
                        traversalIndex = 0f
                    },
                    style = MaterialTheme.typography.headlineSmall
                )
                Text(
                    text = "Create and import actions are introduced with Phase 3 save support.",
                    modifier = Modifier.testTag("app-shell-empty-body").semantics {
                        contentDescription = "Create and import actions are introduced with Phase 3 save support."
                        traversalIndex = 1f
                    }
                )
            }

            is AppShellState.Error -> {
                Text(
                    text = "Unable to continue",
                    modifier = Modifier.testTag("app-shell-error-title").semantics {
                        contentDescription = "Unable to continue"
                        traversalIndex = 0f
                    },
                    style = MaterialTheme.typography.headlineSmall
                )
                Text(
                    text = state.reason,
                    modifier = Modifier.testTag("app-shell-error-body").semantics {
                        contentDescription = state.reason
                        traversalIndex = 1f
                    }
                )
                Button(
                    onClick = { runCatching(onRetry) },
                    modifier = Modifier
                        .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                        .testTag("app-shell-retry")
                        .semantics {
                            contentDescription = "Retry"
                            traversalIndex = 2f
                        }
                ) {
                    Text("Retry")
                }
            }

            is AppShellState.Blocked -> {
                Text(
                    text = "Feature unavailable",
                    modifier = Modifier.testTag("app-shell-blocked-title").semantics {
                        contentDescription = "Feature unavailable"
                        traversalIndex = 0f
                    },
                    style = MaterialTheme.typography.headlineSmall
                )
                state.screenId?.let {
                    Text(
                        text = it,
                        modifier = Modifier
                            .testTag("app-shell-blocked-screen")
                            .semantics {
                                contentDescription = "Unavailable screen $it"
                                traversalIndex = 1f
                            }
                    )
                }
                Text(
                    text = state.reason,
                    modifier = Modifier.testTag("app-shell-blocked-body").semantics {
                        contentDescription = state.reason
                        traversalIndex = if (state.screenId == null) 1f else 2f
                    }
                )
            }
        }
    }
}
