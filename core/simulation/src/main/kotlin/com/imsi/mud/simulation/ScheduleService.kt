package com.imsi.mud.simulation

data class ResourceIdentity(val kind: String, val id: String) {
    init {
        require(kind.matches(Regex("[A-Z][A-Z0-9_]{0,31}"))) { "resource kind must be an uppercase ASCII token" }
        require(id == id.trim() && id.length in 1..128) { "resource id must be trimmed and 1..128 characters" }
    }
}

data class ScheduledEntityRef(val entityType: String, val entityId: EntityId) {
    init {
        require(isCanonicalToken(entityType)) { "entity type must be a canonical token" }
    }

    /** Compatibility view for state hashing; the NUL separator preserves the typed-pair identity. */
    val value: String get() = "$entityType\u0000${entityId.value}"
}

enum class SchedulePriority(val rank: Int) {
    TRAINING_ROUTINE(10),
    PERSONAL_COMMITMENT(20),
    TREATMENT(30),
    OFFICIAL_OPERATION(40),
    WORLD_CRISIS(50),
    EMERGENCY_RESCUE(60)
}

enum class ScheduledActionStatus { PLANNED, RESERVED, RUNNING, PAUSED, NEEDS_RESCHEDULE, COMPLETED, CANCELLED, FAILED }

enum class ResourceClaimPolicy { CONSUME_ON_RESERVE, HOLD_THEN_CONSUME_ON_START, HOLD_THEN_CONSUME_ON_COMPLETE, HOLD_AND_RELEASE }

enum class ScheduleResolution { KEEP_EXISTING, PREEMPT, PAUSE_AND_INSERT, CANCEL_AND_INSERT, RESCHEDULE_REQUIRED }

enum class CancellationStage { BEFORE_START, IN_PROGRESS, FINAL_BOUNDARY }

enum class ResourceTransition { RESERVE, START, COMPLETE, CANCEL, FAIL }

enum class ResourceSettlement { HOLD, CONSUME, RELEASE }

enum class ActionInterruptionResult { CONTINUE, PAUSE, CANCEL, FAIL, RESCHEDULE_REQUIRED }

data class ActionInterruptionPolicy(
    val defaultResult: ActionInterruptionResult,
    val resultByReason: Map<String, ActionInterruptionResult> = emptyMap()
) {
    init {
        require(resultByReason.keys.all(::isCanonicalToken)) { "interruption reason must be a canonical token" }
    }

    fun resultFor(reasonCode: String): ActionInterruptionResult = resultByReason[reasonCode] ?: defaultResult
}

data class ActionCancellationRule(
    val refundBasisPoints: Int,
    val progressLoss: Long,
    val reschedulable: Boolean
) {
    init {
        require(refundBasisPoints in 0..10_000) { "refund basis points must be in 0..10000" }
        require(progressLoss >= 0) { "progress loss must be non-negative" }
    }
}

data class ActionCancellationPolicy(val byStage: Map<CancellationStage, ActionCancellationRule>) {
    init {
        require(byStage.keys == CancellationStage.entries.toSet()) { "cancellation policy must define every stage exactly once" }
    }

    fun ruleFor(stage: CancellationStage): ActionCancellationRule = byStage.getValue(stage)
}

data class ActionKindPolicyProfile(
    val resumable: Boolean,
    val progressBasis: String,
    val interruptionPolicy: ActionInterruptionPolicy,
    val cancellationPolicy: ActionCancellationPolicy,
    val consequenceEventCodec: String
) {
    init {
        require(progressBasis.matches(Regex("[A-Z][A-Z0-9_]{0,31}"))) { "progress basis must be an uppercase ASCII token" }
        require(isNamespacedCodec(consequenceEventCodec)) { "consequence event codec must be namespaced and versioned" }
        val results = interruptionPolicy.resultByReason.values + interruptionPolicy.defaultResult
        require(resumable || ActionInterruptionResult.PAUSE !in results) { "non-resumable profile cannot pause" }
        require(ActionInterruptionResult.RESCHEDULE_REQUIRED !in results || cancellationPolicy.byStage.values.all(ActionCancellationRule::reschedulable)) {
            "reschedule result requires every cancellation stage to be reschedulable"
        }
    }

    fun canonicalJson(): String = buildString {
        append("{\"codec\":").append(CanonicalJson.string(CODEC_ID))
        append(",\"resumable\":").append(resumable)
        append(",\"progressBasis\":").append(CanonicalJson.string(progressBasis))
        append(",\"interruptionPolicy\":{\"defaultResult\":").append(CanonicalJson.string(interruptionPolicy.defaultResult.name))
        append(",\"resultByReason\":{")
        interruptionPolicy.resultByReason.toSortedMap().entries.joinTo(this, separator = ",") { (reason, result) ->
            "${CanonicalJson.string(reason)}:${CanonicalJson.string(result.name)}"
        }
        append("}}")
        append(",\"cancellationByStage\":{")
        CancellationStage.entries.joinTo(this, separator = ",") { stage ->
            val rule = cancellationPolicy.ruleFor(stage)
            "${CanonicalJson.string(stage.name)}:{\"progressLoss\":${rule.progressLoss},\"refundBasisPoints\":${rule.refundBasisPoints},\"reschedulable\":${rule.reschedulable}}"
        }
        append("}")
        append(",\"consequenceEventCodec\":").append(CanonicalJson.string(consequenceEventCodec)).append('}')
    }

    companion object {
        const val CODEC_ID = "ActionKindPolicyProfile.v1"
    }
}

data class ActionClaimSettlement(
    val resource: ResourceIdentity,
    val quantity: Long,
    val settlement: ResourceSettlement,
    val state: ResourceClaimState
) {
    init {
        require(quantity > 0) { "claim settlement quantity must be positive" }
    }
}

data class ActionInterruptionOutcome(
    val actionId: EntityId,
    val reasonCode: String,
    val result: ActionInterruptionResult,
    val progressBasis: String,
    val progressValue: Long,
    val claimSettlements: List<ActionClaimSettlement>,
    val nextState: ScheduledActionStatus,
    val consequenceEventCodec: String,
    val cancellationStage: CancellationStage?,
    val cancellationRule: ActionCancellationRule?
) {
    init {
        require((cancellationStage == null) == (cancellationRule == null)) { "cancellation stage and rule must be present together" }
        require(isCanonicalToken(reasonCode)) { "interruption reason must be a canonical token" }
        require(progressValue >= 0) { "progress value must be non-negative" }
        require(isNamespacedCodec(consequenceEventCodec)) { "consequence event codec must be namespaced and versioned" }
        require(claimSettlements == claimSettlements.sortedBy { "${it.resource.kind}\u0000${it.resource.id}" }) {
            "claim settlements must be canonical sorted"
        }
        require(claimSettlements.map(ActionClaimSettlement::resource).distinct().size == claimSettlements.size) {
            "claim settlements must be unique"
        }
    }

    fun canonicalJson(): String = buildString {
        append("{\"codec\":").append(CanonicalJson.string(CODEC_ID))
        append(",\"actionId\":").append(CanonicalJson.string(actionId.value))
        append(",\"reasonCode\":").append(CanonicalJson.string(reasonCode))
        append(",\"result\":").append(CanonicalJson.string(result.name))
        append(",\"progressBasis\":").append(CanonicalJson.string(progressBasis))
        append(",\"progressValue\":").append(progressValue)
        append(",\"claimSettlements\":[")
        claimSettlements.joinTo(this, separator = ",") { claim ->
            "{\"resourceKind\":${CanonicalJson.string(claim.resource.kind)},\"resourceId\":${CanonicalJson.string(claim.resource.id)},\"quantity\":${claim.quantity},\"settlement\":${CanonicalJson.string(claim.settlement.name)},\"state\":${CanonicalJson.string(claim.state.name)}}"
        }
        append("]")
        append(",\"nextState\":").append(CanonicalJson.string(nextState.name))
        append(",\"consequenceEventCodec\":").append(CanonicalJson.string(consequenceEventCodec))
        append(",\"cancellationStage\":").append(cancellationStage?.let { CanonicalJson.string(it.name) } ?: "null")
        append(",\"cancellationRule\":")
        if (cancellationRule == null) append("null") else append(
            "{\"progressLoss\":${cancellationRule.progressLoss},\"refundBasisPoints\":${cancellationRule.refundBasisPoints},\"reschedulable\":${cancellationRule.reschedulable}}"
        )
        append('}')
    }

    companion object {
        const val CODEC_ID = "ActionInterruptionOutcome.v1"
    }
}

