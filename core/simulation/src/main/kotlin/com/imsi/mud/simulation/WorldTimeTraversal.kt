package com.imsi.mud.simulation

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.text.Normalizer

data class WorldClock(val minute: GameMinute, val subMinuteMs: SubMinuteMillis = SubMinuteMillis(0))

enum class ProgressionMode { FAST_FORWARD, COMBAT_ELAPSED, DUNGEON_ACTION, TRAVEL, NORMAL_ACTION }

sealed interface TimeAdvanceGoal {
    fun isSatisfied(snapshot: WorldTraversalSnapshot): Boolean

    data class UntilMinute(val minute: GameMinute) : TimeAdvanceGoal {
        override fun isSatisfied(snapshot: WorldTraversalSnapshot): Boolean = snapshot.clock.minute.value >= minute.value
    }

    data class UntilFirstActionCompleted(val actionSelector: ActionSelector) : TimeAdvanceGoal {
        override fun isSatisfied(snapshot: WorldTraversalSnapshot): Boolean = actionSelector.selectedActionIds(snapshot).any(snapshot.completedActionIds::contains)
    }

    data class UntilAllActionsCompleted(val actionSelector: ActionSelector) : TimeAdvanceGoal {
        override fun isSatisfied(snapshot: WorldTraversalSnapshot): Boolean = actionSelector.selectedActionIds(snapshot).let { it.isNotEmpty() && it.all(snapshot.completedActionIds::contains) }
    }

    data class UntilCondition(val condition: ConditionRef) : TimeAdvanceGoal {
        override fun isSatisfied(snapshot: WorldTraversalSnapshot): Boolean = condition.hash in snapshot.satisfiedConditionHashes
    }

    data class UntilEvent(val selector: EventSelector) : TimeAdvanceGoal {
        override fun isSatisfied(snapshot: WorldTraversalSnapshot): Boolean = snapshot.observedEvents.any(selector::matches)
    }
}

sealed interface ActionSelector {
    fun selectedActionIds(snapshot: WorldTraversalSnapshot): Set<EntityId>
    fun canonicalJson(): String

    data class ActionId(val actionId: EntityId) : ActionSelector {
        override fun selectedActionIds(snapshot: WorldTraversalSnapshot): Set<EntityId> = setOf(actionId)
        override fun canonicalJson(): String = "{\"actionId\":${CanonicalJson.string(actionId.value)},\"codec\":\"ActionSelector.v1\"}"
    }

    data class Canonical(val codec: String, val payload: String) : ActionSelector {
        init { require(codec.isNotBlank() && payload.isNotBlank()) { "action selector codec and payload must not be blank" } }
        override fun selectedActionIds(snapshot: WorldTraversalSnapshot): Set<EntityId> = snapshot.selectedActionIdsBySelector[canonicalPayloadHash(canonicalJson())].orEmpty()
        override fun canonicalJson(): String = "{\"codec\":${CanonicalJson.string(codec)},\"payload\":${CanonicalJson.string(payload)}}"
    }
}

data class ConditionRef(val codec: String, val payload: String) {
    init { require(codec.isNotBlank() && payload.isNotBlank()) { "condition codec and payload must not be blank" } }
    fun canonicalJson(): String = "{\"codec\":${CanonicalJson.string(codec)},\"payload\":${CanonicalJson.string(payload)}}"
    val hash: PayloadHash get() = canonicalPayloadHash(canonicalJson())
}

data class EventSelector(val eventType: String, val minImportance: EventImportance = EventImportance.LOW, val subjectId: EntityId? = null) {
    init { require(eventType.isNotBlank()) { "event type must not be blank" } }
    fun matches(event: ObservedBoundaryEvent): Boolean = event.eventType == eventType && event.importance.ordinal >= minImportance.ordinal && (subjectId == null || subjectId == event.subjectId)
}

data class ObservedBoundaryEvent(val eventType: String, val importance: EventImportance, val subjectId: EntityId?)

object TimeAdvanceGoalCodec {
    const val CODEC_ID = "TIME_ADVANCE_GOAL.v1"

    fun encode(goal: TimeAdvanceGoal): String = when (goal) {
        is TimeAdvanceGoal.UntilMinute -> envelope("UNTIL_MINUTE", "{\"targetMinute\":${goal.minute.value}}")
        is TimeAdvanceGoal.UntilFirstActionCompleted -> selectorGoal("UNTIL_FIRST_ACTION_COMPLETED", goal.actionSelector)
        is TimeAdvanceGoal.UntilAllActionsCompleted -> selectorGoal("UNTIL_ALL_ACTIONS_COMPLETED", goal.actionSelector)
        is TimeAdvanceGoal.UntilCondition -> envelope("UNTIL_CONDITION", "{\"condition\":${CanonicalJson.string(goal.condition.canonicalJson())}}")
        is TimeAdvanceGoal.UntilEvent -> envelope("UNTIL_EVENT", "{\"eventType\":${CanonicalJson.string(goal.selector.eventType)},\"minImportance\":${CanonicalJson.string(goal.selector.minImportance.name)},\"subjectId\":${goal.selector.subjectId?.let { CanonicalJson.string(it.value) } ?: "null"}}")
    }

    fun hash(goal: TimeAdvanceGoal): PayloadHash = canonicalPayloadHash(encode(goal))

    fun decode(canonicalJson: String): Checked<TimeAdvanceGoal> = try {
        val codec = stringField(canonicalJson, "codec") ?: return incompatible("missing goal codec")
        if (codec != CODEC_ID) return incompatible("unsupported goal codec: $codec")
        val goalPayload = stringField(canonicalJson, "goalPayload") ?: return incompatible("missing goal payload")
        val goal = when (stringField(canonicalJson, "targetType")) {
            "UNTIL_MINUTE" -> TimeAdvanceGoal.UntilMinute(GameMinute.of(longField(goalPayload, "targetMinute") ?: return incompatible("missing target minute")).valueOrReject())
            "UNTIL_FIRST_ACTION_COMPLETED" -> TimeAdvanceGoal.UntilFirstActionCompleted(actionSelector(goalPayload) ?: return incompatible("invalid action selector"))
            "UNTIL_ALL_ACTIONS_COMPLETED" -> TimeAdvanceGoal.UntilAllActionsCompleted(actionSelector(goalPayload) ?: return incompatible("invalid action selector"))
            "UNTIL_CONDITION" -> TimeAdvanceGoal.UntilCondition(conditionRef(goalPayload) ?: return incompatible("invalid condition"))
            "UNTIL_EVENT" -> TimeAdvanceGoal.UntilEvent(EventSelector(stringField(goalPayload, "eventType") ?: return incompatible("missing event type"), EventImportance.valueOf(stringField(goalPayload, "minImportance") ?: return incompatible("missing importance")), nullableEntityField(goalPayload, "subjectId")))
            else -> return incompatible("unsupported goal kind")
        }
        if (encode(goal) != canonicalJson) incompatible("non-canonical goal payload") else Checked.Value(goal)
    } catch (_: IllegalArgumentException) {
        incompatible("invalid goal payload")
    }

