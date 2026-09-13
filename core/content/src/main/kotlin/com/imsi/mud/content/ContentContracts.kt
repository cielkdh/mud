package com.imsi.mud.content

import java.math.BigDecimal
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.text.Normalizer
import java.util.Collections
import java.util.LinkedHashMap
import java.util.TreeMap

/** Stable, human-authored content identity. IDs are deliberately not silently normalized. */
@JvmInline
value class ContentId(val value: String) {
    init {
        require(value.isNotBlank()) { "content id must not be blank" }
        require(value == Normalizer.normalize(value, Normalizer.Form.NFC)) { "content id must be NFC" }
    }
}

@JvmInline
value class AssetId(val value: String) {
    init {
        require(value.isNotBlank()) { "asset id must not be blank" }
        require(value == Normalizer.normalize(value, Normalizer.Form.NFC)) { "asset id must be NFC" }
    }
}

enum class ContentKind(val wireValue: String) {
    ACCESSORY("ACC"), ARMOR("ARM"), BOSS("BOS"), CHAIN("CHAIN"), CONTRACT("CTR"),
    DUNGEON_EVENT("DNG-EVT"), EQUIPMENT_PREFIX("EPRE"), EQUIPMENT_SUFFIX("ESUF"),
    EVENT("EVT"), ITEM("ITM"), LEGEND("LEG"), MONSTER("MON"), MONSTER_PREFIX("MPRE"),
    MONSTER_SUFFIX("MSUF"), RELIC("REL"), SET("SET"), SKILL("SKL"), SKILL_PREFIX("SPRE"),
    SKILL_SUFFIX("SSUF"), WEAPON("WPN");

    companion object {
        fun fromWire(value: String): ContentKind? = entries.firstOrNull { it.wireValue == value }
    }
}

data class SourceLocation(val sourceFile: String, val row: Int) : Comparable<SourceLocation> {
    init {
        require(sourceFile.isNotBlank()) { "source file must not be blank" }
        require(row >= 0) { "source row must not be negative" }
    }

    override fun compareTo(other: SourceLocation): Int = compareValuesBy(this, other, SourceLocation::sourceFile, SourceLocation::row)
}

enum class DiagnosticSeverity { ERROR, WARNING }

data class ContentDiagnostic(
    val code: String,
    val message: String,
    val location: SourceLocation,
    val severity: DiagnosticSeverity = DiagnosticSeverity.ERROR,
    val subject: String? = null,
    val messageKey: String = code,
    val column: Int? = null,
    val field: String? = null,
    val expected: String? = null,
    val actual: String? = null,
    val relatedLocations: List<SourceLocation> = emptyList()
) : Comparable<ContentDiagnostic> {
    val sourceId: String? get() = subject
    val sourceFile: String get() = location.sourceFile
    val row: Int get() = location.row

    override fun compareTo(other: ContentDiagnostic): Int = compareValuesBy(
        this,
        other,
        ContentDiagnostic::severity,
        ContentDiagnostic::code,
        ContentDiagnostic::subject,
        ContentDiagnostic::sourceFile,
        ContentDiagnostic::row,
        ContentDiagnostic::field
    )
}

data class ContentSourceTemplate(
    val location: SourceLocation,
    val id: String,
    val kind: String,
    val sourceDisplayName: String,
    val definitionVersion: Int,
    val definitionJson: String,
    val displayNameOverride: String? = null,
    val grade: String? = null,
    val minLevel: Int? = null,
    val tags: List<String> = emptyList(),
    val enabled: Boolean = true
)

