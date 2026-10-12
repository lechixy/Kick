package com.lechixy.kick.data.model.chat

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class ChatMessage(
    val id: String,
    @SerialName("chatroom_id")
    val chatroomId: Long? = null,
    val content: String = "",
    val type: String = "message",
    @SerialName("created_at")
    val createdAt: String? = null,
    val sender: ChatSender? = null,
    val metadata: JsonElement? = null
)

@Serializable
data class ChatSender(
    val id: Long? = null,
    val username: String = "",
    val slug: String? = null,
    val identity: ChatIdentity? = null
)

@Serializable
data class ChatIdentity(
    val color: String? = null,
    val badges: List<JsonElement> = emptyList(),
    @SerialName("badges_v2")
    val badgesV2: List<ChatBadge> = emptyList()
)

@Serializable
data class ChatBadge(
    val name: String = "",
    @SerialName("badge_type")
    val badgeType: String? = null,
    @SerialName("image_url")
    val imageUrl: String? = null,
    val metadata: JsonElement? = null,
    val selected: Boolean = false,
    @SerialName("sort_order")
    val sortOrder: Int? = null
)
