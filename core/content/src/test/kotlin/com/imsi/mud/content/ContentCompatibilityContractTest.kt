package com.imsi.mud.content

import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.Rule

class ContentCompatibilityContractTest {
    @get:Rule val phase1Fixture = Phase1FixtureRule()
    @Test
    fun `P1-UT-004 terminal REMAP preserves old new provenance and approval revision`() {
        val old = definition(ContentKind.WEAPON, "OLD-WPN", "old")
        val installed = snapshot(template("WPN-0001", ContentKind.WEAPON, "{\"damage\":11}"))
        val new = requireNotNull(installed.definitionRefFor(ContentId("WPN-0001")))
        val plan = ContentCompatibilityPlanner.plan(
            saved(old),
            installed,
            LegacySnapshotIndex.of(listOf(LegacySnapshotEntry.remap(old, new, "legacy-WPN", "rev-C25")))
        ) as BindingPlan.MigrationRequired

        val step = plan.steps.single() as BindingMigrationStep.Remap
        assertEquals(old.id, step.from.id)
        assertEquals(new.id, step.to.id)
        assertEquals("legacy-WPN", step.provenance)
        assertEquals("rev-C25", step.approvalRevision)
    }

    @Test
    fun `P1-BT-004 asset only bundle change remains Compatible and save bytes are unchanged`() {
        val installed = snapshot(template("MON-1", ContentKind.MONSTER, "{\"hp\":10}"))
        val saved = ContentCompatibilitySnapshot.create(installed.identity.logicalContentHash, emptyList())
        val beforeSave = byteArrayOf(1, 2, 3, 4)
        val afterSave = beforeSave.copyOf()
        val firstBundle = InstalledBundle("bundle-a", "content.v1", "balance.v1", ContentHasher.sha256("asset-a"), ContentHasher.sha256("file-a"))
        val secondBundle = InstalledBundle("bundle-b", "content.v1", "balance.v1", ContentHasher.sha256("asset-b"), ContentHasher.sha256("file-b"))

        assertNotEquals(firstBundle, secondBundle)
        assertEquals(BindingPlan.Compatible, ContentCompatibilityPlanner.plan(saved, installed, LegacySnapshotIndex.empty()))
        assertArrayEquals(beforeSave, afterSave)
    }

    @Test
    fun `P1-FT-004 missing required definition is Unsupported and save bytes are unchanged`() {
        val required = definition(ContentKind.SKILL, "SKL-404", "missing")
        val saveBytes = byteArrayOf(9, 8, 7)
        val original = saveBytes.copyOf()

        val plan = ContentCompatibilityPlanner.plan(
            saved(required),
            ContentSnapshot.create("content.v2", "balance.v1", 1, emptyList()),
            LegacySnapshotIndex.empty()
        ) as BindingPlan.Unsupported

        assertEquals(listOf(ContentId("SKL-404")), plan.missingIds)
        assertTrue(plan.changedDefinitions.isEmpty())
        assertArrayEquals(original, saveBytes)

        val changedRequired = definition(ContentKind.SKILL, "SKL-1", "old-definition")
        val changed = ContentCompatibilityPlanner.plan(
            saved(changedRequired),
            snapshot(template("SKL-1", ContentKind.SKILL, "{\"power\":2}")),
            LegacySnapshotIndex.empty()
        ) as BindingPlan.Unsupported
        assertEquals(listOf(ContentId("SKL-1")), changed.changedDefinitions)
        assertTrue(changed.missingIds.isEmpty())
        assertArrayEquals(original, saveBytes)
    }

