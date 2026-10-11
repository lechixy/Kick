package com.lechixy.kick.ui.screens.home

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lechixy.kick.data.repository.KickRepository
import com.lechixy.kick.data.settings.SettingsKeys
import com.lechixy.kick.data.settings.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val kickRepository: KickRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        loadStreams()
    }

    private suspend fun getCurrentLanguage(): String {
        return settingsRepository.get(SettingsKeys.RECOMMENDED_LIVESTREAM_LANGUAGE).first()
    }

    fun loadStreams() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isLoading = true,
                error = null
            )

            try {
                val language = getCurrentLanguage()
                val streams = kickRepository.getLivestreams(language = language)

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
                val language = getCurrentLanguage()
                val streams = kickRepository.getLivestreams(language = language)

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