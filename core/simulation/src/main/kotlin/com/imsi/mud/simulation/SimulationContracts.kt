package com.imsi.mud.simulation

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.text.Normalizer
import java.util.Base64

sealed interface Checked<out T> {
    data class Value<T>(val value: T) : Checked<T>
    data class Rejected(val error: DomainError) : Checked<Nothing>
}

@JvmInline
value class GameMinute private constructor(val value: Long) {
    companion object {
        fun of(value: Long): Checked<GameMinute> =
            if (value >= 0) Checked.Value(GameMinute(value))
            else Checked.Rejected(DomainError.ValidationError("gameMinute", "must be non-negative"))
    }
}

@JvmInline
value class CombatMillis private constructor(val value: Long) {
    companion object {
        fun of(value: Long): Checked<CombatMillis> =
            if (value >= 0) Checked.Value(CombatMillis(value))
            else Checked.Rejected(DomainError.ValidationError("combatMillis", "must be non-negative"))
    }
}

@JvmInline
value class Money private constructor(val amount: Long) {
    companion object {
        fun of(amount: Long): Checked<Money> =
            if (amount >= 0) Checked.Value(Money(amount))
            else Checked.Rejected(DomainError.ValidationError("money", "must be non-negative"))
    }

    fun plus(other: Money): Checked<Money> = try {
        of(Math.addExact(amount, other.amount))
    } catch (_: ArithmeticException) {
        Checked.Rejected(DomainError.ArithmeticOverflow("money addition"))
    }

    fun debit(other: Money): Checked<Money> =
        if (amount < other.amount) Checked.Rejected(DomainError.InsufficientResource("money"))
        else Checked.Value(Money(amount - other.amount))
}

@JvmInline
value class BasisPoint private constructor(val value: Int) {
    companion object {
        fun of(value: Int): Checked<BasisPoint> =
            if (value in 0..10_000) Checked.Value(BasisPoint(value))
            else Checked.Rejected(DomainError.ValidationError("basisPoint", "must be within 0..10000"))
    }
}

@JvmInline
value class ProbabilityPpm private constructor(val value: Int) {
    companion object {
        fun of(value: Int): Checked<ProbabilityPpm> =
            if (value in 0..1_000_000) Checked.Value(ProbabilityPpm(value))
            else Checked.Rejected(DomainError.ValidationError("probabilityPpm", "must be within 0..1000000"))
    }
}

@JvmInline
value class EntityId private constructor(val value: String) {
    companion object {
        fun of(value: String): Checked<EntityId> =
            if (value.isNotBlank()) Checked.Value(EntityId(value))
            else Checked.Rejected(DomainError.ValidationError("entityId", "must not be blank"))
    }
}

@JvmInline
value class SessionEpoch(val value: Long) {
    init {
        require(value >= 0) { "sessionEpoch must be non-negative" }
    }
}

@JvmInline
value class StateVersion(val value: Long) {
    init {
        require(value >= 0) { "stateVersion must be non-negative" }
    }
}

@JvmInline
value class RngStreamKey(val value: String) {
    init {
        require(value.isNotBlank()) { "rng stream key must not be blank" }
    }
}

data class RngStreamState(
    val streamKey: RngStreamKey,
    val algorithmVersion: String,
    val state: Long,
    val increment: Long,
    val drawCounter: Long
) {
    init {
        require(algorithmVersion.isNotBlank()) { "rng algorithm version must not be blank" }
        require(increment and 1L == 1L) { "pcg increment must be odd" }
        require(drawCounter >= 0) { "rng draw counter must be non-negative" }
    }
}

data class RngState(val streams: List<RngStreamState>) {
    init {
        require(streams.map(RngStreamState::streamKey).distinct().size == streams.size) {
            "rng stream keys must be unique"
        }
    }
}

@JvmInline
value class CommandId(val value: String) {
    init {
        require(value.isNotBlank()) { "commandId must not be blank" }
    }
}

@JvmInline
value class EventId(val value: String) {
    init {
        require(value.isNotBlank()) { "eventId must not be blank" }
    }
}

@JvmInline
value class PayloadHash(val value: String) {
    init {
        require(value.matches(Regex("[0-9a-f]{64}"))) { "payloadHash must be a lowercase SHA-256 hex string" }
    }
}

@JvmInline
value class SubMinuteMillis(val value: Int) {
    init {
        require(value in 0..59_999) { "subMinuteMs must be within 0..59999" }
    }
}

@JvmInline
value class EventSequence(val value: Long) {
    init {
        require(value >= 0) { "eventSequence must be non-negative" }
    }
}

sealed interface DomainError {
    data class ValidationError(val field: String, val reason: String) : DomainError
    data class ArithmeticOverflow(val operation: String) : DomainError
    data class StaleSession(val expected: SessionEpoch, val actual: SessionEpoch) : DomainError
    data class VersionConflict(val expected: StateVersion, val actual: StateVersion) : DomainError
    data class IdempotencyKeyReuse(val commandId: CommandId) : DomainError
    data class InsufficientResource(val resource: String) : DomainError
    data class ScheduleConflict(val actionIds: List<EntityId>) : DomainError
    data class ScheduledActionRequired(val mode: ProgressionMode) : DomainError
    data class AtomicElapsedActionRequired(val mode: ProgressionMode) : DomainError
    data class ResourceUnavailable(val resource: String) : DomainError
    data class StaleConsequencePreview(val actionId: EntityId) : DomainError
    data class BoundaryLimitReached(val reason: String) : DomainError
    data class SystemHalted(val reason: String) : DomainError
    data class AdvanceInProgress(val commandId: CommandId, val allowedControls: Set<AdvanceControl>) : DomainError
    data class UnsupportedFeature(val feature: String) : DomainError
    data object SessionClosed : DomainError
    data class PersistenceFailure(val reason: String) : DomainError
    data class InvariantViolation(val reason: String) : DomainError
    data class ContentCompatibilityError(val reason: String) : DomainError
}

