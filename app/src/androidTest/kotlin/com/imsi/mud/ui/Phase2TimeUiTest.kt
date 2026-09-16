package com.imsi.mud.ui

import androidx.compose.runtime.mutableStateOf
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
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.imsi.mud.simulation.AdvanceControl as CoreAdvanceControl
import com.imsi.mud.simulation.ActionCancellationPolicy
import com.imsi.mud.simulation.ActionCancellationRule
import com.imsi.mud.simulation.ActionInterruptionPolicy
import com.imsi.mud.simulation.ActionInterruptionResult
import com.imsi.mud.simulation.ActionKindPolicyProfile
import com.imsi.mud.simulation.CancellationStage
import com.imsi.mud.simulation.Checked
import com.imsi.mud.simulation.CommandEnvelope
import com.imsi.mud.simulation.CommandId
import com.imsi.mud.simulation.CommandResult
import com.imsi.mud.simulation.ControlRequest
import com.imsi.mud.simulation.ControlRequestResult
import com.imsi.mud.simulation.DecisionSelection
import com.imsi.mud.simulation.DeterministicRng
import com.imsi.mud.simulation.EntityId
import com.imsi.mud.simulation.GameMinute
import com.imsi.mud.simulation.PayloadHash
import com.imsi.mud.simulation.ProgressionMode
import com.imsi.mud.simulation.RngOperation
import com.imsi.mud.simulation.RngStreamKey
import com.imsi.mud.simulation.SessionEpoch
import com.imsi.mud.simulation.SessionLifecycle
import com.imsi.mud.simulation.SessionRuntimeState
import com.imsi.mud.simulation.SchedulePriority
import com.imsi.mud.simulation.ScheduleReservePayload
import com.imsi.mud.simulation.ScheduleResolveConflictPayload
import com.imsi.mud.simulation.ScheduledActionPayload
import com.imsi.mud.simulation.ScheduledEntityRef
import com.imsi.mud.simulation.StateVersion
import com.imsi.mud.simulation.TimeAdvanceGoal
import com.imsi.mud.simulation.TimeAdvanceInterruptPolicy
import com.imsi.mud.simulation.TimeTraversalLimits
import com.imsi.mud.simulation.WorldCommandPayload
import com.imsi.mud.MainActivityContent
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class Phase2TimeUiTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun rendersStatusesSummaryAndAllowedAdvanceControls() {
        val actions = mutableListOf<Phase2TimeUiAction>()
        val summary = TimeAdvanceSummary(
            elapsed = "2h",
            stopReason = "Decision required",
            majorEvents = listOf("Arrival", "A", "B", "C", "D", "E", "F"),
            completedWork = listOf("Treatment"),
            resourceWarnings = listOf("Bed nearly full"),
            importantChanges = listOf("Public relation changed"),
            bundleCount = 2,
            unknownImportantCount = 1,
            nextActionLabel = "Review decision"
        )
        compose.setContent {
            Phase2TimeScreen(
                Phase2TimeViewState(
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
        assertEquals(listOf(Phase2TimeUiAction.Pause), actions)
    }

    @Test
    fun hidesCancelWhenProtectedCompletionHasNoAllowedControls() {
        compose.setContent {
            Phase2TimeScreen(
                Phase2TimeViewState(
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
        val actions = mutableListOf<Phase2TimeUiAction>()
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
            conflictingSchedules = listOf("Treatment 14:00-18:00"),
            allowedResolutions = listOf(ScheduleResolution.PAUSE_AND_INSERT),
            preview = preview
        )
        val state = mutableStateOf(Phase2TimeViewState(status = TimeAdvanceStatus.IDLE, conflict = conflict))
        compose.setContent {
            Phase2TimeScreen(state.value, actions::add)
        }

        compose.onNodeWithTag("phase2-time-preview-current-schedule")
            .assertTextEquals("Current schedule: Treatment 14:00-18:00 (Confirmed)")
        compose.onNodeWithTag("phase2-time-preview-refund-or-loss")
            .assertTextEquals("Refund or loss: Unknown (Unknown)")
        compose.onNodeWithText("Resource loss").assertIsDisplayed()
        compose.onNodeWithTag("phase2-time-conflict-0")
            .assertTextEquals("Conflicting scheduled action 1")
        compose.onAllNodesWithText("Treatment 14:00-18:00").assertCountEquals(0)
        compose.onNodeWithTag("phase2-time-resolution-pause_and_insert").performClick()
        assertEquals(emptyList<Phase2TimeUiAction>(), actions)
        compose.runOnIdle { state.value = state.value.copy(conflict = conflict.copy(previewToken = "v2")) }
        compose.onNodeWithTag("phase2-time-resolution-pause_and_insert").performClick()
        assertEquals(emptyList<Phase2TimeUiAction>(), actions)
        compose.onNodeWithTag("phase2-time-resolution-pause_and_insert")
            .assertTextEquals("Confirm Pause and insert")
            .assertHeightIsAtLeast(48.dp)
            .performClick()
        assertEquals(
            listOf(
                Phase2TimeUiAction.ConfirmConflict(
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
    fun decisionChoiceCarriesCanonicalSelectionAndNeverShowsPause() {
        val actions = mutableListOf<Phase2TimeUiAction>()
        val canonicalPayload = "{\"id\":\"npc-1\"}"
        val payloadHash = DecisionSelection("gate-1", "choice-1", "DECISION_CHOICE.v1", canonicalPayload).payloadHash.value
        val choice = PublicDecisionChoice("choice-1", "Choose successor", "DECISION_CHOICE.v1", canonicalPayload, payloadHash)
        compose.setContent {
            Phase2TimeScreen(
                Phase2TimeViewState(
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
                Phase2TimeUiAction.Continue(
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
    fun rejectsDecisionRequiredCombinedWithAdvanceInProgress() {
        val selection = DecisionSelection("gate-1", "choice-1", "DECISION_CHOICE.v1", "{}")
        try {
            Phase2TimeViewState(
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
        val actions = mutableListOf<Phase2TimeUiAction>()
        val preview = emptyPreview()
        compose.setContent {
            Phase2TimeScreen(
                Phase2TimeViewState(
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
        assertEquals(listOf(Phase2TimeUiAction.RefreshPreview("conflict-stale")), actions)
    }

    @Test
    fun controllerRoutesStartDecisionAndControlThroughCoreAuthorityApis() = kotlinx.coroutines.runBlocking {
        val envelopes = mutableListOf<CommandEnvelope<out WorldCommandPayload>>()
        val controls = mutableListOf<ControlRequest>()
        val commandIds = ArrayDeque(listOf(CommandId("cmd-start"), CommandId("cmd-child"), CommandId("cmd-resume"), CommandId("cmd-conflict")))
        val controller = Phase2TimeController(
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

        controller.dispatch(Phase2TimeUiAction.StartAdvance(request))
        controller.dispatch(
            Phase2TimeUiAction.Continue(
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
        controller.dispatch(Phase2TimeUiAction.Pause)
        controller.dispatch(Phase2TimeUiAction.ResumeInterrupted("cmd-interrupted", request))
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
        val confirm = Phase2TimeUiAction.ConfirmConflict(
            conflictId = conflict.conflictId,
            resolution = ScheduleResolution.PREEMPT,
            expectedRowVersions = conflict.expectedRowVersions,
            reservation = conflict.reservation,
            previewCodec = conflict.previewCodec,
            previewHash = conflict.previewHash,
            previewToken = conflict.previewToken
        )
        controller.dispatch(confirm, Phase2TimeViewState(TimeAdvanceStatus.IDLE, conflict = conflict))

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
            Phase2TimeViewState(TimeAdvanceStatus.IDLE, conflict = conflict.copy(previewToken = "new-token"))
        )
        assertEquals(Phase2DispatchResult.RefreshRequested("conflict-route"), stale)
        assertEquals(4, envelopes.size)
    }

    @Test
    fun routeExecutesAdvanceOnceWhenStartIsTappedTwice() {
        var executeCount = 0
        val release = CompletableDeferred<CommandResult>()
        val controller = Phase2TimeController(
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
            Phase2TimeRoute(
                Phase2TimeEntry(
                    state = Phase2TimeViewState(
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
    fun routeAllowsPauseWhileAdvanceExecuteIsStillPending() {
        var executeCount = 0
        var controlCount = 0
        val release = CompletableDeferred<CommandResult>()
        val state = mutableStateOf(
            Phase2TimeViewState(
                status = TimeAdvanceStatus.IDLE,
                startRequest = advanceRequest()
            )
        )
        val controller = Phase2TimeController(
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
            Phase2TimeRoute(Phase2TimeEntry(state.value, controller))
        }

        compose.onNodeWithTag("phase2-time-start").performClick()
        compose.runOnIdle {
            state.value = Phase2TimeViewState(
                status = TimeAdvanceStatus.ADVANCE_IN_PROGRESS,
                advanceInProgress = AdvanceInProgressViewState(
                    goalLabel = "Until treatment completes",
                    lastCommittedCursor = "10:30",
                    allowedControls = setOf(AdvanceControl.PAUSE, AdvanceControl.CANCEL)
                )
            )
        }
        compose.onNodeWithTag("phase2-time-progress").assertIsDisplayed()
        compose.onNodeWithTag("phase2-time-control-pause")
            .performClick()
        compose.waitForIdle()

        assertEquals(1, executeCount)
        assertEquals(1, controlCount)
        release.complete(CommandResult.Accepted(1))
        compose.waitForIdle()
    }

    @Test
    fun mainActivityContentEntersPhase2RouteWhenEntryIsProvided() {
        val controller = Phase2TimeController(
            execute = { CommandResult.Accepted(1) },
            requestControl = { ControlRequestResult.Accepted(CommandId("cmd-active")) },
            runtimeState = { SessionRuntimeState(SessionEpoch(1), SessionLifecycle.OPEN, null) },
            currentVersion = { StateVersion(1) },
            newCommandId = { CommandId("cmd-start") }
        )
        compose.setContent {
            MainActivityContent(
                phase2TimeEntry = Phase2TimeEntry(
                    state = Phase2TimeViewState(TimeAdvanceStatus.IDLE),
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

    private fun advanceRequest() = Phase2AdvanceRequest(
        goal = TimeAdvanceGoal.UntilMinute((GameMinute.of(60) as Checked.Value).value),
        mode = ProgressionMode.FAST_FORWARD,
        limits = TimeTraversalLimits((GameMinute.of(120) as Checked.Value).value, 100),
        interruptPolicy = TimeAdvanceInterruptPolicy()
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
                schedulePriority = SchedulePriority.EMERGENCY_RESCUE,
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
            effectiveMinute = (GameMinute.of(90) as Checked.Value).value
        )
    }

    private companion object {
        val EMPTY_HASH: String = PayloadHash("0".repeat(64)).value
    }
}
