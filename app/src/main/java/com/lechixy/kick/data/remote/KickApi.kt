package com.lechixy.kick.data.remote

import com.lechixy.kick.data.model.ChannelClips
import com.lechixy.kick.data.model.ChannelDetail
import com.lechixy.kick.data.model.ChannelEventResponse
import com.lechixy.kick.data.model.ChannelVideo
import com.lechixy.kick.data.model.ClipResponse
import com.lechixy.kick.data.model.CurrentViewersResponse
import com.lechixy.kick.data.model.LivestreamResponse
import com.lechixy.kick.data.model.RecommendedLivestreamResponse
import com.lechixy.kick.data.model.LiveTotalsResponse
import com.lechixy.kick.data.model.PlaybackRequest
import com.lechixy.kick.data.model.PlaybackResponse
import com.lechixy.kick.data.model.SearchResponse
import com.lechixy.kick.data.model.VideoDetailResponse
import com.lechixy.kick.data.model.chat.ChatHistoryResponse
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
    @GET("https://kick.com/api/v1/subcategories")
    suspend fun getCategories(@Query("page") page: Int = 1, @Query("limit") limit: Int = 32): com.lechixy.kick.data.model.CategoriesResponse

    @GET("https://kick.com/emotes/{slug}")
    suspend fun getEmotes(@Path("slug") slug: String): kotlinx.serialization.json.JsonElement

    @GET("https://web.kick.com/api/v1/channels/{channelId}/chat/active-chatters")
    suspend fun getActiveChatters(@Path("channelId") channelId: Long): kotlinx.serialization.json.JsonObject

    @GET("https://kick.com/api/v2/channels/{slug}/users/{username}")
    suspend fun getChatter(@Path("slug") slug: String, @Path("username") username: String): kotlinx.serialization.json.JsonObject

    @GET("https://kick.com/api/v2/channels/{slug}/info")
    suspend fun getChatterChannel(@Path("slug") slug: String): kotlinx.serialization.json.JsonObject

    @GET("https://web.kick.com/api/v1/gamification/users/{userId}/streak")
    suspend fun getChatterStreak(@Path("userId") userId: Long): kotlinx.serialization.json.JsonObject

    @GET("https://kick.com/api/v1/channels/{slug}/links")
    suspend fun getChannelLinks(@Path("slug") slug: String): kotlinx.serialization.json.JsonElement

    @GET("https://kick.com/api/v2/channels/{slug}/goals")
    suspend fun getChannelGoals(@Path("slug") slug: String): kotlinx.serialization.json.JsonElement

    @GET("api/v1/analytics/leaderboard")
    suspend fun getGiftedSubsLeaderboard(
        @Query("start_time") startTime: String,
        @Query("type") type: String = "gifted_subs",
        @Query("limit") limit: Int = 50
    ): com.lechixy.kick.data.model.GiftedSubsLeaderboardResponse

    @GET("https://web.kick.com/api/v1/recommendations/livestreams/list")
    suspend fun getRecommendedLivestreams(
        @Query("limit") limit: Int = 14,
        @Query("language") language: String
    ): RecommendedLivestreamResponse

    @GET("https://web.kick.com/api/v1/livestreams/live-totals")
    suspend fun getLiveTotals(): LiveTotalsResponse

    @GET("https://kick.com/api/v1/video/{uuid}")
    suspend fun getVideoDetail(@Path("uuid") uuid: String): VideoDetailResponse

    @POST("api/v1/stream/{id}/playback")
    suspend fun getRecordedPlayback(
        @Path("id") id: String,
        @Body request: PlaybackRequest
    ): PlaybackResponse

    @GET("https://kick.com/api/v2/clips/{id}")
    suspend fun getClip(@Path("id") id: String): ClipResponse

    @GET
    suspend fun getChannelClips(@Url url: String): ChannelClips

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

    @GET("api/v1/channels/{channelId}/event")
    suspend fun getChannelEvent(
        @Path("channelId") channelId: Long
    ): ChannelEventResponse

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

    @GET("api/v1/chat/{channelId}/history")
    suspend fun getChatHistory(
        @Path("channelId") channelId: Long,
        @Query("cursor") cursor: String? = null
    ): ChatHistoryResponse

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
