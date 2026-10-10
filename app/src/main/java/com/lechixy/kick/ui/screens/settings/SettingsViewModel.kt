package com.lechixy.kick.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lechixy.kick.data.repository.SettingKey
import com.lechixy.kick.data.repository.SettingsKeys
import com.lechixy.kick.data.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: SettingsRepository
) : ViewModel() {

    private fun <T> observe(
        key: SettingKey<T>
    ): StateFlow<T> {
        return repository.get(key).stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = key.defaultValue
        )
    }

    // General
    val dataSaver = observe(SettingsKeys.DATA_SAVER)

    val autoplay = observe(SettingsKeys.AUTOPLAY)
    val theme = observe(SettingsKeys.THEME)
    val videoQuality = observe(SettingsKeys.VIDEO_QUALITY)

    fun <T> set(key: SettingKey<T>, value: T) {
        viewModelScope.launch {
            repository.set(key, value)
        }
    }

    fun <T> reset(key: SettingKey<T>) {
        viewModelScope.launch {
            repository.reset(key)
        }
    }

    fun resetAll() {
        viewModelScope.launch {
            repository.resetAll()
        }
    }
}