data class AppliedActionInterruption(
    val calendar: ScheduleCalendar,
    val outcome: ActionInterruptionOutcome
)

private fun isCanonicalToken(value: String): Boolean = value.matches(Regex("[A-Z][A-Z0-9_]{0,63}"))

private fun isNamespacedType(value: String): Boolean = value.matches(Regex("[a-z][a-z0-9]*(?:[._-][a-z0-9]+)+"))

private fun isNamespacedCodec(value: String): Boolean = value.matches(Regex("[a-z][a-z0-9]*(?:[._-][a-z0-9]+)+\\.v[1-9][0-9]*"))

fun ResourceClaimPolicy.settlement(transition: ResourceTransition, stage: CancellationStage = CancellationStage.BEFORE_START): ResourceSettlement = when (transition) {
    ResourceTransition.RESERVE -> if (this == ResourceClaimPolicy.CONSUME_ON_RESERVE) ResourceSettlement.CONSUME else ResourceSettlement.HOLD
    ResourceTransition.START -> if (this == ResourceClaimPolicy.HOLD_THEN_CONSUME_ON_START) ResourceSettlement.CONSUME else ResourceSettlement.HOLD
    ResourceTransition.COMPLETE -> when (this) {
        ResourceClaimPolicy.HOLD_THEN_CONSUME_ON_COMPLETE -> ResourceSettlement.CONSUME
        ResourceClaimPolicy.HOLD_AND_RELEASE -> ResourceSettlement.RELEASE
        else -> ResourceSettlement.HOLD
    }
    ResourceTransition.CANCEL, ResourceTransition.FAIL -> when {
        this == ResourceClaimPolicy.CONSUME_ON_RESERVE -> ResourceSettlement.HOLD
        this == ResourceClaimPolicy.HOLD_THEN_CONSUME_ON_START && stage != CancellationStage.BEFORE_START -> ResourceSettlement.HOLD
        this == ResourceClaimPolicy.HOLD_THEN_CONSUME_ON_COMPLETE && stage == CancellationStage.FINAL_BOUNDARY -> ResourceSettlement.CONSUME
        else -> ResourceSettlement.RELEASE
    }
}

enum class PublicValueState { KNOWN, NONE, UNDETERMINED, UNKNOWN, NOT_APPLICABLE }

data class PublicField(val state: PublicValueState, val value: String? = null) {
    init {
        require((state == PublicValueState.KNOWN) == (value != null)) { "only a known public field may carry a value" }
    }
}

data class PublicScheduleEffect(
    val actionId: EntityId,
    val actionKind: String,
    val state: PublicValueState
)

data class PublicResourceEffect(
    val actionId: EntityId,
    val resource: ResourceIdentity,
    val quantity: Long,
    val state: PublicValueState,
    val settlement: ResourceSettlement? = null
) {
    init {
        require(quantity > 0) { "public resource effect quantity must be positive" }
        require((state == PublicValueState.KNOWN) == (settlement != null)) { "only a known resource effect may carry a settlement" }
    }
}

data class PublicConsequencePreview(
    val actionId: EntityId,
    val currentSchedules: List<PublicScheduleEffect>,
    val proposedSchedule: PublicScheduleEffect,
    val cancelledSchedules: List<PublicScheduleEffect>,
    val refundAmount: PublicField,
    val lossAmount: PublicField,
    val resourceEffects: List<PublicResourceEffect>,
    val progressLoss: PublicField,
    val relationshipImpact: PublicField,
    val reputationImpact: PublicField,
    val rescheduleAvailability: PublicField
) {
    fun canonicalJson(): String = buildString {
        append("{\"actionId\":").append(CanonicalJson.string(actionId.value))
        append(",\"codec\":").append(CanonicalJson.string(CODEC_ID))
        append(",\"currentSchedules\":[")
        appendSchedules(currentSchedules)
        append("],\"proposedSchedule\":")
        appendSchedule(proposedSchedule)
        append(",\"cancelledSchedules\":[")
        appendSchedules(cancelledSchedules)
        append("],\"refundAmount\":").appendField(refundAmount)
        append(",\"lossAmount\":").appendField(lossAmount)
        append(",\"resourceEffects\":[")
        resourceEffects.sortedBy { "${it.actionId.value}\u0000${it.resource.kind}\u0000${it.resource.id}" }.joinTo(this, separator = ",") { effect ->
            "{\"actionId\":${CanonicalJson.string(effect.actionId.value)},\"id\":${CanonicalJson.string(effect.resource.id)},\"kind\":${CanonicalJson.string(effect.resource.kind)},\"quantity\":${effect.quantity},\"state\":${CanonicalJson.string(effect.state.name)},\"settlement\":${effect.settlement?.let { CanonicalJson.string(it.name) } ?: "null"}}"
        }
        append("],\"progressLoss\":").appendField(progressLoss)
        append(",\"relationshipImpact\":").appendField(relationshipImpact)
        append(",\"reputationImpact\":").appendField(reputationImpact)
        append(",\"rescheduleAvailability\":").appendField(rescheduleAvailability)
        append('}')
    }

    private fun StringBuilder.appendSchedules(schedules: List<PublicScheduleEffect>) {
        schedules.sortedBy { it.actionId.value }.joinTo(this, separator = ",") { scheduleJson(it) }
    }

    private fun StringBuilder.appendSchedule(schedule: PublicScheduleEffect) = append(scheduleJson(schedule))

    private fun scheduleJson(schedule: PublicScheduleEffect): String =
        "{\"actionId\":${CanonicalJson.string(schedule.actionId.value)},\"actionKind\":${CanonicalJson.string(schedule.actionKind)},\"state\":${CanonicalJson.string(schedule.state.name)}}"

    private fun StringBuilder.appendField(field: PublicField) = append(
        "{\"state\":${CanonicalJson.string(field.state.name)},\"value\":${field.value?.let(CanonicalJson::string) ?: "null"}}"
    )

    val hash: PayloadHash get() = canonicalPayloadHash(canonicalJson())

    companion object {
        const val CODEC_ID = "PublicConsequencePreview.v1"
    }
}

data class ResourceClaim(val resource: ResourceIdentity, val quantity: Long, val policy: ResourceClaimPolicy) {
    init {
        require(quantity > 0) { "resource claim quantity must be positive" }
    }
}

