package com.lechixy.kick.util

import android.content.Context
import android.graphics.Bitmap
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.bitmapConfig
import coil3.request.crossfade
import coil3.size.Precision
import coil3.size.Size

fun buildOptimizedImageRequest(
    context: Context,
    data: Any?,
    targetWidthDp: Int = 360,
    targetHeightDp: Int = 202
): ImageRequest {
    val density = context.resources.displayMetrics.density
    val pxWidth = (targetWidthDp * density).toInt()
    val pxHeight = (targetHeightDp * density).toInt()

    return ImageRequest.Builder(context)
        .data(data)
        .size(Size(pxWidth, pxHeight))
        .precision(Precision.INEXACT)
        .crossfade(true)
        .memoryCachePolicy(CachePolicy.ENABLED)
        .diskCachePolicy(CachePolicy.ENABLED)
        .bitmapConfig(Bitmap.Config.RGB_565) // Bellek kullanımını yarıya düşürür ve kaydırmayı rahatlatır
        .build()
}