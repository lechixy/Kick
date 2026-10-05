package com.lechixy.kick.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class LivestreamResponse(
    val data: LivestreamData
)

@Serializable
data class LivestreamData(
    val livestreams: List<Livestream> = emptyList()
)

@Serializable
data class Livestream(
    val category: Category? = null,
    val channel: Channel? = null,

    @SerialName("chatroom_id")
    val chatroomId: Long? = null,

    val id: String,

    @SerialName("is_mature")
    val isMature: Boolean = false,

    val language: String? = null,

    @SerialName("show_view_count")
    val showViewCount: Boolean = true,

    @SerialName("start_time")
    val startTime: String? = null,

    val tags: List<String> = emptyList(),

    val thumbnail: Thumbnail? = null,

    val title: String? = null,

    @SerialName("viewer_count")
    val viewerCount: Int = 0
)

@Serializable
data class Category(
    val id: Long,
    val name: String,
    val slug: String
)

@Serializable
data class Channel(
    val id: Long,

    @SerialName("profile_pic")
    val profilePic: String? = null,

    val slug: String,
    val username: String
)

@Serializable
data class Thumbnail(
    val src: String? = null,
    val srcset: String? = null
)