data class ScheduledActionPayload(
    val ownerType: String,
    val ownerId: EntityId,
    val participants: List<ScheduledEntityRef>,
    val schedulePriority: SchedulePriority,
    val resourceClaims: List<ResourceClaim>,
    val actionKindPolicy: ActionKindPolicyProfile,
    val completionEventType: String,
    val completionEventCodec: String,
    val canBePreempted: Boolean
) {
    init {
        require(isCanonicalToken(ownerType)) { "owner type must be a canonical token" }
        require(participants.isNotEmpty()) { "scheduled action requires a participant" }
        require(participants == participants.sortedWith(compareBy<ScheduledEntityRef>({ it.entityType }, { it.entityId.value }))) {
            "participants must be canonical sorted by type and id"
        }
        require(participants.distinct().size == participants.size) { "participants must be unique typed refs" }
        require(resourceClaims == resourceClaims.sortedBy { "${it.resource.kind}\u0000${it.resource.id}" }) {
            "resource claims must be canonical sorted"
        }
        require(resourceClaims.map(ResourceClaim::resource).distinct().size == resourceClaims.size) { "resource claims must be unique" }
        require(isNamespacedType(completionEventType)) { "completion event type must be namespaced" }
        require(isNamespacedCodec(completionEventCodec)) { "completion event codec must be namespaced and versioned" }
    }

    fun canonicalJson(): String = buildString {
        append("{\"codec\":").append(CanonicalJson.string(CODEC_ID))
        append(",\"ownerType\":").append(CanonicalJson.string(ownerType))
        append(",\"ownerId\":").append(CanonicalJson.string(ownerId.value))
        append(",\"participants\":[")
        participants.joinTo(this, separator = ",") { participant ->
            "{\"entityType\":${CanonicalJson.string(participant.entityType)},\"entityId\":${CanonicalJson.string(participant.entityId.value)}}"
        }
        append("],\"schedulePriority\":").append(CanonicalJson.string(schedulePriority.name))
        append(",\"resourceClaims\":[")
        resourceClaims.joinTo(this, separator = ",") { claim ->
            "{\"resourceKind\":${CanonicalJson.string(claim.resource.kind)},\"resourceId\":${CanonicalJson.string(claim.resource.id)},\"quantity\":${claim.quantity},\"policy\":${CanonicalJson.string(claim.policy.name)}}"
        }
        append("],\"actionKindPolicy\":").append(actionKindPolicy.canonicalJson())
        append(",\"completionEventType\":").append(CanonicalJson.string(completionEventType))
        append(",\"completionEventCodec\":").append(CanonicalJson.string(completionEventCodec))
        append(",\"canBePreempted\":").append(canBePreempted).append('}')
    }

    val hash: PayloadHash get() = canonicalPayloadHash(canonicalJson())

    companion object {
        const val CODEC_ID = "ScheduledActionPayload.v1"
    }
}

object ScheduledActionPayloadCodec {
    fun encode(payload: ScheduledActionPayload): String = payload.canonicalJson()

    fun decode(canonicalJson: String): Checked<ScheduledActionPayload> = try {
        val fields = canonicalObject(canonicalJson) ?: return incompatible("invalid scheduled action payload")
        val payload = ScheduledActionPayload(
            ownerType = fields.requiredString("ownerType"),
            ownerId = checkedEntityId(fields.requiredString("ownerId")),
            participants = canonicalArray(fields.required("participants")).orEmpty().map { encoded ->
                val participant = canonicalObject(encoded) ?: throw IllegalArgumentException("invalid participant")
                ScheduledEntityRef(participant.requiredString("entityType"), checkedEntityId(participant.requiredString("entityId")))
            },
            schedulePriority = SchedulePriority.valueOf(fields.requiredString("schedulePriority")),
            resourceClaims = canonicalArray(fields.required("resourceClaims")).orEmpty().map { encoded ->
                val claim = canonicalObject(encoded) ?: throw IllegalArgumentException("invalid resource claim")
                ResourceClaim(
                    resource = ResourceIdentity(claim.requiredString("resourceKind"), claim.requiredString("resourceId")),
                    quantity = claim.required("quantity").toLong(),
                    policy = ResourceClaimPolicy.valueOf(claim.requiredString("policy"))
                )
            },
            actionKindPolicy = decodePolicyProfile(fields.required("actionKindPolicy")),
            completionEventType = fields.requiredString("completionEventType"),
            completionEventCodec = fields.requiredString("completionEventCodec"),
            canBePreempted = fields.requiredBoolean("canBePreempted")
        )
        if (fields.requiredString("codec") != ScheduledActionPayload.CODEC_ID || encode(payload) != canonicalJson) {
            incompatible("non-canonical scheduled action payload")
        } else {
            Checked.Value(payload)
        }
    } catch (_: IllegalArgumentException) {
        incompatible("invalid ScheduledActionPayload.v1 value")
    } catch (_: ArithmeticException) {
        incompatible("invalid ScheduledActionPayload.v1 number")
    }

    private fun decodePolicyProfile(encoded: String): ActionKindPolicyProfile {
        val fields = canonicalObject(encoded) ?: throw IllegalArgumentException("invalid action kind profile")
        require(fields.requiredString("codec") == ActionKindPolicyProfile.CODEC_ID)
        val interruption = canonicalObject(fields.required("interruptionPolicy"))
            ?: throw IllegalArgumentException("invalid interruption policy")
        val byReason = canonicalObject(interruption.required("resultByReason"))
            ?.mapValues { (_, value) -> ActionInterruptionResult.valueOf(value.jsonString()) }
            ?: throw IllegalArgumentException("invalid interruption reason map")
        val cancellation = canonicalObject(fields.required("cancellationByStage"))
            ?: throw IllegalArgumentException("invalid cancellation policy")
        return ActionKindPolicyProfile(
            resumable = fields.requiredBoolean("resumable"),
            progressBasis = fields.requiredString("progressBasis"),
            interruptionPolicy = ActionInterruptionPolicy(
                defaultResult = ActionInterruptionResult.valueOf(interruption.requiredString("defaultResult")),
                resultByReason = byReason
            ),
            cancellationPolicy = ActionCancellationPolicy(CancellationStage.entries.associateWith { stage ->
                val rule = canonicalObject(cancellation.required(stage.name))
                    ?: throw IllegalArgumentException("invalid cancellation rule")
                ActionCancellationRule(
                    refundBasisPoints = rule.required("refundBasisPoints").toInt(),
                    progressLoss = rule.required("progressLoss").toLong(),
                    reschedulable = rule.requiredBoolean("reschedulable")
                )
            }),
            consequenceEventCodec = fields.requiredString("consequenceEventCodec")
        )
    }

    private fun checkedEntityId(value: String): EntityId = when (val id = EntityId.of(value)) {
        is Checked.Value -> id.value
        is Checked.Rejected -> throw IllegalArgumentException(id.error.toString())
    }

    private fun incompatible(reason: String): Checked<Nothing> =
        Checked.Rejected(DomainError.ContentCompatibilityError(reason))
}

private fun canonicalObject(encoded: String): LinkedHashMap<String, String>? {
    if (encoded.length < 2 || encoded.first() != '{' || encoded.last() != '}') return null
    val result = linkedMapOf<String, String>()
    var cursor = 1
    if (cursor == encoded.lastIndex) return result
    while (cursor < encoded.lastIndex) {
        val keyEnd = jsonTokenEnd(encoded, cursor) ?: return null
        val key = CanonicalJson.parseString(encoded.substring(cursor, keyEnd)) ?: return null
        if (key in result || keyEnd >= encoded.lastIndex || encoded[keyEnd] != ':') return null
        val valueStart = keyEnd + 1
        val valueEnd = jsonTokenEnd(encoded, valueStart) ?: return null
        result[key] = encoded.substring(valueStart, valueEnd)
        cursor = valueEnd
        if (cursor == encoded.lastIndex) return result
        if (encoded[cursor] != ',') return null
        cursor++
    }
    return null
}

