package com.imsi.mud.content

import java.nio.file.Files
import java.nio.file.Path
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.Rule

class ContentContractsTest {
    @get:Rule val phase1Fixture = Phase1FixtureRule()
    @Test
    fun `P1-UT-001 C25 decoder seals all twenty definition kinds and rejects unknown or raw AST fields`() {
        val sourceRoot = Path.of("..", "..", "content", "source", "catalog").toAbsolutePath().normalize()
        val decoded = ContentKind.entries.map { kind ->
            val document = CanonicalJson.parse(Files.readString(sourceRoot.resolve("${kind.wireValue}.json"))) as CanonicalJson.JsonObject
            val record = ((document.values.getValue("records") as CanonicalJson.JsonArray).values.first() as CanonicalJson.JsonObject)
            fun string(name: String): String = (record.values.getValue(name) as CanonicalJson.JsonString).value
            fun optionalString(name: String): String? = when (val value = record.values.getValue(name)) {
                is CanonicalJson.JsonString -> value.value
                else -> null
            }
            val template = ContentTemplate(
                id = ContentId(string("id")),
                kind = kind,
                sourceDisplayName = string("sourceDisplayName"),
                displayNameOverride = optionalString("displayNameOverride"),
                grade = optionalString("grade"),
                minLevel = (record.values.getValue("minLevel") as? CanonicalJson.JsonNumber)?.value?.intValueExact(),
                tags = ((record.values.getValue("tags") as CanonicalJson.JsonArray).values).map { (it as CanonicalJson.JsonString).value },
                definitionVersion = (record.values.getValue("definitionVersion") as CanonicalJson.JsonNumber).value.intValueExact(),
                definitionJson = record.values.getValue("definition").render(),
                enabled = (record.values.getValue("enabled") as CanonicalJson.JsonLiteral).value == "true",
            )
            ContentDefinitionV1Decoder.decode(template)
        }

        assertEquals(ContentKind.entries.toSet(), decoded.map(ContentDefinitionV1::kind).toSet())
        assertEquals(20, decoded.map { it::class }.distinct().size)
        val weapon = decoded.filterIsInstance<ContentDefinitionV1.Weapon>().single()
        assertTrue(weapon.fields is WeaponDefinitionV1)
        assertEquals(weapon.definition.name, (weapon.fields as WeaponDefinitionV1).name)
        assertTrue(weapon.definition.recommendedLevel >= 1)
        val unknown = template("WPN-invalid", weapon.canonicalJson.dropLast(1) + ",\"unknown\":1}", ContentKind.WEAPON)
        assertTrue(runCatching { ContentDefinitionV1Decoder.decode(unknown) }.isFailure)
        val accessory = decoded.filterIsInstance<ContentDefinitionV1.Accessory>().single()
        val rawEffect = template(
            "ACC-invalid",
            accessory.canonicalJson.replace("\"effects\":[]", "\"effects\":[\"raw executable text\"]"),
            ContentKind.ACCESSORY,
        )
        assertTrue(runCatching { ContentDefinitionV1Decoder.decode(rawEffect) }.isFailure)

        val typedEffectJson = """[{"type":"TriggeredEffect","trigger":"ON_DAMAGE_TAKEN","target":"SELF","chancePpm":50000,"cooldown":{"type":"COMBAT_MS","value":5000},"effects":[{"type":"AddShield","amount":{"type":"Multiply","value":{"type":"ResourceMax","target":"SELF","resource":"HP"},"ratioBp":1000},"duration":{"type":"COMBAT_MS","value":3000}}]}]"""
        val typedEffect = template(
            "ACC-typed",
            accessory.canonicalJson.replace("\"effects\":[]", "\"effects\":$typedEffectJson"),
            ContentKind.ACCESSORY,
        )
        val decodedTypedEffect = ContentDefinitionV1Decoder.decode(typedEffect) as ContentDefinitionV1.Accessory
        val triggered = decodedTypedEffect.definition.effects.single() as EffectNodeV1.TriggeredEffect
        assertEquals(TriggerV1.ON_DAMAGE_TAKEN, triggered.trigger)
        assertEquals(TargetSelectorV1.SELF, triggered.target)
        assertEquals(50_000L, triggered.chancePpm)
        assertEquals(DurationSpecV1(TimeDomainV1.COMBAT_MS, 5_000), triggered.cooldown)
        val shield = triggered.effects.single() as EffectNodeV1.AddShield
        val amount = shield.amount as ValueExprV1.Multiply
        assertEquals(1_000L, amount.ratioBp)
        assertEquals(
            ValueExprV1.ResourceMax(TargetSelectorV1.SELF, ResourceTypeV1.HP),
            amount.value,
        )

        val unknownType = template(
            "ACC-unknown-type",
            typedEffect.definitionJson.replace("\"type\":\"TriggeredEffect\"", "\"type\":\"Anything\""),
            ContentKind.ACCESSORY,
        )
        assertTrue(runCatching { ContentDefinitionV1Decoder.decode(unknownType) }.isFailure)
        val missingType = template(
            "ACC-missing-type",
            typedEffect.definitionJson.replace("\"type\":\"TriggeredEffect\"", "\"type\":null"),
            ContentKind.ACCESSORY,
        )
        assertTrue(runCatching { ContentDefinitionV1Decoder.decode(missingType) }.isFailure)
        val invalidChance = template(
            "ACC-invalid-chance",
            typedEffect.definitionJson.replace("\"chancePpm\":50000", "\"chancePpm\":1000001"),
            ContentKind.ACCESSORY,
        )
        assertTrue(runCatching { ContentDefinitionV1Decoder.decode(invalidChance) }.isFailure)
        val invalidTimeDomain = template(
            "ACC-invalid-time-domain",
            typedEffect.definitionJson.replace("\"type\":\"COMBAT_MS\"", "\"type\":\"WALL_CLOCK_MS\""),
            ContentKind.ACCESSORY,
        )
        assertTrue(runCatching { ContentDefinitionV1Decoder.decode(invalidTimeDomain) }.isFailure)

        var tooDeepCondition = "{\"type\":\"Always\"}"
        repeat(17) { tooDeepCondition = "{\"type\":\"Not\",\"expr\":$tooDeepCondition}" }
        val tooDeepEffect = """[{"type":"TriggeredEffect","trigger":"ON_HIT","target":"CURRENT_TARGET","condition":$tooDeepCondition,"effects":[{"type":"Heal","amount":{"type":"ConstInt","value":1}}]}]"""
        val tooDeep = template(
            "ACC-too-deep",
            accessory.canonicalJson.replace("\"effects\":[]", "\"effects\":$tooDeepEffect"),
            ContentKind.ACCESSORY,
        )
        assertTrue(runCatching { ContentDefinitionV1Decoder.decode(tooDeep) }.isFailure)

        val healNode = "{\"type\":\"Heal\",\"amount\":{\"type\":\"ConstInt\",\"value\":1}}"
        val tooMany = template(
            "ACC-too-many",
            accessory.canonicalJson.replace("\"effects\":[]", "\"effects\":[${List(129) { healNode }.joinToString()}]"),
            ContentKind.ACCESSORY,
        )
        assertTrue(runCatching { ContentDefinitionV1Decoder.decode(tooMany) }.isFailure)

        val referencedSkill = template(
            "ACC-reference",
            accessory.canonicalJson.replace(
                "\"effects\":[]",
                "\"effects\":[{\"type\":\"ApplyDamage\",\"damageType\":\"FIRE\",\"amount\":{\"type\":\"SkillPower\",\"sourceSkill\":\"SKL-001\"}}]",
            ),
            ContentKind.ACCESSORY,
        )
        val referencedDefinition = ContentDefinitionV1Decoder.decode(referencedSkill)
        assertEquals(setOf(ContentId("SKL-001")), referencedDefinition.references)
        assertEquals(
            setOf(TypedContentReferenceV1(ContentId("SKL-001"), ContentKind.SKILL)),
            referencedDefinition.typedReferences,
        )
        assertEquals(
            setOf(TypedContentReferenceV1(ContentId("SKL-001"), ContentKind.SKILL)),
            referencedDefinition.typedReferences,
        )
    }

