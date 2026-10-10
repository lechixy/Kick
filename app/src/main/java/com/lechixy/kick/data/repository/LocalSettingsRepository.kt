package com.lechixy.kick.data.repository

import androidx.compose.runtime.staticCompositionLocalOf

val LocalSettingsRepository =
    staticCompositionLocalOf<SettingsRepository> {
        error("SettingsRepository sağlanmamış.")
    }