package com.imsi.mud.simulation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScheduleServiceTest {
    private val service = ScheduleService()
    private val rhea = entity("rhea")
    private val medicine = ResourceIdentity("ITEM", "medicine")

    @Test
    fun `P2-UT-003 half open participant intervals meet without a conflict`() {
        val calendar = ScheduleCalendar(listOf(action("treatment", 600, 660)), mapOf(medicine to 10))

        val result = service.reserve(request("training", 660, 720), calendar)

        assertTrue(result is ReservationResult.Accepted)
    }

    @Test
    fun `P2-BT-003 conflict is returned and higher priority preemption is never automatic`() {
        val existing = action("routine", 600, 660, SchedulePriority.TRAINING_ROUTINE, canBePreempted = true)
        val calendar = ScheduleCalendar(listOf(existing), mapOf(medicine to 10))
        val request = request("rescue", 630, 690, SchedulePriority.EMERGENCY_RESCUE)

        val conflict = service.reserve(request, calendar) as ReservationResult.Conflict

        assertTrue(ScheduleResolution.PREEMPT in conflict.details.allowedResolutions)
        assertEquals(listOf(existing.actionId), conflict.details.conflictingActionIds)
        assertTrue(conflict.details.consequencePreview.canonicalJson().contains("\"codec\":\"PublicConsequencePreview.v1\""))
        val applied = service.resolveConflict(
            request,
            calendar,
            ScheduleResolution.PREEMPT,
            mapOf(existing.actionId to existing.rowVersion),
            PublicConsequencePreview.CODEC_ID,
            conflict.details.consequencePreview.hash
        ) as ResolutionResult.Applied
        assertEquals(ScheduledActionStatus.NEEDS_RESCHEDULE, applied.calendar.actions.first().status)
        assertEquals("rescue", applied.inserted.actionId.value)
    }

    @Test
    fun `resource holds reject over reservation and cancellation settles by policy`() {
        val held = action("held", 600, 660, claims = listOf(ResourceClaim(medicine, 4, ResourceClaimPolicy.HOLD_AND_RELEASE)))
        val calendar = ScheduleCalendar(listOf(held), mapOf(medicine to 5))

        val rejected = service.reserve(
            request("too-much", 660, 720, claims = listOf(ResourceClaim(medicine, 2, ResourceClaimPolicy.HOLD_AND_RELEASE))),
            calendar
        )
        assertEquals(DomainError.ResourceUnavailable("ITEM:medicine"), (rejected as ReservationResult.Rejected).error)

        val material = action("material", 600, 660, claims = listOf(ResourceClaim(medicine, 1, ResourceClaimPolicy.HOLD_THEN_CONSUME_ON_START)))
        val materialCalendar = ScheduleCalendar(listOf(material), mapOf(medicine to 5))
        val started = checked(service.start(material.actionId, materialCalendar, minute(600)))
        val cancelled = checked(
            service.cancel(material.actionId, started.calendar, CancellationStage.IN_PROGRESS)
        )
        assertEquals(ScheduledActionStatus.CANCELLED, cancelled.action.status)
        assertEquals(0L, cancelled.calendar.heldTotal(medicine))
        assertEquals(ResourceClaimState.CONSUMED, cancelled.calendar.resources.claimStates[ResourceClaimKey(material.actionId, medicine)])
    }

    @Test
    fun `zero duration action is rejected before any reservation is created`() {
        val result = service.reserve(request("invalid", 600, 600), ScheduleCalendar(emptyList(), mapOf(medicine to 1)))

        assertTrue((result as ReservationResult.Rejected).error is DomainError.ValidationError)
        assertTrue(runCatching { ResourceIdentity("item", "medicine") }.isFailure)
        assertTrue(runCatching { ResourceIdentity("ITEM", " medicine") }.isFailure)
    }

    @Test
    fun `scheduled action source emits stable start then complete boundary candidates`() {
        val source = ScheduledActionBoundarySource()
        val snapshot = WorldTraversalSnapshot(WorldClock(minute(599)), ScheduleCalendar(listOf(action("treatment", 600, 660)), mapOf(medicine to 1)))

        assertEquals(minute(600), source.nextTimeAfter(snapshot, null))
        assertEquals(BoundaryCategory.SCHEDULED_ACTION_START, source.candidatesAt(snapshot, minute(600)).single().key.category)
        assertEquals(BoundaryCategory.SCHEDULED_ACTION_COMPLETE, source.candidatesAt(snapshot, minute(660)).single().key.category)
        assertTrue(BoundarySourceConformanceSuite.validate(source, snapshot, null, minute(600)) is Checked.Value)
    }

    @Test
    fun `each resource policy has one deterministic settlement at reserve start complete and cancel`() {
        assertEquals(ResourceSettlement.CONSUME, ResourceClaimPolicy.CONSUME_ON_RESERVE.settlement(ResourceTransition.RESERVE))
        assertEquals(ResourceSettlement.CONSUME, ResourceClaimPolicy.HOLD_THEN_CONSUME_ON_START.settlement(ResourceTransition.START))
        assertEquals(ResourceSettlement.CONSUME, ResourceClaimPolicy.HOLD_THEN_CONSUME_ON_COMPLETE.settlement(ResourceTransition.COMPLETE))
        assertEquals(ResourceSettlement.RELEASE, ResourceClaimPolicy.HOLD_AND_RELEASE.settlement(ResourceTransition.COMPLETE))
        assertEquals(ResourceSettlement.RELEASE, ResourceClaimPolicy.HOLD_THEN_CONSUME_ON_START.settlement(ResourceTransition.CANCEL, CancellationStage.BEFORE_START))
        assertEquals(ResourceSettlement.HOLD, ResourceClaimPolicy.HOLD_THEN_CONSUME_ON_START.settlement(ResourceTransition.CANCEL, CancellationStage.IN_PROGRESS))
    }

    @Test
    fun `resource ledger preserves owned held consumed invariant through every policy transition`() {
        val claim = ResourceClaim(medicine, 1, ResourceClaimPolicy.CONSUME_ON_RESERVE)
        val consumedOnReserve = checked(ResourceLedger(mapOf(medicine to 2)).apply(entity("reserve"), listOf(claim), ResourceTransition.RESERVE))
        assertEquals(1L, consumedOnReserve.owned[medicine])
        assertEquals(0L, consumedOnReserve.held[medicine] ?: 0L)
        assertEquals(1L, consumedOnReserve.consumed[medicine])
        assertTrue(consumedOnReserve.apply(entity("reserve"), listOf(claim), ResourceTransition.RESERVE) is Checked.Rejected)

        val atStart = ResourceClaim(medicine, 1, ResourceClaimPolicy.HOLD_THEN_CONSUME_ON_START)
        val heldThenStarted = checked(ResourceLedger(mapOf(medicine to 2)).apply(entity("start"), listOf(atStart), ResourceTransition.RESERVE))
            .let { checked(it.apply(entity("start"), listOf(atStart), ResourceTransition.START)) }
        assertEquals(1L, heldThenStarted.owned[medicine])
        assertEquals(0L, heldThenStarted.held[medicine] ?: 0L)
        assertEquals(1L, heldThenStarted.consumed[medicine])

        val atComplete = ResourceClaim(medicine, 1, ResourceClaimPolicy.HOLD_THEN_CONSUME_ON_COMPLETE)
        val heldThenCompleted = checked(ResourceLedger(mapOf(medicine to 2)).apply(entity("complete"), listOf(atComplete), ResourceTransition.RESERVE))
            .let { checked(it.apply(entity("complete"), listOf(atComplete), ResourceTransition.COMPLETE)) }
        assertEquals(1L, heldThenCompleted.owned[medicine])
        assertEquals(0L, heldThenCompleted.held[medicine] ?: 0L)
        assertEquals(1L, heldThenCompleted.consumed[medicine])

        val release = ResourceClaim(medicine, 1, ResourceClaimPolicy.HOLD_AND_RELEASE)
        val released = checked(ResourceLedger(mapOf(medicine to 2)).apply(entity("release"), listOf(release), ResourceTransition.RESERVE))
            .let { checked(it.apply(entity("release"), listOf(release), ResourceTransition.COMPLETE)) }
        assertEquals(2L, released.owned[medicine])
        assertEquals(0L, released.held[medicine] ?: 0L)
        assertEquals(0L, released.consumed[medicine] ?: 0L)

        val finalBoundary = checked(ResourceLedger(mapOf(medicine to 2)).apply(entity("final"), listOf(atComplete), ResourceTransition.RESERVE))
            .let { checked(it.apply(entity("final"), listOf(atComplete), ResourceTransition.CANCEL, CancellationStage.FINAL_BOUNDARY)) }
        assertEquals(1L, finalBoundary.owned[medicine])
        assertEquals(0L, finalBoundary.held[medicine] ?: 0L)
        assertEquals(1L, finalBoundary.consumed[medicine])
    }

    @Test
    fun `all claim policies settle durably through their normal lifecycle`() {
        val consumedOnReserve = acceptedCalendar("reserve-consume", ResourceClaimPolicy.CONSUME_ON_RESERVE)
        assertAccounting(consumedOnReserve, "reserve-consume", 1, 0, 1, ResourceClaimState.CONSUMED)
        val consumedOnReserveDone = checked(service.complete(
            entity("reserve-consume"),
            checked(service.start(entity("reserve-consume"), consumedOnReserve, minute(600))).calendar
        ))
        assertAccounting(consumedOnReserveDone.calendar, "reserve-consume", 1, 0, 1, ResourceClaimState.CONSUMED)
        assertTrue(service.complete(entity("reserve-consume"), consumedOnReserveDone.calendar) is Checked.Rejected)

        val consumedOnStart = acceptedCalendar("start-consume", ResourceClaimPolicy.HOLD_THEN_CONSUME_ON_START)
        assertAccounting(consumedOnStart, "start-consume", 2, 1, 0, ResourceClaimState.HELD)
        val started = checked(service.start(entity("start-consume"), consumedOnStart, minute(600)))
        assertAccounting(started.calendar, "start-consume", 1, 0, 1, ResourceClaimState.CONSUMED)
        val failedAfterStart = checked(service.fail(entity("start-consume"), started.calendar, CancellationStage.IN_PROGRESS))
        assertAccounting(failedAfterStart.calendar, "start-consume", 1, 0, 1, ResourceClaimState.CONSUMED)

        val consumedOnComplete = acceptedCalendar("complete-consume", ResourceClaimPolicy.HOLD_THEN_CONSUME_ON_COMPLETE)
        assertAccounting(consumedOnComplete, "complete-consume", 2, 1, 0, ResourceClaimState.HELD)
        val completed = checked(service.complete(
            entity("complete-consume"),
            checked(service.start(entity("complete-consume"), consumedOnComplete, minute(600))).calendar
        ))
        assertAccounting(completed.calendar, "complete-consume", 1, 0, 1, ResourceClaimState.CONSUMED)

        val releasedOnComplete = acceptedCalendar("release", ResourceClaimPolicy.HOLD_AND_RELEASE)
        val released = checked(service.complete(
            entity("release"),
            checked(service.start(entity("release"), releasedOnComplete, minute(600))).calendar
        ))
        assertAccounting(released.calendar, "release", 2, 0, 0, ResourceClaimState.RELEASED)
    }

    @Test
    fun `P2-FT-003 cancel and fail settle each held claim once without leaking resources`() {
        val consumed = checked(service.cancel(
            entity("reserve-nonrefundable"),
            acceptedCalendar("reserve-nonrefundable", ResourceClaimPolicy.CONSUME_ON_RESERVE),
            CancellationStage.BEFORE_START
        ))
        assertAccounting(consumed.calendar, "reserve-nonrefundable", 1, 0, 1, ResourceClaimState.CONSUMED)

        val cancelledBeforeStart = checked(service.cancel(
            entity("start-refundable"),
            acceptedCalendar("start-refundable", ResourceClaimPolicy.HOLD_THEN_CONSUME_ON_START),
            CancellationStage.BEFORE_START
        ))
        assertAccounting(cancelledBeforeStart.calendar, "start-refundable", 2, 0, 0, ResourceClaimState.CANCELLED)

        val completePolicy = acceptedCalendar("complete-refundable", ResourceClaimPolicy.HOLD_THEN_CONSUME_ON_COMPLETE)
        val failedInProgress = checked(service.fail(
            entity("complete-refundable"),
            checked(service.start(entity("complete-refundable"), completePolicy, minute(600))).calendar,
            CancellationStage.IN_PROGRESS
        ))
        assertAccounting(failedInProgress.calendar, "complete-refundable", 2, 0, 0, ResourceClaimState.CANCELLED)

        val finalPolicy = acceptedCalendar("complete-final", ResourceClaimPolicy.HOLD_THEN_CONSUME_ON_COMPLETE)
        val cancelledAtFinal = checked(service.cancel(
            entity("complete-final"),
            checked(service.start(entity("complete-final"), finalPolicy, minute(600))).calendar,
            CancellationStage.FINAL_BOUNDARY
        ))
        assertAccounting(cancelledAtFinal.calendar, "complete-final", 1, 0, 1, ResourceClaimState.CONSUMED)

        val released = checked(service.fail(
            entity("held-release"),
            acceptedCalendar("held-release", ResourceClaimPolicy.HOLD_AND_RELEASE),
            CancellationStage.BEFORE_START
        ))
        assertAccounting(released.calendar, "held-release", 2, 0, 0, ResourceClaimState.CANCELLED)
        assertTrue(service.fail(entity("held-release"), released.calendar, CancellationStage.BEFORE_START) is Checked.Rejected)
    }

    @Test
    fun `preempt cancel and pause apply old settlement and new reservation atomically`() {
        val preempted = resolveWithHeldExisting(ScheduleResolution.PREEMPT, startExisting = false, newClaims = true)
        assertEquals(ScheduledActionStatus.NEEDS_RESCHEDULE, preempted.calendar.actions.first { it.actionId == entity("routine-PREEMPT") }.status)
        assertAccounting(preempted.calendar, "routine-PREEMPT", 2, 1, 0, ResourceClaimState.CANCELLED)
        assertEquals(ResourceClaimState.HELD, preempted.calendar.resources.claimStates[ResourceClaimKey(entity("rescue-PREEMPT"), medicine)])

        val cancelled = resolveWithHeldExisting(ScheduleResolution.CANCEL_AND_INSERT, startExisting = false, newClaims = true)
        assertEquals(ScheduledActionStatus.CANCELLED, cancelled.calendar.actions.first { it.actionId == entity("routine-CANCEL_AND_INSERT") }.status)
        assertAccounting(cancelled.calendar, "routine-CANCEL_AND_INSERT", 2, 1, 0, ResourceClaimState.CANCELLED)

        val paused = resolveWithHeldExisting(ScheduleResolution.PAUSE_AND_INSERT, startExisting = true, newClaims = false)
        assertEquals(ScheduledActionStatus.PAUSED, paused.calendar.actions.first { it.actionId == entity("routine-PAUSE_AND_INSERT") }.status)
        assertAccounting(paused.calendar, "routine-PAUSE_AND_INSERT", 2, 1, 0, ResourceClaimState.HELD)
    }

    @Test
    fun `duplicate action transitions are rejected before a second settlement`() {
        val actionId = entity("once")
        val reserved = acceptedCalendar("once", ResourceClaimPolicy.HOLD_AND_RELEASE)
        val started = checked(service.start(actionId, reserved, minute(600)))
        assertTrue(service.start(actionId, started.calendar, minute(600)) is Checked.Rejected)
        val paused = checked(service.pause(actionId, started.calendar))
        assertTrue(service.pause(actionId, paused.calendar) is Checked.Rejected)
        val preempted = checked(service.preempt(actionId, paused.calendar))
        assertTrue(service.preempt(actionId, preempted.calendar) is Checked.Rejected)
        assertAccounting(preempted.calendar, "once", 2, 0, 0, ResourceClaimState.CANCELLED)
    }

    @Test
    fun `failed conflict insertion exposes none of the provisional preemption`() {
        val existing = action("routine-rollback", 600, 660, SchedulePriority.TRAINING_ROUTINE, canBePreempted = true)
        val holder = action(
            "holder",
            600,
            660,
            claims = listOf(ResourceClaim(medicine, 2, ResourceClaimPolicy.HOLD_AND_RELEASE)),
            participants = listOf(participant(entity("kai")))
        )
        val calendar = ScheduleCalendar(listOf(existing, holder), mapOf(medicine to 2))
        val request = request(
            "rescue-rollback",
            630,
            690,
            SchedulePriority.EMERGENCY_RESCUE,
            listOf(ResourceClaim(medicine, 1, ResourceClaimPolicy.HOLD_AND_RELEASE))
        )
        val conflict = service.reserve(request, calendar) as ReservationResult.Conflict

        val result = service.resolveConflict(
            request,
            calendar,
            ScheduleResolution.PREEMPT,
            conflict.details.conflictingRowVersions,
            PublicConsequencePreview.CODEC_ID,
            conflict.details.consequencePreview.hash
        )

        assertTrue(result is ResolutionResult.Rejected)
        assertEquals(ScheduledActionStatus.RESERVED, calendar.actions.first { it.actionId == existing.actionId }.status)
        assertEquals(2L, calendar.heldTotal(medicine))
        assertEquals(ResourceClaimState.HELD, calendar.resources.claimStates[ResourceClaimKey(holder.actionId, medicine)])
    }

    @Test
    fun `durable action profile selects interruption behavior and produces canonical public outcome`() {
        val pauseProfile = profile(
            resumable = true,
            defaultResult = ActionInterruptionResult.CONTINUE,
            byReason = mapOf("WORLD_CRISIS" to ActionInterruptionResult.PAUSE)
        )
        val running = checked(service.start(
            entity("profile-pause"),
            acceptedCalendar("profile-pause", ResourceClaimPolicy.HOLD_AND_RELEASE, pauseProfile),
            minute(600)
        )).calendar

        val paused = checked(service.applyInterruption(
            entity("profile-pause"),
            running,
            "WORLD_CRISIS",
            30,
            CancellationStage.IN_PROGRESS
        ))

        assertEquals(ScheduledActionStatus.PAUSED, paused.outcome.nextState)
        assertEquals(ResourceSettlement.HOLD, paused.outcome.claimSettlements.single().settlement)
        assertEquals("MINUTES", paused.outcome.progressBasis)
        assertEquals(30L, paused.outcome.progressValue)
        assertEquals("schedule.action.interrupted.v1", paused.outcome.consequenceEventCodec)
        val canonical = paused.outcome.canonicalJson()
        assertTrue(canonical.indexOf("\"actionId\"") < canonical.indexOf("\"reasonCode\""))
        assertTrue(canonical.indexOf("\"reasonCode\"") < canonical.indexOf("\"result\""))
        assertTrue(pauseProfile.canonicalJson().contains("\"codec\":\"ActionKindPolicyProfile.v1\""))

        val continuedCalendar = acceptedCalendar("profile-continue", ResourceClaimPolicy.HOLD_AND_RELEASE, pauseProfile)
        val continued = checked(service.applyInterruption(
            entity("profile-continue"),
            continuedCalendar,
            "UNLISTED_REASON",
            0,
            CancellationStage.BEFORE_START
        ))
        assertEquals(ActionInterruptionResult.CONTINUE, continued.outcome.result)
        assertEquals(ScheduledActionStatus.RESERVED, continued.outcome.nextState)
        assertEquals(continuedCalendar, continued.calendar)

        val rescheduledProfile = profile(false, ActionInterruptionResult.RESCHEDULE_REQUIRED)
        val rescheduledCalendar = acceptedCalendar("profile-reschedule", ResourceClaimPolicy.HOLD_AND_RELEASE, rescheduledProfile)
        val rescheduled = checked(service.applyInterruption(
            entity("profile-reschedule"),
            rescheduledCalendar,
            "WORLD_CRISIS",
            5,
            CancellationStage.BEFORE_START
        ))
        assertEquals(ScheduledActionStatus.NEEDS_RESCHEDULE, rescheduled.outcome.nextState)
        assertEquals(ResourceClaimState.CANCELLED, rescheduled.outcome.claimSettlements.single().state)
        assertEquals(0L, rescheduled.calendar.heldTotal(medicine))
    }

    @Test
    fun `cancellation policy selects the exact stage rule without leaking a claim`() {
        val policy = ActionCancellationPolicy(mapOf(
            CancellationStage.BEFORE_START to ActionCancellationRule(10_000, 0, true),
            CancellationStage.IN_PROGRESS to ActionCancellationRule(5_000, 2, true),
            CancellationStage.FINAL_BOUNDARY to ActionCancellationRule(0, 4, false)
        ))
        val cancelProfile = profile(false, ActionInterruptionResult.CANCEL, cancellation = policy)

        val beforeStart = checked(service.applyInterruption(
            entity("stage-before"),
            acceptedCalendar("stage-before", ResourceClaimPolicy.HOLD_AND_RELEASE, cancelProfile),
            "PLAYER_CANCEL",
            0,
            CancellationStage.BEFORE_START
        ))
        assertEquals(policy.ruleFor(CancellationStage.BEFORE_START), beforeStart.outcome.cancellationRule)
        assertEquals(CancellationStage.BEFORE_START, beforeStart.outcome.cancellationStage)
        assertAccounting(beforeStart.calendar, "stage-before", 2, 0, 0, ResourceClaimState.CANCELLED)

        val inProgressCalendar = checked(service.start(
            entity("stage-progress"),
            acceptedCalendar("stage-progress", ResourceClaimPolicy.HOLD_THEN_CONSUME_ON_COMPLETE, profile(false, ActionInterruptionResult.FAIL, cancellation = policy)),
            minute(600)
        )).calendar
        val failed = checked(service.applyInterruption(
            entity("stage-progress"),
            inProgressCalendar,
            "SYSTEM_FAILURE",
            3,
            CancellationStage.IN_PROGRESS
        ))
        assertEquals(policy.ruleFor(CancellationStage.IN_PROGRESS), failed.outcome.cancellationRule)
        assertEquals(1L, failed.outcome.progressValue - failed.outcome.cancellationRule!!.progressLoss)
        assertAccounting(failed.calendar, "stage-progress", 2, 0, 0, ResourceClaimState.CANCELLED)

        val finalCalendar = checked(service.start(
            entity("stage-final"),
            acceptedCalendar("stage-final", ResourceClaimPolicy.HOLD_THEN_CONSUME_ON_COMPLETE, cancelProfile),
            minute(600)
        )).calendar
        val final = checked(service.applyInterruption(
            entity("stage-final"),
            finalCalendar,
            "PLAYER_CANCEL",
            4,
            CancellationStage.FINAL_BOUNDARY
        ))
        assertEquals(policy.ruleFor(CancellationStage.FINAL_BOUNDARY), final.outcome.cancellationRule)
        assertAccounting(final.calendar, "stage-final", 1, 0, 1, ResourceClaimState.CONSUMED)
    }

    @Test
    fun `invalid action profile and impossible progress loss reject without mutation`() {
        assertTrue(runCatching {
            ActionCancellationPolicy(mapOf(CancellationStage.BEFORE_START to ActionCancellationRule(0, 0, false)))
        }.isFailure)
        assertTrue(runCatching { profile(false, ActionInterruptionResult.PAUSE) }.isFailure)
        assertTrue(runCatching {
            profile(false, ActionInterruptionResult.RESCHEDULE_REQUIRED, cancellation = cancellationPolicy(reschedulable = false))
        }.isFailure)
        assertTrue(runCatching {
            ActionKindPolicyProfile(false, "MINUTES", ActionInterruptionPolicy(ActionInterruptionResult.CONTINUE), cancellationPolicy(), "hidden")
        }.isFailure)

        val rejectingProfile = profile(
            false,
            ActionInterruptionResult.CANCEL,
            cancellation = ActionCancellationPolicy(CancellationStage.entries.associateWith {
                ActionCancellationRule(0, 2, false)
            })
        )
        val calendar = acceptedCalendar("rollback-profile", ResourceClaimPolicy.HOLD_AND_RELEASE, rejectingProfile)
        val result = service.applyInterruption(
            entity("rollback-profile"),
            calendar,
            "PLAYER_CANCEL",
            1,
            CancellationStage.BEFORE_START
        )

        assertTrue(result is Checked.Rejected)
        assertEquals(ScheduledActionStatus.RESERVED, calendar.actions.single().status)
        assertAccounting(calendar, "rollback-profile", 2, 1, 0, ResourceClaimState.HELD)
    }

    @Test
    fun `planned unreserved claims continue cancel or fail without creating resource state`() {
        fun plannedCalendar(id: String, profile: ActionKindPolicyProfile): ScheduleCalendar = ScheduleCalendar(
            listOf(action(
                id,
                600,
                660,
                claims = listOf(ResourceClaim(medicine, 1, ResourceClaimPolicy.HOLD_AND_RELEASE)),
                actionProfile = profile,
                status = ScheduledActionStatus.PLANNED
            )),
            mapOf(medicine to 2)
        )

        val continuedSource = plannedCalendar("planned-continue", profile(false, ActionInterruptionResult.CONTINUE))
        val continued = checked(service.applyInterruption(
            entity("planned-continue"),
            continuedSource,
            "NO_CHANGE",
            0,
            CancellationStage.BEFORE_START
        ))
        assertEquals(ScheduledActionStatus.PLANNED, continued.outcome.nextState)
        assertTrue(continued.outcome.claimSettlements.isEmpty())
        assertEquals(continuedSource.resources, continued.calendar.resources)

        val cancelledSource = plannedCalendar("planned-cancel", profile(false, ActionInterruptionResult.CANCEL))
        val cancelled = checked(service.applyInterruption(
            entity("planned-cancel"),
            cancelledSource,
            "PLAYER_CANCEL",
            0,
            CancellationStage.BEFORE_START
        ))
        assertEquals(ScheduledActionStatus.CANCELLED, cancelled.outcome.nextState)
        assertTrue(cancelled.outcome.claimSettlements.isEmpty())
        assertEquals(cancelledSource.resources, cancelled.calendar.resources)

        val failedSource = plannedCalendar("planned-fail", profile(false, ActionInterruptionResult.FAIL))
        val failed = checked(service.applyInterruption(
            entity("planned-fail"),
            failedSource,
            "SYSTEM_FAILURE",
            0,
            CancellationStage.BEFORE_START
        ))
        assertEquals(ScheduledActionStatus.FAILED, failed.outcome.nextState)
        assertTrue(failed.outcome.claimSettlements.isEmpty())
        assertEquals(failedSource.resources, failed.calendar.resources)

        val rejectedSource = plannedCalendar("planned-pause", profile(true, ActionInterruptionResult.PAUSE))
        val rejected = service.applyInterruption(
            entity("planned-pause"),
            rejectedSource,
            "WORLD_CRISIS",
            0,
            CancellationStage.BEFORE_START
        )
        assertTrue(rejected is Checked.Rejected)
        assertEquals(ScheduledActionStatus.PLANNED, rejectedSource.actions.single().status)
        assertEquals(2L, rejectedSource.resources.owned[medicine])
        assertTrue(rejectedSource.resources.claimStates.isEmpty())
    }

    @Test
    fun `scheduled payload round trips typed identities and never duplicates physical row fields`() {
        val owner = entity("guild-alpha")
        val payload = ScheduledActionPayload(
            ownerType = "ORGANIZATION",
            ownerId = owner,
            participants = listOf(participant(entity("rhea")), participant(entity("rhea"), "SUMMON")),
            schedulePriority = SchedulePriority.OFFICIAL_OPERATION,
            resourceClaims = listOf(ResourceClaim(medicine, 1, ResourceClaimPolicy.HOLD_AND_RELEASE)),
            actionKindPolicy = profile(true, ActionInterruptionResult.CONTINUE),
            completionEventType = "schedule.operation.completed",
            completionEventCodec = "schedule.operation.completed.v1",
            canBePreempted = true
        )

        val encoded = ScheduledActionPayloadCodec.encode(payload)
        val decoded = checked(ScheduledActionPayloadCodec.decode(encoded))

        assertEquals(payload, decoded)
        assertEquals("ORGANIZATION", decoded.ownerType)
        assertEquals(owner, decoded.ownerId)
        assertEquals(listOf("MERCENARY" to "rhea", "SUMMON" to "rhea"), decoded.participants.map { it.entityType to it.entityId.value })
        assertTrue(!encoded.contains("\"actionKind\":"))
        assertTrue(!encoded.contains("startMinute"))
        assertTrue(!encoded.contains("dueMinute"))
        assertTrue(ScheduledActionPayloadCodec.decode(encoded.replace("MERCENARY", "mercenary")) is Checked.Rejected)
        assertTrue(ScheduledActionPayloadCodec.decode(encoded + " ") is Checked.Rejected)
        assertTrue(runCatching { payload.copy(participants = payload.participants.reversed()) }.isFailure)
        assertTrue(runCatching { payload.copy(participants = listOf(payload.participants.first(), payload.participants.first())) }.isFailure)
    }

    @Test
    fun `restored payload keeps the exact interruption policy and outcome`() {
        val durableProfile = profile(
            resumable = true,
            defaultResult = ActionInterruptionResult.CONTINUE,
            byReason = mapOf("WORLD_CRISIS" to ActionInterruptionResult.PAUSE)
        )
        val original = acceptedCalendar("restore-policy", ResourceClaimPolicy.HOLD_AND_RELEASE, durableProfile)
        val persisted = original.actions.single()
        val restoredPayload = checked(ScheduledActionPayloadCodec.decode(persisted.payload.canonicalJson()))
        val restored = ScheduleCalendar(listOf(persisted.copy(payload = restoredPayload)), original.resources)
        val originalRunning = checked(service.start(persisted.actionId, original, minute(600))).calendar
        val restoredRunning = checked(service.start(persisted.actionId, restored, minute(600))).calendar

        val originalOutcome = checked(service.applyInterruption(
            persisted.actionId, originalRunning, "WORLD_CRISIS", 10, CancellationStage.IN_PROGRESS
        ))
        val restoredOutcome = checked(service.applyInterruption(
            persisted.actionId, restoredRunning, "WORLD_CRISIS", 10, CancellationStage.IN_PROGRESS
        ))

        assertEquals(originalOutcome.calendar, restoredOutcome.calendar)
        assertEquals(originalOutcome.outcome.canonicalJson(), restoredOutcome.outcome.canonicalJson())
    }

    @Test
    fun `reschedule selection is typed and leaves the calendar unchanged`() {
        val existing = action("reschedule-existing", 600, 660, SchedulePriority.TRAINING_ROUTINE, canBePreempted = true)
        val calendar = ScheduleCalendar(listOf(existing), mapOf(medicine to 2))
        val request = request("reschedule-new", 630, 690, SchedulePriority.EMERGENCY_RESCUE)
        val conflict = service.reserve(request, calendar) as ReservationResult.Conflict

        val result = service.resolveConflict(
            request,
            calendar,
            ScheduleResolution.RESCHEDULE_REQUIRED,
            conflict.details.conflictingRowVersions,
            PublicConsequencePreview.CODEC_ID,
            conflict.details.consequencePreview.hash
        )

        assertTrue(result is ResolutionResult.RescheduleRequired)
        assertEquals(calendar, (result as ResolutionResult.RescheduleRequired).calendar)
    }

    @Test
    fun `only claimless planned action can start without reserve`() {
        val claimless = action("planned-claimless", 600, 660, status = ScheduledActionStatus.PLANNED)
        val claimlessCalendar = ScheduleCalendar(listOf(claimless), mapOf(medicine to 2))
        val source = ScheduledActionBoundarySource()
        val snapshot = WorldTraversalSnapshot(WorldClock(minute(599)), claimlessCalendar)
        assertEquals(BoundaryCategory.SCHEDULED_ACTION_START, source.candidatesAt(snapshot, minute(600)).single().key.category)
        assertEquals(ScheduledActionStatus.RUNNING, checked(service.start(claimless.actionId, claimlessCalendar, minute(600))).action.status)

        val claimed = action(
            "planned-claimed",
            600,
            660,
            claims = listOf(ResourceClaim(medicine, 1, ResourceClaimPolicy.HOLD_AND_RELEASE)),
            status = ScheduledActionStatus.PLANNED
        )
        val claimedCalendar = ScheduleCalendar(listOf(claimed), mapOf(medicine to 2))
        val claimedSnapshot = WorldTraversalSnapshot(WorldClock(minute(599)), claimedCalendar)
        assertTrue(source.candidatesAt(claimedSnapshot, minute(600)).isEmpty())
        assertTrue(service.start(claimed.actionId, claimedCalendar, minute(600)) is Checked.Rejected)
    }

    @Test
    fun `paused action resumes with a future completion boundary and preserves claims`() {
        val profile = profile(
            true,
            ActionInterruptionResult.CONTINUE,
            byReason = mapOf("WORLD_CRISIS" to ActionInterruptionResult.PAUSE)
        )
        val reserved = acceptedCalendar("pause-resume", ResourceClaimPolicy.HOLD_AND_RELEASE, profile)
        val running = checked(service.start(entity("pause-resume"), reserved, minute(600))).calendar
        val paused = checked(service.pause(entity("pause-resume"), running)).calendar

        val resumed = checked(service.resume(entity("pause-resume"), paused, minute(630), minute(700)))
        val snapshot = WorldTraversalSnapshot(WorldClock(minute(630)), resumed.calendar)
        val source = ScheduledActionBoundarySource()
        assertEquals(minute(700), source.nextTimeAfter(snapshot, null))
        assertEquals(BoundaryCategory.SCHEDULED_ACTION_COMPLETE, source.candidatesAt(snapshot, minute(700)).single().key.category)
        assertEquals(ResourceClaimState.HELD, resumed.calendar.resources.claimStates[ResourceClaimKey(entity("pause-resume"), medicine)])

        val completed = checked(service.complete(entity("pause-resume"), resumed.calendar))
        assertEquals(ScheduledActionStatus.COMPLETED, completed.action.status)
        assertAccounting(completed.calendar, "pause-resume", 2, 0, 0, ResourceClaimState.RELEASED)
    }

    @Test
    fun `schedule write starts current action atomically and rejects only past boundary`() {
        val calendar = ScheduleCalendar(emptyList(), mapOf(medicine to 2))

        val sameTime = service.reserve(
            request(
                "same-time",
                600,
                660,
                claims = listOf(ResourceClaim(medicine, 1, ResourceClaimPolicy.HOLD_THEN_CONSUME_ON_START)),
                effective = 600
            ),
            calendar
        ) as ReservationResult.Accepted
        val past = service.reserve(request("past-time", 599, 660, effective = 600), calendar)

        assertEquals(ScheduledActionStatus.RUNNING, sameTime.action.status)
        assertEquals(ResourceClaimState.CONSUMED, sameTime.calendar.resources.claimStates[ResourceClaimKey(entity("same-time"), medicine)])
        assertEquals(1L, sameTime.calendar.resources.owned[medicine])
        assertEquals(0L, sameTime.calendar.heldTotal(medicine))
        val source = ScheduledActionBoundarySource()
        val snapshot = WorldTraversalSnapshot(WorldClock(minute(600)), sameTime.calendar)
        assertTrue(source.candidatesAt(snapshot, minute(600)).isEmpty())
        assertEquals(minute(660), source.nextTimeAfter(snapshot, null))
        assertTrue(past is ReservationResult.Rejected)
        assertTrue(calendar.actions.isEmpty())
        assertTrue(calendar.resources.claimStates.isEmpty())
    }

    @Test
    fun `current action resource failure returns a pure rejection`() {
        val calendar = ScheduleCalendar(emptyList(), mapOf(medicine to 1))
        val request = request(
            "same-time-insufficient",
            600,
            660,
            claims = listOf(ResourceClaim(medicine, 2, ResourceClaimPolicy.HOLD_THEN_CONSUME_ON_START)),
            effective = 600
        )

        val result = service.reserve(request, calendar)

        assertTrue(result is ReservationResult.Rejected)
        assertEquals(ScheduleCalendar(emptyList(), mapOf(medicine to 1)), calendar)
    }

    @Test
    fun `boundary payload binds the canonical durable action and physical row authority`() {
        val scheduled = action("boundary-payload", 600, 660)
        val snapshot = WorldTraversalSnapshot(WorldClock(minute(599)), ScheduleCalendar(listOf(scheduled), emptyMap()))

        val candidate = ScheduledActionBoundarySource().candidatesAt(snapshot, minute(600)).single()

        assertTrue(candidate.canonicalPayload.contains("\"actionKind\":\"schedule.action\""))
        assertTrue(candidate.canonicalPayload.contains("\"scheduledActionPayload\":${scheduled.payload.canonicalJson()}"))
        assertTrue(candidate.canonicalPayload.contains("\"scheduledActionPayloadHash\":\"${scheduled.payload.hash.value}\""))
        assertTrue(candidate.canonicalPayload.contains("\"startMinute\":600"))
        assertTrue(candidate.canonicalPayload.contains("\"dueMinute\":660"))
    }

    @Test
    fun `public consequence preview is complete public status data with no hidden input`() {
        val cancellation = ActionCancellationPolicy(CancellationStage.entries.associateWith { stage ->
            ActionCancellationRule(5_000, if (stage == CancellationStage.BEFORE_START) 7 else 0, true)
        })
        val existing = action(
            "preview-existing",
            600,
            660,
            SchedulePriority.TRAINING_ROUTINE,
            listOf(ResourceClaim(medicine, 1, ResourceClaimPolicy.HOLD_AND_RELEASE)),
            canBePreempted = true,
            actionProfile = profile(true, ActionInterruptionResult.CONTINUE, cancellation = cancellation)
        )
        val calendar = ScheduleCalendar(listOf(existing), mapOf(medicine to 3))
        val conflict = service.reserve(
            request(
                "preview-new",
                630,
                690,
                SchedulePriority.EMERGENCY_RESCUE,
                listOf(ResourceClaim(ResourceIdentity("ROOM", "clinic-1"), 1, ResourceClaimPolicy.HOLD_AND_RELEASE))
            ),
            calendar
        ) as ReservationResult.Conflict
        val preview = conflict.details.consequencePreview

        assertEquals(PublicValueState.KNOWN, preview.currentSchedules.single().state)
        assertEquals(PublicValueState.KNOWN, preview.proposedSchedule.state)
        assertEquals(PublicValueState.UNDETERMINED, preview.cancelledSchedules.single().state)
        assertEquals(PublicValueState.UNKNOWN, preview.refundAmount.state)
        assertEquals(PublicValueState.UNKNOWN, preview.lossAmount.state)
        assertEquals(setOf(PublicValueState.KNOWN, PublicValueState.UNDETERMINED), preview.resourceEffects.map { it.state }.toSet())
        assertEquals(PublicField(PublicValueState.KNOWN, "7"), preview.progressLoss)
        assertEquals(PublicValueState.UNKNOWN, preview.relationshipImpact.state)
        assertEquals(PublicValueState.UNKNOWN, preview.reputationImpact.state)
        assertEquals(PublicField(PublicValueState.KNOWN, "true"), preview.rescheduleAvailability)
        val encoded = preview.canonicalJson()
        assertTrue(!encoded.contains("affection", ignoreCase = true))
        assertTrue(!encoded.contains("personality", ignoreCase = true))
        assertTrue(!encoded.contains("probability", ignoreCase = true))
    }

    @Test
    fun `stale public preview rejects conflict resolution before changing an action`() {
        val existing = action("routine", 600, 660, SchedulePriority.TRAINING_ROUTINE, canBePreempted = true)
        val calendar = ScheduleCalendar(listOf(existing), mapOf(medicine to 10))

        listOf(ScheduleResolution.PREEMPT, ScheduleResolution.CANCEL_AND_INSERT).forEach { resolution ->
            val result = service.resolveConflict(
                request("rescue", 630, 690, SchedulePriority.EMERGENCY_RESCUE),
                calendar,
                resolution,
                mapOf(existing.actionId to existing.rowVersion),
                PublicConsequencePreview.CODEC_ID,
                canonicalPayloadHash("stale")
            )

            assertEquals(DomainError.StaleConsequencePreview(entity("rescue")), (result as ResolutionResult.Rejected).error)
            assertEquals(ScheduledActionStatus.RESERVED, calendar.actions.single().status)
        }

        val conflict = service.reserve(
            request("rescue", 630, 690, SchedulePriority.EMERGENCY_RESCUE),
            calendar
        ) as ReservationResult.Conflict
        listOf(ScheduleResolution.PREEMPT, ScheduleResolution.CANCEL_AND_INSERT).forEach { resolution ->
            val staleVersion = service.resolveConflict(
                request("rescue", 630, 690, SchedulePriority.EMERGENCY_RESCUE),
                calendar,
                resolution,
                mapOf(existing.actionId to StateVersion(existing.rowVersion.value + 1)),
                PublicConsequencePreview.CODEC_ID,
                conflict.details.consequencePreview.hash
            )
            assertEquals(DomainError.StaleConsequencePreview(existing.actionId), (staleVersion as ResolutionResult.Rejected).error)
            assertEquals(ScheduledActionStatus.RESERVED, calendar.actions.single().status)
        }
    }

    private fun request(
        id: String,
        start: Long,
        due: Long,
        priority: SchedulePriority = SchedulePriority.TREATMENT,
        claims: List<ResourceClaim> = emptyList(),
        canBePreempted: Boolean = false,
        actionProfile: ActionKindPolicyProfile = profile(true, ActionInterruptionResult.CONTINUE),
        effective: Long = 0,
        participants: List<ScheduledEntityRef> = listOf(participant(rhea))
    ) = ReservationRequest(
        entity(id),
        "schedule.action",
        payload(participants, priority, claims, actionProfile, canBePreempted),
        minute(start),
        minute(due),
        minute(effective)
    )

    private fun action(
        id: String,
        start: Long,
        due: Long,
        priority: SchedulePriority = SchedulePriority.TREATMENT,
        claims: List<ResourceClaim> = emptyList(),
        canBePreempted: Boolean = false,
        actionProfile: ActionKindPolicyProfile = profile(true, ActionInterruptionResult.CONTINUE),
        status: ScheduledActionStatus = ScheduledActionStatus.RESERVED,
        participants: List<ScheduledEntityRef> = listOf(participant(rhea))
    ) = ScheduledAction(
        entity(id),
        "schedule.action",
        payload(participants, priority, claims, actionProfile, canBePreempted),
        minute(start),
        minute(due),
        status
    )

    private fun payload(
        participants: List<ScheduledEntityRef>,
        priority: SchedulePriority,
        claims: List<ResourceClaim>,
        actionProfile: ActionKindPolicyProfile,
        canBePreempted: Boolean
    ) = ScheduledActionPayload(
        ownerType = "MERCENARY",
        ownerId = participants.first().entityId,
        participants = participants,
        schedulePriority = priority,
        resourceClaims = claims,
        actionKindPolicy = actionProfile,
        completionEventType = "schedule.action.completed",
        completionEventCodec = "schedule.action.completed.v1",
        canBePreempted = canBePreempted
    )

    private fun minute(value: Long): GameMinute = checked(GameMinute.of(value))
    private fun entity(value: String): EntityId = checked(EntityId.of(value))
    private fun participant(id: EntityId, type: String = "MERCENARY") = ScheduledEntityRef(type, id)
    private fun acceptedCalendar(
        id: String,
        policy: ResourceClaimPolicy,
        actionProfile: ActionKindPolicyProfile = profile(true, ActionInterruptionResult.CONTINUE)
    ): ScheduleCalendar {
        val result = service.reserve(
            request(id, 600, 660, claims = listOf(ResourceClaim(medicine, 1, policy)), actionProfile = actionProfile),
            ScheduleCalendar(emptyList(), mapOf(medicine to 2))
        ) as ReservationResult.Accepted
        return result.calendar
    }

    private fun resolveWithHeldExisting(
        resolution: ScheduleResolution,
        startExisting: Boolean,
        newClaims: Boolean
    ): ResolutionResult.Applied {
        val suffix = resolution.name
        val existingRequest = request(
            "routine-$suffix",
            600,
            660,
            SchedulePriority.TRAINING_ROUTINE,
            listOf(ResourceClaim(medicine, 1, ResourceClaimPolicy.HOLD_AND_RELEASE)),
            canBePreempted = true
        )
        var calendar = (service.reserve(existingRequest, ScheduleCalendar(emptyList(), mapOf(medicine to 2))) as ReservationResult.Accepted).calendar
        if (startExisting) calendar = checked(service.start(existingRequest.actionId, calendar, minute(600))).calendar
        val request = request(
            "rescue-$suffix",
            630,
            690,
            SchedulePriority.EMERGENCY_RESCUE,
            if (newClaims) listOf(ResourceClaim(medicine, 1, ResourceClaimPolicy.HOLD_AND_RELEASE)) else emptyList()
        )
        val conflict = service.reserve(request, calendar) as ReservationResult.Conflict
        return service.resolveConflict(
            request,
            calendar,
            resolution,
            conflict.details.conflictingRowVersions,
            PublicConsequencePreview.CODEC_ID,
            conflict.details.consequencePreview.hash
        ) as ResolutionResult.Applied
    }

    private fun profile(
        resumable: Boolean,
        defaultResult: ActionInterruptionResult,
        byReason: Map<String, ActionInterruptionResult> = emptyMap(),
        cancellation: ActionCancellationPolicy = cancellationPolicy()
    ) = ActionKindPolicyProfile(
        resumable = resumable,
        progressBasis = "MINUTES",
        interruptionPolicy = ActionInterruptionPolicy(defaultResult, byReason),
        cancellationPolicy = cancellation,
        consequenceEventCodec = "schedule.action.interrupted.v1"
    )

    private fun cancellationPolicy(reschedulable: Boolean = true) = ActionCancellationPolicy(
        CancellationStage.entries.associateWith { ActionCancellationRule(0, 0, reschedulable) }
    )

    private fun assertAccounting(
        calendar: ScheduleCalendar,
        actionId: String,
        owned: Long,
        held: Long,
        consumed: Long,
        state: ResourceClaimState
    ) {
        assertEquals(owned, calendar.resources.owned[medicine] ?: 0L)
        assertEquals(held, calendar.resources.held[medicine] ?: 0L)
        assertEquals(consumed, calendar.resources.consumed[medicine] ?: 0L)
        assertEquals(2L, owned + consumed)
        assertTrue(held in 0..owned)
        assertEquals(owned - held, calendar.available(medicine))
        assertEquals(state, calendar.resources.claimStates[ResourceClaimKey(entity(actionId), medicine)])
    }

    private fun <T> checked(value: Checked<T>): T = when (value) {
        is Checked.Value -> value.value
        is Checked.Rejected -> error(value.error.toString())
    }
}