    @Test
    fun `P1-UT-001 importer preserves every V1 kind source field and locator without a source id alias`() {
        val records = ContentKind.entries.mapIndexed { index, kind ->
            ContentSourceTemplate(
                location = SourceLocation("catalog/${kind.wireValue}.json", index + 1),
                id = "${kind.wireValue}-source-${index + 1}",
                kind = kind.wireValue,
                sourceDisplayName = "source name ${index + 1}",
                displayNameOverride = "display name ${index + 1}",
                grade = "grade-${index + 1}",
                minLevel = index,
                tags = listOf("tag-b", "tag-a"),
                definitionVersion = 1,
                definitionJson = "{\"amount\":${index + 1},\"unit\":\"bp\"}",
                enabled = index % 2 == 0,
            )
        }

        val result = CatalogImporter.import(records)

        assertTrue(result.diagnostics.isEmpty())
        assertEquals(ContentKind.entries.size, result.templates.size)
        records.forEach { source ->
            val template = result.templates.single { it.id.value == source.id }
            assertEquals(source.id, template.id.value)
            assertEquals(source.kind, template.kind.wireValue)
            assertEquals(source.sourceDisplayName, template.sourceDisplayName)
            assertEquals(source.displayNameOverride, template.displayNameOverride)
            assertEquals(source.displayNameOverride, template.effectiveDisplayName)
            assertEquals(source.grade, template.grade)
            assertEquals(source.minLevel, template.minLevel)
            assertEquals(source.tags.sorted(), template.tags)
            assertEquals(source.definitionVersion, template.definitionVersion)
            assertEquals(source.enabled, template.enabled)
            assertEquals(source.location, result.sourceLocationsById[template.id])
        }
        assertFalse(ContentTemplate::class.java.declaredFields.any { it.name.equals("sourceId", ignoreCase = true) })
    }