class ContentTemplate(
    val id: ContentId,
    val kind: ContentKind,
    val sourceDisplayName: String,
    val displayNameOverride: String? = null,
    val grade: String? = null,
    val minLevel: Int? = null,
    tags: List<String> = emptyList(),
    val definitionVersion: Int,
    definitionJson: String,
    val enabled: Boolean = true
) {
    val tags: List<String> = Collections.unmodifiableList(tags.map(::requireNfc).distinct().sorted())
    val definitionJson: String = CanonicalJson.canonicalize(definitionJson)
    val effectiveDisplayName: String get() = displayNameOverride ?: sourceDisplayName

    init {
        requireNfc(sourceDisplayName)
        require(definitionVersion == SUPPORTED_DEFINITION_VERSION) { "unsupported definition version: $definitionVersion" }
        require(minLevel == null || minLevel >= 0) { "min level must not be negative" }
        displayNameOverride?.let(::requireNfc)
        grade?.let(::requireNfc)
    }

    override fun equals(other: Any?): Boolean = other is ContentTemplate &&
        id == other.id && kind == other.kind && sourceDisplayName == other.sourceDisplayName &&
        displayNameOverride == other.displayNameOverride && grade == other.grade && minLevel == other.minLevel &&
        tags == other.tags && definitionVersion == other.definitionVersion && definitionJson == other.definitionJson && enabled == other.enabled

    override fun hashCode(): Int = listOf(
        id, kind, sourceDisplayName, displayNameOverride, grade, minLevel, tags, definitionVersion, definitionJson, enabled
    ).hashCode()

    override fun toString(): String = "ContentTemplate(id=${id.value}, kind=${kind.wireValue})"

    fun canonicalJson(): String = CanonicalJson.objectOf(
        "definition" to definitionJson,
        "definitionVersion" to definitionVersion.toString(),
        "displayNameOverride" to (displayNameOverride?.let(CanonicalJson::string) ?: "null"),
        "enabled" to enabled.toString(),
        "grade" to (grade?.let(CanonicalJson::string) ?: "null"),
        "id" to CanonicalJson.string(id.value),
        "kind" to CanonicalJson.string(kind.wireValue),
        "minLevel" to (minLevel?.toString() ?: "null"),
        "sourceDisplayName" to CanonicalJson.string(sourceDisplayName),
        "tags" to tags.joinToString(prefix = "[", postfix = "]", separator = ",", transform = CanonicalJson::string)
    )

    companion object {
        const val SUPPORTED_DEFINITION_VERSION = 1
    }
}

data class ContentIdentity(
    val contentVersion: String,
    val balanceVersion: String,
    val logicalContentHash: String,
    val schemaVersion: Int
) {
    init {
        requireNfc(contentVersion)
        requireNfc(balanceVersion)
        require(logicalContentHash.matches(SHA_256_REGEX)) { "logical content hash must be lowercase SHA-256" }
        require(schemaVersion == ContentSnapshot.SCHEMA_VERSION) { "unsupported content schema version: $schemaVersion" }
    }
}

/**
 * Immutable simulation input. It deliberately contains neither repository handles nor asset paths/bytes.
 */
class ContentSnapshot private constructor(
    val identity: ContentIdentity,
    templates: Map<ContentId, ContentTemplate>
) {
    val templatesById: Map<ContentId, ContentTemplate> = Collections.unmodifiableMap(LinkedHashMap(templates))

    fun definitionRefFor(id: ContentId): ContentDefinitionRef? = templatesById[id]?.let(ContentHasher::definitionRef)

    companion object {
        const val SCHEMA_VERSION = 1

        fun create(
            contentVersion: String,
            balanceVersion: String,
            schemaVersion: Int,
            templates: Iterable<ContentTemplate>
        ): ContentSnapshot {
            require(schemaVersion == SCHEMA_VERSION) { "unsupported content schema version: $schemaVersion" }
            val indexed = linkedMapOf<ContentId, ContentTemplate>()
            templates.forEach { template ->
                require(indexed.put(template.id, template) == null) { "duplicate content id: ${template.id.value}" }
            }
            val sorted = indexed.toSortedMap(compareBy(ContentId::value))
            val identity = ContentIdentity(
                contentVersion = contentVersion,
                balanceVersion = balanceVersion,
                logicalContentHash = ContentHasher.logicalContentHash(sorted.values),
                schemaVersion = schemaVersion
            )
            return ContentSnapshot(identity, sorted)
        }

        fun from(identity: ContentIdentity, templatesById: Map<ContentId, ContentTemplate>): ContentSnapshot {
            require(identity.schemaVersion == SCHEMA_VERSION) { "unsupported content schema version" }
            templatesById.forEach { (id, template) ->
                require(id == template.id) { "content snapshot key does not match template id: ${id.value}" }
            }
            val sorted = templatesById.toSortedMap(compareBy(ContentId::value))
            val actualHash = ContentHasher.logicalContentHash(sorted.values)
            require(actualHash == identity.logicalContentHash) { "content snapshot logical hash does not match templates" }
            return ContentSnapshot(identity, sorted)
        }

        fun emptyForTest(): ContentSnapshot = ContentSnapshot(
            ContentIdentity("test-content.v1", "test-balance.v1", "0".repeat(64), SCHEMA_VERSION),
            emptyMap()
        )
    }
}

