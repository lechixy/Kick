package com.lechixy.kick.data.repository

import com.lechixy.kick.data.model.ChannelDetail
import com.lechixy.kick.data.model.ChannelEvent
import com.lechixy.kick.data.model.ChannelVideo
import com.lechixy.kick.data.model.CurrentViewersResponse
import com.lechixy.kick.data.model.Livestream
import com.lechixy.kick.data.model.PlaybackResponse
import com.lechixy.kick.data.model.PlaybackRequest
import com.lechixy.kick.data.model.SearchData
import com.lechixy.kick.data.model.chat.ChatHistoryData
import com.lechixy.kick.data.model.chat.withSelectedBadgesOnly
import com.lechixy.kick.data.remote.KickApi
import com.lechixy.kick.data.remote.KickChatWebSocketClient
import retrofit2.HttpException

class KickRepository(
    private val api: KickApi
) {
    suspend fun getEmotes(slug: String) = com.lechixy.kick.data.model.chat.parseEmoteCategories(api.getEmotes(slug))
    suspend fun getChannelLinks(slug: String) = com.lechixy.kick.data.model.parseChannelLinks(api.getChannelLinks(slug))
    suspend fun getChannelGoals(slug: String) = com.lechixy.kick.data.model.parseChannelGoals(api.getChannelGoals(slug))

    suspend fun getLastHourGiftedSubsRank(channelId: Long): Int? =
        api.getGiftedSubsLeaderboard(com.lechixy.kick.data.model.hourlyLeaderboardStart()).rankFor(channelId)

    suspend fun resolveViewerSession(session: com.lechixy.kick.data.remote.ViewerSession): com.lechixy.kick.data.remote.ViewerSession {
        if (!session.vodId.isNullOrBlank()) return session
        val slug = session.channelSlug?.takeIf { it.isNotBlank() } ?: error("Missing viewer channel slug")
        val video = getChannelVideos(slug).firstOrNull {
            it.id == session.livestreamId && (it.channelId == null || it.channelId == session.channelId)
        } ?: error("Live video metadata is not available yet")
        val directId = video.vodId?.takeIf { it.isNotBlank() }
        if (directId != null) return session.copy(vodId = directId)
        val uuid = video.video?.uuid?.takeIf { it.isNotBlank() } ?: video.uuid?.takeIf { it.isNotBlank() }
            ?: error("Live video UUID is not available yet")
        val detail = api.getVideoDetail(uuid).livestream ?: error("Missing live video details")
        check(detail.id == session.livestreamId) { "Live video session changed" }
        val vodId = detail.vodId?.takeIf { it.isNotBlank() } ?: error("Live VOD ID is not available yet")
        return session.copy(vodId = vodId)
    }

    suspend fun getRecordedPlayback(id: String, videoUuid: String? = null): PlaybackResponse {
        if (videoUuid != null) {
            return api.getRecordedPlayback(resolveVodId(videoUuid), PlaybackRequest())
        }
        return try {
            api.getRecordedPlayback(id, PlaybackRequest())
        } catch (error: HttpException) {
            if (error.code() != 404) throw error
            // Sessions saved before video UUIDs and stream IDs were separated may contain a UUID here.
            val vodId = resolveVodId(id)
            if (vodId == id) throw error
            api.getRecordedPlayback(vodId, PlaybackRequest())
        }
    }

    private suspend fun resolveVodId(videoUuid: String): String {
        return api.getVideoDetail(videoUuid).livestream?.vodId?.takeIf { it.isNotBlank() }
            ?: error("Kayıt için VOD kimliği bulunamadı")
    }

    suspend fun getClip(id: String) = api.getClip(id).clip

    suspend fun getChannelClips(slug: String) =
        api.getChannelClips("https://kick.com/api/v2/channels/$slug/clips").clips

    val chatWebSocketClient: KickChatWebSocketClient by lazy {
        KickChatWebSocketClient(api)
    }

    suspend fun getLivestreams(language: String): List<Livestream> {
        return api
            .getLivestreams(language = language )
            .data
            .livestreams
    }

    suspend fun getRecommendedLivestreams(language: String): List<Livestream> {
        return try {
            api.getRecommendedLivestreams(language = language).livestreams
        } catch (error: HttpException) {
            if (error.code() !in setOf(401, 403)) throw error
            // Kick can reject anonymous recommendations; retain the working public home feed.
            api.getLivestreams(limit = 14, language = language).data.livestreams
        }
    }

    suspend fun getLiveTotals() = api.getLiveTotals().data

    suspend fun getChannel(slug: String): ChannelDetail {
        val fullUrl = "https://kick.com/api/v2/channels/$slug"
        return api.getChannel(fullUrl)
    }

    suspend fun getChannelEvent(channelId: Long): ChannelEvent? {
        return api.getChannelEvent(channelId).data
    }

    suspend fun getChannelVideos(slug: String): List<ChannelVideo> {
        val fullUrl = "https://kick.com/api/v2/channels/$slug/videos"
        return api.getChannelVideos(fullUrl)
    }

    suspend fun search(query: String): SearchData {
        val fullUrl = "https://search.kick.com/api/v1/search?query=$query"
        return api.search(fullUrl).data
    }

    suspend fun getCurrentViewers(livestreamIds: List<Long>): List<CurrentViewersResponse> {
        if (livestreamIds.isEmpty()) return emptyList()
        val idsQuery = livestreamIds.joinToString("&") { "ids[]=$it" }
        val fullUrl = "https://kick.com/current-viewers?$idsQuery"
        return api.getCurrentViewers(fullUrl)
    }

    suspend fun getCurrentViewers(livestreamId: Long): CurrentViewersResponse? {
        val list = getCurrentViewers(listOf(livestreamId))
        return list.firstOrNull { it.livestreamId == livestreamId } ?: list.firstOrNull()
    }

    suspend fun getChatHistory(channelId: Long, cursor: String? = null): ChatHistoryData? {
        return api.getChatHistory(channelId = channelId, cursor = cursor).data?.withSelectedBadgesOnly()
    }
}
