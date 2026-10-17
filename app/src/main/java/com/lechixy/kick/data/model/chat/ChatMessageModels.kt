package com.lechixy.kick.data.model.chat

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

object ChatMessageType {
    const val MESSAGE = "message"
    const val REPLY = "reply"
    const val CELEBRATION = "celebration"
}

@Serializable
data class ChatMessage(
    val id: String,
    @SerialName("chatroom_id")
    val chatroomId: Long? = null,
    val content: String = "",
    val type: String = ChatMessageType.MESSAGE,
    @SerialName("created_at")
    val createdAt: String? = null,
    val sender: ChatSender? = null,
    val metadata: ChatMessageMetadata? = null,
    @SerialName("thread_parent_id")
    val threadParentId: String? = null
) {
    val isReply: Boolean
        get() = type == ChatMessageType.REPLY || metadata?.originalMessage != null

    val isCelebration: Boolean
        get() = type == ChatMessageType.CELEBRATION || metadata?.celebration != null
}

@Serializable
data class ChatMessageMetadata(
    @SerialName("message_ref")
    val messageRef: String? = null,
    @SerialName("original_sender")
    val originalSender: ChatOriginalSender? = null,
    @SerialName("original_message")
    val originalMessage: ChatOriginalMessage? = null,
    val celebration: ChatCelebration? = null
)

@Serializable
data class ChatOriginalSender(
    val id: Long? = null,
    val username: String = "",
    val slug: String? = null
)

@Serializable
data class ChatOriginalMessage(
    val id: String = "",
    val content: String = ""
)

@Serializable
data class ChatCelebration(
    val id: String = "",
    val type: String = "",
    @SerialName("total_months")
    val totalMonths: Int? = null,
    @SerialName("created_at")
    val createdAt: String? = null
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
    val badges: List<ChatSenderBadge> = emptyList(),
    @SerialName("badges_v2")
    val badgesV2: List<ChatBadge> = emptyList()
)

@Serializable
data class ChatSenderBadge(
    val type: String = "",
    val text: String? = null,
    val count: Int? = null,
    @SerialName("sort_order")
    val sortOrder: Int? = null,
    val active: Boolean? = null
)

@Serializable
data class ChatBadge(
    val name: String = "",
    @SerialName("badge_type")
    val badgeType: String? = null,
    @SerialName("image_url")
    val imageUrl: String? = null,
    val metadata: ChatBadgeMetadata? = null,
    val selected: Boolean = false,
    @SerialName("sort_order")
    val sortOrder: Int? = null
)

@Serializable
data class ChatBadgeMetadata(
    val level: Int? = null
)

@Serializable
data class RewardRedeemedEvent(
    @SerialName("reward_title")
    val rewardTitle: String = "",
    @SerialName("user_id")
    val userId: Long? = null,
    @SerialName("channel_id")
    val channelId: Long? = null,
    val username: String = "",
    @SerialName("user_input")
    val userInput: String = "",
    @SerialName("reward_background_color")
    val rewardBackgroundColor: String? = null
)

@Serializable
data class ChannelSubscriptionEvent(
    @SerialName("user_ids")
    val userIds: List<Long> = emptyList(),
    val username: String = "",
    @SerialName("channel_id")
    val channelId: Long? = null
)

@Serializable
data class BannedUserInfo(
    val id: Long? = null,
    val username: String = "",
    val slug: String? = null
)

@Serializable
data class UserBannedEvent(
    val id: String = "",
    val user: BannedUserInfo? = null,
    @SerialName("banned_by")
    val bannedBy: BannedUserInfo? = null,
    val permanent: Boolean = false,
    val duration: Int? = null,
    @SerialName("expires_at")
    val expiresAt: String? = null
)

@Serializable
data class DeletedMessageRef(
    val id: String = ""
)

@Serializable
data class MessageDeletedEvent(
    val id: String = "",
    val message: DeletedMessageRef? = null,
    val aiModerated: Boolean = false,
    val violatedRules: List<String> = emptyList()
)

@Serializable
data class PointsUpdatedEvent(
    val reason: String = "",
    val points: Int = 0,
    val balance: Int = 0,
    @SerialName("user_id")
    val userId: Long? = null,
    @SerialName("channel_id")
    val channelId: Long? = null
)

sealed interface ChatWebSocketEvent {
    data class MessageReceived(val message: ChatMessage) : ChatWebSocketEvent
    data class RewardRedeemed(val event: RewardRedeemedEvent) : ChatWebSocketEvent
    data class ChannelSubscribed(val event: ChannelSubscriptionEvent) : ChatWebSocketEvent
    data class UserBanned(val event: UserBannedEvent) : ChatWebSocketEvent
    data class MessageDeleted(val event: MessageDeletedEvent) : ChatWebSocketEvent
    data class PointsUpdated(val event: PointsUpdatedEvent) : ChatWebSocketEvent
}

