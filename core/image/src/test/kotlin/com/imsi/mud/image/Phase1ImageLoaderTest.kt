package com.imsi.mud.image

import android.graphics.Canvas
import coil3.Image
import coil3.memory.MemoryCache
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Phase1ImageLoaderTest {
    @Test
    fun `P1-PT-001 Coil memory cache has a hard bound hit and LRU eviction`() {
        val cache = createPhase1MemoryCache(maxSizeBytes = 1_024L)
        val first = MemoryCache.Key("bundle-a|first")
        val second = MemoryCache.Key("bundle-a|second")

        cache[first] = MemoryCache.Value(SizedImage(600L))
        assertNotNull("first lookup must hit Coil's memory cache", cache[first])
        cache[second] = MemoryCache.Value(SizedImage(600L))

        assertEquals(1_024L, cache.maxSize)
        assertTrue(cache.size <= cache.maxSize)
        assertNull("least recently used entry must be evicted at the hard bound", cache[first])
        assertNotNull(cache[second])
    }

    private class SizedImage(override val size: Long) : Image {
        override val width: Int = 1
        override val height: Int = 1
        override val shareable: Boolean = false
        override fun draw(canvas: Canvas) = Unit
    }
}
