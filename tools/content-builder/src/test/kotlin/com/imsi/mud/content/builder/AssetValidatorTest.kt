package com.imsi.mud.content.builder

import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.Base64
import javax.imageio.ImageIO
import kotlin.io.path.writeBytes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.Rule

class AssetValidatorTest {
    @get:Rule val phase1Fixture = Phase1FixtureRule()
    @Test
    fun `P1-FT-003 validator rejects traversal metadata physical and license faults`() {
        val root = Files.createTempDirectory("asset-validation")
        val entries = listOf(
            AssetPreviewEntry("bad-path", "PORTRAIT", "../secret.webp", 1, 1, 1, "a".repeat(64), "fixture"),
            AssetPreviewEntry("bad-mime", "PORTRAIT", "portrait/bad.txt", 1, 1, 1, "a".repeat(64), "fixture")
        )
        val diagnostics = AssetValidator.validate(entries, root, emptySet())
        assertTrue(diagnostics.any { it.code == "ASSET_PATH_TRAVERSAL" })
        assertTrue(diagnostics.any { it.code == "ASSET_UNSUPPORTED_MIME" })

        val invalid = listOf(
            AssetPreviewEntry("uri", "PORTRAIT", "http://example/test.png", 1, 1, 1, "a".repeat(64), "unregistered"),
            AssetPreviewEntry("drive", "PORTRAIT", "C:/outside.png", 1, 1, 1, "a".repeat(64), "unregistered"),
            AssetPreviewEntry("backslash", "PORTRAIT", "portrait\\outside.png", 1, 1, 1, "a".repeat(64), "unregistered"),
            AssetPreviewEntry("large", "PORTRAIT", "portrait/large.png", 8193, 2049, 32L * 1024 * 1024 + 1, "a".repeat(64), "unregistered"),
            AssetPreviewEntry("scope", "PORTRAIT", "portrait/scope.png", 1, 1, 1, "a".repeat(64), "blocked")
        )
        val invalidDiagnostics = AssetValidator.validate(invalid, root, emptySet(), mapOf("blocked" to setOf("IOS_APP")))
        assertTrue(invalidDiagnostics.any { it.code == "ASSET_PATH_TRAVERSAL" })
        assertTrue(invalidDiagnostics.any { it.code == "ASSET_SIZE_LIMIT" })
        assertTrue(invalidDiagnostics.any { it.code == "ASSET_DIMENSION_LIMIT" })
        assertTrue(invalidDiagnostics.any { it.code == "ASSET_LICENSE_UNAPPROVED" })
        val blockedDiagnostics = AssetValidator.validate(listOf(invalid.last()), root, setOf("blocked"), mapOf("blocked" to setOf("IOS_APP")))
        assertTrue(blockedDiagnostics.any { it.code == "ASSET_LICENSE_SCOPE_BLOCKED" })

        val truncatedPng = root.resolve("portrait/truncated.png")
        Files.createDirectories(truncatedPng.parent)
        truncatedPng.writeBytes(byteArrayOf(137.toByte(), 80, 78, 71, 13, 10, 26, 10, 0, 0, 0, 13, 73, 72, 68, 82))
        val structure = AssetValidator.validate(listOf(AssetPreviewEntry("truncated", "PORTRAIT", "portrait/truncated.png", 1, 1, Files.size(truncatedPng), "a".repeat(64), "fixture")), root, setOf("fixture"))
        assertTrue(structure.any { it.code == "ASSET_INVALID_STRUCTURE" })

        val truncatedWebp = root.resolve("portrait/truncated.webp")
        truncatedWebp.writeBytes(byteArrayOf('R'.code.toByte(), 'I'.code.toByte(), 'F'.code.toByte(), 'F'.code.toByte(), 100, 0, 0, 0, 'W'.code.toByte(), 'E'.code.toByte(), 'B'.code.toByte(), 'P'.code.toByte(), 'V'.code.toByte(), 'P'.code.toByte(), '8'.code.toByte(), 'X'.code.toByte(), 10, 0, 0, 0))
        val webpStructure = AssetValidator.validate(listOf(AssetPreviewEntry("truncated-webp", "PORTRAIT", "portrait/truncated.webp", 1, 1, Files.size(truncatedWebp), "a".repeat(64), "fixture")), root, setOf("fixture"))
        assertTrue(webpStructure.any { it.code == "ASSET_INVALID_STRUCTURE" })

        val vp8xOnly = root.resolve("portrait/vp8x-only.webp")
        vp8xOnly.writeBytes(ByteArray(30).also { bytes ->
            "RIFF".toByteArray(Charsets.US_ASCII).copyInto(bytes, 0)
            bytes[4] = 22
            "WEBP".toByteArray(Charsets.US_ASCII).copyInto(bytes, 8)
            "VP8X".toByteArray(Charsets.US_ASCII).copyInto(bytes, 12)
            bytes[16] = 10
            bytes[24] = 7
            bytes[27] = 7
        })
        val vp8xDiagnostics = AssetValidator.validate(listOf(AssetPreviewEntry("vp8x-only", "PORTRAIT", "portrait/vp8x-only.webp", 8, 8, Files.size(vp8xOnly), CanonicalSourceConverter.sha256(Files.readAllBytes(vp8xOnly)), "fixture")), root, setOf("fixture"))
        assertTrue(vp8xDiagnostics.any { it.code == "ASSET_INVALID_STRUCTURE" || it.code == "ASSET_DECODE_FAILED" })

        val metadata = ByteArray(64).also { bytes ->
            byteArrayOf(137.toByte(), 80, 78, 71, 13, 10, 26, 10).copyInto(bytes)
            "acTLeXIfiCCP".toByteArray(Charsets.US_ASCII).copyInto(bytes, 30)
        }
        val metadataPath = root.resolve("portrait/metadata.png")
        metadataPath.writeBytes(metadata)
        val metadataDiagnostics = AssetValidator.validate(listOf(AssetPreviewEntry("metadata", "PORTRAIT", "portrait/metadata.png", 1, 1, metadata.size.toLong(), "a".repeat(64), "fixture", alphaMode = "STRAIGHT")), root, setOf("fixture"))
        assertTrue(metadataDiagnostics.any { it.code == "ASSET_MULTI_FRAME" })
        assertTrue(metadataDiagnostics.any { it.code == "ASSET_EXIF_ORIENTATION" })
        assertTrue(metadataDiagnostics.any { it.code == "ASSET_UNSUPPORTED_ICC" })
        assertTrue(metadataDiagnostics.any { it.code == "ASSET_ALPHA_MISMATCH" })

        val outside = Files.createTempFile("asset-outside", ".png")
        val link = root.resolve("portrait/link.png")
        if (runCatching { Files.createSymbolicLink(link, outside) }.isSuccess) {
            val symlinkDiagnostics = AssetValidator.validate(listOf(AssetPreviewEntry("symlink", "PORTRAIT", "portrait/link.png", 1, 1, 1, "a".repeat(64), "fixture")), root, setOf("fixture"))
            assertTrue(symlinkDiagnostics.any { it.code == "ASSET_SYMLINK_ESCAPE" })
        }
    }