enum class AdvanceControl { PAUSE, CANCEL_ADVANCE, APP_BACKGROUND, CLOSE }

enum class SessionLifecycle { OPEN, PAUSING, PAUSED, CLOSING, CLOSED }

enum class CloseReason { APPLICATION_REQUEST, PARENT_SCOPE }

data class SessionRuntimeState(
    val epoch: SessionEpoch,
    val lifecycle: SessionLifecycle,
    val activeAdvanceCommandId: CommandId?
)

data class CloseResult(
    val closedEpoch: SessionEpoch,
    val lastCommittedVersion: StateVersion,
    val outstandingJobs: Int
) {
    init {
        require(outstandingJobs == 0) { "close must not retain session jobs" }
    }
}

data class ControlRequest(
    val sessionEpoch: SessionEpoch,
    val expectedActiveCommandId: CommandId,
    val kind: AdvanceControl,
    val allowCommitDrain: Boolean
)

sealed interface ControlRequestResult {
    data class Accepted(val activeCommandId: CommandId) : ControlRequestResult
    data class Rejected(val error: DomainError) : ControlRequestResult
}

enum class ReceiptLifecycle { RUNNING, COMMITTED, INTERRUPTED, REJECTED }

sealed interface WorldCommandPayload {
    val codecId: String
    fun canonicalJson(): String
}

sealed interface DomainEventPayload {
    val codecId: String
    fun canonicalJson(): String
}

data class UnsupportedFeaturePayload(val feature: String) : WorldCommandPayload {
    init {
        require(feature.isNotBlank()) { "feature must not be blank" }
    }

    override val codecId: String = CODEC_ID

    override fun canonicalJson(): String = "{\"feature\":${CanonicalJson.string(feature)}}"

    companion object {
        const val CODEC_ID = "unsupported-feature.v1"
    }
}

/**
 * The only caller-supplied data for a short non-fast-forward action.  The
 * resolved outcome is deliberately absent: WorldEngine calculates and seals
 * it from the authoritative snapshot and the action-local RNG stream.
 */
/** Same-module conformance request; production domains wrap this in their own outer gameplay command. */
internal data class AtomicElapsedActionPayload(
    val actionKind: AtomicElapsedActionKind,
    val actionId: EntityId,
    val mode: ProgressionMode,
    val targetMinute: GameMinute,
    val actionInputCodec: String,
    val canonicalActionInput: String,
    val limits: TimeTraversalLimits,
    val actionInputHash: PayloadHash = canonicalPayloadHash(canonicalActionInput)
) : WorldCommandPayload {
    init {
        require(actionInputCodec.isNotBlank() && canonicalActionInput.isNotBlank()) { "atomic elapsed action input must be present" }
        require(actionInputHash == canonicalPayloadHash(canonicalActionInput)) { "atomic elapsed action input hash must match payload" }
        require(mode == actionKind.mode) { "atomic elapsed mode must match action kind" }
        require(mode != ProgressionMode.FAST_FORWARD && mode != ProgressionMode.TRAVEL) { "atomic elapsed mode must be short non-fast-forward" }
    }

    override val codecId: String = CODEC_ID

    override fun canonicalJson(): String = "{\"actionId\":${CanonicalJson.string(actionId.value)},\"actionInput\":${CanonicalJson.string(canonicalActionInput)},\"actionInputCodec\":${CanonicalJson.string(actionInputCodec)},\"actionInputHash\":${CanonicalJson.string(actionInputHash.value)},\"actionKind\":${CanonicalJson.string(actionKind.name)},\"limits\":{\"maxAdvanceMinute\":${limits.maxAdvanceMinute.value},\"maxBoundaryCount\":${limits.maxBoundaryCount},\"maxCandidatesPerBatch\":${limits.maxCandidatesPerBatch}},\"mode\":${CanonicalJson.string(mode.name)},\"targetMinute\":${targetMinute.value}}"

    companion object {
        const val CODEC_ID = "ATOMIC_ELAPSED_ACTION.v1"

        internal fun decode(canonicalJson: String): Checked<AtomicElapsedActionPayload> = try {
            val limits = atomicObjectField(canonicalJson, "limits") ?: return incompatible()
            val payload = AtomicElapsedActionPayload(
                AtomicElapsedActionKind.valueOf(atomicStringField(canonicalJson, "actionKind") ?: return incompatible()),
                EntityId.of(atomicStringField(canonicalJson, "actionId") ?: return incompatible()).atomicValueOrNull() ?: return incompatible(),
                ProgressionMode.valueOf(atomicStringField(canonicalJson, "mode") ?: return incompatible()),
                GameMinute.of(atomicLongField(canonicalJson, "targetMinute") ?: return incompatible()).atomicValueOrNull() ?: return incompatible(),
                atomicStringField(canonicalJson, "actionInputCodec") ?: return incompatible(),
                atomicStringField(canonicalJson, "actionInput") ?: return incompatible(),
                TimeTraversalLimits(
                    GameMinute.of(atomicLongField(limits, "maxAdvanceMinute") ?: return incompatible()).atomicValueOrNull() ?: return incompatible(),
                    (atomicLongField(limits, "maxBoundaryCount") ?: return incompatible()).toInt(),
                    (atomicLongField(limits, "maxCandidatesPerBatch") ?: return incompatible()).toInt()
                ),
                PayloadHash(atomicStringField(canonicalJson, "actionInputHash") ?: return incompatible())
            )
            if (payload.canonicalJson() == canonicalJson) Checked.Value(payload) else incompatible()
        } catch (_: IllegalArgumentException) {
            incompatible()
        }

        private fun incompatible(): Checked<Nothing> = Checked.Rejected(DomainError.ContentCompatibilityError("invalid atomic elapsed action payload"))
    }
}

