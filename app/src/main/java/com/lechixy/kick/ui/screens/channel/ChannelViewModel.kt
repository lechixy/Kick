package com.lechixy.kick.ui.screens.channel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lechixy.kick.data.model.ChannelDetail
import com.lechixy.kick.data.model.ChannelEvent
import com.lechixy.kick.data.model.ChannelLivestream
import com.lechixy.kick.data.model.ChannelVideo
import com.lechixy.kick.data.model.chat.ChatHistoryData
import com.lechixy.kick.data.model.chat.ChatMessage
import com.lechixy.kick.data.model.chat.ChatWebSocketEvent
import com.lechixy.kick.data.model.chat.PinnedMessageInfo
import com.lechixy.kick.data.model.chat.PointsUpdatedEvent
import com.lechixy.kick.data.model.chat.StopStreamBroadcastEvent
import com.lechixy.kick.data.model.chat.StreamerIsLiveEvent
import com.lechixy.kick.data.remote.ChatConnectionState
import com.lechixy.kick.data.remote.KickNetwork
import com.lechixy.kick.data.repository.KickRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class ChannelUiState(
    val isLoading: Boolean = true,
    val channel: ChannelDetail? = null,
    val event: ChannelEvent? = null,
    val videos: List<ChannelVideo> = emptyList(),
    val chatHistory: ChatHistoryData? = null,
    val error: String? = null,
    val liveAnnouncementId: Long? = null
)

