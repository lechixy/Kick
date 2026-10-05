package com.lechixy.kick.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@kotlinx.serialization.Serializable
data class ChannelVideo(
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
    @SerialName("viewer_count") val viewerCount: Int = 0,
    val views: Long = 0L,
    val thumbnail: VideoThumbnail? = null,
    val categories: List<Category> = emptyList()
)

@Serializable
data class VideoThumbnail(
    val src: String? = null,
    val srcset: String? = null
)