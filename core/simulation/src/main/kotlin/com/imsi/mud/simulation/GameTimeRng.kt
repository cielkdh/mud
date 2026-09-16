package com.imsi.mud.simulation

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.text.Normalizer

const val PCG32_XSH_RR_V1 = "PCG32-XSH-RR.v1"

data class ClockTarget(
    val totalGameMinutes: GameMinute,
    val subMinuteMs: SubMinuteMillis
) {
    val calendar: GameCalendar get() = GameClockMath.calendarAt(totalGameMinutes)
}

data class GameCalendar(
    val year: Long,
    val month: Int,
    val day: Int,
    val hour: Int,
    val minute: Int
) {
    init {
        require(year >= 1) { "year must be positive" }
        require(month in 1..12) { "month must be within 1..12" }
        require(day in 1..30) { "day must be within 1..30" }
        require(hour in 0..23) { "hour must be within 0..23" }
        require(minute in 0..59) { "minute must be within 0..59" }
    }
}

object GameClockMath {
    private const val MINUTES_PER_DAY = 24L * 60L

    fun targetAfter(elapsed: CombatMillis, clock: WorldClock): Checked<ClockTarget> = try {
        val elapsedWithRemainder = Math.addExact(clock.subMinuteMs.value.toLong(), elapsed.value)
        val addedMinutes = elapsedWithRemainder / 60_000L
        val newMinutes = Math.addExact(clock.minute.value, addedMinutes)
        Checked.Value(ClockTarget(GameMinute.of(newMinutes).valueOrThrow(), SubMinuteMillis((elapsedWithRemainder % 60_000L).toInt())))
    } catch (_: ArithmeticException) {
        Checked.Rejected(DomainError.ArithmeticOverflow("game clock target"))
    }

    fun calendarAt(totalGameMinutes: GameMinute): GameCalendar {
        val totalDays = totalGameMinutes.value / MINUTES_PER_DAY
        val minuteOfDay = (totalGameMinutes.value % MINUTES_PER_DAY).toInt()
        val dayOfYear = (totalDays % 360L).toInt()
        return GameCalendar(
            year = totalDays / 360L + 1,
            month = dayOfYear / 30 + 1,
            day = dayOfYear % 30 + 1,
            hour = minuteOfDay / 60,
            minute = minuteOfDay % 60
        )
    }
}

data class RngSeed(
    val rawWorldSeedBytes: List<Byte>,
    val initState: Long,
    val initSeq: Long
)

sealed interface RngOperation {
    data object NextUInt32 : RngOperation

    data class Bounded(val exclusiveUpperBound: UInt) : RngOperation {
        init { require(exclusiveUpperBound > 0u) { "exclusive upper bound must be positive" } }
    }
}

data class RngOutcome(val value: UInt, val stream: RngStreamState)
data class BernoulliOutcome(val value: Boolean, val stream: RngStreamState)
data class WeightedRngCandidate<T>(val id: String, val weight: Long, val value: T)
data class WeightedChoiceOutcome<T>(val id: String, val value: T, val stream: RngStreamState)
data class ShuffleOutcome<T>(val values: List<T>, val stream: RngStreamState)

enum class RngLeaf { COMBAT_HIT, COMBAT_CRIT, LOOT, DUNGEON, NPC_DECISION, WORLD_EVENT, POTENTIAL, PORTRAIT, NAME, ENHANCE }