internal enum class AtomicElapsedActionKind(val mode: ProgressionMode) {
    COMBAT(ProgressionMode.COMBAT_ELAPSED),
    DUNGEON(ProgressionMode.DUNGEON_ACTION),
    NORMAL(ProgressionMode.NORMAL_ACTION)
}

/** A new command claims the sealed predecessor; it cannot supply or replace its outcome. */
data class AtomicElapsedDecisionPayload(
    val predecessorEpoch: SessionEpoch,
    val predecessorCommandId: CommandId,
    val pendingSuffixHash: PayloadHash,
    val sealedOutcomeHash: PayloadHash,
    val gateId: String,
    val choiceId: String,
    val selectionCodec: String,
    val canonicalSelectionPayload: String,
    val selectionPayloadHash: PayloadHash = canonicalPayloadHash(canonicalSelectionPayload)
) : WorldCommandPayload {
    init {
        require(gateId.isNotBlank() && choiceId.isNotBlank() && selectionCodec.isNotBlank() && canonicalSelectionPayload.isNotBlank())
        require(selectionPayloadHash == canonicalPayloadHash(canonicalSelectionPayload)) { "atomic elapsed selection hash must match payload" }
    }

    val selection: DecisionSelection get() = DecisionSelection(gateId, choiceId, selectionCodec, canonicalSelectionPayload, selectionPayloadHash)

    override val codecId: String = CODEC_ID

    override fun canonicalJson(): String = "{\"choiceId\":${CanonicalJson.string(choiceId)},\"gateId\":${CanonicalJson.string(gateId)},\"pendingSuffixHash\":${CanonicalJson.string(pendingSuffixHash.value)},\"predecessorCommandId\":${CanonicalJson.string(predecessorCommandId.value)},\"predecessorEpoch\":${predecessorEpoch.value},\"sealedOutcomeHash\":${CanonicalJson.string(sealedOutcomeHash.value)},\"selectionCodec\":${CanonicalJson.string(selectionCodec)},\"selectionPayload\":${CanonicalJson.string(canonicalSelectionPayload)},\"selectionPayloadHash\":${CanonicalJson.string(selectionPayloadHash.value)}}"

    companion object {
        const val CODEC_ID = "ATOMIC_ELAPSED_DECISION.v1"

        fun decode(canonicalJson: String): Checked<AtomicElapsedDecisionPayload> = try {
            val payload = AtomicElapsedDecisionPayload(
                SessionEpoch(atomicLongField(canonicalJson, "predecessorEpoch") ?: return incompatible()),
                CommandId(atomicStringField(canonicalJson, "predecessorCommandId") ?: return incompatible()),
                PayloadHash(atomicStringField(canonicalJson, "pendingSuffixHash") ?: return incompatible()),
                PayloadHash(atomicStringField(canonicalJson, "sealedOutcomeHash") ?: return incompatible()),
                atomicStringField(canonicalJson, "gateId") ?: return incompatible(),
                atomicStringField(canonicalJson, "choiceId") ?: return incompatible(),
                atomicStringField(canonicalJson, "selectionCodec") ?: return incompatible(),
                atomicStringField(canonicalJson, "selectionPayload") ?: return incompatible(),
                PayloadHash(atomicStringField(canonicalJson, "selectionPayloadHash") ?: return incompatible())
            )
            if (payload.canonicalJson() == canonicalJson) Checked.Value(payload) else incompatible()
        } catch (_: IllegalArgumentException) {
            incompatible()
        }

        private fun incompatible(): Checked<Nothing> = Checked.Rejected(DomainError.ContentCompatibilityError("invalid atomic elapsed decision payload"))
    }
}

/** The only player-facing P2 schedule mutations; boundary transitions stay in WorldEngine evaluators. */
sealed interface ScheduleGameplayPayload : WorldCommandPayload

data class ScheduleReservePayload(
    val actionId: EntityId,
    val actionKind: String,
    val scheduledPayload: ScheduledActionPayload,
    val startMinute: GameMinute,
    val dueMinute: GameMinute,
    val effectiveMinute: GameMinute
) : ScheduleGameplayPayload {
    init { require(actionKind.isNotBlank() && dueMinute.value > startMinute.value) }
    override val codecId = CODEC_ID
    override fun canonicalJson(): String = "{\"actionId\":${CanonicalJson.string(actionId.value)},\"actionKind\":${CanonicalJson.string(actionKind)},\"dueMinute\":${dueMinute.value},\"effectiveMinute\":${effectiveMinute.value},\"scheduledPayload\":${CanonicalJson.string(scheduledPayload.canonicalJson())},\"startMinute\":${startMinute.value}}"
    companion object { const val CODEC_ID = "schedule.reserve.v1" }
}

data class ScheduleCancelPayload(
    val actionId: EntityId,
    val expectedRowVersion: StateVersion,
    val stage: CancellationStage
) : ScheduleGameplayPayload {
    override val codecId = CODEC_ID
    override fun canonicalJson(): String = "{\"actionId\":${CanonicalJson.string(actionId.value)},\"expectedRowVersion\":${expectedRowVersion.value},\"stage\":${CanonicalJson.string(stage.name)}}"
    companion object { const val CODEC_ID = "schedule.cancel.v1" }
}

