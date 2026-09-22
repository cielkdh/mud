@file:Suppress("INVISIBLE_REFERENCE", "INVISIBLE_MEMBER")

package com.imsi.mud.ui

import android.app.Activity
import android.app.Application
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.imsi.mud.content.ContentSnapshot
import com.imsi.mud.simulation.AdvanceControl as CoreAdvanceControl
import com.imsi.mud.simulation.ActionCancellationPolicy
import com.imsi.mud.simulation.ActionCancellationRule
import com.imsi.mud.simulation.ActionInterruptionPolicy
import com.imsi.mud.simulation.ActionInterruptionResult
import com.imsi.mud.simulation.ActionKindPolicyProfile
import com.imsi.mud.simulation.AuthoritativeWorldState
import com.imsi.mud.simulation.BoundaryCandidate
import com.imsi.mud.simulation.BoundaryCategory
import com.imsi.mud.simulation.BoundaryCursor
import com.imsi.mud.simulation.BoundaryKey
import com.imsi.mud.simulation.BoundaryRegistryBinding
import com.imsi.mud.simulation.BoundarySource
import com.imsi.mud.simulation.CancellationStage
import com.imsi.mud.simulation.Checked
import com.imsi.mud.simulation.CommandEnvelope
import com.imsi.mud.simulation.CommandId
import com.imsi.mud.simulation.CommandResult
import com.imsi.mud.simulation.CommitReceipt
import com.imsi.mud.simulation.ControlRequest
import com.imsi.mud.simulation.ControlRequestResult
import com.imsi.mud.simulation.DecisionSelection
import com.imsi.mud.simulation.DeterministicRng
import com.imsi.mud.simulation.DomainEvent
import com.imsi.mud.simulation.DomainEventPayload
import com.imsi.mud.simulation.DomainDelta
import com.imsi.mud.simulation.EntityId
import com.imsi.mud.simulation.GameMinute
import com.imsi.mud.simulation.PayloadHash
import com.imsi.mud.simulation.PersistedReceipt
import com.imsi.mud.simulation.ProcessWorldSessionCoordinator
import com.imsi.mud.simulation.ProgressionMode
import com.imsi.mud.simulation.PublicConsequencePreview as CorePublicConsequencePreview
import com.imsi.mud.simulation.PublicField as CorePublicField
import com.imsi.mud.simulation.PublicCompletedWork
import com.imsi.mud.simulation.PublicLowImportanceBundle
import com.imsi.mud.simulation.PublicResourceWarning
import com.imsi.mud.simulation.PublicScheduleEffect
import com.imsi.mud.simulation.PublicTimeAdvanceEvent
import com.imsi.mud.simulation.PublicTimeAdvanceContinuation
import com.imsi.mud.simulation.PublicTimeAdvanceNextAction
import com.imsi.mud.simulation.PublicValueState
import com.imsi.mud.simulation.ReceiptLifecycle
import com.imsi.mud.simulation.RngOperation
import com.imsi.mud.simulation.RngStreamKey
import com.imsi.mud.simulation.RngState
import com.imsi.mud.simulation.ReservationRequest
import com.imsi.mud.simulation.ReservationResult
import com.imsi.mud.simulation.SavePort
import com.imsi.mud.simulation.SessionEpoch
import com.imsi.mud.simulation.SessionLifecycle
import com.imsi.mud.simulation.SessionRuntimeState
import com.imsi.mud.simulation.SchedulePriority as CoreSchedulePriority
import com.imsi.mud.simulation.ScheduleConflictResult
import com.imsi.mud.simulation.ScheduleResolution as CoreScheduleResolution
import com.imsi.mud.simulation.ScheduleService
import com.imsi.mud.simulation.ScheduleReservePayload
import com.imsi.mud.simulation.ScheduleResolveConflictPayload
import com.imsi.mud.simulation.ScheduledActionPayload
import com.imsi.mud.simulation.ScheduledAction
import com.imsi.mud.simulation.ScheduledActionStatus
import com.imsi.mud.simulation.ScheduledEntityRef
import com.imsi.mud.simulation.StateVersion
import com.imsi.mud.simulation.SegmentCommitReceipt
import com.imsi.mud.simulation.AdvanceTimePayload
import com.imsi.mud.simulation.TimeAdvanceGoal
import com.imsi.mud.simulation.TimeAdvanceInterruptPolicy
import com.imsi.mud.simulation.TimeAdvanceResult
import com.imsi.mud.simulation.TimeAdvanceState
import com.imsi.mud.simulation.TimeAdvanceSummaryView
import com.imsi.mud.simulation.TimeAdvanceContinuation
import com.imsi.mud.simulation.TimeTraversalLimits
import com.imsi.mud.simulation.WorldCommandPayload
import com.imsi.mud.simulation.WorldClock
import com.imsi.mud.simulation.WorldEngine
import com.imsi.mud.simulation.WorldSession
import com.imsi.mud.simulation.WorldSnapshot
import com.imsi.mud.simulation.ScheduleCalendar
import com.imsi.mud.MainActivityContent
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

private fun actualMinute(value: Long): GameMinute = when (val checked = GameMinute.of(value)) {
    is Checked.Value -> checked.value
    is Checked.Rejected -> error(checked.error.toString())
}

private suspend fun acquireSession(session: WorldSession): ProcessWorldSessionCoordinator.WorldSessionHandle =
    checkNotNull(ProcessWorldSessionCoordinator.acquireForTest { session })

internal data class SessionBackedTestHandle(
    val session: ProcessWorldSessionCoordinator.WorldSessionHandle,
    val entry: TimeAdvanceEntry
)

internal object SessionBackedTestEntryHolder {
    private val completedClose = CompletableDeferred<Unit>().also { it.complete(Unit) }
    private val closeScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var previousClose: CompletableDeferred<Unit> = completedClose
    private var factory: (suspend () -> SessionBackedTestHandle)? = null
    private var failNextEntry = false
    private val createdSessions = mutableListOf<ProcessWorldSessionCoordinator.WorldSessionHandle>()
    private val closedSessions = mutableSetOf<ProcessWorldSessionCoordinator.WorldSessionHandle>()
    private val completedSessions = mutableSetOf<ProcessWorldSessionCoordinator.WorldSessionHandle>()
    private val lifecycleEvents = mutableListOf<String>()
    private var createdSignal = CompletableDeferred<Unit>()
    private var closedSignal = CompletableDeferred<Unit>()

    @Synchronized
    fun prepare(factory: suspend () -> SessionBackedTestHandle) {
        check(createdSessions.isEmpty()) { "test activity harness is still active" }
        this.factory = factory
        failNextEntry = false
        previousClose = completedClose
        createdSignal = CompletableDeferred()
        closedSignal = CompletableDeferred()
        lifecycleEvents.clear()
    }

    suspend fun awaitEntry(): SessionBackedTestHandle {
        val gate = synchronized(this) { previousClose }
        gate.await()
        synchronized(this) {
            if (failNextEntry) {
                failNextEntry = false
                error("test session setup failed")
            }
        }
        val create = checkNotNull(synchronized(this) { factory })
        val handle = create()
        synchronized(this) {
            createdSessions += handle.session
            lifecycleEvents += "created:${createdSessions.size}"
            val signal = createdSignal
            createdSignal = CompletableDeferred()
            signal.complete(Unit)
        }
        return handle
    }

    fun close(handle: SessionBackedTestHandle) {
        val completion = CompletableDeferred<Unit>()
        val index = synchronized(this) {
            val createdIndex = createdSessions.indexOf(handle.session)
            if (createdIndex < 0 || !closedSessions.add(handle.session)) return
            previousClose = completion
            createdIndex + 1
        }
        synchronized(this) { lifecycleEvents += "close-start:$index" }
        closeScope.launch {
            handle.session.close()
            synchronized(this@SessionBackedTestEntryHolder) {
                lifecycleEvents += "close-complete:$index"
                completedSessions += handle.session
                val signal = closedSignal
                closedSignal = CompletableDeferred()
                signal.complete(Unit)
            }
            completion.complete(Unit)
        }
    }

    suspend fun awaitCreatedCount(expected: Int) {
        while (true) {
            val signal = synchronized(this) {
                if (createdSessions.size >= expected) null else createdSignal
            } ?: return
            signal.await()
        }
    }

    suspend fun awaitClosedCount(expected: Int) {
        while (true) {
            val signal = synchronized(this) {
                if (completedSessions.size >= expected) null else closedSignal
            } ?: return
            signal.await()
        }
    }

    @Synchronized
    fun sessions(): List<ProcessWorldSessionCoordinator.WorldSessionHandle> = createdSessions.toList()

    @Synchronized
    fun events(): List<String> = lifecycleEvents.toList()

    @Synchronized
    fun isWaitingForPreviousClose(): Boolean = !previousClose.isCompleted

    @Synchronized
    fun failNextEntry() {
        failNextEntry = true
    }

    @Synchronized
    fun reset() {
        check(createdSessions.size == completedSessions.size) { "all test sessions must be closed" }
        factory = null
        createdSessions.clear()
        closedSessions.clear()
        completedSessions.clear()
        lifecycleEvents.clear()
        previousClose = completedClose
        failNextEntry = false
    }
}

internal class SessionBackedActivityLifecycleCallbacks : Application.ActivityLifecycleCallbacks {
    private val handles = mutableMapOf<Activity, SessionBackedTestHandle>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private fun loadingBody(): String = if (SessionBackedTestEntryHolder.isWaitingForPreviousClose()) {
        "Waiting for the previous world session to finish closing."
    } else {
        "Preparing a new world session."
    }

    private fun showLoading(component: ComponentActivity) {
        component.setContentView(ComposeView(component).apply {
            setContent {
                AppRoot(
                    state = AppShellState.Loading,
                    onRetry = {},
                    onBack = component::finish,
                    loadingBody = loadingBody()
                )
            }
        })
    }

