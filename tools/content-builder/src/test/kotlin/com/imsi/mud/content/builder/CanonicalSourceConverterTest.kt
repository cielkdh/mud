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

class CanonicalSourceConverterTest {
    @get:Rule val phase1Fixture = Phase1FixtureRule()
    @Test
    fun `P1-CT-001 conversion is deterministic and preserves provenance`() {
        val root = Files.createTempDirectory("source-converter-test")
        val bootstrap = root.resolve("catalog_rows.json")
        bootstrap.writeText(
            """
            {"WPN":{"WPN-2":{"id":"WPN-2","section":9,"row":"| WPN-2 | Blade | 한손검 | 일반 | 1 | 19 | 0 | 베기,균형 |"},"WPN-1":{"id":"WPN-1","section":9,"row":"| WPN-1 | <Sword> | 한손검 | 일반 | 1 | 19 | 0 | 베기,균형 |"}}}
            """.trimIndent()
        )
        val first = root.resolve("first")
        val second = root.resolve("second")

        CanonicalSourceConverter.convert(bootstrap, first)
        CanonicalSourceConverter.convert(bootstrap, second)

        val firstFiles = Files.walk(first).use { stream -> stream.filter(Files::isRegularFile).sorted().toList() }
        val secondFiles = Files.walk(second).use { stream -> stream.filter(Files::isRegularFile).sorted().toList() }
        assertEquals(firstFiles.map { first.relativize(it).toString() }, secondFiles.map { second.relativize(it).toString() })
        firstFiles.forEach { file ->
            val relative = first.relativize(file)
            assertEquals(file.readText(), second.resolve(relative).readText())
        }

        val manifest = first.resolve("catalog-manifest.json").readText()
        assertTrue(manifest.contains("WPN"))
        assertFalse(manifest.contains("catalog_rows.json"))
        val catalog = first.resolve("catalog/WPN.json").readText()
        assertTrue(catalog.contains("WPN-1"))
        assertTrue(catalog.contains("section"))
    }

    @Test
    fun `P1-BT-001 duplicate ids are rejected before files are published`() {
        val root = Files.createTempDirectory("source-converter-invalid")
        val bootstrap = root.resolve("catalog_rows.json")
        bootstrap.writeText("{\"WPN\":{\"WPN-1\":{\"id\":\"WPN-2\",\"section\":1,\"row\":\"| WPN-1 | Name |\"}}}")

        val error = runCatching { CanonicalSourceConverter.convert(bootstrap, root.resolve("out")) }.exceptionOrNull()
        assertTrue(error is IllegalArgumentException)
        assertTrue(error!!.message.orEmpty().contains("id"))
    }

    @Test
    fun `P1-CT-001 non NFC source strings are rejected before writer`() {
        val root = Files.createTempDirectory("source-converter-nfd")
        val bootstrap = root.resolve("catalog_rows.json")
        bootstrap.writeText("{\"WPN\":{\"WPN-1\":{\"id\":\"WPN-1\",\"section\":1,\"row\":\"| WPN-1 | Cafe\\u0301 | 한손검 | 일반 | 1 | 19 | 0 | 베기,균형 |\"}}}")

        val error = runCatching { CanonicalSourceConverter.convert(bootstrap, root.resolve("out")) }.exceptionOrNull()
        assertTrue(error is IllegalArgumentException)
        assertTrue(error!!.message.orEmpty().contains("NFC"))
    }

    @Test
    fun `P1-IT-001 official source converts to the closed 20 kind 3448 row matrix`() {
        val root = Files.createTempDirectory("source-converter-official")
        val bootstrap = projectRoot().resolve("docs/관리데이터/catalog_rows.json")
        val output = root.resolve("source")
        val result = CanonicalSourceConverter.convert(bootstrap, output)

        assertEquals(20, result.fileCount)
        assertEquals(3448, result.rowCount)
        val manifest = JsonParser.parse(output.resolve("catalog-manifest.json").readText()).asObject()
        assertEquals(20, manifest.value("files").asArray().values.size)
        assertEquals(
            setOf("ACC", "ARM", "BOS", "CHAIN", "CTR", "DNG-EVT", "EPRE", "ESUF", "EVT", "ITM", "LEG", "MON", "MPRE", "MSUF", "REL", "SET", "SKL", "SPRE", "SSUF", "WPN"),
            manifest.value("files").asArray().values.map { it.asObject().value("kind").asString() }.toSet()
        )
    }

    @Test
    fun `P1-UT-001 skill resource cost is a typed single multi none or unresolved value`() {
        val root = Files.createTempDirectory("source-converter-resource")
        val bootstrap = root.resolve("catalog_rows.json")
        bootstrap.writeText(
            """
            {"SKL":{"SKL-1":{"id":"SKL-1","section":1,"row":"| SKL-1 | Multi | 범용 | 액티브 | 일반 | 전 클래스 | 기력 14+마력 16 | 8초 | 피해 | 전투 |"},"SKL-2":{"id":"SKL-2","section":1,"row":"| SKL-2 | Choice | 범용 | 액티브 | 일반 | 전 클래스 | 기력/마력 16 | 8초 | 피해 | 전투 |"},"SKL-3":{"id":"SKL-3","section":1,"row":"| SKL-3 | None | 범용 | 패시브 | 일반 | 전 클래스 | - | - | 설명 | 자원 |"}}}
            """.trimIndent()
        )
        val output = root.resolve("source")
        CanonicalSourceConverter.convert(bootstrap, output)
        val records = JsonParser.parse(output.resolve("catalog/SKL.json").readText()).asObject().value("records").asArray().values
            .associateBy { it.asObject().value("id").asString() }
        val multi = records.getValue("SKL-1").asObject().value("definition").asObject().value("resourceCost").asObject()
        assertEquals("MULTI", multi.value("type").asString())
        assertEquals(2, multi.value("costs").asArray().values.size)
        assertEquals("NONE", records.getValue("SKL-3").asObject().value("definition").asObject().value("resourceCost").asObject().value("type").asString())
        val unresolved = records.getValue("SKL-2").asObject()
        assertEquals("UNRESOLVED", unresolved.value("definition").asObject().value("resourceCost").asObject().value("type").asString())
        assertTrue(unresolved.value("definition").asObject().value("unresolved").render().contains("UNRESOLVED_RESOURCE_POLICY"))
        assertTrue(unresolved.value("enabled").asBoolean().not())
    }

    @Test
    fun `P1-CT-001 canonical CSV accepts BOM CRLF quoted empty and unquoted null`() {
        val csv = "\uFEFFid,sourceDisplayName,displayNameOverride,grade,minLevel,tagsJson,enabled,definitionVersion,definitionJson,provenanceSection,provenanceRow,provenanceRawRow\r\n" +
            "WPN-1,Name,\"\",,1,\"[]\",true,1,\"{\"\"unresolved\"\":[]}\",2,3,\"| WPN-1 | Name |\"\r\n"
        val records = parseCanonicalSourceCsv(csv, "catalog/WPN.csv", "WPN")
        assertEquals(1, records.size)
        assertEquals("", records.single().displayNameOverride)
        assertEquals(null, records.single().grade)
        assertEquals(1, records.single().minLevel)
        assertEquals("WPN-1", records.single().id)
    }

    private fun projectRoot(): Path {
        var current = Path.of("").toAbsolutePath().normalize()
        while (current.parent != null) {
            if (Files.isRegularFile(current.resolve("docs/관리데이터/catalog_rows.json"))) return current
            current = current.parent
        }
        error("project root not found")
    }
}