    private fun selectorGoal(targetType: String, selector: ActionSelector): String = envelope(targetType, "{\"actionSelector\":${CanonicalJson.string(selector.canonicalJson())}}")

    private fun envelope(targetType: String, goalPayload: String): String =
        "{\"codec\":${CanonicalJson.string(CODEC_ID)},\"goalPayload\":${CanonicalJson.string(goalPayload)},\"targetType\":${CanonicalJson.string(targetType)}}"

    private fun stringField(json: String, name: String): String? = Regex("\\\"$name\\\":(\\\"(?:\\\\.|[^\\\"])*\\\")")
        .find(json)?.groupValues?.get(1)?.let(CanonicalJson::parseString)

    private fun longField(json: String, name: String): Long? = Regex("\\\"$name\\\":([0-9]+)").find(json)?.groupValues?.get(1)?.toLongOrNull()

    private fun actionSelector(json: String): ActionSelector? = stringField(json, "actionSelector")?.let { raw ->
        val actionId = stringField(raw, "actionId")
        if (actionId != null) ActionSelector.ActionId(EntityId.of(actionId).valueOrReject())
        else ActionSelector.Canonical(stringField(raw, "codec") ?: return null, stringField(raw, "payload") ?: return null)
    }

    private fun conditionRef(json: String): ConditionRef? = stringField(json, "condition")?.let { raw ->
        ConditionRef(stringField(raw, "codec") ?: return null, stringField(raw, "payload") ?: return null)
    }

    private fun nullableEntityField(json: String, name: String): EntityId? {
        if (Regex("\\\"$name\\\":null").containsMatchIn(json)) return null
        return stringField(json, name)?.let { EntityId.of(it).valueOrReject() }
    }

    private fun incompatible(reason: String): Checked<Nothing> = Checked.Rejected(DomainError.ContentCompatibilityError(reason))

    private fun <T> Checked<T>.valueOrReject(): T = when (this) {
        is Checked.Value -> value
        is Checked.Rejected -> throw IllegalArgumentException(error.toString())
    }
}

data class TimeTraversalLimits(val maxAdvanceMinute: GameMinute, val maxBoundaryCount: Int, val maxCandidatesPerBatch: Int = Int.MAX_VALUE) {
    init {
        require(maxBoundaryCount > 0) { "max boundary count must be positive" }
        require(maxCandidatesPerBatch > 0) { "max candidates per batch must be positive" }
    }
}

data class TimeAdvanceInterruptPolicy(
    val importanceThreshold: EventImportance? = null,
    val favoriteSubjectIds: Set<EntityId> = emptySet(),
    val favoriteImportanceBoost: Int = 0,
    val forcedStopEventTypes: Set<String> = emptySet(),
    val explicitStopEventTypes: Set<String> = emptySet(),
    val explicitIgnoreEventTypes: Set<String> = emptySet(),
    val summaryOnly: Boolean = false
) {
    init {
        require(favoriteImportanceBoost in 0..3) { "favorite importance boost must be within 0..3" }
        require(forcedStopEventTypes.all(String::isNotBlank) && explicitStopEventTypes.all(String::isNotBlank) && explicitIgnoreEventTypes.all(String::isNotBlank)) {
            "interrupt policy event types must not be blank"
        }
    }

    fun canonicalJson(): String = "{\"codec\":${CanonicalJson.string(CODEC_ID)},\"explicitIgnoreEventTypes\":${canonicalStrings(explicitIgnoreEventTypes)},\"explicitStopEventTypes\":${canonicalStrings(explicitStopEventTypes)},\"favoriteImportanceBoost\":$favoriteImportanceBoost,\"favoriteSubjectIds\":${canonicalStrings(favoriteSubjectIds.map(EntityId::value))},\"forcedStopEventTypes\":${canonicalStrings(forcedStopEventTypes)},\"importanceThreshold\":${importanceThreshold?.let { CanonicalJson.string(it.name) } ?: "null"},\"summaryOnly\":$summaryOnly}"

    val hash: PayloadHash get() = canonicalPayloadHash(canonicalJson())

    companion object {
        const val CODEC_ID = "TimeAdvanceInterruptPolicy.v1"
    }
}

enum class TimeAdvanceResult { COMPLETED, INTERRUPTED, DECISION_REQUIRED, CANCELLED, UNREACHABLE, LIMIT_REACHED, FAILED }

data class TimeAdvanceState(
    val goal: TimeAdvanceGoal,
    val cursor: BoundaryCursor?,
    val processedBoundaryCount: Int,
    val status: TimeAdvanceResult?,
    val commandEpoch: SessionEpoch? = null,
    val commandId: CommandId? = null,
    val continuationOfCommandId: CommandId? = null,
    val pendingDecisionGateId: String? = null,
    val pendingDecisionChoiceIds: List<String> = emptyList(),
    val selectedDecisionChoiceId: String? = null,
    val pendingSuffix: PendingBoundarySuffix? = null,
    val sealedElapsedOutcome: SealedElapsedOutcome? = null,
    val segmentNo: Int = 0,
    val nextEventSequence: Long = 0,
    val progressionMode: ProgressionMode = ProgressionMode.FAST_FORWARD,
    val limits: TimeTraversalLimits? = null,
    val interruptPolicy: TimeAdvanceInterruptPolicy? = null
) {
    init {
        require(processedBoundaryCount >= 0) { "processed boundary count must be non-negative" }
        require((commandEpoch == null) == (commandId == null)) { "time advance command identity must be complete" }
        require(status != TimeAdvanceResult.DECISION_REQUIRED || pendingSuffix != null && pendingDecisionGateId != null) {
            "decision-required state must retain its gate and pending suffix"
        }
        require(status != TimeAdvanceResult.DECISION_REQUIRED || pendingDecisionChoiceIds.size in 1..8 && pendingDecisionChoiceIds == pendingDecisionChoiceIds.sorted() && pendingDecisionChoiceIds.distinct().size == pendingDecisionChoiceIds.size) {
            "decision-required state must retain one to eight canonical choices"
        }
        require(segmentNo >= 0) { "segment number must be non-negative" }
        require(nextEventSequence >= 0) { "next event sequence must be non-negative" }
    }
}

