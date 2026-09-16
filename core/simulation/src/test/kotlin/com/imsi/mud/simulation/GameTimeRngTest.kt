package com.imsi.mud.simulation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Test

class GameTimeRngTest {
    @Test
    fun `authoritative rng stream round trips through snapshot and domain delta without losing increment`() {
        val initialized = DeterministicRng.initialize(42, 54, RngStreamKey("restore"))
        val advanced = checked(DeterministicRng.draw(initialized, RngOperation.NextUInt32)).stream
        val restored = RngState(listOf(advanced)).streams.single()
        val delta = DomainDelta(emptyList(), RngState(listOf(restored)), emptyList(), CommandResult.Accepted(1))

        assertEquals(advanced, restored)
        assertEquals(advanced.increment, delta.rngState.streams.single().increment)
        assertEquals(advanced.drawCounter, delta.rngState.streams.single().drawCounter)
        assertEquals(
            checked(DeterministicRng.draw(advanced, RngOperation.NextUInt32)),
            checked(DeterministicRng.draw(restored, RngOperation.NextUInt32))
        )
    }

    @Test
    fun `P2-UT-002 clock accumulation and PCG golden vector are deterministic`() {
        val origin = WorldClock(checked(GameMinute.of(0)), SubMinuteMillis(0))
        var sixTenSecondClock = origin
        repeat(6) {
            val target = checked(GameClockMath.targetAfter(checked(CombatMillis.of(10_000)), sixTenSecondClock))
            sixTenSecondClock = WorldClock(target.totalGameMinutes, target.subMinuteMs)
        }
        val sixtySecond = checked(GameClockMath.targetAfter(checked(CombatMillis.of(60_000)), origin))

        assertEquals(sixtySecond, ClockTarget(sixTenSecondClock.minute, sixTenSecondClock.subMinuteMs))
        assertEquals(GameCalendar(1, 1, 1, 0, 1), sixtySecond.calendar)

        var stream = DeterministicRng.initialize(42, 54, RngStreamKey("vector"))
        val outputs = buildList {
            repeat(6) {
                val outcome = checked(DeterministicRng.draw(stream, RngOperation.NextUInt32))
                add(outcome.value.toString(16).padStart(8, '0'))
                stream = outcome.stream
            }
        }
        assertEquals(listOf("a15c02b7", "7b47f409", "ba1d3330", "83d2f293", "bfa4784b", "cbed606e"), outputs)
        assertEquals(6L, stream.drawCounter)
    }

    @Test
    fun `P2-BT-002 calendar rollover and raw world seed derivation use golden bytes`() {
        val lastMinute = WorldClock(checked(GameMinute.of(359L * 24L * 60L + 23L * 60L + 59L)), SubMinuteMillis(0))
        val next = checked(GameClockMath.targetAfter(checked(CombatMillis.of(60_000)), lastMinute))
        assertEquals(GameCalendar(2, 1, 1, 0, 0), next.calendar)

        val key = canonicalRngStreamKey(RngLeaf.COMBAT_HIT, "encounter-1")
        val seed = checked(DeterministicRng.deriveSeed("0123456789abcdef", key))
        assertEquals(listOf(1, 35, 69, 103, -119, -85, -51, -17).map(Int::toByte), seed.rawWorldSeedBytes)
        assertEquals("f09814a209fa697c", seed.initState.toULong().toString(16).padStart(16, '0'))
        assertEquals("11250d1cf45b5b0c", seed.initSeq.toULong().toString(16).padStart(16, '0'))

        var derived = checked(DeterministicRng.seeded("0123456789abcdef", key))
        val outputs = buildList {
            repeat(6) {
                val outcome = checked(DeterministicRng.draw(derived, RngOperation.NextUInt32))
                add(outcome.value.toString(16).padStart(8, '0'))
                derived = outcome.stream
            }
        }
        assertEquals(listOf("bde0dd7c", "6d5ccb16", "21580dd1", "c566d000", "93859cb5", "e919850e"), outputs)
        assertEquals(6L, derived.drawCounter)
    }

    @Test
    fun `P2-FT-002 unsupported algorithm and counter overflow fail closed`() {
        val unsupported = RngStreamState(RngStreamKey("unsupported"), "unknown.v1", 0, 1, 0)
        val unsupportedResult = DeterministicRng.draw(unsupported, RngOperation.NextUInt32)
        assertEquals(Checked.Rejected(DomainError.UnsupportedFeature("rng algorithm unknown.v1")), unsupportedResult)
        assertEquals(unsupportedResult, DeterministicRng.draw(unsupported, RngOperation.NextUInt32))

        val overflow = RngStreamState(RngStreamKey("overflow"), PCG32_XSH_RR_V1, 0, 1, Long.MAX_VALUE)
        val overflowResult = DeterministicRng.draw(overflow, RngOperation.NextUInt32)
        assertEquals(Checked.Rejected(DomainError.ArithmeticOverflow("rng draw counter")), overflowResult)
        assertEquals(overflowResult, DeterministicRng.draw(overflow, RngOperation.NextUInt32))
        assertEquals(
            Checked.Rejected(DomainError.ValidationError("worldSeed", "must be lower-case fixed16 hex")),
            DeterministicRng.deriveSeed("0123456789ABCDEF", RngStreamKey("invalid"))
        )
    }

