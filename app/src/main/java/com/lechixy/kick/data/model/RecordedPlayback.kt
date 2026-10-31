package com.lechixy.kick.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class PlaybackRequest(
    @SerialName("video_player") val videoPlayer: PlaybackPlayer = PlaybackPlayer(),
    @SerialName("video_session") val videoSession: JsonObject = JsonObject(emptyMap()),
    @SerialName("user_session") val userSession: PlaybackUser = PlaybackUser()
)
@Serializable
data class PlaybackPlayer(val player: JsonObject = JsonObject(emptyMap()))
@Serializable
data class PlaybackUser(@SerialName("non_personalised_ads") val nonPersonalisedAds: Boolean = true)
@Serializable
data class PlaybackResponse(
    @SerialName("playback_url") val playbackUrl: PlaybackUrls,
    @SerialName("video_session") val videoSession: PlaybackVideoSession = PlaybackVideoSession()
)
@Serializable
data class PlaybackUrls(val vod: String = "")
@Serializable
data class PlaybackVideoSession(
    @SerialName("video_duration") val durationSeconds: Long = 0,
    @SerialName("video_title") val title: String = ""
)
@Serializable
data class RecordedPlayback(
    val id: String,
    val title: String,
    val durationMs: Long,
    val thumbnail: String? = null,
    val startTime: String? = null,
    val views: Long = 0,
    val categories: List<Category> = emptyList(),
    val language: String? = null,
    val directUrl: String? = null,
    // The v2 video UUID must be resolved to livestream.vod_id before stream playback.
    val videoUuid: String? = null,
    val progressId: String = id
)
@Serializable
data class ChannelClips(val clips: List<ChannelClip> = emptyList())
@OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
@Serializable
data class ChannelClip(
    val id: String,
    val title: String = "",
    @kotlinx.serialization.json.JsonNames("playback_url", "video_url")
    @SerialName("clip_url") val clipUrl: String? = null,
    @SerialName("thumbnail_url") val thumbnailUrl: String? = null,
    val duration: Double = 0.0,
    @kotlinx.serialization.json.JsonNames("views_count", "view_count")
    val views: Long = 0,
    @SerialName("created_at") val createdAt: String? = null,
    val category: Category? = null
) {
    fun toRecording() = RecordedPlayback(
        id = id, title = title, durationMs = (duration * 1000).toLong(),
        thumbnail = thumbnailUrl, startTime = createdAt, views = views,
        categories = listOfNotNull(category), directUrl = clipUrl
    )
}

@Serializable
data class ClipResponse(val clip: ChannelClip)

fun ChannelVideo.toRecording(): RecordedPlayback? = playbackId?.let { id ->
    RecordedPlayback(
        id = id, title = sessionTitle.orEmpty(), durationMs = duration,
        thumbnail = thumbnail?.src, startTime = startTime ?: createdAt,
        views = views, categories = categories, language = language,
        videoUuid = if (vodId.isNullOrBlank()) id else null,
        progressId = video?.uuid ?: uuid ?: id
    )
}

@Serializable
data class VideoDetailResponse(val livestream: VideoDetailLivestream? = null)

@Serializable
data class VideoDetailLivestream(@SerialName("vod_id") val vodId: String? = null, val id: Long? = null)

@Serializable
data class PopularClipsResponse(val data: PopularClipsData = PopularClipsData())

@Serializable
data class PopularClipsData(val clips: List<ChannelClip> = emptyList(), val cursor: String? = null)
