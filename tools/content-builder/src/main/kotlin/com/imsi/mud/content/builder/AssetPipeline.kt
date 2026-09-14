package com.imsi.mud.content.builder

import java.io.ByteArrayInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.text.Normalizer
import java.util.Locale
import javax.imageio.ImageIO
import com.imsi.mud.content.AssetCategory
import com.imsi.mud.content.AssetId
import com.imsi.mud.content.AssetImage
import com.imsi.mud.content.AssetResolver
import com.imsi.mud.content.CropRect
import com.imsi.mud.content.ImageUsage

data class AssetPreviewEntry(
    val id: String,
    val category: String,
    val relativePath: String,
    val width: Int,
    val height: Int,
    val byteSize: Long,
    val sha256: String,
    val licenseId: String,
    val mimeType: String = "image/${relativePath.substringAfterLast('.', "").lowercase(Locale.ROOT)}",
    val alphaMode: String = "OPAQUE",
    val colorSpace: String = "SRGB",
    val poolVersion: String = "pool.v1",
    val validationStatus: String = "VALID",
    val focalXppm: Int? = null,
    val focalYppm: Int? = null,
    val usageType: String? = null,
    val cropProfile: String? = null,
    val resolutionReason: String? = null,
    val sourceDisplayName: String? = null,
    val effectiveDisplayName: String? = null,
    val missing: Boolean = false,
    val duplicate: Boolean = false,
    val unused: Boolean = false
)

data class LicenseRegistryEntry(
    val id: String,
    val license: String,
    val source: String,
    val approvalStatus: String,
    val distributionScopes: Set<String>
)

data class AssetDiagnostic(
    val code: String,
    val message: String,
    val assetId: String,
    val field: String? = null
)

data class AssetPreviewResult(
    val pageCount: Int,
    val maxEntriesPerPage: Int,
    val outputDirectory: Path
)

object AssetValidator {
    private const val MAX_BYTES = 32L * 1024 * 1024
    private const val MAX_PIXELS = 16_777_216L
    private const val MAX_DIMENSION = 8_192
    private val categories = setOf("PORTRAIT", "BACKGROUND", "ICON", "EMBLEM", "EVENT_ART", "KEY_ART")
    private val mimeTypes = mapOf("png" to "image/png", "webp" to "image/webp")
    private val alphaModes = setOf("OPAQUE", "STRAIGHT")
    private val colorSpaces = setOf("SRGB")
    private val validationStatuses = setOf("VALID", "NOT_PROVIDED", "LICENSE_BLOCKED", "INVALID")

