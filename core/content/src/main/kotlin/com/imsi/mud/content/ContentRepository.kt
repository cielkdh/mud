package com.imsi.mud.content

import java.util.Collections

/** Runtime metadata only; the platform adapter owns opening database files and image bytes. */
data class InstalledBundle(
    val bundleId: String,
    val contentVersion: String,
    val balanceVersion: String,
    val assetManifestSha256: String? = null,
    val artifactFileSha256: String? = null
) {
    init {
        requireNfc(bundleId)
        requireNfc(contentVersion)
        requireNfc(balanceVersion)
        assetManifestSha256?.let(::requireSha256)
        artifactFileSha256?.let(::requireSha256)
    }
}

enum class AliasPolicy { REMAP, TOMBSTONE }

data class ContentAlias(
    val oldId: ContentId,
    val newId: ContentId?,
    val policy: AliasPolicy,
    val reason: String
) {
    init {
        requireNfc(reason)
        require((policy == AliasPolicy.REMAP) == (newId != null)) { "remap aliases require a target and tombstones must not have one" }
    }
}

enum class AssetCategory { PORTRAIT, ICON, EMBLEM, BACKGROUND, EVENT_ART, KEY_ART }

enum class ImageUsage {
    LIST_FACE, DETAIL_PORTRAIT, DIALOG_PORTRAIT, BATTLE_TOKEN, CHRONICLE_THUMB, ICON, EMBLEM,
    ROOM_BACKGROUND, EVENT_ART, KEY_ART
}

data class AssetImage(
    val id: AssetId,
    val category: AssetCategory,
    val relativePath: String,
    val width: Int,
    val height: Int,
    val byteSize: Long,
    val sha256: String,
    val focalXppm: Int? = null,
    val focalYppm: Int? = null
) {
    init {
        requireNfc(relativePath)
        require(width > 0 && height > 0) { "asset dimensions must be positive" }
        require(byteSize >= 0) { "asset byte size must not be negative" }
        require(sha256.matches(Regex("[0-9a-f]{64}"))) { "asset hash must be lowercase SHA-256" }
        focalXppm?.let { require(it in 0..1_000_000) { "focal x must be within 0..1000000" } }
        focalYppm?.let { require(it in 0..1_000_000) { "focal y must be within 0..1000000" } }
    }
}

data class AssetBinding(
    val templateId: ContentId,
    val usage: ImageUsage,
    val assetId: AssetId,
    val priority: Int
) {
    init { require(priority >= 0) { "asset binding priority must not be negative" } }
}

enum class FallbackMatcher {
    SEX, ARCHETYPE, CLASS, MONSTER_FAMILY, REGION, ROOM_THEME, ITEM_TYPE, FACILITY_CATEGORY,
    CATEGORY_DEFAULT, GLOBAL_DEFAULT
}

data class AssetFallback(
    val usage: ImageUsage,
    val matcher: FallbackMatcher,
    val matcherValue: String?,
    val assetId: AssetId,
    val priority: Int
) {
    init {
        require(priority >= 0) { "asset fallback priority must not be negative" }
        require(if (matcher in DEFAULT_MATCHERS) matcherValue == null else !matcherValue.isNullOrBlank()) {
            "default fallback has no matcher value and contextual fallback requires one"
        }
        matcherValue?.let(::requireNfc)
    }
}

class IncompatibleContent(val code: String, detail: String = code) : IllegalStateException(detail)

/**
 * The sole P1 data-facing contract. Implementations must return the documented stable orders.
 */
interface ContentRepository : AutoCloseable {
    fun findTemplate(id: ContentId): ContentTemplate?
    fun listTemplates(kind: ContentKind): List<ContentTemplate>
    fun findAlias(oldId: ContentId): ContentAlias?
    fun findAssetBindings(templateId: ContentId, usage: ImageUsage): List<AssetBinding>
    fun findAsset(id: AssetId): AssetImage?
    fun listAssetFallbacks(usage: ImageUsage): List<AssetFallback>
}

/**
 * Deterministic JVM oracle for P1. The state gate models the production ownership contract:
 * once close starts, no new read may enter; close waits for active reads and is idempotent.
 */
