package com.lechixy.kick

import com.lechixy.kick.data.model.*
import com.lechixy.kick.data.remote.KickApi
import com.lechixy.kick.data.repository.KickRepository
import com.lechixy.kick.ui.screens.channel.safeChannelUrl
import com.lechixy.kick.ui.screens.channel.socialChannelUrl
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import java.lang.reflect.Proxy
import org.junit.Assert.*
import org.junit.Test

class ChannelAboutDataTest {
    @Test fun aboutFetchesOnlySubresourcesAndSortsLinks() = runBlocking {
        val calls = mutableListOf<String>()
        val api = Proxy.newProxyInstance(KickApi::class.java.classLoader, arrayOf(KickApi::class.java)) { _, method, _ ->
            calls += method.name
            when (method.name) {
                "getChannelLinks" -> Json.parseToJsonElement("""{"data":[{"id":2,"channel_id":7,"order":2},{"id":1,"channel_id":7,"order":1}]}""")
                "getChannelGoals" -> Json.parseToJsonElement("""{"data":{"goals":[{"id":"g","channel_id":"7","type":"subscriptions","status":"active","target_value":4000,"current_value":3583}]}}""")
                else -> error("Core channel must not be fetched: ${method.name}")
            }
        } as KickApi
        val repository = KickRepository(api)
        assertEquals(listOf(1L, 2L), repository.getChannelLinks("tester").map { it.id })
        assertEquals(3583, repository.getChannelGoals("tester").single().currentValue)
        assertEquals(listOf("getChannelLinks", "getChannelGoals"), calls)
    }

    @Test fun socialHandlesAndFullUrlsResolveWithoutAllowingExecutableSchemes() {
        assertEquals("https://instagram.com/tester", socialChannelUrl("Instagram", "@tester"))
        assertEquals("https://discord.com/users/12345", socialChannelUrl("Discord", "12345"))
        assertEquals("https://discord.gg/invite", socialChannelUrl("Discord", "discord.gg/invite"))
        assertEquals("https://youtube.com/channel/UC123", socialChannelUrl("YouTube", "https://youtube.com/channel/UC123"))
        assertNull(safeChannelUrl("javascript:alert(1)"))
        assertNull(socialChannelUrl("X", "intent://launch"))
        assertNull(safeChannelUrl("https://user:password@example.com"))
    }

    @Test fun bareAndEmptySubresourcesAreSupported() {
        assertTrue(parseChannelLinks(Json.parseToJsonElement("[]")).isEmpty())
        assertTrue(parseChannelGoals(Json.parseToJsonElement("{\"data\":null}")).isEmpty())
    }
}