    fun validate(
        entries: List<AssetPreviewEntry>,
        root: Path,
        approvedLicenseIds: Set<String>,
        licenseScopes: Map<String, Set<String>> = emptyMap()
    ): List<AssetDiagnostic> {
        val diagnostics = mutableListOf<AssetDiagnostic>()
        val seenIds = mutableSetOf<String>()
        val seenPaths = mutableSetOf<String>()
        val seenFoldedPaths = mutableSetOf<String>()
        val normalizedRoot = root.toAbsolutePath().normalize()
        entries.sortedBy(AssetPreviewEntry::id).forEach { entry ->
            if (!seenIds.add(entry.id)) diagnostics += AssetDiagnostic("ASSET_DUPLICATE_ID", "duplicate asset id", entry.id)
            if (!categories.contains(entry.category)) diagnostics += AssetDiagnostic("ASSET_INVALID_CATEGORY", "unsupported category", entry.id, "category")
            if (entry.relativePath.isBlank() || entry.relativePath != Normalizer.normalize(entry.relativePath, Normalizer.Form.NFC)) diagnostics += AssetDiagnostic("ASSET_INVALID_PATH", "path must be non-empty NFC", entry.id, "relativePath")
            val pathText = entry.relativePath
            val pathSegments = pathText.split('/')
            val forbiddenPath = pathText.startsWith('/') || pathText.contains('\\') || pathText.contains('\u0000') ||
                pathText.matches(Regex("^[A-Za-z][A-Za-z0-9+.-]*:.*")) || pathSegments.any { it.isEmpty() || it == "." || it == ".." || it.any(Char::isISOControl) }
            val normalized = runCatching { Path.of(pathText).normalize() }.getOrNull()
            if (forbiddenPath || normalized == null || normalized.isAbsolute || normalized.startsWith("..")) {
                diagnostics += AssetDiagnostic("ASSET_PATH_TRAVERSAL", "path must remain below the asset root", entry.id, "relativePath")
                return@forEach
            }
            val path = normalized.toString().replace('\\', '/')
            if (!seenPaths.add(path)) diagnostics += AssetDiagnostic("ASSET_DUPLICATE_PATH", "duplicate relative path", entry.id, "relativePath")
            if (!seenFoldedPaths.add(path.lowercase(Locale.ROOT))) diagnostics += AssetDiagnostic("ASSET_DUPLICATE_PATH_CASE_FOLD", "relative path duplicates under Unicode case-fold", entry.id, "relativePath")
            val extension = path.substringAfterLast('.', "").lowercase(Locale.ROOT)
            if (extension !in setOf("png", "webp")) {
                diagnostics += AssetDiagnostic("ASSET_UNSUPPORTED_MIME", "only PNG and WebP are accepted", entry.id, "relativePath")
                return@forEach
            }
            if (entry.mimeType != mimeTypes.getValue(extension)) diagnostics += AssetDiagnostic("ASSET_MIME_MISMATCH", "mimeType differs from extension", entry.id, "mimeType")
            if (entry.alphaMode !in alphaModes) diagnostics += AssetDiagnostic("ASSET_INVALID_ALPHA_MODE", "unsupported alphaMode", entry.id, "alphaMode")
            if (entry.colorSpace !in colorSpaces) diagnostics += AssetDiagnostic("ASSET_INVALID_COLOR_SPACE", "unsupported colorSpace", entry.id, "colorSpace")
            if (entry.validationStatus !in validationStatuses) diagnostics += AssetDiagnostic("ASSET_INVALID_STATUS", "unsupported validationStatus", entry.id, "validationStatus")
            if (entry.validationStatus != "VALID") diagnostics += AssetDiagnostic("ASSET_VALIDATION_FAILED", "asset is not in VALID status", entry.id, "validationStatus")
            if (entry.byteSize <= 0 || entry.byteSize > MAX_BYTES) diagnostics += AssetDiagnostic("ASSET_SIZE_LIMIT", "asset size is outside the allowed bound", entry.id, "byteSize")
            if (entry.width <= 0 || entry.height <= 0 || entry.width > MAX_DIMENSION || entry.height > MAX_DIMENSION || entry.width.toLong() * entry.height.toLong() > MAX_PIXELS) {
                diagnostics += AssetDiagnostic("ASSET_DIMENSION_LIMIT", "asset dimensions are outside the allowed bound", entry.id, "width")
            }
            if ((entry.focalXppm == null) != (entry.focalYppm == null) || entry.focalXppm?.let { it !in 0..1_000_000 } == true || entry.focalYppm?.let { it !in 0..1_000_000 } == true) {
                diagnostics += AssetDiagnostic("ASSET_INVALID_FOCAL", "focal coordinates must be a paired 0..1,000,000 value", entry.id, "focalXppm")
            }
            if (entry.sha256.length != 64 || entry.sha256.any { it !in "0123456789abcdef" }) {
                diagnostics += AssetDiagnostic("ASSET_INVALID_HASH", "sha256 must be lowercase hexadecimal", entry.id, "sha256")
            }
            if (entry.licenseId !in approvedLicenseIds) diagnostics += AssetDiagnostic("ASSET_LICENSE_UNAPPROVED", "asset license is not approved", entry.id, "licenseId")
            if (entry.licenseId in licenseScopes && "ANDROID_APP" !in licenseScopes.getValue(entry.licenseId)) diagnostics += AssetDiagnostic("ASSET_LICENSE_SCOPE_BLOCKED", "license is not approved for Android app distribution", entry.id, "licenseId")
            val file = normalizedRoot.resolve(normalized).normalize()
            if (!file.startsWith(normalizedRoot)) {
                diagnostics += AssetDiagnostic("ASSET_PATH_TRAVERSAL", "resolved path escaped the asset root", entry.id, "relativePath")
                return@forEach
            }
            var currentPath = normalizedRoot
            val symlinkFound = pathSegments.any { segment ->
                currentPath = currentPath.resolve(segment)
                Files.isSymbolicLink(currentPath)
            }
            if (symlinkFound) {
                diagnostics += AssetDiagnostic("ASSET_SYMLINK_ESCAPE", "symbolic links are not allowed in asset paths", entry.id, "relativePath")
                return@forEach
            }
            if (!Files.isRegularFile(file)) {
                diagnostics += AssetDiagnostic("ASSET_MISSING_FILE", "physical asset file is missing", entry.id, "relativePath")
                return@forEach
            }
            val realRoot = runCatching { normalizedRoot.toRealPath() }.getOrNull()
            val realFile = runCatching { file.toRealPath() }.getOrNull()
            if (realRoot != null && realFile != null && !realFile.startsWith(realRoot)) {
                diagnostics += AssetDiagnostic("ASSET_SYMLINK_ESCAPE", "real path escaped the asset root", entry.id, "relativePath")
                return@forEach
            }
            val bytes = Files.readAllBytes(file)
            val actualHash = MessageDigest.getInstance("SHA-256").digest(bytes).toHex()
            if (actualHash != entry.sha256) diagnostics += AssetDiagnostic("ASSET_HASH_MISMATCH", "physical hash differs from manifest", entry.id, "sha256")
            if (bytes.size.toLong() != entry.byteSize) diagnostics += AssetDiagnostic("ASSET_SIZE_MISMATCH", "physical size differs from manifest", entry.id, "byteSize")
            val dimensions = when (extension) {
                "png" -> readPngDimensions(bytes)
                "webp" -> readWebpDimensions(bytes)
                else -> null
            }
            if (dimensions == null) diagnostics += AssetDiagnostic("ASSET_INVALID_MAGIC", "file magic does not match its extension", entry.id, "relativePath")
            else if (dimensions.first != entry.width || dimensions.second != entry.height) {
                diagnostics += AssetDiagnostic("ASSET_DIMENSION_MISMATCH", "physical dimensions differ from manifest", entry.id, "width")
            }
            validateStructure(extension, bytes, entry.id)?.let(diagnostics::add)
            validateDecodedPixels(extension, bytes, entry)?.let(diagnostics::add)
            diagnostics += physicalDiagnostics(entry, extension, bytes)
        }
        return diagnostics.sortedWith(compareBy(AssetDiagnostic::code, AssetDiagnostic::assetId, AssetDiagnostic::field))
    }

