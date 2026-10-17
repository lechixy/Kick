package com.lechixy.kick.ui.screens.channel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lechixy.kick.data.model.ChannelDetail
import com.lechixy.kick.data.model.ChannelVideo
import com.lechixy.kick.data.model.chat.ChatMessage
import com.lechixy.kick.data.remote.ChatConnectionState
import com.lechixy.kick.data.remote.KickNetwork
import com.lechixy.kick.data.repository.KickRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class ChannelUiState(
    val isLoading: Boolean = true,
    val channel: ChannelDetail? = null,
    val videos: List<ChannelVideo> = emptyList(),
    val error: String? = null
)

class ChannelViewModel(
    private val repository: KickRepository = KickRepository(KickNetwork.api)
) : ViewModel() {

    companion object {
        private const val VIEWERS_POLL_INTERVAL_MS = 60_000L
    }

    private val _uiState = MutableStateFlow(ChannelUiState())
    val uiState: StateFlow<ChannelUiState> = _uiState.asStateFlow()

    val chatMessages: StateFlow<List<ChatMessage>> = repository.chatWebSocketClient.messages
    val chatConnectionState: StateFlow<ChatConnectionState> = repository.chatWebSocketClient.connectionState

    private var viewersPollingJob: Job? = null

    fun loadChannel(slug: String, autoConnectChat: Boolean = true) {
        viewModelScope.launch {
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
                val videos = videosDeferred.await()

                _uiState.value = ChannelUiState(
                    isLoading = false,
                    channel = channel,
                    videos = videos,
                    error = null
                )

                channel.livestream?.id?.let { livestreamId ->
                    startViewersPolling(livestreamId)
                }

                if (autoConnectChat) {
                    connectChat()
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
    }

    override fun onCleared() {
        super.onCleared()
        viewersPollingJob?.cancel()
        repository.chatWebSocketClient.disconnect()
    }
}