data class ScheduleResolveConflictPayload(
    val reservation: ScheduleReservePayload,
    val selected: ScheduleResolution,
    val expectedRowVersions: Map<EntityId, StateVersion>,
    val previewCodec: String,
    val previewHash: PayloadHash
) : ScheduleGameplayPayload {
    init {
        require(previewCodec.isNotBlank())
        require(expectedRowVersions.keys.map(EntityId::value) == expectedRowVersions.keys.map(EntityId::value).sorted())
    }
    override val codecId = CODEC_ID
    override fun canonicalJson(): String = "{\"expectedRowVersions\":[${expectedRowVersions.entries.joinToString(",") { "{\"actionId\":${CanonicalJson.string(it.key.value)},\"rowVersion\":${it.value.value}}" }}],\"previewCodec\":${CanonicalJson.string(previewCodec)},\"previewHash\":${CanonicalJson.string(previewHash.value)},\"reservation\":${CanonicalJson.string(reservation.canonicalJson())},\"selected\":${CanonicalJson.string(selected.name)}}"
    companion object { const val CODEC_ID = "schedule.resolve-conflict.v1" }
}

object ScheduleGameplayPayloadCodec {
    fun decode(codecId: String, canonicalJson: String): Checked<ScheduleGameplayPayload> = try {
        val payload = when (codecId) {
            ScheduleReservePayload.CODEC_ID -> reserve(canonicalJson)
            ScheduleCancelPayload.CODEC_ID -> ScheduleCancelPayload(
                entity(string(canonicalJson, "actionId")),
                StateVersion(number(canonicalJson, "expectedRowVersion")),
                CancellationStage.valueOf(string(canonicalJson, "stage"))
            )
            ScheduleResolveConflictPayload.CODEC_ID -> {
                val reservationJson = string(canonicalJson, "reservation")
                val reservation = reserve(reservationJson)
                ScheduleResolveConflictPayload(
                    reservation,
                    ScheduleResolution.valueOf(string(canonicalJson, "selected")),
                    versions(canonicalJson),
                    string(canonicalJson, "previewCodec"),
                    PayloadHash(string(canonicalJson, "previewHash"))
                )
            }
            else -> return incompatible()
        }
        val reencoded = payload.canonicalJson()
        if (payload.codecId == codecId && reencoded == canonicalJson) Checked.Value(payload) else {
            val mismatch = canonicalJson.indices.firstOrNull { canonicalJson[it] != reencoded.getOrNull(it) } ?: minOf(canonicalJson.length, reencoded.length)
            incompatible("non-canonical schedule payload at $mismatch")
        }
    } catch (error: IllegalArgumentException) {
        incompatible(error.message ?: "invalid schedule gameplay payload")
    }

    private fun reserve(json: String): ScheduleReservePayload {
        val scheduled = when (val decoded = ScheduledActionPayloadCodec.decode(string(json, "scheduledPayload"))) {
            is Checked.Value -> decoded.value
            is Checked.Rejected -> throw IllegalArgumentException(decoded.error.toString())
        }
        return ScheduleReservePayload(
            entity(string(json, "actionId")), string(json, "actionKind"), scheduled,
            minute(number(json, "startMinute")), minute(number(json, "dueMinute")), minute(number(json, "effectiveMinute"))
        )
    }

    private fun versions(json: String): Map<EntityId, StateVersion> {
        val body = Regex("\\\"expectedRowVersions\\\":\\[(.*?)](?=,|})").find(json)?.groupValues?.get(1) ?: throw IllegalArgumentException("missing expected row versions")
        if (body.isEmpty()) return emptyMap()
        val matches = Regex("\\{\\\"actionId\\\":(\\\"(?:\\\\.|[^\\\"])*\\\"),\\\"rowVersion\\\":(\\d+)}").findAll(body).toList()
        val values = matches.associate { entity(CanonicalJson.parseString(it.groupValues[1]) ?: throw IllegalArgumentException()) to StateVersion(it.groupValues[2].toLong()) }
        if (values.size != matches.size || values.keys.map(EntityId::value) != values.keys.map(EntityId::value).sorted()) throw IllegalArgumentException("invalid expected row versions")
        return values
    }

    private fun string(json: String, field: String): String = Regex("\\\"$field\\\":(\\\"(?:\\\\.|[^\\\"])*\\\")")
        .find(json)?.groupValues?.get(1)?.let(CanonicalJson::parseString) ?: throw IllegalArgumentException("missing $field")
    private fun number(json: String, field: String): Long = Regex("\\\"$field\\\":(\\d+)(?=,|})")
        .find(json)?.groupValues?.get(1)?.toLongOrNull() ?: throw IllegalArgumentException("missing $field")
    private fun entity(value: String): EntityId = when (val checked = EntityId.of(value)) {
        is Checked.Value -> checked.value
        is Checked.Rejected -> throw IllegalArgumentException(checked.error.toString())
    }
    private fun minute(value: Long): GameMinute = when (val checked = GameMinute.of(value)) {
        is Checked.Value -> checked.value
        is Checked.Rejected -> throw IllegalArgumentException(checked.error.toString())
    }
    private fun incompatible(reason: String = "invalid schedule gameplay payload"): Checked<Nothing> = Checked.Rejected(DomainError.ContentCompatibilityError(reason))
}

data class UnsupportedFeatureEventPayload(val feature: String) : DomainEventPayload {
    init {
        require(feature.isNotBlank()) { "feature must not be blank" }
    }

    override val codecId: String = CODEC_ID

    override fun canonicalJson(): String = "{\"feature\":${CanonicalJson.string(feature)}}"

    companion object {
        const val CODEC_ID = "unsupported-feature-event.v1"
    }
}