data class ContentImportResult(
    val templates: List<ContentTemplate>,
    val diagnostics: List<ContentDiagnostic>,
    val sourceLocationsById: Map<ContentId, SourceLocation>
) {
    val isValid: Boolean get() = diagnostics.none { it.severity == DiagnosticSeverity.ERROR }
}

/** Pure source-record importer: parsing files belongs to the build tool, not to this module. */
object CatalogImporter {
    fun import(records: Iterable<ContentSourceTemplate>): ContentImportResult {
        val diagnostics = mutableListOf<ContentDiagnostic>()
        val templates = mutableListOf<ContentTemplate>()
        val seen = linkedMapOf<String, ContentSourceTemplate>()
        records.sortedBy(ContentSourceTemplate::location).forEach { record ->
            val id = runCatching { ContentId(record.id) }.getOrElse { error ->
                diagnostics += diagnostic(
                    if (record.id != Normalizer.normalize(record.id, Normalizer.Form.NFC)) "NON_NFC_CONTENT_ID" else "INVALID_CONTENT_ID",
                    error.message.orEmpty(), record
                )
                return@forEach
            }
            val first = seen.putIfAbsent(id.value, record)
            if (first != null) {
                diagnostics += diagnostic(
                    "DUPLICATE_CONTENT_ID",
                    "duplicate content id: ${id.value}",
                    record,
                    relatedLocations = listOf(first.location, record.location).sorted()
                )
                return@forEach
            }
            val kind = ContentKind.fromWire(record.kind)
            if (kind == null) {
                diagnostics += diagnostic("UNSUPPORTED_CONTENT_KIND", "unsupported content kind: ${record.kind}", record)
                return@forEach
            }
            if (record.definitionVersion != ContentTemplate.SUPPORTED_DEFINITION_VERSION) {
                diagnostics += diagnostic("UNSUPPORTED_DEFINITION_VERSION", "unsupported definition version: ${record.definitionVersion}", record)
                return@forEach
            }
            runCatching {
                ContentTemplate(
                    id = id,
                    kind = kind,
                    sourceDisplayName = record.sourceDisplayName,
                    displayNameOverride = record.displayNameOverride,
                    grade = record.grade,
                    minLevel = record.minLevel,
                    tags = record.tags,
                    definitionVersion = record.definitionVersion,
                    definitionJson = record.definitionJson,
                    enabled = record.enabled
                )
            }.onSuccess(templates::add).onFailure { error ->
                diagnostics += diagnostic("INVALID_TEMPLATE", error.message.orEmpty(), record)
            }
        }
        return ContentImportResult(
            templates = templates.sortedBy { it.id.value },
            diagnostics = diagnostics.sorted(),
            sourceLocationsById = templates.associate { template ->
                template.id to requireNotNull(seen[template.id.value]).location
            }.toSortedMap(compareBy(ContentId::value))
        )
    }

    private fun diagnostic(
        code: String,
        message: String,
        record: ContentSourceTemplate,
        relatedLocations: List<SourceLocation> = emptyList()
    ) = ContentDiagnostic(
        code = code,
        message = message,
        location = record.location,
        subject = record.id,
        relatedLocations = relatedLocations
    )
}

/** Pure validation entrypoint for callers that construct templates without importing source records. */
object ContentValidator {
    fun validate(templates: Iterable<ContentTemplate>): List<ContentDiagnostic> {
        val result = mutableListOf<ContentDiagnostic>()
        val seen = mutableSetOf<ContentId>()
        templates.sortedBy { it.id.value }.forEachIndexed { index, template ->
            if (!seen.add(template.id)) {
                result += ContentDiagnostic(
                    "DUPLICATE_CONTENT_ID",
                    "duplicate content id: ${template.id.value}",
                    SourceLocation("<memory>", index),
                    subject = template.id.value
                )
            }
        }
        return result.sorted()
    }
}

