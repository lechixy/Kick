package com.lechixy.kick.ui.screens.settings.general

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lechixy.kick.data.settings.SettingsKeys
import com.lechixy.kick.ui.settings.SettingDropdown
import com.lechixy.kick.ui.settings.SettingSection
import com.lechixy.kick.ui.settings.SettingSlider
import com.lechixy.kick.ui.settings.SettingSwitch
import com.lechixy.kick.ui.settings.SettingTextField

@Composable
fun GeneralSettingsScreen(
    modifier: Modifier = Modifier
) {
    Scaffold(modifier = modifier.fillMaxSize()) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 840.dp) // WinUI 3 masaüstü/tablet responsive max genişlik sınırı
                    .padding(vertical = 8.dp)
            ) {
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

                item {
                    SettingSection(
                        title = "Sohbet ve Arayüz",
                        description = "Görünüm ve font yapılandırması"
                    ) {
                        SettingDropdown(
                            key = SettingsKeys.THEME,
                            title = "Uygulama Teması",
                            options = listOf(
                                "system" to "Sistem Varsayılanı",
                                "light" to "Açık Tema",
                                "dark" to "Koyu Tema"
                            )
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
            }
        }
    }
}