package com.lechixy.kick.data.repository

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

data class SettingKey<T>(
    val key: Preferences.Key<T>,
    val defaultValue: T
)

object SettingsKeys {

    // General
    val RECOMMENDED_LIVESTREAM_LANGUAGE = SettingKey(
        stringPreferencesKey("recommended_livestream_language"),
        "en"
    )
    val DATA_SAVER = SettingKey(
        booleanPreferencesKey("data_saver"),
        false
    )

    val AUTOPLAY = SettingKey(
        booleanPreferencesKey("autoplay"),
        true
    )

    val MUTE_BY_DEFAULT = SettingKey(
        booleanPreferencesKey("mute_by_default"),
        false
    )

    val THEME = SettingKey(
        stringPreferencesKey("theme"),
        "system"
    )

    val VIDEO_QUALITY = SettingKey(
        intPreferencesKey("video_quality"),
        720
    )
}

private val Context.settingsDataStore by preferencesDataStore(
    name = "app_settings"
)

class SettingsRepository(context: Context) {

    private val dataStore =
        context.applicationContext.settingsDataStore

    val preferences: Flow<Preferences> = dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }

    fun <T> get(key: SettingKey<T>): Flow<T> {
        return preferences.map { prefs ->
            prefs[key.key] ?: key.defaultValue
        }
    }

    suspend fun <T> set(
        key: SettingKey<T>,
        value: T
    ) {
        dataStore.edit { prefs ->
            prefs[key.key] = value
        }
    }

    suspend fun <T> reset(key: SettingKey<T>) {
        dataStore.edit { prefs ->
            prefs.remove(key.key)
        }
    }

    suspend fun resetAll() {
        dataStore.edit { prefs ->
            prefs.clear()
        }
    }
}