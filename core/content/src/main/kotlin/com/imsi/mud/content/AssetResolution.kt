package com.imsi.mud.content

enum class EntityKind { MERCENARY, MONSTER, DUNGEON, ROOM, ITEM, FACILITY }

enum class QualityMode { FULL, LOW, TEXT }

enum class CropProfile { SQUARE_FACE, PORTRAIT_3_4, SQUARE_CENTER, LANDSCAPE_16_9, FIT_INSIDE }

data class FallbackContext(
    val sex: String? = null,
    val characterClass: String? = null,
    val monsterFamily: String? = null,
    val region: String? = null,
    val roomTheme: String? = null,
    val itemType: String? = null,
    val facilityCategory: String? = null
) {
    init {
        listOfNotNull(sex, characterClass, monsterFamily, region, roomTheme, itemType, facilityCategory).forEach(::requireNfc)
    }

    fun valueFor(matcher: FallbackMatcher): String? = when (matcher) {
        FallbackMatcher.SEX -> sex
        FallbackMatcher.CLASS -> characterClass
        FallbackMatcher.MONSTER_FAMILY -> monsterFamily
        FallbackMatcher.REGION -> region
        FallbackMatcher.ROOM_THEME -> roomTheme
        FallbackMatcher.ITEM_TYPE -> itemType
        FallbackMatcher.FACILITY_CATEGORY -> facilityCategory
        FallbackMatcher.ARCHETYPE, FallbackMatcher.CATEGORY_DEFAULT -> null
        FallbackMatcher.GLOBAL_DEFAULT -> null
    }
}

data class AssetResolveRequest(
    val bundleId: String,
    val templateId: ContentId? = null,
    val exactAssetKeys: List<AssetId> = emptyList(),
    val entityKind: EntityKind,
    val usage: ImageUsage,
    val fallbackContext: FallbackContext = FallbackContext(),
    val targetPx: Int,
    val qualityMode: QualityMode = QualityMode.FULL
) {
    init {
        requireNfc(bundleId)
        require(targetPx > 0) { "targetPx must be positive" }
        require(exactAssetKeys.distinct().size == exactAssetKeys.size) { "exact asset keys must not contain duplicates" }
    }
}

data class CropRect(val left: Int, val top: Int, val width: Int, val height: Int) {
    init {
        require(left >= 0 && top >= 0 && width > 0 && height > 0) { "crop rectangle must be non-empty and in-bounds origin" }
    }
}

sealed interface ResolvedAsset {
    data class Exact(
        val asset: AssetImage,
        val crop: CropRect,
        val profile: CropProfile,
        val effectiveTargetPx: Int
    ) : ResolvedAsset

    data class Fallback(
        val asset: AssetImage,
        val crop: CropRect,
        val profile: CropProfile,
        val fallback: AssetFallback,
        val effectiveTargetPx: Int
    ) : ResolvedAsset

    data class SkippedByQualityMode(val qualityMode: QualityMode, val usage: ImageUsage) : ResolvedAsset
    data class AssetUnavailable(val attemptedCount: Int, val terminalReason: AssetUnavailableReason) : ResolvedAsset
}

enum class AssetUnavailableReason { NO_CANDIDATES, ALL_CANDIDATES_UNAVAILABLE }

/**
 * Candidate selection and crop math are pure. The injected probe is the only place a future image
 * adapter may open/decode a byte stream; this resolver calls it at most once for an AssetId.
 */
