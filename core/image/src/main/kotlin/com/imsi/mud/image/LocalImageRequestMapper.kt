package com.imsi.mud.image

import com.imsi.mud.content.AssetImage
import com.imsi.mud.content.AssetResolveRequest
import com.imsi.mud.content.AssetResolver
import com.imsi.mud.content.ContentRepository
import com.imsi.mud.content.CropProfile
import com.imsi.mud.content.CropRect
import com.imsi.mud.content.ResolvedAsset
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.text.Normalizer

/**
 * Android-facing, decode-ready request metadata. It deliberately carries no UI copy or semantics.
 */
data class LocalImageRequest(
    val dataUri: String,
    val memoryCacheKey: String,
    val targetPx: Int,
    val crop: CropRect,
    val cropProfile: CropProfile,
)

data class LocalAssetResolution(
    val resolved: ResolvedAsset,
    val imageRequest: LocalImageRequest?,
)

/**
 * Runs the pure candidate resolver against a local decode probe and maps the selected asset for UI
 * loading. The pure resolver de-duplicates candidates, so this probe sees each AssetId at most once.
 */
object LocalAssetResolver {
    fun resolve(
        repository: ContentRepository,
        bundleRootUri: String,
        request: AssetResolveRequest,
        probe: (localUri: String) -> Boolean,
    ): LocalAssetResolution {
        val resolved = AssetResolver.resolve(repository, request) { asset ->
            probe(LocalAssetUriMapper.map(bundleRootUri, asset.relativePath))
        }
        return LocalAssetResolution(
            resolved = resolved,
            imageRequest = LocalImageRequestMapper.map(bundleRootUri, request, resolved),
        )
    }
}

/** Maps a pure P1 resolution result to a local-only image request without touching storage. */
object LocalImageRequestMapper {
    fun map(
        bundleRootUri: String,
        request: AssetResolveRequest,
        resolved: ResolvedAsset,
    ): LocalImageRequest? {
        val resolvedContent = when (resolved) {
            is ResolvedAsset.Exact -> ResolvedContent(
                resolved.asset,
                resolved.crop,
                resolved.profile,
                resolved.effectiveTargetPx,
            )

            is ResolvedAsset.Fallback -> ResolvedContent(
                resolved.asset,
                resolved.crop,
                resolved.profile,
                resolved.effectiveTargetPx,
            )

            is ResolvedAsset.SkippedByQualityMode,
            is ResolvedAsset.AssetUnavailable -> return null
        }

        val asset = resolvedContent.asset
        return LocalImageRequest(
            dataUri = LocalAssetUriMapper.map(bundleRootUri, asset.relativePath),
            memoryCacheKey = AssetResolver.memoryCacheKey(
                bundleId = request.bundleId,
                asset = asset,
                usage = request.usage,
                targetPx = request.targetPx,
                qualityMode = request.qualityMode,
            ),
            targetPx = resolvedContent.targetPx,
            crop = resolvedContent.crop,
            cropProfile = resolvedContent.profile,
        )
    }

    private data class ResolvedContent(
        val asset: AssetImage,
        val crop: CropRect,
        val profile: CropProfile,
        val targetPx: Int,
    )
}

private object LocalAssetUriMapper {
    fun map(bundleRootUri: String, relativePath: String): String {
        val root = runCatching { URI(bundleRootUri) }
            .getOrElse { throw IllegalArgumentException("bundle root must be a valid local URI", it) }
        require(root.scheme.equals("file", ignoreCase = true)) { "only local file bundle roots are supported" }
        require(root.rawAuthority.isNullOrEmpty()) { "file bundle root must not have an authority" }
        require(root.rawQuery == null && root.rawFragment == null) { "bundle root must not have query or fragment" }
        require(!root.isOpaque && root.path?.startsWith('/') == true) { "file bundle root must be absolute" }
        require(root.path.split('/').none { it == "." || it == ".." }) { "bundle root contains an invalid segment" }
        require(relativePath == Normalizer.normalize(relativePath, Normalizer.Form.NFC)) {
            "asset path must be NFC"
        }
        require(!relativePath.startsWith('/') && '\\' !in relativePath) { "asset path must be root-relative" }
        require(relativePath.none { it.code < 0x20 }) { "asset path must not contain control characters" }
        val segments = relativePath.split('/')
        require(segments.isNotEmpty() && segments.none { it.isBlank() || it == "." || it == ".." }) {
            "asset path contains an invalid segment"
        }
        require(relativePath.endsWith(".png", true) || relativePath.endsWith(".webp", true)) {
            "only PNG and WebP assets are supported"
        }
        val encodedPath = segments.joinToString("/") { segment ->
            URLEncoder.encode(segment, StandardCharsets.UTF_8.name()).replace("+", "%20")
        }
        return bundleRootUri.trimEnd('/') + "/" + encodedPath
    }
}