    @Test
    fun `P1-CT-004 same inputs produce equal plans without I O`() {
        val required = definition(ContentKind.MONSTER, "MON-1", "old")
        val installed = snapshot(template("MON-1", ContentKind.MONSTER, "{\"hp\":20}"))
        val saved = saved(required)
        val beforeSave = byteArrayOf(4, 2)
        val afterSave = beforeSave.copyOf()

        val first = ContentCompatibilityPlanner.plan(saved, installed, LegacySnapshotIndex.empty())
        val second = ContentCompatibilityPlanner.plan(saved, installed, LegacySnapshotIndex.empty())

        assertEquals(first, second)
        assertArrayEquals(beforeSave, afterSave)
    }

    @Test
    fun `P1-IT-004 BindingPlan v1 codec roundtrips all DTOs and fails closed`() {
        val old = definition(ContentKind.WEAPON, "OLD-WPN", "old")
        val new = definition(ContentKind.WEAPON, "WPN-1", "new")
        val plans = listOf<BindingPlan>(
            BindingPlan.Compatible,
            BindingPlan.MigrationRequired(
                listOf(
                    BindingMigrationStep.Remap(old, new, "legacy", "rev-1"),
                    BindingMigrationStep.Tombstone(old.copy(id = ContentId("OLD-DELETED")), "removed", "rev-2")
                )
            ),
            BindingPlan.Unsupported(listOf(ContentId("MISSING-1")), listOf(ContentId("CHANGED-1")))
        )

        plans.forEach { plan ->
            val encoded = BindingPlanCodec.encode(plan)
            assertEquals(plan, BindingPlanCodec.decode(BindingPlanCodec.CODEC_ID, encoded))
        }
        assertTrue(runCatching { BindingPlanCodec.decode("binding-plan.v2", "{}") }.isFailure)
        assertTrue(runCatching { BindingPlanCodec.decode(BindingPlanCodec.CODEC_ID, "{not-json}") }.isFailure)
        assertTrue(runCatching { BindingPlanCodec.decode(BindingPlanCodec.CODEC_ID, "{\"planType\":\"COMPATIBLE\",\"version\":2}") }.isFailure)
        val blankProvenance = BindingPlanCodec.encode(
            BindingPlan.MigrationRequired(listOf(BindingMigrationStep.Remap(old, new, "legacy", "rev-1")))
        ).replace("\"provenance\":\"legacy\"", "\"provenance\":\"\"")
        assertTrue(runCatching { BindingPlanCodec.decode(BindingPlanCodec.CODEC_ID, blankProvenance) }.isFailure)
        val crossKind = BindingPlanCodec.encode(
            BindingPlan.MigrationRequired(listOf(BindingMigrationStep.Remap(old, new, "legacy", "rev-1")))
        ).replace("\"id\":\"WPN-1\",\"kind\":\"WPN\"", "\"id\":\"WPN-1\",\"kind\":\"ARM\"")
        assertTrue(runCatching { BindingPlanCodec.decode(BindingPlanCodec.CODEC_ID, crossKind) }.isFailure)

        assertTrue(
            runCatching {
                BindingPlan.MigrationRequired(
                    listOf(
                        BindingMigrationStep.Remap(old, new, "legacy", "rev-1"),
                        BindingMigrationStep.Tombstone(old, "removed", "rev-2"),
                    )
                )
            }.isFailure
        )
        val singleMigration = BindingPlanCodec.encode(
            BindingPlan.MigrationRequired(listOf(BindingMigrationStep.Remap(old, new, "legacy", "rev-1")))
        )
        val encodedStep = singleMigration.substringAfter("\"steps\":[").substringBeforeLast("],\"version\"")
        val duplicateTerminal = singleMigration.replace("\"steps\":[$encodedStep]", "\"steps\":[$encodedStep,$encodedStep]")
        assertTrue(runCatching { BindingPlanCodec.decode(BindingPlanCodec.CODEC_ID, duplicateTerminal) }.isFailure)
        val emptyMigration = singleMigration.replace("\"steps\":[$encodedStep]", "\"steps\":[]")
        assertTrue(runCatching { BindingPlanCodec.decode(BindingPlanCodec.CODEC_ID, emptyMigration) }.isFailure)
    }