fun canonicalRngStreamKey(leaf: RngLeaf, id: String? = null, attemptNo: Int? = null): RngStreamKey {
    fun part(value: String): String {
        require(value.isNotBlank() && '/' !in value) { "rng stream key segment must be non-blank and slash-free" }
        require(value == Normalizer.normalize(value, Normalizer.Form.NFC)) { "rng stream key segment must be NFC" }
        return value
    }
    return when (leaf) {
        RngLeaf.COMBAT_HIT -> RngStreamKey("combat/${part(requireNotNull(id))}/hit")
        RngLeaf.COMBAT_CRIT -> RngStreamKey("combat/${part(requireNotNull(id))}/crit")
        RngLeaf.LOOT -> RngStreamKey("loot/${part(requireNotNull(id))}")
        RngLeaf.DUNGEON -> RngStreamKey("dungeon/${part(requireNotNull(id))}")
        RngLeaf.NPC_DECISION -> RngStreamKey("npc/${part(requireNotNull(id))}/decision")
        RngLeaf.WORLD_EVENT -> RngStreamKey("world/event")
        RngLeaf.POTENTIAL -> RngStreamKey("potential/${part(requireNotNull(id))}")
        RngLeaf.PORTRAIT -> RngStreamKey("portrait/${part(requireNotNull(id))}")
        RngLeaf.NAME -> RngStreamKey("name/${part(requireNotNull(id))}")
        RngLeaf.ENHANCE -> {
            require(attemptNo != null && attemptNo >= 0) { "enhance attempt must be non-negative" }
            RngStreamKey("enhance/${part(requireNotNull(id))}/$attemptNo")
        }
    }.also { require((leaf == RngLeaf.WORLD_EVENT) == (id == null && attemptNo == null)) { "world event key has no id" } }
}

object DeterministicRng {
    private const val MULTIPLIER = 6_364_136_223_846_793_005L
    private val SUPPORTED_ALGORITHM = PCG32_XSH_RR_V1

    fun deriveSeed(worldSeed: String, streamKey: RngStreamKey): Checked<RngSeed> {
        if (!worldSeed.matches(Regex("[0-9a-f]{16}"))) {
            return Checked.Rejected(DomainError.ValidationError("worldSeed", "must be lower-case fixed16 hex"))
        }
        val numeric = worldSeed.toULong(16)
        val rawBytes = (7 downTo 0).map { index -> ((numeric shr (index * 8) and 0xffuL).toByte()) }
        val streamBytes = streamKey.value.toByteArray(StandardCharsets.UTF_8)
        val material = buildList<Byte> {
            addAll("MUD-RNG-PCG32.v1\u0000".toByteArray(StandardCharsets.UTF_8).toList())
            addAll(u32be(8).toList())
            addAll(rawBytes)
            addAll(u32be(streamBytes.size).toList())
            addAll(streamBytes.toList())
        }.toByteArray()
        val digest = MessageDigest.getInstance("SHA-256").digest(material)
        return Checked.Value(RngSeed(rawBytes, readLongBe(digest, 0), readLongBe(digest, 8)))
    }

    fun seeded(worldSeed: String, streamKey: RngStreamKey): Checked<RngStreamState> = when (val seed = deriveSeed(worldSeed, streamKey)) {
        is Checked.Value -> Checked.Value(initialize(seed.value.initState, seed.value.initSeq, streamKey))
        is Checked.Rejected -> seed
    }

    fun initialize(initState: Long, initSeq: Long, streamKey: RngStreamKey): RngStreamState {
        val increment = ((initSeq.toULong() shl 1) or 1uL).toLong()
        var state = 0L
        state = transition(state, increment).second
        state = (state.toULong() + initState.toULong()).toLong()
        state = transition(state, increment).second
        return RngStreamState(streamKey, SUPPORTED_ALGORITHM, state, increment, 0)
    }

    fun draw(stream: RngStreamState, operation: RngOperation): Checked<RngOutcome> {
        if (stream.algorithmVersion != SUPPORTED_ALGORITHM) {
            return Checked.Rejected(DomainError.UnsupportedFeature("rng algorithm ${stream.algorithmVersion}"))
        }
        return when (operation) {
            RngOperation.NextUInt32 -> nextRaw(stream)
            is RngOperation.Bounded -> bounded(stream, operation.exclusiveUpperBound)
        }
    }

    fun bernoulli(stream: RngStreamState, probabilityPpm: Int): Checked<BernoulliOutcome> {
        if (probabilityPpm !in 0..1_000_000) {
            return Checked.Rejected(DomainError.ValidationError("probabilityPpm", "must be within 0..1000000"))
        }
        if (probabilityPpm == 0) return Checked.Value(BernoulliOutcome(false, stream))
        if (probabilityPpm == 1_000_000) return Checked.Value(BernoulliOutcome(true, stream))
        return when (val result = draw(stream, RngOperation.Bounded(1_000_000u))) {
            is Checked.Value -> Checked.Value(BernoulliOutcome(result.value.value < probabilityPpm.toUInt(), result.value.stream))
            is Checked.Rejected -> result
        }
    }

