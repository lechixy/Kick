package com.lechixy.kick.data.remote

import com.lechixy.kick.data.model.ChannelDetail
import com.lechixy.kick.data.model.ChannelVideo
import com.lechixy.kick.data.model.LivestreamResponse
import com.lechixy.kick.data.model.SearchResponse
import retrofit2.http.GET
import retrofit2.http.Query
import retrofit2.http.Url

interface KickApi {

    @GET("api/v1/livestreams")
    suspend fun getLivestreams(
        @Query("limit") limit: Int = 20,
        @Query("sort") sort: String = "viewer_count_desc"
    ): LivestreamResponse

    @GET
    suspend fun getChannel(
        @Url url: String
    ): ChannelDetail

    @GET
    suspend fun getChannelVideos(
        @Url url: String
    ): List<ChannelVideo>

    @GET
    suspend fun search(
        @Url url: String
    ): SearchResponse
}