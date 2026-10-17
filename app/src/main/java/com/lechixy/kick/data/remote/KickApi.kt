package com.lechixy.kick.data.remote

import com.lechixy.kick.data.model.ChannelDetail
import com.lechixy.kick.data.model.ChannelVideo
import com.lechixy.kick.data.model.CurrentViewersResponse
import com.lechixy.kick.data.model.LivestreamResponse
import com.lechixy.kick.data.model.SearchResponse
import com.lechixy.kick.data.model.chat.RealtimeAuthRequest
import com.lechixy.kick.data.model.chat.RealtimeAuthResponse
import com.lechixy.kick.data.model.chat.RealtimeConnectionRequest
import com.lechixy.kick.data.model.chat.RealtimeConnectionResponse
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Url

interface KickApi {
    @GET("api/v1/livestreams/featured")
    suspend fun getLivestreams(
        @Query("limit") limit: Int = 20,
        @Query("sort") sort: String = "viewer_count_desc",
        @Query("language") language: String = "tr"
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

    @GET
    suspend fun getCurrentViewers(
        @Url url: String
    ): List<CurrentViewersResponse>

    @POST("api/v1/realtime/connection")
    suspend fun getRealtimeConnection(
        @Body request: RealtimeConnectionRequest
    ): RealtimeConnectionResponse

    @POST("api/v1/realtime/auth/connection")
    suspend fun getRealtimeAuth(
        @Body request: RealtimeAuthRequest
    ): RealtimeAuthResponse

    @POST("api/v1/realtime/channels/{channelId}/chat/connection")
    suspend fun getChannelChatConnection(
        @Path("channelId") channelId: Long,
        @Body request: RealtimeConnectionRequest
    ): RealtimeConnectionResponse
}