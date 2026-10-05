package com.lechixy.kick.ui.screens.home

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lechixy.kick.data.remote.KickNetwork
import com.lechixy.kick.data.repository.KickRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class HomeViewModel : ViewModel() {

    private val repository = KickRepository(KickNetwork.api)

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        loadStreams()
    }

    fun loadStreams() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isLoading = true,
                error = null
            )

            try {
                val streams = repository.getLivestreams()

                _uiState.value = HomeUiState(
                    streams = streams
                )
            } catch (e: Exception) {
                _uiState.value = HomeUiState(
                    error = e.message ?: "Unknown error"
                )
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isRefreshing = true,
                error = null
            )

            Log.e("HomeViewModel", "Refreshing streams...")

            try {
                val streams = repository.getLivestreams()

                _uiState.value = _uiState.value.copy(
                    streams = streams,
                    isRefreshing = false
                )
            } catch (e: Exception) {
                Log.e("HomeViewModel", "Error refreshing streams", e)
                _uiState.value = _uiState.value.copy(
                    isRefreshing = false,
                    error = e.message ?: "Unknown error"
                )
            }
        }
    }
}