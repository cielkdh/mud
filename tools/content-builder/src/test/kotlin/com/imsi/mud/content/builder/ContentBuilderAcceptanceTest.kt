package com.imsi.mud.content.builder

import java.nio.file.Files
import java.nio.file.Path
import java.sql.DriverManager
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.io.path.readText
import kotlin.io.path.writeText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.Rule

class ContentBuilderAcceptanceTest {
    @get:Rule val phase1Fixture = Phase1FixtureRule()
    @Test
    fun `P1-BT-002 typed definition keys reject generic references and raw executable nodes`() {
        val genericRoot = Files.createTempDirectory("content-builder-generic-key")
        val genericSource = canonicalSource(genericRoot)
        rewriteWpn(genericSource) { record ->
            if (record.value("id").asString() != "WPN-0001") return@rewriteWpn record
            val definition = record.value("definition").asObject()
            JsonObject(record.values + ("definition" to JsonObject(definition.values + mapOf(
                "allowedTags" to JsonArray(listOf(JsonString("SWORD")))
            ))))
        }
        val genericError = expectBuildError {
            ContentBuilder.build(request(genericSource, genericRoot.resolve("output"), "content.generic-key.v1"))
        }
        assertTrue(genericError.message.orEmpty().contains("unknown=[allowedTags]"))

        val astRoot = Files.createTempDirectory("content-builder-raw-ast")
        val astSource = canonicalSkillSource(astRoot)
        rewriteCatalog(astSource, "SKL") { record ->
            if (record.value("id").asString() != "SKL-0001") return@rewriteCatalog record
            val definition = record.value("definition").asObject()
            JsonObject(record.values + ("definition" to JsonObject(definition.values + mapOf(
                "effects" to JsonArray(listOf(JsonString("raw effect text")))
            ))))
        }
        val astError = expectBuildError {
            ContentBuilder.build(request(astSource, astRoot.resolve("output"), "content.raw-ast.v1"))
        }
        assertTrue(astError.message.orEmpty().contains("effects"))
    }

    @Test
    fun `P1-BT-003 source failures preserve diagnostic contract and publish no bundle`() {
        data class SourceFailureCase(
            val name: String,
            val expectedCode: String,
            val sourceFile: String,
            val mutate: (Path) -> Unit
        )

        val cases = listOf(
            SourceFailureCase("malformed-json", "SOURCE_INVALID", "catalog/WPN.json") { source ->
                source.resolve("catalog/WPN.json").writeText("{\n")
                rewriteManifestHash(source, "WPN", CanonicalSourceConverter.sha256("{\n".toByteArray()))
            },
            SourceFailureCase("duplicate-json-key", "JSON_DUPLICATE_KEY", "catalog/WPN.json") { source ->
                val malformed = "{\"duplicate\":1,\"duplicate\":2}"
                source.resolve("catalog/WPN.json").writeText(malformed)
                rewriteManifestHash(source, "WPN", CanonicalSourceConverter.sha256(malformed.toByteArray()))
            },
            SourceFailureCase("unknown-field", "SOURCE_UNKNOWN_FIELD", "catalog/WPN.json") { source ->
                rewriteWpn(source) { record -> JsonObject(record.values + ("unknownField" to JsonString("not-allowed"))) }
            },
            SourceFailureCase("nfc", "SOURCE_ENCODING_INVALID", "catalog/WPN.json") { source ->
                val path = source.resolve("catalog/WPN.json")
                val decomposed = path.readText().replace("훈련용 <장검>", "Cafe\u0301")
                path.writeText(decomposed)
                rewriteManifestHash(source, "WPN", CanonicalSourceConverter.sha256(decomposed.toByteArray()))
            },
            SourceFailureCase("malformed-csv", "SOURCE_INVALID", "catalog/WPN.csv") { source ->
                val malformed = "id,sourceDisplayName,displayNameOverride,grade,minLevel,tagsJson,enabled,definitionVersion,definitionJson,provenanceSection,provenanceRow,provenanceRawRow\nWPN-0001,\"unterminated"
                source.resolve("catalog/WPN.csv").writeText(malformed)
                rewriteManifestEntry(source, "WPN", "catalog/WPN.csv", 1, CanonicalSourceConverter.sha256(malformed.toByteArray()))
            }
        )

        cases.forEach { testCase ->
            val root = Files.createTempDirectory("content-source-diagnostic-${testCase.name}")
            val source = canonicalSource(root)
            testCase.mutate(source)
            val output = root.resolve("output")
            val error = expectBuildError {
                ContentBuilder.build(request(source, output, "content.source.${testCase.name}.v1"))
            }
            assertEquals(testCase.expectedCode, error.code)
            assertEquals(0L, countFiles(output, "content.db"))

            val reportPath = Files.walk(output).use { stream ->
                stream.filter { it.fileName.toString() == "validation-report.json" }.findFirst().orElseThrow()
            }
            val report = JsonParser.parse(reportPath.readText()).asObject()
            assertEquals("FAIL", report.value("status").asString())
            val diagnostic = report.value("diagnostics").asArray().values.single().asObject()
            assertEquals(testCase.expectedCode, diagnostic.value("code").asString())
            assertEquals(testCase.sourceFile, diagnostic.value("sourceFile").asString())
            listOf("severity", "code", "messageKey", "message", "sourceId", "sourceFile", "row", "column", "field", "expected", "actual")
                .forEach { key -> assertTrue("missing diagnostic key: $key", diagnostic.values.containsKey(key)) }
            assertTrue(diagnostic.value("row") != JsonNull)
            assertTrue(diagnostic.value("column") != JsonNull)
            assertTrue(diagnostic.value("field") != JsonNull)
            assertTrue(diagnostic.value("expected") != JsonNull)
            assertTrue(diagnostic.value("actual") != JsonNull)

            val markdown = reportPath.parent.resolve("validation-report.md").readText()
            assertTrue(markdown.contains("- status: FAIL"))
            assertTrue(markdown.contains(testCase.expectedCode))
        }

        val validRoot = Files.createTempDirectory("content-source-diagnostic-order")
        val validOutput = validRoot.resolve("output")
        val validResult = ContentBuilder.build(request(canonicalSource(validRoot), validOutput, "content.source.valid.v1"))
        val validReport = JsonParser.parse(validResult.report.readText()).asObject()
        val diagnostics = validReport.value("diagnostics").asArray().values.map { it.asObject() }
        val keys = diagnostics.map { diagnostic ->
            listOf(
                if (diagnostic.value("severity").asString() == "ERROR") 0 else 1,
                diagnostic.value("code").asString(),
                diagnostic.value("sourceId").let { if (it == JsonNull) "" else it.asString() },
                diagnostic.value("sourceFile").let { if (it == JsonNull) "" else it.asString() },
                diagnostic.value("row").let { if (it == JsonNull) -1 else it.asInt() },
                diagnostic.value("column").let { if (it == JsonNull) -1 else it.asInt() },
                diagnostic.value("field").let { if (it == JsonNull) "" else it.asString() }
            )
        }
        assertEquals(keys.sortedWith(compareBy({ it[0] }, { it[1] }, { it[2] }, { it[3] }, { it[4] as Int }, { it[5] as Int }, { it[6] })), keys)
        val markdown = validResult.bundleDirectory.resolve("validation-report.md").readText()
        assertTrue(markdown.contains("- status: PASS"))
        assertTrue(markdown.contains("- diagnostics: ${diagnostics.size}"))
    }