data class CommandEnvelope<P : WorldCommandPayload>(
    val commandId: CommandId,
    val sessionEpoch: SessionEpoch,
    val expectedVersion: StateVersion?,
    val actorId: EntityId?,
    val payload: P,
    val payloadHash: PayloadHash
) {
    companion object {
        fun <P : WorldCommandPayload> create(
            commandId: CommandId,
            sessionEpoch: SessionEpoch,
            expectedVersion: StateVersion?,
            actorId: EntityId?,
            payload: P
        ): CommandEnvelope<P> = CommandEnvelope(
            commandId = commandId,
            sessionEpoch = sessionEpoch,
            expectedVersion = expectedVersion,
            actorId = actorId,
            payload = payload,
            payloadHash = CommandPayloadCodec.hash(payload)
        )
    }
}

data class DomainEvent<E : DomainEventPayload>(
    val eventId: EventId,
    val sourceId: EntityId?,
    val sourceEventId: EventId?,
    val sourceEpoch: SessionEpoch,
    val sourceCommandId: CommandId,
    val sourceVersion: StateVersion,
    val gameMinute: GameMinute,
    val subMinuteMs: SubMinuteMillis,
    val eventSequence: EventSequence,
    val visibility: EventVisibility,
    val importance: EventImportance,
    val payload: E
)

enum class EventVisibility {
    PUBLIC,
    PARTICIPANTS,
    OBSERVER_SCOPED,
    SYSTEM_HIDDEN
}

enum class EventImportance { LOW, NORMAL, HIGH, CRITICAL }

sealed interface CommandResult {
    val submissionSequence: Long?

    data class Accepted(
        override val submissionSequence: Long?
    ) : CommandResult

    data class Rejected(
        val error: DomainError,
        override val submissionSequence: Long?
    ) : CommandResult
}

interface AggregateState {
    val aggregateId: EntityId
    val aggregateType: String
    fun canonicalJson(): String
}

data class AggregateChange(
    val aggregateId: EntityId,
    val before: AggregateState?,
    val after: AggregateState?
) {
    init {
        require(before != null || after != null) { "aggregate change must carry a before or after state" }
        require(before?.aggregateId == null || before.aggregateId == aggregateId) {
            "aggregate before state id must match aggregate change id"
        }
        require(after?.aggregateId == null || after.aggregateId == aggregateId) {
            "aggregate after state id must match aggregate change id"
        }
    }
}

data class DomainDelta(
    val aggregateChanges: List<AggregateChange>,
    val rngState: RngState,
    val events: List<DomainEvent<out DomainEventPayload>>,
    val result: CommandResult,
    val worldChange: WorldStateChange? = null
)

data class CommitReceipt(
    val stateVersion: StateVersion,
    val result: CommandResult,
    val lifecycleStatus: ReceiptLifecycle = if (result is CommandResult.Accepted) ReceiptLifecycle.COMMITTED else ReceiptLifecycle.REJECTED,
    val lastCommittedSegmentNo: Int = -1
)

data class PersistedReceipt(
    val payloadHash: PayloadHash,
    val stateVersion: StateVersion,
    val result: CommandResult,
    val lifecycleStatus: ReceiptLifecycle = if (result is CommandResult.Accepted) ReceiptLifecycle.COMMITTED else ReceiptLifecycle.REJECTED,
    val lastCommittedSegmentNo: Int = -1,
    val timeAdvanceState: TimeAdvanceState? = null,
    val continuation: TimeAdvanceContinuation? = null
)

data class BoundaryRegistryBinding(
    val engineOrderVersion: Int,
    val sourceIds: List<String>,
    val boundaryOrderVersion: String = "BoundaryOrder.v1",
    val candidateCodecs: Set<String> = emptySet()
) {
    init {
        require(engineOrderVersion >= 0) { "engine order version must be non-negative" }
        require(sourceIds == sourceIds.sorted() && sourceIds.distinct().size == sourceIds.size) { "source ids must be canonical sorted and unique" }
        require(sourceIds.all { it.matches(Regex("[\\x21-\\x7e]+")) }) { "source ids must be printable ASCII" }
        require(sourceIds.isEmpty() || candidateCodecs.isNotEmpty()) { "non-empty source registry must declare candidate codecs" }
        require(candidateCodecs.all { it.matches(Regex("[\\x21-\\x7e]+")) }) { "candidate codecs must be printable ASCII" }
        require(boundaryOrderVersion.isNotBlank()) { "boundary order version must not be blank" }
    }
}

data class AuthoritativeWorldState(
    val clock: WorldClock,
    val calendar: ScheduleCalendar,
    val timeAdvance: TimeAdvanceState?,
    val boundaryBinding: BoundaryRegistryBinding
) {
    /** Phase 2 has exactly one durable semantic world; persistence owns its single row. */
    val worldId: String = WORLD_ID

    companion object {
        const val WORLD_ID = "WORLD"
    }
}

data class WorldSnapshot(
    val stateVersion: StateVersion,
    val world: AuthoritativeWorldState,
    val rngState: RngState,
    val aggregates: Map<EntityId, AggregateState>
)

data class WorldStateChange(
    val before: AuthoritativeWorldState,
    val after: AuthoritativeWorldState
)

sealed interface ExecutionPlan {
    data class Atomic(val delta: DomainDelta) : ExecutionPlan
    data class Segment(
        val expectedSegmentNo: Int,
        val delta: DomainDelta,
        val timeAdvanceState: TimeAdvanceState,
        val terminalResult: TimeAdvanceResult?,
        val continuation: TimeAdvanceContinuation? = null
    ) : ExecutionPlan
}

data class PublicSnapshot(val sessionEpoch: SessionEpoch, val stateVersion: StateVersion, val clock: WorldClock)

data class PublicDomainEvent(val eventId: EventId, val gameMinute: GameMinute, val type: String)

