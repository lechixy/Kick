package com.lechixy.kick.util

import android.app.Activity
import android.app.LocaleManager
import android.content.Context
import android.os.Build
import android.os.LocaleList
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

object LanguageUtils {
    fun updateAppLocale(context: Context, languageTag: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val localeManager = context.getSystemService(Context.LOCALE_SERVICE) as? LocaleManager
            if (languageTag == "system" || languageTag.isEmpty()) {
                // Boş liste gönderildiğinde sistem varsayılanına döner
                localeManager?.applicationLocales = LocaleList.getEmptyLocaleList()
            } else {
                localeManager?.applicationLocales = LocaleList.forLanguageTags(languageTag)
            }
        } else {
            if (languageTag == "system" || languageTag.isEmpty()) {
                AppCompatDelegate.setApplicationLocales(LocaleListCompat.getEmptyLocaleList())
            } else {
                val appLocale = LocaleListCompat.forLanguageTags(languageTag)
                AppCompatDelegate.setApplicationLocales(appLocale)
            }
        }

        (context as? Activity)?.recreate()
    }

    fun getCurrentAppLanguage(context: Context): String {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val localeManager = context.getSystemService(Context.LOCALE_SERVICE) as? LocaleManager
            val locales = localeManager?.applicationLocales
            // Eğer uygulama için özel bir dil atanmamışsa "system" döner
            if (locales == null || locales.isEmpty) {
                "system"
            } else {
                locales.get(0).language
            }
        } else {
            val appLocales = AppCompatDelegate.getApplicationLocales()
            if (appLocales.isEmpty) {
                "system"
            } else {
                appLocales.get(0)?.language ?: "system"
            }
        }
    }
}