package com.imsi.mud.content.builder

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.text.Normalizer
import com.imsi.mud.content.ContentKind

data class SourceConversionResult(
    val manifest: Path,
    val fileCount: Int,
    val rowCount: Int,
    val sourceHash: String
)

/** Converts the historical bootstrap catalog once; the builder consumes only this canonical output. */
object CanonicalSourceConverter {
    private val kindOrder = ContentKind.entries.map(ContentKind::wireValue)
    private val expectedFieldCounts = mapOf(
        "ACC" to 6, "ARM" to 8, "BOS" to 9, "CHAIN" to 5, "CTR" to 8, "DNG-EVT" to 6,
        "EPRE" to 5, "ESUF" to 5, "EVT" to 6, "ITM" to 5, "LEG" to 5, "MON" to 7,
        "MPRE" to 5, "MSUF" to 5, "REL" to 6, "SET" to 7, "SKL" to 10, "SPRE" to 6,
        "SSUF" to 6, "WPN" to 8
    )

    fun convert(bootstrap: Path, outputRoot: Path, sourceVersion: String = "catalog.v1"): SourceConversionResult {
        require(Files.isRegularFile(bootstrap)) { "bootstrap source is missing: $bootstrap" }
        require(!Files.exists(outputRoot) || Files.list(outputRoot).use { !it.findAny().isPresent }) {
            "canonical source output must be absent or empty: $outputRoot"
        }
        Files.createDirectories(outputRoot.parent ?: bootstrap.parent)
        val root = if (bootstrap.fileName.toString().endsWith(".csv", ignoreCase = true)) {
            parseBootstrapCsv(readUtf8(bootstrap), bootstrap.toString())
        } else {
            parseSourceJson(readUtf8(bootstrap), bootstrap.toString(), "bootstrap-root").asObject().also { it.requireKeys(kindOrder.toSet(), "bootstrap root") }
        }
        val target = Files.createTempDirectory(outputRoot.parent ?: bootstrap.parent, "canonical-source-")
        try {
            val catalogDir = target.resolve("catalog")
            Files.createDirectories(catalogDir)
            val manifestFiles = mutableListOf<JsonValue>()
            var totalRows = 0
            val sourceKinds = root.values.keys.sortedWith(compareBy({ kindOrder.indexOf(it).let { index -> if (index < 0) Int.MAX_VALUE else index } }, { it }))
            sourceKinds.forEach { kind ->
                require(ContentKind.fromWire(kind) != null) { "unsupported content kind: $kind" }
                val rows = root.value(kind).asObject()
                val records = rows.values.entries.sortedBy { it.key }.mapIndexed { index, (key, value) ->
                    val row = value.asObject()
                    row.requireKeys(setOf("id", "row", "section"), "bootstrap $kind/$key")
                    val id = row.value("id").asString()
                    require(id == key) { "bootstrap key/id mismatch: $key != $id" }
                    require(id == Normalizer.normalize(id, Normalizer.Form.NFC)) { "content id must be NFC: $id" }
                    val rawRow = row.value("row").asString()
                    val section = row.value("section").asInt()
                    parseRecord(kind, id, rawRow, section, index + 1)
                }
                val relativePath = Path.of("catalog", "$kind.json")
                val file = catalogDir.resolve("$kind.json")
                val document = JsonObject(
                    mapOf(
                        "definitionVersion" to JsonNumber(java.math.BigDecimal.ONE),
                        "kind" to JsonString(kind),
                        "records" to JsonArray(records),
                        "schemaVersion" to JsonNumber(java.math.BigDecimal.ONE),
                        "sourceVersion" to JsonString(sourceVersion)
                    )
                )
                writeUtf8(file, document.render())
                manifestFiles += JsonObject(
                    mapOf(
                        "exactFileSha256" to JsonString(sha256(Files.readAllBytes(file))),
                        "kind" to JsonString(kind),
                        "path" to JsonString(relativePath.toString().replace('\\', '/')),
                        "schemaVersion" to JsonNumber(java.math.BigDecimal.ONE),
                        "rowCount" to JsonNumber(java.math.BigDecimal(records.size))
                    )
                )
                totalRows += records.size
            }

            val manifest = JsonObject(
                mapOf(
                    "files" to JsonArray(manifestFiles),
                    "manifestVersion" to JsonNumber(java.math.BigDecimal.ONE),
                    "sourceVersion" to JsonString(sourceVersion)
                )
            )
            val manifestPath = target.resolve("catalog-manifest.json")
            writeUtf8(manifestPath, manifest.render())
            val digest = MessageDigest.getInstance("SHA-256")
            digest.update(Files.readAllBytes(manifestPath))
            manifestFiles.forEach { file -> digest.update(Files.readAllBytes(target.resolve(file.asObject().value("path").asString()))) }
            val sourceHash = digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) }
            moveDirectory(target, outputRoot)
            return SourceConversionResult(outputRoot.resolve("catalog-manifest.json"), manifestFiles.size, totalRows, sourceHash)
        } catch (error: Throwable) {
            target.toFile().deleteRecursively()
            throw error
        }
    }

    private fun parseRecord(kind: String, id: String, rawRow: String, section: Int, rowNumber: Int): JsonObject {
        require(rawRow.trim().startsWith("|") && rawRow.trim().endsWith("|")) { "invalid pipe row for $id" }
        val fields = rawRow.trim().split('|').drop(1).dropLast(1).map(String::trim)
        require(fields.firstOrNull() == id) { "raw row id mismatch for $id" }
        require(fields.size == expectedFieldCounts.getValue(kind)) { "closed field schema mismatch for $kind/$id: ${fields.size}" }
        val displayIndex = if (kind in setOf("EVT", "DNG-EVT", "CHAIN")) 2 else 1
        val displayName = fields.getOrNull(displayIndex).orEmpty()
        require(displayName.isNotBlank()) { "display name is blank for $id" }
        val unresolvedIndices = when (kind) {
            "ACC" -> listOf(5)
            "ITM" -> listOf(4)
            "EPRE", "ESUF" -> listOf(3, 4)
            "MPRE", "MSUF" -> listOf(3)
            "SET" -> listOf(3, 4, 5, 6)
            "SKL" -> listOf(8)
            "SPRE", "SSUF" -> listOf(3, 4, 5)
            "CTR" -> listOf(4, 5, 6)
            "REL" -> listOf(3, 4, 5)
            "EVT", "DNG-EVT" -> listOf(3, 4, 5)
            "CHAIN" -> listOf(3, 4)
            "LEG" -> listOf(2, 3)
            else -> emptyList()
        }
        val missingFields = when (kind) {
            "WPN" -> listOf("enhance", "protection", "slot")
            "ARM" -> listOf("enhance", "protection")
            "MON" -> listOf("resistance", "skills", "AI", "affix")
            "BOS" -> listOf("baseMonster", "phaseDefinitions", "loot")
            else -> emptyList()
        }
        val unresolved = mutableListOf<JsonValue>()
        unresolvedIndices.distinct().forEach { index ->
            unresolved += unresolvedField(kind, index, fields[index])
        }
        missingFields.forEach { field ->
            unresolved += JsonObject(mapOf(
                "code" to JsonString("UNRESOLVED_SOURCE_FIELD"),
                "field" to JsonString(field),
                "reason" to JsonString("the canonical source does not provide this field"),
                "sourceText" to JsonString("")
            ))
        }
        val resourceCost = if (kind == "SKL") resourceCost(fields[6], unresolved) else null
        val gradeIndex = when (kind) {
            "WPN", "ARM", "ACC", "ITM" -> 3
            "EPRE", "ESUF", "MPRE", "MSUF", "SET", "SPRE", "SSUF" -> 2
            "SKL" -> 4
            else -> null
        }
        val grade: JsonValue = gradeIndex?.let { JsonString(fields[it]) } ?: JsonNull
        val definition = definitionFor(kind, fields, unresolved, resourceCost)
        return JsonObject(
            mapOf(
                "definition" to definition,
                "definitionVersion" to JsonNumber(java.math.BigDecimal.ONE),
                "displayNameOverride" to JsonNull,
                "enabled" to JsonBoolean(unresolved.isEmpty()),
                "grade" to grade,
                "id" to JsonString(id),
                "kind" to JsonString(kind),
                "minLevel" to JsonNull,
                "provenance" to JsonObject(
                    mapOf(
                        "rawRow" to JsonString(rawRow),
                        "row" to JsonNumber(java.math.BigDecimal(rowNumber)),
                        "section" to JsonNumber(java.math.BigDecimal(section))
                    )
                ),
                "sourceDisplayName" to JsonString(displayName),
                "tags" to JsonArray(topTags(kind, fields).map(::JsonString))
            )
        )
    }

    private data class CsvCell(val value: String?, val quoted: Boolean)

    private fun parseBootstrapCsv(input: String, sourceFile: String): JsonObject = try {
        parseBootstrapCsvUnchecked(input)
    } catch (error: SourceParseException) {
        throw error
    } catch (error: Exception) {
        val row = Regex("row (\\d+)", RegexOption.IGNORE_CASE).find(error.message.orEmpty())?.groupValues?.get(1)?.toIntOrNull() ?: 1
        val column = Regex("column (\\d+)", RegexOption.IGNORE_CASE).find(error.message.orEmpty())?.groupValues?.get(1)?.toIntOrNull() ?: 1
        val actual = error.message ?: error::class.java.simpleName
        val code = when {
            actual.contains("duplicate JSON key", ignoreCase = true) -> "JSON_DUPLICATE_KEY"
            actual.contains("header", ignoreCase = true) || actual.contains("unknown", ignoreCase = true) -> "SOURCE_UNKNOWN_FIELD"
            actual.contains("NFC", ignoreCase = true) -> "SOURCE_ENCODING_INVALID"
            else -> "SOURCE_INVALID"
        }
        throw SourceParseException(SourceParseDiagnostic(code, code, actual, null, sourceFile, row, column, "bootstrap-csv", "valid bootstrap CSV row", actual))
    }

    private fun parseBootstrapCsvUnchecked(input: String): JsonObject {
        val rows = parseCsv(input)
        require(rows.isNotEmpty()) { "CSV source is empty" }
        val header = rows.first().map { it.value }
        require(header == listOf("kind", "id", "section", "row")) { "CSV header must be kind,id,section,row" }
        val grouped = linkedMapOf<String, LinkedHashMap<String, JsonValue>>()
        rows.drop(1).forEachIndexed { index, cells ->
            require(cells.size == header.size) { "CSV column count mismatch at row ${index + 2}" }
            val kind = cells[0].value ?: error("CSV kind is null at row ${index + 2}")
            val id = cells[1].value ?: error("CSV id is null at row ${index + 2}")
            val section = cells[2].value ?: error("CSV section is null at row ${index + 2}")
            require(section.matches(Regex("[0-9]+"))) { "CSV section must be an ASCII decimal integer at row ${index + 2}" }
            val rawRow = cells[3].value ?: error("CSV row is null at row ${index + 2}")
            val record = JsonObject(mapOf(
                "id" to JsonString(id),
                "row" to JsonString(rawRow),
                "section" to JsonNumber(section.toBigDecimal())
            ))
            val rowsById = grouped.getOrPut(kind) { linkedMapOf() }
            require(rowsById.put(id, record) == null) { "duplicate CSV id: $id" }
        }
        return JsonObject(grouped.mapValues { JsonObject(it.value) })
    }

    private fun parseCsv(input: String): List<List<CsvCell>> {
        val rows = mutableListOf<List<CsvCell>>()
        val fields = mutableListOf<CsvCell>()
        val value = StringBuilder()
        var quoted = false
        var justClosedQuote = false
        var index = 0
        fun finishField() {
            fields += CsvCell(if (!quoted && !justClosedQuote && value.isEmpty()) null else value.toString(), quoted || justClosedQuote)
            value.clear()
            quoted = false
            justClosedQuote = false
        }
        fun finishRow() {
            finishField()
            if (fields.size != 1 || fields[0].value != null) rows += fields.toList()
            fields.clear()
        }
        while (index < input.length) {
            val char = input[index]
            if (quoted) {
                when {
                    char == '"' && input.getOrNull(index + 1) == '"' -> {
                        value.append('"')
                        index += 2
                        continue
                    }
                    char == '"' -> {
                        quoted = false
                        justClosedQuote = true
                    }
                    else -> value.append(char)
                }
            } else if (justClosedQuote) {
                when (char) {
                    ',' -> finishField()
                    '\r', '\n' -> {
                        finishRow()
                        if (char == '\r' && input.getOrNull(index + 1) == '\n') index++
                    }
                    else -> error("invalid CSV character after closing quote at $index")
                }
            } else {
                when (char) {
                    '"' -> {
                        require(value.isEmpty()) { "CSV quote must start a field at $index" }
                        quoted = true
                    }
                    ',' -> finishField()
                    '\r', '\n' -> {
                        finishRow()
                        if (char == '\r' && input.getOrNull(index + 1) == '\n') index++
                    }
                    else -> value.append(char)
                }
            }
            index++
        }
        require(!quoted) { "unterminated CSV quote" }
        if (fields.isNotEmpty() || value.isNotEmpty() || justClosedQuote) finishRow()
        return rows
    }

    private fun unresolvedField(kind: String, index: Int, sourceText: String, codeOverride: String? = null): JsonObject {
        val code = codeOverride ?: when {
            kind == "SKL" && index == 6 -> "UNRESOLVED_RESOURCE_POLICY"
            kind == "SET" -> "UNRESOLVED_SET_TIER_EFFECT"
            kind == "CTR" && index == 4 -> "UNRESOLVED_TIME_DOMAIN"
            kind == "CTR" && index == 5 -> "UNRESOLVED_OBJECTIVE_POLICY"
            kind == "CTR" && index == 6 -> "UNRESOLVED_REWARD_POLICY"
            kind == "REL" && index == 3 -> "UNRESOLVED_ELIGIBILITY_AST"
            kind == "REL" && index == 4 -> "UNRESOLVED_RELATIONSHIP_EFFECTS"
            kind == "REL" && index == 5 -> "UNRESOLVED_FOLLOWUP_POLICY"
            kind in setOf("EVT", "DNG-EVT") && index == 3 -> "UNRESOLVED_ELIGIBILITY_AST"
            kind in setOf("EVT", "DNG-EVT") && index == 4 -> "UNRESOLVED_CHOICE_DEFINITION"
            kind in setOf("EVT", "DNG-EVT") && index == 5 -> "UNRESOLVED_EFFECT_AST"
            kind == "CHAIN" && index == 3 -> "UNRESOLVED_STEP_DEFINITION"
            kind == "CHAIN" && index == 4 -> "UNRESOLVED_EFFECT_AST"
            kind == "LEG" && index == 2 -> "UNRESOLVED_ELIGIBILITY_AST"
            kind == "LEG" && index == 3 -> "UNRESOLVED_SCENARIO_DEFINITION"
            kind in setOf("EPRE", "ESUF", "MPRE", "MSUF") && index == 3 -> "UNRESOLVED_AFFIX_EFFECT_AST"
            kind in setOf("SPRE", "SSUF") && index == 3 -> "UNRESOLVED_AFFIX_MODIFICATION"
            kind in setOf("SPRE", "SSUF") && index == 4 -> "UNRESOLVED_AFFIX_AMOUNT"
            kind in setOf("SPRE", "SSUF") && index == 5 -> "UNRESOLVED_AFFIX_APPLICABILITY"
            else -> "UNRESOLVED_EFFECT_AST"
        }
        val field = when {
            kind == "SKL" && index == 6 -> "resourceCost"
            kind == "SKL" && index == 8 -> "effects"
            kind == "SET" && index in 3..6 -> "tiers[${index - 3}].effects"
            kind == "CTR" && index == 4 -> "duration"
            kind == "CTR" && index == 5 -> "objective"
            kind == "CTR" && index == 6 -> "rewards"
            kind == "REL" && index == 3 -> "eligibilityAST"
            kind == "REL" && index == 4 -> "relationshipEffects"
            kind == "REL" && index == 5 -> "followup"
            kind in setOf("EVT", "DNG-EVT") && index == 3 -> "eligibilityAST"
            kind in setOf("EVT", "DNG-EVT") && index == 4 -> "choices"
            kind in setOf("EVT", "DNG-EVT") && index == 5 -> "effects"
            kind == "CHAIN" && index == 3 -> "steps"
            kind == "CHAIN" && index == 4 -> "finalEffects"
            kind == "LEG" && index == 2 -> "eligibilityAST"
            kind == "LEG" && index == 3 -> "scenario"
            kind in setOf("EPRE", "ESUF", "MPRE", "MSUF") && index == 3 -> "effects"
            kind in setOf("EPRE", "ESUF") && index == 4 -> "allowedEquipmentScope"
            kind in setOf("SPRE", "SSUF") && index == 3 -> "modificationKind"
            kind in setOf("SPRE", "SSUF") && index == 4 -> "amount"
            kind in setOf("SPRE", "SSUF") && index == 5 -> "applicability"
            kind == "ACC" && index == 5 -> "effects"
            kind == "ITM" && index == 4 -> "effects"
            else -> "c$index"
        }
        val reason = when (code) {
            "UNRESOLVED_TIME_DOMAIN" -> "source duration has no executable time domain"
            "UNRESOLVED_RESOURCE_POLICY" -> "source resource text has ambiguous or incomplete cost policy"
            "UNRESOLVED_REWARD_POLICY" -> "source reward text has no typed reward policy"
            "UNRESOLVED_STEP_DEFINITION" -> "source step text has no typed step definition"
            else -> "source text does not provide a typed executable AST"
        }
        return JsonObject(mapOf(
            "code" to JsonString(code),
            "field" to JsonString(field),
            "reason" to JsonString(reason),
            "sourceText" to JsonString(sourceText)
        ))
    }

    private fun resourceCost(raw: String, unresolved: MutableList<JsonValue>): JsonObject {
        val trimmed = raw.replace(" ", "")
        if (trimmed == "-" || trimmed == "없음") {
            return JsonObject(mapOf("type" to JsonString("NONE")))
        }
        if ('/' in trimmed) {
            unresolved += unresolvedField("SKL", 6, raw, "UNRESOLVED_RESOURCE_POLICY")
            return JsonObject(mapOf("type" to JsonString("UNRESOLVED"), "reason" to JsonString("UNRESOLVED_RESOURCE_POLICY")))
        }
        val costs = trimmed.split('+').mapNotNull { token ->
            val match = Regex("^(기력|마력)(\\d+)$").matchEntire(token) ?: return@mapNotNull null
            JsonObject(mapOf(
                "resource" to JsonString(if (match.groupValues[1] == "기력") "STAMINA" else "MANA"),
                "amount" to JsonNumber(match.groupValues[2].toBigDecimal())
            ))
        }
        if (costs.size == trimmed.count { it == '+' } + 1 && costs.isNotEmpty()) {
            return if (costs.size == 1) {
                JsonObject(mapOf("type" to JsonString("SINGLE"), "resource" to costs.single().value("resource"), "amount" to costs.single().value("amount")))
            } else {
                JsonObject(mapOf("type" to JsonString("MULTI"), "costs" to JsonArray(costs)))
            }
        }
        unresolved += unresolvedField("SKL", 6, raw, "UNRESOLVED_RESOURCE_AMOUNT")
        return JsonObject(mapOf("type" to JsonString("UNRESOLVED"), "reason" to JsonString("UNRESOLVED_RESOURCE_AMOUNT")))
    }

    private fun definitionFor(kind: String, fields: List<String>, unresolved: List<JsonValue>, resourceCostValue: JsonValue? = null): JsonObject {
        fun value(index: Int): JsonValue = JsonString(fields[index])
        fun int(index: Int): JsonValue = JsonNumber(java.math.BigDecimal(fields[index].toIntOrNull() ?: error("expected integer at c$index")))
        fun quantityBp(index: Int): JsonValue {
            val number = Regex("[+-]?(?:\\d+(?:\\.\\d+)?)").find(fields[index])?.value?.toBigDecimal()
                ?: error("expected numeric multiplier at c$index")
            return JsonNumber(number.multiply(java.math.BigDecimal(10_000)))
        }
        fun millis(index: Int): JsonValue {
            if (fields[index] == "-") return JsonNull
            val number = Regex("[+-]?\\d+(?:\\.\\d+)?").find(fields[index])?.value?.toBigDecimal()
                ?: error("expected duration at c$index")
            val unit = fields[index].replace(Regex("[0-9+-.\\s]"), "")
            val factor = when (unit) { "초" -> java.math.BigDecimal(1_000); "분" -> java.math.BigDecimal(60_000); "시간" -> java.math.BigDecimal(3_600_000); else -> java.math.BigDecimal.ONE }
            return JsonNumber(number.multiply(factor))
        }
        fun levelBand(index: Int): JsonObject {
            val match = Regex("^([0-9]+)\\s*~\\s*([0-9]+)$").matchEntire(fields[index])
                ?: error("expected level band at c$index")
            return JsonObject(mapOf(
                "min" to JsonNumber(match.groupValues[1].toBigDecimal()),
                "max" to JsonNumber(match.groupValues[2].toBigDecimal())
            ))
        }
        fun tags(index: Int): JsonArray = JsonArray(fields[index].split(',').map(String::trim).filter(String::isNotBlank).map(::JsonString))
        fun effects(): JsonArray = JsonArray(emptyList())
        return when (kind) {
            "WPN" -> JsonObject(mapOf("name" to value(1), "weaponType" to value(2), "grade" to value(3), "recommendedLevel" to int(4), "physicalPower" to int(5), "magicPower" to int(6), "tags" to tags(7), "unresolved" to JsonArray(unresolved)))
            "ARM" -> JsonObject(mapOf("name" to value(1), "slot" to value(2), "grade" to value(3), "recommendedLevel" to int(4), "defense" to int(5), "magicDefense" to int(6), "weightClass" to value(7), "unresolved" to JsonArray(unresolved)))
            "ACC" -> JsonObject(mapOf("name" to value(1), "slot" to value(2), "grade" to value(3), "recommendedLevel" to int(4), "effects" to effects(), "unresolved" to JsonArray(unresolved)))
            "ITM" -> JsonObject(mapOf("name" to value(1), "itemCategory" to value(2), "grade" to value(3), "primaryUseCategory" to value(4), "effects" to effects(), "unresolved" to JsonArray(unresolved)))
            "MON" -> JsonObject(mapOf("name" to value(1), "family" to value(2), "levelBand" to levelBand(3), "threatCoefficientBp" to quantityBp(4), "role" to value(5), "attributes" to tags(6), "unresolved" to JsonArray(unresolved)))
            "BOS" -> JsonObject(mapOf("name" to value(1), "family" to value(2), "recommendedLevel" to int(3), "dungeonRank" to value(4), "archetypeTags" to tags(5), "phaseCount" to int(6), "coreMechanic" to value(7), "rewardTags" to tags(8), "unresolved" to JsonArray(unresolved)))
            "EPRE", "ESUF" -> JsonObject(mapOf("position" to JsonString(if (kind == "EPRE") "PREFIX" else "SUFFIX"), "name" to value(1), "grade" to value(2), "effects" to effects(), "allowedEquipmentScope" to JsonNull, "unresolved" to JsonArray(unresolved)))
            "MPRE", "MSUF" -> JsonObject(mapOf("position" to JsonString(if (kind == "MPRE") "PREFIX" else "SUFFIX"), "name" to value(1), "grade" to value(2), "effects" to effects(), "threatMultiplierBp" to quantityBp(4), "unresolved" to JsonArray(unresolved)))
            "SET" -> JsonObject(mapOf("name" to value(1), "grade" to value(2), "tiers" to JsonArray((3..6).map { index -> JsonObject(mapOf("pieces" to JsonNumber(java.math.BigDecimal(index - 1)), "effects" to effects())) }), "unresolved" to JsonArray(unresolved)))
            "SKL" -> JsonObject(mapOf("name" to value(1), "skillGroup" to value(2), "skillType" to value(3), "grade" to value(4), "classScope" to value(5), "resourceCost" to (resourceCostValue ?: JsonNull), "cooldownCombatMillis" to millis(7), "effects" to effects(), "tags" to tags(9), "unresolved" to JsonArray(unresolved)))
            "SPRE", "SSUF" -> JsonObject(mapOf("position" to JsonString(if (kind == "SPRE") "PREFIX" else "SUFFIX"), "name" to value(1), "grade" to value(2), "modificationKind" to JsonNull, "amount" to JsonNull, "applicability" to JsonNull, "unresolved" to JsonArray(unresolved)))
            "CTR" -> JsonObject(mapOf("name" to value(1), "category" to value(2), "contractRank" to value(3), "duration" to JsonNull, "objective" to JsonNull, "rewards" to JsonArray(emptyList()), "featureTags" to tags(7), "unresolved" to JsonArray(unresolved)))
            "REL" -> JsonObject(mapOf("name" to value(1), "category" to value(2), "eligibilityAST" to JsonNull, "relationshipEffects" to JsonArray(emptyList()), "followup" to JsonNull, "unresolved" to JsonArray(unresolved)))
            "EVT", "DNG-EVT" -> JsonObject(mapOf("category" to value(1), "name" to value(2), "eligibilityAST" to JsonNull, "choices" to JsonArray(emptyList()), "effects" to effects(), "unresolved" to JsonArray(unresolved)))
            "CHAIN" -> JsonObject(mapOf("category" to value(1), "name" to value(2), "steps" to JsonArray(emptyList()), "finalEffects" to effects(), "unresolved" to JsonArray(unresolved)))
            "LEG" -> JsonObject(mapOf("name" to value(1), "eligibilityAST" to JsonNull, "scenario" to JsonNull, "chronicleTags" to tags(4), "unresolved" to JsonArray(unresolved)))
            else -> error("unsupported content kind: $kind")
        }
    }

    private fun topTags(kind: String, fields: List<String>): List<String> = when (kind) {
        "WPN" -> fields[7].split(',').map(String::trim)
        "MON" -> fields[6].split(',').map(String::trim)
        "BOS" -> fields[5].split(',').map(String::trim) + fields[8].split(',').map(String::trim)
        "SKL" -> fields[9].split(',').map(String::trim)
        "CTR" -> fields[7].split(',').map(String::trim)
        "LEG" -> fields[4].split(',').map(String::trim)
        else -> emptyList()
    }.filter(String::isNotBlank)

    private fun typedField(value: String): JsonValue {
        val trimmed = value.trim()
        val range = Regex("^([+-]?\\d+(?:\\.\\d+)?)\\s*~\\s*([+-]?\\d+(?:\\.\\d+)?)$").matchEntire(trimmed)
        if (range != null) {
            return JsonObject(mapOf(
                "kind" to JsonString("range"),
                "lower" to JsonNumber(range.groupValues[1].toBigDecimal()),
                "upper" to JsonNumber(range.groupValues[2].toBigDecimal()),
                "raw" to JsonString(value)
            ))
        }
        val quantity = Regex("^([+-]?\\d+(?:\\.\\d+)?)\\s*([%+]?[A-Za-z가-힣]+|%)$").matchEntire(trimmed)
        if (quantity != null) {
            return JsonObject(mapOf(
                "kind" to JsonString("quantity"),
                "number" to JsonNumber(quantity.groupValues[1].toBigDecimal()),
                "unit" to JsonString(quantity.groupValues[2]),
                "raw" to JsonString(value)
            ))
        }
        val number = trimmed.toBigDecimalOrNull()
        if (number != null) {
            return JsonObject(mapOf("kind" to JsonString("number"), "number" to JsonNumber(number), "raw" to JsonString(value)))
        }
        return JsonObject(mapOf("kind" to JsonString("text"), "raw" to JsonString(value), "value" to JsonString(value)))
    }

    private fun writeUtf8(path: Path, content: String) {
        Files.createDirectories(path.parent)
        Files.writeString(path, content + "\n", StandardCharsets.UTF_8)
    }

    private fun moveDirectory(source: Path, target: Path) {
        Files.move(source, target, StandardCopyOption.ATOMIC_MOVE)
    }

    internal fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes)
        .joinToString("") { "%02x".format(it.toInt() and 0xff) }
}