    fun assetManifestSha256(entries: List<AssetPreviewEntry>, licenses: List<LicenseRegistryEntry>): String {
        val referenced = entries.map(AssetPreviewEntry::licenseId).toSet()
        val document = JsonObject(mapOf(
            "assets" to JsonArray(entries.sortedBy(AssetPreviewEntry::id).map { entry ->
                JsonObject(mapOf(
                    "alphaMode" to JsonString(entry.alphaMode),
                    "assetId" to JsonString(entry.id),
                    "category" to JsonString(entry.category),
                    "colorSpace" to JsonString(entry.colorSpace),
                    "exactFileSha256" to JsonString(entry.sha256),
                    "focalXppm" to (entry.focalXppm?.let { JsonNumber(java.math.BigDecimal(it)) } ?: JsonNull),
                    "focalYppm" to (entry.focalYppm?.let { JsonNumber(java.math.BigDecimal(it)) } ?: JsonNull),
                    "height" to JsonNumber(java.math.BigDecimal(entry.height)),
                    "licenseId" to JsonString(entry.licenseId),
                    "mimeType" to JsonString(entry.mimeType),
                    "byteSize" to JsonNumber(java.math.BigDecimal(entry.byteSize)),
                    "poolVersion" to JsonString(entry.poolVersion),
                    "relativePath" to JsonString(entry.relativePath),
                    "sourceDisplayName" to (entry.sourceDisplayName?.let(::JsonString) ?: JsonNull),
                    "effectiveDisplayName" to (entry.effectiveDisplayName?.let(::JsonString) ?: JsonNull),
                    "validationStatus" to JsonString(entry.validationStatus),
                    "width" to JsonNumber(java.math.BigDecimal(entry.width))
                ))
            }),
            "licenses" to JsonArray(licenses.filter { it.id in referenced }.sortedBy(LicenseRegistryEntry::id).map { license ->
                JsonObject(mapOf(
                    "approvalStatus" to JsonString(license.approvalStatus),
                    "distributionScopes" to JsonArray(license.distributionScopes.sorted().map(::JsonString)),
                    "id" to JsonString(license.id),
                    "license" to JsonString(license.license),
                    "source" to JsonString(license.source)
                ))
            })
        ))
        return CanonicalSourceConverter.sha256(document.render().toByteArray(Charsets.UTF_8))
    }

