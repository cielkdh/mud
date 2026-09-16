@file:Suppress("INVISIBLE_REFERENCE", "INVISIBLE_MEMBER")

package com.imsi.mud.simulation

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.imsi.mud.content.ContentSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Phase2RngIntegrationTest {
    @Test
    fun P2_IT_002() = runBlocking {
        val actionId = "p2-it-002-combat"
        val outerCommandId = CommandId("p2-it-002-outer")
        val source = CalendarBoundarySource()
        val port = RecordingSavePort()
        val session = WorldSession(
            SessionEpoch(1),
            port,
            this,
            ContentSnapshot.emptyForTest(),
            snapshot(1_439, source, actionId, "0123456789abcdef"),
            engine(source),
            Dispatchers.Unconfined
        )
        val envelope = CommandEnvelope.create(
            outerCommandId,
            SessionEpoch(1),
            StateVersion(0),
            entity("actor"),
            AtomicElapsedActionPayload(
                AtomicElapsedActionKind.COMBAT,
                entity(actionId),
                ProgressionMode.COMBAT_ELAPSED,
                minute(1_441),
                "combat.input.v1",
                "{\"attack\":1}",
                TimeTraversalLimits(minute(1_441), 8)
            )
        )

        assertEquals(CommandResult.Accepted(1), session.execute(envelope))
        assertEquals(1, port.commits)
        assertEquals(setOf(outerCommandId), port.receipts.keys)

        val delta = port.lastDelta!!
        assertEquals(1_441, delta.worldChange!!.after.clock.minute.value)
        assertEquals("combat/$actionId/hit", delta.rngState.streams.single().streamKey.value)
        assertEquals(PCG32_XSH_RR_V1, delta.rngState.streams.single().algorithmVersion)
        assertEquals(8889892628791201266L, delta.rngState.streams.single().state)
        assertEquals(4931366992596904975L, delta.rngState.streams.single().increment)
        assertEquals(1L, delta.rngState.streams.single().drawCounter)
        assertEquals(
            listOf("calendar.day.started.v1", "elapsed.action.applied.v1"),
            delta.events.map { it.payload.codecId }
        )
        assertEquals(listOf(1_440L, 1_441L), delta.events.map { it.gameMinute.value })
        assertTrue(delta.events.all { it.sourceCommandId == outerCommandId })
        assertTrue(delta.events.none { it.sourceCommandId == CommandId("p2-it-002-f002") })
        assertEquals(
            "ec37e79146e2347315c73c752e603b163069dfe35931d42367797efd5f88ce77",
            (delta.aggregateChanges.single().after as IntegrationAggregate).outcomeHash!!.value
        )
        session.close()
    }

    private fun engine(source: BoundarySource): WorldEngine = WorldEngine(listOf(source)).useAtomicElapsedOutcomeCalculatorForConformance(
        AtomicElapsedOutcomeCalculator { _, payload, rng ->
            when (val draw = DeterministicRng.draw(rng, RngOperation.NextUInt32)) {
                is Checked.Rejected -> draw
                is Checked.Value -> Checked.Value(
                    AtomicElapsedOutcomeResult(
                        SealedElapsedOutcome(
                            "combat.result.v1",
                            draw.value.value.toString(),
                            payload.targetMinute,
                            payload.actionId
                        ),
                        draw.value.stream
                    )
                )
            }
        }
    ).useAtomicElapsedOutcomeApplierForConformance(AtomicElapsedOutcomeApplier { snapshot, outcome ->
        val current = snapshot.aggregates[outcome.domainResultId] as IntegrationAggregate
        require(current.applyCount == 0 && current.outcomeHash == null)
        snapshot.copy(aggregates = snapshot.aggregates + (outcome.domainResultId to current.copy(applyCount = 1, outcomeHash = outcome.hash)))
    })

    private fun snapshot(at: Long, source: BoundarySource, actionId: String, worldSeed: String): WorldSnapshot {
        val key = canonicalRngStreamKey(RngLeaf.COMBAT_HIT, actionId)
        val stream = when (val seeded = DeterministicRng.seeded(worldSeed, key)) {
            is Checked.Value -> seeded.value
            is Checked.Rejected -> error(seeded.error.toString())
        }
        return WorldSnapshot(
            StateVersion(0),
            AuthoritativeWorldState(
                WorldClock(minute(at)),
                ScheduleCalendar(emptyList(), emptyMap()),
                null,
                BoundaryRegistryBinding(
                    1,
                    (listOf(source.sourceId) + "elapsed.action").sorted(),
                    candidateCodecs = setOf("CalendarBoundaryPayload.v1", SealedElapsedOutcome.CODEC_ID)
                )
            ),
            RngState(listOf(stream)),
            mapOf(entity(actionId) to IntegrationAggregate(entity(actionId), 0, null))
        )
    }

    private data class IntegrationAggregate(
        override val aggregateId: EntityId,
        val applyCount: Int,
        val outcomeHash: PayloadHash?
    ) : AggregateState {
        override val aggregateType: String = "p2-it-002.v1"
        override fun canonicalJson(): String = "{\"applyCount\":$applyCount,\"outcomeHash\":${outcomeHash?.let { CanonicalJson.string(it.value) } ?: "null"}}"
    }

    private class RecordingSavePort : SavePort {
        val receipts = mutableMapOf<CommandId, PersistedReceipt>()
        var commits = 0
        var lastDelta: DomainDelta? = null

        override suspend fun findReceipt(sessionEpoch: SessionEpoch, commandId: CommandId): PersistedReceipt? = receipts[commandId]

        override suspend fun commit(envelope: CommandEnvelope<out WorldCommandPayload>, delta: DomainDelta): CommitReceipt {
            commits++
            lastDelta = delta
            return CommitReceipt(StateVersion(commits.toLong()), delta.result).also { receipt ->
                receipts[envelope.commandId] = PersistedReceipt(envelope.payloadHash, receipt.stateVersion, receipt.result)
            }
        }
    }

    private fun minute(value: Long): GameMinute = when (val checked = GameMinute.of(value)) {
        is Checked.Value -> checked.value
        is Checked.Rejected -> error(checked.error.toString())
    }

    private fun entity(value: String): EntityId = when (val checked = EntityId.of(value)) {
        is Checked.Value -> checked.value
        is Checked.Rejected -> error(checked.error.toString())
    }
}