data class PendingBoundarySuffix(val candidates: List<BoundaryCandidate>) {
    val codecId: String = CODEC_ID
    val canonicalPayload: String = candidates.joinToString(prefix = "{\"candidates\":[", postfix = "]}", separator = ",") { it.pendingSuffixJson() }
    val hash: PayloadHash = canonicalCodecPayloadHash(codecId, canonicalPayload)

    init {
        require(canonicalPayload.toByteArray(StandardCharsets.UTF_8).size <= WorldTimeTraversal.MAX_PENDING_BATCH_BYTES) {
            "pending suffix exceeds byte cap"
        }
        require(candidates.map(BoundaryCandidate::key).distinct().size == candidates.size) { "pending suffix candidate keys must be unique" }
    }

    companion object {
        const val CODEC_ID = "BoundaryBatch.v1"

        fun decode(canonicalPayload: String, expectedHash: PayloadHash): Checked<PendingBoundarySuffix> = try {
            val values = objectArrayField(canonicalPayload, "candidates") ?: return incompatible()
            val suffix = PendingBoundarySuffix(values.map(::decodePendingCandidate))
            if (suffix.canonicalPayload == canonicalPayload && suffix.hash == expectedHash) Checked.Value(suffix) else incompatible()
        } catch (_: IllegalArgumentException) {
            incompatible()
        }

        private fun incompatible(): Checked<Nothing> = Checked.Rejected(DomainError.ContentCompatibilityError("invalid BoundaryBatch.v1 payload"))
    }
}

private fun BoundaryCandidate.pendingSuffixJson(): String =
    "{\"candidateKind\":${CanonicalJson.string(candidateKind)},\"canonicalPayload\":${CanonicalJson.string(canonicalPayload)},\"category\":${CanonicalJson.string(key.category.name)},\"decisionChoiceIds\":${canonicalStrings(decisionChoiceIds)},\"disposition\":${CanonicalJson.string(disposition.name)},\"domainSequence\":${key.domainSequence},\"eventType\":${eventType?.let(CanonicalJson::string) ?: "null"},\"importance\":${importance?.let { CanonicalJson.string(it.name) } ?: "null"},\"payloadCodec\":${CanonicalJson.string(payloadCodec)},\"payloadHash\":${CanonicalJson.string(payloadHash.value)},\"priority\":${key.priority},\"publicSafetyStop\":$publicSafetyStop,\"sourceId\":${CanonicalJson.string(key.sourceId)},\"stableEntityId\":${CanonicalJson.string(key.stableEntityId)},\"stableSubKey\":${CanonicalJson.string(key.stableSubKey)},\"subjectId\":${subjectId?.let { CanonicalJson.string(it.value) } ?: "null"},\"time\":${key.boundaryTime.value}}"

data class SealedElapsedOutcome(
    val codec: String,
    val canonicalPayload: String,
    val effectiveMinute: GameMinute,
    val domainResultId: EntityId,
    val hash: PayloadHash = canonicalCodecPayloadHash(CODEC_ID, envelopePayload(codec, canonicalPayload, effectiveMinute, domainResultId))
) {
    init {
        require(codec.isNotBlank()) { "sealed outcome codec must not be blank" }
        require(canonicalPayload.isNotBlank()) { "sealed outcome payload must not be blank" }
        require(hash == canonicalCodecPayloadHash(CODEC_ID, encode())) { "sealed outcome hash must match codec, payload, minute, and result id" }
    }

    fun encode(): String = envelopePayload(codec, canonicalPayload, effectiveMinute, domainResultId)

    companion object {
        const val CODEC_ID = "SealedElapsedOutcome.v1"

        fun decode(encoded: String, expectedHash: PayloadHash): Checked<SealedElapsedOutcome> = try {
            val outcome = SealedElapsedOutcome(
                requiredStringField(encoded, "outcomeCodec"),
                requiredStringField(encoded, "outcomePayload"),
                requiredMinuteField(encoded, "effectiveMinute"),
                requiredEntityField(encoded, "domainResultId")
            )
            if (outcome.encode() == encoded && outcome.hash == expectedHash) Checked.Value(outcome) else incompatible()
        } catch (_: IllegalArgumentException) {
            incompatible()
        }

        private fun envelopePayload(codec: String, payload: String, minute: GameMinute, id: EntityId): String =
            "{\"domainResultId\":${CanonicalJson.string(id.value)},\"effectiveMinute\":${minute.value},\"outcomeCodec\":${CanonicalJson.string(codec)},\"outcomePayload\":${CanonicalJson.string(payload)}}"

        private fun incompatible(): Checked<Nothing> = Checked.Rejected(DomainError.ContentCompatibilityError("invalid SealedElapsedOutcome.v1 payload"))
    }
}

private fun decodePendingCandidate(json: String): BoundaryCandidate {
    val time = requiredMinuteField(json, "time")
    val subject = nullableStringField(json, "subjectId")?.let(::requiredEntity)
    val candidate = BoundaryCandidate(
        key = BoundaryKey(
            time,
            BoundaryCategory.valueOf(requiredStringField(json, "category")),
            requiredLongField(json, "priority").toIntExact(),
            requiredLongField(json, "domainSequence"),
            requiredStringField(json, "stableEntityId"),
            requiredStringField(json, "stableSubKey"),
            requiredStringField(json, "sourceId")
        ),
        candidateKind = requiredStringField(json, "candidateKind"),
        payloadCodec = requiredStringField(json, "payloadCodec"),
        canonicalPayload = requiredStringField(json, "canonicalPayload"),
        payloadHash = PayloadHash(requiredStringField(json, "payloadHash")),
        disposition = BoundaryDisposition.valueOf(requiredStringField(json, "disposition")),
        eventType = nullableStringField(json, "eventType"),
        importance = nullableStringField(json, "importance")?.let(EventImportance::valueOf),
        subjectId = subject,
        publicSafetyStop = requiredBooleanField(json, "publicSafetyStop"),
        decisionChoiceIds = requiredStringArrayField(json, "decisionChoiceIds")
    )
    require(candidate.pendingSuffixJson() == json) { "non-canonical boundary candidate" }
    return candidate
}

private fun objectArrayField(json: String, name: String): List<String>? {
    val marker = "\"$name\":["
    val markerIndex = json.indexOf(marker)
    if (markerIndex < 0) return null
    val start = markerIndex + marker.length
    val values = mutableListOf<String>()
    var index = start
    while (index < json.length) {
        while (index < json.length && json[index].isWhitespace()) index++
        if (index >= json.length) return null
        if (json[index] == ']') return values
        if (json[index] != '{') return null
        val objectStart = index
        var depth = 0
        var quoted = false
        var escaped = false
        while (index < json.length) {
            val char = json[index++]
            if (quoted) {
                if (escaped) escaped = false else if (char == '\\') escaped = true else if (char == '"') quoted = false
            } else when (char) {
                '"' -> quoted = true
                '{' -> depth++
                '}' -> if (--depth == 0) break
            }
        }
        if (depth != 0 || quoted) return null
        values += json.substring(objectStart, index)
        if (index >= json.length) return null
        when (json[index]) {
            ',' -> index++
            ']' -> return values
            else -> return null
        }
    }
    return null
}