    private fun readPngDimensions(bytes: ByteArray): Pair<Int, Int>? {
        if (bytes.size < 24 || !bytes.copyOfRange(0, 8).contentEquals(byteArrayOf(137.toByte(), 80, 78, 71, 13, 10, 26, 10))) return null
        val width = ByteBuffer.wrap(bytes, 16, 4).order(ByteOrder.BIG_ENDIAN).int
        val height = ByteBuffer.wrap(bytes, 20, 4).order(ByteOrder.BIG_ENDIAN).int
        return width to height
    }

    private fun physicalDiagnostics(entry: AssetPreviewEntry, extension: String, bytes: ByteArray): List<AssetDiagnostic> {
        val diagnostics = mutableListOf<AssetDiagnostic>()
        val text = String(bytes, Charsets.ISO_8859_1)
        val animated = if (extension == "png") text.contains("acTL") else text.contains("ANIM") || text.contains("ANMF")
        val exif = if (extension == "png") text.contains("eXIf") else text.contains("EXIF")
        val icc = if (extension == "png") text.contains("iCCP") else text.contains("ICCP")
        if (animated) diagnostics += AssetDiagnostic("ASSET_MULTI_FRAME", "animated or multi-frame assets are not accepted", entry.id, "relativePath")
        if (exif) diagnostics += AssetDiagnostic("ASSET_EXIF_ORIENTATION", "EXIF metadata is not accepted", entry.id, "relativePath")
        if (icc) diagnostics += AssetDiagnostic("ASSET_UNSUPPORTED_ICC", "embedded ICC profile is not accepted", entry.id, "colorSpace")
        val alpha = when (extension) {
            "png" -> if (bytes.size > 25) (bytes[25].toInt() and 0xff) == 4 || (bytes[25].toInt() and 0xff) == 6 else false
            "webp" -> webpHasAlpha(bytes)
            else -> false
        }
        if ((entry.alphaMode == "STRAIGHT") != alpha) diagnostics += AssetDiagnostic("ASSET_ALPHA_MISMATCH", "declared alphaMode differs from physical pixels", entry.id, "alphaMode")
        if (entry.colorSpace == "SRGB" && icc) diagnostics += AssetDiagnostic("ASSET_COLOR_SPACE_MISMATCH", "embedded color profile is not sRGB", entry.id, "colorSpace")
        return diagnostics
    }

    private fun webpHasAlpha(bytes: ByteArray): Boolean {
        var offset = 12
        while (offset + 8 <= bytes.size) {
            val type = String(bytes, offset, 4, Charsets.US_ASCII)
            val size = littleEndianInt(bytes, offset + 4).toLong() and 0xffffffffL
            val dataStart = offset + 8
            val dataEnd = dataStart.toLong() + size
            if (dataEnd > bytes.size) return false
            when (type) {
                "ALPH" -> return true
                "VP8X" -> if (size >= 1 && (bytes[dataStart].toInt() and 0x10) != 0) return true
                "VP8L" -> if (
                    size >= 5 && bytes[dataStart].toInt() == 0x2f &&
                    (bytes[dataStart + 4].toInt() and 0x10) != 0
                ) return true
            }
            offset = (dataEnd + (size and 1L)).toInt()
        }
        return false
    }