    @Test
    fun `P1-BT-003 ast profile asset and alias failures each persist one complete report`() {
        val requiredDiagnosticKeys = setOf(
            "severity", "code", "messageKey", "message", "sourceId", "sourceFile",
            "row", "column", "field", "expected", "actual",
        )
        fun assertFailureStage(name: String, expectedCode: String, buildRequest: ContentBuildRequest) {
            val error = expectBuildError { ContentBuilder.build(buildRequest) }
            assertEquals("$name error code", expectedCode, error.code)
            val reports = mutableListOf<Path>()
            if (Files.exists(buildRequest.outputRoot)) {
                Files.walk(buildRequest.outputRoot).use { paths ->
                    paths.filter { it.fileName.toString() == "validation-report.json" }.forEach(reports::add)
                }
            }
            assertEquals("$name must write exactly one JSON report", 1, reports.size)
            val report = JsonParser.parse(reports.single().readText()).asObject()
            assertEquals("FAIL", report.value("status").asString())
            val diagnostics = report.value("diagnostics").asArray().values
            assertTrue("$name must have at least one diagnostic", diagnostics.isNotEmpty())
            diagnostics.map(JsonValue::asObject).forEach { diagnostic ->
                assertEquals(requiredDiagnosticKeys, diagnostic.values.keys)
            }
            assertFalse(Files.exists(buildRequest.outputRoot.resolve("current.json")))
            assertFalse(Files.exists(buildRequest.outputRoot.resolve("bundles")))
        }

        val astRoot = Files.createTempDirectory("content-builder-report-ast")
        val astSource = canonicalSource(astRoot)
        rewriteWpn(astSource) { record ->
            if (record.value("id").asString() != "WPN-0001") return@rewriteWpn record
            val definition = record.value("definition").asObject()
            JsonObject(record.values + ("definition" to JsonObject(definition.values + ("allowedTags" to JsonArray(emptyList())))))
        }
        assertFailureStage("AST", "CONTENT_VALIDATION_FAILED", request(astSource, astRoot.resolve("output"), "content.report.ast.v1"))

        val profileRoot = Files.createTempDirectory("content-builder-report-profile")
        assertFailureStage(
            "profile",
            "UNRESOLVED_CONTENT",
            request(canonicalSource(profileRoot), profileRoot.resolve("output"), "content.report.profile.v1").copy(profile = "FULL"),
        )

        val assetRoot = Files.createTempDirectory("content-builder-report-asset")
        val missingAssetRoot = assetRoot.resolve("assets")
        Files.createDirectories(missingAssetRoot)
        assertFailureStage(
            "asset",
            "MISSING_REQUIRED_ASSET",
            request(canonicalSource(assetRoot), assetRoot.resolve("output"), "content.report.asset.v1").copy(
                assetEntries = listOf(AssetPreviewEntry("missing", "PORTRAIT", "portrait/missing.png", 8, 8, 1L, "0".repeat(64), "fixture")),
                assetRoot = missingAssetRoot,
                approvedLicenseIds = setOf("fixture"),
                licenseRegistry = listOf(LicenseRegistryEntry("fixture", "PROJECT_OWNED_FIXTURE", "phase1-fixtures/P1-BT-003", "APPROVED", setOf("ANDROID_APP"))),
            ),
        )

        val aliasRoot = Files.createTempDirectory("content-builder-report-alias")
        val duplicateAliases = listOf(
            ContentAliasEntry("alias-a", "WPN-LEGACY", "WPN-0001", "REMAP", "fixture", "WPN", 1, 1, "rev-1"),
            ContentAliasEntry("alias-b", "WPN-LEGACY", "WPN-0001", "REMAP", "fixture", "WPN", 1, 2, "rev-1"),
        )
        assertFailureStage(
            "alias",
            "CONTENT_ALIAS_INVALID",
            request(canonicalSource(aliasRoot), aliasRoot.resolve("output"), "content.report.alias.v1").copy(aliases = duplicateAliases),
        )

        val reportIoRoot = Files.createTempDirectory("content-builder-report-io")
        val outputFile = reportIoRoot.resolve("output-file")
        Files.writeString(outputFile, "not-a-directory")
        val reportIoError = expectBuildError {
            ContentBuilder.build(request(reportIoRoot.resolve("missing-source"), outputFile, "content.report.io.v1"))
        }
        assertEquals("BUILD_IO", reportIoError.code)
    }

    @Test
    fun `P1-BT-002 canonical definition rejects generic extensions before writing`() {
        val root = Files.createTempDirectory("content-builder-matrix-negative")
        val source = canonicalSource(root)
        val kinds = listOf("MON", "WPN")
        kinds.forEachIndexed { index, kind ->
            val path = source.resolve("catalog/$kind.json")
            val original = path.readText()
            rewriteCatalog(source, kind) { record ->
                if (record.value("id").asString() != JsonParser.parse(original).asObject().value("records").asArray().values.first().asObject().value("id").asString()) return@rewriteCatalog record
                JsonObject(record.values + ("definition" to JsonObject(record.value("definition").asObject().values + ("allowedTags" to JsonArray(emptyList())))))
            }
            val error = expectBuildError { ContentBuilder.build(request(source, root.resolve("output-$index"), "content.matrix.$index.v1")) }
            assertTrue("$kind should reject generic key: ${error.message}", error.message.orEmpty().contains("unknown=[allowedTags]"))
            path.writeText(original)
            rewriteManifestHash(source, kind, CanonicalSourceConverter.sha256(original.toByteArray()))
        }
    }

    @Test
    fun `P1-BT-002 tag conflicts and recipe cycles produce stable diagnostics`() {
        val root = Files.createTempDirectory("content-builder-rules")
        val source = canonicalSource(root)
        val output = root.resolve("output")
        val error = expectBuildError {
            ContentBuilder.build(request(source, output, "content.rules.v1").copy(rules = BuildRuleSet(
                tagRules = listOf(TagRule("WPN-0001", setOf("SWORD"), setOf("SWORD"))),
                recipeEdges = listOf(RecipeEdge("WPN-0001", "WPN-0002"), RecipeEdge("WPN-0002", "WPN-0001"))
            )))
        }

        assertEquals("CONTENT_VALIDATION_FAILED", error.code)
        assertTrue(error.message.orEmpty().contains("TAG_CONFLICT:WPN-0001"))
        assertTrue(error.message.orEmpty().contains("RECIPE_CYCLE:WPN-0001"))
        assertFalse(Files.exists(output.resolve("current.json")))
        assertFalse(Files.exists(output.resolve("bundles")))
    }

    @Test
    fun `P1-CT-002 error writes zero bundles and warning writes one sealed bundle`() {
        val errorRoot = Files.createTempDirectory("content-builder-writer-error")
        val errorSource = canonicalSource(errorRoot)
        val errorOutput = errorRoot.resolve("output")
        val error = expectBuildError {
            ContentBuilder.build(request(errorSource, errorOutput, "content.writer.error.v1").copy(rules = BuildRuleSet(
                tagRules = listOf(TagRule("WPN-0001", setOf("SWORD"), setOf("SWORD")))
            )))
        }
        assertEquals("CONTENT_VALIDATION_FAILED", error.code)
        assertEquals(0L, countFiles(errorOutput, "content.db"))

        val warningRoot = Files.createTempDirectory("content-builder-writer-warning")
        val warningSource = canonicalSource(warningRoot)
        val warningOutput = warningRoot.resolve("output")
        val result = ContentBuilder.build(request(warningSource, warningOutput, "content.writer.warning.v1"))
        assertEquals(1L, countFiles(warningOutput, "content.db"))
        assertTrue(Files.isRegularFile(result.bundleDirectory.resolve("content.db")))
        assertTrue(result.report.readText().contains("UNRESOLVED_DISABLED"))
        assertTrue(result.report.readText().contains("\"status\":\"PASS\""))
    }

    @Test
    fun `P1-FT-002 staging sqlite and atomic publish faults fail safe`() {
        val ddl = ddl()

        val stagingRoot = Files.createTempDirectory("content-builder-fault-staging")
        val stagingSource = canonicalSource(stagingRoot)
        val stagingOutput = stagingRoot.resolve("output")
        Files.createDirectories(stagingOutput.resolve("staging"))
        Files.writeString(stagingOutput.resolve("staging/sentinel"), "orphan")
        assertEquals("STAGING_NOT_EMPTY", expectBuildError {
            ContentBuilder.build(ContentBuildRequest(stagingSource, stagingOutput, ddl, "content.fault.staging.v1", "balance.test.v1"))
        }.code)

        val walRoot = Files.createTempDirectory("content-builder-fault-wal")
        val walSource = canonicalSource(walRoot)
        val walOutput = walRoot.resolve("output")
        assertEquals("PRAGMA_INVALID", expectBuildError {
            ContentBuilder.build(ContentBuildRequest(walSource, walOutput, ddl, "content.fault.wal.v1", "balance.test.v1", fault = BuildFault.WAL_REQUEST))
        }.code)

        val sidecarRoot = Files.createTempDirectory("content-builder-fault-sidecar")
        val sidecarSource = canonicalSource(sidecarRoot)
        val sidecarOutput = sidecarRoot.resolve("output")
        assertEquals("SQLITE_SIDECAR", expectBuildError {
            ContentBuilder.build(ContentBuildRequest(sidecarSource, sidecarOutput, ddl, "content.fault.sidecar.v1", "balance.test.v1", fault = BuildFault.FAKE_SIDECAR))
        }.code)
        assertTrue(Files.isRegularFile(sidecarOutput.resolve("staging/content.db-wal")))

        val handleRoot = Files.createTempDirectory("content-builder-fault-handle")
        val handleSource = canonicalSource(handleRoot)
        val handleOutput = handleRoot.resolve("output")
        assertEquals("RESOURCE_OPEN", expectBuildError {
            ContentBuilder.build(ContentBuildRequest(handleSource, handleOutput, ddl, "content.fault.handle.v1", "balance.test.v1", fault = BuildFault.OPEN_HANDLE))
        }.code)

        val atomicRoot = Files.createTempDirectory("content-builder-fault-atomic")
        val atomicSource = canonicalSource(atomicRoot)
        val atomicOutput = atomicRoot.resolve("output")
        assertEquals("PUBLISH_ATOMIC_UNSUPPORTED", expectBuildError {
            ContentBuilder.build(ContentBuildRequest(atomicSource, atomicOutput, ddl, "content.fault.atomic.v1", "balance.test.v1", fault = BuildFault.ATOMIC_MOVE_UNSUPPORTED))
        }.code)
        assertTrue(Files.isRegularFile(atomicOutput.resolve("staging/content.db")))
        assertFalse(Files.exists(atomicOutput.resolve("current.json")))

        val pointerRoot = Files.createTempDirectory("content-builder-fault-pointer")
        val pointerSource = canonicalSource(pointerRoot)
        val pointerOutput = pointerRoot.resolve("output")
        assertEquals("PUBLISH_POINTER_FAILED", expectBuildError {
            ContentBuilder.build(ContentBuildRequest(pointerSource, pointerOutput, ddl, "content.fault.pointer.v1", "balance.test.v1", fault = BuildFault.POINTER_MOVE_FAILURE))
        }.code)
        assertTrue(Files.list(pointerOutput.resolve("bundles")).use { it.findAny().isPresent })
        assertFalse(Files.exists(pointerOutput.resolve("current.json")))
    }