private fun requiredStringField(json: String, name: String): String = Regex("\\\"$name\\\":(\\\"(?:\\\\.|[^\\\"])*\\\")")
    .find(json)?.groupValues?.get(1)?.let(CanonicalJson::parseString)
    ?: throw IllegalArgumentException("missing $name")

private fun nullableStringField(json: String, name: String): String? {
    if (Regex("\\\"$name\\\":null(?=,|\\})").containsMatchIn(json)) return null
    return requiredStringField(json, name)
}

private fun requiredLongField(json: String, name: String): Long = Regex("\\\"$name\\\":(-?\\d+)(?=,|\\})")
    .find(json)?.groupValues?.get(1)?.toLongOrNull() ?: throw IllegalArgumentException("missing $name")

private fun requiredBooleanField(json: String, name: String): Boolean = Regex("\\\"$name\\\":(true|false)(?=,|\\})")
    .find(json)?.groupValues?.get(1)?.toBooleanStrictOrNull() ?: throw IllegalArgumentException("missing $name")

private fun requiredStringArrayField(json: String, name: String): List<String> {
    val raw = Regex("\\\"$name\\\":\\[(.*?)](?=,|\\})").find(json)?.groupValues?.get(1)
        ?: throw IllegalArgumentException("missing $name")
    if (raw.isEmpty()) return emptyList()
    return Regex("\\\"(?:\\\\.|[^\\\"])*\\\"").findAll(raw).map {
        CanonicalJson.parseString(it.value) ?: throw IllegalArgumentException("invalid $name")
    }.toList().also { require(it.joinToString(",") { value -> CanonicalJson.string(value) } == raw) { "non-canonical $name" } }
}

private fun requiredMinuteField(json: String, name: String): GameMinute = when (val result = GameMinute.of(requiredLongField(json, name))) {
    is Checked.Value -> result.value
    is Checked.Rejected -> throw IllegalArgumentException(result.error.toString())
}

private fun requiredEntityField(json: String, name: String): EntityId = requiredEntity(requiredStringField(json, name))

private fun requiredEntity(value: String): EntityId = when (val result = EntityId.of(value)) {
    is Checked.Value -> result.value
    is Checked.Rejected -> throw IllegalArgumentException(result.error.toString())
}

private fun Long.toIntExact(): Int = Math.toIntExact(this)

data class TimeAdvanceContinuation(
    val predecessorEpoch: SessionEpoch,
    val predecessorCommandId: CommandId,
    val childCommandId: CommandId,
    val pendingSuffixHash: PayloadHash,
    val sealedOutcomeHash: PayloadHash?,
    val selection: DecisionSelection
)

data class DecisionSelection(
    val gateId: String,
    val choiceId: String,
    val codec: String,
    val canonicalPayload: String,
    val payloadHash: PayloadHash = canonicalPayloadHash(canonicalPayload)
) {
    init {
        require(gateId.isNotBlank() && choiceId.isNotBlank() && codec.isNotBlank() && canonicalPayload.isNotBlank()) {
            "decision selection fields must not be blank"
        }
        require(payloadHash == canonicalPayloadHash(canonicalPayload)) { "decision selection hash must match payload" }
    }
}

data class SegmentCommitReceipt(val receipt: CommitReceipt, val segmentNo: Int, val timeAdvanceState: TimeAdvanceState) {
    init {
        require(segmentNo >= 0) { "segment number must be non-negative" }
    }
}

enum class BoundaryCategory(val rank: Int) {
    MANDATORY_DECISION_PREREQUISITE(10),
    LIFECYCLE(20),
    COMBAT_CRISIS(30),
    ORGANIZATION_DECISION(40),
    SCHEDULED_ACTION_START(50),
    SCHEDULED_ACTION_COMPLETE(55),
    RELATIONSHIP(60),
    ECONOMY_SETTLEMENT(70),
    RANKING(80),
    WORLD_EVENT(90),
    INFORMATION(100)
}

enum class BoundaryDisposition { CONTINUE, DECISION_GATE, TIME_ADVANCE_STOP }

object TimeAdvanceInterruptPolicyEvaluator {
    fun disposition(candidate: BoundaryCandidate, policy: TimeAdvanceInterruptPolicy): BoundaryDisposition {
        if (candidate.disposition == BoundaryDisposition.DECISION_GATE) return BoundaryDisposition.DECISION_GATE
        if (candidate.disposition == BoundaryDisposition.TIME_ADVANCE_STOP) return BoundaryDisposition.TIME_ADVANCE_STOP
        val eventType = candidate.eventType ?: candidate.candidateKind
        if (eventType in policy.forcedStopEventTypes) return BoundaryDisposition.TIME_ADVANCE_STOP
        val importance = candidate.importance
        val threshold = policy.importanceThreshold
        if (importance != null && threshold != null) {
            val boosted = (importance.ordinal + if (candidate.subjectId in policy.favoriteSubjectIds) policy.favoriteImportanceBoost else 0)
                .coerceAtMost(EventImportance.entries.lastIndex)
            if (boosted >= threshold.ordinal) return BoundaryDisposition.TIME_ADVANCE_STOP
        }
        if (eventType in policy.explicitStopEventTypes) return BoundaryDisposition.TIME_ADVANCE_STOP
        if (eventType in policy.explicitIgnoreEventTypes) return BoundaryDisposition.CONTINUE
        return if (candidate.publicSafetyStop) BoundaryDisposition.TIME_ADVANCE_STOP else BoundaryDisposition.CONTINUE
    }

    fun terminal(
        candidate: BoundaryCandidate,
        policy: TimeAdvanceInterruptPolicy,
        explicitControl: AdvanceControl?
    ): TimeAdvanceResult? = when (disposition(candidate, policy)) {
        BoundaryDisposition.DECISION_GATE -> TimeAdvanceResult.DECISION_REQUIRED
        BoundaryDisposition.TIME_ADVANCE_STOP -> if (explicitControl == AdvanceControl.CANCEL_ADVANCE) TimeAdvanceResult.CANCELLED else TimeAdvanceResult.INTERRUPTED
        BoundaryDisposition.CONTINUE -> when (explicitControl) {
            AdvanceControl.CANCEL_ADVANCE -> TimeAdvanceResult.CANCELLED
            AdvanceControl.PAUSE, AdvanceControl.APP_BACKGROUND, AdvanceControl.CLOSE -> TimeAdvanceResult.INTERRUPTED
            null -> null
        }
    }
}