    private fun attachEntry(activity: Activity, component: ComponentActivity) {
        scope.launch {
            try {
                val next = SessionBackedTestEntryHolder.awaitEntry()
                withContext(Dispatchers.Main.immediate) {
                    if (component.isDestroyed) {
                        SessionBackedTestEntryHolder.close(next)
                    } else {
                        handles[activity] = next
                        component.setContentView(ComposeView(component).apply {
                            setContent {
                                MainActivityContent(next.entry, onBack = component::finish)
                            }
                        })
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                withContext(Dispatchers.Main.immediate) {
                    if (!component.isDestroyed) {
                        component.setContentView(ComposeView(component).apply {
                            setContent {
                                AppRoot(
                                    state = AppShellState.Error("test session setup failed"),
                                    onRetry = {
                                        showLoading(component)
                                        attachEntry(activity, component)
                                    },
                                    onBack = component::finish
                                )
                            }
                        })
                    }
                }
            }
        }
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
        if (activity::class.java != ComponentActivity::class.java) return
        val component = activity as ComponentActivity
        showLoading(component)
        attachEntry(activity, component)
    }

    override fun onActivityDestroyed(activity: Activity) {
        handles.remove(activity)?.let(SessionBackedTestEntryHolder::close)
    }

    override fun onActivityStarted(activity: Activity) = Unit
    override fun onActivityResumed(activity: Activity) = Unit
    override fun onActivityPaused(activity: Activity) = Unit
    override fun onActivityStopped(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
}

class TimeAdvanceUiTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun rendersStatusesSummaryAndAllowedAdvanceControls() {
        val actions = mutableListOf<TimeAdvanceUiAction>()
        val summary = TimeAdvanceSummary(
            TimeAdvanceSummaryView(
                elapsedMinutes = 120,
                terminalReason = TimeAdvanceResult.DECISION_REQUIRED,
                majorEvents = listOf("Arrival", "A", "B", "C", "D").mapIndexed { index, type ->
                    PublicTimeAdvanceEvent(type, actualMinute(index.toLong()))
                },
                majorEventsOverflowCount = 2,
                completedWork = listOf(PublicCompletedWork("Treatment", actualMinute(120))),
                completedWorkOverflowCount = 0,
                resourceWarnings = listOf(PublicResourceWarning("BED", 0)),
                resourceWarningsOverflowCount = 0,
                importantChanges = listOf(PublicTimeAdvanceEvent("Public relation changed", actualMinute(60))),
                importantChangesOverflowCount = 0,
                lowImportanceBundles = listOf(PublicLowImportanceBundle("ordinary.event.v1", 2)),
                lowImportanceBundlesOverflowCount = 0,
                unknownImportantEventCount = 1,
                continuation = PublicTimeAdvanceContinuation.DECISION,
                nextAction = PublicTimeAdvanceNextAction.CHOOSE_DECISION
            )
        )
        compose.setContent {
            TimeAdvanceScreen(
                TimeAdvanceViewState(
                    status = TimeAdvanceStatus.ADVANCING,
                    goalLabel = "Until treatment completes",
                    advanceInProgress = AdvanceInProgressViewState("Until treatment completes", "10:30", setOf(AdvanceControl.PAUSE)),
                    summary = summary
                ),
                actions::add
            )
        }

        compose.onNodeWithTag("phase2-time-status-advancing").assertIsDisplayed()
        compose.onNodeWithTag("phase2-time-progress").assertIsDisplayed()
        compose.onNodeWithTag("phase2-time-control-pause")
            .assertHeightIsAtLeast(48.dp)
            .assertWidthIsAtLeast(48.dp)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
            .performClick()
        compose.onNodeWithTag("phase2-time-summary").assertIsDisplayed()
        compose.onNodeWithTag("phase2-time-summary-major-events-overflow").assertTextEquals("Major events: 2 more")
        compose.onNodeWithText("Unknown important items: 1").assertIsDisplayed()
        compose.onNodeWithText("Elapsed: 120 minutes").assertIsDisplayed()
        compose.onNodeWithText("Continuation: Decision").assertIsDisplayed()
        compose.onNodeWithText("Next: Choose decision").assertIsDisplayed()
        assertEquals(listOf(TimeAdvanceUiAction.Pause), actions)
    }

    @Test
    fun longKoreanCopyRemainsScrollableWithAccessibleControls() {
        val longCopy = "긴 한국어 안내 문구가 작은 화면과 큰 글꼴에서도 잘리지 않고 다음 안전 경계까지 진행 상태를 설명합니다. ".repeat(4)
        compose.setContent {
            TimeAdvanceScreen(
                TimeAdvanceViewState(
                    status = TimeAdvanceStatus.ADVANCE_IN_PROGRESS,
                    goalLabel = longCopy,
                    feedbackMessage = longCopy,
                    advanceInProgress = AdvanceInProgressViewState(
                        goalLabel = longCopy,
                        lastCommittedCursor = "10:30",
                        allowedControls = setOf(AdvanceControl.PAUSE)
                    )
                ),
                onAction = {}
            )
        }

        compose.onNodeWithTag("phase2-time-screen").assertIsDisplayed()
        compose.onNodeWithTag("phase2-time-control-feedback").assertIsDisplayed()
        compose.onNodeWithTag("phase2-time-screen")
            .performScrollToNode(hasTestTag("phase2-time-control-pause"))
        compose.onNodeWithTag("phase2-time-control-pause")
            .assertIsDisplayed()
            .assertHeightIsAtLeast(48.dp)
            .assertWidthIsAtLeast(48.dp)
    }

    @Test
    fun hidesCancelWhenProtectedCompletionHasNoAllowedControls() {
        compose.setContent {
            TimeAdvanceScreen(
                TimeAdvanceViewState(
                    status = TimeAdvanceStatus.ADVANCE_IN_PROGRESS,
                    advanceInProgress = AdvanceInProgressViewState("Completing", "10:30", emptySet())
                ),
                onAction = {}
            )
        }

        compose.onNodeWithTag("phase2-time-progress").assertIsDisplayed()
        compose.onAllNodesWithTag("phase2-time-control-cancel").assertCountEquals(0)
    }

    @Test
    fun requiresExplicitConfirmationBeforeRiskResolutionCallback() {
        val actions = mutableListOf<TimeAdvanceUiAction>()
        val preview = PublicConsequencePreview(
            currentSchedule = PublicConsequenceField("Current schedule", "Treatment 14:00-18:00", PublicValueStatus.KNOWN),
            proposedSchedule = PublicConsequenceField("New schedule", "Rescue 14:00-16:00", PublicValueStatus.KNOWN),
            cancelledSchedule = PublicConsequenceField("Cancelled schedule", "Treatment", PublicValueStatus.NONE),
            refundOrLoss = PublicConsequenceField("Refund or loss", "Unknown", PublicValueStatus.UNKNOWN),
            resourceSettlement = PublicConsequenceField("Resources", "Bed returned", PublicValueStatus.KNOWN),
            progressLoss = PublicConsequenceField("Progress loss", "Not applicable", PublicValueStatus.NOT_APPLICABLE),
            relationshipImpact = PublicConsequenceField("Relationship", "Not disclosed", PublicValueStatus.UNDETERMINED),
            reputationImpact = PublicConsequenceField("Reputation", "None", PublicValueStatus.NONE),
            rescheduleAvailability = PublicConsequenceField("Reschedule", "Possible", PublicValueStatus.KNOWN),
            riskReasons = listOf(PublicRiskReason.RESOURCE_LOSS),
            hasLoss = true
        )
        val conflict = ScheduleConflictViewState(
            conflictId = "conflict-1",
            previewToken = "v1",
            previewCodec = "PUBLIC_CONSEQUENCE.v1",
            previewHash = EMPTY_HASH,
            expectedRowVersions = listOf(ExpectedScheduleRowVersion("action-1", 3)),
            reservation = reservation(),
            conflictingSchedules = listOf("hidden-affection=0.99"),
            allowedResolutions = listOf(ScheduleResolution.PAUSE_AND_INSERT),
            preview = preview
        )
        val state = mutableStateOf(TimeAdvanceViewState(status = TimeAdvanceStatus.IDLE, conflict = conflict))
        compose.setContent {
            TimeAdvanceScreen(state.value, actions::add)
        }

        compose.onNodeWithTag("phase2-time-preview-current-schedule")
            .assertTextEquals("Current schedule: Treatment 14:00-18:00 (Confirmed)")
        compose.onNodeWithTag("phase2-time-preview-refund-or-loss")
            .assertTextEquals("Refund or loss: Unknown (Unknown)")
        compose.onNodeWithTag("phase2-time-preview-proposed-schedule").assertIsDisplayed()
        compose.onNodeWithTag("phase2-time-preview-cancelled-schedule").assertIsDisplayed()
        compose.onNodeWithTag("phase2-time-preview-resource-settlement").assertIsDisplayed()
        compose.onNodeWithTag("phase2-time-preview-progress-loss").assertIsDisplayed()
        compose.onNodeWithTag("phase2-time-preview-relationship-impact").assertIsDisplayed()
        compose.onNodeWithTag("phase2-time-preview-reputation-impact").assertIsDisplayed()
        compose.onNodeWithTag("phase2-time-preview-reschedule-availability").assertIsDisplayed()
        compose.onNodeWithText("Resource loss").assertIsDisplayed()
        compose.onNodeWithTag("phase2-time-conflict-0").assertTextEquals("Conflicting scheduled action 1")
        compose.onAllNodesWithText("hidden-affection=0.99").assertCountEquals(0)
        compose.onNodeWithTag("phase2-time-resolution-pause_and_insert").performClick()
        assertEquals(emptyList<TimeAdvanceUiAction>(), actions)
        compose.runOnIdle { state.value = state.value.copy(conflict = conflict.copy(previewToken = "v2")) }
        compose.onNodeWithTag("phase2-time-resolution-pause_and_insert").performClick()
        assertEquals(emptyList<TimeAdvanceUiAction>(), actions)
        compose.onNodeWithTag("phase2-time-resolution-pause_and_insert")
            .assertTextEquals("Confirm Pause and insert")
            .assertHeightIsAtLeast(48.dp)
            .performClick()
        assertEquals(
            listOf(
                TimeAdvanceUiAction.ConfirmConflict(
                    conflictId = "conflict-1",
                    resolution = ScheduleResolution.PAUSE_AND_INSERT,
                    expectedRowVersions = listOf(ExpectedScheduleRowVersion("action-1", 3)),
                    reservation = reservation(),
                    previewCodec = "PUBLIC_CONSEQUENCE.v1",
                    previewHash = EMPTY_HASH,
                    previewToken = "v2"
                )
            ),
            actions
        )
    }

    @Test
    fun preemptAndCancelAndInsertRequireSeparateRiskConfirmations() {
        val actions = mutableListOf<TimeAdvanceUiAction>()
        val state = mutableStateOf(
            TimeAdvanceViewState(
                status = TimeAdvanceStatus.IDLE,
                conflict = ScheduleConflictViewState(
                    conflictId = "risk-conflict",
                    previewToken = "risk-token",
                    previewCodec = "PublicConsequencePreview.v1",
                    previewHash = EMPTY_HASH,
                    expectedRowVersions = listOf(ExpectedScheduleRowVersion("action-1", 3)),
                    reservation = reservation(),
                    conflictingSchedules = listOf("Treatment"),
                    allowedResolutions = listOf(ScheduleResolution.PREEMPT, ScheduleResolution.CANCEL_AND_INSERT),
                    preview = emptyPreview()
                )
            )
        )
        compose.setContent { TimeAdvanceScreen(state.value, actions::add) }

        compose.onNodeWithTag("phase2-time-resolution-preempt").performClick()
        assertEquals(emptyList<TimeAdvanceUiAction>(), actions)
        compose.onNodeWithTag("phase2-time-resolution-preempt").assertTextEquals("Confirm Preempt").performClick()
        assertEquals(1, actions.size)
        assertEquals(ScheduleResolution.PREEMPT, (actions.single() as TimeAdvanceUiAction.ConfirmConflict).resolution)

        compose.onNodeWithTag("phase2-time-resolution-cancel_and_insert").performClick()
        compose.onNodeWithTag("phase2-time-resolution-cancel_and_insert").assertTextEquals("Confirm Cancel and insert").performClick()
        assertEquals(2, actions.size)
        assertEquals(ScheduleResolution.CANCEL_AND_INSERT, (actions.last() as TimeAdvanceUiAction.ConfirmConflict).resolution)
    }

    @Test
    fun normalReservationUsesExecuteAndSingleFlight() {
        val envelopes = mutableListOf<CommandEnvelope<out WorldCommandPayload>>()
        val release = CompletableDeferred<CommandResult>()
        val controller = TimeAdvanceController(
            execute = {
                envelopes += it
                release.await()
            },
            requestControl = { ControlRequestResult.Accepted(CommandId("active")) },
            runtimeState = { SessionRuntimeState(SessionEpoch(1), SessionLifecycle.OPEN, null) },
            currentVersion = { StateVersion(4) },
            newCommandId = { CommandId("reserve-command") }
        )
        val reservation = reservation()
        compose.setContent {
            TimeAdvanceRoute(
                TimeAdvanceEntry(
                    TimeAdvanceViewState(TimeAdvanceStatus.IDLE, scheduleReservation = reservation),
                    controller
                )
            )
        }

        compose.onNodeWithTag("phase2-time-reserve").performClick()
        compose.onNodeWithTag("phase2-time-reserve").assertIsNotEnabled().performClick()
        assertEquals(1, envelopes.size)
        assertEquals(reservation, envelopes.single().payload)
        release.complete(CommandResult.Accepted(5))
        compose.waitForIdle()
        compose.onNodeWithTag("phase2-time-status-idle").assertIsDisplayed()
    }

    @Test
    fun sessionBackedNormalReservationUsesWorldSessionAndSingleFlight() {
        runBlocking {
            val fixture = SessionBackedScheduleFixture(this)
            try {
                fixture.port.blockNextCommit()
                val controller = TimeAdvanceController(
                    fixture.session,
                    newCommandId = { CommandId("session-reserve") }
                )
                compose.setContent {
                    TimeAdvanceRoute(
                        TimeAdvanceEntry(
                            TimeAdvanceViewState(TimeAdvanceStatus.IDLE, scheduleReservation = reservation()),
                            controller
                        )
                    )
                }

                compose.onNodeWithTag("phase2-time-reserve").performClick()
                withTimeout(5_000) { fixture.port.commitEntered.await() }
                compose.onNodeWithTag("phase2-time-reserve").assertIsNotEnabled().performClick()
                fixture.port.releaseCommit.complete(Unit)
                compose.waitForIdle()

                assertEquals(1, fixture.port.commitCount)
                assertEquals(1, fixture.port.receipts.size)
                assertTrue(fixture.session.publications.value != null)
                compose.onNodeWithTag("phase2-time-publication").assertIsDisplayed()
                compose.onAllNodesWithTag("phase2-time-summary").assertCountEquals(0)
            } finally {
                fixture.session.close()
            }
        }
    }

    @Test
    fun sessionBackedDecisionGatePublishesChoicesAndSubmitsContinuation() = runBlocking {
        val fixture = DecisionGateSessionFixture(this)
        try {
            val controller = TimeAdvanceController(
                fixture.session,
                newCommandId = { if (fixture.port.commitCount == 0) CommandId("decision-parent") else CommandId("decision-child") }
            )
            compose.setContent {
                TimeAdvanceRoute(
                    TimeAdvanceEntry(
                        TimeAdvanceViewState(TimeAdvanceStatus.IDLE, startRequest = advanceRequest()),
                        controller
                    )
                )
            }

            compose.onNodeWithTag("phase2-time-start").performClick()
            compose.waitForIdle()
            compose.onNodeWithTag("phase2-time-choice-A").assertIsDisplayed()
            val parentPublication = checkNotNull(fixture.session.publications.value)
            assertEquals(TimeAdvanceResult.DECISION_REQUIRED, parentPublication.timeAdvanceTerminal?.result)
            assertEquals(TimeAdvanceResult.DECISION_REQUIRED, parentPublication.timeAdvanceSummary?.terminalReason)
            assertEquals("gate-1", parentPublication.timeAdvanceTerminal?.gateId)
            assertEquals(listOf("A", "B"), parentPublication.timeAdvanceTerminal?.choices?.map { it.choiceId })
            compose.onNodeWithText("Continuation: Decision").assertIsDisplayed()
            compose.onNodeWithText("Next: Choose decision").assertIsDisplayed()

            compose.onNodeWithTag("phase2-time-choice-A").performClick()
            compose.waitForIdle()
            assertEquals(3, fixture.port.commitCount)
            assertEquals("A", fixture.port.receipts.values.last().timeAdvanceState?.selectedDecisionChoiceId)
            assertEquals(TimeAdvanceResult.COMPLETED, fixture.session.publications.value?.timeAdvanceTerminal?.result)
            assertEquals(PublicTimeAdvanceNextAction.ACKNOWLEDGE, fixture.session.publications.value?.timeAdvanceSummary?.nextAction)
        } finally {
            fixture.session.close()
        }
    }

    @Test
    fun sessionBackedConflictResolutionUsesWorldSessionForPreempt() = runBlocking {
        runSessionBackedConflictResolution(this, ScheduleResolution.PREEMPT)
    }

    @Test
    fun sessionBackedConflictResolutionUsesWorldSessionForCancelAndInsert() = runBlocking {
        runSessionBackedConflictResolution(this, ScheduleResolution.CANCEL_AND_INSERT)
    }

    @Test
    fun sessionBackedConflictPreviewAndRowsAreRecheckedBeforeWorldSessionExecute() = runBlocking {
        val reservation = reservation().copy(startMinute = actualMinute(20), dueMinute = actualMinute(40))
        val calendar = ScheduleCalendar(listOf(existingScheduleAction()), emptyMap())
        val fixture = SessionBackedScheduleFixture(this, calendar)
        try {
            val conflict = ScheduleConflictViewState.from(
                conflictDetails(calendar, reservation),
                reservation,
                "session-stale"
            )
            val controller = TimeAdvanceController(
                fixture.session,
                newCommandId = { CommandId("session-stale") }
            )
            val state = TimeAdvanceViewState(TimeAdvanceStatus.IDLE, conflict = conflict)
            val action = confirmConflictAction(conflict)

            assertEquals(
                TimeAdvanceDispatchResult.RefreshRequested("session-stale"),
                controller.dispatch(action.copy(previewHash = "f".repeat(64)), state)
            )
            assertEquals(
                TimeAdvanceDispatchResult.RefreshRequested("session-stale"),
                controller.dispatch(
                    action.copy(expectedRowVersions = action.expectedRowVersions.map { it.copy(rowVersion = it.rowVersion + 1) }),
                    state
                )
            )
            assertEquals(0, fixture.port.commitCount)
            assertTrue(fixture.port.receipts.isEmpty())
        } finally {
            fixture.session.close()
        }
    }

    @Test
    fun sessionBackedCommitFailureDoesNotPublishAndRetryCommitsOnce() = runBlocking {
        val fixture = SessionBackedScheduleFixture(this)
        try {
            fixture.port.failNextCommit = true
            val controller = TimeAdvanceController(
                fixture.session,
                newCommandId = { CommandId("session-retry-${fixture.port.commitCount}") }
            )
            compose.setContent {
                TimeAdvanceRoute(
                    TimeAdvanceEntry(
                        TimeAdvanceViewState(TimeAdvanceStatus.IDLE, scheduleReservation = reservation()),
                        controller
                    )
                )
            }

            compose.onNodeWithTag("phase2-time-reserve").performClick()
            compose.waitForIdle()
            compose.onNodeWithTag("phase2-time-error").assertTextEquals("Schedule change could not be completed.")
            compose.onAllNodesWithTag("phase2-time-publication").assertCountEquals(0)
            compose.onAllNodesWithTag("phase2-time-summary").assertCountEquals(0)
            assertTrue(fixture.session.publications.value == null)
            assertEquals(0, fixture.port.receipts.size)

            compose.onNodeWithTag("phase2-time-reserve").performClick()
            compose.waitForIdle()
            compose.onNodeWithTag("phase2-time-publication").assertIsDisplayed()
            compose.onAllNodesWithTag("phase2-time-summary").assertCountEquals(0)
            assertEquals(1, fixture.port.commitCount)
            assertEquals(1, fixture.port.receipts.size)
            assertTrue(fixture.port.committedEvents.flatten().isEmpty())
        } finally {
            fixture.session.close()
        }
    }

    @Test
    fun testOnlyActivityRecreateWaitsForActiveSessionCloseBeforeCreatingNewWriter() = runBlocking {
        val fixtures = mutableListOf<ActualAdvanceFixture>()
        SessionBackedTestEntryHolder.prepare {
            ActualAdvanceFixture(this).also { fixture ->
                fixtures += fixture
            }.let { fixture ->
                val session = acquireSession(fixture.session)
                SessionBackedTestHandle(
                    session,
                    TimeAdvanceEntry(
                        TimeAdvanceViewState(TimeAdvanceStatus.ADVANCING, startRequest = advanceRequest()),
                        TimeAdvanceController(session, newCommandId = { CommandId("activity-session") })
                    )
                )
            }
        }
        val application = ApplicationProvider.getApplicationContext<Application>()
        val callbacks = SessionBackedActivityLifecycleCallbacks()
        application.registerActivityLifecycleCallbacks(callbacks)
        val scenario = ActivityScenario.launch(ComponentActivity::class.java)
        try {
            withTimeout(5_000) { SessionBackedTestEntryHolder.awaitCreatedCount(1) }
            val oldFixture = fixtures.single()
            val execution = async(start = CoroutineStart.UNDISPATCHED) {
                oldFixture.session.execute(oldFixture.envelope)
            }
            withTimeout(5_000) { oldFixture.port.firstSegmentEntered.await() }

            scenario.recreate()

            withTimeout(5_000) {
                while ("close-start:1" !in SessionBackedTestEntryHolder.events()) yield()
            }
            assertEquals(1, SessionBackedTestEntryHolder.sessions().size)
            assertEquals(
                listOf("created:1", "close-start:1"),
                SessionBackedTestEntryHolder.events()
            )

            oldFixture.port.releaseFirstSegment.complete(Unit)
            execution.await()
            withTimeout(5_000) { SessionBackedTestEntryHolder.awaitClosedCount(1) }
            withTimeout(5_000) { SessionBackedTestEntryHolder.awaitCreatedCount(2) }
            val sessions = SessionBackedTestEntryHolder.sessions()
            assertEquals(2, sessions.size)
            assertEquals(SessionLifecycle.CLOSED, sessions[0].runtimeState.value.lifecycle)
            assertEquals(SessionLifecycle.OPEN, sessions[1].runtimeState.value.lifecycle)
            assertEquals(
                listOf("created:1", "close-start:1", "close-complete:1", "created:2"),
                SessionBackedTestEntryHolder.events()
            )
        } finally {
            scenario.close()
            withTimeout(5_000) { SessionBackedTestEntryHolder.awaitClosedCount(2) }
            application.unregisterActivityLifecycleCallbacks(callbacks)
            SessionBackedTestEntryHolder.reset()
        }
    }

    @Test
    fun testOnlySessionReleaseIgnoresStaleAndDuplicateHandles() = runBlocking {
        var stale: SessionBackedTestHandle? = null
        val newFixtures = mutableListOf<SessionBackedScheduleFixture>()
        try {
            SessionBackedTestEntryHolder.prepare {
                SessionBackedScheduleFixture(this).let { fixture ->
                    val session = acquireSession(fixture.session)
                    SessionBackedTestHandle(
                        session,
                        TimeAdvanceEntry(
                            TimeAdvanceViewState(TimeAdvanceStatus.IDLE, scheduleReservation = reservation()),
                            TimeAdvanceController(session, newCommandId = { CommandId("stale-old") })
                        )
                    )
                }
            }
            stale = SessionBackedTestEntryHolder.awaitEntry()
            SessionBackedTestEntryHolder.close(stale!!)
            withTimeout(5_000) { SessionBackedTestEntryHolder.awaitClosedCount(1) }
            SessionBackedTestEntryHolder.reset()

            SessionBackedTestEntryHolder.prepare {
                SessionBackedScheduleFixture(this).also(newFixtures::add).let { fixture ->
                    val session = acquireSession(fixture.session)
                    SessionBackedTestHandle(
                        session,
                        TimeAdvanceEntry(
                            TimeAdvanceViewState(TimeAdvanceStatus.IDLE, scheduleReservation = reservation()),
                            TimeAdvanceController(session, newCommandId = { CommandId("stale-new") })
                        )
                    )
                }
            }
            val current = SessionBackedTestEntryHolder.awaitEntry()
            SessionBackedTestEntryHolder.close(checkNotNull(stale))
            assertEquals(SessionLifecycle.OPEN, current.session.runtimeState.value.lifecycle)
            SessionBackedTestEntryHolder.close(current)
            SessionBackedTestEntryHolder.close(current)
            withTimeout(5_000) { SessionBackedTestEntryHolder.awaitClosedCount(1) }
            assertEquals(listOf("created:1", "close-start:1", "close-complete:1"), SessionBackedTestEntryHolder.events())
        } finally {
            if (newFixtures.any { it.session.runtimeState.value.lifecycle != SessionLifecycle.CLOSED }) {
                newFixtures.forEach { it.session.close() }
            }
            SessionBackedTestEntryHolder.reset()
        }
    }

    @Test
    fun conflictMapperKeepsOnlyPublicPreviewFields() {
        val corePreview = CorePublicConsequencePreview(
            actionId = (EntityId.of("action-new") as Checked.Value).value,
            currentSchedules = listOf(PublicScheduleEffect((EntityId.of("existing") as Checked.Value).value, "treatment", PublicValueState.KNOWN)),
            proposedSchedule = PublicScheduleEffect((EntityId.of("action-new") as Checked.Value).value, "rescue", PublicValueState.KNOWN),
            cancelledSchedules = listOf(PublicScheduleEffect((EntityId.of("existing") as Checked.Value).value, "treatment", PublicValueState.UNDETERMINED)),
            refundAmount = CorePublicField(PublicValueState.UNKNOWN),
            lossAmount = CorePublicField(PublicValueState.UNKNOWN),
            resourceEffects = emptyList(),
            progressLoss = CorePublicField(PublicValueState.NOT_APPLICABLE),
            relationshipImpact = CorePublicField(PublicValueState.UNKNOWN),
            reputationImpact = CorePublicField(PublicValueState.UNKNOWN),
            rescheduleAvailability = CorePublicField(PublicValueState.KNOWN, "true")
        )
        val mapped = ScheduleConflictViewState.from(
            ScheduleConflictResult(
                conflictingActionIds = listOf((EntityId.of("existing") as Checked.Value).value),
                conflictingRowVersions = mapOf((EntityId.of("existing") as Checked.Value).value to StateVersion(7)),
                requestedPriority = CoreSchedulePriority.EMERGENCY_RESCUE,
                existingPriorities = listOf(CoreSchedulePriority.TREATMENT),
                allowedResolutions = setOf(CoreScheduleResolution.PREEMPT, CoreScheduleResolution.CANCEL_AND_INSERT),
                consequencePreview = corePreview
            ),
            reservation(),
            "public-conflict"
        )

        assertEquals("PublicConsequencePreview.v1", mapped.previewCodec)
        assertEquals(corePreview.hash.value, mapped.previewHash)
        assertEquals(listOf(ScheduleResolution.PREEMPT, ScheduleResolution.CANCEL_AND_INSERT), mapped.allowedResolutions)
        assertEquals(PublicValueStatus.UNKNOWN, mapped.preview.refundOrLoss.status)
        assertEquals(PublicValueStatus.UNKNOWN, mapped.preview.relationshipImpact.status)
        assertTrue(mapped.preview.fields.none { (_, field) -> field.value.contains("hidden", ignoreCase = true) })
    }

    @Test
    fun staleHashOrRowVersionRequestsRefreshWithoutExecute() = runBlocking {
        var executeCount = 0
        val controller = TimeAdvanceController(
            execute = { executeCount += 1; CommandResult.Accepted(1) },
            requestControl = { ControlRequestResult.Accepted(CommandId("active")) },
            runtimeState = { SessionRuntimeState(SessionEpoch(1), SessionLifecycle.OPEN, null) },
            currentVersion = { StateVersion(1) },
            newCommandId = { CommandId("command") }
        )
        val conflict = ScheduleConflictViewState(
            conflictId = "stale",
            previewToken = "token",
            previewCodec = "PublicConsequencePreview.v1",
            previewHash = EMPTY_HASH,
            expectedRowVersions = listOf(ExpectedScheduleRowVersion("action-1", 3)),
            reservation = reservation(),
            conflictingSchedules = listOf("Treatment"),
            allowedResolutions = listOf(ScheduleResolution.PREEMPT),
            preview = emptyPreview()
        )
        val action = TimeAdvanceUiAction.ConfirmConflict(
            conflictId = conflict.conflictId,
            resolution = ScheduleResolution.PREEMPT,
            expectedRowVersions = conflict.expectedRowVersions,
            reservation = conflict.reservation,
            previewCodec = conflict.previewCodec,
            previewHash = conflict.previewHash,
            previewToken = conflict.previewToken
        )

        assertEquals(
            TimeAdvanceDispatchResult.RefreshRequested("stale"),
            controller.dispatch(action.copy(previewHash = "1".repeat(64)), TimeAdvanceViewState(TimeAdvanceStatus.IDLE, conflict = conflict))
        )
        assertEquals(
            TimeAdvanceDispatchResult.RefreshRequested("stale"),
            controller.dispatch(action.copy(expectedRowVersions = listOf(ExpectedScheduleRowVersion("action-1", 4))), TimeAdvanceViewState(TimeAdvanceStatus.IDLE, conflict = conflict))
        )
        assertEquals(0, executeCount)
    }


    @Test
    fun decisionChoiceCarriesCanonicalSelectionAndNeverShowsPause() {
        val actions = mutableListOf<TimeAdvanceUiAction>()
        val canonicalPayload = "{\"id\":\"npc-1\"}"
        val payloadHash = DecisionSelection("gate-1", "choice-1", "DECISION_CHOICE.v1", canonicalPayload).payloadHash.value
        val choice = PublicDecisionChoice("choice-1", "Choose successor", "DECISION_CHOICE.v1", canonicalPayload, payloadHash)
        compose.setContent {
            TimeAdvanceScreen(
                TimeAdvanceViewState(
                    status = TimeAdvanceStatus.DECISION_REQUIRED,
                    decisionRequired = DecisionRequiredViewState(
                        "cmd-1",
                        "gate-1",
                        listOf(choice),
                        advanceRequest(),
                        1,
                        EMPTY_HASH
                    )
                ),
                actions::add
            )
        }

        compose.onAllNodesWithTag("phase2-time-control-pause").assertCountEquals(0)
        compose.onNodeWithTag("phase2-time-choice-choice-1").performClick()
        assertEquals(
            listOf(
                TimeAdvanceUiAction.Continue(
                    "cmd-1",
                    "gate-1",
                    "choice-1",
                    "DECISION_CHOICE.v1",
                    canonicalPayload,
                    payloadHash,
                    advanceRequest(),
                    1,
                    EMPTY_HASH,
                    null
                )
            ),
            actions
        )
    }

    @Test
    fun controllerBlocksDecisionChoiceWhenCommittedGateIsStale() = runBlocking {
        var executeCount = 0
        val choice = PublicDecisionChoice(
            "choice-1",
            "Choose successor",
            "DECISION_CHOICE.v1",
            "{\"id\":\"npc-1\"}",
            DecisionSelection("gate-1", "choice-1", "DECISION_CHOICE.v1", "{\"id\":\"npc-1\"}").payloadHash.value
        )
        val controller = TimeAdvanceController(
            execute = { executeCount++; CommandResult.Accepted(1) },
            requestControl = { ControlRequestResult.Accepted(CommandId("active")) },
            runtimeState = { SessionRuntimeState(SessionEpoch(1), SessionLifecycle.OPEN, null) },
            currentVersion = { StateVersion(1) },
            newCommandId = { CommandId("stale-child") },
            currentPublication = {
                TimeAdvanceCommittedSnapshot(
                    stateVersion = 1,
                    clockMinute = 1,
                    eventCount = 0,
                    sourceCommandId = "parent",
                    timeAdvanceTerminal = TimeAdvanceResult.DECISION_REQUIRED,
                    decisionGateId = "gate-1",
                    decisionChoices = listOf(choice),
                    predecessorEpoch = 1,
                    pendingSuffixHash = EMPTY_HASH,
                    sealedOutcomeHash = null
                )
            }
        )
        val result = controller.dispatch(
            TimeAdvanceUiAction.Continue(
                "parent",
                "wrong-gate",
                choice.choiceId,
                choice.codec,
                choice.canonicalPayload,
                choice.payloadHash,
                advanceRequest(),
                1,
                EMPTY_HASH,
                null
            )
        )
        assertEquals(TimeAdvanceDispatchResult.DecisionStale("parent"), result)
        assertEquals(0, executeCount)
    }

    @Test
    fun rejectsDecisionRequiredCombinedWithAdvanceInProgress() {
        val selection = DecisionSelection("gate-1", "choice-1", "DECISION_CHOICE.v1", "{}")
        try {
            TimeAdvanceViewState(
                status = TimeAdvanceStatus.DECISION_REQUIRED,
                decisionRequired = DecisionRequiredViewState(
                    "cmd-1",
                    "gate-1",
                    listOf(PublicDecisionChoice("choice-1", "Choose", "DECISION_CHOICE.v1", "{}", selection.payloadHash.value)),
                    advanceRequest(),
                    1,
                    EMPTY_HASH
                ),
                advanceInProgress = AdvanceInProgressViewState("Goal", "10:30", setOf(AdvanceControl.PAUSE))
            )
            throw AssertionError("Expected invalid state rejection")
        } catch (_: IllegalArgumentException) {
        }
    }

    @Test
    fun stalePreviewOffersRefreshAndDoesNotSubmitResolution() {
        val actions = mutableListOf<TimeAdvanceUiAction>()
        val preview = emptyPreview()
        compose.setContent {
            TimeAdvanceScreen(
                TimeAdvanceViewState(
                    status = TimeAdvanceStatus.IDLE,
                    conflict = ScheduleConflictViewState(
                        conflictId = "conflict-stale",
                        previewToken = "token",
                        previewCodec = "PUBLIC_CONSEQUENCE.v1",
                        previewHash = EMPTY_HASH,
                        expectedRowVersions = emptyList(),
                        reservation = reservation(),
                        conflictingSchedules = emptyList(),
                        allowedResolutions = listOf(ScheduleResolution.PREEMPT),
                        preview = preview,
                        previewStale = true
                    )
                ),
                actions::add
            )
        }

        compose.onNodeWithTag("phase2-time-refresh-preview").performClick()
        compose.onNodeWithTag("phase2-time-resolution-preempt").assertIsNotEnabled().performClick()
        assertEquals(listOf(TimeAdvanceUiAction.RefreshPreview("conflict-stale")), actions)
    }

    @Test
    fun controllerRoutesStartDecisionAndControlThroughCoreAuthorityApis() = kotlinx.coroutines.runBlocking {
        val envelopes = mutableListOf<CommandEnvelope<out WorldCommandPayload>>()
        val controls = mutableListOf<ControlRequest>()
        val commandIds = ArrayDeque(listOf(CommandId("cmd-start"), CommandId("cmd-child"), CommandId("cmd-resume"), CommandId("cmd-conflict")))
        val controller = TimeAdvanceController(
            execute = {
                envelopes += it
                CommandResult.Accepted(1)
            },
            requestControl = {
                controls += it
                ControlRequestResult.Accepted(it.expectedActiveCommandId)
            },
            runtimeState = {
                SessionRuntimeState(SessionEpoch(4), SessionLifecycle.OPEN, CommandId("cmd-active"))
            },
            currentVersion = { StateVersion(9) },
            newCommandId = { commandIds.removeFirst() }
        )
        val request = advanceRequest()
        val selection = DecisionSelection("gate-1", "choice-1", "DECISION_CHOICE.v1", "{\"id\":\"npc-1\"}")

        controller.dispatch(TimeAdvanceUiAction.StartAdvance(request))
        controller.dispatch(
            TimeAdvanceUiAction.Continue(
                continuationOfCommandId = "cmd-parent",
                gateId = selection.gateId,
                choiceId = selection.choiceId,
                codec = selection.codec,
                canonicalPayload = selection.canonicalPayload,
                payloadHash = selection.payloadHash.value,
                request = request,
                predecessorEpoch = 4,
                pendingSuffixHash = EMPTY_HASH,
                sealedOutcomeHash = null
            )
        )
        controller.dispatch(TimeAdvanceUiAction.Pause)
        controller.dispatch(TimeAdvanceUiAction.ResumeInterrupted("cmd-interrupted", request))
        val conflict = ScheduleConflictViewState(
            conflictId = "conflict-route",
            previewToken = "token-route",
            previewCodec = "PublicConsequencePreview.v1",
            previewHash = EMPTY_HASH,
            expectedRowVersions = listOf(ExpectedScheduleRowVersion("action-1", 7)),
            reservation = reservation(),
            conflictingSchedules = listOf("Training"),
            allowedResolutions = listOf(ScheduleResolution.PREEMPT),
            preview = emptyPreview()
        )
        val confirm = TimeAdvanceUiAction.ConfirmConflict(
            conflictId = conflict.conflictId,
            resolution = ScheduleResolution.PREEMPT,
            expectedRowVersions = conflict.expectedRowVersions,
            reservation = conflict.reservation,
            previewCodec = conflict.previewCodec,
            previewHash = conflict.previewHash,
            previewToken = conflict.previewToken
        )
        controller.dispatch(confirm, TimeAdvanceViewState(TimeAdvanceStatus.IDLE, conflict = conflict))

        assertEquals(listOf("cmd-start", "cmd-child", "cmd-resume", "cmd-conflict"), envelopes.map { it.commandId.value })
        assertEquals(listOf(StateVersion(9), StateVersion(9), StateVersion(9), StateVersion(9)), envelopes.map { it.expectedVersion })
        val decisionPayload = envelopes[1].payload as com.imsi.mud.simulation.AdvanceTimePayload
        assertEquals("gate-1", decisionPayload.continuation?.selection?.gateId)
        assertEquals(envelopes[1].commandId, decisionPayload.continuation?.childCommandId)
        assertEquals(null, decisionPayload.resumeOfCommandId)
        assertEquals("cmd-interrupted", (envelopes[2].payload as com.imsi.mud.simulation.AdvanceTimePayload).resumeOfCommandId?.value)
        val conflictPayload = envelopes[3].payload as ScheduleResolveConflictPayload
        assertEquals(StateVersion(7), conflictPayload.expectedRowVersions.values.single())
        assertEquals(EMPTY_HASH, conflictPayload.previewHash.value)
        assertEquals(conflict.reservation, conflictPayload.reservation)
        assertEquals(
            listOf(ControlRequest(SessionEpoch(4), CommandId("cmd-active"), CoreAdvanceControl.PAUSE, true)),
            controls
        )
        val stale = controller.dispatch(
            confirm,
            TimeAdvanceViewState(TimeAdvanceStatus.IDLE, conflict = conflict.copy(previewToken = "new-token"))
        )
        assertEquals(TimeAdvanceDispatchResult.RefreshRequested("conflict-route"), stale)
        assertEquals(4, envelopes.size)
    }

    @Test
    fun routeExecutesAdvanceOnceWhenStartIsTappedTwice() {
        var executeCount = 0
        val release = CompletableDeferred<CommandResult>()
        val controller = TimeAdvanceController(
            execute = {
                executeCount += 1
                release.await()
            },
            requestControl = { ControlRequestResult.Accepted(CommandId("cmd-active")) },
            runtimeState = { SessionRuntimeState(SessionEpoch(1), SessionLifecycle.OPEN, null) },
            currentVersion = { StateVersion(1) },
            newCommandId = { CommandId("cmd-start") }
        )
        compose.setContent {
            TimeAdvanceRoute(
                TimeAdvanceEntry(
                    state = TimeAdvanceViewState(
                        status = TimeAdvanceStatus.IDLE,
                        startRequest = advanceRequest()
                    ),
                    controller = controller
                )
            )
        }

        compose.onNodeWithTag("phase2-time-start")
            .assertHeightIsAtLeast(48.dp)
            .assertWidthIsAtLeast(48.dp)
            .performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("phase2-time-start")
            .assertIsNotEnabled()
            .performClick()
        assertEquals(1, executeCount)

        release.complete(CommandResult.Accepted(1))
        compose.waitForIdle()
        assertEquals(1, executeCount)
    }

    @Test
    fun acceptedAdvanceWithStaleTerminalPublicationDoesNotShowSuccessSummary() {
        val controller = TimeAdvanceController(
            execute = { CommandResult.Accepted(1) },
            requestControl = { ControlRequestResult.Accepted(CommandId("cmd-start")) },
            runtimeState = { SessionRuntimeState(SessionEpoch(1), SessionLifecycle.OPEN, null) },
            currentVersion = { StateVersion(1) },
            newCommandId = { CommandId("cmd-start") },
            currentPublication = {
                TimeAdvanceCommittedSnapshot(2, 20, 0, "other-command", TimeAdvanceResult.COMPLETED)
            }
        )
        compose.setContent {
            TimeAdvanceRoute(
                TimeAdvanceEntry(
                    TimeAdvanceViewState(TimeAdvanceStatus.IDLE, startRequest = advanceRequest()),
                    controller
                )
            )
        }

        compose.onNodeWithTag("phase2-time-start").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("phase2-time-status-failed").assertIsDisplayed()
        compose.onAllNodesWithTag("phase2-time-summary").assertCountEquals(0)
        compose.onAllNodesWithTag("phase2-time-retry").assertCountEquals(0)
    }

    @Test
    fun committedAdvanceWithoutSummaryCannotDispatchGameplayRetry() {
        var executeCount = 0
        val controller = TimeAdvanceController(
            execute = { executeCount++; CommandResult.Accepted(1) },
            requestControl = { ControlRequestResult.Accepted(CommandId("cmd-start")) },
            runtimeState = { SessionRuntimeState(SessionEpoch(1), SessionLifecycle.OPEN, null) },
            currentVersion = { StateVersion(1) },
            newCommandId = { CommandId("cmd-start") },
            currentPublication = {
                TimeAdvanceCommittedSnapshot(2, 20, 1, "cmd-start", TimeAdvanceResult.COMPLETED)
            }
        )
        compose.setContent {
            TimeAdvanceRoute(TimeAdvanceEntry(TimeAdvanceViewState(TimeAdvanceStatus.IDLE, startRequest = advanceRequest()), controller))
        }

        compose.onNodeWithTag("phase2-time-start").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("phase2-time-status-failed").assertIsDisplayed()
        compose.onAllNodesWithTag("phase2-time-summary").assertCountEquals(0)
        compose.onAllNodesWithTag("phase2-time-retry").assertCountEquals(0)
        assertEquals(1, executeCount)
    }

    @Test
    fun committedSummaryRefreshDoesNotExecuteGameplayAgain() {
        var executeCount = 0
        var refreshCount = 0
        var publication = TimeAdvanceCommittedSnapshot(2, 20, 1, "cmd-start", TimeAdvanceResult.COMPLETED)
        val controller = TimeAdvanceController(
            execute = { executeCount++; CommandResult.Accepted(1) },
            requestControl = { ControlRequestResult.Accepted(CommandId("cmd-start")) },
            runtimeState = { SessionRuntimeState(SessionEpoch(1), SessionLifecycle.OPEN, null) },
            currentVersion = { StateVersion(1) },
            newCommandId = { CommandId("cmd-start") },
            currentPublication = { publication },
            refreshSummary = {
                refreshCount++
                publication = publication.copy(timeAdvanceSummary = summaryProjection(TimeAdvanceResult.COMPLETED))
            }
        )
        compose.setContent {
            TimeAdvanceRoute(TimeAdvanceEntry(TimeAdvanceViewState(TimeAdvanceStatus.IDLE, startRequest = advanceRequest()), controller))
        }

        compose.onNodeWithTag("phase2-time-start").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("phase2-time-summary").assertIsDisplayed()
        compose.onAllNodesWithTag("phase2-time-retry").assertCountEquals(0)
        assertEquals(1, executeCount)
        assertEquals(1, refreshCount)
    }

    @Test
    fun routeAllowsPauseWhileAdvanceExecuteIsStillPending() {
        var executeCount = 0
        var controlCount = 0
        val release = CompletableDeferred<CommandResult>()
        val state = mutableStateOf(
            TimeAdvanceViewState(
                status = TimeAdvanceStatus.IDLE,
                startRequest = advanceRequest()
            )
        )
        val controller = TimeAdvanceController(
            execute = {
                executeCount += 1
                release.await()
            },
            requestControl = {
                controlCount += 1
                ControlRequestResult.Accepted(it.expectedActiveCommandId)
            },
            runtimeState = {
                SessionRuntimeState(SessionEpoch(1), SessionLifecycle.OPEN, CommandId("cmd-start"))
            },
            currentVersion = { StateVersion(1) },
            newCommandId = { CommandId("cmd-start") }
        )
        compose.setContent {
            TimeAdvanceRoute(TimeAdvanceEntry(state.value, controller))
        }

        compose.onNodeWithTag("phase2-time-start").performClick()
        compose.runOnIdle {
            state.value = TimeAdvanceViewState(
                status = TimeAdvanceStatus.ADVANCE_IN_PROGRESS,
                advanceInProgress = AdvanceInProgressViewState(
                    goalLabel = "Until treatment completes",
                    lastCommittedCursor = "10:30",
                    allowedControls = setOf(AdvanceControl.PAUSE, AdvanceControl.CANCEL)
                )
            )
        }
        compose.onNodeWithTag("phase2-time-progress").assertIsDisplayed()
        compose.onNodeWithTag("phase2-time-control-cancel").assertIsDisplayed()
        compose.onNodeWithTag("phase2-time-control-pause")
            .performClick()
        compose.waitForIdle()

        assertEquals(1, executeCount)
        assertEquals(1, controlCount)
        compose.onNodeWithTag("phase2-time-status-pause_requested").assertIsDisplayed()
        release.complete(CommandResult.Accepted(1))
        compose.waitForIdle()
    }

    @Test
    fun acceptedPauseStaysSingleFlightUntilCommittedSafeBoundary() {
        val activeCommand = CommandId("pause-command")
        var active = true
        var requestCount = 0
        var publication: TimeAdvanceCommittedSnapshot? = null
        val controller = TimeAdvanceController(
            execute = { CommandResult.Accepted(1) },
            requestControl = {
                requestCount += 1
                ControlRequestResult.Accepted(activeCommand)
            },
            runtimeState = {
                SessionRuntimeState(SessionEpoch(1), SessionLifecycle.OPEN, activeCommand.takeIf { active })
            },
            currentVersion = { StateVersion(1) },
            newCommandId = { activeCommand },
            currentPublication = { publication }
        )
        compose.setContent {
            TimeAdvanceRoute(
                TimeAdvanceEntry(
                    TimeAdvanceViewState(
                        status = TimeAdvanceStatus.ADVANCE_IN_PROGRESS,
                        startRequest = advanceRequest(),
                        advanceInProgress = AdvanceInProgressViewState(
                            "Time advance",
                            "10:00",
                            setOf(AdvanceControl.PAUSE, AdvanceControl.CANCEL)
                        )
                    ),
                    controller
                )
            )
        }

        compose.onNodeWithTag("phase2-time-control-pause").performClick()
        compose.waitUntil(5_000) { requestCount == 1 }
        compose.onNodeWithTag("phase2-time-control-pause")
            .assertIsNotEnabled()
            .performClick()
        compose.onNodeWithTag("phase2-time-control-feedback")
            .assertTextEquals("Pause request accepted. It will take effect at the next committed safe time boundary.")
        assertEquals(1, requestCount)

        active = false
        publication = TimeAdvanceCommittedSnapshot(
            2,
            20,
            1,
            activeCommand.value,
            TimeAdvanceResult.INTERRUPTED,
            timeAdvanceSummary = summaryProjection(TimeAdvanceResult.INTERRUPTED)
        )
        compose.waitUntil(5_000) {
            compose.onAllNodesWithTag("phase2-time-status-interrupted").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("phase2-time-control-feedback")
            .assertTextEquals("Pause applied at the next committed safe time boundary.")
        assertEquals(1, requestCount)
    }

    @Test
    fun acceptedCancelStaysSingleFlightUntilCommittedSafeBoundary() {
        val activeCommand = CommandId("cancel-command")
        var active = true
        var requestCount = 0
        var publication: TimeAdvanceCommittedSnapshot? = null
        val controller = TimeAdvanceController(
            execute = { CommandResult.Accepted(1) },
            requestControl = {
                requestCount += 1
                ControlRequestResult.Accepted(activeCommand)
            },
            runtimeState = {
                SessionRuntimeState(SessionEpoch(1), SessionLifecycle.OPEN, activeCommand.takeIf { active })
            },
            currentVersion = { StateVersion(1) },
            newCommandId = { activeCommand },
            currentPublication = { publication }
        )
        compose.setContent {
            TimeAdvanceRoute(
                TimeAdvanceEntry(
                    TimeAdvanceViewState(
                        status = TimeAdvanceStatus.ADVANCE_IN_PROGRESS,
                        startRequest = advanceRequest(),
                        advanceInProgress = AdvanceInProgressViewState(
                            "Time advance",
                            "10:00",
                            setOf(AdvanceControl.PAUSE, AdvanceControl.CANCEL)
                        )
                    ),
                    controller
                )
            )
        }

        compose.onNodeWithTag("phase2-time-control-cancel").performClick()
        compose.waitUntil(5_000) { requestCount == 1 }
        compose.onNodeWithTag("phase2-time-control-cancel")
            .assertIsNotEnabled()
            .performClick()
        compose.onNodeWithTag("phase2-time-control-feedback")
            .assertTextEquals("Cancellation request accepted. It will take effect at the next committed safe time boundary.")
        assertEquals(1, requestCount)

        active = false
        publication = TimeAdvanceCommittedSnapshot(
            2,
            20,
            1,
            activeCommand.value,
            TimeAdvanceResult.CANCELLED,
            timeAdvanceSummary = summaryProjection(TimeAdvanceResult.CANCELLED)
        )
        compose.waitUntil(5_000) {
            compose.onAllNodesWithTag("phase2-time-status-cancelled").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("phase2-time-control-feedback")
            .assertTextEquals("Cancellation applied at the next committed safe time boundary.")
        assertEquals(1, requestCount)
    }

    @Test
    fun routeUsesWorldSessionFastForwardAndShowsEveryCrossedBoundary() = runBlocking {
        val fixture = ActualAdvanceFixture(this)
        try {
            compose.setContent {
                TimeAdvanceRoute(
                    TimeAdvanceEntry(
                        TimeAdvanceViewState(
                            status = TimeAdvanceStatus.IDLE,
                            startRequest = advanceRequest().copy(
                                goal = TimeAdvanceGoal.UntilMinute(actualMinute(200)),
                                limits = TimeTraversalLimits(actualMinute(200), 200)
                            )
                        ),
                        TimeAdvanceController(
                            fixture.session,
                            newCommandId = { fixture.commandId }
                        )
                    )
                )
            }

            compose.onNodeWithTag("phase2-time-start").performClick()
            withTimeout(5_000) { fixture.port.firstSegmentEntered.await() }
            compose.onNodeWithTag("phase2-time-progress").assertIsDisplayed()

            fixture.port.releaseFirstSegment.complete(Unit)
            compose.waitUntil(5_000) {
                compose.onAllNodesWithTag("phase2-time-summary").fetchSemanticsNodes().isNotEmpty()
            }

            compose.onNodeWithTag("phase2-time-publication").assertIsDisplayed()
            compose.onNodeWithTag("phase2-time-status-completed").assertIsDisplayed()
            compose.onNodeWithText("Elapsed: 200 minutes").assertIsDisplayed()
            compose.onNodeWithText("Next: Acknowledge").assertIsDisplayed()
            val receipt = fixture.port.receipts.values.single()
            assertEquals(TimeAdvanceResult.COMPLETED, receipt.timeAdvanceState?.status)
            assertEquals(TimeAdvanceResult.COMPLETED, fixture.session.publications.value?.timeAdvanceTerminal?.result)
            assertEquals(200L, fixture.session.publications.value?.timeAdvanceSummary?.elapsedMinutes)
            assertEquals(TimeAdvanceResult.COMPLETED, fixture.session.publications.value?.timeAdvanceSummary?.terminalReason)
            assertEquals(PublicTimeAdvanceNextAction.ACKNOWLEDGE, fixture.session.publications.value?.timeAdvanceSummary?.nextAction)
            assertEquals(fixture.commandId, fixture.session.publications.value?.sourceCommandId)
            assertEquals(200, receipt.timeAdvanceState?.processedBoundaryCount)
            val events = fixture.port.committedEvents.flatten()
            assertEquals((1L..200L).toList(), events.map { it.gameMinute.value })
            assertEquals(events.size, events.map { it.eventId }.distinct().size)
            assertTrue(events.all { it.sourceCommandId == fixture.commandId })
        } finally {
            fixture.session.close()
        }
    }

    @Test
    fun rendersTerminalTimeAdvanceStatusesWithPublicContinuationState() {
        val state = mutableStateOf(
            TimeAdvanceViewState(
                status = TimeAdvanceStatus.INTERRUPTED,
                continuationOfCommandId = "interrupted-command",
                resumeRequest = advanceRequest()
            )
        )
        compose.setContent { TimeAdvanceScreen(state.value, onAction = {}) }

        compose.onNodeWithTag("phase2-time-status-interrupted").assertIsDisplayed()
        compose.onNodeWithTag("phase2-time-continue").assertIsDisplayed()

        listOf(
            TimeAdvanceStatus.UNREACHABLE,
            TimeAdvanceStatus.LIMIT_REACHED,
            TimeAdvanceStatus.CANCELLED
        ).forEach { status ->
            compose.runOnIdle { state.value = TimeAdvanceViewState(status = status) }
            compose.waitForIdle()
            compose.onNodeWithTag("phase2-time-status-${status.name.lowercase()}").assertIsDisplayed()
        }
    }

    @Test
    fun mapsOnlyMatchingCommittedTerminalResultsToEveryTimeAdvanceStatus() {
        val command = CommandId("advance-command")
        val other = CommandId("other-command")
        val expected = mapOf(
            TimeAdvanceResult.COMPLETED to TimeAdvanceStatus.COMPLETED,
            TimeAdvanceResult.INTERRUPTED to TimeAdvanceStatus.INTERRUPTED,
            TimeAdvanceResult.DECISION_REQUIRED to TimeAdvanceStatus.DECISION_REQUIRED,
            TimeAdvanceResult.CANCELLED to TimeAdvanceStatus.CANCELLED,
            TimeAdvanceResult.UNREACHABLE to TimeAdvanceStatus.UNREACHABLE,
            TimeAdvanceResult.LIMIT_REACHED to TimeAdvanceStatus.LIMIT_REACHED,
            TimeAdvanceResult.FAILED to TimeAdvanceStatus.FAILED
        )
        expected.forEach { (terminal, status) ->
            val publication = TimeAdvanceCommittedSnapshot(1, 1, 0, command.value, terminal)
            assertEquals(terminal, publication.timeAdvanceTerminalFor(command))
            assertEquals(status, terminal.uiStatus())
            assertEquals(null, publication.timeAdvanceTerminalFor(other))
        }
    }

    @Test
    fun realWorldSessionControlLanePausesAndCancelsActiveAdvance() = runBlocking {
        listOf(
            TimeAdvanceUiAction.Pause to TimeAdvanceResult.INTERRUPTED,
            TimeAdvanceUiAction.Cancel to TimeAdvanceResult.CANCELLED
        ).forEach { (action, terminalStatus) ->
            val fixture = ActualAdvanceFixture(this)
            val execution = async(start = CoroutineStart.UNDISPATCHED) {
                fixture.session.execute(fixture.envelope)
            }
            withTimeout(5_000) { fixture.port.firstSegmentEntered.await() }

            val dispatch = TimeAdvanceController(fixture.session).dispatch(action)
            assertEquals(
                TimeAdvanceDispatchResult.Control(ControlRequestResult.Accepted(fixture.commandId)),
                dispatch
            )

            fixture.port.releaseFirstSegment.complete(Unit)
            assertEquals(CommandResult.Accepted(1), execution.await())
            val receipt = fixture.port.receipts.values.single()
            assertEquals(terminalStatus, receipt.timeAdvanceState?.status)
            assertEquals(1, fixture.port.receipts.size)
            val events = fixture.port.committedEvents.flatten()
            assertTrue(events.isNotEmpty())
            assertEquals(events.size, events.map { it.eventId }.distinct().size)
            assertTrue(events.all { it.sourceCommandId == fixture.commandId })
            assertEquals(null, fixture.session.runtimeState.value.activeAdvanceCommandId)
            fixture.session.close()
        }
    }

    @Test
    fun sessionBackedControlCommitFailureLeavesErrorAndRetryCanComplete() = runBlocking {
        val fixture = ActualAdvanceFixture(this)
        var retryId = 0
        try {
            fixture.port.failNextSegmentCommit = true
            val controller = TimeAdvanceController(
                fixture.session,
                newCommandId = { CommandId("control-retry-${++retryId}") }
            )
            compose.setContent {
                TimeAdvanceRoute(
                    TimeAdvanceEntry(
                        TimeAdvanceViewState(TimeAdvanceStatus.IDLE, startRequest = advanceRequest()),
                        controller
                    )
                )
            }

            compose.onNodeWithTag("phase2-time-start").performClick()
            withTimeout(5_000) { fixture.port.firstSegmentEntered.await() }
            compose.onNodeWithTag("phase2-time-control-pause").performClick()
            compose.waitUntil(5_000) {
                compose.onAllNodesWithTag("phase2-time-control-feedback").fetchSemanticsNodes().isNotEmpty()
            }

            fixture.port.releaseFirstSegment.complete(Unit)
            compose.waitUntil(10_000) {
                compose.onAllNodesWithTag("phase2-time-retry").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithTag("phase2-time-error")
                .assertTextEquals("The control request was accepted, but its commit could not be confirmed. Retry the time advance.")
            compose.onAllNodesWithTag("phase2-time-publication").assertCountEquals(0)
            compose.onAllNodesWithTag("phase2-time-summary").assertCountEquals(0)

            compose.onNodeWithTag("phase2-time-retry").performClick()
            compose.waitUntil(10_000) {
                compose.onAllNodesWithTag("phase2-time-summary").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithTag("phase2-time-status-completed").assertIsDisplayed()
            compose.onNodeWithTag("phase2-time-publication").assertIsDisplayed()
            assertTrue(fixture.session.publications.value != null)
        } finally {
            fixture.session.close()
        }
    }

    @Test
    fun slowSessionBackedControlStaysProcessingPastTimeoutThenPublishes() = runBlocking {
        val fixture = ActualAdvanceFixture(this)
        var released = false
        try {
            val controller = TimeAdvanceController(
                fixture.session,
                newCommandId = { CommandId("slow-control") }
            )
            compose.setContent {
                TimeAdvanceRoute(
                    TimeAdvanceEntry(
                        TimeAdvanceViewState(TimeAdvanceStatus.IDLE, startRequest = advanceRequest()),
                        controller
                    )
                )
            }

            compose.onNodeWithTag("phase2-time-start").performClick()
            withTimeout(5_000) { fixture.port.firstSegmentEntered.await() }
            compose.onNodeWithTag("phase2-time-control-pause").performClick()
            compose.waitUntil(5_000) {
                compose.onAllNodesWithTag("phase2-time-control-feedback").fetchSemanticsNodes().isNotEmpty()
            }

            compose.waitUntil(7_000) {
                compose.onAllNodesWithText("Processing. Waiting for the next committed safe time boundary.")
                    .fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithText("Processing. Waiting for the next committed safe time boundary.").assertIsDisplayed()
            compose.onAllNodesWithTag("phase2-time-retry").assertCountEquals(0)
            compose.onNodeWithTag("phase2-time-control-pause").assertIsNotEnabled()

            fixture.port.releaseFirstSegment.complete(Unit)
            released = true
            compose.waitUntil(10_000) {
                compose.onAllNodesWithTag("phase2-time-summary").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithTag("phase2-time-publication").assertIsDisplayed()
            assertEquals(1, fixture.port.receipts.keys.map { it.second }.distinct().size)
            val committedEvents = fixture.port.committedEvents.flatten()
            assertEquals(committedEvents.size, committedEvents.map { it.eventId }.distinct().size)
        } finally {
            if (!released) fixture.port.releaseFirstSegment.complete(Unit)
            fixture.session.close()
        }
    }

    @Test
    fun mainActivityContentEntersPhase2RouteWhenEntryIsProvided() {
        val controller = TimeAdvanceController(
            execute = { CommandResult.Accepted(1) },
            requestControl = { ControlRequestResult.Accepted(CommandId("cmd-active")) },
            runtimeState = { SessionRuntimeState(SessionEpoch(1), SessionLifecycle.OPEN, null) },
            currentVersion = { StateVersion(1) },
            newCommandId = { CommandId("cmd-start") }
        )
        compose.setContent {
            MainActivityContent(
                timeAdvanceEntry = TimeAdvanceEntry(
                    state = TimeAdvanceViewState(TimeAdvanceStatus.IDLE),
                    controller = controller
                ),
                onBack = {}
            )
        }

        compose.onNodeWithTag("phase2-time-screen").assertIsDisplayed()
        compose.onAllNodesWithTag("app-shell-ready-title").assertCountEquals(0)
    }

    @Test
    fun androidPcg32MatchesReferenceVectorAndDrawCounter() {
        var stream = DeterministicRng.initialize(42, 54, RngStreamKey("reference/android"))
        val values = buildList {
            repeat(6) {
                val outcome = (DeterministicRng.draw(stream, RngOperation.NextUInt32) as Checked.Value).value
                add(outcome.value.toString(16).padStart(8, '0'))
                stream = outcome.stream
            }
        }

        assertEquals(listOf("a15c02b7", "7b47f409", "ba1d3330", "83d2f293", "bfa4784b", "cbed606e"), values)
        assertEquals(6, stream.drawCounter)
    }

    private suspend fun runSessionBackedConflictResolution(
        scope: CoroutineScope,
        resolution: ScheduleResolution
    ) {
        val reservation = reservation().copy(startMinute = actualMinute(20), dueMinute = actualMinute(40))
        val calendar = ScheduleCalendar(listOf(existingScheduleAction()), emptyMap())
        val details = conflictDetails(calendar, reservation)
        val fixture = SessionBackedScheduleFixture(scope, calendar)
        try {
            val conflict = ScheduleConflictViewState.from(details, reservation, "session-$resolution")
            val controller = TimeAdvanceController(
                fixture.session,
                newCommandId = { CommandId("session-$resolution") }
            )
            compose.setContent {
                TimeAdvanceRoute(
                    TimeAdvanceEntry(
                        TimeAdvanceViewState(TimeAdvanceStatus.IDLE, conflict = conflict),
                        controller
                    )
                )
            }

            val tag = "phase2-time-resolution-${resolution.name.lowercase()}"
            compose.onNodeWithTag("phase2-time-screen")
                .performScrollToNode(hasTestTag(tag))
            compose.onNodeWithTag(tag).performClick()
            val label = resolution.name.replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() }
            compose.waitUntil(5_000) {
                compose.onAllNodesWithText("Confirm $label").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithTag(tag).assertTextEquals("Confirm $label").performClick()
            compose.waitForIdle()

            compose.onNodeWithTag("phase2-time-publication").assertIsDisplayed()
            compose.onAllNodesWithTag("phase2-time-summary").assertCountEquals(0)
            assertEquals(1, fixture.port.commitCount)
            assertEquals(1, fixture.port.receipts.size)
            assertTrue(fixture.port.committedEvents.flatten().isEmpty())
            assertTrue(fixture.session.publications.value != null)
        } finally {
            fixture.session.close()
        }
    }

    private fun conflictDetails(
        calendar: ScheduleCalendar,
        reservation: ScheduleReservePayload
    ): ScheduleConflictResult = when (
        val result = ScheduleService().reserve(
            ReservationRequest(
                reservation.actionId,
                reservation.actionKind,
                reservation.scheduledPayload,
                reservation.startMinute,
                reservation.dueMinute,
                reservation.effectiveMinute
            ),
            calendar
        )
    ) {
        is ReservationResult.Conflict -> result.details
        else -> error("expected a schedule conflict")
    }

    private fun existingScheduleAction(): ScheduledAction = ScheduledAction(
        actionId = (EntityId.of("action-existing") as Checked.Value<EntityId>).value,
        actionKind = "schedule.existing",
        payload = reservation().scheduledPayload.copy(
            schedulePriority = CoreSchedulePriority.TREATMENT,
            canBePreempted = true
        ),
        startMinute = actualMinute(10),
        dueMinute = actualMinute(30),
        status = ScheduledActionStatus.RESERVED,
        rowVersion = StateVersion(3)
    )

    private fun confirmConflictAction(conflict: ScheduleConflictViewState): TimeAdvanceUiAction.ConfirmConflict =
        TimeAdvanceUiAction.ConfirmConflict(
            conflictId = conflict.conflictId,
            resolution = conflict.allowedResolutions.first(),
            expectedRowVersions = conflict.expectedRowVersions,
            reservation = conflict.reservation,
            previewCodec = conflict.previewCodec,
            previewHash = conflict.previewHash,
            previewToken = conflict.previewToken
        )

    private fun emptyPreview() = PublicConsequencePreview(
        currentSchedule = PublicConsequenceField("Current schedule", "No impact", PublicValueStatus.NONE),
        proposedSchedule = PublicConsequenceField("New schedule", "No impact", PublicValueStatus.NONE),
        cancelledSchedule = PublicConsequenceField("Cancelled schedule", "No impact", PublicValueStatus.NONE),
        refundOrLoss = PublicConsequenceField("Refund or loss", "No impact", PublicValueStatus.NONE),
        resourceSettlement = PublicConsequenceField("Resources", "No impact", PublicValueStatus.NONE),
        progressLoss = PublicConsequenceField("Progress loss", "No impact", PublicValueStatus.NONE),
        relationshipImpact = PublicConsequenceField("Relationship", "No impact", PublicValueStatus.NONE),
        reputationImpact = PublicConsequenceField("Reputation", "No impact", PublicValueStatus.NONE),
        rescheduleAvailability = PublicConsequenceField("Reschedule", "Not applicable", PublicValueStatus.NOT_APPLICABLE)
    )

    private class DecisionGateSessionFixture(scope: CoroutineScope) {
        val port = FaultInjectingScheduleSavePort()
        private val source = DecisionGateSource()
        val session = WorldSession(
            SessionEpoch(1),
            port,
            scope,
            ContentSnapshot.emptyForTest(),
            WorldSnapshot(
                StateVersion(0),
                AuthoritativeWorldState(
                    WorldClock(actualMinute(0)),
                    ScheduleCalendar(emptyList(), emptyMap()),
                    null,
                    BoundaryRegistryBinding(
                        1,
                        listOf(source.sourceId),
                        candidateCodecs = setOf("CalendarBoundaryPayload.v1")
                    )
                ),
                RngState(emptyList()),
                emptyMap()
            ),
            WorldEngine(
                listOf(source),
                selectionEvaluator = com.imsi.mud.simulation.DecisionSelectionEvaluator { snapshot, _ -> snapshot }
            ),
            Dispatchers.Unconfined
        )
    }

    private class DecisionGateSource : BoundarySource {
        override val sourceId: String = "android-qa-decision-gate"

        override fun nextTimeAfter(
            snapshot: com.imsi.mud.simulation.WorldTraversalSnapshot,
            cursor: BoundaryCursor?
        ): GameMinute? = if (cursor == null) actualMinute(1) else null

        override fun candidatesAt(
            snapshot: com.imsi.mud.simulation.WorldTraversalSnapshot,
            time: GameMinute
        ): List<BoundaryCandidate> = listOf(
            BoundaryCandidate(
                BoundaryKey(time, BoundaryCategory.WORLD_EVENT, 0, 0, "gate-1", "", sourceId),
                "decision.gate.v1",
                "CalendarBoundaryPayload.v1",
                "{\"minute\":1}",
                disposition = com.imsi.mud.simulation.BoundaryDisposition.DECISION_GATE,
                decisionChoiceIds = listOf("A", "B")
            )
        )
    }

    private class SessionBackedScheduleFixture(
        scope: CoroutineScope,
        calendar: ScheduleCalendar = ScheduleCalendar(emptyList(), emptyMap())
    ) {
        val port = FaultInjectingScheduleSavePort()
        val session = WorldSession(
            SessionEpoch(1),
            port,
            scope,
            ContentSnapshot.emptyForTest(),
            WorldSnapshot(
                StateVersion(0),
                AuthoritativeWorldState(
                    WorldClock(actualMinute(0)),
                    calendar,
                    null,
                    BoundaryRegistryBinding(1, emptyList())
                ),
                RngState(emptyList()),
                emptyMap()
            ),
            WorldEngine(emptyList()),
            Dispatchers.Unconfined
        )
    }

    private class FaultInjectingScheduleSavePort : SavePort {
        val receipts = linkedMapOf<Pair<SessionEpoch, CommandId>, PersistedReceipt>()
        val committedEvents = mutableListOf<List<DomainEvent<out DomainEventPayload>>>()
        var commitCount = 0
            private set
        var failNextCommit = false
        var commitEntered = CompletableDeferred<Unit>()
            private set
        var releaseCommit = CompletableDeferred<Unit>()
            private set
        private var blockNext = false
        private var stateVersion = StateVersion(0)

        fun blockNextCommit() {
            check(!blockNext) { "commit is already blocked" }
            blockNext = true
            commitEntered = CompletableDeferred()
            releaseCommit = CompletableDeferred()
        }

        override suspend fun findReceipt(sessionEpoch: SessionEpoch, commandId: CommandId): PersistedReceipt? =
            receipts[sessionEpoch to commandId]

        override suspend fun commit(
            envelope: CommandEnvelope<out WorldCommandPayload>,
            delta: DomainDelta
        ): CommitReceipt {
            receipts[envelope.sessionEpoch to envelope.commandId]?.let { existing ->
                return CommitReceipt(existing.stateVersion, existing.result, existing.lifecycleStatus, existing.lastCommittedSegmentNo)
            }
            if (blockNext) {
                blockNext = false
                commitEntered.complete(Unit)
                releaseCommit.await()
            }
            if (failNextCommit) {
                failNextCommit = false
                error("injected schedule commit failure")
            }
            if (delta.result is CommandResult.Accepted) {
                stateVersion = StateVersion(stateVersion.value + 1)
            }
            val receipt = CommitReceipt(stateVersion, delta.result)
            receipts[envelope.sessionEpoch to envelope.commandId] = PersistedReceipt(
                envelope.payloadHash,
                receipt.stateVersion,
                receipt.result,
                receipt.lifecycleStatus,
                receipt.lastCommittedSegmentNo
            )
            committedEvents += delta.events
            commitCount++
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
            receipts[envelope.sessionEpoch to envelope.commandId]?.let { existing ->
                if (existing.lastCommittedSegmentNo >= expectedSegmentNo) {
                    return SegmentCommitReceipt(
                        CommitReceipt(existing.stateVersion, existing.result, existing.lifecycleStatus, existing.lastCommittedSegmentNo),
                        existing.lastCommittedSegmentNo,
                        timeAdvanceState
                    )
                }
            }
            if (delta.result is CommandResult.Accepted) stateVersion = StateVersion(stateVersion.value + 1)
            val lifecycle = when (terminalResult) {
                null -> ReceiptLifecycle.RUNNING
                TimeAdvanceResult.INTERRUPTED, TimeAdvanceResult.DECISION_REQUIRED, TimeAdvanceResult.FAILED -> ReceiptLifecycle.INTERRUPTED
                else -> ReceiptLifecycle.COMMITTED
            }
            val receipt = CommitReceipt(stateVersion, delta.result, lifecycle, expectedSegmentNo)
            receipts[envelope.sessionEpoch to envelope.commandId] = PersistedReceipt(
                envelope.payloadHash,
                receipt.stateVersion,
                receipt.result,
                lifecycle,
                expectedSegmentNo,
                timeAdvanceState,
                continuation
            )
            committedEvents += delta.events
            commitCount++
            return SegmentCommitReceipt(receipt, expectedSegmentNo, timeAdvanceState)
        }
    }

    private class ActualAdvanceFixture(scope: kotlinx.coroutines.CoroutineScope) {
        val source = SequentialAdvanceSource(200)
        val port = AndroidControlledAdvancePort()
        val session = WorldSession(
            SessionEpoch(1),
            port,
            scope,
            ContentSnapshot.emptyForTest(),
            WorldSnapshot(
                StateVersion(0),
                AuthoritativeWorldState(
                    WorldClock(actualMinute(0)),
                    ScheduleCalendar(emptyList(), emptyMap()),
                    null,
                    BoundaryRegistryBinding(1, listOf(source.sourceId), candidateCodecs = setOf("CalendarBoundaryPayload.v1"))
                ),
                RngState(emptyList()),
                emptyMap()
            ),
            WorldEngine(listOf(source)),
            Dispatchers.Unconfined
        )
        val commandId = CommandId("android-actual-advance")
        val envelope = CommandEnvelope.create(
            commandId,
            SessionEpoch(1),
            StateVersion(0),
            null,
            AdvanceTimePayload(
                TimeAdvanceGoal.UntilMinute(actualMinute(200)),
                ProgressionMode.FAST_FORWARD,
                TimeTraversalLimits(actualMinute(200), maxBoundaryCount = 200)
            )
        )
    }

    private class SequentialAdvanceSource(private val lastMinute: Long) : BoundarySource {
        override val sourceId: String = "android-qa-active-advance"

        override fun nextTimeAfter(snapshot: com.imsi.mud.simulation.WorldTraversalSnapshot, cursor: com.imsi.mud.simulation.BoundaryCursor?): GameMinute? =
            (snapshot.clock.minute.value + 1).takeIf { it <= lastMinute }?.let(::actualMinute)

        override fun candidatesAt(snapshot: com.imsi.mud.simulation.WorldTraversalSnapshot, time: GameMinute): List<BoundaryCandidate> = listOf(
            BoundaryCandidate(
                BoundaryKey(time, BoundaryCategory.WORLD_EVENT, 0, 0, "event-${time.value}", "", sourceId),
                "calendar.day.start.v1",
                "CalendarBoundaryPayload.v1",
                "{\"minute\":${time.value}}"
            )
        )
    }

    private class AndroidControlledAdvancePort : SavePort {
        val firstSegmentEntered = CompletableDeferred<Unit>()
        val releaseFirstSegment = CompletableDeferred<Unit>()
        val receipts = mutableMapOf<Pair<SessionEpoch, CommandId>, PersistedReceipt>()
        val committedEvents = mutableListOf<List<DomainEvent<out DomainEventPayload>>>()
        var failNextSegmentCommit = false
        private var stateVersion = 0L

        override suspend fun findReceipt(sessionEpoch: SessionEpoch, commandId: CommandId): PersistedReceipt? =
            receipts[sessionEpoch to commandId]

        override suspend fun commit(
            envelope: CommandEnvelope<out WorldCommandPayload>,
            delta: DomainDelta
        ): CommitReceipt = error("atomic commit was not expected")

        override suspend fun commitSegment(
            envelope: CommandEnvelope<out WorldCommandPayload>,
            expectedSegmentNo: Int,
            delta: DomainDelta,
            timeAdvanceState: TimeAdvanceState,
            terminalResult: TimeAdvanceResult?,
            continuation: TimeAdvanceContinuation?
        ): SegmentCommitReceipt {
            if (expectedSegmentNo == 0) {
                firstSegmentEntered.complete(Unit)
                releaseFirstSegment.await()
            }
            if (failNextSegmentCommit) {
                failNextSegmentCommit = false
                error("injected control commit failure")
            }
            committedEvents += delta.events
            val lifecycle = when (terminalResult) {
                null -> ReceiptLifecycle.RUNNING
                TimeAdvanceResult.INTERRUPTED, TimeAdvanceResult.DECISION_REQUIRED, TimeAdvanceResult.FAILED -> ReceiptLifecycle.INTERRUPTED
                else -> ReceiptLifecycle.COMMITTED
            }
            val receipt = CommitReceipt(StateVersion(++stateVersion), delta.result, lifecycle, expectedSegmentNo)
            receipts[envelope.sessionEpoch to envelope.commandId] = PersistedReceipt(
                envelope.payloadHash,
                receipt.stateVersion,
                receipt.result,
                lifecycle,
                expectedSegmentNo,
                timeAdvanceState,
                continuation
            )
            return SegmentCommitReceipt(receipt, expectedSegmentNo, timeAdvanceState)
        }
    }

    private fun advanceRequest() = TimeAdvanceRequest(
        goal = TimeAdvanceGoal.UntilMinute((GameMinute.of(60) as Checked.Value).value),
        mode = ProgressionMode.FAST_FORWARD,
        limits = TimeTraversalLimits((GameMinute.of(120) as Checked.Value).value, 100),
        interruptPolicy = TimeAdvanceInterruptPolicy()
    )

    private fun summaryProjection(result: TimeAdvanceResult): TimeAdvanceSummaryView = TimeAdvanceSummaryView(
        elapsedMinutes = 20,
        terminalReason = result,
        majorEvents = emptyList(),
        majorEventsOverflowCount = 0,
        completedWork = emptyList(),
        completedWorkOverflowCount = 0,
        resourceWarnings = emptyList(),
        resourceWarningsOverflowCount = 0,
        importantChanges = emptyList(),
        importantChangesOverflowCount = 0,
        lowImportanceBundles = emptyList(),
        lowImportanceBundlesOverflowCount = 0,
        unknownImportantEventCount = 0,
        continuation = when (result) {
            TimeAdvanceResult.INTERRUPTED -> PublicTimeAdvanceContinuation.RESUME
            TimeAdvanceResult.DECISION_REQUIRED -> PublicTimeAdvanceContinuation.DECISION
            else -> null
        },
        nextAction = when (result) {
            TimeAdvanceResult.INTERRUPTED -> PublicTimeAdvanceNextAction.CONTINUE
            TimeAdvanceResult.DECISION_REQUIRED -> PublicTimeAdvanceNextAction.CHOOSE_DECISION
            TimeAdvanceResult.FAILED -> PublicTimeAdvanceNextAction.RETRY
            else -> PublicTimeAdvanceNextAction.ACKNOWLEDGE
        }
    )

    private fun reservation(): ScheduleReservePayload {
        val owner = (EntityId.of("player-1") as Checked.Value<EntityId>).value
        val cancellation = ActionCancellationPolicy(
            CancellationStage.entries.associateWith { ActionCancellationRule(10_000, 0, true) }
        )
        return ScheduleReservePayload(
            actionId = (EntityId.of("action-new") as Checked.Value<EntityId>).value,
            actionKind = "schedule.test",
            scheduledPayload = ScheduledActionPayload(
                ownerType = "PLAYER",
                ownerId = owner,
                participants = listOf(ScheduledEntityRef("PLAYER", owner)),
                schedulePriority = CoreSchedulePriority.EMERGENCY_RESCUE,
                resourceClaims = emptyList(),
                actionKindPolicy = ActionKindPolicyProfile(
                    resumable = true,
                    progressBasis = "MINUTE",
                    interruptionPolicy = ActionInterruptionPolicy(ActionInterruptionResult.CONTINUE),
                    cancellationPolicy = cancellation,
                    consequenceEventCodec = "schedule.test.v1"
                ),
                completionEventType = "schedule.test.complete",
                completionEventCodec = "schedule.test.v1",
                canBePreempted = true
            ),
            startMinute = (GameMinute.of(100) as Checked.Value).value,
            dueMinute = (GameMinute.of(120) as Checked.Value).value,
            effectiveMinute = (GameMinute.of(0) as Checked.Value).value
        )
    }

    private companion object {
        val EMPTY_HASH: String = PayloadHash("0".repeat(64)).value
    }
}

class SessionBackedActivityErrorUiTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    @Test
    fun testOnlyActivityCanRetryAfterSessionSetupError() = runBlocking {
        SessionBackedTestEntryHolder.prepare { error("test-only setup failure") }
        SessionBackedTestEntryHolder.failNextEntry()
        val application = ApplicationProvider.getApplicationContext<Application>()
        val callbacks = SessionBackedActivityLifecycleCallbacks()
        application.registerActivityLifecycleCallbacks(callbacks)
        val scenario = ActivityScenario.launch(ComponentActivity::class.java)
        try {
            compose.waitUntil(5_000) {
                compose.onAllNodesWithTag("app-shell-error-title").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithTag("app-shell-error-body")
                .assertTextEquals("We couldn't complete this action. Please try again.")
            compose.onNodeWithTag("app-shell-retry").performClick()
            compose.waitUntil(5_000) {
                compose.onAllNodesWithTag("app-shell-error-title").fetchSemanticsNodes().isNotEmpty()
            }
        } finally {
            scenario.close()
            application.unregisterActivityLifecycleCallbacks(callbacks)
            SessionBackedTestEntryHolder.reset()
        }
    }
}