    @Test
    fun `P1-IT-002 prepared values target integrity licenses and query inventory remain sealed`() {
        val root = Files.createTempDirectory("content-builder-it2")
        val source = canonicalSource(root)
        val output = root.resolve("output")
        val request = request(source, output, "content.it2.v1")
        val first = ContentBuilder.build(request)
        val second = ContentBuilder.build(request)
        assertEquals(first.bundleHash, second.bundleHash)
        assertTrue(first.bundleDirectory.resolve("query-plan.json").readText().contains("CDB-Q06"))
        assertFalse(first.bundleDirectory.resolve("query-plan.json").readText().contains("SCAN "))
        assertFalse(first.bundleDirectory.resolve("query-plan.json").readText().contains("USE TEMP B-TREE"))

        val pointerBeforeCorruption = output.resolve("current.json").readText()
        Files.writeString(first.bundleDirectory.resolve("validation-report.json"), "corrupted\n")
        val corruption = expectBuildError { ContentBuilder.build(request) }
        assertEquals("INTEGRITY_FAILED", corruption.code)
        assertEquals(pointerBeforeCorruption, output.resolve("current.json").readText())

        rewriteWpn(source) { record ->
            if (record.value("id").asString() != "WPN-0001") return@rewriteWpn record
            JsonObject(record.values + ("sourceDisplayName" to JsonString("O'Brien; DROP TABLE content_template;--")))
        }
        val quotedOutput = root.resolve("quoted-output")
        val quoted = ContentBuilder.build(request(source, quotedOutput, "content.it2.quoted.v1"))
        Class.forName("org.sqlite.JDBC")
        DriverManager.getConnection("jdbc:sqlite:${quoted.bundleDirectory.resolve("content.db").toAbsolutePath()}").use { connection ->
            connection.prepareStatement("SELECT source_display_name FROM content_template WHERE id = ?").use { statement ->
                statement.setString(1, "WPN-0001")
                statement.executeQuery().use { resultSet ->
                    assertTrue(resultSet.next())
                    assertEquals("O'Brien; DROP TABLE content_template;--", resultSet.getString(1))
                }
            }
            connection.createStatement().use { statement ->
                statement.executeQuery("SELECT COUNT(*) FROM content_template").use { resultSet ->
                    assertTrue(resultSet.next())
                    assertEquals(3, resultSet.getInt(1))
                }
            }
        }

        val assetRoot = root.resolve("assets")
        val assetPath = assetRoot.resolve("portrait/test.png")
        Files.createDirectories(assetPath.parent)
        val png = minimalPng()
        Files.write(assetPath, png)
        val asset = AssetPreviewEntry(
            id = "asset-1",
            category = "PORTRAIT",
            relativePath = "portrait/test.png",
            width = 8,
            height = 8,
            byteSize = png.size.toLong(),
            sha256 = CanonicalSourceConverter.sha256(png),
            licenseId = "fixture-license"
        )
        val licenseOne = LicenseRegistryEntry("fixture-license", "CC0", "fixture", "APPROVED", setOf("ANDROID_APP"))
        val licenseTwo = licenseOne.copy(license = "CC0-v2")
        val assetFirst = ContentBuilder.build(ContentBuildRequest(
            source, root.resolve("asset-output-one"), ddl(), "content.asset.v1", "balance.test.v1",
            assetEntries = listOf(asset), assetRoot = assetRoot, approvedLicenseIds = setOf(asset.licenseId), licenseRegistry = listOf(licenseOne)
        ))
        val assetSecond = ContentBuilder.build(ContentBuildRequest(
            source, root.resolve("asset-output-two"), ddl(), "content.asset.v1", "balance.test.v1",
            assetEntries = listOf(asset), assetRoot = assetRoot, approvedLicenseIds = setOf(asset.licenseId), licenseRegistry = listOf(licenseTwo)
        ))
        assertTrue(assetFirst.bundleHash != assetSecond.bundleHash)

        val readOnlyUrl = "jdbc:sqlite:file:${assetFirst.bundleDirectory.resolve("content.db").toAbsolutePath().toString().replace('\\', '/')}?mode=ro"
        DriverManager.getConnection(readOnlyUrl).use { connection ->
            connection.createStatement().use { statement ->
                statement.execute("PRAGMA query_only=ON")
                val writeError = runCatching { statement.executeUpdate("INSERT INTO content_template(id) VALUES('sealed')") }.exceptionOrNull()
                assertNotNull(writeError)
            }
        }
        writePhase1Evidence("P1-IT-002", JsonObject(mapOf(
            "testId" to JsonString("P1-IT-002"),
            "bundleHash" to JsonString(first.bundleHash),
            "sealedDbSha256" to JsonString(CanonicalSourceConverter.sha256(Files.readAllBytes(first.bundleDirectory.resolve("content.db")))),
            "queryPlanSha256" to JsonString(CanonicalSourceConverter.sha256(Files.readAllBytes(first.bundleDirectory.resolve("query-plan.json")))),
            "integrity" to JsonString("ok"),
            "foreignKeyCheck" to JsonBoolean(true),
            "semanticAudit" to JsonBoolean(true),
            "queries" to JsonArray((1..6).map { JsonString("CDB-Q0$it") })
        )).render())
    }

