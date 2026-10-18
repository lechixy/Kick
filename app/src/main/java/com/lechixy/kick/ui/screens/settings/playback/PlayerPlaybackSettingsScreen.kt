package com.lechixy.kick.ui.screens.settings.playback

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import com.lechixy.kick.ui.settings.SettingsTopBar

@Composable
fun PlayerPlaybackSettingsScreen(
    modifier: Modifier = Modifier,
    onBackClick: () -> Unit
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = { SettingsTopBar(title = "Player & Playback", onBackClick = onBackClick) }
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
                        title = "Player",
                        description = "Oynatıcı ve video ayarları"
                    ) {
                        SettingSwitch(
                            key = SettingsKeys.LOW_LATENCY,
                            title = "Düşük Gecikme Modu (Low Latency)",
                            description = "Web sitesi ile anlık senkronize yayın (~2s gecikme). Güçlü internet bağlantısı gerektirir."
                        )
                        SettingSwitch(
                            key = SettingsKeys.AUTO_FULLSCREEN_LIVE,
                            title = "Otomatik Tam Ekran (Auto Fullscreen)",
                            description = "Kanala girildiğinde yayın canlıysa ve oynatıcı yüklendiyse hemen tam ekrana geç"
                        )
                        SettingDropdown(
                            key = SettingsKeys.VIDEO_QUALITY,
                            title = "Varsayılan Video Kalitesi",
                            description = "Mobil ve Wi-Fi üzerindeki başlangıç çözünürlüğü",
                            options = listOf(
                                -1 to "Auto",
                                160 to "160p",
                                360 to "360p",
                                480 to "480p",
                                720 to "720p (HD)",
                                1080 to "1080p (FHD)"
                            )
                        )
                        SettingSlider(
                            key = SettingsKeys.CHAT_FONT_SIZE,
                            title = "Sohbet Yazı Boyutu",
                            description = "Yayın içi sohbet mesajlarının yazı tipi boyutu",
                            valueRange = 10f..24f,
                            steps = 13,
                            valueFormatter = { "${it}sp" }
                        )
                    }
                }
            }
        }
    }
}