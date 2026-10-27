package com.lechixy.kick.account

import com.lechixy.kick.data.account.*
import com.lechixy.kick.data.model.ChannelDetail
import com.lechixy.kick.data.model.ChatroomDetail
import kotlinx.coroutines.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.Request
import okio.Buffer
import org.junit.Assert.*
import org.junit.Test

class AccountChatRepositoryTest {
    @Test fun fetchesBothBalancesFromTheirRespectiveHosts() = runBlocking {
        val auth = signedInAuth { request ->
            when (request.url.encodedPath) {
                "/api/v2/channels/tester/points" -> {
                    assertEquals("kick.com", request.url.host)
                    objectJson("""{"data":{"points":56085},"message":"OK"}""")
                }
                "/api/v1/kicks/balance" -> {
                    assertEquals("web.kick.com", request.url.host)
                    objectJson("""{"data":{"balance":{"available":0,"user_id":42}}}""")
                }
                else -> error("Unexpected request")
            }
        }
        val repo = AccountChatRepository(auth)
        assertEquals(56085L, repo.points(channel))
        assertEquals(0L, repo.kicksBalance())
    }

    @Test fun cooldownReflectsModeChangesPrivilegesAndSessionReset() = runBlocking {
        val auth = signedInAuth { request ->
            if (request.method == "POST") objectJson("{}") else read(request)
        }
        val repo = AccountChatRepository(auth)
        val access = repo.access(channel)
        assertEquals(0L, repo.cooldown(channel.id, access).remainingMillis)
        repo.send(channel, "hello")
        val cooldown = repo.cooldown(channel.id, access)
        assertTrue(cooldown.remainingMillis in 1..5000)
        assertEquals(5000L, cooldown.totalMillis)
        assertEquals(0L, repo.cooldown(channel.id, access.copy(member = access.member.copy(privileged = true))).remainingMillis)
        assertEquals(0L, repo.cooldown(channel.id, access.copy(settings = access.settings.copy(slowMode = Mode(false)))).remainingMillis)
        repo.clearSession()
        assertEquals(0L, repo.cooldown(channel.id, access).remainingMillis)
    }
    private val channel = ChannelDetail(id = 100, userId = 999, slug = "tester", chatroom = ChatroomDetail(id = 777))
    private val settings = """{"data":{
        "allow_links":{"enabled":true},"emotes_only_mode":{"enabled":false},
        "subscribers_only_mode":{"enabled":false},"followers_only_mode":{"enabled":false},
        "minimum_account_age":{"enabled":false},"slow_mode":{"enabled":true,"duration_seconds":5}}}"""
    private val membership = """{"subscription":null,"banned":null,"is_following":true,
        "is_broadcaster":false,"is_moderator":false,"is_super_admin":false}"""
    private fun read(request: Request) = when {
        request.url.encodedPath.endsWith("/settings") -> objectJson(settings)
        request.url.encodedPath.endsWith("/me") -> objectJson(membership)
        else -> objectJson("""{"data":{"identity":{"color":"#ff0000","badges_v2":[]}}}""")
    }
    @Test fun queuedMessageCannotBeSentByNewAccount() = runBlocking {
        val store = MemorySessionStore()
        store.write("website_account", Json.encodeToString(StoredWebSession(42, "session_token=old", "session_token=old", "agent", profile = AccountProfile(42, "old"))))
        val entered = CompletableDeferred<Unit>()
        val finish = CompletableDeferred<Unit>()
        var posts = 0
        val auth = AuthRepository(store) { request ->
            when {
                request.url.encodedPath == "/api/v1/user" -> objectJson("""{"id":43,"username":"new"}""")
                request.method == "POST" -> {
                    posts++; entered.complete(Unit); finish.await()
                    objectJson("""{"data":{"is_sent":true,"message_id":"id"}}""")
                }
                else -> read(request)
            }
        }
        val repo = AccountChatRepository(auth)
        val first = async { runCatching { repo.send(channel, "first") } }
        entered.await()
        val queued = async(start = CoroutineStart.UNDISPATCHED) { runCatching { repo.send(channel, "queued") } }
        auth.signOut()
        auth.connect("session_token=new", "session_token=new", "agent")
        assertTrue(auth.session.value.signedIn)
        finish.complete(Unit)
        assertTrue(first.await().isFailure)
        assertTrue(queued.await().isFailure)
        assertEquals(1, posts)
    }
    @Test fun usesChatroomIdAndAppliesSuccessfulSendCooldown() = runBlocking {
        var posts = 0
        val auth = signedInAuth { request ->
            if (request.method == "POST") {
                posts++
                val buffer = Buffer(); request.body!!.writeTo(buffer)
                val body = objectJson(buffer.readUtf8())
                assertEquals("/api/v2/messages/send/777", request.url.encodedPath)
                assertEquals("kick.com", request.url.host)
                assertEquals("Bearer access", request.header("Authorization"))
                assertNull(body["broadcaster_user_id"])
                assertEquals("\"message\"", body["type"].toString())
                objectJson("""{"data":{"is_sent":true,"message_id":"id"}}""")
            } else read(request)
        }
        val repo = AccountChatRepository(auth)
        assertEquals("id", repo.send(channel, "hello"))
        assertTrue(runCatching { repo.send(channel, "again") }.isFailure)
        assertEquals(1, posts)
    }
    @Test fun successfulWebResponseCanOmitMessageIdAndMissingChatroomPreventsPost() = runBlocking {
        var posts = 0
        val auth = signedInAuth { request ->
            if (request.method == "POST") { posts++; objectJson("{}") } else read(request)
        }
        val repo = AccountChatRepository(auth)
        assertTrue(runCatching { repo.send(channel.copy(chatroom = null), "hello") }.isFailure)
        assertEquals(0, posts)
        assertNull(repo.send(channel, "hello"))
        assertEquals(1, posts)
        assertTrue(runCatching { repo.send(channel, "again") }.isFailure)
        assertEquals(1, posts)
    }
    @Test fun webRejectionDoesNotStartSuccessfulSendCooldown() = runBlocking {
        var posts = 0
        val auth = signedInAuth { request ->
            if (request.method == "POST") {
                posts++
                objectJson("""{"status":{"error":true}}""")
            } else read(request)
        }
        val repo = AccountChatRepository(auth)
        repeat(2) { assertTrue(runCatching { repo.send(channel, "hello") }.isFailure) }
        assertEquals(2, posts)
    }
    @Test fun inaccessibleOrChangedSettingsPreventPost() = runBlocking {
        var posts = 0
        var forbid = false
        val auth = signedInAuth { request ->
            if (request.method == "POST") { posts++; error("Must not post") }
            if (request.url.encodedPath.endsWith("/settings")) {
                if (forbid) objectJson(settings.replace("\"emotes_only_mode\":{\"enabled\":false}", "\"emotes_only_mode\":{\"enabled\":true}"))
                else throw AccountApiException(403)
            } else read(request)
        }
        val repo = AccountChatRepository(auth)
        assertTrue(runCatching { repo.send(channel, "hello") }.isFailure)
        forbid = true
        assertTrue(runCatching { repo.send(channel, "hello") }.isFailure)
        assertEquals(0, posts)
    }
    @Test fun ambiguousPostIsNeverReplayedAnd429PreservesCooldown() = runBlocking {
        var posts = 0
        var rateLimited = false
        val auth = signedInAuth { request ->
            if (request.method == "POST") {
                posts++
                if (rateLimited) throw AccountApiException(429, 10)
                throw java.io.IOException("timeout")
            } else read(request)
        }
        val repo = AccountChatRepository(auth)
        assertTrue(runCatching { repo.send(channel, "hello") }.isFailure)
        assertEquals(1, posts)
        assertTrue(runCatching { repo.send(channel, "hello") }.isFailure)
        assertEquals(1, posts)
        repo.clearSession(); rateLimited = true
        assertTrue(runCatching { repo.send(channel, "hello") }.isFailure)
        assertEquals(2, posts)
        assertTrue(repo.restriction(channel.id, repo.access(channel))!!.contains("Gönderim sınırı"))
        val cooldown = repo.cooldown(channel.id, repo.access(channel))
        assertEquals(10000L, cooldown.totalMillis)
        assertTrue(cooldown.remainingMillis in 1..10000)
    }
}