    @Test
    fun `P1-UT-001 importer preserves the closed kind contract and reports invalid source rows stably`() {
        val result = CatalogImporter.import(
            listOf(
                ContentSourceTemplate(SourceLocation("catalog.csv", 4), "npc-e\u0301", "MON", "NFD", 1, "{}"),
                ContentSourceTemplate(SourceLocation("catalog.csv", 2), "npc-1", "MON", "One", 1, "{}"),
                ContentSourceTemplate(SourceLocation("catalog.csv", 3), "npc-1", "MON", "Two", 1, "{}"),
                ContentSourceTemplate(SourceLocation("catalog.csv", 5), "future-1", "FUTURE", "Future", 1, "{}"),
                ContentSourceTemplate(SourceLocation("catalog.csv", 6), "old-1", "MON", "Old", 2, "{}"),
                ContentSourceTemplate(SourceLocation("catalog.csv", 7), "disabled-1", "MON", "Disabled", 1, "{}", enabled = false)
            )
        )

        assertEquals(
            listOf(
                "DUPLICATE_CONTENT_ID",
                "NON_NFC_CONTENT_ID",
                "UNSUPPORTED_CONTENT_KIND",
                "UNSUPPORTED_DEFINITION_VERSION"
            ),
            result.diagnostics.map(ContentDiagnostic::code)
        )
        assertFalse(result.isValid)
        assertFalse(result.templates.single { it.id == ContentId("disabled-1") }.enabled)
    }

