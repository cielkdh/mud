package com.imsi.mud.content.builder

import com.imsi.mud.content.AssetCategory
import com.imsi.mud.content.AssetId
import com.imsi.mud.content.AssetImage
import com.imsi.mud.content.AssetResolveRequest
import com.imsi.mud.content.AssetResolver
import com.imsi.mud.content.ContentRepository
import com.imsi.mud.content.EntityKind
import com.imsi.mud.content.ImageUsage
import com.imsi.mud.content.InMemoryContentRepository
import com.imsi.mud.content.InstalledBundle
import com.imsi.mud.content.QualityMode
import com.imsi.mud.content.ResolvedAsset
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.lang.management.ManagementFactory
import java.lang.management.MemoryType
import java.nio.file.Files
import java.nio.file.Path
import java.sql.DriverManager
import java.sql.PreparedStatement
import java.util.Base64
import javax.imageio.ImageIO
import kotlin.io.path.readText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.Rule

class AssetPreviewRendererTest {
    private data class RuntimeMetrics(
        val decodeMetrics: List<JsonObject>,
        val textForbiddenIoCount: Int,
        val textAllowedDecodeCount: Int,
        val cacheKeySampleCount: Int,
        val staleAssetCount: Int,
    )
    @get:Rule val phase1Fixture = Phase1FixtureRule()
    @Test
    fun `P1-PT-001 synthetic ten thousand entries are split into bounded escaped pages`() {
        val root = Files.createTempDirectory("asset-preview-performance")
        val assetRoot = root.resolve("assets")
        val seedFile = root.resolve("seed.png")
        Files.createDirectories(seedFile.parent)
        val png = ByteArrayOutputStream().use { outputStream ->
            ImageIO.write(BufferedImage(8, 8, BufferedImage.TYPE_INT_RGB), "png", outputStream)
            outputStream.toByteArray()
        }
        Files.write(seedFile, png)
        val pngHash = CanonicalSourceConverter.sha256(png)
        val entries = (0 until 10_000).map { index ->
            val relativePath = "portrait/${index.toString().padStart(5, '0')}.png"
            val file = assetRoot.resolve(relativePath)
            Files.createDirectories(file.parent)
            runCatching { Files.createLink(file, seedFile) }.getOrElse { Files.write(file, png) }
            AssetPreviewEntry(
                id = "asset-$index<&",
                category = "PORTRAIT",
                relativePath = relativePath,
                width = 8,
                height = 8,
                byteSize = png.size.toLong(),
                sha256 = pngHash,
                licenseId = "fixture",
            )
        }
        val source = maximumCanonicalSource(root.resolve("source"), 10_000)
        val directPreview = root.resolve("direct-preview")
        val previewStarted = System.nanoTime()
        AssetPreviewRenderer.render(entries, directPreview)
        val previewElapsedMs = (System.nanoTime() - previewStarted) / 1_000_000
        assertEquals(20L, Files.list(directPreview).use { stream ->
            stream.filter { it.fileName.toString().startsWith("PORTRAIT-") }.count()
        })
        val heapPools = ManagementFactory.getMemoryPoolMXBeans().filter { it.type == MemoryType.HEAP }
        heapPools.forEach { runCatching { it.resetPeakUsage() } }
        val builderHeapBefore = heapPools.sumOf { it.usage.used.coerceAtLeast(0L) }
        val builderStarted = System.nanoTime()
        val build = ContentBuilder.build(ContentBuildRequest(
            sourceRoot = source,
            outputRoot = root.resolve("bundle"),
            ddlPath = Path.of("..", "..", "docs", "설계부록", "02_제안_content_schema.sql").toAbsolutePath().normalize(),
            contentVersion = "content.pt.v1",
            balanceVersion = "balance.test.v1",
            assetEntries = entries,
            assetRoot = assetRoot,
            approvedLicenseIds = setOf("fixture"),
            licenseRegistry = listOf(LicenseRegistryEntry("fixture", "PROJECT_OWNED_FIXTURE", "phase1-fixtures/P1-PT-001", "APPROVED", setOf("ANDROID_APP"))),
            aliases = listOf(ContentAliasEntry("alias-pt", "WPN-PT-LEGACY", "WPN-PT-00001", "REMAP", "performance fixture", "WPN", 1, 1, "p1-pt")),
            assetBindings = listOf(AssetBindingEntry("binding-pt", "WPN-PT-00001", "LIST_FACE", entries.first().id, 0)),
            assetFallbacks = listOf(AssetFallbackEntry("fallback-pt", "LIST_FACE", "GLOBAL_DEFAULT", null, entries.first().id, 0)),
        ))
        val builderWallMs = (System.nanoTime() - builderStarted) / 1_000_000
        val builderPeakHeap = heapPools.sumOf { it.peakUsage.used.coerceAtLeast(0L) }
        val preview = build.bundleDirectory.resolve("asset-preview")
        val previewPages = Files.list(preview).use { stream -> stream.filter { it.fileName.toString().startsWith("PORTRAIT-") }.count().toInt() }
        assertEquals(20, previewPages)
        assertTrue(preview.resolve("PORTRAIT-001.html").readText().contains("asset-0&lt;&amp;"))
        assertTrue(preview.resolve("PORTRAIT-020.html").readText().contains("asset-9999"))
        assertTrue(!Files.exists(preview.resolve("PORTRAIT-021.html")))
        val plans = build.bundleDirectory.resolve("query-plan.json").readText()
        assertTrue((1..6).all { plans.contains("CDB-Q0$it") })
        assertTrue(!plans.contains("SCAN ") && !plans.contains("USE TEMP B-TREE"))
        DriverManager.getConnection("jdbc:sqlite:${build.bundleDirectory.resolve("content.db").toAbsolutePath()}").use { connection ->
            connection.createStatement().use { statement ->
                statement.executeQuery("SELECT COUNT(*) FROM content_template").use { resultSet ->
                    assertTrue(resultSet.next())
                    assertEquals(10_000, resultSet.getInt(1))
                }
                statement.executeQuery("SELECT COUNT(*) FROM asset_image").use { resultSet ->
                    assertTrue(resultSet.next())
                    assertEquals(10_000, resultSet.getInt(1))
                }
            }
        }
        val queryMetrics = measureQueries(build.bundleDirectory.resolve("content.db"), build.bundleDirectory.resolve("query-plan.json"))
        val webp = Base64.getMimeDecoder().decode(
            Files.readString(Path.of("..", "..", "phase1-fixtures", "P1-IT-003", "assets", "one-pixel.webp.base64"))
        )
        val runtimeMetrics = measureResolverAndDecode(png, webp)
        writePhase1Evidence("P1-PT-001", JsonObject(mapOf(
            "testId" to JsonString("P1-PT-001"),
            "entryCount" to JsonNumber(java.math.BigDecimal(10_000)),
            "assetRowCount" to JsonNumber(java.math.BigDecimal(10_000)),
            "pageCount" to JsonNumber(java.math.BigDecimal(previewPages)),
            "maxEntriesPerPage" to JsonNumber(java.math.BigDecimal(500)),
            "previewElapsedMs" to JsonNumber(java.math.BigDecimal(previewElapsedMs)),
            "builderWallMs" to JsonNumber(java.math.BigDecimal(builderWallMs)),
            "builderHeapDeltaBytes" to JsonNumber(java.math.BigDecimal((builderPeakHeap - builderHeapBefore).coerceAtLeast(0L))),
            "builderPeakHeapBytes" to JsonNumber(java.math.BigDecimal(builderPeakHeap)),
            "dbSizeBytes" to JsonNumber(java.math.BigDecimal(Files.size(build.bundleDirectory.resolve("content.db")))),
            "queryMetrics" to JsonArray(queryMetrics),
            "decodeMetrics" to JsonArray(runtimeMetrics.decodeMetrics),
            "textForbiddenIoCount" to JsonNumber(java.math.BigDecimal(runtimeMetrics.textForbiddenIoCount)),
            "textAllowedDecodeCount" to JsonNumber(java.math.BigDecimal(runtimeMetrics.textAllowedDecodeCount)),
            "cacheKeySampleCount" to JsonNumber(java.math.BigDecimal(runtimeMetrics.cacheKeySampleCount)),
            "staleAssetCount" to JsonNumber(java.math.BigDecimal(runtimeMetrics.staleAssetCount)),
            "decodedFormats" to JsonArray(listOf(JsonString("PNG"), JsonString("WEBP")))
        )).render())
    }

