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
    val counter: Long
) {
    init {
        require(algorithmVersion.isNotBlank()) { "rng algorithm version must not be blank" }
        require(counter >= 0) { "rng counter must be non-negative" }
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
    data class UnsupportedFeature(val feature: String) : DomainError
    data object SessionClosed : DomainError
    data class PersistenceFailure(val reason: String) : DomainError
    data class InvariantViolation(val reason: String) : DomainError
    data class ContentCompatibilityError(val reason: String) : DomainError
}

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

enum class EventVisibility { PUBLIC, PRIVATE, SYSTEM }

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
    val result: CommandResult
)

data class CommitReceipt(
    val stateVersion: StateVersion,
    val result: CommandResult
)

data class PersistedReceipt(
    val payloadHash: PayloadHash,
    val stateVersion: StateVersion,
    val result: CommandResult
)

interface SavePort {
    suspend fun findReceipt(sessionEpoch: SessionEpoch, commandId: CommandId): PersistedReceipt?
    suspend fun commit(envelope: CommandEnvelope<out WorldCommandPayload>, delta: DomainDelta): CommitReceipt
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
        if (codecId != UnsupportedFeaturePayload.CODEC_ID) {
            return Checked.Rejected(DomainError.ContentCompatibilityError("unsupported command codec: $codecId"))
        }
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

object DomainEventPayloadCodec {
    fun decode(codecId: String, canonicalJson: String): Checked<DomainEventPayload> {
        if (codecId != UnsupportedFeatureEventPayload.CODEC_ID) {
            return Checked.Rejected(DomainError.ContentCompatibilityError("unsupported event codec: $codecId"))
        }
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

private object CanonicalJson {
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