private fun canonicalArray(encoded: String): List<String>? {
    if (encoded.length < 2 || encoded.first() != '[' || encoded.last() != ']') return null
    val result = mutableListOf<String>()
    var cursor = 1
    if (cursor == encoded.lastIndex) return result
    while (cursor < encoded.lastIndex) {
        val valueEnd = jsonTokenEnd(encoded, cursor) ?: return null
        result += encoded.substring(cursor, valueEnd)
        cursor = valueEnd
        if (cursor == encoded.lastIndex) return result
        if (encoded[cursor] != ',') return null
        cursor++
    }
    return null
}

private fun jsonTokenEnd(encoded: String, start: Int): Int? {
    if (start !in encoded.indices) return null
    if (encoded[start] == '"') {
        var cursor = start + 1
        while (cursor < encoded.length) {
            when (encoded[cursor++]) {
                '"' -> return cursor
                '\\' -> if (cursor >= encoded.length) return null else cursor++
            }
        }
        return null
    }
    if (encoded[start] == '{' || encoded[start] == '[') {
        val closing = ArrayDeque<Char>()
        closing.addLast(if (encoded[start] == '{') '}' else ']')
        var cursor = start + 1
        var inString = false
        while (cursor < encoded.length) {
            val character = encoded[cursor++]
            if (inString) {
                when (character) {
                    '\\' -> if (cursor >= encoded.length) return null else cursor++
                    '"' -> inString = false
                }
                continue
            }
            when (character) {
                '"' -> inString = true
                '{' -> closing.addLast('}')
                '[' -> closing.addLast(']')
                '}', ']' -> {
                    if (closing.isEmpty() || closing.removeLast() != character) return null
                    if (closing.isEmpty()) return cursor
                }
            }
        }
        return null
    }
    var cursor = start
    while (cursor < encoded.length && encoded[cursor] !in charArrayOf(',', '}', ']')) cursor++
    return cursor.takeIf { it > start }
}

private fun Map<String, String>.required(name: String): String =
    this[name] ?: throw IllegalArgumentException("missing $name")

private fun Map<String, String>.requiredString(name: String): String = required(name).jsonString()

private fun Map<String, String>.requiredBoolean(name: String): Boolean = when (val value = required(name)) {
    "true" -> true
    "false" -> false
    else -> throw IllegalArgumentException("invalid $name: $value")
}

private fun String.jsonString(): String =
    CanonicalJson.parseString(this) ?: throw IllegalArgumentException("invalid canonical string")

data class ResourceClaimKey(val actionId: EntityId, val resource: ResourceIdentity)

enum class ResourceClaimState { HELD, CONSUMED, RELEASED, CANCELLED }

/** Immutable resource accounting used by the authoritative schedule transition. */
data class ResourceLedger(
    val owned: Map<ResourceIdentity, Long>,
    val held: Map<ResourceIdentity, Long> = emptyMap(),
    val consumed: Map<ResourceIdentity, Long> = emptyMap(),
    val claimStates: Map<ResourceClaimKey, ResourceClaimState> = emptyMap()
) {
    init {
        require(owned.values.all { it >= 0 } && held.values.all { it >= 0 } && consumed.values.all { it >= 0 }) {
            "resource quantities must be non-negative"
        }
        require(held.all { (resource, quantity) -> quantity <= (owned[resource] ?: 0L) }) { "held resources must not exceed owned resources" }
    }

    fun available(resource: ResourceIdentity): Long = Math.subtractExact(owned[resource] ?: 0L, held[resource] ?: 0L)

    fun apply(actionId: EntityId, claims: List<ResourceClaim>, transition: ResourceTransition, stage: CancellationStage = CancellationStage.BEFORE_START): Checked<ResourceLedger> {
        var result: Checked<ResourceLedger> = Checked.Value(this)
        for (claim in claims) {
            result = when (result) {
                is Checked.Value -> result.value.applyOne(actionId, claim, transition, stage)
                is Checked.Rejected -> return result
            }
        }
        return result
    }

    private fun applyOne(actionId: EntityId, claim: ResourceClaim, transition: ResourceTransition, stage: CancellationStage): Checked<ResourceLedger> {
        val key = ResourceClaimKey(actionId, claim.resource)
        val state = claimStates[key]
        if (transition == ResourceTransition.RESERVE) {
            if (state != null) return reject("claim is already reserved")
            return when (claim.policy.settlement(transition)) {
                ResourceSettlement.CONSUME -> consume(key, claim)
                ResourceSettlement.HOLD -> hold(key, claim)
                ResourceSettlement.RELEASE -> reject("reserve cannot release a claim")
            }
        }
        if (state == null) return reject("claim was not reserved")
        if (state != ResourceClaimState.HELD) return Checked.Value(this)
        return when (claim.policy.settlement(transition, stage)) {
            ResourceSettlement.CONSUME -> consume(key, claim)
            ResourceSettlement.RELEASE -> release(
                key,
                claim,
                if (transition == ResourceTransition.COMPLETE) ResourceClaimState.RELEASED else ResourceClaimState.CANCELLED
            )
            ResourceSettlement.HOLD -> Checked.Value(this)
        }
    }

    private fun hold(key: ResourceClaimKey, claim: ResourceClaim): Checked<ResourceLedger> = try {
        if (available(claim.resource) < claim.quantity) reject("resource is unavailable")
        else Checked.Value(updated(
            held = held + (claim.resource to Math.addExact(held[claim.resource] ?: 0L, claim.quantity)),
            claimStates = claimStates + (key to ResourceClaimState.HELD)
        ))
    } catch (_: ArithmeticException) {
        reject("resource arithmetic overflow")
    }

    private fun consume(key: ResourceClaimKey, claim: ResourceClaim): Checked<ResourceLedger> = try {
        val previousState = claimStates[key]
        val heldQuantity = if (previousState == ResourceClaimState.HELD) claim.quantity else 0L
        val canConsume = if (heldQuantity == 0L) {
            available(claim.resource) >= claim.quantity
        } else {
            (owned[claim.resource] ?: 0L) >= claim.quantity && (held[claim.resource] ?: 0L) >= claim.quantity
        }
        if (!canConsume) reject("resource is unavailable")
        else Checked.Value(updated(
            owned = owned + (claim.resource to Math.subtractExact(owned[claim.resource] ?: 0L, claim.quantity)),
            held = if (heldQuantity == 0L) held else held + (claim.resource to Math.subtractExact(held[claim.resource] ?: 0L, heldQuantity)),
            consumed = consumed + (claim.resource to Math.addExact(consumed[claim.resource] ?: 0L, claim.quantity)),
            claimStates = claimStates + (key to ResourceClaimState.CONSUMED)
        ))
    } catch (_: ArithmeticException) {
        reject("resource arithmetic overflow")
    }

    private fun release(key: ResourceClaimKey, claim: ResourceClaim, terminalState: ResourceClaimState): Checked<ResourceLedger> = try {
        if ((held[claim.resource] ?: 0L) < claim.quantity) reject("claim hold is unavailable")
        else Checked.Value(updated(
            held = held + (claim.resource to Math.subtractExact(held[claim.resource] ?: 0L, claim.quantity)),
            claimStates = claimStates + (key to terminalState)
        ))
    } catch (_: ArithmeticException) {
        reject("resource arithmetic overflow")
    }

    private fun updated(
        owned: Map<ResourceIdentity, Long> = this.owned,
        held: Map<ResourceIdentity, Long> = this.held,
        consumed: Map<ResourceIdentity, Long> = this.consumed,
        claimStates: Map<ResourceClaimKey, ResourceClaimState> = this.claimStates
    ): ResourceLedger = ResourceLedger(owned, held, consumed, claimStates)

    private fun reject(reason: String): Checked<Nothing> = Checked.Rejected(DomainError.ResourceUnavailable(reason))
}

