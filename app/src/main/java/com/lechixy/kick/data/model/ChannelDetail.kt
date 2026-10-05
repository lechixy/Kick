package com.lechixy.kick.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ChannelDetail(
    val id: Long,
    @SerialName("user_id") val userId: Long? = null,
    val slug: String,
    @SerialName("is_banned") val isBanned: Boolean = false,
    @SerialName("playback_url") val playbackUrl: String? = null,
    @SerialName("vod_enabled") val vodEnabled: Boolean = false,
    @SerialName("subscription_enabled") val subscriptionEnabled: Boolean = false,
    @SerialName("is_affiliate") val isAffiliate: Boolean = false,
    @SerialName("followers_count") val followersCount: String? = null,
    @SerialName("banner_image") val bannerImage: BannerImage? = null,
    @SerialName("offline_banner_image") val offlineBannerImage: OfflineBannerImage? = null,
    val verified: Boolean = false,
    val livestream: ChannelLivestream? = null,
    val user: ChannelUser? = null,
    val chatroom: ChatroomDetail? = null,
    @SerialName("recent_categories") val recentCategories: List<RecentCategory> = emptyList()
)

@Serializable
data class ChannelLivestream(
    val id: Long,
    val slug: String? = null,
    @SerialName("channel_id") val channelId: Long? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("session_title") val sessionTitle: String? = null,
    @SerialName("is_live") val isLive: Boolean = false,
    @SerialName("start_time") val startTime: String? = null,
    val source: String? = null,
    val duration: Long = 0L,
    val language: String? = null,
    @SerialName("is_mature") val isMature: Boolean = false,
    @SerialName("viewer_count") val viewerCount: Int = 0,
    @SerialName("lang_iso") val langIso: String? = null,
    val tags: List<String> = emptyList(),
    val categories: List<RecentCategory> = emptyList()
)

@kotlinx.serialization.Serializable
data class BannerImage(
    val url: String? = null
)

@kotlinx.serialization.Serializable
data class OfflineBannerImage(
    val src: String? = null,
    val srcset: String? = null
)

@kotlinx.serialization.Serializable
data class ChannelUser(
    val id: Long,
    val username: String,
    val bio: String? = null,
    val instagram: String? = null,
    val twitter: String? = null,
    val youtube: String? = null,
    val discord: String? = null,
    val tiktok: String? = null,
    val facebook: String? = null,
    @SerialName("profile_pic") val profilePic: String? = null
)

@kotlinx.serialization.Serializable
data class ChatroomDetail(
    val id: Long,
    @SerialName("channel_id") val channelId: Long? = null,
    @SerialName("chat_mode") val chatMode: String? = null,
    @SerialName("slow_mode") val slowMode: Boolean = false,
    @SerialName("message_interval") val messageInterval: Int = 0
)

@Serializable
data class RecentCategory(
    val id: Long,
    val name: String,
    val slug: String,
    val tags: List<String> = emptyList()
)