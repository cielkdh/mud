package com.imsi.mud.image

import android.content.Context
import coil3.ImageLoader
import coil3.memory.MemoryCache

const val PHASE1_IMAGE_MEMORY_CACHE_MAX_BYTES: Long = 64L * 1024L * 1024L

/** Coil owns the cache; P1 only fixes its memory bound and disables a duplicate disk cache. */
fun createPhase1ImageLoader(context: Context): ImageLoader = ImageLoader.Builder(context)
    .memoryCache { createPhase1MemoryCache() }
    .diskCache(null as coil3.disk.DiskCache?)
    .build()

internal fun createPhase1MemoryCache(maxSizeBytes: Long = PHASE1_IMAGE_MEMORY_CACHE_MAX_BYTES): MemoryCache {
    require(maxSizeBytes > 0L)
    return MemoryCache.Builder()
        .maxSizeBytes(maxSizeBytes)
        .weakReferencesEnabled(false)
        .build()
}

/** Clears Coil's bounded cache when the immutable InstalledBundle identity changes. */
class BundleScopedCoilCache {
    private var activeBundleId: String? = null

    @Synchronized
    fun activate(imageLoader: ImageLoader, bundleId: String) {
        require(bundleId.isNotBlank())
        if (activeBundleId != null && activeBundleId != bundleId) {
            imageLoader.memoryCache?.clear()
        }
        activeBundleId = bundleId
    }
}