data class ScheduledAction(
    val actionId: EntityId,
    val actionKind: String,
    val payload: ScheduledActionPayload,
    val startMinute: GameMinute,
    val dueMinute: GameMinute,
    val status: ScheduledActionStatus = ScheduledActionStatus.RESERVED,
    val rowVersion: StateVersion = StateVersion(0)
) {
    init {
        require(isNamespacedType(actionKind)) { "action kind must be namespaced" }
        require(dueMinute.value > startMinute.value) { "scheduled action due minute must be after start minute" }
    }

    val participants: List<ScheduledEntityRef> get() = payload.participants
    val claims: List<ResourceClaim> get() = payload.resourceClaims
    val priority: SchedulePriority get() = payload.schedulePriority
    val canBePreempted: Boolean get() = payload.canBePreempted

    fun overlaps(other: ScheduledAction): Boolean =
        startMinute.value < other.dueMinute.value && other.startMinute.value < dueMinute.value

    fun occupiesParticipants(): Boolean = status in setOf(
        ScheduledActionStatus.PLANNED,
        ScheduledActionStatus.RESERVED,
        ScheduledActionStatus.RUNNING
    )
}

data class ScheduleCalendar(
    val actions: List<ScheduledAction>,
    val resources: ResourceLedger
) {
    constructor(actions: List<ScheduledAction>, ownedResources: Map<ResourceIdentity, Long>) :
        this(actions, bootstrapLedger(actions, ownedResources))

    init {
        require(actions.map(ScheduledAction::actionId).distinct().size == actions.size) { "action ids must be unique" }
        val claimsByKey = actions.flatMap { action ->
            action.claims.map { claim -> ResourceClaimKey(action.actionId, claim.resource) to claim }
        }.toMap()
        require(resources.claimStates.keys.all(claimsByKey::containsKey)) { "resource claim state must reference a scheduled action claim" }
        val expectedHeld = resources.claimStates.asSequence()
            .filter { it.value == ResourceClaimState.HELD }
            .map { (key, _) -> claimsByKey.getValue(key) }
            .groupingBy(ResourceClaim::resource)
            .fold(0L) { total, claim -> Math.addExact(total, claim.quantity) }
        require(nonZero(resources.held) == nonZero(expectedHeld)) { "held totals must match durable claim states" }
        val expectedConsumed = resources.claimStates.asSequence()
            .filter { it.value == ResourceClaimState.CONSUMED }
            .map { (key, _) -> claimsByKey.getValue(key) }
            .groupingBy(ResourceClaim::resource)
            .fold(0L) { total, claim -> Math.addExact(total, claim.quantity) }
        require(nonZero(resources.consumed) == nonZero(expectedConsumed)) { "consumed totals must match durable claim states" }
        actions.filter { it.status in CLAIM_STATE_REQUIRED_STATUSES }.forEach { action ->
            action.claims.forEach { claim ->
                require(ResourceClaimKey(action.actionId, claim.resource) in resources.claimStates) {
                    "non-planned scheduled action claim must have a durable state"
                }
            }
        }
        actions.filter { it.status in ACTIVE_CLAIM_STATUSES }.forEach { action ->
            action.claims.forEach { claim ->
                require(resources.claimStates[ResourceClaimKey(action.actionId, claim.resource)] in ACTIVE_CLAIM_STATES) {
                    "active scheduled action claim must have a durable held or consumed state"
                }
            }
        }
        actions.filter { it.status == ScheduledActionStatus.COMPLETED }.forEach { action ->
            action.claims.forEach { claim ->
                require(resources.claimStates[ResourceClaimKey(action.actionId, claim.resource)] in COMPLETED_CLAIM_STATES) {
                    "completed scheduled action claim must be consumed or released"
                }
            }
        }
        actions.filter { it.status in INTERRUPTED_TERMINAL_STATUSES }.forEach { action ->
            action.claims.forEach { claim ->
                val state = resources.claimStates[ResourceClaimKey(action.actionId, claim.resource)]
                require(state == null || state in INTERRUPTED_TERMINAL_CLAIM_STATES) {
                    "cancelled, failed, or rescheduled claim must be unreserved, consumed, or cancelled"
                }
            }
        }
    }

    val ownedResources: Map<ResourceIdentity, Long> get() = resources.owned

    fun heldTotal(resource: ResourceIdentity): Long = resources.held[resource] ?: 0L

    fun available(resource: ResourceIdentity): Long = resources.available(resource)

    private companion object {
        val ACTIVE_CLAIM_STATUSES = setOf(ScheduledActionStatus.RESERVED, ScheduledActionStatus.RUNNING, ScheduledActionStatus.PAUSED)
        val ACTIVE_CLAIM_STATES = setOf(ResourceClaimState.HELD, ResourceClaimState.CONSUMED)
        val COMPLETED_CLAIM_STATES = setOf(ResourceClaimState.CONSUMED, ResourceClaimState.RELEASED)
        val CLAIM_STATE_REQUIRED_STATUSES = ACTIVE_CLAIM_STATUSES + ScheduledActionStatus.NEEDS_RESCHEDULE + ScheduledActionStatus.COMPLETED
        val INTERRUPTED_TERMINAL_STATUSES = setOf(ScheduledActionStatus.NEEDS_RESCHEDULE, ScheduledActionStatus.CANCELLED, ScheduledActionStatus.FAILED)
        val INTERRUPTED_TERMINAL_CLAIM_STATES = setOf(ResourceClaimState.CONSUMED, ResourceClaimState.CANCELLED)
    }
}

private fun nonZero(values: Map<ResourceIdentity, Long>): Map<ResourceIdentity, Long> = values.filterValues { it != 0L }

private fun bootstrapLedger(actions: List<ScheduledAction>, ownedResources: Map<ResourceIdentity, Long>): ResourceLedger {
    var ledger = ResourceLedger(ownedResources)
    actions.sortedBy { it.actionId.value }.forEach { action ->
        if (action.claims.isEmpty() || action.status == ScheduledActionStatus.PLANNED) return@forEach
        require(action.status == ScheduledActionStatus.RESERVED) {
            "non-reserved fixture actions with claims require an explicit durable ResourceLedger"
        }
        ledger = when (val result = ledger.apply(action.actionId, action.claims, ResourceTransition.RESERVE)) {
            is Checked.Value -> result.value
            is Checked.Rejected -> throw IllegalArgumentException(result.error.toString())
        }
    }
    return ledger
}

data class ReservationRequest(
    val actionId: EntityId,
    val actionKind: String,
    val payload: ScheduledActionPayload,
    val startMinute: GameMinute,
    val dueMinute: GameMinute,
    val effectiveMinute: GameMinute
) {
    fun asAction(): ScheduledAction {
        require(startMinute.value >= effectiveMinute.value) { "scheduled action start must not be before the command effective minute" }
        return ScheduledAction(actionId, actionKind, payload, startMinute, dueMinute)
    }
}

data class ScheduleConflictResult(
    val conflictingActionIds: List<EntityId>,
    val conflictingRowVersions: Map<EntityId, StateVersion>,
    val requestedPriority: SchedulePriority,
    val existingPriorities: List<SchedulePriority>,
    val allowedResolutions: Set<ScheduleResolution>,
    val consequencePreview: PublicConsequencePreview
)

sealed interface ReservationResult {
    data class Accepted(val calendar: ScheduleCalendar, val action: ScheduledAction) : ReservationResult
    data class Conflict(val details: ScheduleConflictResult) : ReservationResult
    data class Rejected(val error: DomainError) : ReservationResult
}

sealed interface ResolutionResult {
    data class Applied(val calendar: ScheduleCalendar, val inserted: ScheduledAction) : ResolutionResult
    data class RescheduleRequired(val calendar: ScheduleCalendar, val conflict: ScheduleConflictResult) : ResolutionResult
    data class Rejected(val error: DomainError) : ResolutionResult
}

