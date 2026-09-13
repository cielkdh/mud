package com.imsi.mud.content.builder

import java.math.BigDecimal
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.text.Normalizer

internal sealed interface JsonValue {
    fun render(): String
}

internal data class JsonObject(val values: Map<String, JsonValue>) : JsonValue {
    override fun render(): String = values.toSortedMap().entries.joinToString(separator = ",", prefix = "{", postfix = "}") { (key, value) ->
        JsonString(key).render() + ":" + value.render()
    }
}

internal data class JsonArray(val values: List<JsonValue>) : JsonValue {
    override fun render(): String = values.joinToString(separator = ",", prefix = "[", postfix = "]") { it.render() }
}

internal data class JsonString(val value: String) : JsonValue {
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

internal data class JsonNumber(val value: BigDecimal) : JsonValue {
    override fun render(): String = value.stripTrailingZeros().let { if (it.signum() == 0) "0" else it.toPlainString() }
}

internal data class JsonBoolean(val value: Boolean) : JsonValue {
    override fun render(): String = value.toString()
}

internal data object JsonNull : JsonValue {
    override fun render(): String = "null"
}

internal object JsonParser {
    fun parse(input: String): JsonValue = Parser(input).parseDocument()

    private class Parser(private val input: String) {
        private var position = 0

        fun parseDocument(): JsonValue {
            val value = parseValue()
            whitespace()
            require(position == input.length) { "trailing JSON input at $position" }
            return value
        }

        private fun parseValue(): JsonValue {
            whitespace()
            return when (peek()) {
                '{' -> parseObject()
                '[' -> parseArray()
                '"' -> JsonString(nfc(parseString()))
                't' -> parseLiteral("true", JsonBoolean(true))
                'f' -> parseLiteral("false", JsonBoolean(false))
                'n' -> parseLiteral("null", JsonNull)
                '-', in '0'..'9' -> parseNumber()
                else -> fail("expected JSON value")
            }
        }

        private fun parseObject(): JsonObject {
            consume('{')
            whitespace()
            val values = linkedMapOf<String, JsonValue>()
            if (tryConsume('}')) return JsonObject(values)
            while (true) {
                whitespace()
                val key = nfc(parseString())
                whitespace()
                consume(':')
                check(values.put(key, parseValue()) == null) { "duplicate JSON key: $key" }
                whitespace()
                if (tryConsume('}')) return JsonObject(values)
                consume(',')
            }
        }

