package com.imsi.mud.ui.debug

import android.graphics.Bitmap
import android.graphics.Color
import android.os.Debug
import android.util.Log
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.test.platform.app.InstrumentationRegistry
import coil3.decode.DataSource
import coil3.request.SuccessResult
import com.imsi.mud.content.AssetCategory
import com.imsi.mud.content.AssetId
import com.imsi.mud.content.AssetImage
import com.imsi.mud.content.AssetResolveRequest
import com.imsi.mud.content.AssetUnavailableReason
import com.imsi.mud.content.EntityKind
import com.imsi.mud.content.ImageUsage
import com.imsi.mud.content.InMemoryContentRepository
import com.imsi.mud.content.InstalledBundle
import com.imsi.mud.content.QualityMode
import com.imsi.mud.content.ResolvedAsset
import com.imsi.mud.image.BundleScopedCoilCache
import com.imsi.mud.image.LocalAssetResolver
import com.imsi.mud.image.PHASE1_IMAGE_MEMORY_CACHE_MAX_BYTES
import com.imsi.mud.image.createPhase1ImageLoader
import com.imsi.mud.image.toCoilImageRequest
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class DebugAssetGalleryTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun p1It003_exactAndFallbackKeepTheSamePublicLabel() {
        requireFixture("P1-IT-003")
        val state = mutableStateOf<DebugAssetGalleryState>(
            DebugAssetGalleryState.Content(
                publicName = "정찰대장 리아",
                description = "용병 초상",
                resolution = DebugAssetResolution.Exact,
            ),
        )
        compose.setContent { DebugAssetGallery(state.value) }

        compose.onNodeWithContentDescription("정찰대장 리아").assertIsDisplayed()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.TraversalIndex, 0f))
        compose.onAllNodesWithContentDescription("정찰대장 리아").assertCountEquals(1)

        compose.runOnIdle {
            state.value = DebugAssetGalleryState.Content(
                publicName = "정찰대장 리아",
                description = "용병 초상",
                resolution = DebugAssetResolution.Fallback,
            )
        }

        compose.onNodeWithContentDescription("정찰대장 리아").assertIsDisplayed()
        compose.onAllNodesWithContentDescription("정찰대장 리아").assertCountEquals(1)
    }

    @Test
    fun p1Ct003_loadingIsAnnouncedOnce() {
        requireFixture("P1-CT-003")
        compose.setContent { DebugAssetGallery(DebugAssetGalleryState.Loading) }

        compose.onAllNodesWithContentDescription("이미지 불러오는 중").assertCountEquals(1)
        compose.onNodeWithContentDescription("이미지 불러오는 중")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.TraversalIndex, 0f))
    }

    @Test
    fun p1Et001_skippedAndTerminalStatesKeepTextAndActionsWithoutImageSemantics() {
        requireFixture("P1-ET-001")
        val state = mutableStateOf<DebugAssetGalleryState>(
            DebugAssetGalleryState.NoImage(
                publicName = "침수된 왕실 묘지",
                description = "텍스트 모드에서는 배경 이미지를 표시하지 않습니다.",
                actionLabel = "탐색 계속",
            ),
        )
        compose.setContent { DebugAssetGallery(state.value) }

        compose.onAllNodesWithTag("asset-gallery-image").assertCountEquals(0)
        compose.onNodeWithText("침수된 왕실 묘지").assertIsDisplayed()
        compose.onNodeWithText("텍스트 모드에서는 배경 이미지를 표시하지 않습니다.").assertIsDisplayed()
        compose.onNodeWithText("탐색 계속").assertIsDisplayed()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.TraversalIndex, 2f))

        compose.runOnIdle {
            state.value = DebugAssetGalleryState.NoImage(
                publicName = "침수된 왕실 묘지",
                description = "이미지를 사용할 수 없어도 탐색 정보는 유지됩니다.",
                actionLabel = "다시 시도",
            )
        }

        compose.onAllNodesWithTag("asset-gallery-image").assertCountEquals(0)
        compose.onNodeWithText("침수된 왕실 묘지").assertIsDisplayed()
        compose.onNodeWithText("이미지를 사용할 수 없어도 탐색 정보는 유지됩니다.").assertIsDisplayed()
        compose.onNodeWithText("다시 시도").assertIsDisplayed()
    }

    @Test
    fun p1It003_terminalDiagnosticsStayOutsideTheGalleryState() {
        requireFixture("P1-IT-003")
        val state = ResolvedAsset.AssetUnavailable(
            attemptedCount = 2,
            terminalReason = AssetUnavailableReason.ALL_CANDIDATES_UNAVAILABLE,
        ).toDebugGalleryState(
            publicName = "침수된 왕실 묘지",
            description = "던전 배경",
            noImageDescription = "이미지 없이 탐색 정보를 표시합니다.",
            actionLabel = "탐색 계속",
        )

        assertEquals(
            DebugAssetGalleryState.NoImage(
                publicName = "침수된 왕실 묘지",
                description = "이미지 없이 탐색 정보를 표시합니다.",
                actionLabel = "탐색 계속",
            ),
            state,
        )
    }

    @Test
    fun p1Pt001_processPssIsMeasuredAgainstTheMinProfileLimit() {
        requireFixture("P1-PT-001")
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(context.cacheDir, "phase1-coil-performance").apply { mkdirs() }
        val bitmap = Bitmap.createBitmap(512, 640, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.MAGENTA) }
        fun fixture(name: String, format: Bitmap.CompressFormat): File = File(directory, name).also { file ->
            FileOutputStream(file).use { output -> assertTrue(bitmap.compress(format, 100, output)) }
        }
        val png = fixture("runtime.png", Bitmap.CompressFormat.PNG)
        val webp = fixture("runtime.webp", Bitmap.CompressFormat.WEBP_LOSSLESS)
        val assets = listOf(
            AssetImage(AssetId("runtime-png"), AssetCategory.PORTRAIT, png.name, 512, 640, png.length(), "1".repeat(64)),
            AssetImage(AssetId("runtime-webp"), AssetCategory.PORTRAIT, webp.name, 512, 640, webp.length(), "2".repeat(64)),
        )
        val repository = InMemoryContentRepository(
            installedBundle = InstalledBundle("bundle-a", "content-v1", "balance-v1"),
            templates = emptyList(), aliases = emptyList(), bindings = emptyList(), assets = assets, fallbacks = emptyList(),
        )
        val imageLoader = createPhase1ImageLoader(context)
        val cacheBoundary = BundleScopedCoilCache()
        cacheBoundary.activate(imageLoader, "bundle-a")
        val samples = mutableListOf(Debug.getPss())
        val combinations = assets.flatMap { asset ->
            listOf(QualityMode.FULL, QualityMode.LOW).flatMap { quality ->
                listOf(128, 1_024).map { target -> Triple(asset, quality, target) }
            }
        }
        val executedCombinations = mutableSetOf<String>()
        val executedBundleCombinations = mutableSetOf<String>()
        var memoryCacheHitCount = 0
        repeat(24) { index ->
            val bundleId = if (index < 12) "bundle-a" else "bundle-b"
            if (index == 12) {
                cacheBoundary.activate(imageLoader, bundleId)
                assertTrue(imageLoader.memoryCache?.keys.orEmpty().none { it.key.startsWith("bundle-a|") })
            }
            val (asset, quality, target) = combinations[index % combinations.size]
            val combination = "${asset.relativePath.substringAfterLast('.').uppercase()}|$quality|$target"
            executedCombinations += combination
            executedBundleCombinations += "$bundleId|$combination"
            val resolution = LocalAssetResolver.resolve(
                repository = repository,
                bundleRootUri = directory.toURI().toString(),
                request = AssetResolveRequest(
                    bundleId = bundleId,
                    exactAssetKeys = listOf(asset.id),
                    entityKind = EntityKind.MERCENARY,
                    usage = ImageUsage.LIST_FACE,
                    targetPx = target,
                    qualityMode = quality,
                ),
                probe = { true },
            )
            val request = requireNotNull(resolution.imageRequest).toCoilImageRequest(context)
            val result = runBlocking { imageLoader.execute(request) }
            assertTrue("Coil must decode ${asset.relativePath}", result is SuccessResult)
            val repeated = runBlocking { imageLoader.execute(request) }
            assertTrue("repeated Coil request must succeed", repeated is SuccessResult)
            if ((repeated as SuccessResult).dataSource == DataSource.MEMORY_CACHE) memoryCacheHitCount++
            samples += Debug.getPss()
        }
        val steadyPssKb = samples.takeLast(5).average().toLong()
        val transientPeakPssKb = samples.maxOrNull() ?: 0L
        assertEquals("all PNG/WebP quality/target combinations must run", 8, executedCombinations.size)
        assertEquals("each immutable bundle must run all decode combinations", 16, executedBundleCombinations.size)
        assertTrue("Coil memory cache must serve repeated requests", memoryCacheHitCount > 0)
        assertEquals(PHASE1_IMAGE_MEMORY_CACHE_MAX_BYTES, imageLoader.memoryCache?.maxSize)
        assertTrue((imageLoader.memoryCache?.size ?: Long.MAX_VALUE) <= PHASE1_IMAGE_MEMORY_CACHE_MAX_BYTES)
        assertTrue("instrumentation steady PSS must be measured", steadyPssKb > 0L)
        assertTrue("instrumentation steady PSS $steadyPssKb KiB exceeds the 384 MiB MIN profile", steadyPssKb <= 384L * 1_024L)
        assertTrue("instrumentation transient PSS $transientPeakPssKb KiB exceeds 512 MiB", transientPeakPssKb <= 512L * 1_024L)
        Log.i(
            "Phase1Performance",
            "P1_PT_METRICS steadyPssKb=$steadyPssKb transientPeakPssKb=$transientPeakPssKb " +
                "sampleCount=${samples.size} decodeCombinationCount=${executedCombinations.size} " +
                "bundleCombinationCount=${executedBundleCombinations.size} memoryCacheHitCount=$memoryCacheHitCount " +
                "coilCacheMaxBytes=$PHASE1_IMAGE_MEMORY_CACHE_MAX_BYTES",
        )
        repository.close()
        imageLoader.shutdown()
        bitmap.recycle()
    }

    private fun requireFixture(testId: String) {
        val bytes = InstrumentationRegistry.getInstrumentation().context.assets
            .open("phase1-fixtures/$testId.txt")
            .use { it.readBytes() }
        assertTrue(String(bytes, Charsets.UTF_8).contains(testId))
    }
}