    @Test
    fun `P1-UT-002 snapshot canonicalizes definitions and has deterministic logical hash`() {
        val first = ContentSnapshot.create(
            contentVersion = "content.v1",
            balanceVersion = "balance.v1",
            schemaVersion = 1,
            templates = listOf(
                template("mon-b", "{\"b\":2,\"a\":1}"),
                template("mon-a", "{ \"z\" : [ true , null ], \"a\" : \"x\" }")
            )
        )
        val second = ContentSnapshot.create(
            contentVersion = "content.v1",
            balanceVersion = "balance.v1",
            schemaVersion = 1,
            templates = listOf(
                template("mon-a", "{\"a\":\"x\",\"z\":[true,null]}"),
                template("mon-b", "{\"a\":1,\"b\":2}")
            )
        )

        assertEquals(first.identity.logicalContentHash, second.identity.logicalContentHash)
        assertEquals(listOf("mon-a", "mon-b"), first.templatesById.keys.map(ContentId::value))
        assertTrue(runCatching { ContentSnapshot.create("c", "b", 1, listOf(template("mon-a"), template("mon-a"))) }.isFailure)
        assertTrue(runCatching { ContentSnapshot.create("c", "b", 1, listOf(template("e\u0301"))) }.isFailure)
        assertTrue(runCatching { ContentSnapshot.create("c", "b", 2, listOf(template("mon-a"))) }.isFailure)
        assertTrue(runCatching { template("mon-a", "{\"same\":1,\"same\":2}") }.isFailure)
        assertTrue(runCatching { ContentSnapshot.from(first.identity, mapOf(ContentId("wrong") to template("mon-a"))) }.isFailure)
        assertTrue(
            runCatching {
                ContentTemplate(ContentId("mon-nfc"), ContentKind.MONSTER, "e\u0301", definitionVersion = 1, definitionJson = "{}")
            }.isFailure
        )
        assertTrue(
            runCatching {
                AssetImage(AssetId("asset-nfc"), AssetCategory.PORTRAIT, "e\u0301.webp", 1, 1, 1, ContentHasher.sha256("asset"))
            }.isFailure
        )
    }

    @Test
    fun `P1-UT-002 definition hash covers the C25 gameplay envelope but excludes display fields`() {
        val base = template("mon-hash", "{\"hp\":10}", sourceDisplayName = "Base name")
        val baseHash = ContentHasher.definitionRef(base).definitionHash
        assertNotEquals(baseHash, ContentHasher.definitionRef(template("mon-hash", "{\"hp\":10}", grade = "EPIC")).definitionHash)
        assertNotEquals(baseHash, ContentHasher.definitionRef(template("mon-hash", "{\"hp\":10}", minLevel = 2)).definitionHash)
        assertNotEquals(baseHash, ContentHasher.definitionRef(template("mon-hash", "{\"hp\":10}", tags = listOf("fire"))).definitionHash)
        assertNotEquals(baseHash, ContentHasher.definitionRef(template("mon-hash", "{\"hp\":10}", enabled = false)).definitionHash)

        val renamed = template("mon-hash", "{\"hp\":10}", sourceDisplayName = "Renamed")
        assertEquals(baseHash, ContentHasher.definitionRef(renamed).definitionHash)
        assertNotEquals(
            ContentSnapshot.create("content.v1", "balance.v1", 1, listOf(base)).identity.logicalContentHash,
            ContentSnapshot.create("content.v1", "balance.v1", 1, listOf(renamed)).identity.logicalContentHash
        )

        val source = CanonicalJson.parse(
            Files.readString(Path.of("..", "..", "content", "source", "catalog", "ACC.json").toAbsolutePath().normalize())
        ) as CanonicalJson.JsonObject
        val record = ((source.values.getValue("records") as CanonicalJson.JsonArray).values.first() as CanonicalJson.JsonObject)
        val canonicalDefinition = record.values.getValue("definition") as CanonicalJson.JsonObject
        fun canonicalTemplate(definition: CanonicalJson.JsonObject): ContentTemplate = ContentTemplate(
            id = ContentId((record.values.getValue("id") as CanonicalJson.JsonString).value),
            kind = ContentKind.ACCESSORY,
            sourceDisplayName = (record.values.getValue("sourceDisplayName") as CanonicalJson.JsonString).value,
            grade = (record.values.getValue("grade") as CanonicalJson.JsonString).value,
            definitionVersion = 1,
            definitionJson = definition.render(),
            enabled = false
        )

        val canonicalBase = canonicalTemplate(canonicalDefinition)
        val renamedDefinition = CanonicalJson.JsonObject(
            canonicalDefinition.values + ("name" to CanonicalJson.JsonString("표시명 교정"))
        )
        val unresolved = canonicalDefinition.values.getValue("unresolved") as CanonicalJson.JsonArray
        val firstUnresolved = unresolved.values.first() as CanonicalJson.JsonObject
        val reviewedUnresolved = CanonicalJson.JsonArray(
            listOf(CanonicalJson.JsonObject(firstUnresolved.values + mapOf(
                "reason" to CanonicalJson.JsonString("review wording changed"),
                "sourceText" to CanonicalJson.JsonString("source wording changed")
            ))) + unresolved.values.drop(1)
        )
        val reviewedDefinition = CanonicalJson.JsonObject(
            canonicalDefinition.values + ("unresolved" to reviewedUnresolved)
        )
        val gameplayDefinition = CanonicalJson.JsonObject(
            canonicalDefinition.values + ("recommendedLevel" to CanonicalJson.JsonNumber(java.math.BigDecimal(999)))
        )

        val canonicalHash = ContentHasher.definitionRef(canonicalBase).definitionHash
        listOf(canonicalTemplate(renamedDefinition), canonicalTemplate(reviewedDefinition)).forEach { presentationOnly ->
            assertEquals(canonicalHash, ContentHasher.definitionRef(presentationOnly).definitionHash)
            assertNotEquals(
                ContentHasher.logicalContentHash(listOf(canonicalBase)),
                ContentHasher.logicalContentHash(listOf(presentationOnly))
            )
        }
        assertNotEquals(canonicalHash, ContentHasher.definitionRef(canonicalTemplate(gameplayDefinition)).definitionHash)
    }