data class BoundaryKey(
    val boundaryTime: GameMinute,
    val category: BoundaryCategory,
    val priority: Int,
    val domainSequence: Long,
    val stableEntityId: String,
    val stableSubKey: String,
    val sourceId: String
)

data class BoundaryCandidate(
    val key: BoundaryKey,
    val candidateKind: String,
    val payloadCodec: String,
    val canonicalPayload: String,
    val payloadHash: PayloadHash = canonicalPayloadHash(canonicalPayload),
    val disposition: BoundaryDisposition = BoundaryDisposition.CONTINUE,
    val eventType: String? = null,
    val importance: EventImportance? = null,
    val subjectId: EntityId? = null,
    val publicSafetyStop: Boolean = false,
    val decisionChoiceIds: List<String> = listOf("continue")
) {
    init {
        require(candidateKind.isNotBlank()) { "candidate kind must not be blank" }
        require(payloadCodec.isNotBlank()) { "payload codec must not be blank" }
        require(canonicalPayload.isNotBlank()) { "candidate payload must not be blank" }
        require(canonicalPayload == Normalizer.normalize(canonicalPayload, Normalizer.Form.NFC)) { "candidate payload must be NFC" }
        require(payloadHash == canonicalPayloadHash(canonicalPayload)) { "candidate payload hash must match canonical payload" }
        require(key.sourceId.matches(Regex("[\\x21-\\x7e]+"))) { "source id must be printable ASCII" }
        require(decisionChoiceIds.all(String::isNotBlank) && decisionChoiceIds == decisionChoiceIds.sorted() && decisionChoiceIds.distinct().size == decisionChoiceIds.size) {
            "decision choices must be canonical sorted and unique"
        }
        require(disposition != BoundaryDisposition.DECISION_GATE || decisionChoiceIds.size in 1..8) {
            "decision gate must expose one to eight choices"
        }
    }
}

internal fun canonicalPayloadHash(payload: String): PayloadHash = PayloadHash(
    MessageDigest.getInstance("SHA-256").digest(payload.toByteArray(StandardCharsets.UTF_8))
        .joinToString("") { "%02x".format(it.toInt() and 0xff) }
)

internal fun canonicalCodecPayloadHash(codecId: String, payload: String): PayloadHash {
    require(codecId.isNotBlank()) { "codec id must not be blank" }
    val material = ("PERSISTEDPAYLOAD\u0000$codecId\u0000").toByteArray(StandardCharsets.UTF_8) +
        payload.toByteArray(StandardCharsets.UTF_8)
    return PayloadHash(MessageDigest.getInstance("SHA-256").digest(material).joinToString("") { "%02x".format(it.toInt() and 0xff) })
}

private fun canonicalStrings(values: Collection<String>): String = values.sorted().joinToString(prefix = "[", postfix = "]", separator = ",") {
    CanonicalJson.string(it)
}

data class BoundaryCursor(val boundaryTime: GameMinute, val lastCompletedKey: BoundaryKey)

data class WorldTraversalSnapshot(
    val clock: WorldClock,
    val calendar: ScheduleCalendar = ScheduleCalendar(emptyList(), emptyMap()),
    val completedActionIds: Set<EntityId> = emptySet(),
    val selectedActionIdsBySelector: Map<PayloadHash, Set<EntityId>> = emptyMap(),
    val satisfiedConditionHashes: Set<PayloadHash> = emptySet(),
    val observedEvents: List<ObservedBoundaryEvent> = emptyList(),
    val aggregates: Map<EntityId, AggregateState> = emptyMap()
)

interface BoundarySource {
    val sourceId: String
    fun nextTimeAfter(snapshot: WorldTraversalSnapshot, cursor: BoundaryCursor?): GameMinute?
    fun candidatesAt(snapshot: WorldTraversalSnapshot, time: GameMinute): List<BoundaryCandidate>
}

/** Stateless calendar boundary provider; it reads only the working snapshot clock. */
class CalendarBoundarySource : BoundarySource {
    override val sourceId: String = "calendar"

    override fun nextTimeAfter(snapshot: WorldTraversalSnapshot, cursor: BoundaryCursor?): GameMinute? = try {
        val nextDay = Math.multiplyExact(Math.addExact(snapshot.clock.minute.value / MINUTES_PER_DAY, 1), MINUTES_PER_DAY)
        GameMinute.of(nextDay).valueOrNull()
    } catch (_: ArithmeticException) {
        null
    }

    override fun candidatesAt(snapshot: WorldTraversalSnapshot, time: GameMinute): List<BoundaryCandidate> {
        if (time.value == 0L || time.value % MINUTES_PER_DAY != 0L) return emptyList()
        val dayIndex = time.value / MINUTES_PER_DAY
        val kinds = buildList {
            add("calendar.day.start.v1" to 0L)
            if (dayIndex % DAYS_PER_MONTH == 0L) add("calendar.month.start.v1" to 1L)
            if (dayIndex % DAYS_PER_YEAR == 0L) add("calendar.year.start.v1" to 2L)
        }
        return kinds.map { (kind, sequence) ->
            BoundaryCandidate(
                key = BoundaryKey(time, BoundaryCategory.LIFECYCLE, 0, sequence, "%019d".format(dayIndex), kind.substringAfter("calendar.").substringBefore(".start"), sourceId),
                candidateKind = kind,
                payloadCodec = "CalendarBoundaryPayload.v1",
                canonicalPayload = "{\"dayIndex\":$dayIndex,\"gameMinute\":${time.value}}"
            )
        }
    }

    private fun <T> Checked<T>.valueOrNull(): T? = (this as? Checked.Value<T>)?.value

    private companion object {
        const val MINUTES_PER_DAY = 1_440L
        const val DAYS_PER_MONTH = 30L
        const val DAYS_PER_YEAR = 360L
    }
}

fun interface BoundaryEvaluator {
    fun evaluate(snapshot: WorldTraversalSnapshot, candidate: BoundaryCandidate): BoundaryEvaluation
}

internal fun boundaryEvaluator(
    apply: (WorldTraversalSnapshot, BoundaryCandidate) -> WorldTraversalSnapshot
) = BoundaryEvaluator { snapshot, candidate ->
    BoundaryEvaluation(apply(snapshot, candidate), listOf(candidate.eventDraft()))
}

data class DomainEventDraft(
    val stableEventKey: String,
    val sourceId: EntityId?,
    val gameMinute: GameMinute,
    val visibility: EventVisibility,
    val importance: EventImportance,
    val payload: DomainEventPayload
)

data class BoundaryEvaluation(
    val snapshot: WorldTraversalSnapshot,
    val events: List<DomainEventDraft>
)

