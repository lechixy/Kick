package com.lechixy.kick.account

import com.lechixy.kick.data.account.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import okhttp3.Request

class MemorySessionStore : SessionStore {
    private val data = mutableMapOf<String, String>()
    @Synchronized override fun read(name: String) = data[name]
    @Synchronized override fun write(name: String, value: String?) { if (value == null) data.remove(name) else data[name] = value }
}

fun objectJson(value: String): JsonObject = Json.parseToJsonElement(value) as JsonObject

fun signedInAuth(transport: suspend (Request) -> JsonObject): AuthRepository {
    val store = MemorySessionStore()
    store.write("website_account", Json.encodeToString(StoredWebSession(42, "session_token=access", "session_token=access", "test-agent",
        "2020-01-01T00:00:00Z", AccountProfile(42, "tester", createdAt = "2020-01-01T00:00:00Z"))))
    return AuthRepository(store, transport)
}