    fun <T> weightedChoice(
        stream: RngStreamState,
        candidates: List<WeightedRngCandidate<T>>
    ): Checked<WeightedChoiceOutcome<T>> {
        val eligible = candidates.filter { it.weight > 0 }.sortedBy { it.id }
        if (eligible.isEmpty()) {
            return Checked.Rejected(DomainError.ValidationError("candidates", "must contain a positive-weight candidate"))
        }
        if (eligible.any { it.id.isBlank() } || eligible.map { it.id }.distinct().size != eligible.size) {
            return Checked.Rejected(DomainError.ValidationError("candidates", "ids must be non-blank and unique"))
        }
        var totalWeight = 0L
        for (candidate in eligible) {
            if (candidate.weight > UInt.MAX_VALUE.toLong() - totalWeight) {
                return Checked.Rejected(DomainError.ArithmeticOverflow("rng total weight"))
            }
            totalWeight += candidate.weight
        }
        val draw = when (val result = draw(stream, RngOperation.Bounded(totalWeight.toUInt()))) {
            is Checked.Value -> result.value
            is Checked.Rejected -> return result
        }
        var upperBound = 0L
        for (candidate in eligible) {
            upperBound += candidate.weight
            if (draw.value.toLong() < upperBound) {
                return Checked.Value(WeightedChoiceOutcome(candidate.id, candidate.value, draw.stream))
            }
        }
        return Checked.Rejected(DomainError.InvariantViolation("weighted choice exceeded total weight"))
    }

    fun <T> shuffle(stream: RngStreamState, values: List<T>): Checked<ShuffleOutcome<T>> {
        val shuffled = values.toMutableList()
        var current = stream
        for (index in shuffled.lastIndex downTo 1) {
            val draw = when (val result = draw(current, RngOperation.Bounded((index + 1).toUInt()))) {
                is Checked.Value -> result.value
                is Checked.Rejected -> return result
            }
            current = draw.stream
            val swapIndex = draw.value.toInt()
            val value = shuffled[index]
            shuffled[index] = shuffled[swapIndex]
            shuffled[swapIndex] = value
        }
        return Checked.Value(ShuffleOutcome(shuffled.toList(), current))
    }

    private fun bounded(stream: RngStreamState, bound: UInt): Checked<RngOutcome> {
        val threshold = (0u - bound) % bound
        var current = stream
        while (true) {
            when (val result = nextRaw(current)) {
                is Checked.Rejected -> return result
                is Checked.Value -> {
                    current = result.value.stream
                    if (result.value.value >= threshold) return Checked.Value(result.value.copy(value = result.value.value % bound))
                }
            }
        }
    }

    private fun nextRaw(stream: RngStreamState): Checked<RngOutcome> = try {
        val transition = transition(stream.state, stream.increment)
        Checked.Value(RngOutcome(transition.first, stream.copy(state = transition.second, drawCounter = Math.addExact(stream.drawCounter, 1))))
    } catch (_: ArithmeticException) {
        Checked.Rejected(DomainError.ArithmeticOverflow("rng draw counter"))
    }

    private fun transition(state: Long, increment: Long): Pair<UInt, Long> {
        val oldState = state.toULong()
        val nextState = (oldState * MULTIPLIER.toULong() + increment.toULong()).toLong()
        val xorshifted = (((oldState shr 18) xor oldState) shr 27).toUInt()
        val rotate = (oldState shr 59).toInt() and 31
        val output = if (rotate == 0) xorshifted else (xorshifted shr rotate) or (xorshifted shl ((-rotate) and 31))
        return output to nextState
    }

    private fun u32be(value: Int): ByteArray = byteArrayOf(
        (value ushr 24).toByte(),
        (value ushr 16).toByte(),
        (value ushr 8).toByte(),
        value.toByte()
    )

    private fun readLongBe(bytes: ByteArray, offset: Int): Long = (0 until 8).fold(0L) { result, index ->
        (result shl 8) or (bytes[offset + index].toLong() and 0xffL)
    }
}

private fun <T> Checked<T>.valueOrThrow(): T = when (this) {
    is Checked.Value -> value
    is Checked.Rejected -> error(error)
}