internal fun BoundaryCandidate.eventDraft(): DomainEventDraft {
    val payload = if (candidateKind == "elapsed.action.apply.v1") {
        val outcome = when (val decoded = SealedElapsedOutcome.decode(
            canonicalPayload,
            canonicalCodecPayloadHash(SealedElapsedOutcome.CODEC_ID, canonicalPayload)
        )) {
            is Checked.Value -> decoded.value
            is Checked.Rejected -> throw IllegalArgumentException(decoded.error.toString())
        }
        ElapsedActionAppliedEventPayload(outcome.domainResultId, outcome.hash)
    } else BoundaryDomainEventType.forCandidate(candidateKind)?.let { type ->
        BoundaryDomainEventPayload(type, payloadHash, key.stableEntityId)
    } ?: throw IllegalArgumentException("unregistered boundary event kind: $candidateKind")
    val source = subjectId ?: (EntityId.of(key.stableEntityId) as? Checked.Value<EntityId>)?.value
    return DomainEventDraft(
        stableEventKey = "${key.sourceId}/${key.stableEntityId}/${key.stableSubKey}/${candidateKind}",
        sourceId = source,
        gameMinute = key.boundaryTime,
        visibility = EventVisibility.SYSTEM_HIDDEN,
        importance = importance ?: EventImportance.NORMAL,
        payload = payload
    )
}

data class TimeTraversalResult(
    val result: TimeAdvanceResult,
    val snapshot: WorldTraversalSnapshot,
    val cursor: BoundaryCursor?,
    val processedBoundaryCount: Int,
    val remainingGoal: TimeAdvanceGoal?,
    val stopCandidate: BoundaryCandidate? = null,
    val pendingSuffix: PendingBoundarySuffix? = null,
    val error: DomainError? = null,
    val failure: TraversalFailure? = null,
    val eventDrafts: List<DomainEventDraft> = emptyList()
)

/** A corrupt traversal must not be turned into a durable FAILED time-advance segment. */
enum class TraversalFailure { SYSTEM_HALT, DOMAIN_FAILED }