    @Test
    fun `P1-IT-003 validator discovers WebP SPI decodes pixels and rejects header-only png`() {
        val root = Files.createTempDirectory("asset-png")
        val file = root.resolve("portrait/test.png")
        Files.createDirectories(file.parent)
        val png = ByteArrayOutputStream().use { output ->
            ImageIO.write(BufferedImage(8, 8, BufferedImage.TYPE_INT_RGB), "png", output)
            output.toByteArray()
        }
        file.writeBytes(png)
        /* obsolete header-only fixture
            137.toByte(), 80, 78, 71, 13, 10, 26, 10,
            0, 0, 0, 13, 73, 72, 68, 82,
            0, 0, 0, 8, 0, 0, 0, 8
        ))
        val entries = listOf(AssetPreviewEntry("png", "PORTRAIT", "portrait/test.png", 8, 8, 33, "a".repeat(64), "fixture"))
        val diagnostics = AssetValidator.validate(entries, root, setOf("fixture"))
        */
        val validDiagnostics = AssetValidator.validate(listOf(AssetPreviewEntry("png", "PORTRAIT", "portrait/test.png", 8, 8, png.size.toLong(), CanonicalSourceConverter.sha256(png), "fixture")), root, setOf("fixture"))
        assertTrue(validDiagnostics.none { it.code == "ASSET_DECODE_FAILED" || it.code == "ASSET_INVALID_MAGIC" || it.code == "ASSET_DIMENSION_MISMATCH" })

        assertTrue("WebP ImageIO SPI was not discovered", ImageIO.getImageReadersByFormatName("webp").hasNext())
        val webp = Base64.getMimeDecoder().decode(Files.readString(Path.of("..", "..", "phase1-fixtures", "P1-IT-003", "assets", "one-pixel.webp.base64")))
        val webpFile = root.resolve("portrait/one-pixel.webp")
        webpFile.writeBytes(webp)
        val webpDiagnostics = AssetValidator.validate(
            listOf(AssetPreviewEntry("webp", "PORTRAIT", "portrait/one-pixel.webp", 1, 1, webp.size.toLong(), CanonicalSourceConverter.sha256(webp), "fixture")),
            root,
            setOf("fixture")
        )
        assertTrue("valid WebP did not decode: $webpDiagnostics", webpDiagnostics.none { it.code == "ASSET_DECODE_FAILED" || it.code == "ASSET_INVALID_STRUCTURE" || it.code == "ASSET_DIMENSION_MISMATCH" })

        val alphaWebp = Base64.getMimeDecoder().decode(Files.readString(Path.of("..", "..", "phase1-fixtures", "P1-IT-003", "assets", "one-pixel-vp8l-alpha.webp.base64")))
        assertEquals("VP8L", String(alphaWebp, 12, 4, Charsets.US_ASCII))
        assertTrue((alphaWebp[24].toInt() and 0x10) != 0)
        val alphaWebpFile = root.resolve("portrait/one-pixel-vp8l-alpha.webp")
        alphaWebpFile.writeBytes(alphaWebp)
        val alphaWebpDiagnostics = AssetValidator.validate(
            listOf(AssetPreviewEntry("webp-alpha", "PORTRAIT", "portrait/one-pixel-vp8l-alpha.webp", 1, 1, alphaWebp.size.toLong(), CanonicalSourceConverter.sha256(alphaWebp), "fixture", alphaMode = "STRAIGHT")),
            root,
            setOf("fixture")
        )
        assertTrue("transparent VP8L alpha was rejected: $alphaWebpDiagnostics", alphaWebpDiagnostics.none { it.code == "ASSET_ALPHA_MISMATCH" || it.code == "ASSET_DECODE_FAILED" || it.code == "ASSET_INVALID_STRUCTURE" })

        val headerOnly = root.resolve("portrait/header-only.png")
        headerOnly.writeBytes(byteArrayOf(
            137.toByte(), 80, 78, 71, 13, 10, 26, 10,
            0, 0, 0, 13, 73, 72, 82,
            0, 0, 0, 8, 0, 0, 0, 8
        ))
        val headerDiagnostics = AssetValidator.validate(listOf(AssetPreviewEntry("header-only", "PORTRAIT", "portrait/header-only.png", 8, 8, Files.size(headerOnly), CanonicalSourceConverter.sha256(Files.readAllBytes(headerOnly)), "fixture")), root, setOf("fixture"))
        assertTrue(headerDiagnostics.any { it.code == "ASSET_DECODE_FAILED" })
    }
}