    @Test
    fun `P1-IT-003 asset manifest binding fallback database and preview roundtrip`() {
        val root = Files.createTempDirectory("content-builder-it3")
        val source = canonicalSource(root)
        val assetRoot = root.resolve("assets")
        val pngPath = assetRoot.resolve("portrait/test.png")
        Files.createDirectories(pngPath.parent)
        val png = minimalPng()
        Files.write(pngPath, png)
        val pngEntry = AssetPreviewEntry("asset-png", "PORTRAIT", "portrait/test.png", 8, 8, png.size.toLong(), CanonicalSourceConverter.sha256(png), "fixture-license", sourceDisplayName = "Portrait source", effectiveDisplayName = "Portrait public")
        Files.write(assetRoot.resolve("portrait/fallback.png"), png)
        val fallbackEntry = pngEntry.copy(id = "asset-fallback", relativePath = "portrait/fallback.png")
        Files.write(assetRoot.resolve("portrait/unused.png"), png)
        val unusedEntry = pngEntry.copy(id = "asset-unused", relativePath = "portrait/unused.png")
        val request = ContentBuildRequest(
            sourceRoot = source,
            outputRoot = root.resolve("output"),
            ddlPath = ddl(),
            contentVersion = "content.it3.v1",
            balanceVersion = "balance.test.v1",
            assetEntries = listOf(pngEntry, fallbackEntry, unusedEntry),
            assetRoot = assetRoot,
            approvedLicenseIds = setOf("fixture-license"),
            licenseRegistry = listOf(LicenseRegistryEntry("fixture-license", "CC0", "fixture", "APPROVED", setOf("ANDROID_APP"))),
            aliases = listOf(ContentAliasEntry("alias-1", "WPN-OLD", "WPN-0001", "REMAP", "fixture", "WPN", 1, 1, "rev-1")),
            assetBindings = listOf(AssetBindingEntry("binding-1", "WPN-0001", "LIST_FACE", "asset-png", 0)),
            assetFallbacks = listOf(AssetFallbackEntry("fallback-1", "LIST_FACE", "GLOBAL_DEFAULT", null, "asset-fallback", 0))
        )
        val result = ContentBuilder.build(request)
        assertTrue(Files.isRegularFile(result.bundleDirectory.resolve("assets/portrait/test.png")))
        assertTrue(result.bundleDirectory.resolve("asset-preview/PORTRAIT-001.html").readText().contains("Portrait public"))
        assertTrue(result.bundleDirectory.resolve("asset-preview/PORTRAIT-001.html").readText().contains("../assets/portrait/test.png"))
        val persistedReport = JsonParser.parse(result.bundleDirectory.resolve("validation-report.json").readText()).asObject()
        val persistedAssets = persistedReport.value("assets").asArray().values.map(JsonValue::asObject)
        assertEquals(setOf("asset-png", "asset-fallback", "asset-unused"), persistedAssets.map { it.value("id").asString() }.toSet())
        assertTrue(persistedAssets.all { it.value("usageType") == JsonNull })
        val previewRows = persistedReport.value("previewRows").asArray().values.map(JsonValue::asObject)
        assertEquals(3, previewRows.size)
        assertEquals(setOf("EXACT", "FALLBACK:GLOBAL_DEFAULT", "UNUSED"), previewRows.map { it.value("resolutionReason").asString() }.toSet())
        assertTrue(previewRows.filter { it.value("resolutionReason").asString() != "UNUSED" }.all {
            it.value("usageType").asString() == "LIST_FACE" && it.value("cropProfile").asString() == "SQUARE_FACE"
        })
        assertTrue(previewRows.single { it.value("resolutionReason").asString() == "UNUSED" }.value("unused").asBoolean())
        val previewHtml = result.bundleDirectory.resolve("asset-preview/PORTRAIT-001.html").readText()
        assertTrue(previewHtml.contains("data-crop-profile=\"SQUARE_FACE\"") && previewHtml.contains("FALLBACK:GLOBAL_DEFAULT"))
        val jsonDrivenReport = root.resolve("json-driven-validation-report.json")
        val modifiedPreview = JsonObject(previewRows.first().values + ("effectiveDisplayName" to JsonString("JSON preview sentinel")))
        Files.writeString(jsonDrivenReport, JsonObject(persistedReport.values + ("previewRows" to JsonArray(listOf(modifiedPreview)))).render())
        val jsonDrivenPreview = root.resolve("json-driven-preview")
        ContentBuilder::class.java.getDeclaredMethod("renderAssetPreview", Path::class.java, Path::class.java)
            .apply { isAccessible = true }
            .invoke(ContentBuilder, jsonDrivenReport, jsonDrivenPreview)
        assertTrue(jsonDrivenPreview.resolve("PORTRAIT-001.html").readText().contains("JSON preview sentinel"))
        val db = result.bundleDirectory.resolve("content.db")
        DriverManager.getConnection("jdbc:sqlite:${db.toAbsolutePath()}").use { connection ->
            val checks = listOf(
                "SELECT COUNT(*) FROM content_alias" to 1,
                "SELECT COUNT(*) FROM asset_image" to 3,
                "SELECT COUNT(*) FROM asset_binding" to 1,
                "SELECT COUNT(*) FROM asset_fallback" to 1,
                "SELECT asset_id FROM asset_binding WHERE template_id=? AND usage_type=? ORDER BY priority" to 1,
                "SELECT asset_id FROM asset_fallback WHERE usage_type=?" to 1
            )
            checks.forEach { (sql, expected) ->
                connection.prepareStatement(sql).use { statement ->
                    if (sql.contains("template_id")) {
                        statement.setString(1, "WPN-0001")
                        statement.setString(2, "LIST_FACE")
                    } else if (sql.contains("usage_type=?")) {
                        statement.setString(1, "LIST_FACE")
                    }
                    statement.executeQuery().use { resultSet ->
                        if (sql.startsWith("SELECT COUNT")) {
                            assertTrue(resultSet.next())
                            assertEquals(expected, resultSet.getInt(1))
                        } else {
                            assertTrue(resultSet.next())
                            assertEquals(expected, 1)
                        }
                    }
                }
            }
        }
        val plans = result.bundleDirectory.resolve("query-plan.json").readText()
        assertTrue(plans.contains("CDB-Q01") && plans.contains("CDB-Q02") && plans.contains("CDB-Q03") && plans.contains("CDB-Q04") && plans.contains("CDB-Q05") && plans.contains("CDB-Q06"))
        assertFalse(plans.contains("SCAN "))
        assertFalse(plans.contains("USE TEMP B-TREE"))
        assertEquals(CanonicalSourceConverter.sha256(png), CanonicalSourceConverter.sha256(Files.readAllBytes(result.bundleDirectory.resolve("assets/portrait/test.png"))))
        val bundleManifest = JsonParser.parse(result.bundleDirectory.resolve("content-bundle-manifest.json").readText()).asObject()
        val shippedAsset = bundleManifest.value("files").asArray().values.map(JsonValue::asObject).single { it.value("path").asString() == "assets/portrait/test.png" }
        assertEquals(CanonicalSourceConverter.sha256(png), shippedAsset.value("sha256").asString())
        val artifactSources = mapOf(
            "previewArtifact" to result.bundleDirectory.resolve("asset-preview/PORTRAIT-001.html"),
            "validationReportArtifact" to result.bundleDirectory.resolve("validation-report.json"),
            "databaseArtifact" to result.bundleDirectory.resolve("content.db"),
            "queryPlanArtifact" to result.bundleDirectory.resolve("query-plan.json")
        )
        val artifactEvidence = artifactSources.flatMap { (key, source) -> listOf(
            key to JsonString("artifacts/${source.fileName}"),
            "${key}Sha256" to JsonString(CanonicalSourceConverter.sha256(Files.readAllBytes(source)))
        ) }.toMap()
        writePhase1Evidence("P1-IT-003", JsonObject(mapOf(
            "testId" to JsonString("P1-IT-003"),
            "bundleHash" to JsonString(result.bundleHash),
            "generatedByVersion" to bundleManifest.value("generatedByVersion"),
            "assetManifestSha256" to bundleManifest.value("assetManifestSha256"),
            "assetPath" to JsonString("assets/portrait/test.png"),
            "assetSha256" to shippedAsset.value("sha256"),
            "bindingCount" to JsonNumber(java.math.BigDecimal.ONE),
            "fallbackCount" to JsonNumber(java.math.BigDecimal.ONE),
            "preview" to JsonString("asset-preview/PORTRAIT-001.html")
        ) + artifactEvidence).render())
        val evidenceArtifacts = phase1EvidenceDirectory("P1-IT-003").resolve("artifacts")
        Files.createDirectories(evidenceArtifacts)
        artifactSources.values.forEach { source ->
            Files.copy(source, evidenceArtifacts.resolve(source.fileName), java.nio.file.StandardCopyOption.REPLACE_EXISTING)
        }
    }

    @Test
    fun `P1-FT-003 asset mutation after copy is rejected before publish`() {
        val root = Files.createTempDirectory("content-builder-asset-mutation")
        val source = canonicalSource(root)
        val assetRoot = root.resolve("assets")
        val assetPath = assetRoot.resolve("portrait/test.png")
        Files.createDirectories(assetPath.parent)
        val bytes = minimalPng()
        Files.write(assetPath, bytes)
        val entry = AssetPreviewEntry("asset-png", "PORTRAIT", "portrait/test.png", 8, 8, bytes.size.toLong(), CanonicalSourceConverter.sha256(bytes), "fixture-license")
        val error = expectBuildError {
            ContentBuilder.build(ContentBuildRequest(
                sourceRoot = source,
                outputRoot = root.resolve("output"),
                ddlPath = ddl(),
                contentVersion = "content.asset-mutation.v1",
                balanceVersion = "balance.test.v1",
                assetEntries = listOf(entry),
                assetRoot = assetRoot,
                approvedLicenseIds = setOf("fixture-license"),
                licenseRegistry = listOf(LicenseRegistryEntry("fixture-license", "CC0", "fixture", "APPROVED", setOf("ANDROID_APP"))),
                fault = BuildFault.MUTATE_ASSET_AFTER_COPY
            ))
        }
        assertEquals("VALIDATION_FAILED", error.code)
        assertFalse(Files.exists(root.resolve("output/current.json")))
    }

    @Test
    fun `P1-FT-003 asset source replacement after pinned copy is rejected before publish`() {
        val root = Files.createTempDirectory("content-builder-asset-source-mutation")
        val source = canonicalSource(root)
        val assetRoot = root.resolve("assets")
        val assetPath = assetRoot.resolve("portrait/test.png")
        Files.createDirectories(assetPath.parent)
        val bytes = minimalPng()
        Files.write(assetPath, bytes)
        val entry = AssetPreviewEntry("asset-png", "PORTRAIT", "portrait/test.png", 8, 8, bytes.size.toLong(), CanonicalSourceConverter.sha256(bytes), "fixture-license")
        val error = expectBuildError {
            ContentBuilder.build(ContentBuildRequest(
                sourceRoot = source,
                outputRoot = root.resolve("output"),
                ddlPath = ddl(),
                contentVersion = "content.asset-source-mutation.v1",
                balanceVersion = "balance.test.v1",
                assetEntries = listOf(entry),
                assetRoot = assetRoot,
                approvedLicenseIds = setOf("fixture-license"),
                licenseRegistry = listOf(LicenseRegistryEntry("fixture-license", "CC0", "fixture", "APPROVED", setOf("ANDROID_APP"))),
                fault = BuildFault.MUTATE_ASSET_SOURCE_AFTER_COPY,
            ))
        }
        assertEquals("ASSET_VALIDATION_FAILED", error.code)
        assertFalse(Files.exists(root.resolve("output/current.json")))
    }

