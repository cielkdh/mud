package com.imsi.mud.image

import com.imsi.mud.content.AssetCategory
import com.imsi.mud.content.AssetFallback
import com.imsi.mud.content.AssetId
import com.imsi.mud.content.AssetImage
import com.imsi.mud.content.AssetResolveRequest
import com.imsi.mud.content.AssetUnavailableReason
import com.imsi.mud.content.CropProfile
import com.imsi.mud.content.CropRect
import com.imsi.mud.content.EntityKind
import com.imsi.mud.content.FallbackMatcher
import com.imsi.mud.content.ImageUsage
import com.imsi.mud.content.InMemoryContentRepository
import com.imsi.mud.content.InstalledBundle
import com.imsi.mud.content.QualityMode
import com.imsi.mud.content.ResolvedAsset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.Rule

class LocalImageRequestMapperTest {
    @get:Rule val phase1Fixture = Phase1FixtureRule()
    @Test
    fun `P1-UT-003 maps only validated local assets and preserves crop`() {
        val result = LocalImageRequestMapper.map(
            bundleRootUri = "file:///android_asset/phase1/",
            request = request(bundleId = "bundle-v1", qualityMode = QualityMode.FULL),
            resolved = ResolvedAsset.Exact(
                asset = asset("portraits/scout.webp"),
                crop = CropRect(left = 1, top = 0, width = 9, height = 9),
                profile = CropProfile.SQUARE_FACE,
                effectiveTargetPx = 256,
            ),
        )

        requireNotNull(result)
        assertEquals("file:///android_asset/phase1/portraits/scout.webp", result.dataUri)
        assertEquals(CropRect(1, 0, 9, 9), result.crop)
        assertEquals(CropProfile.SQUARE_FACE, result.cropProfile)
        assertEquals(256, result.targetPx)
        assertEquals(256 to 256, result.decodeSize())
        assertEquals(
            "bundle-v1|fixture-image|${"1".repeat(64)}|LIST_FACE|256|FULL",
            result.memoryCacheKey,
        )
    }

    @Test
    fun `P1-UT-003 decode size preserves the crop aspect inside the target long edge`() {
        val portrait = LocalImageRequest(
            dataUri = "file:///android_asset/phase1/portrait.webp",
            memoryCacheKey = "portrait",
            targetPx = 1024,
            crop = CropRect(0, 0, 480, 640),
            cropProfile = CropProfile.PORTRAIT_3_4,
        )
        val landscape = portrait.copy(
            targetPx = 1920,
            crop = CropRect(0, 0, 1280, 720),
            cropProfile = CropProfile.LANDSCAPE_16_9,
        )

        assertEquals(768 to 1024, portrait.decodeSize())
        assertEquals(1920 to 1080, landscape.decodeSize())
    }

