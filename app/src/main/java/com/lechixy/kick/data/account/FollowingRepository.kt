package com.lechixy.kick.data.account

import okhttp3.HttpUrl.Companion.toHttpUrl
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.*

data class FollowedChannel(
    val slug: String, val username: String, val avatar: String?, val live: Boolean,
    val title: String?, val category: String?, val viewers: Int?, val showViewers: Boolean
)

data class FollowingState(
    val channels: List<FollowedChannel> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
    val loaded: Boolean = false,
    val nextCursor: String? = null
)

class FollowingRepository(private val auth: AuthRepository, private val scope: CoroutineScope) {
    private val mutableState = MutableStateFlow(FollowingState())
    val state = mutableState.asStateFlow()
    private var loadJob: Job? = null
    private val mutationMutex = Mutex()
    private val seenCursors = mutableSetOf<String>()

    init {
        scope.launch {
            auth.session.map { it.signedIn to it.generation }.distinctUntilChanged().collect { (signedIn, _) ->
                loadJob?.cancel(); seenCursors.clear(); mutableState.value = FollowingState()
                if (signedIn) refresh()
            }
        }
    }

    fun refresh() = load(reset = true)
    fun loadMore() = load(reset = false)

    private fun load(reset: Boolean) {
        if (!auth.session.value.signedIn) return
        if (!reset && (state.value.loading || state.value.nextCursor == null)) return
        loadJob?.cancel()
        loadJob = scope.launch {
            val generation = auth.session.value.generation
            if (reset) seenCursors.clear()
            val cursor = if (reset) null else state.value.nextCursor
            mutableState.value = state.value.copy(loading = true, error = null)
            try {
                val url = "https://kick.com/api/v2/channels/followed".toHttpUrl().newBuilder()
                    .apply { if (cursor != null) addQueryParameter("cursor", cursor) }.build().toString()
                val response = auth.request(url)
                val channels = response["channels"] as? JsonArray ?: error("Missing channels")
                val parsed = channels.map { element ->
                    val item = element.jsonObject
                    FollowedChannel(
                        item.string("channel_slug")?.takeIf { it.isNotBlank() } ?: error("Missing slug"),
                        item.string("user_username") ?: item.string("channel_slug")!!,
                        item.string("profile_picture"), item["is_live"]?.jsonPrimitive?.booleanOrNull ?: false,
                        item.string("session_title"), item.string("category_name"), item.string("viewer_count")?.toIntOrNull(),
                        item["show_view_count"]?.jsonPrimitive?.booleanOrNull ?: true)
                }
                if (generation != auth.session.value.generation || !auth.session.value.signedIn) return@launch
                if (cursor != null) seenCursors.add(cursor)
                val next = response.string("nextCursor")?.takeUnless { it.isBlank() || it in seenCursors }
                mutableState.value = FollowingState(
                    channels = ((if (reset) emptyList() else state.value.channels) + parsed).distinctBy { it.slug.lowercase() },
                    loaded = true, nextCursor = next)
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                if (generation == auth.session.value.generation && auth.session.value.signedIn) {
                    mutableState.value = state.value.copy(loading = false, error = error.userMessage())
                }
            }
        }
    }

    /** Current status comes from /me; the paginated list alone cannot prove a negative. */
    suspend fun setFollowing(slug: String, following: Boolean) {
        val generation = auth.session.value.generation
        mutationMutex.withLock {
            check(auth.session.value.signedIn && generation == auth.session.value.generation) { "Session changed" }
            require(slug.matches(Regex("[A-Za-z0-9_-]+")))
            auth.request("https://kick.com/api/v2/channels/$slug/follow", if (following) "POST" else "DELETE")
            refresh()
        }
    }
}