object ContentHasher {
    fun sha256(value: String): String = sha256(value.toByteArray(StandardCharsets.UTF_8))

    fun definitionRef(template: ContentTemplate): ContentDefinitionRef = ContentDefinitionRef(
        kind = template.kind,
        id = template.id,
        definitionVersion = template.definitionVersion,
        definitionHash = sha256(
            CanonicalJson.objectOf(
                "definition" to CanonicalJson.definitionHashProjection(template.definitionJson),
                "definitionVersion" to template.definitionVersion.toString(),
                "enabled" to template.enabled.toString(),
                "grade" to (template.grade?.let(CanonicalJson::string) ?: "null"),
                "id" to CanonicalJson.string(template.id.value),
                "kind" to CanonicalJson.string(template.kind.wireValue),
                "minLevel" to (template.minLevel?.toString() ?: "null"),
                "tags" to template.tags.joinToString(prefix = "[", postfix = "]", separator = ",", transform = CanonicalJson::string)
            )
        )
    )

    fun logicalContentHash(templates: Iterable<ContentTemplate>): String {
        val entries = templates.sortedWith(compareBy<ContentTemplate>({ it.kind.wireValue }, { it.id.value }))
            .joinToString(",") { it.canonicalJson() }
        return sha256("{\"templates\":[$entries]}")
    }

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes)
        .joinToString("") { "%02x".format(it.toInt() and 0xff) }
}

internal object CanonicalJson {
    fun parse(input: String): JsonValue {
        val parser = Parser(input)
        val value = parser.parseValue()
        parser.requireEnd()
        return value
    }

    fun canonicalize(input: String): String {
        return parse(input).render()
    }

    /**
     * C25 compatibility hashes cover executable gameplay data, not presentation or
     * source-review text that happens to be carried inside the canonical definition.
     */
    fun definitionHashProjection(input: String): String {
        val root = parse(input) as? JsonObject ?: return canonicalize(input)
        val projected = root.values
            .filterKeys { it != "name" }
            .mapValues { (key, value) -> if (key == "unresolved") projectUnresolved(value) else value }
            .toSortedMap()
        return JsonObject(projected).render()
    }

    private fun projectUnresolved(value: JsonValue): JsonValue = when (value) {
        is JsonArray -> JsonArray(value.values.map(::projectUnresolved))
        is JsonObject -> JsonObject(value.values.filterKeys { it !in setOf("reason", "sourceText") }.toSortedMap())
        else -> value
    }

    fun string(value: String): String = JsonString(requireNfcText(value)).render()

    /** Values are already canonical JSON snippets. */
    fun objectOf(vararg fields: Pair<String, String>): String = fields
        .sortedBy { it.first }
        .joinToString(prefix = "{", postfix = "}", separator = ",") { (key, value) -> string(key) + ":" + value }

    sealed interface JsonValue { fun render(): String }
    data class JsonObject(val values: Map<String, JsonValue>) : JsonValue {
        override fun render(): String = values.entries.joinToString(separator = ",", prefix = "{", postfix = "}") { (key, value) ->
            string(key) + ":" + value.render()
        }
    }
    data class JsonArray(val values: List<JsonValue>) : JsonValue {
        override fun render(): String = values.joinToString(separator = ",", prefix = "[", postfix = "]") { it.render() }
    }
    data class JsonString(val value: String) : JsonValue {
        override fun render(): String = buildString {
            append('"')
            value.forEach { char ->
                when (char) {
                    '"' -> append("\\\"")
                    '\\' -> append("\\\\")
                    '\b' -> append("\\b")
                    '\u000c' -> append("\\f")
                    '\n' -> append("\\n")
                    '\r' -> append("\\r")
                    '\t' -> append("\\t")
                    else -> if (char.code < 0x20) append("\\u%04x".format(char.code)) else append(char)
                }
            }
            append('"')
        }
    }
    data class JsonNumber(val value: BigDecimal) : JsonValue {
        override fun render(): String = value.stripTrailingZeros().let { if (it.signum() == 0) "0" else it.toPlainString() }
    }
    data class JsonLiteral(val value: String) : JsonValue { override fun render(): String = value }

    private class Parser(private val input: String) {
        private var position = 0

