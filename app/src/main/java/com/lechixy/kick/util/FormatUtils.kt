package com.lechixy.kick.util

import com.lechixy.kick.data.model.VideoThumbnail
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

object FormatUtils {

    fun formatViewersCount(count: Int?): String {
        if (count == null) return "0"
        // show 24895 like 24.895
        return NumberFormat.getNumberInstance(Locale.US).format(count)
    }
    fun formatFollowersCount(countStr: String?): String {
        val count = countStr?.toDoubleOrNull() ?: return "0"
        return when {
            count >= 1_000_000 -> String.format(Locale.US, "%.1fM", count / 1_000_000)
            count >= 1_000 -> String.format(Locale.US, "%.1fK", count / 1_000)
            else -> count.toLong().toString()
        }
    }

    // Tarihi UTC olarak parse eder ve Epoch millis döndürür
    fun parseUtcStartTimeToMillis(dateString: String?): Long {
        if (dateString.isNullOrBlank()) return 0L
        return runCatching {
            val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC") // Kritik nokta: UTC olarak yorumla
            }
            sdf.parse(dateString)?.time ?: 0L
        }.getOrDefault(0L)
    }

    fun formatDuration(durationMillis: Long): String {
        val totalSeconds = durationMillis / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return if (hours > 0) {
            String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.US, "%02d:%02d", minutes, seconds)
        }
    }

    fun formatLastLive(startTimeStr: String?): String {
        if (startTimeStr.isNullOrBlank()) return "Son zamanlarda yayın yapmadı"

        return try {
            val format = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }
            val date = format.parse(startTimeStr) ?: return "Son zamanlarda yayın yapmadı"
            val now = System.currentTimeMillis()
            val diffMillis = now - date.time

            val diffHours = TimeUnit.MILLISECONDS.toHours(diffMillis)
            val diffDays = TimeUnit.MILLISECONDS.toDays(diffMillis)

            when {
                diffHours < 1 -> "Last live recently"
                diffHours < 24 -> "Last live $diffHours hours ago"
                diffDays == 1L -> "Last live yesterday"
                diffDays < 30 -> "Last live $diffDays days ago"
                else -> "Son zamanlarda yayın yapmadı"
            }
        } catch (e: Exception) {
            "Son zamanlarda yayın yapmadı"
        }
    }

    fun extractVideoThumbnailUrl(thumbnail: VideoThumbnail?): String? {
        if (thumbnail == null) return null
        if (!thumbnail.srcset.isNullOrBlank()) {
            val parts = thumbnail.srcset.split(",")
            if (parts.isNotEmpty()) {
                val candidate = parts.firstOrNull { it.contains("720.webp") } ?: parts.first()
                return candidate.trim().split(" ").firstOrNull()
            }
        }
        if (!thumbnail.src.isNullOrBlank()) {
            return if (thumbnail.src.startsWith("http")) {
                thumbnail.src
            } else {
                "https://images.kick.com/video_thumbnails/${thumbnail.src}"
            }
        }
        return null
    }
}