    private fun validateStructure(extension: String, bytes: ByteArray, assetId: String): AssetDiagnostic? = when (extension) {
        "png" -> validatePngStructure(bytes, assetId)
        "webp" -> validateWebpStructure(bytes, assetId)
        else -> null
    }

    private fun validateDecodedPixels(extension: String, bytes: ByteArray, entry: AssetPreviewEntry): AssetDiagnostic? {
        val input = runCatching { ImageIO.createImageInputStream(ByteArrayInputStream(bytes)) }.getOrNull()
            ?: return AssetDiagnostic("ASSET_DECODE_FAILED", "image input stream could not be created", entry.id, "relativePath")
        input.use { imageInput ->
            val reader = ImageIO.getImageReaders(imageInput).asSequence().firstOrNull { candidate ->
                candidate.originatingProvider?.formatNames?.any { formatName -> formatName.equals(extension, ignoreCase = true) } == true
            }
                ?: return AssetDiagnostic("ASSET_DECODE_FAILED", "no decoder is available for $extension", entry.id, "relativePath")
            return try {
                reader.input = imageInput
                val image = reader.read(0)
                if (image == null || image.width != entry.width || image.height != entry.height) {
                    AssetDiagnostic("ASSET_DECODE_FAILED", "decoded pixel dimensions do not match the manifest", entry.id, "relativePath")
                } else if (runCatching { reader.getNumImages(true) }.getOrDefault(1) != 1) {
                    AssetDiagnostic("ASSET_MULTI_FRAME", "multi-frame assets are not accepted", entry.id, "relativePath")
                } else {
                    null
                }
            } catch (error: Exception) {
                AssetDiagnostic("ASSET_DECODE_FAILED", "image pixels could not be decoded: ${error.message ?: error::class.java.simpleName}", entry.id, "relativePath")
            } finally {
                reader.dispose()
            }
        }
    }

    private fun validatePngStructure(bytes: ByteArray, assetId: String): AssetDiagnostic? {
        val signature = byteArrayOf(137.toByte(), 80, 78, 71, 13, 10, 26, 10)
        if (bytes.size < 8 || !bytes.copyOfRange(0, 8).contentEquals(signature)) return AssetDiagnostic("ASSET_INVALID_STRUCTURE", "PNG signature is incomplete", assetId, "relativePath")
        var offset = 8
        var ihdr = false
        var idat = false
        var iend = false
        while (offset < bytes.size) {
            if (offset + 12 > bytes.size) return AssetDiagnostic("ASSET_INVALID_STRUCTURE", "PNG chunk is truncated", assetId, "relativePath")
            val length = readBigEndianInt(bytes, offset)
            if (length < 0 || offset + 12L + length > bytes.size) return AssetDiagnostic("ASSET_INVALID_STRUCTURE", "PNG chunk length exceeds file", assetId, "relativePath")
            val type = String(bytes, offset + 4, 4, Charsets.US_ASCII)
            val dataStart = offset + 8
            val crcStart = dataStart + length
            val crc = java.util.zip.CRC32().apply {
                update(bytes, offset + 4, 4)
                update(bytes, dataStart, length)
            }.value
            if (crc != readBigEndianUnsignedInt(bytes, crcStart)) return AssetDiagnostic("ASSET_INVALID_STRUCTURE", "PNG chunk CRC mismatch", assetId, "relativePath")
            when (type) {
                "IHDR" -> if (length != 13 || ihdr) return AssetDiagnostic("ASSET_INVALID_STRUCTURE", "PNG IHDR is invalid", assetId, "relativePath") else ihdr = true
                "IDAT" -> idat = true
                "IEND" -> {
                    if (length != 0 || iend) return AssetDiagnostic("ASSET_INVALID_STRUCTURE", "PNG IEND is invalid", assetId, "relativePath")
                    iend = true
                    if (crcStart + 4 != bytes.size) return AssetDiagnostic("ASSET_INVALID_STRUCTURE", "PNG has trailing bytes after IEND", assetId, "relativePath")
                }
            }
            offset = crcStart + 4
            if (iend) break
        }
        return if (ihdr && idat && iend) null else AssetDiagnostic("ASSET_INVALID_STRUCTURE", "PNG requires complete IHDR, IDAT and IEND chunks", assetId, "relativePath")
    }