    @Test
    fun `P1-CT-001 canonical JSON permits quoted empty text while preserving deterministic hash`() {
        val canonical = CanonicalJson.canonicalize("{ \"value\" : \"\" }")
        assertEquals("{\"value\":\"\"}", canonical)
        assertEquals(ContentHasher.sha256(canonical), ContentHasher.sha256(CanonicalJson.canonicalize("{\"value\":\"\"}")))
    }

    @Test
    fun `P1-UT-003 repository offers exactly six stable reads and closes deterministically`() {
        val item = template("item-1", kind = ContentKind.ITEM)
        val npc = template("mon-1")
        val portrait = asset("NPC-1")
        val repository = InMemoryContentRepository(
            installedBundle = InstalledBundle("bundle.v1", "content.v1", "balance.v1"),
            templates = listOf(npc, item),
            aliases = listOf(ContentAlias(ContentId("old-mon"), ContentId("mon-1"), AliasPolicy.REMAP, "renamed")),
            bindings = listOf(AssetBinding(ContentId("mon-1"), ImageUsage.LIST_FACE, portrait.id, 10)),
            assets = listOf(portrait),
            fallbacks = listOf(AssetFallback(ImageUsage.LIST_FACE, FallbackMatcher.GLOBAL_DEFAULT, null, portrait.id, 100))
        )

        assertEquals(npc, repository.findTemplate(ContentId("mon-1")))
        assertEquals(listOf("item-1"), repository.listTemplates(ContentKind.ITEM).map { it.id.value })
        assertEquals(ContentId("mon-1"), repository.findAlias(ContentId("old-mon"))?.newId)
        assertEquals(listOf(portrait.id), repository.findAssetBindings(ContentId("mon-1"), ImageUsage.LIST_FACE).map { it.assetId })
        assertEquals(portrait, repository.findAsset(portrait.id))
        assertEquals(listOf(portrait.id), repository.listAssetFallbacks(ImageUsage.LIST_FACE).map { it.assetId })

        repository.close()
        val closed = runCatching { repository.findTemplate(ContentId("mon-1")) }.exceptionOrNull()
        assertTrue(closed is IncompatibleContent && closed.code == "ContentRepositoryClosed")
        repository.close()
    }

