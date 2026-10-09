package com.lechixy.kick.ui.screens.following

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lechixy.kick.data.model.ChannelDetail
import com.lechixy.kick.data.remote.KickNetwork
import com.lechixy.kick.data.repository.KickRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class FollowingUiState(
    val isLoading: Boolean = false,
    val online: List<ChannelDetail> = emptyList(),
    val offline: List<ChannelDetail> = emptyList(),
    val error: String? = null
)

class FollowingViewModel(
    private val repository: KickRepository = KickRepository(KickNetwork.api)
) : ViewModel() {

    private val _uiState = MutableStateFlow(FollowingUiState())
    val uiState: StateFlow<FollowingUiState> = _uiState.asStateFlow()

    fun loadFollowingChannels(followedSlugs: Set<String>) {
        if (followedSlugs.isEmpty()) {
            _uiState.value =
                FollowingUiState(online = emptyList(), offline = emptyList(), isLoading = false)
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                // Channels
                val online = mutableListOf<ChannelDetail>()
                val offline = mutableListOf<ChannelDetail>()

                // Live streams
                followedSlugs.forEach { slug ->
                    val livestreamDeferred = async { repository.getChannel(slug) }
                    val livestream = livestreamDeferred.await()
                    if (livestream.livestream != null) {
                        online.add(livestream)
                    } else {
                        offline.add(livestream)
                    }
                }

                _uiState.value =
                    FollowingUiState(isLoading = false, online = online, offline = offline)
            } catch (e: Exception) {
                _uiState.value = FollowingUiState(
                    isLoading = false,
                    online = emptyList(),
                    offline = emptyList(),
                    error = e.message ?: "Takip edilen kanallar yüklenemedi"
                )
            }
        }
    }
}