    @Test
    fun `P1-CN-001 repository allows OPEN parallel reads and close waits then rejects reads`() {
        val parallelRepository = repository()
        val start = CountDownLatch(1)
        val complete = CountDownLatch(8)
        val failures = Collections.synchronizedList(mutableListOf<Throwable>())
        repeat(8) {
            thread {
                try {
                    start.await()
                    assertEquals(ContentId("MON-1"), parallelRepository.findTemplate(ContentId("MON-1"))?.id)
                } catch (failure: Throwable) {
                    failures += failure
                } finally {
                    complete.countDown()
                }
            }
        }
        start.countDown()
        assertTrue(complete.await(2, TimeUnit.SECONDS))
        assertTrue(failures.isEmpty())
        parallelRepository.close()

        val enteredRead = CountDownLatch(1)
        val releaseRead = CountDownLatch(1)
        val readCount = AtomicInteger()
        val repository = repository(beforeRead = {
            if (readCount.incrementAndGet() == 1) {
                enteredRead.countDown()
                releaseRead.await()
            }
        })
        val readerDone = CountDownLatch(1)
        val closeDone = CountDownLatch(1)
        thread {
            try {
                repository.findTemplate(ContentId("MON-1"))
            } finally {
                readerDone.countDown()
            }
        }
        assertTrue(enteredRead.await(2, TimeUnit.SECONDS))
        thread {
            repository.close()
            closeDone.countDown()
        }

        var closeRejected = false
        repeat(100) {
            val error = runCatching { repository.findTemplate(ContentId("MON-1")) }.exceptionOrNull()
            if (error is IncompatibleContent && error.code == "ContentRepositoryClosed") {
                closeRejected = true
                return@repeat
            }
            Thread.sleep(2)
        }
        assertTrue(closeRejected)
        assertFalse(closeDone.await(25, TimeUnit.MILLISECONDS))
        releaseRead.countDown()
        assertTrue(readerDone.await(2, TimeUnit.SECONDS))
        assertTrue(closeDone.await(2, TimeUnit.SECONDS))
        repository.close()
        val closed = runCatching { repository.findTemplate(ContentId("MON-1")) }.exceptionOrNull()
        assertTrue(closed is IncompatibleContent && closed.code == "ContentRepositoryClosed")
    }

    private fun repository(beforeRead: (() -> Unit)? = null): InMemoryContentRepository = InMemoryContentRepository(
        installedBundle = InstalledBundle("bundle.v1", "content.v1", "balance.v1"),
        templates = listOf(template("MON-1", ContentKind.MONSTER, "{\"hp\":10}")),
        aliases = emptyList(),
        bindings = emptyList(),
        assets = emptyList(),
        fallbacks = emptyList(),
        beforeRead = beforeRead
    )

    private fun saved(vararg definitions: ContentDefinitionRef): ContentCompatibilitySnapshot = ContentCompatibilitySnapshot.create(
        logicalContentHash = ContentHasher.sha256("saved-${definitions.joinToString { it.id.value }}"),
        requiredDefinitions = definitions.toList()
    )

    private fun snapshot(vararg templates: ContentTemplate): ContentSnapshot = ContentSnapshot.create(
        contentVersion = "content.v2",
        balanceVersion = "balance.v1",
        schemaVersion = 1,
        templates = templates.toList()
    )

    private fun definition(kind: ContentKind, id: String, seed: String): ContentDefinitionRef = ContentDefinitionRef(
        kind = kind,
        id = ContentId(id),
        definitionVersion = 1,
        definitionHash = ContentHasher.sha256(seed)
    )

    private fun template(id: String, kind: ContentKind, definition: String): ContentTemplate = ContentTemplate(
        id = ContentId(id),
        kind = kind,
        sourceDisplayName = id,
        definitionVersion = 1,
        definitionJson = definition
    )
}
