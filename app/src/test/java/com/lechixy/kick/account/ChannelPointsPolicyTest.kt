package com.lechixy.kick.account

import com.lechixy.kick.data.account.ChannelPointsPolicy
import com.lechixy.kick.data.model.chat.PointsUpdatedEvent
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class ChannelPointsPolicyTest {
    @Test fun privatePublicationDoesNotRequireRedundantUserId() {
        val event = Json.decodeFromString<PointsUpdatedEvent>(
            """{"reason":"WATCHED","points":10,"balance":120,"channel_id":7}""")
        assertNull(event.userId)
        assertTrue(ChannelPointsPolicy.acceptsPublication("private-channelpoints-42", 42))
        assertTrue(ChannelPointsPolicy.accepts(event, 7, 42))
        assertEquals(120, event.balance)
    }

    @Test fun rejectsOtherChannelsAccountsAndGuestPublications() {
        val event = PointsUpdatedEvent(channelId = 7, userId = 42)
        assertTrue(ChannelPointsPolicy.accepts(event, 7, 42))
        assertFalse(ChannelPointsPolicy.accepts(event, 8, 42))
        assertFalse(ChannelPointsPolicy.accepts(event, 7, 99))
        assertFalse(ChannelPointsPolicy.accepts(event.copy(userId = null), 7, null))
        assertFalse(ChannelPointsPolicy.acceptsPublication("chatrooms.7", 42))
        assertFalse(ChannelPointsPolicy.acceptsPublication("private-channelpoints-99", 42))
        assertFalse(ChannelPointsPolicy.acceptsPublication(null, 42))
        assertFalse(ChannelPointsPolicy.acceptsPublication("private-channelpoints-42", null))
    }
}
