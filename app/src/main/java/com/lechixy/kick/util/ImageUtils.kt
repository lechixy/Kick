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

/**
 * Srcset veya resim URL'lerinden belirtilen hedef genişliğe (targetWidthPx)
 * en uygun responsive resim URL'sini seçer.
 */
fun getResponsiveImageUrl(
    srcset: String?,
    src: String? = null,
    url: String? = null,
    responsive: String? = null,
    targetWidthPx: Int = 1080
): String? {
    val rawSrcset = srcset?.takeIf { it.isNotBlank() }
        ?: responsive?.takeIf { it.isNotBlank() }
        ?: src?.takeIf { isSrcsetLike(it) }
        ?: url?.takeIf { isSrcsetLike(it) }

    if (!rawSrcset.isNullOrBlank()) {
        val parsedUrl = parseSrcsetAndSelectBest(rawSrcset, targetWidthPx)
        if (!parsedUrl.isNullOrBlank()) {
            return formatImageUrl(parsedUrl)
        }
    }

    val fallback = src?.takeIf { it.isNotBlank() }
        ?: url?.takeIf { it.isNotBlank() }
        ?: responsive?.takeIf { it.isNotBlank() }

    if (!fallback.isNullOrBlank()) {
        return formatImageUrl(fallback)
    }

    return null
}

private fun isSrcsetLike(str: String): Boolean {
    return str.contains(",") || (str.contains(" ") && (str.contains("http") || str.contains(".webp") || str.contains(".jpg") || str.contains(".png")))
}

private data class SrcsetCandidate(
    val url: String,
    val widthPx: Int
)

private fun parseSrcsetAndSelectBest(srcset: String, targetWidthPx: Int): String? {
    val candidates = srcset.split(",")
        .mapNotNull { item ->
            val trimmed = item.trim()
            if (trimmed.isBlank()) return@mapNotNull null
            val parts = trimmed.split("\\s+".toRegex())
            if (parts.isEmpty()) return@mapNotNull null
            val candidateUrl = parts[0].trim()
            if (candidateUrl.isBlank()) return@mapNotNull null

            var widthPx = 0
            if (parts.size > 1) {
                val descriptor = parts[1].lowercase()
                if (descriptor.endsWith("w")) {
                    widthPx = descriptor.removeSuffix("w").toIntOrNull() ?: 0
                } else if (descriptor.endsWith("x")) {
                    val density = descriptor.removeSuffix("x").toFloatOrNull() ?: 1f
                    widthPx = (density * 360).toInt()
                }
            }
            if (widthPx == 0) {
                widthPx = extractWidthFromUrl(candidateUrl)
            }
            SrcsetCandidate(candidateUrl, widthPx)
        }

    if (candidates.isEmpty()) return null

    if (targetWidthPx > 0) {
        val candidatesWithWidth = candidates.filter { it.widthPx > 0 }
        if (candidatesWithWidth.isNotEmpty()) {
            val suitable = candidatesWithWidth.filter { it.widthPx >= targetWidthPx }
            if (suitable.isNotEmpty()) {
                return suitable.minByOrNull { it.widthPx }?.url
            }
            return candidatesWithWidth.maxByOrNull { it.widthPx }?.url
        }
    }

    return candidates.lastOrNull()?.url ?: candidates.firstOrNull()?.url
}

private fun extractWidthFromUrl(url: String): Int {
    val match = Regex("""(?:responsive_|_|/|-)(\d{3,4})(?:\.|\b)""").find(url)
    return match?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 0
}

private fun formatImageUrl(url: String): String {
    val trimmed = url.trim()
    return when {
        trimmed.startsWith("http://") || trimmed.startsWith("https://") -> trimmed
        trimmed.startsWith("//") -> "https:$trimmed"
        trimmed.startsWith("/") -> "https://images.kick.com$trimmed"
        else -> "https://images.kick.com/$trimmed"
    }
}