/** Public terminal reason for the command that produced a committed publication. */
data class PublicTimeAdvanceChoice(
    val choiceId: String,
    val label: String,
    val codec: String,
    val canonicalPayload: String,
    val payloadHash: PayloadHash = canonicalPayloadHash(canonicalPayload)
) {
    init {
        require(choiceId.isNotBlank() && label.isNotBlank() && codec.isNotBlank() && canonicalPayload.isNotBlank())
        require(payloadHash == canonicalPayloadHash(canonicalPayload)) { "public choice hash must match payload" }
    }

    companion object {
        const val CODEC_ID = "DecisionChoice.v1"

        fun fromChoiceId(choiceId: String): PublicTimeAdvanceChoice {
            require(choiceId.isNotBlank())
            val payload = "{\"choiceId\":${CanonicalJson.string(choiceId)}}"
            return PublicTimeAdvanceChoice(choiceId, choiceId, CODEC_ID, payload)
        }
    }
}

/** Public terminal reason and only the redacted data required to continue a decision. */
data class PublicTimeAdvanceTerminal(
    val commandId: CommandId,
    val result: TimeAdvanceResult,
    val gateId: String? = null,
    val choices: List<PublicTimeAdvanceChoice> = emptyList(),
    val pendingSuffixHash: PayloadHash? = null,
    val sealedOutcomeHash: PayloadHash? = null
) {
    init {
        if (result == TimeAdvanceResult.DECISION_REQUIRED) {
            require(!gateId.isNullOrBlank()) { "decision terminal must expose a gate id" }
            require(choices.size in 1..8) { "decision terminal must expose one to eight choices" }
            require(choices.map { it.choiceId }.distinct().size == choices.size)
            require(pendingSuffixHash != null) { "decision terminal must expose its pending suffix hash" }
        } else {
            require(gateId == null && choices.isEmpty() && pendingSuffixHash == null && sealedOutcomeHash == null) {
                "non-decision terminal must not expose decision details"
            }
        }
    }
}

data class CommittedPublication(
    val snapshot: PublicSnapshot,
    val events: List<PublicDomainEvent>,
    val sourceCommandId: CommandId,
    val timeAdvanceTerminal: PublicTimeAdvanceTerminal? = null
)

interface SavePort {
    suspend fun findReceipt(sessionEpoch: SessionEpoch, commandId: CommandId): PersistedReceipt?
    suspend fun commit(envelope: CommandEnvelope<out WorldCommandPayload>, delta: DomainDelta): CommitReceipt

    suspend fun commitSegment(
        envelope: CommandEnvelope<out WorldCommandPayload>,
        expectedSegmentNo: Int,
        delta: DomainDelta,
        timeAdvanceState: TimeAdvanceState,
        terminalResult: TimeAdvanceResult?,
        continuation: TimeAdvanceContinuation? = null
    ): SegmentCommitReceipt = throw UnsupportedOperationException("resumable segment commits are not supported")
}

object CommandPayloadCodec {
    private const val DOMAIN_PREFIX = "CMDPAYLOAD\u0000"

    fun encode(payload: WorldCommandPayload): ByteArray = payload.canonicalJson().toByteArray(StandardCharsets.UTF_8)

    fun hash(payload: WorldCommandPayload): PayloadHash {
        val bytes = (DOMAIN_PREFIX + payload.codecId + "\u0000").toByteArray(StandardCharsets.UTF_8) + encode(payload)
        return PayloadHash(
            MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { byte ->
                "%02x".format(byte.toInt() and 0xff)
            }
        )
    }

    fun decode(codecId: String, canonicalJson: String): Checked<WorldCommandPayload> {
        if (codecId == AdvanceTimePayload.CODEC_ID) return AdvanceTimePayload.decode(canonicalJson)
        if (codecId == AtomicElapsedDecisionPayload.CODEC_ID) return AtomicElapsedDecisionPayload.decode(canonicalJson)
        if (codecId in setOf(ScheduleReservePayload.CODEC_ID, ScheduleCancelPayload.CODEC_ID, ScheduleResolveConflictPayload.CODEC_ID)) {
            return ScheduleGameplayPayloadCodec.decode(codecId, canonicalJson)
        }
        if (codecId != UnsupportedFeaturePayload.CODEC_ID) return Checked.Rejected(DomainError.ContentCompatibilityError("unsupported command codec: $codecId"))
        val prefix = "{\"feature\":"
        if (!canonicalJson.startsWith(prefix) || !canonicalJson.endsWith("}")) {
            return Checked.Rejected(DomainError.ContentCompatibilityError("invalid unsupported-feature payload"))
        }
        val feature = CanonicalJson.parseString(canonicalJson.substring(prefix.length, canonicalJson.length - 1))
            ?: return Checked.Rejected(DomainError.ContentCompatibilityError("invalid unsupported-feature string"))
        return try {
            Checked.Value(UnsupportedFeaturePayload(feature))
        } catch (_: IllegalArgumentException) {
            Checked.Rejected(DomainError.ContentCompatibilityError("invalid unsupported-feature value"))
        }
    }
}

private fun atomicStringField(json: String, name: String): String? =
    Regex("\\\"$name\\\":(\\\"(?:\\\\.|[^\\\"])*\\\")")
        .find(json)?.groupValues?.get(1)?.let(CanonicalJson::parseString)

private fun atomicLongField(json: String, name: String): Long? =
    Regex("\\\"$name\\\":(-?\\d+)(?=,|})").find(json)?.groupValues?.get(1)?.toLongOrNull()

