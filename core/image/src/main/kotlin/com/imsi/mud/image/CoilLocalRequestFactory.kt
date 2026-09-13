package com.imsi.mud.image

import android.content.Context
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import com.imsi.mud.content.CropProfile

/** Builds a local-only Coil request. Loading UI and accessibility copy stay with the caller. */
fun LocalImageRequest.toCoilImageRequest(context: Context): ImageRequest {
    val (decodeWidth, decodeHeight) = decodeSize()
    return ImageRequest.Builder(context)
        .data(dataUri)
        .size(decodeWidth, decodeHeight)
        .memoryCacheKey(memoryCacheKey)
        .diskCachePolicy(CachePolicy.DISABLED)
        .networkCachePolicy(CachePolicy.DISABLED)
        .build()
}

internal fun LocalImageRequest.decodeSize(): Pair<Int, Int> = when (cropProfile) {
    CropProfile.SQUARE_FACE,
    CropProfile.SQUARE_CENTER -> targetPx to targetPx

    CropProfile.PORTRAIT_3_4 -> maxOf(1, targetPx * 3 / 4) to targetPx
    CropProfile.LANDSCAPE_16_9 -> targetPx to maxOf(1, targetPx * 9 / 16)
    CropProfile.FIT_INSIDE -> fitInsideSize(crop.width, crop.height, targetPx)
}

private fun fitInsideSize(width: Int, height: Int, longEdge: Int): Pair<Int, Int> =
    if (width >= height) {
        longEdge to maxOf(1, (height.toLong() * longEdge / width).toInt())
    } else {
        maxOf(1, (width.toLong() * longEdge / height).toInt()) to longEdge
    }
