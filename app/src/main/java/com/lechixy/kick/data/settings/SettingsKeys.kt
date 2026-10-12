package com.lechixy.kick.data.settings

import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey

sealed class SettingType {
    data object BooleanType : SettingType()
    data object StringType : SettingType()
    data object IntType : SettingType()
    data object FloatType : SettingType()
}

data class SettingKey<T>(
    val key: Preferences.Key<T>,
    val defaultValue: T,
    val type: SettingType,
    val isExportable: Boolean = true,
    val validator: ((T) -> Boolean)? = null
)

object SettingsKeys {
    /*
     * General
     */
    val APP_LANGUAGE = SettingKey(
        key = stringPreferencesKey("app_language"),
        defaultValue = "system",
        type = SettingType.StringType
    )
    val THEME = SettingKey(
        key = stringPreferencesKey("theme"),
        defaultValue = "system",
        type = SettingType.StringType
    )
    val DYNAMIC_COLOR = SettingKey(
        key = booleanPreferencesKey("dynamic_color"),
        defaultValue = true,
        type = SettingType.BooleanType
    )


    /*
     * Player & Playback
     */
    val AUTOPLAY = SettingKey(
        key = booleanPreferencesKey("autoplay"),
        defaultValue = true,
        type = SettingType.BooleanType
    )
    val VIDEO_QUALITY = SettingKey(
        key = intPreferencesKey("video_quality"),
        defaultValue = 720,
        type = SettingType.IntType
    )
    val CHAT_FONT_SIZE = SettingKey(
        key = intPreferencesKey("chat_font_size"),
        defaultValue = 14,
        type = SettingType.IntType,
        validator = { it in 10..24 }
    )
    val AUTO_CONNECT_CHAT = SettingKey(
        key = booleanPreferencesKey("auto_connect_chat"),
        defaultValue = true,
        type = SettingType.BooleanType
    )
    val CHAT_MESSAGE_SPACING = SettingKey(
        key = intPreferencesKey("chat_message_spacing"),
        defaultValue = 2,
        type = SettingType.IntType,
        validator = { it in 0..16 }
    )

    val RECOMMENDED_LIVESTREAM_LANGUAGE = SettingKey(
        key = stringPreferencesKey("recommended_livestream_language"),
        defaultValue = "en",
        type = SettingType.StringType
    )
    /*
     * Exportable Keys
     */
    val ALL_KEYS: List<SettingKey<*>> by lazy {
        listOf(
            THEME,
            VIDEO_QUALITY,
            RECOMMENDED_LIVESTREAM_LANGUAGE,
            CHAT_FONT_SIZE,
            AUTO_CONNECT_CHAT,
            CHAT_MESSAGE_SPACING,
            DYNAMIC_COLOR
        )
    }
}