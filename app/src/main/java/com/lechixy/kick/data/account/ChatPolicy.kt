package com.lechixy.kick.data.account

import java.time.Instant

data class Mode(val enabled: Boolean, val durationSeconds: Long = 0)
data class ChatSettings(
    val allowLinks: Boolean,
    val emotesOnly: Boolean,
    val subscribersOnly: Boolean,
    val followersOnly: Mode,
    val minimumAccountAge: Mode,
    val slowMode: Mode,
    val rules: String
)
data class ChannelMembership(
    val following: Boolean, val followingSince: Instant?, val subscriber: Boolean,
    val banned: Boolean, val privileged: Boolean
)

object ChatPolicy {
    // Unicode extended grapheme clusters (Java/Android regex \X), not UTF-16 code units.
    fun messageError(content: String): String? = when {
        content.isBlank() -> "Mesaj boş olamaz."
        Regex("\\X").findAll(content).count() > 500 -> "Mesaj en fazla 500 karakter olabilir."
        content.toByteArray(Charsets.UTF_8).size > 2048 -> "Mesaj UTF-8 olarak 2048 baytı aşamaz."
        else -> null
    }

    fun restriction(settings: ChatSettings, member: ChannelMembership, createdAt: Instant?,
                    now: Instant, lastSentAt: Instant?, content: String? = null): String? {
        if (member.banned) return "Bu kanalda mesaj gönderme izniniz yok (yasak/zaman aşımı)."
        if (settings.emotesOnly) return "Yalnızca emote modu açık. Metin gönderimi kapalı."
        if (!member.privileged) {
            if (settings.subscribersOnly && !member.subscriber) return "Mesaj göndermek için abone olmalısınız."
            if (settings.followersOnly.enabled) {
                if (!member.following) return "Mesaj göndermek için kanalı takip etmelisiniz."
                val since = member.followingSince ?: return "Takip süreniz doğrulanamadı."
                if (now.isBefore(since.plusSeconds(settings.followersOnly.durationSeconds))) return "Gerekli takip süresi henüz dolmadı."
            }
            if (settings.minimumAccountAge.enabled) {
                val created = createdAt ?: return "Hesap yaşınız doğrulanamadığı için gönderim kapalı."
                if (now.isBefore(created.plusSeconds(settings.minimumAccountAge.durationSeconds))) return "Hesabınızın yaşı kanal şartını karşılamıyor."
            }
            if (settings.slowMode.enabled && lastSentAt != null) {
                val remaining = (java.time.Duration.between(now,
                    lastSentAt.plusSeconds(settings.slowMode.durationSeconds)).toMillis() + 999) / 1000
                if (remaining > 0) return "Yavaş mod: $remaining saniye bekleyin."
            }
        }
        if (content != null && !settings.allowLinks && Regex("(?i)(https?://|www\\.|\\b[a-z0-9-]+(?:\\.[a-z0-9-]+)*\\.[a-z]{2,}(?:[/\\s]|$))").containsMatchIn(content)) {
            return "Bu kanalda bağlantı paylaşımı kapalı."
        }
        return null
    }
}
