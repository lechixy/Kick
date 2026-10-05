package com.lechixy.kick.data.repository

import com.lechixy.kick.data.model.ChannelDetail
import com.lechixy.kick.data.model.ChannelVideo
import com.lechixy.kick.data.model.Livestream
import com.lechixy.kick.data.model.SearchData
import com.lechixy.kick.data.remote.KickApi

class KickRepository(
    private val api: KickApi
) {

    suspend fun getLivestreams(): List<Livestream> {
        return api
            .getLivestreams()
            .data
            .livestreams
    }

    suspend fun getChannel(slug: String): ChannelDetail {
        val fullUrl = "https://kick.com/api/v2/channels/$slug"
        return api.getChannel(fullUrl)
    }

    suspend fun getChannelVideos(slug: String): List<ChannelVideo> {
        val fullUrl = "https://kick.com/api/v2/channels/$slug/videos"
        return api.getChannelVideos(fullUrl)
    }

    suspend fun search(query: String): SearchData {
        val fullUrl = "https://search.kick.com/api/v1/search?query=$query"
        return api.search(fullUrl).data
    }
}