data class ActionTransition(val calendar: ScheduleCalendar, val action: ScheduledAction)

class ScheduleService {
    fun reserve(request: ReservationRequest, calendar: ScheduleCalendar): ReservationResult {
        val proposed = try {
            request.asAction()
        } catch (error: IllegalArgumentException) {
            return ReservationResult.Rejected(DomainError.ValidationError("schedule", error.message.orEmpty()))
        }
        if (calendar.actions.any { it.actionId == proposed.actionId }) {
            return ReservationResult.Rejected(DomainError.ValidationError("actionId", "already exists"))
        }
        val conflicts = calendar.actions.filter { existing ->
            existing.occupiesParticipants() && existing.overlaps(proposed) &&
                existing.participants.any(proposed.participants::contains)
        }
        if (conflicts.isNotEmpty()) return ReservationResult.Conflict(conflictFor(proposed, conflicts))

        val unavailable = try {
            proposed.claims.firstOrNull { claim -> calendar.available(claim.resource) < claim.quantity }
        } catch (_: ArithmeticException) {
            return ReservationResult.Rejected(DomainError.ArithmeticOverflow("resource availability"))
        }
        unavailable?.let { claim ->
            return ReservationResult.Rejected(DomainError.ResourceUnavailable("${claim.resource.kind}:${claim.resource.id}"))
        }
        val reservedResources = when (val result = calendar.resources.apply(proposed.actionId, proposed.claims, ResourceTransition.RESERVE)) {
            is Checked.Value -> result.value
            is Checked.Rejected -> return ReservationResult.Rejected(result.error)
        }
        val startsNow = proposed.startMinute == request.effectiveMinute
        val resources = if (startsNow) {
            when (val result = reservedResources.apply(proposed.actionId, proposed.claims, ResourceTransition.START)) {
                is Checked.Value -> result.value
                is Checked.Rejected -> return ReservationResult.Rejected(result.error)
            }
        } else {
            reservedResources
        }
        val accepted = proposed.copy(status = if (startsNow) ScheduledActionStatus.RUNNING else ScheduledActionStatus.RESERVED)
        val acceptedCalendar = calendar.copy(actions = calendar.actions + accepted, resources = resources)
        return ReservationResult.Accepted(acceptedCalendar, accepted)
    }

    fun resolveConflict(
        request: ReservationRequest,
        calendar: ScheduleCalendar,
        selected: ScheduleResolution,
        expectedVersions: Map<EntityId, StateVersion>,
        previewCodec: String,
        previewHash: PayloadHash
    ): ResolutionResult {
        val proposed = try {
            request.asAction()
        } catch (error: IllegalArgumentException) {
            return ResolutionResult.Rejected(DomainError.ValidationError("schedule", error.message.orEmpty()))
        }
        val conflicts = calendar.actions.filter { existing ->
            existing.occupiesParticipants() && existing.overlaps(proposed) &&
                existing.participants.any(proposed.participants::contains)
        }
        if (conflicts.isEmpty()) return ResolutionResult.Rejected(DomainError.StaleConsequencePreview(proposed.actionId))
        val conflict = conflictFor(proposed, conflicts)
        if (previewCodec != PublicConsequencePreview.CODEC_ID || previewHash != conflict.consequencePreview.hash) {
            return ResolutionResult.Rejected(DomainError.StaleConsequencePreview(proposed.actionId))
        }
        if (selected !in conflict.allowedResolutions || selected == ScheduleResolution.KEEP_EXISTING) {
            return ResolutionResult.Rejected(DomainError.ScheduleConflict(conflict.conflictingActionIds))
        }
        conflicts.firstOrNull { expectedVersions[it.actionId] != it.rowVersion }?.let { stale ->
            return ResolutionResult.Rejected(DomainError.StaleConsequencePreview(stale.actionId))
        }
        if (selected == ScheduleResolution.RESCHEDULE_REQUIRED) {
            return ResolutionResult.RescheduleRequired(calendar, conflict)
        }
        var revisedCalendar = calendar
        for (action in conflicts.sortedBy { it.actionId.value }) {
            val transition = when (selected) {
                ScheduleResolution.PREEMPT -> preempt(action.actionId, revisedCalendar)
                ScheduleResolution.PAUSE_AND_INSERT -> pause(action.actionId, revisedCalendar)
                ScheduleResolution.CANCEL_AND_INSERT -> cancel(action.actionId, revisedCalendar, cancellationStage(action))
                ScheduleResolution.KEEP_EXISTING, ScheduleResolution.RESCHEDULE_REQUIRED ->
                    return ResolutionResult.Rejected(DomainError.ScheduleConflict(conflict.conflictingActionIds))
            }
            revisedCalendar = when (transition) {
                is Checked.Value -> transition.value.calendar
                is Checked.Rejected -> return ResolutionResult.Rejected(transition.error)
            }
        }
        return when (val reservation = reserve(request, revisedCalendar)) {
            is ReservationResult.Accepted -> ResolutionResult.Applied(reservation.calendar, reservation.action)
            is ReservationResult.Rejected -> ResolutionResult.Rejected(reservation.error)
            is ReservationResult.Conflict -> ResolutionResult.Rejected(DomainError.ScheduleConflict(reservation.details.conflictingActionIds))
        }
    }

    fun start(actionId: EntityId, calendar: ScheduleCalendar, effectiveMinute: GameMinute): Checked<ActionTransition> {
        val action = calendar.actions.firstOrNull { it.actionId == actionId }
            ?: return Checked.Rejected(DomainError.ValidationError("actionId", "does not exist"))
        if (action.startMinute != effectiveMinute) {
            return Checked.Rejected(DomainError.ValidationError("effectiveMinute", "must equal the scheduled start minute"))
        }
        if (action.status == ScheduledActionStatus.PLANNED && action.claims.isNotEmpty()) {
            return Checked.Rejected(DomainError.ValidationError("action.status", "resource claims must be reserved before start"))
        }
        return transition(
            actionId,
            calendar,
            setOf(ScheduledActionStatus.PLANNED, ScheduledActionStatus.RESERVED),
            ScheduledActionStatus.RUNNING,
            ResourceTransition.START
        )
    }

    fun complete(actionId: EntityId, calendar: ScheduleCalendar): Checked<ActionTransition> = transition(
        actionId, calendar, setOf(ScheduledActionStatus.RUNNING), ScheduledActionStatus.COMPLETED, ResourceTransition.COMPLETE
    )

    fun pause(actionId: EntityId, calendar: ScheduleCalendar): Checked<ActionTransition> = transition(
        actionId, calendar, setOf(ScheduledActionStatus.RUNNING), ScheduledActionStatus.PAUSED, null
    )

    fun resume(
        actionId: EntityId,
        calendar: ScheduleCalendar,
        resumeMinute: GameMinute,
        newDueMinute: GameMinute
    ): Checked<ActionTransition> {
        val index = calendar.actions.indexOfFirst { it.actionId == actionId }
        if (index < 0) return Checked.Rejected(DomainError.ValidationError("actionId", "does not exist"))
        val action = calendar.actions[index]
        if (action.status != ScheduledActionStatus.PAUSED) {
            return Checked.Rejected(DomainError.ValidationError("action.status", "only a paused action can resume"))
        }
        if (!action.payload.actionKindPolicy.resumable) {
            return Checked.Rejected(DomainError.ValidationError("actionKindPolicy", "action is not resumable"))
        }
        if (newDueMinute.value <= resumeMinute.value) {
            return Checked.Rejected(DomainError.ValidationError("newDueMinute", "must be after resume minute"))
        }
        val resumed = action.copy(startMinute = resumeMinute, dueMinute = newDueMinute, status = ScheduledActionStatus.RUNNING)
        val conflicts = calendar.actions.filterIndexed { candidateIndex, candidate ->
            candidateIndex != index && candidate.occupiesParticipants() && candidate.overlaps(resumed) &&
                candidate.participants.any(resumed.participants::contains)
        }
        if (conflicts.isNotEmpty()) {
            return Checked.Rejected(DomainError.ScheduleConflict(conflicts.map(ScheduledAction::actionId).sortedBy(EntityId::value)))
        }
        val nextVersion = try {
            StateVersion(Math.addExact(action.rowVersion.value, 1L))
        } catch (_: ArithmeticException) {
            return Checked.Rejected(DomainError.ArithmeticOverflow("scheduled action row version"))
        }
        val updated = resumed.copy(rowVersion = nextVersion)
        val actions = calendar.actions.toMutableList().also { it[index] = updated }
        return Checked.Value(ActionTransition(calendar.copy(actions = actions), updated))
    }