class InMemoryContentRepository(
    val installedBundle: InstalledBundle,
    templates: Iterable<ContentTemplate>,
    aliases: Iterable<ContentAlias>,
    bindings: Iterable<AssetBinding>,
    assets: Iterable<AssetImage>,
    fallbacks: Iterable<AssetFallback>,
    beforeRead: (() -> Unit)? = null
) : ContentRepository {
    private val beforeRead = beforeRead
    private val templatesById = indexUnique(templates, ContentTemplate::id, "duplicate template id")
    private val aliasesByOldId = indexUnique(aliases, ContentAlias::oldId, "duplicate alias old id")
    private val assetsById = indexUnique(assets, AssetImage::id, "duplicate asset id")
    private val bindingsByTemplateUsage: Map<Pair<ContentId, ImageUsage>, List<AssetBinding>> = bindings
        .groupBy { it.templateId to it.usage }
        .mapValues { (_, values) ->
            require(values.map(AssetBinding::priority).distinct().size == values.size) { "duplicate asset binding priority" }
            immutable(values.sortedWith(compareBy<AssetBinding>({ it.priority }, { it.assetId.value })))
        }
    private val fallbacksByUsage: Map<ImageUsage, List<AssetFallback>> = fallbacks
        .groupBy(AssetFallback::usage)
        .mapValues { (_, values) ->
            require(values.map { Triple(it.matcher, it.matcherValue, it.priority) }.distinct().size == values.size) {
                "duplicate asset fallback matcher and priority"
            }
            immutable(values.sortedWith(compareBy<AssetFallback>({ it.matcher.ordinal }, { it.matcherValue ?: "" }, { it.priority }, { it.assetId.value })))
        }

    init {
        aliasesByOldId.values.forEach { alias ->
            alias.newId?.let { target -> require(templatesById.containsKey(target)) { "alias target template is missing" } }
        }
        bindingsByTemplateUsage.values.flatten().forEach { binding ->
            require(templatesById.containsKey(binding.templateId)) { "asset binding template is missing" }
            require(assetsById.containsKey(binding.assetId)) { "asset binding asset is missing" }
        }
        fallbacksByUsage.values.flatten().forEach { fallback ->
            require(assetsById.containsKey(fallback.assetId)) { "asset fallback asset is missing" }
        }
    }

    private val lifecycleLock = Object()
    private var lifecycle = Lifecycle.OPEN
    private var activeReads = 0

    override fun findTemplate(id: ContentId): ContentTemplate? = read { templatesById[id] }

    override fun listTemplates(kind: ContentKind): List<ContentTemplate> = read {
        immutable(templatesById.values.filter { it.kind == kind }.sortedBy { it.id.value })
    }

    override fun findAlias(oldId: ContentId): ContentAlias? = read { aliasesByOldId[oldId] }

    override fun findAssetBindings(templateId: ContentId, usage: ImageUsage): List<AssetBinding> = read {
        bindingsByTemplateUsage[templateId to usage] ?: emptyList()
    }

    override fun findAsset(id: AssetId): AssetImage? = read { assetsById[id] }

    override fun listAssetFallbacks(usage: ImageUsage): List<AssetFallback> = read {
        fallbacksByUsage[usage] ?: emptyList()
    }

    override fun close() {
        synchronized(lifecycleLock) {
            when (lifecycle) {
                Lifecycle.CLOSED -> return
                Lifecycle.CLOSING -> {
                    while (lifecycle != Lifecycle.CLOSED) lifecycleLock.wait()
                    return
                }
                Lifecycle.OPEN -> lifecycle = Lifecycle.CLOSING
            }
            while (activeReads > 0) lifecycleLock.wait()
            lifecycle = Lifecycle.CLOSED
            lifecycleLock.notifyAll()
        }
    }

    private fun <T> read(block: () -> T): T {
        synchronized(lifecycleLock) {
            if (lifecycle != Lifecycle.OPEN) throw IncompatibleContent("ContentRepositoryClosed")
            activeReads++
        }
        return try {
            beforeRead?.invoke()
            block()
        } finally {
            synchronized(lifecycleLock) {
                activeReads--
                if (activeReads == 0) lifecycleLock.notifyAll()
            }
        }
    }

    private enum class Lifecycle { OPEN, CLOSING, CLOSED }
}

private fun <K, V> indexUnique(values: Iterable<V>, key: (V) -> K, error: String): Map<K, V> {
    val indexed = linkedMapOf<K, V>()
    values.forEach { value -> require(indexed.put(key(value), value) == null) { error } }
    return Collections.unmodifiableMap(indexed)
}

private fun <T> immutable(values: List<T>): List<T> = Collections.unmodifiableList(values.toList())

private val DEFAULT_MATCHERS = setOf(FallbackMatcher.CATEGORY_DEFAULT, FallbackMatcher.GLOBAL_DEFAULT)

private fun requireSha256(value: String) {
    require(value.matches(Regex("[0-9a-f]{64}"))) { "hash must be lowercase SHA-256" }
}