private fun atomicObjectField(json: String, name: String): String? {
    val start = Regex("\\\"$name\\\":\\{").find(json)?.range?.last ?: return null
    var depth = 1
    var quoted = false
    var escaped = false
    for (index in start + 1 until json.length) {
        when (val char = json[index]) {
            '"' -> if (!escaped) quoted = !quoted
            '\\' -> if (quoted) escaped = !escaped
            else -> {
                escaped = false
                if (!quoted && char == '{') depth++
                if (!quoted && char == '}' && --depth == 0) return json.substring(start, index + 1)
            }
        }
    }
    return null
}

private fun <T> Checked<T>.atomicValueOrNull(): T? = (this as? Checked.Value<T>)?.value

data class ElapsedActionAppliedEventPayload(
    val domainResultId: EntityId,
    val outcomeHash: PayloadHash
) : DomainEventPayload {
    override val codecId: String = CODEC_ID
    override fun canonicalJson(): String =
        "{\"domainResultId\":${CanonicalJson.string(domainResultId.value)},\"outcomeHash\":${CanonicalJson.string(outcomeHash.value)}}"

    companion object {
        const val CODEC_ID = "elapsed.action.applied.v1"
        fun decode(json: String): Checked<ElapsedActionAppliedEventPayload> = strictEventPayload(json) {
            ElapsedActionAppliedEventPayload(
                atomicStringField(json, "domainResultId")?.let(EntityId::of)?.atomicValueOrNull() ?: throw IllegalArgumentException(),
                PayloadHash(atomicStringField(json, "outcomeHash") ?: throw IllegalArgumentException())
            )
        }
    }
}

enum class BoundaryDomainEventType(val candidateKind: String, val codec: String) {
    CALENDAR_DAY_STARTED("calendar.day.start.v1", "calendar.day.started.v1"),
    CALENDAR_MONTH_STARTED("calendar.month.start.v1", "calendar.month.started.v1"),
    CALENDAR_YEAR_STARTED("calendar.year.start.v1", "calendar.year.started.v1"),
    SCHEDULED_ACTION_STARTED("scheduled.action.start.v1", "scheduled.action.started.v1"),
    SCHEDULED_ACTION_COMPLETED("scheduled.action.complete.v1", "scheduled.action.completed.v1"),
    DECISION_REQUIRED("decision.required.v1", "decision.required.v1"),
    DECISION_SELECTION_APPLIED("decision.selection.apply.v1", "decision.selection.applied.v1"),
    ECONOMY_SETTLED("economy.settlement.v1", "economy.settled.v1");

    companion object {
        fun forCandidate(candidateKind: String): BoundaryDomainEventType? = entries.firstOrNull { it.candidateKind == candidateKind }
        fun forCodec(codecId: String): BoundaryDomainEventType? = entries.firstOrNull { it.codec == codecId }
    }
}

data class BoundaryDomainEventPayload internal constructor(
    val eventType: BoundaryDomainEventType,
    val candidatePayloadHash: PayloadHash,
    val stableEntityId: String
) : DomainEventPayload {
    init { require(stableEntityId.isNotBlank()) }
    override val codecId: String = eventType.codec
    override fun canonicalJson(): String =
        "{\"candidatePayloadHash\":${CanonicalJson.string(candidatePayloadHash.value)},\"stableEntityId\":${CanonicalJson.string(stableEntityId)}}"

    companion object {
        fun decode(codecId: String, json: String): Checked<BoundaryDomainEventPayload> {
            val type = BoundaryDomainEventType.forCodec(codecId)
                ?: return Checked.Rejected(DomainError.ContentCompatibilityError("unsupported boundary event codec: $codecId"))
            return strictEventPayload(json) {
                BoundaryDomainEventPayload(
                    type,
                PayloadHash(atomicStringField(json, "candidatePayloadHash") ?: throw IllegalArgumentException()),
                atomicStringField(json, "stableEntityId") ?: throw IllegalArgumentException()
            )
        }
        }
    }
}

data class DecisionSelectionAppliedEventPayload(
    val gateId: String,
    val choiceId: String,
    val selectionCodec: String,
    val selectionPayloadHash: PayloadHash
) : DomainEventPayload {
    init {
        require(gateId.isNotBlank() && choiceId.isNotBlank() && selectionCodec.isNotBlank()) {
            "decision selection event fields must not be blank"
        }
    }

    override val codecId: String = CODEC_ID
    override fun canonicalJson(): String =
        "{\"choiceId\":${CanonicalJson.string(choiceId)},\"gateId\":${CanonicalJson.string(gateId)},\"selectionCodec\":${CanonicalJson.string(selectionCodec)},\"selectionPayloadHash\":${CanonicalJson.string(selectionPayloadHash.value)}}"

    companion object {
        const val CODEC_ID = "decision.selection.applied.v1"

        fun decode(json: String): Checked<DecisionSelectionAppliedEventPayload> = strictEventPayload(json) {
            DecisionSelectionAppliedEventPayload(
                atomicStringField(json, "gateId") ?: throw IllegalArgumentException(),
                atomicStringField(json, "choiceId") ?: throw IllegalArgumentException(),
                atomicStringField(json, "selectionCodec") ?: throw IllegalArgumentException(),
                PayloadHash(atomicStringField(json, "selectionPayloadHash") ?: throw IllegalArgumentException())
            )
        }
    }
}

private inline fun <T : DomainEventPayload> strictEventPayload(json: String, create: () -> T): Checked<T> = try {
    val payload = create()
    if (payload.canonicalJson() == json) Checked.Value(payload)
    else Checked.Rejected(DomainError.ContentCompatibilityError("non-canonical ${payload.codecId} payload"))
} catch (_: IllegalArgumentException) {
    Checked.Rejected(DomainError.ContentCompatibilityError("invalid event payload"))
}