object AssetResolver {
    fun resolve(
        repository: ContentRepository,
        request: AssetResolveRequest,
        probe: (AssetImage) -> Boolean
    ): ResolvedAsset {
        if (request.qualityMode == QualityMode.TEXT && request.usage in TEXT_FORBIDDEN_USAGES) {
            return ResolvedAsset.SkippedByQualityMode(request.qualityMode, request.usage)
        }
        val profile = profileFor(request.usage)
        val candidates = linkedMapOf<AssetId, AssetFallback?>()
        request.exactAssetKeys.forEach { candidates.putIfAbsent(it, null) }
        request.templateId?.let { templateId ->
            repository.findAssetBindings(templateId, request.usage).forEach { binding -> candidates.putIfAbsent(binding.assetId, null) }
        }
        val fallbackRows = repository.listAssetFallbacks(request.usage)
        fallbackMatcherOrder(request.entityKind, request.usage).forEach { matcher ->
            val expected = request.fallbackContext.valueFor(matcher)
            fallbackRows.asSequence()
                .filter { it.matcher == matcher && (matcher == FallbackMatcher.GLOBAL_DEFAULT || it.matcherValue == expected) }
                .sortedWith(compareBy<AssetFallback>({ it.priority }, { it.assetId.value }))
                .forEach { candidates.putIfAbsent(it.assetId, it) }
        }

        val attempted = mutableListOf<AssetId>()
        candidates.forEach { (assetId, fallback) ->
            val asset = repository.findAsset(assetId) ?: return@forEach
            if (asset.category != categoryFor(request.usage)) return@forEach
            attempted += assetId
            if (probe(asset)) {
                val crop = crop(asset, profile)
                val target = effectiveTarget(request.targetPx, request.usage, request.qualityMode)
                return if (fallback == null) ResolvedAsset.Exact(asset, crop, profile, target)
                else ResolvedAsset.Fallback(asset, crop, profile, fallback, target)
            }
        }
        return ResolvedAsset.AssetUnavailable(
            attemptedCount = attempted.size,
            terminalReason = if (candidates.isEmpty()) AssetUnavailableReason.NO_CANDIDATES else AssetUnavailableReason.ALL_CANDIDATES_UNAVAILABLE
        )
    }

    fun crop(asset: AssetImage, profile: CropProfile): CropRect {
        if (profile == CropProfile.FIT_INSIDE) return CropRect(0, 0, asset.width, asset.height)
        val (targetWidth, targetHeight) = when (profile) {
            CropProfile.SQUARE_FACE, CropProfile.SQUARE_CENTER -> 1 to 1
            CropProfile.PORTRAIT_3_4 -> 3 to 4
            CropProfile.LANDSCAPE_16_9 -> 16 to 9
            CropProfile.FIT_INSIDE -> error("handled above")
        }
        val sourceWider = asset.width.toLong() * targetHeight > asset.height.toLong() * targetWidth
        val cropWidth = if (sourceWider) maxOf(1, ((asset.height.toLong() * targetWidth) / targetHeight).toInt()) else asset.width
        val cropHeight = if (sourceWider) asset.height else maxOf(1, ((asset.width.toLong() * targetHeight) / targetWidth).toInt())
        val (defaultX, defaultY) = if (profile == CropProfile.SQUARE_FACE) 500_000 to 350_000 else 500_000 to 500_000
        val centerX = ((asset.width.toLong() * (asset.focalXppm ?: defaultX)) / 1_000_000L).toInt()
        val centerY = ((asset.height.toLong() * (asset.focalYppm ?: defaultY)) / 1_000_000L).toInt()
        val left = (centerX - cropWidth / 2).coerceIn(0, asset.width - cropWidth)
        val top = (centerY - cropHeight / 2).coerceIn(0, asset.height - cropHeight)
        return CropRect(left, top, cropWidth, cropHeight)
    }

    fun profileFor(usage: ImageUsage): CropProfile = when (usage) {
        ImageUsage.LIST_FACE -> CropProfile.SQUARE_FACE
        ImageUsage.DETAIL_PORTRAIT, ImageUsage.DIALOG_PORTRAIT -> CropProfile.PORTRAIT_3_4
        ImageUsage.BATTLE_TOKEN, ImageUsage.CHRONICLE_THUMB, ImageUsage.EMBLEM -> CropProfile.SQUARE_CENTER
        ImageUsage.ICON -> CropProfile.SQUARE_CENTER
        ImageUsage.ROOM_BACKGROUND, ImageUsage.EVENT_ART -> CropProfile.LANDSCAPE_16_9
        ImageUsage.KEY_ART -> CropProfile.FIT_INSIDE
    }