        private fun parseArray(): JsonArray {
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

        private fun parseString(): String {
            consume('"')
            val result = StringBuilder()
            while (position < input.length) {
                when (val char = input[position++]) {
                    '"' -> {
                        val value = result.toString()
                        require(value.indices.all { index ->
                            val char = value[index]
                            !char.isSurrogate() || (char.isHighSurrogate() && index + 1 < value.length && value[index + 1].isLowSurrogate()) || (char.isLowSurrogate() && index > 0 && value[index - 1].isHighSurrogate())
                        }) { "unpaired surrogate in JSON string" }
                        return value
                    }
                    '\\' -> result.append(parseEscape())
                    else -> {
                        require(char.code >= 0x20) { "control character in JSON string at ${position - 1}" }
                        result.append(char)
                    }
                }
            }
            fail("unterminated string")
        }

        private fun parseEscape(): Char {
            require(position < input.length) { "unterminated escape" }
            return when (val escaped = input[position++]) {
                '"', '\\', '/' -> escaped
                'b' -> '\b'
                'f' -> '\u000c'
                'n' -> '\n'
                'r' -> '\r'
                't' -> '\t'
                'u' -> {
                    require(position + 4 <= input.length) { "short unicode escape" }
                    val value = input.substring(position, position + 4).toIntOrNull(16)
                    require(value != null) { "invalid unicode escape" }
                    position += 4
                    value.toChar()
                }
                else -> fail("invalid escape: $escaped")
            }
        }

        private fun parseNumber(): JsonNumber {
            val start = position
            tryConsume('-')
            if (tryConsume('0')) {
                require(!isDigit(peek())) { "leading zero in JSON number" }
            } else {
                require(peek()?.let { it in '1'..'9' } == true) { "invalid JSON number" }
                while (isDigit(peek())) position++
            }
            if (tryConsume('.')) {
                require(isDigit(peek())) { "invalid JSON fraction" }
                while (isDigit(peek())) position++
            }
            if (peek() == 'e' || peek() == 'E') {
                position++
                if (peek() == '+' || peek() == '-') position++
                require(isDigit(peek())) { "invalid JSON exponent" }
                while (isDigit(peek())) position++
            }
            return JsonNumber(input.substring(start, position).toBigDecimal())
        }

        private fun isDigit(value: Char?): Boolean = value != null && value in '0'..'9'

        private fun <T : JsonValue> parseLiteral(text: String, value: T): T {
            require(input.regionMatches(position, text, 0, text.length)) { "invalid literal at $position" }
            position += text.length
            return value
        }

        private fun whitespace() {
            while (peek()?.isWhitespace() == true) position++
        }

        private fun peek(): Char? = input.getOrNull(position)

        private fun consume(expected: Char) {
            require(tryConsume(expected)) { "expected '$expected' at $position" }
        }

        private fun tryConsume(expected: Char): Boolean = if (peek() == expected) {
            position++
            true
        } else false

        private fun fail(message: String): Nothing = throw IllegalArgumentException("invalid JSON: $message")

        private fun nfc(value: String): String {
            require(value == Normalizer.normalize(value, Normalizer.Form.NFC)) { "JSON string is not NFC" }
            return value
        }
    }
}

internal data class ReportDiagnostic(
    val severity: String,
    val code: String,
    val messageKey: String,
    val message: String,
    val sourceId: String?,
    val sourceFile: String?,
    val row: Int?,
    val column: Int?,
    val field: String?,
    val expected: String?,
    val actual: String?
) {
    fun stableKey(): List<Comparable<*>> = listOf(
        if (severity == "ERROR") 0 else 1,
        code,
        sourceId.orEmpty(),
        sourceFile.orEmpty(),
        row ?: -1,
        column ?: -1,
        field.orEmpty(),
        messageKey,
        actual.orEmpty()
    )
}

internal data class SourceParseDiagnostic(
    val code: String,
    val messageKey: String,
    val message: String,
    val sourceId: String?,
    val sourceFile: String,
    val row: Int?,
    val column: Int?,
    val field: String?,
    val expected: String?,
    val actual: String?
) {
    fun toReportDiagnostic(): ReportDiagnostic = ReportDiagnostic(
        severity = "ERROR",
        code = code,
        messageKey = messageKey,
        message = message,
        sourceId = sourceId,
        sourceFile = sourceFile,
        row = row,
        column = column,
        field = field,
        expected = expected,
        actual = actual
    )

    fun render(): String = toReportDiagnostic().let { diagnostic ->
        "code=${diagnostic.code};messageKey=${diagnostic.messageKey};sourceFile=${diagnostic.sourceFile};row=${diagnostic.row};column=${diagnostic.column};field=${diagnostic.field};expected=${diagnostic.expected};actual=${diagnostic.actual}"
    }
}

internal class SourceParseException(val diagnostic: SourceParseDiagnostic) : IllegalArgumentException(diagnostic.render())

internal fun parseSourceJson(input: String, sourceFile: String, field: String): JsonValue = try {
    JsonParser.parse(input)
} catch (error: Exception) {
    val offset = Regex("(?:at|position)\\s+(\\d+)").find(error.message.orEmpty())?.groupValues?.get(1)?.toIntOrNull() ?: 0
    val safeOffset = offset.coerceIn(0, input.length)
    val row = input.take(safeOffset).count { it == '\n' } + 1
    val lineStart = input.lastIndexOf('\n', safeOffset - 1).let { if (it < 0) 0 else it + 1 }
    val actual = error.message ?: error::class.java.simpleName
    val code = when {
        actual.contains("duplicate JSON key", ignoreCase = true) -> "JSON_DUPLICATE_KEY"
        actual.contains("NFC", ignoreCase = true) -> "SOURCE_ENCODING_INVALID"
        else -> "SOURCE_INVALID"
    }
    throw SourceParseException(SourceParseDiagnostic(
        code = code,
        messageKey = code,
        message = actual,
        sourceId = null,
        sourceFile = sourceFile,
        row = row,
        column = safeOffset - lineStart + 1,
        field = field,
        expected = "valid JSON",
        actual = actual
    ))
}

internal fun readUtf8(path: Path): String {
    val decoder = StandardCharsets.UTF_8.newDecoder()
        .onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT)
    val decoded = decoder.decode(ByteBuffer.wrap(Files.readAllBytes(path))).toString()
    return if (decoded.startsWith('\uFEFF')) decoded.drop(1) else decoded
}

internal fun JsonObject.requireKeys(allowed: Set<String>, context: String) {
    val unknown = values.keys - allowed
    require(unknown.isEmpty()) { "$context contains unknown fields: ${unknown.sorted().joinToString(",")}" }
}

internal fun JsonValue.asObject(): JsonObject = this as? JsonObject ?: error("expected JSON object")
internal fun JsonValue.asArray(): JsonArray = this as? JsonArray ?: error("expected JSON array")
internal fun JsonValue.asString(): String = (this as? JsonString)?.value ?: error("expected JSON string")
internal fun JsonValue.asBoolean(): Boolean = (this as? JsonBoolean)?.value ?: error("expected JSON boolean")
internal fun JsonValue.asInt(): Int = (this as? JsonNumber)?.value?.intValueExact() ?: error("expected JSON integer")
internal fun JsonValue.asLong(): Long = (this as? JsonNumber)?.value?.longValueExact() ?: error("expected JSON long")
internal fun JsonValue.asNullableString(): String? = if (this == JsonNull) null else asString()

internal fun JsonObject.value(name: String): JsonValue = values[name] ?: error("missing JSON field: $name")
internal fun JsonObject.optional(name: String): JsonValue? = values[name]
