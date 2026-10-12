package com.lechixy.kick.ui.screens.channel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lechixy.kick.data.model.ChannelDetail
import com.lechixy.kick.data.model.ChannelVideo
import com.lechixy.kick.data.model.chat.ChatMessage
import com.lechixy.kick.data.remote.ChatConnectionState
import com.lechixy.kick.data.remote.KickNetwork
import com.lechixy.kick.data.repository.KickRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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

    private val _uiState = MutableStateFlow(ChannelUiState())
    val uiState: StateFlow<ChannelUiState> = _uiState.asStateFlow()

    val chatMessages: StateFlow<List<ChatMessage>> = repository.chatWebSocketClient.messages
    val chatConnectionState: StateFlow<ChatConnectionState> = repository.chatWebSocketClient.connectionState

    fun loadChannel(slug: String, autoConnectChat: Boolean = true) {
        viewModelScope.launch {
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
        repository.chatWebSocketClient.disconnect()
    }
}