    private fun measureQueries(database: Path, queryPlan: Path): List<JsonObject> {
        data class QueryCase(val id: String, val sql: String, val bind: (PreparedStatement) -> Unit)
        val cases = listOf(
            QueryCase("CDB-Q01", "SELECT id,kind,source_display_name,display_name,enabled,definition_json,definition_version FROM content_template WHERE id = ?") { it.setString(1, "WPN-PT-00001") },
            QueryCase("CDB-Q02", "SELECT id,kind,source_display_name,display_name,enabled,definition_json,definition_version FROM content_template WHERE kind = ? ORDER BY id") { it.setString(1, "WPN") },
            QueryCase("CDB-Q03", "SELECT old_id,new_id,policy,reason FROM content_alias WHERE old_id = ?") { it.setString(1, "WPN-PT-LEGACY") },
            QueryCase("CDB-Q04", "SELECT asset_id,priority FROM asset_binding WHERE template_id = ? AND usage_type = ? ORDER BY priority") { statement -> statement.setString(1, "WPN-PT-00001"); statement.setString(2, "LIST_FACE") },
            QueryCase("CDB-Q05", "SELECT id,relative_path,category,width,height,byte_size,sha256 FROM asset_image WHERE id = ?") { it.setString(1, "asset-0<&") },
            QueryCase("CDB-Q06", "SELECT asset_id,priority FROM asset_fallback WHERE usage_type = ?") { it.setString(1, "LIST_FACE") },
        )
        val plans = JsonParser.parse(queryPlan.readText()).asObject().value("queries").asArray().values
            .map(JsonValue::asObject)
            .associate { it.value("id").asString() to it.value("plan").asString() }
        return DriverManager.getConnection("jdbc:sqlite:${database.toAbsolutePath()}").use { connection ->
            connection.createStatement().use { it.execute("PRAGMA query_only=ON") }
            cases.map { query ->
                val samples = (0 until 20).map {
                    val started = System.nanoTime()
                    connection.prepareStatement(query.sql).use { statement ->
                        query.bind(statement)
                        statement.executeQuery().use { rows -> while (rows.next()) rows.getObject(1) }
                    }
                    (System.nanoTime() - started) / 1_000L
                }.sorted()
                JsonObject(mapOf(
                    "id" to JsonString(query.id),
                    "plan" to JsonString(plans.getValue(query.id)),
                    "p50Micros" to JsonNumber(java.math.BigDecimal(samples[samples.size / 2])),
                    "p95Micros" to JsonNumber(java.math.BigDecimal(samples[(samples.size * 95 / 100).coerceAtMost(samples.lastIndex)])),
                ))
            }
        }
    }

