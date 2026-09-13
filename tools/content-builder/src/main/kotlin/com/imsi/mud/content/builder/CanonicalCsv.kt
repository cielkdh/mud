package com.imsi.mud.content.builder

import com.imsi.mud.content.ContentSourceTemplate
import com.imsi.mud.content.SourceLocation
import java.text.Normalizer

private data class CanonicalCsvCell(val value: String?, val quoted: Boolean)

private val canonicalCsvHeader = listOf(
    "id", "sourceDisplayName", "displayNameOverride", "grade", "minLevel", "tagsJson", "enabled",
    "definitionVersion", "definitionJson", "provenanceSection", "provenanceRow", "provenanceRawRow"
)

internal fun parseCanonicalSourceCsv(input: String, sourceFile: String, kind: String): List<ContentSourceTemplate> = try {
    parseCanonicalSourceCsvUnchecked(input, sourceFile, kind)
} catch (error: SourceParseException) {
    throw error
} catch (error: Exception) {
    val row = Regex(":(\\d+)").find(error.message.orEmpty())?.groupValues?.get(1)?.toIntOrNull() ?: 1
    val column = Regex("column (\\d+)", RegexOption.IGNORE_CASE).find(error.message.orEmpty())?.groupValues?.get(1)?.toIntOrNull() ?: 1
    val actual = error.message ?: error::class.java.simpleName
    val code = when {
        actual.contains("duplicate JSON key", ignoreCase = true) -> "JSON_DUPLICATE_KEY"
        actual.contains("unknown", ignoreCase = true) || actual.contains("header", ignoreCase = true) -> "SOURCE_UNKNOWN_FIELD"
        actual.contains("NFC", ignoreCase = true) -> "SOURCE_ENCODING_INVALID"
        else -> "SOURCE_INVALID"
    }
    throw SourceParseException(SourceParseDiagnostic(code, code, actual, null, sourceFile, row, column, "canonical-csv", "valid canonical CSV row", actual))
}

private fun parseCanonicalSourceCsvUnchecked(input: String, sourceFile: String, kind: String): List<ContentSourceTemplate> {
    val rows = parseCanonicalCsv(if (input.startsWith('\uFEFF')) input.drop(1) else input)
    require(rows.isNotEmpty()) { "CSV source is empty: $sourceFile" }
    require(rows.first().map(CanonicalCsvCell::value) == canonicalCsvHeader) {
        "CSV header must be ${canonicalCsvHeader.joinToString(",")}: $sourceFile"
    }
    return rows.drop(1).mapIndexed { index, cells ->
        require(cells.size == canonicalCsvHeader.size) { "CSV column count mismatch at ${sourceFile}:${index + 2}" }
        fun required(column: Int): String = cells[column].value ?: error("CSV null at ${sourceFile}:${index + 2}:${canonicalCsvHeader[column]}")
        fun optional(column: Int): String? = cells[column].value
        val id = required(0)
        val sourceDisplayName = required(1)
        fun unsignedInt(column: Int): Int? {
            val raw = optional(column) ?: return null
            require(raw.matches(Regex("[0-9]+"))) { "CSV ${canonicalCsvHeader[column]} must be an unsigned ASCII integer" }
            return raw.toIntOrNull() ?: error("CSV ${canonicalCsvHeader[column]} is out of range")
        }
        val minLevel = unsignedInt(4)
        val tags = parseSourceJson(required(5), sourceFile, "tagsJson").asArray().values.map(JsonValue::asString)
        val enabled = required(6).let { when (it) { "true" -> true; "false" -> false; else -> error("CSV enabled must be true or false") } }
        val definitionVersion = required(7).let {
            require(it.matches(Regex("[0-9]+"))) { "CSV definitionVersion must be an unsigned ASCII integer" }
            it.toIntOrNull() ?: error("CSV definitionVersion is out of range")
        }
        val definitionJson = parseSourceJson(required(8), sourceFile, "definitionJson").asObject().render()
        val section = required(9).let {
            require(it.matches(Regex("[0-9]+"))) { "CSV provenanceSection must be an unsigned ASCII integer" }
            it.toIntOrNull() ?: error("CSV provenanceSection is out of range")
        }
        val row = required(10).let {
            require(it.matches(Regex("[0-9]+"))) { "CSV provenanceRow must be an unsigned ASCII integer" }
            it.toIntOrNull() ?: error("CSV provenanceRow is out of range")
        }
        required(11)
        listOf(id, sourceDisplayName, optional(2), optional(3), kind).filterNotNull().forEach {
            require(it == Normalizer.normalize(it, Normalizer.Form.NFC)) { "CSV string is not NFC at ${sourceFile}:${index + 2}" }
        }
        ContentSourceTemplate(
            location = SourceLocation(sourceFile, row),
            id = id,
            kind = kind,
            sourceDisplayName = sourceDisplayName,
            definitionVersion = definitionVersion,
            definitionJson = definitionJson,
            displayNameOverride = optional(2),
            grade = optional(3),
            minLevel = minLevel,
            tags = tags,
            enabled = enabled
        )
    }
}

private fun parseCanonicalCsv(input: String): List<List<CanonicalCsvCell>> {
    val rows = mutableListOf<List<CanonicalCsvCell>>()
    val fields = mutableListOf<CanonicalCsvCell>()
    val value = StringBuilder()
    var quoted = false
    var justClosedQuote = false
    var index = 0
    fun finishField() {
        fields += CanonicalCsvCell(if (!quoted && !justClosedQuote && value.isEmpty()) null else value.toString(), quoted || justClosedQuote)
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
                char == '"' && input.getOrNull(index + 1) == '"' -> { value.append('"'); index += 2; continue }
                char == '"' -> { quoted = false; justClosedQuote = true }
                else -> value.append(char)
            }
        } else if (justClosedQuote) {
            when (char) {
                ',' -> finishField()
                '\r', '\n' -> { finishRow(); if (char == '\r' && input.getOrNull(index + 1) == '\n') index++ }
                else -> error("invalid CSV character after closing quote at $index")
            }
        } else {
            when (char) {
                '"' -> { require(value.isEmpty()) { "CSV quote must start a field at $index" }; quoted = true }
                ',' -> finishField()
                '\r', '\n' -> { finishRow(); if (char == '\r' && input.getOrNull(index + 1) == '\n') index++ }
                else -> value.append(char)
            }
        }
        index++
    }
    require(!quoted) { "unterminated CSV quote" }
    if (fields.isNotEmpty() || value.isNotEmpty() || justClosedQuote) finishRow()
    return rows
}
