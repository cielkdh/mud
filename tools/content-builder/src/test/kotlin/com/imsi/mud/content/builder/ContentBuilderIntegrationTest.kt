package com.imsi.mud.content.builder

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.readText
import kotlin.io.path.writeText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.Rule

class ContentBuilderIntegrationTest {
    @get:Rule val phase1Fixture = Phase1FixtureRule()
    @Test
    fun `P1-IT-001 builder publishes fresh sqlite bundle and repeated build is idempotent`() {
        val root = Files.createTempDirectory("content-builder-it")
        val source = root.resolve("source")
        val bootstrap = Path.of("..", "..", "phase1-fixtures", "P1-CT-001", "bootstrap", "catalog_rows.json").toAbsolutePath().normalize()
        CanonicalSourceConverter.convert(bootstrap, source)
        val output = root.resolve("bundles")
        val request = ContentBuildRequest(
            sourceRoot = source,
            outputRoot = output,
            ddlPath = Path.of("..", "..", "docs", "설계부록", "02_제안_content_schema.sql").toAbsolutePath().normalize(),
            contentVersion = "content.test.v1",
            balanceVersion = "balance.test.v1"
        )

        val first = ContentBuilder.build(request)
        val second = ContentBuilder.build(request)

        assertEquals(first.bundleHash, second.bundleHash)
        assertEquals(first.bundleDirectory, second.bundleDirectory)
        assertTrue(Files.isRegularFile(first.bundleDirectory.resolve("content.db")))
        assertTrue(Files.isRegularFile(first.bundleDirectory.resolve("validation-report.json")))
        assertTrue(Files.isRegularFile(first.bundleDirectory.resolve("content-bundle-manifest.json")))
        assertTrue(first.report.readText().contains("PASS"))
        val queryPlan = first.bundleDirectory.resolve("query-plan.json").readText()
        assertFalse(queryPlan.contains("SCAN "))
        assertFalse(queryPlan.contains("USE TEMP B-TREE"))
        val pointer = output.resolve("current.json").readText()
        assertTrue(pointer.contains("bundleId"))
        assertTrue(!pointer.contains("bundleDirectory"))
        assertTrue(!pointer.contains("C:\\"))
    }

    @Test
    fun `P1-IT-001 ALPHA keeps unreachable disabled unresolved rows while FULL rejects all unresolved`() {
        val root = Files.createTempDirectory("content-profile-it")
        val source = root.resolve("source")
        val bootstrap = Path.of("..", "..", "phase1-fixtures", "P1-CT-001", "bootstrap", "catalog_rows.json").toAbsolutePath().normalize()
        CanonicalSourceConverter.convert(bootstrap, source)
        val ddl = Path.of("..", "..", "docs", "설계부록", "02_제안_content_schema.sql").toAbsolutePath().normalize()

        val alpha = ContentBuilder.build(ContentBuildRequest(source, root.resolve("alpha"), ddl, "content.alpha.v1", "balance.test.v1", profile = "ALPHA"))
        assertTrue(alpha.report.readText().contains("PASS"))
        val full = runCatching {
            ContentBuilder.build(ContentBuildRequest(source, root.resolve("full"), ddl, "content.full.v1", "balance.test.v1", profile = "FULL"))
        }.exceptionOrNull()
        assertTrue(full is ContentBuildException)
        assertTrue(full!!.message.orEmpty().contains("UNRESOLVED_CONTENT"))
    }