    @Test
    fun `P1-BT-003 cache identity includes bundle and rejects network or traversal`() {
        val resolved = ResolvedAsset.Exact(
            asset = asset("portraits/scout.webp"),
            crop = CropRect(0, 0, 10, 10),
            profile = CropProfile.SQUARE_FACE,
            effectiveTargetPx = 128,
        )
        val first = requireNotNull(
            LocalImageRequestMapper.map(
                "file:///android_asset/phase1/",
                request(bundleId = "bundle-v1", qualityMode = QualityMode.LOW),
                resolved,
            ),
        )
        val nextBundle = requireNotNull(
            LocalImageRequestMapper.map(
                "file:///android_asset/phase1/",
                request(bundleId = "bundle-v2", qualityMode = QualityMode.LOW),
                resolved,
            ),
        )

        assertNotEquals(first.memoryCacheKey, nextBundle.memoryCacheKey)
        assertEquals(
            "bundle-v1|fixture-image|${"1".repeat(64)}|LIST_FACE|128|LOW",
            first.memoryCacheKey,
        )
        assertThrows(IllegalArgumentException::class.java) {
            LocalImageRequestMapper.map(
                "https://example.invalid/assets/",
                request(bundleId = "bundle-v1", qualityMode = QualityMode.LOW),
                resolved,
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            LocalImageRequestMapper.map(
                "file:relative-assets",
                request(bundleId = "bundle-v1", qualityMode = QualityMode.LOW),
                resolved,
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            LocalImageRequestMapper.map(
                "file:///android_asset/phase1/",
                request(bundleId = "bundle-v1", qualityMode = QualityMode.LOW),
                resolved.copy(asset = asset("../save.db")),
            )
        }
    }

    @Test
    fun `P1-CT-003 skipped and terminal results do not create image requests`() {
        assertNull(
            LocalImageRequestMapper.map(
                "file:///android_asset/phase1/",
                request(
                    bundleId = "bundle-v1",
                    qualityMode = QualityMode.TEXT,
                    usage = ImageUsage.ROOM_BACKGROUND,
                ),
                ResolvedAsset.SkippedByQualityMode(QualityMode.TEXT, ImageUsage.ROOM_BACKGROUND),
            ),
        )
        assertNull(
            LocalImageRequestMapper.map(
                "file:///android_asset/phase1/",
                request(
                    bundleId = "bundle-v1",
                    qualityMode = QualityMode.FULL,
                    usage = ImageUsage.ROOM_BACKGROUND,
                ),
                ResolvedAsset.AssetUnavailable(0, AssetUnavailableReason.NO_CANDIDATES),
            ),
        )
    }

    @Test
    fun `P1-BT-003 local resolver probes each candidate once and reaches fallback`() {
        val broken = asset("portraits/broken.webp", id = "broken")
        val fallback = asset("portraits/fallback.webp", id = "fallback")
        val repository = InMemoryContentRepository(
            installedBundle = InstalledBundle("bundle-v1", "content-v1", "balance-v1"),
            templates = emptyList(),
            aliases = emptyList(),
            bindings = emptyList(),
            assets = listOf(broken, fallback),
            fallbacks = listOf(
                AssetFallback(
                    usage = ImageUsage.LIST_FACE,
                    matcher = FallbackMatcher.CATEGORY_DEFAULT,
                    matcherValue = null,
                    assetId = broken.id,
                    priority = 0,
                ),
                AssetFallback(
                    usage = ImageUsage.LIST_FACE,
                    matcher = FallbackMatcher.CATEGORY_DEFAULT,
                    matcherValue = null,
                    assetId = fallback.id,
                    priority = 1,
                ),
            ),
        )
        val seenUris = mutableListOf<String>()
        assertThrows(IllegalArgumentException::class.java) {
            AssetResolveRequest(
                bundleId = "bundle-v1",
                exactAssetKeys = listOf(broken.id, broken.id),
                entityKind = EntityKind.MERCENARY,
                usage = ImageUsage.LIST_FACE,
                targetPx = 128,
                qualityMode = QualityMode.FULL,
            )
        }
        val request = AssetResolveRequest(
            bundleId = "bundle-v1",
            exactAssetKeys = listOf(broken.id),
            entityKind = EntityKind.MERCENARY,
            usage = ImageUsage.LIST_FACE,
            targetPx = 128,
            qualityMode = QualityMode.FULL,
        )

        val result = LocalAssetResolver.resolve(
            repository = repository,
            bundleRootUri = "file:///android_asset/phase1/",
            request = request,
        ) { uri ->
            seenUris += uri
            uri.endsWith("fallback.webp")
        }

        assertEquals(
            listOf(
                "file:///android_asset/phase1/portraits/broken.webp",
                "file:///android_asset/phase1/portraits/fallback.webp",
            ),
            seenUris,
        )
        assertEquals(fallback.id, (result.resolved as ResolvedAsset.Fallback).asset.id)
        assertEquals("file:///android_asset/phase1/portraits/fallback.webp", result.imageRequest?.dataUri)
    }

    @Test
    fun `P1-PT-001 local resolver quality and bundle switch keep cache and text io bounded`() {
        val selected = asset("portraits/performance.webp", id = "performance")
        var repositoryReads = 0
        val repository = InMemoryContentRepository(
            installedBundle = InstalledBundle("bundle-v1", "content-v1", "balance-v1"),
            templates = emptyList(),
            aliases = emptyList(),
            bindings = emptyList(),
            assets = listOf(selected),
            fallbacks = emptyList(),
            beforeRead = { repositoryReads++ },
        )
        val exact = listOf(selected.id)
        val full = LocalAssetResolver.resolve(
            repository,
            "file:///android_asset/phase1/",
            request("bundle-v1", QualityMode.FULL).copy(exactAssetKeys = exact),
        ) { true }
        val low = LocalAssetResolver.resolve(
            repository,
            "file:///android_asset/phase1/",
            request("bundle-v2", QualityMode.LOW).copy(exactAssetKeys = exact),
        ) { true }

        assertEquals(256, (full.resolved as ResolvedAsset.Exact).effectiveTargetPx)
        assertEquals(128, (low.resolved as ResolvedAsset.Exact).effectiveTargetPx)
        assertNotEquals(full.imageRequest?.memoryCacheKey, low.imageRequest?.memoryCacheKey)

        val readsBeforeText = repositoryReads
        var textProbeCalls = 0
        val text = LocalAssetResolver.resolve(
            repository,
            "file:///android_asset/phase1/",
            request("bundle-v2", QualityMode.TEXT, ImageUsage.ROOM_BACKGROUND).copy(exactAssetKeys = exact),
        ) {
            textProbeCalls++
            true
        }
        assertEquals(ResolvedAsset.SkippedByQualityMode(QualityMode.TEXT, ImageUsage.ROOM_BACKGROUND), text.resolved)
        assertNull(text.imageRequest)
        assertEquals(readsBeforeText, repositoryReads)
        assertEquals(0, textProbeCalls)
        repository.close()
    }

    @Test
    fun `P1-OP-001 local resolution is deterministic and remote roots fail before decode`() {
        val selected = asset("portraits/offline.webp", id = "offline")
        val repository = InMemoryContentRepository(
            installedBundle = InstalledBundle("bundle-v1", "content-v1", "balance-v1"),
            templates = emptyList(),
            aliases = emptyList(),
            bindings = emptyList(),
            assets = listOf(selected),
            fallbacks = emptyList(),
        )
        val request = request(bundleId = "bundle-v1", qualityMode = QualityMode.FULL)
            .copy(exactAssetKeys = listOf(selected.id))
        val localUris = mutableListOf<String>()

        val first = LocalAssetResolver.resolve(repository, "file:///android_asset/phase1/", request) { uri ->
            localUris += uri
            true
        }
        val second = LocalAssetResolver.resolve(repository, "file:///android_asset/phase1/", request) { uri ->
            localUris += uri
            true
        }

        assertEquals(first, second)
        assertEquals(
            listOf(
                "file:///android_asset/phase1/portraits/offline.webp",
                "file:///android_asset/phase1/portraits/offline.webp",
            ),
            localUris,
        )
        var remoteProbeCalls = 0
        assertThrows(IllegalArgumentException::class.java) {
            LocalAssetResolver.resolve(repository, "https://example.invalid/assets/", request) {
                remoteProbeCalls++
                true
            }
        }
        assertEquals(0, remoteProbeCalls)
    }

    private fun asset(path: String, id: String = "fixture-image") = AssetImage(
        id = AssetId(id),
        category = AssetCategory.PORTRAIT,
        relativePath = path,
        width = 10,
        height = 10,
        byteSize = 1,
        sha256 = "1".repeat(64),
    )

    private fun request(
        bundleId: String,
        qualityMode: QualityMode,
        usage: ImageUsage = ImageUsage.LIST_FACE,
    ) = AssetResolveRequest(
        bundleId = bundleId,
        entityKind = EntityKind.MERCENARY,
        usage = usage,
        targetPx = 256,
        qualityMode = qualityMode,
    )
}
