package com.lechixy.kick.ui.screens.channel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lechixy.kick.data.model.ChannelDetail
import com.lechixy.kick.data.model.ChannelEvent
import com.lechixy.kick.data.model.ChannelVideo
import com.lechixy.kick.data.model.chat.ChatHistoryData
import com.lechixy.kick.data.model.chat.ChatMessage
import com.lechixy.kick.data.model.chat.ChatWebSocketEvent
import com.lechixy.kick.data.model.chat.PinnedMessageInfo
import com.lechixy.kick.data.model.chat.PointsUpdatedEvent
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
    val error: String? = null
)

class ChannelViewModel(
    private val repository: KickRepository = KickRepository(KickNetwork.api)
) : ViewModel() {

    companion object {
        private const val TAG = "ChannelViewModel"
        private const val VIEWERS_POLL_INTERVAL_MS = 60_000L
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
    private var loadedSlug: String? = null

    fun loadChannel(slug: String, autoConnectChat: Boolean = true) {
        viewModelScope.launch {
            if (loadedSlug != null && !loadedSlug.equals(slug, ignoreCase = true)) {
                repository.chatWebSocketClient.disconnect()
            }
            loadedSlug = slug
            viewersPollingJob?.cancel()
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

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
        viewersPollingJob?.cancel()
        repository.chatWebSocketClient.disconnect()
    }
}