    private fun validateWebpStructure(bytes: ByteArray, assetId: String): AssetDiagnostic? {
        if (bytes.size < 12 || String(bytes, 0, 4, Charsets.US_ASCII) != "RIFF" || String(bytes, 8, 4, Charsets.US_ASCII) != "WEBP") {
            return AssetDiagnostic("ASSET_INVALID_STRUCTURE", "WebP RIFF header is incomplete", assetId, "relativePath")
        }
        val riffSize = littleEndianInt(bytes, 4).toLong() and 0xffffffffL
        if (riffSize != bytes.size - 8L) return AssetDiagnostic("ASSET_INVALID_STRUCTURE", "WebP RIFF length does not match file", assetId, "relativePath")
        var offset = 12
        var imageChunk = false
        while (offset < bytes.size) {
            if (offset + 8 > bytes.size) return AssetDiagnostic("ASSET_INVALID_STRUCTURE", "WebP chunk header is truncated", assetId, "relativePath")
            val size = littleEndianInt(bytes, offset + 4).toLong() and 0xffffffffL
            val end = offset + 8L + size
            if (end > bytes.size) return AssetDiagnostic("ASSET_INVALID_STRUCTURE", "WebP chunk length exceeds file", assetId, "relativePath")
            val type = String(bytes, offset, 4, Charsets.US_ASCII)
            if (type in setOf("VP8 ", "VP8L")) imageChunk = true
            offset = (end + (size and 1L)).toInt()
        }
        return if (imageChunk && offset == bytes.size) null else AssetDiagnostic("ASSET_INVALID_STRUCTURE", "WebP image chunk is missing", assetId, "relativePath")
    }

    private fun readBigEndianInt(bytes: ByteArray, offset: Int): Int =
        ((bytes[offset].toInt() and 0xff) shl 24) or ((bytes[offset + 1].toInt() and 0xff) shl 16) or
            ((bytes[offset + 2].toInt() and 0xff) shl 8) or (bytes[offset + 3].toInt() and 0xff)

    private fun readBigEndianUnsignedInt(bytes: ByteArray, offset: Int): Long = readBigEndianInt(bytes, offset).toLong() and 0xffffffffL

    private fun readWebpDimensions(bytes: ByteArray): Pair<Int, Int>? {
        if (bytes.size < 16 || String(bytes, 0, 4, Charsets.US_ASCII) != "RIFF" || String(bytes, 8, 4, Charsets.US_ASCII) != "WEBP") return null
        var offset = 12
        while (offset + 8 <= bytes.size) {
            val type = String(bytes, offset, 4, Charsets.US_ASCII)
            val size = littleEndianInt(bytes, offset + 4)
            val payload = offset + 8
            if (payload + size > bytes.size) return null
            when (type) {
                "VP8X" -> if (size >= 10) {
                    val width = oneBased24(bytes, payload + 4)
                    val height = oneBased24(bytes, payload + 7)
                    return width to height
                }
                "VP8L" -> if (size >= 5 && bytes[payload].toInt() and 0xff == 0x2f) {
                    val bits = bytes.copyOfRange(payload + 1, payload + 5)
                    val width = 1 + ((bits[0].toInt() and 0xff) or ((bits[1].toInt() and 0x3f) shl 8))
                    val height = 1 + (((bits[1].toInt() and 0xc0) shr 6) or ((bits[2].toInt() and 0xff) shl 2) or ((bits[3].toInt() and 0x0f) shl 10))
                    return width to height
                }
                "VP8 " -> if (size >= 10 && bytes[payload + 3].toInt() and 0xff == 0x9d && bytes[payload + 4].toInt() and 0xff == 0x01 && bytes[payload + 5].toInt() and 0xff == 0x2a) {
                    val width = littleEndianShort(bytes, payload + 6) and 0x3fff
                    val height = littleEndianShort(bytes, payload + 8) and 0x3fff
                    return width to height
                }
            }
            offset = payload + size + (size and 1)
        }
        return null
    }

