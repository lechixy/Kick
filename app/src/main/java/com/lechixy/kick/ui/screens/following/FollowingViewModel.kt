package com.lechixy.kick.ui.screens.following

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lechixy.kick.data.model.Livestream
import com.lechixy.kick.data.remote.KickNetwork
import com.lechixy.kick.data.repository.KickRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class FollowingUiState(
    val isLoading: Boolean = false,
    val streams: List<Livestream> = emptyList(),
    val error: String? = null
)

class FollowingViewModel(
    private val repository: KickRepository = KickRepository(KickNetwork.api)
) : ViewModel() {

    private val _uiState = MutableStateFlow(FollowingUiState())
    val uiState: StateFlow<FollowingUiState> = _uiState.asStateFlow()

    fun loadFollowingStreams(followedSlugs: Set<String>) {
        if (followedSlugs.isEmpty()) {
            _uiState.value = FollowingUiState(streams = emptyList(), isLoading = false)
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val allStreams = repository.getLivestreams()
                val normalizedFollowed = followedSlugs.map { it.trim().lowercase() }.toSet()

                val filtered = allStreams.filter { stream ->
                    val streamSlug = stream.channel?.slug?.trim()?.lowercase() ?: ""
                    normalizedFollowed.contains(streamSlug)
                }

                _uiState.value = FollowingUiState(isLoading = false, streams = filtered)
            } catch (e: Exception) {
                _uiState.value = FollowingUiState(
                    isLoading = false,
                    error = e.message ?: "Takip edilen yayınlar yüklenemedi"
                )
            }
        }
    }
}