    @Test
    fun `P1-FT-001 active reference closure rejects reachable unresolved and dangling records`() {
        val root = Files.createTempDirectory("content-closure-it")
        val source = root.resolve("source")
        val fixtureBootstrap = Path.of("..", "..", "phase1-fixtures", "P1-CT-001", "bootstrap", "catalog_rows.json").toAbsolutePath().normalize()
        val fixture = JsonParser.parse(fixtureBootstrap.readText()).asObject()
        val bootstrap = root.resolve("catalog_rows.json")
        val accessory = JsonObject(mapOf(
            "ACC-0001" to JsonObject(mapOf(
                "id" to JsonString("ACC-0001"),
                "section" to JsonNumber(java.math.BigDecimal(1)),
                "row" to JsonString("| ACC-0001 | Test ring | finger | Common | 1 | effect |")
            )
        )))
        bootstrap.writeText(JsonObject(fixture.values + ("ACC" to accessory)).render())
        CanonicalSourceConverter.convert(bootstrap, source)
        val catalogPath = source.resolve("catalog/WPN.json")
        val catalog = JsonParser.parse(catalogPath.readText()).asObject()
        val records = catalog.value("records").asArray().values.map { value ->
            val record = value.asObject()
            if (record.value("id").asString() != "WPN-0001") return@map record
            val definition = record.value("definition").asObject()
            JsonObject(record.values + (
                "enabled" to JsonBoolean(true)
            ) + (
                "definition" to JsonObject(definition.values +
                    ("unresolved" to JsonArray(emptyList()))
                )
            ))
        }
        val rewritten = JsonObject(catalog.values + ("records" to JsonArray(records))).render() + "\n"
        catalogPath.writeText(rewritten)
        rewriteManifestHash(source, "WPN", CanonicalSourceConverter.sha256(rewritten.toByteArray()))
        val ddl = Path.of("..", "..", "docs", "설계부록", "02_제안_content_schema.sql").toAbsolutePath().normalize()
        val error = runCatching {
            ContentBuilder.build(ContentBuildRequest(source, root.resolve("prototype"), ddl, "content.closure.v1", "balance.test.v1", profile = "PROTOTYPE", rules = BuildRuleSet(recipeEdges = listOf(RecipeEdge("WPN-0001", "WPN-0002")))))
        }.exceptionOrNull()
        assertTrue(error is ContentBuildException)
        assertTrue("prototype error: $error", error!!.message.orEmpty().contains("PROFILE_INVALID"))

        val danglingRecords = records.map { value ->
            val record = value.asObject()
            if (record.value("id").asString() != "WPN-0001") return@map record
            record
        }
        val danglingText = JsonObject(catalog.values + ("records" to JsonArray(danglingRecords))).render() + "\n"
        catalogPath.writeText(danglingText)
        rewriteManifestHash(source, "WPN", CanonicalSourceConverter.sha256(danglingText.toByteArray()))
        val dangling = runCatching {
            ContentBuilder.build(ContentBuildRequest(source, root.resolve("dangling"), ddl, "content.dangling.v1", "balance.test.v1", profile = "ALPHA", rules = BuildRuleSet(recipeEdges = listOf(RecipeEdge("WPN-0001", "WPN-MISSING")))))
        }.exceptionOrNull()
        assertTrue(dangling is ContentBuildException)
        assertTrue(dangling!!.message.orEmpty().contains("DANGLING_REFERENCE"))

        val accessoryPath = source.resolve("catalog/ACC.json")
        val accessoryCatalog = JsonParser.parse(accessoryPath.readText()).asObject()
        val accessoryRecords = accessoryCatalog.value("records").asArray().values.mapIndexed { index, value ->
            val record = value.asObject()
            if (index != 0) return@mapIndexed record
            val definition = record.value("definition").asObject()
            val wrongKindEffect = JsonObject(mapOf(
                "amount" to JsonObject(mapOf(
                    "sourceSkill" to JsonString("WPN-0001"),
                    "type" to JsonString("SkillPower"),
                )),
                "damageType" to JsonString("FIRE"),
                "type" to JsonString("ApplyDamage"),
            ))
            JsonObject(record.values + mapOf(
                "enabled" to JsonBoolean(true),
                "definition" to JsonObject(definition.values + mapOf(
                    "effects" to JsonArray(listOf(wrongKindEffect)),
                    "unresolved" to JsonArray(emptyList()),
                )),
            ))
        }
        val accessoryText = JsonObject(accessoryCatalog.values + ("records" to JsonArray(accessoryRecords))).render() + "\n"
        accessoryPath.writeText(accessoryText)
        rewriteManifestHash(source, "ACC", CanonicalSourceConverter.sha256(accessoryText.toByteArray()))
        val wrongKind = runCatching {
            ContentBuilder.build(ContentBuildRequest(source, root.resolve("wrong-kind"), ddl, "content.wrong-kind.v1", "balance.test.v1"))
        }.exceptionOrNull()
        assertTrue(wrongKind is ContentBuildException)
        assertTrue(wrongKind!!.message.orEmpty().contains("requires SKL, got WPN"))
    }

    private fun rewriteManifestHash(source: Path, kind: String, hash: String) {
        val path = source.resolve("catalog-manifest.json")
        val manifest = JsonParser.parse(path.readText()).asObject()
        val files = manifest.value("files").asArray().values.map { value ->
            val entry = value.asObject()
            if (entry.value("kind").asString() == kind) JsonObject(entry.values + ("exactFileSha256" to JsonString(hash))) else entry
        }
        path.writeText(JsonObject(manifest.values + ("files" to JsonArray(files))).render() + "\n")
    }
}
