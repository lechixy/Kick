package com.lechixy.kick.ui.components.chat

import com.lechixy.kick.R
import com.lechixy.kick.data.model.chat.ChatBadge
import com.lechixy.kick.data.model.chat.ChatIdentity
import com.lechixy.kick.data.model.chat.ChatSenderBadge
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatBadgeTest {

    @Test
    fun resolveBadges_resolvesVipBadgeCorrectly() {
        val identity = ChatIdentity(
            badges = listOf(
                ChatSenderBadge(
                    type = "vip",
                    text = "VIP",
                    active = true,
                    sortOrder = 5
                )
            )
        )

        val resolved = ChatTextParser.resolveBadges(identity, emptyList())
        assertEquals(1, resolved.size)
        val vipBadge = resolved.first()
        assertEquals("badge_local_vip", vipBadge.key)
        assertEquals("VIP", vipBadge.name)
        assertEquals(R.drawable.vip_badge, vipBadge.drawableRes)
        assertEquals(5, vipBadge.sortOrder)
    }

    @Test
    fun resolveBadges_skipsInactiveVipBadge() {
        val identity = ChatIdentity(
            badges = listOf(
                ChatSenderBadge(
                    type = "vip",
                    text = "VIP",
                    active = false,
                    sortOrder = 5
                )
            )
        )

        val resolved = ChatTextParser.resolveBadges(identity, emptyList())
        assertTrue(resolved.isEmpty())
    }

    @Test
    fun resolveBadges_sortsBadgesProperly() {
        val identity = ChatIdentity(
            badges = listOf(
                ChatSenderBadge(type = "vip", text = "VIP", sortOrder = 5),
                ChatSenderBadge(type = "broadcaster", text = "Broadcaster", sortOrder = 1),
                ChatSenderBadge(type = "moderator", text = "Moderator", sortOrder = 3)
            )
        )

        val resolved = ChatTextParser.resolveBadges(identity, emptyList())
        assertEquals(3, resolved.size)
        assertEquals("Broadcaster", resolved[0].name)
        assertEquals("Moderator", resolved[1].name)
        assertEquals("VIP", resolved[2].name)
    }

    @Test
    fun resolveBadges_prefersV2BadgeOverLocalVip() {
        val identity = ChatIdentity(
            badges = listOf(
                ChatSenderBadge(type = "vip", text = "VIP", sortOrder = 5)
            ),
            badgesV2 = listOf(
                ChatBadge(
                    name = "vip",
                    imageUrl = "https://example.com/vip.png",
                    selected = true,
                    sortOrder = 5
                )
            )
        )

        val resolved = ChatTextParser.resolveBadges(identity, emptyList())
        assertEquals(1, resolved.size)
        assertEquals("https://example.com/vip.png", resolved[0].imageUrl)
    }

    @Test
    fun resolveBadges_resolvesFounderBadgeCorrectly() {
        val identity = ChatIdentity(
            badges = listOf(
                ChatSenderBadge(
                    type = "founder",
                    text = "Founder"
                )
            )
        )

        val resolved = ChatTextParser.resolveBadges(identity, emptyList())
        assertEquals(1, resolved.size)
        val founderBadge = resolved.first()
        assertEquals("badge_local_founder", founderBadge.key)
        assertEquals("Founder", founderBadge.name)
        assertEquals(R.drawable.founder_badge, founderBadge.drawableRes)
        assertEquals(6, founderBadge.sortOrder)
    }
}