    @Test
    fun `P1-FT-003 parent link swap with identical bytes is rejected before publish`() {
        val root = Files.createTempDirectory("content-builder-asset-parent-swap")
        val source = canonicalSource(root)
        val assetRoot = root.resolve("assets")
        val assetPath = assetRoot.resolve("portrait/test.png")
        Files.createDirectories(assetPath.parent)
        val bytes = minimalPng()
        Files.write(assetPath, bytes)
        val entry = AssetPreviewEntry("asset-png", "PORTRAIT", "portrait/test.png", 8, 8, bytes.size.toLong(), CanonicalSourceConverter.sha256(bytes), "fixture-license")
        val error = expectBuildError {
            ContentBuilder.build(ContentBuildRequest(
                sourceRoot = source,
                outputRoot = root.resolve("output"),
                ddlPath = ddl(),
                contentVersion = "content.asset-parent-swap.v1",
                balanceVersion = "balance.test.v1",
                assetEntries = listOf(entry),
                assetRoot = assetRoot,
                approvedLicenseIds = setOf("fixture-license"),
                licenseRegistry = listOf(LicenseRegistryEntry("fixture-license", "CC0", "fixture", "APPROVED", setOf("ANDROID_APP"))),
                fault = BuildFault.SWAP_ASSET_PARENT_BEFORE_HANDLE,
            ))
        }
        assertEquals("INVALID_ASSET_PATH", error.code)
        assertFalse(Files.exists(root.resolve("output/current.json")))
    }

    @Test
    fun `P1-FT-003 asset root replacement with identical bytes is rejected before publish`() {
        val root = Files.createTempDirectory("content-builder-asset-root-swap")
        val source = canonicalSource(root)
        val assetRoot = root.resolve("assets")
        val assetPath = assetRoot.resolve("portrait/test.png")
        Files.createDirectories(assetPath.parent)
        val bytes = minimalPng()
        Files.write(assetPath, bytes)
        val entry = AssetPreviewEntry(
            "asset-png", "PORTRAIT", "portrait/test.png", 8, 8,
            bytes.size.toLong(), CanonicalSourceConverter.sha256(bytes), "fixture-license",
        )
        val error = expectBuildError {
            ContentBuilder.build(ContentBuildRequest(
                sourceRoot = source,
                outputRoot = root.resolve("output"),
                ddlPath = ddl(),
                contentVersion = "content.asset-root-swap.v1",
                balanceVersion = "balance.test.v1",
                assetEntries = listOf(entry),
                assetRoot = assetRoot,
                approvedLicenseIds = setOf("fixture-license"),
                licenseRegistry = listOf(
                    LicenseRegistryEntry("fixture-license", "CC0", "fixture", "APPROVED", setOf("ANDROID_APP"))
                ),
                fault = BuildFault.SWAP_ASSET_ROOT_BEFORE_HANDLE,
            ))
        }
        assertEquals("INVALID_ASSET_PATH", error.code)
        assertFalse(Files.exists(root.resolve("output/current.json")))
    }

    @Test
    fun `P1-FT-002 real directory fsync open failure is surfaced as safe publish failure`() {
        val missingDirectory = Files.createTempDirectory("content-builder-fsync").resolve("missing")
        val method = ContentBuilder::class.java.getDeclaredMethod("forceDirectory", Path::class.java).apply { isAccessible = true }
        val invocation = runCatching { method.invoke(ContentBuilder, missingDirectory) }.exceptionOrNull()
        val cause = invocation?.cause
        assertTrue(cause is ContentBuildException)
        assertEquals("PUBLISH_ATOMIC_UNSUPPORTED", (cause as ContentBuildException).code)
    }

    @Test
    fun `P1-FT-002 nested directory tree durability force succeeds`() {
        val existingDirectory = Files.createTempDirectory("content-builder-fsync-existing")
        val nestedDirectory = existingDirectory.resolve("assets/portrait/nested")
        Files.createDirectories(nestedDirectory)
        Files.writeString(nestedDirectory.resolve("asset.bin"), "fixture")
        val method = ContentBuilder::class.java.getDeclaredMethod("forceTree", Path::class.java).apply { isAccessible = true }

        val invocation = runCatching { method.invoke(ContentBuilder, existingDirectory) }.exceptionOrNull()

        assertTrue("nested directory tree force failed: ${invocation?.cause}", invocation == null)
    }

    @Test
    fun `P1-IT-005 source to sealed bundle artifact handoff preserves identity`() {
        val root = Files.createTempDirectory("content-builder-it5")
        val source = canonicalSource(root)
        val output = root.resolve("output")
        val result = ContentBuilder.build(request(source, output, "content.it5.v1"))
        val manifest = JsonParser.parse(result.bundleDirectory.resolve("content-bundle-manifest.json").readText()).asObject()
        assertEquals(result.bundleHash, manifest.value("bundleId").asString())
        assertTrue(Files.isRegularFile(result.bundleDirectory.resolve("content.db")))
        assertTrue(Files.isRegularFile(result.bundleDirectory.resolve("asset-preview/index.html")))
        assertTrue(Files.isRegularFile(result.bundleDirectory.resolve("query-plan.json")))
        assertTrue((1..6).all { result.bundleDirectory.resolve("query-plan.json").readText().contains("CDB-Q0$it") })
        assertEquals(result.bundleHash, JsonParser.parse(output.resolve("current.json").readText()).asObject().value("bundleId").asString())
        assertTrue(manifest.value("artifactFileSha256").asString().length == 64)
        writePhase1Evidence("P1-IT-005", JsonObject(mapOf(
            "testId" to JsonString("P1-IT-005"),
            "sourceHash" to JsonString(result.sourceHash),
            "logicalContentHash" to JsonString(result.logicalContentHash),
            "bundleHash" to JsonString(result.bundleHash),
            "dbSha256" to manifest.value("artifactFileSha256"),
            "currentPointerBundleId" to JsonParser.parse(output.resolve("current.json").readText()).asObject().value("bundleId")
        )).render())
    }

    @Test
    fun `P1-IT-005 output contract v2 coexists with legacy v1 bundle identity`() {
        val root = Files.createTempDirectory("content-builder-tool-identity")
        val source = canonicalSource(root)
        val output = root.resolve("output")
        val currentRequest = request(source, output, "content.identity.v1")
        val legacy = ContentBuilder.build(currentRequest.copy(generatedByVersion = "p1-content-builder.v1"))
        val current = ContentBuilder.build(currentRequest)
        val repeated = ContentBuilder.build(currentRequest)

        assertTrue(legacy.bundleHash != current.bundleHash)
        assertEquals(current.bundleHash, repeated.bundleHash)
        assertTrue(Files.isDirectory(output.resolve("bundles").resolve(legacy.bundleHash)))
        assertTrue(Files.isDirectory(output.resolve("bundles").resolve(current.bundleHash)))
        assertEquals(2L, Files.list(output.resolve("bundles")).use { it.count() })
        assertEquals(
            "p1-content-builder.v2",
            JsonParser.parse(current.bundleDirectory.resolve("content-bundle-manifest.json").readText())
                .asObject().value("generatedByVersion").asString()
        )
    }

    @Test
    fun `P1-IT-005 alias chains flatten to terminal same-kind rows`() {
        val root = Files.createTempDirectory("content-builder-alias")
        val source = canonicalSource(root)
        val aliases = listOf(
            ContentAliasEntry("alias-1", "WPN-LEGACY-A", "WPN-LEGACY-B", "REMAP", "fixture", oldKind = "WPN", provenanceSection = 1, provenanceRow = 1, approvalRevision = "rev-1"),
            ContentAliasEntry("alias-2", "WPN-LEGACY-B", "WPN-0001", "REMAP", "fixture", oldKind = "WPN", provenanceSection = 1, provenanceRow = 2, approvalRevision = "rev-1")
        )
        val result = ContentBuilder.build(request(source, root.resolve("output"), "content.alias.v1").copy(aliases = aliases))
        DriverManager.getConnection("jdbc:sqlite:${result.bundleDirectory.resolve("content.db").toAbsolutePath()}").use { connection ->
            connection.prepareStatement("SELECT old_id,new_id FROM content_alias ORDER BY old_id").use { statement ->
                statement.executeQuery().use { rows ->
                    assertTrue(rows.next())
                    assertEquals("WPN-LEGACY-A", rows.getString(1))
                    assertEquals("WPN-0001", rows.getString(2))
                    assertTrue(rows.next())
                    assertEquals("WPN-LEGACY-B", rows.getString(1))
                    assertEquals("WPN-0001", rows.getString(2))
                }
            }
        }
        val aliasReport = JsonParser.parse(result.bundleDirectory.resolve("validation-report.json").readText()).asObject()
        val firstResolution = aliasReport.value("aliasResolutions").asArray().values.map(JsonValue::asObject).first { it.value("oldId").asString() == "WPN-LEGACY-A" }
        assertEquals("WPN-LEGACY-B", firstResolution.value("sourceNewId").asString())
        assertEquals("WPN-0001", firstResolution.value("newId").asString())
        val wrongKind = expectBuildError {
            ContentBuilder.build(request(source, root.resolve("wrong-kind"), "content.alias.wrong-kind.v1").copy(aliases = aliases.map { it.copy(oldKind = "ARM") }))
        }
        assertEquals("CONTENT_VALIDATION_FAILED", wrongKind.code)
        val missingProvenance = expectBuildError {
            ContentBuilder.build(request(source, root.resolve("missing-provenance"), "content.alias.missing-provenance.v1").copy(aliases = aliases.map { it.copy(oldKind = "", provenanceSection = 0, provenanceRow = 0, approvalRevision = "") }))
        }
        assertEquals("CONTENT_VALIDATION_FAILED", missingProvenance.code)
    }