    fun memoryCacheKey(
        bundleId: String,
        asset: AssetImage,
        usage: ImageUsage,
        targetPx: Int,
        qualityMode: QualityMode
    ): String = listOf(
        bundleId,
        asset.id.value,
        asset.sha256,
        usage.name,
        effectiveTarget(targetPx, usage, qualityMode).toString(),
        qualityMode.name
    ).joinToString("|")

    fun categoryFor(usage: ImageUsage): AssetCategory = when (usage) {
        ImageUsage.LIST_FACE, ImageUsage.DETAIL_PORTRAIT, ImageUsage.DIALOG_PORTRAIT,
        ImageUsage.BATTLE_TOKEN, ImageUsage.CHRONICLE_THUMB -> AssetCategory.PORTRAIT
        ImageUsage.ICON -> AssetCategory.ICON
        ImageUsage.EMBLEM -> AssetCategory.EMBLEM
        ImageUsage.ROOM_BACKGROUND -> AssetCategory.BACKGROUND
        ImageUsage.EVENT_ART -> AssetCategory.EVENT_ART
        ImageUsage.KEY_ART -> AssetCategory.KEY_ART
    }

    private fun effectiveTarget(targetPx: Int, usage: ImageUsage, quality: QualityMode): Int {
        val clamped = targetPx.coerceAtMost(MAX_TARGET_PX_BY_USAGE.getValue(usage))
        return if (quality == QualityMode.LOW) maxOf(1, clamped / 2) else clamped
    }

    private fun fallbackMatcherOrder(entity: EntityKind, usage: ImageUsage): List<FallbackMatcher> = when (entity) {
        EntityKind.MERCENARY -> when (usage) {
            ImageUsage.BATTLE_TOKEN -> listOf(FallbackMatcher.CLASS, FallbackMatcher.CATEGORY_DEFAULT, FallbackMatcher.GLOBAL_DEFAULT)
            ImageUsage.CHRONICLE_THUMB -> listOf(FallbackMatcher.CATEGORY_DEFAULT, FallbackMatcher.GLOBAL_DEFAULT)
            else -> listOf(FallbackMatcher.SEX, FallbackMatcher.CATEGORY_DEFAULT, FallbackMatcher.GLOBAL_DEFAULT)
        }
        EntityKind.MONSTER -> listOf(FallbackMatcher.MONSTER_FAMILY, FallbackMatcher.CATEGORY_DEFAULT, FallbackMatcher.GLOBAL_DEFAULT)
        EntityKind.DUNGEON -> listOf(FallbackMatcher.REGION, FallbackMatcher.CATEGORY_DEFAULT, FallbackMatcher.GLOBAL_DEFAULT)
        EntityKind.ROOM -> listOf(FallbackMatcher.ROOM_THEME, FallbackMatcher.REGION, FallbackMatcher.CATEGORY_DEFAULT, FallbackMatcher.GLOBAL_DEFAULT)
        EntityKind.ITEM -> listOf(FallbackMatcher.ITEM_TYPE, FallbackMatcher.CATEGORY_DEFAULT, FallbackMatcher.GLOBAL_DEFAULT)
        EntityKind.FACILITY -> listOf(FallbackMatcher.FACILITY_CATEGORY, FallbackMatcher.CATEGORY_DEFAULT, FallbackMatcher.GLOBAL_DEFAULT)
    }

    private val TEXT_FORBIDDEN_USAGES = setOf(ImageUsage.ROOM_BACKGROUND, ImageUsage.EVENT_ART, ImageUsage.KEY_ART)
    private val MAX_TARGET_PX_BY_USAGE = mapOf(
        ImageUsage.LIST_FACE to 256,
        ImageUsage.ICON to 256,
        ImageUsage.EMBLEM to 256,
        ImageUsage.CHRONICLE_THUMB to 256,
        ImageUsage.DETAIL_PORTRAIT to 1024,
        ImageUsage.DIALOG_PORTRAIT to 1024,
        ImageUsage.BATTLE_TOKEN to 256,
        ImageUsage.ROOM_BACKGROUND to 1920,
        ImageUsage.EVENT_ART to 1920,
        ImageUsage.KEY_ART to 1920
    )
}