    @Test
    fun `P1-CT-003 asset resolver crop is floor clamped and fallback candidates are finite`() {
        val primary = asset("NPC-BAD", width = 11, height = 4)
        val fallback = asset("NPC-GOOD", width = 11, height = 4)
        val repository = InMemoryContentRepository(
            installedBundle = InstalledBundle("bundle.v1", "content.v1", "balance.v1"),
            templates = listOf(template("mon-1")),
            aliases = emptyList(),
            bindings = listOf(AssetBinding(ContentId("mon-1"), ImageUsage.LIST_FACE, primary.id, 1)),
            assets = listOf(primary, fallback),
            fallbacks = listOf(AssetFallback(ImageUsage.LIST_FACE, FallbackMatcher.GLOBAL_DEFAULT, null, fallback.id, 1))
        )
        val seen = mutableListOf<AssetId>()
        val result = AssetResolver.resolve(
            repository,
            AssetResolveRequest(
                bundleId = "bundle.v1",
                templateId = ContentId("mon-1"),
                exactAssetKeys = listOf(primary.id),
                entityKind = EntityKind.MONSTER,
                usage = ImageUsage.LIST_FACE,
                targetPx = 8
            )
        ) { asset ->
            seen += asset.id
            asset.id != primary.id
        }

        val resolved = result as ResolvedAsset.Fallback
        assertEquals(fallback.id, resolved.asset.id)
        assertEquals(CropRect(left = 3, top = 0, width = 4, height = 4), resolved.crop)
        assertEquals(listOf(primary.id, fallback.id), seen)
        assertEquals(2, seen.distinct().size)
        assertTrue(
            runCatching {
                AssetResolveRequest(
                    bundleId = "bundle.v1",
                    exactAssetKeys = listOf(primary.id, primary.id),
                    entityKind = EntityKind.MONSTER,
                    usage = ImageUsage.LIST_FACE,
                    targetPx = 8
                )
            }.isFailure
        )
    }

    @Test
    fun `P1-CT-003 quality mode clamps target buckets and TEXT skips forbidden usage before repository access`() {
        val portrait = asset("NPC-1")
        val repository = InMemoryContentRepository(
            installedBundle = InstalledBundle("bundle.v1", "content.v1", "balance.v1"),
            templates = emptyList(), aliases = emptyList(), bindings = emptyList(), assets = listOf(portrait), fallbacks = emptyList()
        )
        val low = AssetResolver.resolve(
            repository,
            AssetResolveRequest("bundle.v1", exactAssetKeys = listOf(portrait.id), entityKind = EntityKind.MONSTER, usage = ImageUsage.LIST_FACE, targetPx = 999, qualityMode = QualityMode.LOW)
        ) { true } as ResolvedAsset.Exact
        assertEquals(128, low.effectiveTargetPx)
        val battle = AssetResolver.resolve(
            repository,
            AssetResolveRequest("bundle.v1", exactAssetKeys = listOf(portrait.id), entityKind = EntityKind.MONSTER, usage = ImageUsage.BATTLE_TOKEN, targetPx = 999)
        ) { true } as ResolvedAsset.Exact
        assertEquals(256, battle.effectiveTargetPx)

        val noReadRepository = object : ContentRepository {
            override fun findTemplate(id: ContentId): ContentTemplate? = error("repository must not be read")
            override fun listTemplates(kind: ContentKind): List<ContentTemplate> = error("repository must not be read")
            override fun findAlias(oldId: ContentId): ContentAlias? = error("repository must not be read")
            override fun findAssetBindings(templateId: ContentId, usage: ImageUsage): List<AssetBinding> = error("repository must not be read")
            override fun findAsset(id: AssetId): AssetImage? = error("repository must not be read")
            override fun listAssetFallbacks(usage: ImageUsage): List<AssetFallback> = error("repository must not be read")
            override fun close() = Unit
        }
        val skipped = AssetResolver.resolve(
            noReadRepository,
            AssetResolveRequest("bundle.v1", entityKind = EntityKind.ROOM, usage = ImageUsage.ROOM_BACKGROUND, targetPx = 1, qualityMode = QualityMode.TEXT)
        ) { error("decode must not run") }
        assertEquals(ResolvedAsset.SkippedByQualityMode(QualityMode.TEXT, ImageUsage.ROOM_BACKGROUND), skipped)
    }