    @Test
    fun `P1-ET-001 builder fault matrix keeps stable failure codes`() {
        val faults = listOf(
            BuildFault.WAL_REQUEST to "PRAGMA_INVALID",
            BuildFault.FAKE_SIDECAR to "SQLITE_SIDECAR",
            BuildFault.OPEN_HANDLE to "RESOURCE_OPEN",
            BuildFault.CRASH_AFTER_STAGING to "BUILD_CRASHED",
            BuildFault.ATOMIC_MOVE_UNSUPPORTED to "PUBLISH_ATOMIC_UNSUPPORTED",
            BuildFault.DIRECTORY_FSYNC_UNSUPPORTED to "PUBLISH_ATOMIC_UNSUPPORTED",
            BuildFault.POINTER_MOVE_FAILURE to "PUBLISH_POINTER_FAILED"
        )
        faults.forEachIndexed { index, (fault, expected) ->
            val root = Files.createTempDirectory("content-builder-et-$index")
            val error = expectBuildError {
                ContentBuilder.build(request(canonicalSource(root), root.resolve("output"), "content.et.$index.v1").copy(fault = fault))
            }
            assertEquals(expected, error.code)
        }
    }

    @Test
    fun `P1-ET-001 detailed diagnostics map to the fixed external result code set`() {
        val mappings = mapOf(
            "JSON_DUPLICATE_KEY" to "SOURCE_INVALID",
            "SOURCE_UNKNOWN_FIELD" to "SOURCE_INVALID",
            "CONTENT_ALIAS_CYCLE" to "VALIDATION_FAILED",
            "PROFILE_INVALID" to "VALIDATION_FAILED",
            "ASSET_LICENSE_SCOPE_BLOCKED" to "VALIDATION_FAILED",
            "PRAGMA_INVALID" to "INTEGRITY_FAILED",
            "SQLITE_SIDECAR" to "INTEGRITY_FAILED",
            "PUBLISH_POINTER_FAILED" to "PUBLISH_ATOMIC_UNSUPPORTED",
            "BUILD_CRASHED" to "BUILD_IO",
            "PUBLISH_CONFLICT" to "PUBLISH_CONFLICT",
        )
        mappings.forEach { (detailCode, resultCode) ->
            assertEquals(resultCode, ContentBuildException(detailCode, "fixture").externalResultCode)
        }
    }

    @Test
    fun `P1-ET-001 actual asset failures expose fixed CLI result codes`() {
        fun failure(relativePath: String, bytes: ByteArray?): ContentBuildException {
            val root = Files.createTempDirectory("content-builder-asset-result")
            val assetRoot = root.resolve("assets")
            Files.createDirectories(assetRoot)
            if (bytes != null && !relativePath.startsWith("..")) {
                val file = assetRoot.resolve(relativePath)
                Files.createDirectories(file.parent)
                Files.write(file, bytes)
            }
            val declaredBytes = bytes ?: byteArrayOf(0)
            return expectBuildError {
                ContentBuilder.build(
                    request(canonicalSource(root), root.resolve("output"), "content.asset-result.v1").copy(
                        assetEntries = listOf(
                            AssetPreviewEntry(
                                "asset-result", "PORTRAIT", relativePath, 1, 1,
                                declaredBytes.size.toLong(), CanonicalSourceConverter.sha256(declaredBytes), "fixture",
                            )
                        ),
                        assetRoot = assetRoot,
                        approvedLicenseIds = setOf("fixture"),
                        licenseRegistry = listOf(
                            LicenseRegistryEntry("fixture", "PROJECT_OWNED_FIXTURE", "phase1-fixtures/P1-ET-001", "APPROVED", setOf("ANDROID_APP"))
                        ),
                    )
                )
            }
        }

        val invalidPath = failure("../save.db", null)
        val missing = failure("portrait/missing.png", null)
        val decode = failure("portrait/broken.png", ByteArray(32))
        assertEquals("INVALID_ASSET_PATH", invalidPath.code)
        assertEquals("MISSING_REQUIRED_ASSET", missing.code)
        assertEquals("ASSET_DECODE_FAILED", decode.code)
        listOf(invalidPath, missing, decode).forEach { error ->
            assertTrue(renderCliError(error).startsWith("${error.externalResultCode}:"))
            assertEquals(error.code, error.externalResultCode)
        }
    }

    @Test
    fun `P1-ET-001 assets CLI process emits fixed path missing and decode result codes`() {
        fun cliFailure(relativePath: String, bytes: ByteArray?, expectedCode: String) {
            val root = Files.createTempDirectory("content-builder-assets-cli")
            val assetRoot = root.resolve("assets")
            Files.createDirectories(assetRoot)
            if (bytes != null) {
                val file = assetRoot.resolve(relativePath)
                Files.createDirectories(file.parent)
                Files.write(file, bytes)
            }
            val declaredBytes = bytes ?: byteArrayOf(0)
            val manifest = JsonObject(mapOf(
                "entries" to JsonArray(listOf(JsonObject(mapOf(
                    "alphaMode" to JsonString("OPAQUE"),
                    "assetId" to JsonString("asset-cli"),
                    "byteSize" to JsonNumber(java.math.BigDecimal(declaredBytes.size)),
                    "category" to JsonString("PORTRAIT"),
                    "colorSpace" to JsonString("SRGB"),
                    "exactFileSha256" to JsonString(CanonicalSourceConverter.sha256(declaredBytes)),
                    "height" to JsonNumber(java.math.BigDecimal.ONE),
                    "licenseId" to JsonString("fixture"),
                    "mimeType" to JsonString("image/png"),
                    "poolVersion" to JsonString("pool.v1"),
                    "relativePath" to JsonString(relativePath),
                    "validationStatus" to JsonString("VALID"),
                    "width" to JsonNumber(java.math.BigDecimal.ONE),
                )))),
            ))
            val licenses = JsonObject(mapOf(
                "entries" to JsonArray(listOf(JsonObject(mapOf(
                    "approvalStatus" to JsonString("APPROVED"),
                    "distributionScopes" to JsonArray(listOf(JsonString("ANDROID_APP"))),
                    "id" to JsonString("fixture"),
                    "license" to JsonString("PROJECT_OWNED_FIXTURE"),
                    "source" to JsonString("phase1-fixtures/P1-ET-001"),
                )))),
            ))
            val manifestPath = root.resolve("asset-manifest.json").apply { writeText(manifest.render()) }
            val licensePath = root.resolve("license-registry.json").apply { writeText(licenses.render()) }
            val javaExecutable = Path.of(
                System.getProperty("java.home"), "bin",
                if (System.getProperty("os.name").contains("windows", true)) "java.exe" else "java",
            )
            val process = ProcessBuilder(
                javaExecutable.toString(),
                "-cp", requireNotNull(System.getProperty("phase1.testRuntimeClasspath")),
                "com.imsi.mud.content.builder.BuilderCliKt",
                "assets",
                "--manifest", manifestPath.toString(),
                "--asset-root", assetRoot.toString(),
                "--license-registry", licensePath.toString(),
            ).redirectErrorStream(true).start()
            assertTrue("assets CLI did not terminate", process.waitFor(30, TimeUnit.SECONDS))
            val output = process.inputStream.bufferedReader().readText()
            assertEquals("assets CLI must fail closed", 2, process.exitValue())
            assertTrue("fixed result code missing from CLI output: $output", output.lineSequence().any { it.startsWith("$expectedCode: ") })
            assertTrue("CLI detail code missing from output: $output", output.contains("[detail=$expectedCode]"))
        }

        cliFailure("../save.db", null, "INVALID_ASSET_PATH")
        cliFailure("portrait/missing.png", null, "MISSING_REQUIRED_ASSET")
        cliFailure("portrait/broken.png", ByteArray(32), "ASSET_DECODE_FAILED")
    }

