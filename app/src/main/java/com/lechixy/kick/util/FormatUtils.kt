package com.lechixy.kick.util

import android.text.format.DateUtils
import android.icu.number.NumberFormatter
import android.icu.number.Notation
import android.icu.number.Precision
import com.lechixy.kick.data.model.VideoThumbnail
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

object FormatUtils {

    private val compactNumbers by lazy {
        NumberFormatter.with().notation(Notation.compactShort()).precision(Precision.maxFraction(1))
    }

    fun formatViewersCount(count: Int?, locale: Locale = Locale.getDefault()): String =
        NumberFormat.getIntegerInstance(locale).format(count ?: 0)

    fun formatFollowersCount(countStr: String?, locale: Locale = Locale.getDefault()): String {
        val count = countStr?.toBigDecimalOrNull() ?: return NumberFormat.getIntegerInstance(locale).format(0)
        return compactNumbers.locale(locale).format(count).toString()
    }

    // Tarihi UTC olarak parse eder ve Epoch millis döndürür
    fun parseUtcStartTimeToMillis(dateString: String?): Long {
        if (dateString.isNullOrBlank()) return 0L
        val normalized = dateString
            .trim()
            .replace('T', ' ')
            .substringBefore('.')
            .trimEnd('Z')
        return runCatching {
            val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC") // Kritik nokta: UTC olarak yorumla
            }
            sdf.parse(normalized)?.time ?: 0L
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

    fun formatLiveOffset(offsetMs: Long): String {
        val totalSeconds = (offsetMs / 1000).coerceAtLeast(0)
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return when {
            hours > 0 -> String.format(Locale.US, "-%d:%02d:%02d", hours, minutes, seconds)
            minutes > 0 -> String.format(Locale.US, "-%02d:%02d", minutes, seconds)
            else -> String.format(Locale.US, "-00:%02d", seconds)
        }
    }

    fun formatLastLive(
        startTimeStr: String?,
        showPrefix: Boolean = false
    ): String {
        if (startTimeStr.isNullOrBlank()) {
            return if (showPrefix) {
                // Bu metinleri strings.xml üzerinden yerelleştir.
                "Son zamanlarda yayın yapmadı"
            } else {
                "Bilinmiyor"
            }
        }

        return try {
            val format = SimpleDateFormat(
                "yyyy-MM-dd HH:mm:ss",
                Locale.US
            ).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }

            val date = format.parse(startTimeStr)
                ?: return "Bilinmiyor"

            val relativeTime = DateUtils.getRelativeTimeSpanString(
                date.time,
                System.currentTimeMillis(),
                DateUtils.MINUTE_IN_MILLIS,
                DateUtils.FORMAT_ABBREV_RELATIVE
            ).toString()

            if (showPrefix) {
                "Last live ${relativeTime.take( 1).lowercase()}${relativeTime.drop(1)}"
            } else {
                relativeTime
            }
        } catch (e: Exception) {
            "Bilinmiyor"
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