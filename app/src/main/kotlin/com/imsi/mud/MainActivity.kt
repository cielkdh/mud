package com.imsi.mud

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.platform.ComposeView
import com.imsi.mud.content.ContentSnapshot
import com.imsi.mud.simulation.AuthoritativeWorldState
import com.imsi.mud.simulation.CommandEnvelope
import com.imsi.mud.simulation.CommandId
import com.imsi.mud.simulation.CommitReceipt
import com.imsi.mud.simulation.DomainDelta
import com.imsi.mud.simulation.GameMinute
import com.imsi.mud.simulation.PersistedReceipt
import com.imsi.mud.simulation.ReceiptLifecycle
import com.imsi.mud.simulation.SavePort
import com.imsi.mud.simulation.SegmentCommitReceipt
import com.imsi.mud.simulation.SealedElapsedOutcome
import com.imsi.mud.simulation.SessionEpoch
import com.imsi.mud.simulation.StateVersion
import com.imsi.mud.simulation.TimeAdvanceContinuation
import com.imsi.mud.simulation.TimeAdvanceResult
import com.imsi.mud.simulation.TimeAdvanceState
import com.imsi.mud.simulation.WorldCommandPayload
import com.imsi.mud.simulation.WorldClock
import com.imsi.mud.simulation.WorldEngine
import com.imsi.mud.simulation.WorldSession
import com.imsi.mud.simulation.WorldSnapshot
import com.imsi.mud.simulation.BoundaryRegistryBinding
import com.imsi.mud.simulation.RngState
import com.imsi.mud.simulation.ProgressionMode
import com.imsi.mud.simulation.ScheduleCalendar
import com.imsi.mud.simulation.TimeAdvanceGoal
import com.imsi.mud.simulation.TimeAdvanceInterruptPolicy
import com.imsi.mud.simulation.TimeTraversalLimits
import com.imsi.mud.ui.AppRoot
import com.imsi.mud.ui.AppShellState
import com.imsi.mud.ui.Phase2AdvanceRequest
import com.imsi.mud.ui.Phase2TimeEntry
import com.imsi.mud.ui.Phase2TimeViewState
import com.imsi.mud.ui.Phase2TimeController
import com.imsi.mud.ui.TimeAdvanceStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

open class MainActivity : ComponentActivity() {
    private var launcherEntry: Phase2TimeEntry? = null
    private var launcherScope: CoroutineScope? = null

    protected open fun phase2TimeEntry(): Phase2TimeEntry = launcherEntry ?: createLauncherPhase2TimeEntry().also {
        launcherEntry = it
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(ComposeView(this).apply {
            setContent {
                MainActivityContent(phase2TimeEntry(), onBack = ::finish)
            }
        })
    }

    override fun onDestroy() {
        launcherScope?.cancel()
        super.onDestroy()
    }

    private fun createLauncherPhase2TimeEntry(): Phase2TimeEntry {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        launcherScope = scope
        val session = WorldSession(
            epoch = SessionEpoch(1),
            savePort = LauncherSavePort(),
            parentScope = scope,
            contentSnapshot = ContentSnapshot.create(
                contentVersion = "phase2-content.v1",
                balanceVersion = "phase2-balance.v1",
                schemaVersion = ContentSnapshot.SCHEMA_VERSION,
                templates = emptyList()
            ),
            initialSnapshot = WorldSnapshot(
                stateVersion = StateVersion(0),
                world = AuthoritativeWorldState(
                    clock = WorldClock(minute(0)),
                    calendar = ScheduleCalendar(emptyList(), emptyMap()),
                    timeAdvance = null,
                    boundaryBinding = BoundaryRegistryBinding(
                        engineOrderVersion = 1,
                        sourceIds = listOf("elapsed.action"),
                        candidateCodecs = setOf(SealedElapsedOutcome.CODEC_ID)
                    )
                ),
                rngState = RngState(emptyList()),
                aggregates = emptyMap()
            ),
            engine = WorldEngine(emptyList())
        )
        val request = Phase2AdvanceRequest(
            goal = TimeAdvanceGoal.UntilMinute(minute(1)),
            mode = ProgressionMode.FAST_FORWARD,
            limits = TimeTraversalLimits(minute(1), maxBoundaryCount = 8),
            interruptPolicy = TimeAdvanceInterruptPolicy()
        )
        return Phase2TimeEntry(
            state = Phase2TimeViewState(
                status = TimeAdvanceStatus.IDLE,
                startRequest = request
            ),
            controller = Phase2TimeController(session)
        )
    }

    private fun minute(value: Long): GameMinute = when (val checked = GameMinute.of(value)) {
        is com.imsi.mud.simulation.Checked.Value -> checked.value
        is com.imsi.mud.simulation.Checked.Rejected -> error(checked.error.toString())
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

private class LauncherSavePort : SavePort {
    private val receipts = mutableMapOf<Pair<SessionEpoch, CommandId>, PersistedReceipt>()
    private var nextStateVersion = 0L

    override suspend fun findReceipt(sessionEpoch: SessionEpoch, commandId: CommandId): PersistedReceipt? =
        receipts[sessionEpoch to commandId]

    override suspend fun commit(
        envelope: CommandEnvelope<out WorldCommandPayload>,
        delta: DomainDelta
    ): CommitReceipt {
        val receipt = CommitReceipt(StateVersion(++nextStateVersion), delta.result)
        receipts[envelope.sessionEpoch to envelope.commandId] = PersistedReceipt(
            payloadHash = envelope.payloadHash,
            stateVersion = receipt.stateVersion,
            result = receipt.result
        )
        return receipt
    }

    override suspend fun commitSegment(
        envelope: CommandEnvelope<out WorldCommandPayload>,
        expectedSegmentNo: Int,
        delta: DomainDelta,
        timeAdvanceState: TimeAdvanceState,
        terminalResult: TimeAdvanceResult?,
        continuation: TimeAdvanceContinuation?
    ): SegmentCommitReceipt {
        val lifecycle = when (terminalResult) {
            null -> ReceiptLifecycle.RUNNING
            TimeAdvanceResult.INTERRUPTED, TimeAdvanceResult.DECISION_REQUIRED, TimeAdvanceResult.FAILED -> ReceiptLifecycle.INTERRUPTED
            else -> ReceiptLifecycle.COMMITTED
        }
        val receipt = CommitReceipt(
            stateVersion = StateVersion(++nextStateVersion),
            result = delta.result,
            lifecycleStatus = lifecycle,
            lastCommittedSegmentNo = expectedSegmentNo
        )
        receipts[envelope.sessionEpoch to envelope.commandId] = PersistedReceipt(
            payloadHash = envelope.payloadHash,
            stateVersion = receipt.stateVersion,
            result = receipt.result,
            lifecycleStatus = lifecycle,
            lastCommittedSegmentNo = expectedSegmentNo,
            timeAdvanceState = timeAdvanceState,
            continuation = continuation
        )
        return SegmentCommitReceipt(receipt, expectedSegmentNo, timeAdvanceState)
    }
}