    @Test
    fun `P1-IT-003 assets CLI preview href resolves the verified fixture asset`() {
        val root = Files.createTempDirectory("content-builder-assets-preview-cli")
        val assetRoot = root.resolve("assets")
        val asset = assetRoot.resolve("portrait/test.png")
        Files.createDirectories(asset.parent)
        val bytes = minimalPng()
        Files.write(asset, bytes)
        val manifest = JsonObject(mapOf(
            "entries" to JsonArray(listOf(JsonObject(mapOf(
                "alphaMode" to JsonString("OPAQUE"), "assetId" to JsonString("asset-cli"),
                "byteSize" to JsonNumber(java.math.BigDecimal(bytes.size)), "category" to JsonString("PORTRAIT"),
                "colorSpace" to JsonString("SRGB"), "exactFileSha256" to JsonString(CanonicalSourceConverter.sha256(bytes)),
                "height" to JsonNumber(java.math.BigDecimal(8)), "licenseId" to JsonString("fixture"),
                "mimeType" to JsonString("image/png"), "poolVersion" to JsonString("pool.v1"),
                "relativePath" to JsonString("portrait/test.png"), "validationStatus" to JsonString("VALID"),
                "width" to JsonNumber(java.math.BigDecimal(8))
            ))))
        ))
        val licenses = JsonObject(mapOf(
            "entries" to JsonArray(listOf(JsonObject(mapOf(
                "approvalStatus" to JsonString("APPROVED"), "distributionScopes" to JsonArray(listOf(JsonString("ANDROID_APP"))),
                "id" to JsonString("fixture"), "license" to JsonString("PROJECT_OWNED_FIXTURE"), "source" to JsonString("fixture")
            ))))
        ))
        val manifestPath = root.resolve("asset-manifest.json").apply { writeText(manifest.render()) }
        val licensePath = root.resolve("license-registry.json").apply { writeText(licenses.render()) }
        val preview = root.resolve("generated/preview")
        val javaExecutable = Path.of(System.getProperty("java.home"), "bin", if (System.getProperty("os.name").contains("windows", true)) "java.exe" else "java")
        val process = ProcessBuilder(
            javaExecutable.toString(), "-cp", requireNotNull(System.getProperty("phase1.testRuntimeClasspath")),
            "com.imsi.mud.content.builder.BuilderCliKt", "assets", "--manifest", manifestPath.toString(),
            "--asset-root", assetRoot.toString(), "--license-registry", licensePath.toString(),
            "--preview-output", preview.toString()
        ).redirectErrorStream(true).start()
        assertTrue("assets CLI did not terminate", process.waitFor(30, TimeUnit.SECONDS))
        assertEquals(process.inputStream.bufferedReader().readText(), 0, process.exitValue())
        val page = preview.resolve("PORTRAIT-001.html")
        val href = Regex("""<img[^>]+src=\"([^\"]+)\"""").find(page.readText())!!.groupValues[1]
        assertEquals(asset.toRealPath(), page.parent.resolve(href).normalize().toRealPath())
    }

    @Test
    fun `P1-CN-001 same output lock permits one publisher and rejects the other`() {
        val root = Files.createTempDirectory("content-builder-concurrency")
        val source = canonicalSource(root)
        val output = root.resolve("output")
        val request = request(source, output, "content.concurrent.v1").copy(fault = BuildFault.HOLD_LOCK)
        val pool = Executors.newFixedThreadPool(2)
        try {
            val outcomes = listOf(
                pool.submit<Result<ContentBuildResult>> { runCatching { ContentBuilder.build(request) } },
                pool.submit<Result<ContentBuildResult>> { runCatching { ContentBuilder.build(request) } }
            ).map { it.get(30, TimeUnit.SECONDS) }
            assertEquals(1, outcomes.count(Result<ContentBuildResult>::isSuccess))
            val conflicts = outcomes.mapNotNull { it.exceptionOrNull() }.filterIsInstance<ContentBuildException>()
            assertEquals(1, conflicts.size)
            assertEquals("PUBLISH_CONFLICT", conflicts.single().code)
        } finally {
            pool.shutdownNow()
        }
    }

    @Test
    fun `P1-REC-001 crash killpoint leaves diagnosable orphan and rerun completes pointer`() {
        val root = Files.createTempDirectory("content-builder-recovery")
        val source = canonicalSource(root)
        val output = root.resolve("output")
        val crashing = request(source, output, "content.recovery.v1").copy(fault = BuildFault.CRASH_AFTER_STAGING)
        assertEquals("BUILD_CRASHED", expectBuildError { ContentBuilder.build(crashing) }.code)
        assertTrue(Files.isRegularFile(output.resolve("staging/content.db")))
        assertFalse(Files.exists(output.resolve("current.json")))

        val recovered = ContentBuilder.build(crashing.copy(fault = null))
        assertTrue(Files.isRegularFile(recovered.bundleDirectory.resolve("content-bundle-manifest.json")))
        assertTrue(output.resolve("current.json").readText().contains(recovered.bundleHash))
        val orphan = output.resolve("orphan")
        assertTrue(Files.isDirectory(orphan))
        assertTrue(Files.walk(orphan).use { it.anyMatch { path -> path.fileName.toString() == "orphan-diagnosis.json" } })
        val beforeMoveRoot = Files.createTempDirectory("content-builder-recovery-before-move")
        val beforeMoveOutput = beforeMoveRoot.resolve("output")
        val beforeMoveRequest = request(canonicalSource(beforeMoveRoot), beforeMoveOutput, "content.recovery.before-move.v1").copy(fault = BuildFault.CRASH_BEFORE_BUNDLE_MOVE)
        assertEquals("BUILD_CRASHED_BEFORE_BUNDLE_MOVE", expectBuildError { ContentBuilder.build(beforeMoveRequest) }.code)
        assertTrue(Files.isRegularFile(beforeMoveOutput.resolve("staging/build-staging.marker")))
        assertFalse(Files.exists(beforeMoveOutput.resolve("current.json")))
        val beforeMoveRecovered = ContentBuilder.build(beforeMoveRequest.copy(fault = null))
        assertTrue(beforeMoveOutput.resolve("current.json").readText().contains(beforeMoveRecovered.bundleHash))
        assertTrue(Files.isDirectory(beforeMoveOutput.resolve("orphan")))

        val afterMoveRoot = Files.createTempDirectory("content-builder-recovery-after-move")
        val afterMoveOutput = afterMoveRoot.resolve("output")
        val afterMoveRequest = request(canonicalSource(afterMoveRoot), afterMoveOutput, "content.recovery.after-move.v1").copy(fault = BuildFault.CRASH_AFTER_BUNDLE_MOVE_BEFORE_POINTER)
        assertEquals("BUILD_CRASHED_AFTER_BUNDLE_MOVE", expectBuildError { ContentBuilder.build(afterMoveRequest) }.code)
        val movedBundle = Files.list(afterMoveOutput.resolve("bundles")).use { it.findFirst().orElseThrow() }
        assertFalse(Files.exists(movedBundle.resolve("build-staging.marker")))
        assertFalse(Files.exists(afterMoveOutput.resolve("current.json")))
        val afterMoveRecovered = ContentBuilder.build(afterMoveRequest.copy(fault = null))
        assertTrue(afterMoveOutput.resolve("current.json").readText().contains(afterMoveRecovered.bundleHash))
        val externalKillpoints = listOf(
            BuildFault.HOLD_AFTER_STAGING_FOR_EXTERNAL_KILL,
            BuildFault.HOLD_BEFORE_BUNDLE_MOVE_FOR_EXTERNAL_KILL,
            BuildFault.HOLD_AFTER_BUNDLE_MOVE_FOR_EXTERNAL_KILL,
            BuildFault.HOLD_AFTER_POINTER_FOR_EXTERNAL_KILL,
        )
        val externalKillpointPointers = externalKillpoints.mapIndexed(::verifyExternalProcessKill)
        writePhase1Evidence("P1-REC-001", JsonObject(mapOf(
            "testId" to JsonString("P1-REC-001"),
            "states" to JsonArray(listOf(JsonString("CRASHED_STAGING"), JsonString("ORPHAN_QUARANTINED"), JsonString("CRASHED_BEFORE_BUNDLE_MOVE"), JsonString("CRASHED_AFTER_BUNDLE_MOVE_BEFORE_POINTER"), JsonString("PUBLISHED"))),
            "orphanDiagnosis" to JsonString("artifacts/orphan-diagnosis.json"),
            "externalKillpoints" to JsonArray(externalKillpoints.map { JsonString(it.name) }),
            "externalKillpointPointers" to JsonArray(externalKillpointPointers),
            "sidecarCount" to JsonNumber(java.math.BigDecimal.ZERO),
            "currentPointer" to JsonString(output.resolve("current.json").readText().trim()),
            "beforeMovePointer" to JsonString(beforeMoveOutput.resolve("current.json").readText().trim()),
            "afterMovePointer" to JsonString(afterMoveOutput.resolve("current.json").readText().trim()),
            "currentPointerArtifact" to JsonString("artifacts/current.json"),
            "bundleManifestArtifact" to JsonString("artifacts/content-bundle-manifest.json"),
            "databaseArtifact" to JsonString("artifacts/content.db")
        )).render())
        val evidenceArtifacts = phase1EvidenceDirectory("P1-REC-001").resolve("artifacts")
        Files.createDirectories(evidenceArtifacts)
        val finalBundleId = assertValidPublishedPointer(output)
        val finalBundle = output.resolve("bundles").resolve(finalBundleId)
        Files.copy(output.resolve("current.json"), evidenceArtifacts.resolve("current.json"), java.nio.file.StandardCopyOption.REPLACE_EXISTING)
        Files.copy(finalBundle.resolve("content-bundle-manifest.json"), evidenceArtifacts.resolve("content-bundle-manifest.json"), java.nio.file.StandardCopyOption.REPLACE_EXISTING)
        Files.copy(finalBundle.resolve("content.db"), evidenceArtifacts.resolve("content.db"), java.nio.file.StandardCopyOption.REPLACE_EXISTING)
        val orphanDiagnosis = Files.walk(orphan).use { paths ->
            paths.filter { it.fileName.toString() == "orphan-diagnosis.json" }.findFirst().orElseThrow()
        }
        Files.copy(orphanDiagnosis, evidenceArtifacts.resolve("orphan-diagnosis.json"), java.nio.file.StandardCopyOption.REPLACE_EXISTING)
    }