    @Test
    fun `P2-CT-002 canonical leaf keys isolate hit draws from other streams`() {
        assertEquals("combat/encounter-1/hit", canonicalRngStreamKey(RngLeaf.COMBAT_HIT, "encounter-1").value)
        assertEquals("combat/encounter-1/crit", canonicalRngStreamKey(RngLeaf.COMBAT_CRIT, "encounter-1").value)
        assertEquals("loot/source-1", canonicalRngStreamKey(RngLeaf.LOOT, "source-1").value)
        assertEquals("dungeon/dungeon-1", canonicalRngStreamKey(RngLeaf.DUNGEON, "dungeon-1").value)
        assertEquals("npc/npc-1/decision", canonicalRngStreamKey(RngLeaf.NPC_DECISION, "npc-1").value)
        assertEquals("world/event", canonicalRngStreamKey(RngLeaf.WORLD_EVENT).value)
        assertEquals("potential/entity-1", canonicalRngStreamKey(RngLeaf.POTENTIAL, "entity-1").value)
        assertEquals("portrait/npc-1", canonicalRngStreamKey(RngLeaf.PORTRAIT, "npc-1").value)
        assertEquals("name/npc-1", canonicalRngStreamKey(RngLeaf.NAME, "npc-1").value)
        assertEquals("enhance/item-1/3", canonicalRngStreamKey(RngLeaf.ENHANCE, "item-1", 3).value)

        val crit = checked(DeterministicRng.seeded("0123456789abcdef", canonicalRngStreamKey(RngLeaf.COMBAT_CRIT, "encounter-1")))
        val loot = checked(DeterministicRng.seeded("0123456789abcdef", canonicalRngStreamKey(RngLeaf.LOOT, "source-1")))
        val npc = checked(DeterministicRng.seeded("0123456789abcdef", canonicalRngStreamKey(RngLeaf.NPC_DECISION, "npc-1")))
        val critBefore = checked(DeterministicRng.draw(crit, RngOperation.NextUInt32))
        val lootBefore = checked(DeterministicRng.draw(loot, RngOperation.NextUInt32))
        val npcBefore = checked(DeterministicRng.draw(npc, RngOperation.NextUInt32))

        var hit = checked(DeterministicRng.seeded("0123456789abcdef", canonicalRngStreamKey(RngLeaf.COMBAT_HIT, "encounter-1")))
        repeat(5) { hit = checked(DeterministicRng.draw(hit, RngOperation.NextUInt32)).stream }

        var repeatedHit = checked(DeterministicRng.seeded("0123456789abcdef", canonicalRngStreamKey(RngLeaf.COMBAT_HIT, "encounter-1")))
        repeat(5) { repeatedHit = checked(DeterministicRng.draw(repeatedHit, RngOperation.NextUInt32)).stream }

        assertEquals(critBefore, checked(DeterministicRng.draw(crit, RngOperation.NextUInt32)))
        assertEquals(lootBefore, checked(DeterministicRng.draw(loot, RngOperation.NextUInt32)))
        assertEquals(npcBefore, checked(DeterministicRng.draw(npc, RngOperation.NextUInt32)))
        assertEquals(hit, repeatedHit)
        assertEquals(5L, hit.drawCounter)
    }

    @Test
    fun `P2-UT-003 weighted choice and shuffle are canonical and input preserving`() {
        val stream = DeterministicRng.initialize(42, 54, RngStreamKey("collection"))
        val candidates = listOf(
            WeightedRngCandidate("z", 1, "z-value"),
            WeightedRngCandidate("ignored", -1, "ignored-value"),
            WeightedRngCandidate("a", 1, "a-value")
        )
        val choice = checked(DeterministicRng.weightedChoice(stream, candidates))
        assertEquals("z", choice.id)
        assertEquals(1L, choice.stream.drawCounter)
        assertEquals(
            Checked.Rejected(DomainError.ArithmeticOverflow("rng total weight")),
            DeterministicRng.weightedChoice(stream, listOf(WeightedRngCandidate("a", Long.MAX_VALUE, "a")))
        )

        val input = listOf("a", "b", "c", "d")
        val shuffled = checked(DeterministicRng.shuffle(stream, input))
        assertEquals(listOf("b", "c", "a", "d"), shuffled.values)
        assertEquals(listOf("a", "b", "c", "d"), input)
        assertNotSame(input, shuffled.values)
        assertEquals(3L, shuffled.stream.drawCounter)
    }

    @Test
    fun `P2-FT-003 bounded rejection consumes one counter per raw draw`() {
        val stream = RngStreamState(RngStreamKey("rejection"), PCG32_XSH_RR_V1, 0, -1, 0)
        val result = checked(DeterministicRng.draw(stream, RngOperation.Bounded(2_147_483_649u)))
        assertTrue(result.value < 2_147_483_649u)
        assertEquals(2L, result.stream.drawCounter)
    }

    @Test
    fun `P2-FT-004 invalid canonical keys fail at the boundary`() {
        assertIllegalArgument { canonicalRngStreamKey(RngLeaf.COMBAT_HIT, "") }
        assertIllegalArgument { canonicalRngStreamKey(RngLeaf.COMBAT_HIT, "a/b") }
        assertIllegalArgument { canonicalRngStreamKey(RngLeaf.COMBAT_HIT, "e\u0301") }
        assertIllegalArgument { canonicalRngStreamKey(RngLeaf.WORLD_EVENT, "event-1") }
        assertIllegalArgument { canonicalRngStreamKey(RngLeaf.COMBAT_HIT) }
    }

    private fun assertIllegalArgument(action: () -> Unit) {
        try {
            action()
            error("expected IllegalArgumentException")
        } catch (_: IllegalArgumentException) {
        }
    }

    private fun <T> checked(value: Checked<T>): T = when (value) {
        is Checked.Value -> value.value
        is Checked.Rejected -> error(value.error.toString())
    }
}