class WorldTimeTraversal(
    private val evaluator: BoundaryEvaluator,
    private val dispositionFor: (BoundaryCandidate) -> BoundaryDisposition = BoundaryCandidate::disposition,
    private val approvedCodecs: Set<String> = emptySet()
) {
    fun traverse(
        initial: WorldTraversalSnapshot,
        target: GameMinute,
        goal: TimeAdvanceGoal,
        limits: TimeTraversalLimits,
        sources: List<BoundarySource>,
        initialCursor: BoundaryCursor? = null
    ): TimeTraversalResult {
        if (target.value < initial.clock.minute.value) return halted(initial, null, 0, "targetMinute must not move backward")
        if (goal.isSatisfied(initial)) return completed(initial, null, 0, goal)
        val terminalTarget = if (target.value > limits.maxAdvanceMinute.value) limits.maxAdvanceMinute else target
        val cappedByAdvanceLimit = terminalTarget != target

        var snapshot = initial
        var cursor = initialCursor
        var processed = 0
        val drafts = mutableListOf<DomainEventDraft>()
        while (snapshot.clock.minute.value < terminalTarget.value || cursor?.boundaryTime == snapshot.clock.minute) {
            if (processed == limits.maxBoundaryCount) {
                return limited(snapshot, cursor, processed, goal).copy(eventDrafts = drafts)
            }
            val nextSourceTime = sources.mapNotNull { source -> source.nextTimeAfter(snapshot, cursor) }.minByOrNull(GameMinute::value)
            val next = listOfNotNull(terminalTarget, nextSourceTime).minBy(GameMinute::value)
            if (next.value < snapshot.clock.minute.value || (next.value == snapshot.clock.minute.value && cursor == null)) {
                    return halted(snapshot, cursor, processed, "boundary did not advance world time").copy(eventDrafts = drafts)
            }
            val nextSnapshot = if (next.value > snapshot.clock.minute.value) snapshot.copy(clock = WorldClock(next)) else snapshot
            if (nextSourceTime == null || nextSourceTime.value > terminalTarget.value) {
                return (if (goal.isSatisfied(nextSnapshot)) completed(nextSnapshot, cursor, processed, goal)
                else if (cappedByAdvanceLimit) limited(nextSnapshot, cursor, processed, goal)
                else unreachable(nextSnapshot, cursor, processed, goal)).copy(eventDrafts = drafts)
            }
            val candidates = mutableListOf<BoundaryCandidate>()
            for (source in sources) {
                val provided = source.candidatesAt(nextSnapshot, next)
                if (provided.any { it.key.sourceId != source.sourceId }) {
                    return halted(snapshot, cursor, processed, "boundary source id mismatch").copy(eventDrafts = drafts)
                }
                candidates += provided
            }
            if (candidates.isEmpty() && next == snapshot.clock.minute) {
                return halted(snapshot, cursor, processed, "same-time boundary has no remaining candidate").copy(eventDrafts = drafts)
            }
            if (candidates.size > limits.maxCandidatesPerBatch) {
                return limitedByBoundary(snapshot, cursor, processed, "boundary candidate count exceeds cap").copy(eventDrafts = drafts)
            }
            validateBatch(candidates, next, cursor)?.let { error ->
                return when (error) {
                    is DomainError.BoundaryLimitReached -> limitedByBoundary(snapshot, cursor, processed, error.reason)
                    else -> halted(snapshot, cursor, processed, error)
                }.copy(eventDrafts = drafts)
            }
            val pendingBytes = try {
                PendingBoundarySuffix(candidates).canonicalPayload.toByteArray(StandardCharsets.UTF_8).size
            } catch (_: IllegalArgumentException) {
                MAX_PENDING_BATCH_BYTES + 1
            }
            if (pendingBytes > MAX_PENDING_BATCH_BYTES) {
                return limitedByBoundary(snapshot, cursor, processed, "pending boundary batch exceeds byte cap").copy(eventDrafts = drafts)
            }
            snapshot = nextSnapshot
            val ordered = candidates.sortedWith(BOUNDARY_ORDER)
            var stop: BoundaryCandidate? = null
            var lastApplied: BoundaryCandidate? = null
            for ((index, candidate) in ordered.withIndex()) {
                val beforeCandidate = snapshot
                val evaluation = try {
                    evaluator.evaluate(snapshot, candidate)
                } catch (error: Exception) {
                    return halted(beforeCandidate, cursor, processed, "boundary evaluator failed: ${error.message ?: error::class.simpleName}").copy(eventDrafts = drafts)
                }
                snapshot = evaluation.snapshot.recordObserved(evaluation.events)
                drafts += evaluation.events
                if (introducesPastOrCurrentAction(beforeCandidate, snapshot, next)) {
                    return halted(beforeCandidate, cursor, processed, "boundary evaluator introduced a past or current scheduled action")
                }
                lastApplied = candidate
                when (dispositionFor(candidate)) {
                    BoundaryDisposition.DECISION_GATE -> {
                    stop = candidate
                    val pendingSuffix = PendingBoundarySuffix(ordered.drop(index + 1))
                    cursor = lastApplied?.let { BoundaryCursor(next, it.key) } ?: cursor
                    return TimeTraversalResult(TimeAdvanceResult.DECISION_REQUIRED, snapshot, cursor, processed, goal, stop, pendingSuffix, eventDrafts = drafts)
                    }
                    BoundaryDisposition.TIME_ADVANCE_STOP -> stop = candidate
                    BoundaryDisposition.CONTINUE -> Unit
                }
            }
            cursor = lastApplied?.let { BoundaryCursor(next, it.key) } ?: cursor
            processed++
            if (stop != null) return TimeTraversalResult(TimeAdvanceResult.INTERRUPTED, snapshot, cursor, processed, goal, stop, eventDrafts = drafts)
            if (goal.isSatisfied(snapshot)) return completed(snapshot, cursor, processed, goal).copy(eventDrafts = drafts)
        }
        return (if (goal.isSatisfied(snapshot)) completed(snapshot, cursor, processed, goal)
        else if (cappedByAdvanceLimit) limited(snapshot, cursor, processed, goal)
        else unreachable(snapshot, cursor, processed, goal)).copy(eventDrafts = drafts)
    }

    /** Applies only the durable suffix frozen by a prior decision gate; sources are not queried again. */
    fun resumeFrozenBatch(
        initial: WorldTraversalSnapshot,
        goal: TimeAdvanceGoal,
        cursor: BoundaryCursor,
        suffix: PendingBoundarySuffix
    ): TimeTraversalResult {
        val candidates = suffix.candidates
        if (candidates.isEmpty()) return TimeTraversalResult(TimeAdvanceResult.LIMIT_REACHED, initial, cursor, 1, goal)
        val time = candidates.first().key.boundaryTime
        if (initial.clock.minute != time) return halted(initial, cursor, 0, "persisted boundary suffix clock mismatch")
        // A persisted suffix is already accepted state; any malformed cap/order/codec is corruption, not a new limit.
        validateBatch(candidates, time, cursor)?.let { error -> return halted(initial, cursor, 0, error) }
        val ordered = candidates.sortedWith(BOUNDARY_ORDER)
        if (ordered != candidates) return halted(initial, cursor, 0, "persisted boundary suffix order mismatch")

        var snapshot = initial
        var completedCursor = cursor
        var stop: BoundaryCandidate? = null
        val drafts = mutableListOf<DomainEventDraft>()
        for ((index, candidate) in ordered.withIndex()) {
            val beforeCandidate = snapshot
            val evaluation = try {
                evaluator.evaluate(snapshot, candidate)
            } catch (error: Exception) {
                return halted(beforeCandidate, completedCursor, 0, "boundary evaluator failed: ${error.message ?: error::class.simpleName}").copy(eventDrafts = drafts)
            }
            snapshot = evaluation.snapshot.recordObserved(evaluation.events)
            drafts += evaluation.events
            if (introducesPastOrCurrentAction(beforeCandidate, snapshot, time)) {
                return halted(beforeCandidate, completedCursor, 0, "boundary evaluator introduced a past or current scheduled action")
            }
            completedCursor = BoundaryCursor(time, candidate.key)
            when (dispositionFor(candidate)) {
                BoundaryDisposition.DECISION_GATE -> {
                return TimeTraversalResult(
                    TimeAdvanceResult.DECISION_REQUIRED,
                    snapshot,
                    completedCursor,
                    0,
                    goal,
                    candidate,
                    PendingBoundarySuffix(ordered.drop(index + 1)),
                    eventDrafts = drafts
                )
                }
                BoundaryDisposition.TIME_ADVANCE_STOP -> {
                    stop = candidate
                }
                BoundaryDisposition.CONTINUE -> Unit
            }
        }
        return (if (stop != null) TimeTraversalResult(TimeAdvanceResult.INTERRUPTED, snapshot, completedCursor, 1, goal, stop)
        else if (goal.isSatisfied(snapshot)) completed(snapshot, completedCursor, 1, goal)
        else TimeTraversalResult(TimeAdvanceResult.LIMIT_REACHED, snapshot, completedCursor, 1, goal)).copy(eventDrafts = drafts)
    }

    private fun validateBatch(candidates: List<BoundaryCandidate>, time: GameMinute, cursor: BoundaryCursor?): DomainError? {
        val duplicate = candidates.groupBy(BoundaryCandidate::key).entries.firstOrNull { it.value.size > 1 }
        if (duplicate != null) return DomainError.BoundaryLimitReached("duplicate boundary key")
        for (candidate in candidates) {
            if (candidate.key.boundaryTime != time || !isAfterCursor(candidate.key, cursor)) {
                return DomainError.BoundaryLimitReached("boundary candidate is not strictly after the cursor")
            }
            if (candidate.canonicalPayload.toByteArray(StandardCharsets.UTF_8).size > MAX_CANDIDATE_PAYLOAD_BYTES) {
                return DomainError.BoundaryLimitReached("boundary candidate exceeds payload byte cap")
            }
            if (approvedCodecs.isNotEmpty() && candidate.payloadCodec !in approvedCodecs) {
                return DomainError.ContentCompatibilityError("boundary candidate codec is not registered")
            }
        }
        return null
    }

    private fun completed(snapshot: WorldTraversalSnapshot, cursor: BoundaryCursor?, processed: Int, goal: TimeAdvanceGoal) =
        TimeTraversalResult(TimeAdvanceResult.COMPLETED, snapshot, cursor, processed, null)

    private fun unreachable(snapshot: WorldTraversalSnapshot, cursor: BoundaryCursor?, processed: Int, goal: TimeAdvanceGoal) =
        TimeTraversalResult(TimeAdvanceResult.UNREACHABLE, snapshot, cursor, processed, goal)

    private fun limited(snapshot: WorldTraversalSnapshot, cursor: BoundaryCursor?, processed: Int, goal: TimeAdvanceGoal) =
        TimeTraversalResult(TimeAdvanceResult.LIMIT_REACHED, snapshot, cursor, processed, goal)

    private fun limitedByBoundary(snapshot: WorldTraversalSnapshot, cursor: BoundaryCursor?, processed: Int, reason: String) =
        TimeTraversalResult(
            TimeAdvanceResult.LIMIT_REACHED,
            snapshot,
            cursor,
            processed,
            null,
            error = DomainError.BoundaryLimitReached(reason),
            failure = TraversalFailure.DOMAIN_FAILED
        )

    private fun halted(snapshot: WorldTraversalSnapshot, cursor: BoundaryCursor?, processed: Int, reason: String) =
        TimeTraversalResult(
            TimeAdvanceResult.FAILED,
            snapshot,
            cursor,
            processed,
            null,
            error = DomainError.SystemHalted(reason),
            failure = TraversalFailure.SYSTEM_HALT
        )

    private fun halted(snapshot: WorldTraversalSnapshot, cursor: BoundaryCursor?, processed: Int, error: DomainError) =
        halted(snapshot, cursor, processed, when (error) {
            is DomainError.ContentCompatibilityError -> error.reason
            is DomainError.BoundaryLimitReached -> error.reason
            is DomainError.InvariantViolation -> error.reason
            is DomainError.SystemHalted -> error.reason
            else -> error.toString()
        })

    private fun WorldTraversalSnapshot.recordObserved(events: List<DomainEventDraft>): WorldTraversalSnapshot {
        if (events.isEmpty()) return this
        val additions = events.map { ObservedBoundaryEvent(it.payload.codecId, it.importance, it.sourceId) }
            .filterNot(observedEvents::contains)
        return if (additions.isEmpty()) this else copy(observedEvents = observedEvents + additions)
    }

    private fun introducesPastOrCurrentAction(
        before: WorldTraversalSnapshot,
        after: WorldTraversalSnapshot,
        boundaryTime: GameMinute
    ): Boolean {
        val existing = before.calendar.actions.associateBy(ScheduledAction::actionId)
        return after.calendar.actions.any { action ->
            val previous = existing[action.actionId]
            when {
                previous == null -> action.startMinute.value <= boundaryTime.value || action.dueMinute.value <= boundaryTime.value
                previous.startMinute != action.startMinute && action.startMinute.value <= boundaryTime.value -> true
                previous.dueMinute != action.dueMinute && action.dueMinute.value <= boundaryTime.value -> true
                else -> false
            }
        }
    }

    private fun isAfterCursor(key: BoundaryKey, cursor: BoundaryCursor?): Boolean = when {
        cursor == null -> true
        key.boundaryTime.value > cursor.boundaryTime.value -> true
        key.boundaryTime.value < cursor.boundaryTime.value -> false
        else -> BOUNDARY_KEY_ORDER.compare(key, cursor.lastCompletedKey) > 0
    }

    companion object {
        internal const val MAX_CANDIDATE_PAYLOAD_BYTES = 65_536
        internal const val MAX_PENDING_BATCH_BYTES = 1_048_576
        val BOUNDARY_ORDER = compareBy<BoundaryCandidate>(
            { it.key.boundaryTime.value },
            { it.key.category.rank },
            { it.key.priority },
            { it.key.domainSequence },
            { it.key.stableEntityId },
            { it.key.stableSubKey },
            { it.key.sourceId }
        )
        val BOUNDARY_KEY_ORDER = compareBy<BoundaryKey>(
            { it.boundaryTime.value },
            { it.category.rank },
            { it.priority },
            { it.domainSequence },
            { it.stableEntityId },
            { it.stableSubKey },
            { it.sourceId }
        )
    }
}

