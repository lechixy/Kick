package com.lechixy.kick.ui.screens.settings.general

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.lechixy.kick.data.settings.SettingsKeys
import com.lechixy.kick.ui.settings.SettingDropdown
import com.lechixy.kick.ui.settings.SettingSection
import com.lechixy.kick.ui.settings.SettingSlider
import com.lechixy.kick.ui.settings.SettingSwitch
import com.lechixy.kick.ui.settings.SettingTextField
import com.lechixy.kick.ui.settings.SettingsTopBar
import com.lechixy.kick.util.LanguageUtils.updateAppLocale

@Composable
fun GeneralSettingsScreen(
    modifier: Modifier = Modifier,
    onBackClick: () -> Unit
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = { SettingsTopBar(title = "General", onBackClick = onBackClick) }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            ) {
                item {
                    SettingSection(
                        title = "Sohbet ve Arayüz",
                        description = "Görünüm ve font yapılandırması"
                    ) {
                        val context = LocalContext.current
                        SettingDropdown(
                            key = SettingsKeys.APP_LANGUAGE,
                            title = "Uygulama Dili",
                            options = listOf(
                                "system" to "Sistem Varsayılanı",
                                "en" to "English",
                                "tr" to "Türkçe"
                            ),
                            onValueChanged = { selectedLanguageTag ->
                                updateAppLocale(context, selectedLanguageTag)
                            }
                        )

                        SettingDropdown(
                            key = SettingsKeys.THEME,
                            title = "Uygulama Teması",
                            options = listOf(
                                "system" to "Sistem Varsayılanı",
                                "light" to "Açık Tema",
                                "dark" to "Koyu Tema",
                                "amoled" to "Amoled Tema"
                            )
                        )

                        SettingSwitch(
                            key = SettingsKeys.DYNAMIC_COLOR,
                            title = "Dinamik Renkler",
                            description = "Sistem renk paletine göre uygulama renklerini değiştir"
                        )

                        SettingSlider(
                            key = SettingsKeys.CHAT_FONT_SIZE,
                            title = "Sohbet Yazı Boyutu",
                            description = "Yayın içi sohbet mesajlarının yazı tipi boyutu",
                            valueRange = 12f..22f,
                            steps = 4,
                            valueFormatter = { "${it}sp" }
                        )
                    }
                }

                item {
                    SettingSection(
                        title = "Yayınlar ve Oynatıcı",
                        description = "Canlı yayın akışı ve video oynatıcı tercihleri"
                    ) {
                        SettingSwitch(
                            key = SettingsKeys.AUTOPLAY,
                            title = "Otomatik Oynatma",
                            description = "Önerilen yayınları otomatik başlat"
                        )

                        SettingDropdown(
                            key = SettingsKeys.VIDEO_QUALITY,
                            title = "Varsayılan Video Kalitesi",
                            description = "Mobil ve Wi-Fi üzerindeki başlangıç çözünürlüğü",
                            options = listOf(
                                360 to "360p",
                                480 to "480p",
                                720 to "720p (HD)",
                                1080 to "1080p (FHD)"
                            )
                        )

                        SettingTextField(
                            key = SettingsKeys.RECOMMENDED_LIVESTREAM_LANGUAGE,
                            title = "Önerilen Yayın Dili",
                            description = "Akışta öne çıkarılacak yayıncıların birincil dili (örn. tr, en)",
                            placeholder = "tr"
                        )
                    }
                }
            }
        }
    }
}