    fun cancel(actionId: EntityId, calendar: ScheduleCalendar, stage: CancellationStage): Checked<ActionTransition> {
        validateStage(actionId, calendar, stage)?.let { return Checked.Rejected(it) }
        return transition(
            actionId,
            calendar,
            NON_TERMINAL_STATUSES,
            ScheduledActionStatus.CANCELLED,
            ResourceTransition.CANCEL,
            stage
        )
    }

    fun fail(actionId: EntityId, calendar: ScheduleCalendar, stage: CancellationStage): Checked<ActionTransition> {
        validateStage(actionId, calendar, stage)?.let { return Checked.Rejected(it) }
        return transition(
            actionId,
            calendar,
            NON_TERMINAL_STATUSES,
            ScheduledActionStatus.FAILED,
            ResourceTransition.FAIL,
            stage
        )
    }

    fun preempt(actionId: EntityId, calendar: ScheduleCalendar): Checked<ActionTransition> {
        val action = calendar.actions.firstOrNull { it.actionId == actionId }
            ?: return Checked.Rejected(DomainError.ValidationError("actionId", "does not exist"))
        return transition(
            actionId,
            calendar,
            setOf(ScheduledActionStatus.RESERVED, ScheduledActionStatus.RUNNING, ScheduledActionStatus.PAUSED),
            ScheduledActionStatus.NEEDS_RESCHEDULE,
            ResourceTransition.CANCEL,
            cancellationStage(action)
        )
    }

    fun applyInterruption(
        actionId: EntityId,
        calendar: ScheduleCalendar,
        reasonCode: String,
        progressValue: Long,
        stage: CancellationStage
    ): Checked<AppliedActionInterruption> {
        if (!isCanonicalToken(reasonCode)) {
            return Checked.Rejected(DomainError.ValidationError("reasonCode", "must be a canonical token"))
        }
        if (progressValue < 0) {
            return Checked.Rejected(DomainError.ValidationError("progressValue", "must be non-negative"))
        }
        val action = calendar.actions.firstOrNull { it.actionId == actionId }
            ?: return Checked.Rejected(DomainError.ValidationError("actionId", "does not exist"))
        if (action.status in TERMINAL_STATUSES) {
            return Checked.Rejected(DomainError.ValidationError("action.status", "action is already terminal"))
        }
        val profile = action.payload.actionKindPolicy
        val result = profile.interruptionPolicy.resultFor(reasonCode)
        val cancellationRule = if (result in CANCELLING_INTERRUPTION_RESULTS) profile.cancellationPolicy.ruleFor(stage) else null
        if (cancellationRule != null && cancellationRule.progressLoss > progressValue) {
            return Checked.Rejected(DomainError.ValidationError("progressValue", "is less than the configured progress loss"))
        }
        if (result == ActionInterruptionResult.RESCHEDULE_REQUIRED) {
            validateStage(actionId, calendar, stage)?.let { return Checked.Rejected(it) }
        }
        val transition = when (result) {
            ActionInterruptionResult.CONTINUE -> Checked.Value(ActionTransition(calendar, action))
            ActionInterruptionResult.PAUSE -> pause(actionId, calendar)
            ActionInterruptionResult.CANCEL -> cancel(actionId, calendar, stage)
            ActionInterruptionResult.FAIL -> fail(actionId, calendar, stage)
            ActionInterruptionResult.RESCHEDULE_REQUIRED -> preempt(actionId, calendar)
        }
        val applied = when (transition) {
            is Checked.Value -> transition.value
            is Checked.Rejected -> return transition
        }
        val claimSettlements = action.claims.mapNotNull { claim ->
            val before = calendar.resources.claimStates[ResourceClaimKey(actionId, claim.resource)]
            val after = applied.calendar.resources.claimStates[ResourceClaimKey(actionId, claim.resource)]
            if (before == null && after == null) return@mapNotNull null
            if (after == null) return Checked.Rejected(DomainError.ValidationError("resourceClaim", "missing durable state after interruption"))
            val settlement = when {
                after == ResourceClaimState.CONSUMED -> ResourceSettlement.CONSUME
                after in setOf(ResourceClaimState.RELEASED, ResourceClaimState.CANCELLED) -> ResourceSettlement.RELEASE
                else -> ResourceSettlement.HOLD
            }
            ActionClaimSettlement(claim.resource, claim.quantity, settlement, after)
        }
        return Checked.Value(
            AppliedActionInterruption(
                calendar = applied.calendar,
                outcome = ActionInterruptionOutcome(
                    actionId = actionId,
                    reasonCode = reasonCode,
                    result = result,
                    progressBasis = profile.progressBasis,
                    progressValue = progressValue,
                    claimSettlements = claimSettlements,
                    nextState = applied.action.status,
                    consequenceEventCodec = profile.consequenceEventCodec,
                    cancellationStage = stage.takeIf { cancellationRule != null },
                    cancellationRule = cancellationRule
                )
            )
        )
    }

    private fun transition(
        actionId: EntityId,
        calendar: ScheduleCalendar,
        allowedStatuses: Set<ScheduledActionStatus>,
        nextStatus: ScheduledActionStatus,
        resourceTransition: ResourceTransition?,
        stage: CancellationStage = CancellationStage.BEFORE_START
    ): Checked<ActionTransition> {
        val index = calendar.actions.indexOfFirst { it.actionId == actionId }
        if (index < 0) return Checked.Rejected(DomainError.ValidationError("actionId", "does not exist"))
        val action = calendar.actions[index]
        if (action.status !in allowedStatuses) {
            return Checked.Rejected(DomainError.ValidationError("action.status", "cannot transition from ${action.status} to $nextStatus"))
        }
        val resources = if (resourceTransition == null || action.status == ScheduledActionStatus.PLANNED) {
            calendar.resources
        } else {
            when (val result = calendar.resources.apply(actionId, action.claims, resourceTransition, stage)) {
                is Checked.Value -> result.value
                is Checked.Rejected -> return result
            }
        }
        val nextVersion = try {
            StateVersion(Math.addExact(action.rowVersion.value, 1L))
        } catch (_: ArithmeticException) {
            return Checked.Rejected(DomainError.ArithmeticOverflow("scheduled action row version"))
        }
        val updated = action.copy(status = nextStatus, rowVersion = nextVersion)
        val actions = calendar.actions.toMutableList().also { it[index] = updated }
        return Checked.Value(ActionTransition(calendar.copy(actions = actions, resources = resources), updated))
    }

    private fun cancellationStage(action: ScheduledAction): CancellationStage = when (action.status) {
        ScheduledActionStatus.RUNNING, ScheduledActionStatus.PAUSED -> CancellationStage.IN_PROGRESS
        else -> CancellationStage.BEFORE_START
    }