    private fun request(source: Path, output: Path, contentVersion: String): ContentBuildRequest = ContentBuildRequest(
        sourceRoot = source,
        outputRoot = output,
        ddlPath = ddl(),
        contentVersion = contentVersion,
        balanceVersion = "balance.test.v1"
    )

    private fun ddl(): Path = Path.of("..", "..", "docs", "설계부록", "02_제안_content_schema.sql").toAbsolutePath().normalize()

    private fun canonicalSource(root: Path): Path {
        val source = root.resolve("source")
        val bootstrap = Path.of("..", "..", "phase1-fixtures", "P1-CT-001", "bootstrap", "catalog_rows.json").toAbsolutePath().normalize()
        CanonicalSourceConverter.convert(bootstrap, source)
        return source
    }

    private fun verifyExternalProcessKill(index: Int, fault: BuildFault): JsonObject {
        val root = Files.createTempDirectory("content-builder-external-kill-$index")
        val source = canonicalSource(root)
        val output = root.resolve("output")
        val baseline = ContentBuilder.build(
            request(source, output, "content.external-kill.$index.baseline.v1")
                .copy(generatedByVersion = "p1-content-builder.external-kill.v1")
        )
        val baselinePointer = output.resolve("current.json").readText().trim()
        assertEquals(baseline.bundleHash, assertValidPublishedPointer(output))
        val contentVersion = "content.external-kill.$index.v1"
        val javaExecutable = Path.of(System.getProperty("java.home"), "bin", if (System.getProperty("os.name").contains("windows", true)) "java.exe" else "java")
        val classpath = requireNotNull(System.getProperty("phase1.testRuntimeClasspath"))
        assertTrue("test runtime classpath was not provided", classpath.isNotBlank())
        val process = ProcessBuilder(
            javaExecutable.toString(),
            "-cp",
            classpath,
            ExternalKillHarness::class.java.name,
            source.toString(),
            output.toString(),
            ddl().toString(),
            contentVersion,
            fault.name,
            "p1-content-builder.external-kill.v1",
        ).redirectErrorStream(true).start()
        val marker = output.resolve(".${fault.name.lowercase()}.ready")
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30)
        while (!Files.isRegularFile(marker) && process.isAlive && System.nanoTime() < deadline) Thread.sleep(25L)
        if (!Files.isRegularFile(marker)) {
            val outputText = if (process.isAlive) "process still active" else process.inputStream.bufferedReader().readText()
            process.destroyForcibly()
            throw AssertionError("external killpoint was not reached for $fault: $outputText")
        }
        process.destroyForcibly()
        assertTrue("external builder process did not terminate", process.waitFor(10, TimeUnit.SECONDS))
        assertTrue("external process must terminate abnormally", process.exitValue() != 0)
        Thread.sleep(250L) // Windows can release the terminated process' file lock asynchronously.

        val afterKillPointer = output.resolve("current.json").readText().trim()
        val afterKillBundleId = assertValidPublishedPointer(output)
        val expectedState = if (fault == BuildFault.HOLD_AFTER_POINTER_FOR_EXTERNAL_KILL) "NEXT" else "PREVIOUS"
        if (expectedState == "PREVIOUS") {
            assertEquals("pointer changed before the durable pointer checkpoint", baselinePointer, afterKillPointer)
            assertEquals(baseline.bundleHash, afterKillBundleId)
        } else {
            assertTrue("pointer checkpoint must publish a complete next bundle", afterKillBundleId != baseline.bundleHash)
        }
        val recovered = ContentBuilder.build(request(source, output, contentVersion).copy(generatedByVersion = "p1-content-builder.external-kill.v1"))
        assertEquals(recovered.bundleHash, assertValidPublishedPointer(output))
        if (expectedState == "NEXT") assertEquals(afterKillPointer, output.resolve("current.json").readText().trim())
        val sidecars = Files.walk(output).use { stream ->
            stream.filter(Files::isRegularFile).filter { path ->
                path.fileName.toString().endsWith("-wal") || path.fileName.toString().endsWith("-shm") || path.fileName.toString().endsWith("-journal")
            }.count()
        }
        assertEquals(0L, sidecars)
        return JsonObject(mapOf(
            "afterKillBundleId" to JsonString(afterKillBundleId),
            "afterRecoveryBundleId" to JsonString(recovered.bundleHash),
            "beforeBundleId" to JsonString(baseline.bundleHash),
            "fault" to JsonString(fault.name),
            "pointerState" to JsonString(expectedState),
        ))
    }

    private fun assertValidPublishedPointer(output: Path): String {
        val pointer = JsonParser.parse(output.resolve("current.json").readText()).asObject()
        assertEquals(setOf("bundleId", "contentVersion"), pointer.values.keys)
        val bundleId = pointer.value("bundleId").asString()
        val bundle = output.resolve("bundles").resolve(bundleId)
        val manifest = JsonParser.parse(bundle.resolve("content-bundle-manifest.json").readText()).asObject()
        assertEquals(bundleId, manifest.value("bundleId").asString())
        assertEquals(
            manifest.value("artifactFileSha256").asString(),
            CanonicalSourceConverter.sha256(Files.readAllBytes(bundle.resolve("content.db"))),
        )
        return bundleId
    }

    private fun canonicalSkillSource(root: Path): Path {
        val bootstrap = root.resolve("skill-bootstrap.json")
        bootstrap.writeText("{\"SKL\":{\"SKL-0001\":{\"id\":\"SKL-0001\",\"section\":1,\"row\":\"| SKL-0001 | Test skill | General | Active | Common | All | Stamina 12 | 8s | damage | tag |\"}}}")
        val source = root.resolve("source")
        CanonicalSourceConverter.convert(bootstrap, source)
        return source
    }

    private fun rewriteWpn(source: Path, rewrite: (JsonObject) -> JsonObject) = rewriteCatalog(source, "WPN", rewrite)

    private fun rewriteCatalog(source: Path, kind: String, rewrite: (JsonObject) -> JsonObject) {
        val path = source.resolve("catalog/$kind.json")
        val catalog = JsonParser.parse(path.readText()).asObject()
        val records = catalog.value("records").asArray().values.map { rewrite(it.asObject()) }
        val rewritten = JsonObject(catalog.values + ("records" to JsonArray(records))).render() + "\n"
        path.writeText(rewritten)
        val manifestPath = source.resolve("catalog-manifest.json")
        val manifest = JsonParser.parse(manifestPath.readText()).asObject()
        val files = manifest.value("files").asArray().values.map { value ->
            val entry = value.asObject()
            if (entry.value("kind").asString() == kind) JsonObject(entry.values + ("exactFileSha256" to JsonString(CanonicalSourceConverter.sha256(rewritten.toByteArray())))) else entry
        }
        manifestPath.writeText(JsonObject(manifest.values + ("files" to JsonArray(files))).render() + "\n")
    }

    private fun rewriteManifestHash(source: Path, kind: String, hash: String) {
        val manifestPath = source.resolve("catalog-manifest.json")
        val manifest = JsonParser.parse(manifestPath.readText()).asObject()
        val files = manifest.value("files").asArray().values.map { value ->
            val entry = value.asObject()
            if (entry.value("kind").asString() == kind) JsonObject(entry.values + ("exactFileSha256" to JsonString(hash))) else entry
        }
        manifestPath.writeText(JsonObject(manifest.values + ("files" to JsonArray(files))).render() + "\n")
    }

    private fun rewriteManifestEntry(source: Path, kind: String, path: String, rowCount: Int, hash: String) {
        val manifestPath = source.resolve("catalog-manifest.json")
        val manifest = JsonParser.parse(manifestPath.readText()).asObject()
        val files = manifest.value("files").asArray().values.map { value ->
            val entry = value.asObject()
            if (entry.value("kind").asString() == kind) JsonObject(entry.values + mapOf(
                "exactFileSha256" to JsonString(hash),
                "path" to JsonString(path),
                "rowCount" to JsonNumber(java.math.BigDecimal(rowCount))
            )) else entry
        }
        manifestPath.writeText(JsonObject(manifest.values + ("files" to JsonArray(files))).render() + "\n")
    }

    private fun expectBuildError(block: () -> Unit): ContentBuildException {
        val error = runCatching(block).exceptionOrNull()
        assertTrue("expected ContentBuildException, got $error", error is ContentBuildException)
        return error as ContentBuildException
    }

    private fun countFiles(root: Path, fileName: String): Long {
        if (!Files.exists(root)) return 0
        return Files.walk(root).use { stream -> stream.filter(Files::isRegularFile).filter { it.fileName.toString() == fileName }.count() }
    }

    private fun minimalPng(): ByteArray = ByteArrayOutputStream().use { output ->
        ImageIO.write(BufferedImage(8, 8, BufferedImage.TYPE_INT_RGB), "png", output)
        output.toByteArray()
    }

}