object DomainEventPayloadCodec {
    fun decode(codecId: String, canonicalJson: String): Checked<DomainEventPayload> {
        if (codecId == ElapsedActionAppliedEventPayload.CODEC_ID) return ElapsedActionAppliedEventPayload.decode(canonicalJson)
        if (codecId == DecisionSelectionAppliedEventPayload.CODEC_ID) return DecisionSelectionAppliedEventPayload.decode(canonicalJson)
        if (BoundaryDomainEventType.forCodec(codecId) != null) return BoundaryDomainEventPayload.decode(codecId, canonicalJson)
        if (codecId != UnsupportedFeatureEventPayload.CODEC_ID) return Checked.Rejected(DomainError.ContentCompatibilityError("unsupported event codec: $codecId"))
        val prefix = "{\"feature\":"
        if (!canonicalJson.startsWith(prefix) || !canonicalJson.endsWith("}")) {
            return Checked.Rejected(DomainError.ContentCompatibilityError("invalid unsupported-feature event payload"))
        }
        val feature = CanonicalJson.parseString(canonicalJson.substring(prefix.length, canonicalJson.length - 1))
            ?: return Checked.Rejected(DomainError.ContentCompatibilityError("invalid unsupported-feature event string"))
        return try {
            Checked.Value(UnsupportedFeatureEventPayload(feature))
        } catch (_: IllegalArgumentException) {
            Checked.Rejected(DomainError.ContentCompatibilityError("invalid unsupported-feature event value"))
        }
    }
}

object DomainEventCodec {
    fun encode(event: DomainEvent<out DomainEventPayload>): ByteArray {
        val fields = listOf(
            event.eventId.value,
            event.sourceId?.value,
            event.sourceEventId?.value,
            event.sourceEpoch.value.toString(),
            event.sourceCommandId.value,
            event.sourceVersion.value.toString(),
            event.gameMinute.value.toString(),
            event.subMinuteMs.value.toString(),
            event.eventSequence.value.toString(),
            event.visibility.name,
            event.importance.name,
            event.payload.codecId,
            event.payload.canonicalJson()
        ).joinToString("|") { value -> value?.let(::encodeField) ?: "-" }
        return fields.toByteArray(StandardCharsets.UTF_8)
    }

    fun decode(bytes: ByteArray): Checked<DomainEvent<out DomainEventPayload>> = try {
        val fields = String(bytes, StandardCharsets.UTF_8).split('|')
        require(fields.size == 13) { "wrong field count" }
        val payload = when (val decoded = DomainEventPayloadCodec.decode(decodeField(fields[11]), decodeField(fields[12]))) {
            is Checked.Value -> decoded.value
            is Checked.Rejected -> return Checked.Rejected(decoded.error)
        }
        Checked.Value(
            DomainEvent(
                eventId = EventId(decodeField(fields[0])),
                sourceId = decodeNullableEntityId(fields[1]),
                sourceEventId = decodeNullableEventId(fields[2]),
                sourceEpoch = SessionEpoch(decodeField(fields[3]).toLong()),
                sourceCommandId = CommandId(decodeField(fields[4])),
                sourceVersion = StateVersion(decodeField(fields[5]).toLong()),
                gameMinute = checkedValue(GameMinute.of(decodeField(fields[6]).toLong())),
                subMinuteMs = SubMinuteMillis(decodeField(fields[7]).toInt()),
                eventSequence = EventSequence(decodeField(fields[8]).toLong()),
                visibility = EventVisibility.valueOf(decodeField(fields[9])),
                importance = EventImportance.valueOf(decodeField(fields[10])),
                payload = payload
            )
        )
    } catch (_: Exception) {
        Checked.Rejected(DomainError.ContentCompatibilityError("invalid domain event codec input"))
    }

    private fun encodeField(value: String): String = Base64.getUrlEncoder().withoutPadding()
        .encodeToString(value.toByteArray(StandardCharsets.UTF_8))

    private fun decodeField(value: String): String = String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8)

    private fun decodeNullableEntityId(value: String): EntityId? =
        if (value == "-") null else EntityId.of(decodeField(value)).let(::checkedValue)

    private fun decodeNullableEventId(value: String): EventId? =
        if (value == "-") null else EventId(decodeField(value))
}

private fun <T> checkedValue(value: Checked<T>): T = when (value) {
    is Checked.Value -> value.value
    is Checked.Rejected -> throw IllegalArgumentException(value.error.toString())
}

internal object CanonicalJson {
    fun string(value: String): String = buildString {
        append('"')
        Normalizer.normalize(value, Normalizer.Form.NFC).forEach { character ->
            when (character) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                '\b' -> append("\\b")
                '\u000C' -> append("\\f")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> if (character.code < 0x20) append("\\u%04x".format(character.code)) else append(character)
            }
        }
        append('"')
    }

    fun parseString(value: String): String? {
        if (value.length < 2 || value.first() != '"' || value.last() != '"') return null
        return buildString {
            var index = 1
            while (index < value.lastIndex) {
                val character = value[index++]
                if (character != '\\') {
                    if (character == '"' || character.code < 0x20) return null
                    append(character)
                    continue
                }
                if (index >= value.lastIndex) return null
                when (val escape = value[index++]) {
                    '"', '\\', '/' -> append(escape)
                    'b' -> append('\b')
                    'f' -> append('\u000C')
                    'n' -> append('\n')
                    'r' -> append('\r')
                    't' -> append('\t')
                    'u' -> {
                        if (index + 4 > value.lastIndex) return null
                        val codePoint = value.substring(index, index + 4).toIntOrNull(16) ?: return null
                        append(codePoint.toChar())
                        index += 4
                    }
                    else -> return null
                }
            }
        }
    }
}