object BoundarySourceConformanceSuite {
    fun validate(
        source: BoundarySource,
        snapshot: WorldTraversalSnapshot,
        cursor: BoundaryCursor?,
        time: GameMinute,
        approvedCodecs: Set<String> = emptySet(),
        maxCandidatesPerBatch: Int = Int.MAX_VALUE,
        maxPendingBatchBytes: Int = WorldTimeTraversal.MAX_PENDING_BATCH_BYTES
    ): Checked<Unit> {
        if (maxCandidatesPerBatch <= 0 || maxPendingBatchBytes <= 0) return rejected("boundary caps must be positive")
        val firstNext = source.nextTimeAfter(snapshot, cursor)
        val secondNext = source.nextTimeAfter(snapshot, cursor)
        if (firstNext != secondNext) return rejected("next boundary time is unstable")
        if (firstNext != null && (firstNext.value < snapshot.clock.minute.value || (firstNext == snapshot.clock.minute && cursor == null))) {
            return rejected("next boundary time is not in the future")
        }
        val first = source.candidatesAt(snapshot, time)
        val second = source.candidatesAt(snapshot, time)
        if (first != second) return rejected("candidate order is unstable")
        if (first.size > maxCandidatesPerBatch) return rejected("candidate count exceeds cap")
        val pendingBytes = first.joinToString(prefix = "{\"candidates\":[", postfix = "]}", separator = ",") { it.pendingSuffixJson() }
            .toByteArray(StandardCharsets.UTF_8).size
        if (pendingBytes > maxPendingBatchBytes) {
            return rejected("candidate batch exceeds byte cap")
        }
        val duplicate = first.groupBy(BoundaryCandidate::key).any { it.value.size > 1 }
        if (duplicate) return rejected("duplicate boundary key")
        for (candidate in first) {
            if (candidate.key.sourceId != source.sourceId || candidate.key.boundaryTime != time || !isAfterCursor(candidate.key, cursor)) {
                return rejected("candidate is outside its requested future boundary")
            }
            if (candidate.canonicalPayload.toByteArray(StandardCharsets.UTF_8).size > WorldTimeTraversal.MAX_CANDIDATE_PAYLOAD_BYTES) {
                return rejected("candidate exceeds payload byte cap")
            }
            if (candidate.canonicalPayload != Normalizer.normalize(candidate.canonicalPayload, Normalizer.Form.NFC) ||
                candidate.payloadHash != canonicalPayloadHash(candidate.canonicalPayload) ||
                !candidate.payloadCodec.matches(Regex("[\\x21-\\x7e]+"))) {
                return rejected("candidate payload is not canonical")
            }
            if (approvedCodecs.isNotEmpty() && candidate.payloadCodec !in approvedCodecs) return rejected("candidate codec is not registered")
        }
        return Checked.Value(Unit)
    }

    private fun rejected(reason: String): Checked<Unit> = Checked.Rejected(DomainError.BoundaryLimitReached(reason))

    private fun isAfterCursor(key: BoundaryKey, cursor: BoundaryCursor?): Boolean = when {
        cursor == null -> true
        key.boundaryTime.value > cursor.boundaryTime.value -> true
        key.boundaryTime.value < cursor.boundaryTime.value -> false
        else -> BOUNDARY_KEY_ORDER.compare(key, cursor.lastCompletedKey) > 0
    }

    private val BOUNDARY_KEY_ORDER = compareBy<BoundaryKey>(
        { it.boundaryTime.value },
        { it.category.rank },
        { it.priority },
        { it.domainSequence },
        { it.stableEntityId },
        { it.stableSubKey },
        { it.sourceId }
    )
}
