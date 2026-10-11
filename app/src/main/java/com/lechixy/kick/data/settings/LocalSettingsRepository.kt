package com.lechixy.kick.data.settings

import androidx.compose.runtime.staticCompositionLocalOf

val LocalSettingsRepository = staticCompositionLocalOf<SettingsRepository> {
    error("SettingsRepository bulunamadı! CompositionLocalProvider ile sağlandığından emin olun.")
}