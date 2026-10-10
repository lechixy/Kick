package com.lechixy.kick.data.local

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object PlayerSettingsManager {
    private const val PREFS_NAME = "kick_player_settings_prefs"
    private const val KEY_PIP_ENABLED = "pip_enabled"

    private val _pipEnabled = MutableStateFlow(true)
    val pipEnabled: StateFlow<Boolean> = _pipEnabled.asStateFlow()

    private var sharedPreferences: SharedPreferences? = null

    fun init(context: Context) {
        if (sharedPreferences == null) {
            val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            sharedPreferences = prefs
            _pipEnabled.value = prefs.getBoolean(KEY_PIP_ENABLED, true)
        }
    }

    fun setPipEnabled(enabled: Boolean) {
        _pipEnabled.value = enabled
        sharedPreferences?.edit()?.putBoolean(KEY_PIP_ENABLED, enabled)?.apply()
    }
}
