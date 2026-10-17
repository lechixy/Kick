package com.lechixy.kick.data.local

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object PlayerSettingsManager {
    private const val PREFS_NAME = "kick_player_settings_prefs"
    private const val KEY_PIP_ENABLED = "pip_enabled"
    private const val KEY_LOW_LATENCY_ENABLED = "low_latency_v2_enabled"

    private val _pipEnabled = MutableStateFlow(true)
    val pipEnabled: StateFlow<Boolean> = _pipEnabled.asStateFlow()

    private val _lowLatencyEnabled = MutableStateFlow(true)
    val lowLatencyEnabled: StateFlow<Boolean> = _lowLatencyEnabled.asStateFlow()

    private var sharedPreferences: SharedPreferences? = null

    fun init(context: Context) {
        if (sharedPreferences == null) {
            val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            sharedPreferences = prefs
            _pipEnabled.value = prefs.getBoolean(KEY_PIP_ENABLED, true)
            _lowLatencyEnabled.value = prefs.getBoolean(KEY_LOW_LATENCY_ENABLED, true)
        }
    }

    fun setPipEnabled(enabled: Boolean) {
        _pipEnabled.value = enabled
        sharedPreferences?.edit()?.putBoolean(KEY_PIP_ENABLED, enabled)?.apply()
    }

    fun setLowLatencyEnabled(enabled: Boolean) {
        _lowLatencyEnabled.value = enabled
        sharedPreferences?.edit()?.putBoolean(KEY_LOW_LATENCY_ENABLED, enabled)?.apply()
    }
}