    private fun oneBased24(bytes: ByteArray, offset: Int): Int = 1 + (bytes[offset].toInt() and 0xff) + ((bytes[offset + 1].toInt() and 0xff) shl 8) + ((bytes[offset + 2].toInt() and 0xff) shl 16)
    private fun littleEndianInt(bytes: ByteArray, offset: Int): Int = (bytes[offset].toInt() and 0xff) or ((bytes[offset + 1].toInt() and 0xff) shl 8) or ((bytes[offset + 2].toInt() and 0xff) shl 16) or ((bytes[offset + 3].toInt() and 0xff) shl 24)
    private fun littleEndianShort(bytes: ByteArray, offset: Int): Int = (bytes[offset].toInt() and 0xff) or ((bytes[offset + 1].toInt() and 0xff) shl 8)

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it.toInt() and 0xff) }
}

object AssetPreviewRenderer {
    private data class Page(val category: String, val number: Int, val entries: List<AssetPreviewEntry>)
    private data class PreviewCrop(val profile: String, val rect: CropRect)

    fun render(
        entries: List<AssetPreviewEntry>,
        outputDirectory: Path,
        pageSize: Int = 500,
        assetHrefPrefix: String = "../assets"
    ): AssetPreviewResult {
        require(pageSize in 1..500) { "asset preview page size must be between 1 and 500" }
        require(assetHrefPrefix.isNotBlank()) { "asset preview href prefix must not be blank" }
        Files.createDirectories(outputDirectory)
        val sorted = entries.sortedWith(compareBy(AssetPreviewEntry::category, AssetPreviewEntry::id))
        val pages = sorted.groupBy(AssetPreviewEntry::category).toSortedMap().flatMap { (category, categoryEntries) ->
            categoryEntries.chunked(pageSize).mapIndexed { index, page -> Page(category, index + 1, page) }
        }
        val pageCounts = pages.groupingBy(Page::category).eachCount()
        pages.forEach { page ->
            val pageName = "${page.category}-${page.number.toString().padStart(3, '0')}.html"
            Files.writeString(
                outputDirectory.resolve(pageName),
                renderPage(page.entries, page.number, pageCounts.getValue(page.category), page.category, assetHrefPrefix)
            )
        }
        Files.writeString(outputDirectory.resolve("index.html"), renderIndex(sorted, pages))
        return AssetPreviewResult(pages.size, pages.maxOfOrNull { it.entries.size } ?: 0, outputDirectory)
    }

    private fun renderIndex(entries: List<AssetPreviewEntry>, pages: List<Page>): String = buildString {
        append("<!doctype html><html lang=\"en\"><meta charset=\"utf-8\"><title>Asset preview</title><body>")
        append("<h1>Asset preview</h1><p data-asset-count=\"").append(entries.size).append("\">")
        append(entries.size).append(" assets in ").append(pages.size).append(" pages</p><nav>")
        entries.firstOrNull()?.let { append("<p data-first-asset=\"").append(escapeHtml(it.id)).append("\">First asset</p>") }
        pages.forEach { page ->
            val number = page.number.toString().padStart(3, '0')
            val file = "${page.category}-$number.html"
            append("<a data-category=\"").append(escapeHtml(page.category)).append("\" href=\"").append(file).append("\">")
                .append(escapeHtml(page.category)).append(" ").append(page.number).append("</a> ")
        }
        append("</nav></body></html>\n")
    }