    private fun measureResolverAndDecode(png: ByteArray, webp: ByteArray): RuntimeMetrics {
        val assets = mapOf(
            "PNG" to AssetImage(AssetId("runtime-png"), AssetCategory.PORTRAIT, "portrait/runtime.png", 8, 8, png.size.toLong(), CanonicalSourceConverter.sha256(png)),
            "WEBP" to AssetImage(AssetId("runtime-webp"), AssetCategory.PORTRAIT, "portrait/runtime.webp", 1, 1, webp.size.toLong(), CanonicalSourceConverter.sha256(webp)),
        )
        var repositoryIoCount = 0
        val repository = InMemoryContentRepository(
            installedBundle = InstalledBundle("bundle-a", "content-v1", "balance-v1"),
            templates = emptyList(), aliases = emptyList(), bindings = emptyList(), assets = assets.values, fallbacks = emptyList(),
            beforeRead = { repositoryIoCount++ },
        )
        val bytesById = mapOf(assets.getValue("PNG").id to png, assets.getValue("WEBP").id to webp)
        val decodeMetrics = mutableListOf<JsonObject>()
        val bundleAKeys = mutableSetOf<String>()
        val bundleBKeys = mutableSetOf<String>()
        assets.forEach { (format, asset) ->
            listOf(QualityMode.FULL, QualityMode.LOW).forEach { quality ->
                listOf(128, 1_024).forEach { target ->
                    val samples = (0 until 20).map {
                        val started = System.nanoTime()
                        val request = AssetResolveRequest(
                            bundleId = "bundle-a",
                            exactAssetKeys = listOf(asset.id),
                            entityKind = EntityKind.MERCENARY,
                            usage = ImageUsage.LIST_FACE,
                            targetPx = target,
                            qualityMode = quality,
                        )
                        val resolved = AssetResolver.resolve(repository, request) { candidate ->
                            ImageIO.read(ByteArrayInputStream(bytesById.getValue(candidate.id))) != null
                        }
                        assertTrue(resolved is ResolvedAsset.Exact)
                        (System.nanoTime() - started) / 1_000L
                    }.sorted()
                    val request = AssetResolveRequest("bundle-a", exactAssetKeys = listOf(asset.id), entityKind = EntityKind.MERCENARY, usage = ImageUsage.LIST_FACE, targetPx = target, qualityMode = quality)
                    bundleAKeys += AssetResolver.memoryCacheKey("bundle-a", asset, ImageUsage.LIST_FACE, target, quality)
                    bundleBKeys += AssetResolver.memoryCacheKey("bundle-b", asset, ImageUsage.LIST_FACE, target, quality)
                    val resolved = AssetResolver.resolve(repository, request) { true } as ResolvedAsset.Exact
                    decodeMetrics += JsonObject(mapOf(
                        "format" to JsonString(format),
                        "qualityMode" to JsonString(quality.name),
                        "targetPx" to JsonNumber(java.math.BigDecimal(target)),
                        "effectiveTargetPx" to JsonNumber(java.math.BigDecimal(resolved.effectiveTargetPx)),
                        "p50Micros" to JsonNumber(java.math.BigDecimal(samples[samples.size / 2])),
                        "p95Micros" to JsonNumber(java.math.BigDecimal(samples[(samples.size * 95 / 100).coerceAtMost(samples.lastIndex)])),
                    ))
                }
            }
        }
        val beforeForbidden = repositoryIoCount
        var forbiddenDecodeCount = 0
        val forbidden = AssetResolver.resolve(
            repository,
            AssetResolveRequest("bundle-a", exactAssetKeys = listOf(assets.getValue("PNG").id), entityKind = EntityKind.ROOM, usage = ImageUsage.ROOM_BACKGROUND, targetPx = 1_024, qualityMode = QualityMode.TEXT),
        ) { forbiddenDecodeCount++; true }
        assertTrue(forbidden is ResolvedAsset.SkippedByQualityMode)
        val forbiddenIoCount = (repositoryIoCount - beforeForbidden) + forbiddenDecodeCount
        var allowedDecodeCount = 0
        val allowed = AssetResolver.resolve(
            repository,
            AssetResolveRequest("bundle-a", exactAssetKeys = listOf(assets.getValue("PNG").id), entityKind = EntityKind.MERCENARY, usage = ImageUsage.LIST_FACE, targetPx = 128, qualityMode = QualityMode.TEXT),
        ) { allowedDecodeCount++; ImageIO.read(ByteArrayInputStream(bytesById.getValue(it.id))) != null }
        assertTrue(allowed is ResolvedAsset.Exact)
        repository.close()
        return RuntimeMetrics(
            decodeMetrics = decodeMetrics,
            textForbiddenIoCount = forbiddenIoCount,
            textAllowedDecodeCount = allowedDecodeCount,
            cacheKeySampleCount = bundleAKeys.size + bundleBKeys.size,
            staleAssetCount = bundleAKeys.intersect(bundleBKeys).size,
        )
    }