    @Test
    fun `P1-IT-005 alias graph and compatibility reject unproven changed same id`() {
        val current = template("mon-1", "{\"hp\":20}")
        val snapshot = ContentSnapshot.create("content.v2", "balance.v1", 1, listOf(current))
        val savedDefinition = ContentDefinitionRef(ContentKind.MONSTER, ContentId("mon-1"), 1, ContentHasher.sha256("old"))
        val saved = ContentCompatibilitySnapshot.create(
            logicalContentHash = ContentHasher.sha256("old-logical"),
            requiredDefinitions = listOf(savedDefinition)
        )

        val unproven = ContentCompatibilityPlanner.plan(saved, snapshot, LegacySnapshotIndex.empty())
        assertTrue(unproven is BindingPlan.Unsupported)
        assertEquals(listOf(ContentId("mon-1")), (unproven as BindingPlan.Unsupported).changedDefinitions)

        val approved = LegacySnapshotIndex.of(
            listOf(
                LegacySnapshotEntry.remap(
                    from = savedDefinition,
                    to = snapshot.definitionRefFor(ContentId("mon-1"))!!,
                    provenance = "P1-approval",
                    approvalRevision = "C24"
                )
            )
        )
        assertTrue(ContentCompatibilityPlanner.plan(saved, snapshot, approved) is BindingPlan.MigrationRequired)

        val tombstonePlan = ContentCompatibilityPlanner.plan(
            saved,
            ContentSnapshot.create("content.v3", "balance.v1", 1, emptyList()),
            LegacySnapshotIndex.of(listOf(LegacySnapshotEntry.tombstone(savedDefinition, "removed-by-C24", "C24")))
        ) as BindingPlan.MigrationRequired
        val tombstone = tombstonePlan.steps.single() as BindingMigrationStep.Tombstone
        assertEquals(savedDefinition, tombstone.from)
        assertEquals("removed-by-C24", tombstone.provenance)

        val cycle = runCatching {
            ContentAliasGraph.flatten(
                listOf(
                    AliasEdge(ContentId("a"), ContentKind.MONSTER, ContentId("b")),
                    AliasEdge(ContentId("b"), ContentKind.MONSTER, ContentId("a"))
                ),
                emptyMap()
            )
        }.exceptionOrNull()
        assertTrue(cycle is ContentAliasGraphException && cycle.code == "ALIAS_CYCLE")

        val kindMismatch = runCatching {
            ContentAliasGraph.flatten(
                listOf(
                    AliasEdge(ContentId("mon-old"), ContentKind.MONSTER, ContentId("item-old")),
                    AliasEdge(ContentId("item-old"), ContentKind.ITEM, null, AliasPolicy.TOMBSTONE)
                ),
                emptyMap()
            )
        }.exceptionOrNull()
        assertTrue(kindMismatch is ContentAliasGraphException && kindMismatch.code == "ALIAS_KIND_MISMATCH")
    }

    private fun template(
        id: String,
        definition: String = "{}",
        kind: ContentKind = ContentKind.MONSTER,
        sourceDisplayName: String = id,
        grade: String? = null,
        minLevel: Int? = null,
        tags: List<String> = emptyList(),
        enabled: Boolean = true
    ): ContentTemplate = ContentTemplate(
        ContentId(id),
        kind,
        sourceDisplayName,
        grade = grade,
        minLevel = minLevel,
        tags = tags,
        definitionVersion = 1,
        definitionJson = definition,
        enabled = enabled
    )

    private fun asset(id: String, width: Int = 8, height: Int = 8): AssetImage = AssetImage(
        id = AssetId(id),
        category = AssetCategory.PORTRAIT,
        relativePath = "portrait/$id.webp",
        width = width,
        height = height,
        byteSize = 1,
        sha256 = ContentHasher.sha256(id)
    )
}
