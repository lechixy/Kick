package com.lechixy.kick.data.account

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

enum class SessionStatus { Guest, Connecting, Authenticated, ReauthenticationRequired }

data class AccountSession(
    val status: SessionStatus = SessionStatus.Guest,
    val profile: AccountProfile? = null,
    val error: String? = null,
    val generation: Long = 0
) {
    val signedIn get() = status == SessionStatus.Authenticated
}

@Serializable
data class AccountProfile(
    @SerialName("user_id") val userId: Long,
    val name: String,
    @SerialName("profile_picture") val avatar: String? = null,
    val createdAt: String? = null
)

class AccountApiException(val status: Int, val retryAfterSeconds: Long? = null) : Exception(
    when (status) {
        401 -> "Oturum doğrulanamadı. Yeniden giriş yapın."
        403 -> "Kick bu işlem için erişim izni vermedi."
        404 -> "Bu özellik veya kanal bulunamadı."
        422 -> "Kick bu isteği kabul etmedi. Mesajı ve kanal kurallarını kontrol edin."
        429 -> "Çok fazla istek. Lütfen biraz bekleyin."
        else -> "Kick isteği başarısız (HTTP $status)."
    }
)

internal fun JsonObject.string(key: String): String? =
    (get(key) as? kotlinx.serialization.json.JsonPrimitive)?.takeUnless { it is kotlinx.serialization.json.JsonNull }?.content