    private fun maximumCanonicalSource(sourceRoot: Path, count: Int): Path {
        val canonical = JsonParser.parse(
            Files.readString(Path.of("..", "..", "content", "source", "catalog", "WPN.json").toAbsolutePath().normalize())
        ).asObject()
        val baseRecord = canonical.value("records").asArray().values.first().asObject()
        val records = (1..count).map { index ->
            val id = "WPN-PT-${index.toString().padStart(5, '0')}"
            val definition = baseRecord.value("definition").asObject()
            JsonObject(baseRecord.values + mapOf(
                "definition" to JsonObject(definition.values + ("name" to JsonString("Performance Weapon $index"))),
                "id" to JsonString(id),
                "provenance" to JsonObject(mapOf(
                    "rawRow" to JsonString("generated performance fixture $id"),
                    "row" to JsonNumber(java.math.BigDecimal(index)),
                    "section" to JsonNumber(java.math.BigDecimal(1)),
                )),
                "sourceDisplayName" to JsonString("Performance Weapon $index"),
            ))
        }
        val document = JsonObject(mapOf(
            "definitionVersion" to JsonNumber(java.math.BigDecimal.ONE),
            "kind" to JsonString("WPN"),
            "records" to JsonArray(records),
            "schemaVersion" to JsonNumber(java.math.BigDecimal.ONE),
            "sourceVersion" to JsonString("catalog.performance.v1"),
        ))
        val catalog = sourceRoot.resolve("catalog")
        Files.createDirectories(catalog)
        val sourceFile = catalog.resolve("WPN.json")
        val sourceBytes = document.render().toByteArray(Charsets.UTF_8)
        Files.write(sourceFile, sourceBytes)
        val manifest = JsonObject(mapOf(
            "files" to JsonArray(listOf(JsonObject(mapOf(
                "exactFileSha256" to JsonString(CanonicalSourceConverter.sha256(sourceBytes)),
                "kind" to JsonString("WPN"),
                "path" to JsonString("catalog/WPN.json"),
                "rowCount" to JsonNumber(java.math.BigDecimal(count)),
                "schemaVersion" to JsonNumber(java.math.BigDecimal.ONE),
            )))),
            "manifestVersion" to JsonNumber(java.math.BigDecimal.ONE),
            "sourceVersion" to JsonString("catalog.performance.v1"),
        ))
        Files.writeString(sourceRoot.resolve("catalog-manifest.json"), manifest.render())
        return sourceRoot
    }
}
