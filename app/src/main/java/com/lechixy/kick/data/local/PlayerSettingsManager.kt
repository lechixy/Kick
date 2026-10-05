package com.lechixy.kick.data.local

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object PlayerSettingsManager {
    private const val PREFS_NAME = "kick_player_settings"
    private const val KEY_PIP_ENABLED = "pip_enabled"
    private const val KEY_DATA_SAVER = "data_saver_enabled"

    private val _pipEnabled = MutableStateFlow(true)
    private val _dataSaverEnabled = MutableStateFlow(false)

    val pipEnabled: StateFlow<Boolean> = _pipEnabled.asStateFlow()
    val dataSaverEnabled: StateFlow<Boolean> = _dataSaverEnabled.asStateFlow()

    private var prefs: SharedPreferences? = null

    fun init(context: Context) {
        if (prefs == null) {
            prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            _pipEnabled.value = prefs?.getBoolean(KEY_PIP_ENABLED, true) ?: true
            _dataSaverEnabled.value = prefs?.getBoolean(KEY_DATA_SAVER, false) ?: false
        }
    }

    fun setPipEnabled(enabled: Boolean) {
        prefs?.edit()?.putBoolean(KEY_PIP_ENABLED, enabled)?.apply()
        _pipEnabled.value = enabled
    }

    fun setDataSaverEnabled(enabled: Boolean) {
        prefs?.edit()?.putBoolean(KEY_DATA_SAVER, enabled)?.apply()
        _dataSaverEnabled.value = enabled
    }
}