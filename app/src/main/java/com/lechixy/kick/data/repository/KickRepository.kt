package com.lechixy.kick.data.repository

import com.lechixy.kick.data.model.ChannelDetail
import com.lechixy.kick.data.model.ChannelEvent
import com.lechixy.kick.data.model.ChannelVideo
import com.lechixy.kick.data.model.CurrentViewersResponse
import com.lechixy.kick.data.model.Livestream
import com.lechixy.kick.data.model.SearchData
import com.lechixy.kick.data.model.chat.ChatHistoryData
import com.lechixy.kick.data.model.chat.withSelectedBadgesOnly
import com.lechixy.kick.data.remote.KickApi
import com.lechixy.kick.data.remote.KickChatWebSocketClient

class KickRepository(
    private val api: KickApi
) {

    val chatWebSocketClient: KickChatWebSocketClient by lazy {
        KickChatWebSocketClient(api)
    }

    suspend fun getLivestreams(language: String): List<Livestream> {
        return api
            .getLivestreams(language = language )
            .data
            .livestreams
    }

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