class ChannelViewModel(
    private val repository: KickRepository = KickRepository(KickNetwork.api)
) : ViewModel() {

    companion object {
        private const val TAG = "ChannelViewModel"
        private const val VIEWERS_POLL_INTERVAL_MS = 60_000L
        private const val LIVE_METADATA_RETRY_DELAY_MS = 2_500L
        private const val LIVE_METADATA_MAX_ATTEMPTS = 4
    }

    private val _uiState = MutableStateFlow(ChannelUiState())
    val uiState: StateFlow<ChannelUiState> = _uiState.asStateFlow()

    val chatMessages: StateFlow<List<ChatMessage>> = repository.chatWebSocketClient.messages
    val pinnedMessage: StateFlow<PinnedMessageInfo?> = repository.chatWebSocketClient.pinnedMessage
    val userColors: StateFlow<Map<String, String>> = repository.chatWebSocketClient.userColors
    val chatEvents: SharedFlow<ChatWebSocketEvent> = repository.chatWebSocketClient.events
    val pointsUpdated: StateFlow<PointsUpdatedEvent?> = repository.chatWebSocketClient.pointsUpdated
    val chatConnectionState: StateFlow<ChatConnectionState> = repository.chatWebSocketClient.connectionState

    private var viewersPollingJob: Job? = null
    private var liveRefreshJob: Job? = null
    private var loadedSlug: String? = null

    init {
        observeWebSocketEvents()
    }

    private fun observeWebSocketEvents() {
        viewModelScope.launch {
            repository.chatWebSocketClient.events.collect { wsEvent ->
                when (wsEvent) {
                    is ChatWebSocketEvent.StreamerIsLive -> handleStreamerIsLive(wsEvent.event)
                    is ChatWebSocketEvent.StopStreamBroadcast -> handleStopStreamBroadcast(wsEvent.event)
                    else -> Unit
                }
            }
        }
    }

    fun consumeLiveAnnouncement(announcementId: Long) {
        _uiState.update { current ->
            if (current.liveAnnouncementId == announcementId) {
                current.copy(liveAnnouncementId = null)
            } else {
                current
            }
        }
    }

    private fun handleStreamerIsLive(event: StreamerIsLiveEvent) {
        val liveInfo = event.livestream ?: return
        val currentChannel = _uiState.value.channel ?: return
        if (liveInfo.channelId != null && liveInfo.channelId != currentChannel.id) return

        liveRefreshJob?.cancel()

        val normalizedStartTime = liveInfo.createdAt
            ?.trim()
            ?.replace('T', ' ')
            ?.substringBefore('.')
            ?.trimEnd('Z')
            ?.takeIf { it.isNotBlank() }

        val optimisticLivestream = ChannelLivestream(
            id = liveInfo.id,
            channelId = liveInfo.channelId ?: currentChannel.id,
            sessionTitle = liveInfo.sessionTitle ?: currentChannel.livestream?.sessionTitle,
            source = liveInfo.source ?: currentChannel.livestream?.source,
            createdAt = liveInfo.createdAt ?: currentChannel.livestream?.createdAt,
            startTime = normalizedStartTime ?: currentChannel.livestream?.startTime,
            isLive = true,
            viewerCount = currentChannel.livestream?.viewerCount ?: 0,
            language = currentChannel.livestream?.language,
            langIso = currentChannel.livestream?.langIso,
            isMature = currentChannel.livestream?.isMature ?: false,
            thumbnail = currentChannel.livestream?.thumbnail,
            tags = currentChannel.livestream?.tags.orEmpty(),
            categories = currentChannel.livestream?.categories?.ifEmpty { currentChannel.recentCategories }
                ?: currentChannel.recentCategories
        )

        val announcementId = System.nanoTime()
        _uiState.update { current ->
            val ch = current.channel ?: return@update current
            current.copy(
                channel = ch.copy(livestream = optimisticLivestream),
                liveAnnouncementId = announcementId
            )
        }

        startViewersPolling(liveInfo.id)

        val slug = loadedSlug ?: currentChannel.slug
        liveRefreshJob = viewModelScope.launch {
            repeat(LIVE_METADATA_MAX_ATTEMPTS) { attempt ->
                if (attempt > 0) {
                    delay(LIVE_METADATA_RETRY_DELAY_MS)
                }
                try {
                    val freshChannel = repository.getChannel(slug)
                    val freshEvent = runCatching { repository.getChannelEvent(freshChannel.id) }.getOrNull()
                    var hasCompleteLiveMetadata = false

                    _uiState.update { current ->
                        val currentCh = current.channel ?: return@update current
                        // StopStreamBroadcast geldiyse canlı yayını tekrar açma
                        if (currentCh.livestream == null) return@update current

                        val mergedLivestream = freshChannel.livestream?.copy(
                            sessionTitle = freshChannel.livestream.sessionTitle
                                ?: liveInfo.sessionTitle
                                ?: currentCh.livestream.sessionTitle,
                            startTime = freshChannel.livestream.startTime
                                ?: normalizedStartTime
                                ?: currentCh.livestream.startTime
                        ) ?: currentCh.livestream.copy(
                            sessionTitle = liveInfo.sessionTitle ?: currentCh.livestream.sessionTitle
                        )

                        hasCompleteLiveMetadata =
                            freshChannel.livestream != null && !freshChannel.playbackUrl.isNullOrBlank()

                        current.copy(
                            channel = freshChannel.copy(
                                playbackUrl = freshChannel.playbackUrl ?: currentCh.playbackUrl,
                                livestream = mergedLivestream
                            ),
                            event = freshEvent ?: current.event
                        )
                    }

                    if (hasCompleteLiveMetadata) return@launch
                } catch (_: Exception) {
                }
            }
        }
    }

    private fun handleStopStreamBroadcast(event: StopStreamBroadcastEvent) {
        val currentChannel = _uiState.value.channel ?: return
        val targetChannelId = event.livestream?.channel?.id
        if (targetChannelId != null && targetChannelId != currentChannel.id) return

        liveRefreshJob?.cancel()
        viewersPollingJob?.cancel()

        val updatedIsBanned = event.livestream?.channel?.isBanned ?: currentChannel.isBanned
        _uiState.update { current ->
            val ch = current.channel ?: return@update current
            current.copy(
                channel = ch.copy(
                    livestream = null,
                    isBanned = updatedIsBanned
                ),
                liveAnnouncementId = null
            )
        }

        val slug = loadedSlug ?: currentChannel.slug
        viewModelScope.launch {
            try {
                val updatedVideos = repository.getChannelVideos(slug).filterNot { it.isLive }
                _uiState.update { it.copy(videos = updatedVideos) }
            } catch (_: Exception) {
            }
        }
    }

    fun loadChannel(slug: String, autoConnectChat: Boolean = true) {
        viewModelScope.launch {
            if (loadedSlug != null && !loadedSlug.equals(slug, ignoreCase = true)) {
                repository.chatWebSocketClient.disconnect()
            }
            loadedSlug = slug
            liveRefreshJob?.cancel()
            viewersPollingJob?.cancel()
            _uiState.value = _uiState.value.copy(isLoading = true, error = null, liveAnnouncementId = null)

            try {
                val channelDeferred = async { repository.getChannel(slug) }
                val videosDeferred = async {
                    try {
                        repository.getChannelVideos(slug).filterNot { it.isLive }
                    } catch (_: Exception) {
                        emptyList<ChannelVideo>()
                    }
                }

                val channel = channelDeferred.await()
                val eventDeferred = async {
                    try {
                        repository.getChannelEvent(channel.id)
                    } catch (_: Exception) {
                        null
                    }
                }
                val historyDeferred = async {
                    try {
                        repository.getChatHistory(channel.id)
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to load chat history for channelId=${channel.id}", e)
                        null
                    }
                }
                val videos = videosDeferred.await()
                val event = eventDeferred.await()
                val history = historyDeferred.await()

                _uiState.value = ChannelUiState(
                    isLoading = false,
                    channel = channel,
                    event = event,
                    videos = videos,
                    chatHistory = history,
                    error = null
                )

                if (autoConnectChat) {
                    connectChat()
                }

                repository.chatWebSocketClient.updatePinnedMessage(history?.pinnedMessage)
                repository.chatWebSocketClient.cacheUserColorsFromMessages(history?.messages.orEmpty())

                channel.livestream?.id?.let { livestreamId ->
                    startViewersPolling(livestreamId)
                }
            } catch (e: Exception) {
                _uiState.value = ChannelUiState(
                    isLoading = false,
                    error = e.message ?: "Failed to load channel"
                )
            }
        }
    }

    private fun startViewersPolling(livestreamId: Long) {
        viewersPollingJob?.cancel()
        viewersPollingJob = viewModelScope.launch {
            while (isActive) {
                delay(VIEWERS_POLL_INTERVAL_MS)
                updateLivestreamViewers(livestreamId)
            }
        }
    }

    suspend fun updateLivestreamViewers(livestreamId: Long) {
        try {
            val viewerInfo = repository.getCurrentViewers(livestreamId) ?: return
            _uiState.update { current ->
                val currentChannel = current.channel
                if (currentChannel?.livestream != null && currentChannel.livestream.id == livestreamId) {
                    current.copy(
                        channel = currentChannel.copy(
                            livestream = currentChannel.livestream.copy(
                                viewerCount = viewerInfo.viewers
                            )
                        )
                    )
                } else {
                    current
                }
            }
        } catch (_: Exception) {
            // Ağ veya API hatalarında kullanıcı deneyimini kesintiye uğratma
        }
    }

    fun connectChat() {
        val channel = _uiState.value.channel ?: return
        val chatroomId = channel.chatroom?.id ?: return
        repository.chatWebSocketClient.connect(
            channelId = channel.id,
            chatroomId = chatroomId
        )
        val existingHistory = _uiState.value.chatHistory
        if (existingHistory != null) {
            if (repository.chatWebSocketClient.pinnedMessage.value == null && existingHistory.pinnedMessage != null) {
                repository.chatWebSocketClient.updatePinnedMessage(existingHistory.pinnedMessage)
            }
            repository.chatWebSocketClient.cacheUserColorsFromMessages(existingHistory.messages)
        } else {
            viewModelScope.launch {
                try {
                    val history = repository.getChatHistory(channel.id)
                    repository.chatWebSocketClient.updatePinnedMessage(history?.pinnedMessage)
                    repository.chatWebSocketClient.cacheUserColorsFromMessages(history?.messages.orEmpty())
                    if (history != null) {
                        _uiState.update { it.copy(chatHistory = history) }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to load chat history in connectChat for channelId=${channel.id}", e)
                }
            }
        }
    }

    fun disconnectChat() {
        repository.chatWebSocketClient.disconnect()
    }

    override fun onCleared() {
        super.onCleared()
        liveRefreshJob?.cancel()
        viewersPollingJob?.cancel()
        repository.chatWebSocketClient.disconnect()
    }
}