        fun parseValue(): JsonValue {
            whitespace()
            val token = peek()
            return when {
                token == '{' -> objectValue()
                token == '[' -> arrayValue()
                token == '"' -> JsonString(requireNfcText(stringValue()))
                token == 't' -> literal("true")
                token == 'f' -> literal("false")
                token == 'n' -> literal("null")
                token == '-' || (token != null && token in '0'..'9') -> numberValue()
                else -> fail("expected JSON value")
            }
        }

        fun requireEnd() {
            whitespace()
            if (position != input.length) fail("trailing JSON input")
        }

        private fun objectValue(): JsonObject {
            consume('{')
            whitespace()
            val values = TreeMap<String, JsonValue>()
            if (tryConsume('}')) return JsonObject(values)
            while (true) {
                whitespace()
                val key = requireNfcText(stringValue())
                whitespace(); consume(':')
                val value = parseValue()
                require(values.put(key, value) == null) { "duplicate JSON key: $key" }
                whitespace()
                if (tryConsume('}')) return JsonObject(values)
                consume(',')
            }
        }

        private fun arrayValue(): JsonArray {
            consume('[')
            whitespace()
            val values = mutableListOf<JsonValue>()
            if (tryConsume(']')) return JsonArray(values)
            while (true) {
                values += parseValue()
                whitespace()
                if (tryConsume(']')) return JsonArray(values)
                consume(',')
            }
        }

        private fun stringValue(): String {
            consume('"')
            val output = StringBuilder()
            while (position < input.length) {
                val char = input[position++]
                when (char) {
                    '"' -> return output.toString()
                    '\\' -> output.append(escape())
                    else -> {
                        if (char.code < 0x20) fail("control character in JSON string")
                        output.append(char)
                    }
                }
            }
            fail("unterminated JSON string")
        }

        private fun escape(): Char {
            if (position >= input.length) fail("unterminated JSON escape")
            return when (val escaped = input[position++]) {
                '"', '\\', '/' -> escaped
                'b' -> '\b'
                'f' -> '\u000c'
                'n' -> '\n'
                'r' -> '\r'
                't' -> '\t'
                'u' -> {
                    if (position + 4 > input.length) fail("short JSON unicode escape")
                    val code = input.substring(position, position + 4).toIntOrNull(16) ?: fail("invalid JSON unicode escape")
                    position += 4
                    code.toChar()
                }
                else -> fail("invalid JSON escape: $escaped")
            }
        }

        private fun numberValue(): JsonNumber {
            val start = position
            tryConsume('-')
            if (tryConsume('0')) {
                // no leading digits after zero
            } else {
                require(isNonZeroDigit()) { "invalid JSON number" }
                while (isDigit()) position++
            }
            if (tryConsume('.')) {
                require(isDigit()) { "invalid JSON decimal" }
                while (isDigit()) position++
            }
            if (peek() == 'e' || peek() == 'E') {
                position++
                if (peek() == '+' || peek() == '-') position++
                require(isDigit()) { "invalid JSON exponent" }
                while (isDigit()) position++
            }
            return JsonNumber(input.substring(start, position).toBigDecimal())
        }

        private fun literal(value: String): JsonLiteral {
            require(input.regionMatches(position, value, 0, value.length)) { "invalid JSON literal" }
            position += value.length
            return JsonLiteral(value)
        }

        private fun whitespace() { while (peek()?.isWhitespace() == true) position++ }
        private fun peek(): Char? = input.getOrNull(position)
        private fun isDigit(): Boolean = peek()?.let { it in '0'..'9' } == true
        private fun isNonZeroDigit(): Boolean = peek()?.let { it in '1'..'9' } == true
        private fun consume(char: Char) { require(tryConsume(char)) { "expected '$char'" } }
        private fun tryConsume(char: Char): Boolean = if (peek() == char) { position++; true } else false
        private fun fail(message: String): Nothing = throw IllegalArgumentException("invalid JSON: $message at $position")
    }
}

private val SHA_256_REGEX = Regex("[0-9a-f]{64}")

internal fun requireNfc(value: String): String {
    require(value.isNotBlank()) { "value must not be blank" }
    return requireNfcText(value)
}

internal fun requireNfcText(value: String): String {
    require(value == Normalizer.normalize(value, Normalizer.Form.NFC)) { "value must be NFC" }
    return value
}
