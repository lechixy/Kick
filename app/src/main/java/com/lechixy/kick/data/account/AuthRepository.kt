package com.lechixy.kick.data.account

import android.content.Context
import android.content.Intent
import com.lechixy.kick.KickWebLoginActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.URLDecoder
import java.util.concurrent.TimeUnit

@Serializable
data class StoredWebSession(
    val userId: Long, val kickCookies: String, val webCookies: String,
    val userAgent: String, val createdAt: String? = null, val profile: AccountProfile? = null
)

/** One device-local website session. Credentials only travel directly to Kick over HTTPS. */
class AuthRepository(
    private val store: SessionStore,
    private val transport: (suspend (Request) -> JsonElement)? = null
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val lock = Any()
    private val client = OkHttpClient.Builder().followRedirects(false).followSslRedirects(false)
        .retryOnConnectionFailure(false).callTimeout(20, TimeUnit.SECONDS).build()
    // Recover stale connections for reads without automatically replaying account mutations.
    private val readClient = client.newBuilder().retryOnConnectionFailure(true).build()
    private var stored = restore()
    private val mutableSession = MutableStateFlow(AccountSession(
        status = if (stored == null) SessionStatus.Guest else SessionStatus.Authenticated,
        profile = stored?.profile
    ))
    val session = mutableSession.asStateFlow()
    val accountCreatedAt: String? get() = session.value.profile?.createdAt ?: stored?.createdAt

    private fun restore(): StoredWebSession? {
        var result = store.read("website_account")?.let {
            runCatching { json.decodeFromString<StoredWebSession>(it) }.getOrNull()
        }
        // Preserve an already verified website login when upgrading from the two-login build.
        if (result == null) {
            val legacy = store.read("web_session")?.let {
                runCatching { json.decodeFromString<StoredWebSession>(it) }.getOrNull()
            }
            val profile = store.read("session")?.let {
                runCatching { json.decodeFromJsonElement<AccountProfile>(json.parseToJsonElement(it).jsonObject.getValue("profile")) }.getOrNull()
            }
            if (legacy != null && legacy.userId == profile?.userId) result = legacy.copy(profile = profile)
        }
        result = result?.takeIf { it.userId > 0 && cookie(it.kickCookies, "session_token") != null &&
            (it.profile == null || it.profile.userId == it.userId) }
        store.write("website_account", result?.let { json.encodeToString(it) })
        for (key in listOf("session", "pending", "web_session")) store.write(key, null)
        return result
    }

    fun beginLogin(context: Context) {
        try { context.startActivity(Intent(context, KickWebLoginActivity::class.java)) }
        catch (_: Exception) { cancelLogin("Kick giriş ekranı açılamadı.") }
    }

    fun startLogin(): Long = synchronized(lock) {
        mutableSession.value = session.value.copy(status = SessionStatus.Connecting,
            error = null, generation = session.value.generation + 1)
        session.value.generation
    }

    fun cancelLogin(message: String? = null) = synchronized(lock) {
        mutableSession.value = session.value.copy(
            status = if (stored == null) SessionStatus.Guest else SessionStatus.Authenticated,
            error = message, generation = session.value.generation + 1)
    }

    suspend fun connect(kickCookies: String, webCookies: String, userAgent: String,
                        expectedGeneration: Long = session.value.generation) {
        val candidate = StoredWebSession(0, kickCookies, webCookies, userAgent)
        require(cookie(kickCookies, "session_token") != null) { "Web session cookie missing" }
        val profile = profileFrom(execute(buildRequest(PROFILE_URL, "GET", null, candidate)).jsonObject)
        synchronized(lock) {
            check(expectedGeneration == session.value.generation) { "Session changed" }
            val verified = candidate.copy(userId = profile.userId, createdAt = profile.createdAt, profile = profile)
            store.write("website_account", json.encodeToString(verified)); stored = verified
            mutableSession.value = AccountSession(SessionStatus.Authenticated, profile,
                generation = session.value.generation + 1)
        }
    }

    suspend fun signOut() = synchronized(lock) { clear(SessionStatus.Guest) }

    private fun clear(status: SessionStatus) {
        store.write("website_account", null); stored = null
        mutableSession.value = AccountSession(status,
            error = if (status == SessionStatus.ReauthenticationRequired) "Oturum süresi doldu. Yeniden giriş yapın." else null,
            generation = session.value.generation + 1)
    }

    suspend fun loadProfile() {
        val generation = session.value.generation
        val profile = profileFrom(request(PROFILE_URL))
        synchronized(lock) {
            check(session.value.signedIn && generation == session.value.generation) { "Session changed" }
            if (profile.userId != stored?.userId) {
                clear(SessionStatus.ReauthenticationRequired)
                error("Account identity changed")
            }
            val updated = stored!!.copy(profile = profile, createdAt = profile.createdAt)
            store.write("website_account", json.encodeToString(updated)); stored = updated
            mutableSession.value = session.value.copy(profile = profile, error = null)
        }
    }

    suspend fun request(url: String, method: String = "GET", body: JsonObject? = null): JsonObject =
        requestJson(url, method, body).jsonObject

    suspend fun requestArray(url: String): JsonArray = requestJson(url, "GET", null).jsonArray

    suspend fun requestElement(url: String): JsonElement = requestJson(url, "GET", null)

    private suspend fun requestJson(url: String, method: String, body: JsonObject?): JsonElement {
        val (current, generation) = synchronized(lock) {
            check(session.value.signedIn) { "Sign in required" }
            (stored ?: error("Sign in required")) to session.value.generation
        }
        val request = buildRequest(url, method, body, current)
        try {
            val result = execute(request)
            synchronized(lock) {
                check(session.value.signedIn && generation == session.value.generation) { "Session changed" }
            }
            return result
        } catch (failure: AccountApiException) {
            if (failure.status == 401) synchronized(lock) {
                if (generation == session.value.generation) clear(SessionStatus.ReauthenticationRequired)
            }
            throw failure
        }
    }

    private fun profileFrom(response: JsonObject): AccountProfile {
        val user = (response["data"] as? JsonObject) ?: response
        val id = (user.string("id") ?: user.string("user_id"))?.toLongOrNull()?.takeIf { it > 0 }
            ?: error("Missing user id")
        val name = (user.string("username") ?: user.string("name"))?.takeIf { it.isNotBlank() }
            ?: error("Missing username")
        val avatar = listOf("profilepic", "profile_pic", "profile_picture", "profile_thumb")
            .firstNotNullOfOrNull { user.string(it)?.takeIf(String::isNotBlank) }
        return AccountProfile(id, name, avatar, user.string("created_at"))
    }

    private fun buildRequest(url: String, method: String, body: JsonObject?, current: StoredWebSession): Request {
        val address = url.toHttpUrl()
        require(address.scheme == "https" && address.host in setOf("kick.com", "web.kick.com") &&
            address.port == 443 && address.username.isEmpty() && address.password.isEmpty())
        val cookies = if (address.host == "kick.com") current.kickCookies else current.webCookies
        val token = cookie(cookies, "session_token") ?: cookie(current.kickCookies, "session_token")
            ?: error("Web session cookie missing")
        val builder = Request.Builder().url(address).header("Accept", "application/json")
            .header("User-Agent", current.userAgent).header("Authorization", "Bearer $token")
            .header("Origin", "https://kick.com").header("Referer", "https://kick.com/")
        if (cookies.isNotBlank()) builder.header("Cookie", cookies)
        cookie(cookies, "XSRF-TOKEN")?.let { builder.header("X-XSRF-TOKEN", it) }
        if (method != "GET") builder.method(method, body?.toString()?.toRequestBody(JSON_TYPE)
            ?: if (method == "POST") "{}".toRequestBody(JSON_TYPE) else null)
        return builder.build()
    }

    private suspend fun execute(request: Request): JsonElement = withContext(Dispatchers.IO) {
        transport?.let { return@withContext it(request) }
        val address = "${request.url.scheme}://${request.url.host}${request.url.encodedPath}"
        val start = System.nanoTime()
        accountDiagnostic("KickAccountApi", "--> ${request.method} $address")
        try { (if (request.method == "GET") readClient else client).newCall(request).execute().use { response ->
            val elapsedMs = (System.nanoTime() - start) / 1_000_000
            accountDiagnostic("KickAccountApi", "<-- ${response.code} $address (${elapsedMs}ms, contentType=${response.body.contentType()})")
            if (!response.isSuccessful) throw AccountApiException(response.code, response.header("Retry-After")?.toLongOrNull())
            val text = response.body.string()
            if (text.isBlank()) buildJsonObject {} else json.parseToJsonElement(text)
        } } catch (error: Exception) {
            if (error is kotlinx.coroutines.CancellationException) throw error
            accountDiagnostic("KickAccountApi", "Request failed: ${request.method} $address type=${error.javaClass.simpleName} reason=${accountNetworkFailureCode(error)}")
            throw error
        }
    }

    companion object {
        private const val PROFILE_URL = "https://kick.com/api/v1/user"
        private val JSON_TYPE = "application/json".toMediaType()
        internal fun cookie(header: String, name: String): String? = header.split(';').map { it.trim().split('=', limit = 2) }
            .firstOrNull { it.size == 2 && it[0] == name }?.get(1)?.takeIf { it.isNotBlank() }
            ?.let { runCatching { URLDecoder.decode(it.replace("+", "%2B"), "UTF-8") }.getOrNull() }
    }
}

fun Throwable.userMessage(): String = when (this) {
    is AccountApiException -> message!!
    is java.io.IOException -> "Bağlantı kurulamadı. İnternet bağlantınızı kontrol edin."
    else -> "İşlem tamamlanamadı. Yeniden deneyin."
}