    private fun renderPage(
        entries: List<AssetPreviewEntry>,
        page: Int,
        pageCount: Int,
        category: String,
        assetHrefPrefix: String
    ): String = buildString {
        append("<!doctype html><html lang=\"en\"><meta charset=\"utf-8\"><title>Asset page ").append(escapeHtml(category)).append(" ").append(page).append("</title><body>")
        append("<h1>Asset page ").append(escapeHtml(category)).append(" ").append(page).append("/ ").append(pageCount).append("</h1><table><thead><tr><th>ID</th><th>Preview</th><th>Category</th><th>Path</th><th>Size</th><th>Usage</th><th>Crop</th><th>Reason</th><th>Status</th></tr></thead><tbody>")
        entries.forEach { entry ->
            val publicName = entry.effectiveDisplayName ?: entry.sourceDisplayName ?: "Preview"
            val previewCrop = previewCrop(entry)
            append("<tr data-missing=\"").append(entry.missing).append("\" data-duplicate=\"").append(entry.duplicate).append("\" data-unused=\"").append(entry.unused).append("\"")
            previewCrop?.let { crop ->
                append(" data-crop-left=\"").append(crop.rect.left).append("\" data-crop-top=\"").append(crop.rect.top)
                    .append("\" data-crop-width=\"").append(crop.rect.width).append("\" data-crop-height=\"").append(crop.rect.height)
                    .append("\" data-crop-profile=\"").append(crop.profile).append("\"")
            }
            append("><td>").append(escapeHtml(entry.id)).append("</td><td>")
            append(previewImage(entry, previewCrop, assetHrefPrefix, publicName))
            append("</td><td>")
                .append(escapeHtml(entry.category)).append("</td><td>")
                .append(escapeHtml(entry.relativePath)).append("</td><td>")
                .append(entry.byteSize).append("</td><td>")
                .append(escapeHtml(entry.usageType.orEmpty())).append("</td><td>")
                .append(escapeHtml(previewCrop?.profile.orEmpty())).append("</td><td>")
                .append(escapeHtml(entry.resolutionReason.orEmpty())).append("</td><td>")
                .append(escapeHtml(entry.validationStatus)).append(" license=").append(escapeHtml(entry.licenseId)).append("</td></tr>")
        }
        append("</tbody></table></body></html>\n")
    }

    private fun previewCrop(entry: AssetPreviewEntry): PreviewCrop? {
        val usageName = entry.usageType ?: return null
        val usage = runCatching { ImageUsage.valueOf(usageName) }.getOrElse {
            throw IllegalArgumentException("unsupported preview usage: $usageName")
        }
        val expectedCategory = AssetResolver.categoryFor(usage)
        require(entry.category == expectedCategory.name) { "preview usage/category mismatch: $usageName" }
        val profile = AssetResolver.profileFor(usage)
        require(entry.cropProfile == null || entry.cropProfile == profile.name) { "preview usage/crop mismatch: $usageName" }
        require(entry.resolutionReason == "EXACT" || entry.resolutionReason?.startsWith("FALLBACK:") == true) {
            "preview resolution reason must be EXACT or FALLBACK:"
        }
        val asset = AssetImage(
            id = AssetId(entry.id),
            category = AssetCategory.valueOf(entry.category),
            relativePath = entry.relativePath,
            width = entry.width,
            height = entry.height,
            byteSize = entry.byteSize,
            sha256 = entry.sha256,
            focalXppm = entry.focalXppm,
            focalYppm = entry.focalYppm
        )
        return PreviewCrop(profile.name, AssetResolver.crop(asset, profile))
    }

    private fun previewImage(
        entry: AssetPreviewEntry,
        previewCrop: PreviewCrop?,
        assetHrefPrefix: String,
        publicName: String
    ): String {
        val src = "${assetHrefPrefix.trimEnd('/')}/${entry.relativePath}"
        val image = "<img width=\"${entry.width}\" height=\"${entry.height}\" loading=\"lazy\" src=\"${escapeHtml(src)}\" alt=\"${escapeHtml(publicName)}\""
        if (previewCrop == null) return "$image>"
        val crop = previewCrop.rect
        return "<div style=\"position:relative;width:${crop.width}px;height:${crop.height}px;overflow:hidden\">" +
            "$image style=\"position:absolute;left:-${crop.left}px;top:-${crop.top}px;max-width:none\"></div>"
    }

    internal fun escapeHtml(value: String): String = value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&#39;")
}