    private fun validateStage(actionId: EntityId, calendar: ScheduleCalendar, stage: CancellationStage): DomainError? {
        val status = calendar.actions.firstOrNull { it.actionId == actionId }?.status
            ?: return DomainError.ValidationError("actionId", "does not exist")
        val valid = when (stage) {
            CancellationStage.BEFORE_START -> status in setOf(ScheduledActionStatus.PLANNED, ScheduledActionStatus.RESERVED, ScheduledActionStatus.NEEDS_RESCHEDULE)
            CancellationStage.IN_PROGRESS, CancellationStage.FINAL_BOUNDARY -> status in setOf(ScheduledActionStatus.RUNNING, ScheduledActionStatus.PAUSED)
        }
        return if (valid) null else DomainError.ValidationError("cancellationStage", "$stage does not match $status")
    }

    private fun conflictFor(proposed: ScheduledAction, conflicts: List<ScheduledAction>): ScheduleConflictResult {
        val orderedConflicts = conflicts.sortedBy { it.actionId.value }
        val canReplace = orderedConflicts.all { it.canBePreempted && proposed.priority.rank > it.priority.rank }
        val canPause = canReplace && orderedConflicts.all { it.status == ScheduledActionStatus.RUNNING }
        val cancellationRules = orderedConflicts.map { action ->
            action.payload.actionKindPolicy.cancellationPolicy.ruleFor(cancellationStage(action))
        }
        val totalProgressLoss = runCatching {
            cancellationRules.fold(0L) { total, rule -> Math.addExact(total, rule.progressLoss) }
        }.getOrNull()
        val hasClaimImpact = orderedConflicts.any { it.claims.isNotEmpty() }
        val allowed = buildSet {
            add(ScheduleResolution.KEEP_EXISTING)
            add(ScheduleResolution.RESCHEDULE_REQUIRED)
            if (canReplace) {
                add(ScheduleResolution.PREEMPT)
                add(ScheduleResolution.CANCEL_AND_INSERT)
            }
            if (canPause) add(ScheduleResolution.PAUSE_AND_INSERT)
        }
        return ScheduleConflictResult(
            conflictingActionIds = orderedConflicts.map(ScheduledAction::actionId),
            conflictingRowVersions = orderedConflicts.associate { it.actionId to it.rowVersion },
            requestedPriority = proposed.priority,
            existingPriorities = orderedConflicts.map(ScheduledAction::priority),
            allowedResolutions = allowed,
            consequencePreview = PublicConsequencePreview(
                actionId = proposed.actionId,
                currentSchedules = orderedConflicts.map {
                    PublicScheduleEffect(it.actionId, it.actionKind, PublicValueState.KNOWN)
                },
                proposedSchedule = PublicScheduleEffect(proposed.actionId, proposed.actionKind, PublicValueState.KNOWN),
                cancelledSchedules = orderedConflicts.map {
                    PublicScheduleEffect(it.actionId, it.actionKind, if (canReplace) PublicValueState.UNDETERMINED else PublicValueState.NOT_APPLICABLE)
                },
                refundAmount = PublicField(if (hasClaimImpact) PublicValueState.UNKNOWN else PublicValueState.NONE),
                lossAmount = PublicField(if (hasClaimImpact) PublicValueState.UNKNOWN else PublicValueState.NONE),
                resourceEffects = buildList {
                    orderedConflicts.forEach { action ->
                        action.claims.forEach { claim ->
                            add(PublicResourceEffect(action.actionId, claim.resource, claim.quantity, PublicValueState.UNDETERMINED))
                        }
                    }
                    proposed.claims.forEach { claim ->
                        add(
                            PublicResourceEffect(
                                proposed.actionId,
                                claim.resource,
                                claim.quantity,
                                PublicValueState.KNOWN,
                                claim.policy.settlement(ResourceTransition.RESERVE)
                            )
                        )
                    }
                },
                progressLoss = when {
                    !canReplace -> PublicField(PublicValueState.NOT_APPLICABLE)
                    totalProgressLoss == null -> PublicField(PublicValueState.UNKNOWN)
                    totalProgressLoss == 0L -> PublicField(PublicValueState.NONE)
                    else -> PublicField(PublicValueState.KNOWN, totalProgressLoss.toString())
                },
                relationshipImpact = PublicField(PublicValueState.UNKNOWN),
                reputationImpact = PublicField(PublicValueState.UNKNOWN),
                rescheduleAvailability = PublicField(PublicValueState.KNOWN, canReplace.toString())
            )
        )
    }

    private companion object {
        val TERMINAL_STATUSES = setOf(ScheduledActionStatus.COMPLETED, ScheduledActionStatus.CANCELLED, ScheduledActionStatus.FAILED)
        val NON_TERMINAL_STATUSES = ScheduledActionStatus.entries.toSet() - TERMINAL_STATUSES
        val CANCELLING_INTERRUPTION_RESULTS = setOf(
            ActionInterruptionResult.CANCEL,
            ActionInterruptionResult.FAIL,
            ActionInterruptionResult.RESCHEDULE_REQUIRED
        )
    }
}

class ScheduledActionBoundarySource : BoundarySource {
    override val sourceId: String = "scheduled-action"

    override fun nextTimeAfter(snapshot: WorldTraversalSnapshot, cursor: BoundaryCursor?): GameMinute? = snapshot.calendar.actions.asSequence()
        .flatMap { action -> when (action.status) {
            ScheduledActionStatus.PLANNED -> if (action.claims.isEmpty()) sequenceOf(action.startMinute) else emptySequence()
            ScheduledActionStatus.RESERVED -> sequenceOf(action.startMinute, action.dueMinute)
            ScheduledActionStatus.RUNNING -> sequenceOf(action.dueMinute)
            else -> emptySequence()
        } }
        .filter { it.value > snapshot.clock.minute.value }
        .minByOrNull(GameMinute::value)

    override fun candidatesAt(snapshot: WorldTraversalSnapshot, time: GameMinute): List<BoundaryCandidate> = snapshot.calendar.actions.asSequence()
        .flatMap { action -> sequence {
            if ((action.status == ScheduledActionStatus.RESERVED || action.status == ScheduledActionStatus.PLANNED && action.claims.isEmpty()) && action.startMinute == time) {
                yield(candidate(action, BoundaryCategory.SCHEDULED_ACTION_START, "scheduled.action.start.v1"))
            }
            if (action.status in setOf(ScheduledActionStatus.RESERVED, ScheduledActionStatus.RUNNING) && action.dueMinute == time) {
                yield(candidate(action, BoundaryCategory.SCHEDULED_ACTION_COMPLETE, "scheduled.action.complete.v1"))
            }
        } }
        .sortedWith(compareBy({ it.key.category.rank }, { it.key.stableEntityId }))
        .toList()

    private fun candidate(action: ScheduledAction, category: BoundaryCategory, kind: String): BoundaryCandidate = BoundaryCandidate(
        key = BoundaryKey(action.startMinute.takeIf { category == BoundaryCategory.SCHEDULED_ACTION_START } ?: action.dueMinute, category, 0, 0, action.actionId.value, "", sourceId),
        candidateKind = kind,
        payloadCodec = ScheduledActionPayload.CODEC_ID,
        canonicalPayload = "{\"actionId\":${CanonicalJson.string(action.actionId.value)},\"actionKind\":${CanonicalJson.string(action.actionKind)},\"dueMinute\":${action.dueMinute.value},\"scheduledActionPayload\":${action.payload.canonicalJson()},\"scheduledActionPayloadHash\":${CanonicalJson.string(action.payload.hash.value)},\"startMinute\":${action.startMinute.value}}",
        eventType = kind,
        importance = EventImportance.NORMAL,
        subjectId = action.actionId
    )
}
