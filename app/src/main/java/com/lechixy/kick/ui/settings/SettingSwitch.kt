package com.lechixy.kick.ui.settings

import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lechixy.kick.data.settings.LocalSettingsRepository
import com.lechixy.kick.data.settings.SettingKey
import kotlinx.coroutines.launch

@Composable
fun SettingSwitch(
    key: SettingKey<Boolean>,
    title: String,
    modifier: Modifier = Modifier,
    description: String? = null,
    icon: Int? = null,
    enabled: Boolean = true
) {
    val repository = LocalSettingsRepository.current
    val coroutineScope = rememberCoroutineScope()
    val value by repository.get(key).collectAsStateWithLifecycle(initialValue = key.defaultValue)

    SettingSwitch(
        title = title,
        checked = value,
        onCheckedChange = { newValue ->
            if (key == com.lechixy.kick.data.settings.SettingsKeys.LOW_LATENCY) {
                com.lechixy.kick.data.local.PlayerSettingsManager.setLowLatencyEnabled(newValue)
            } else if (key == com.lechixy.kick.data.settings.SettingsKeys.AUTO_FULLSCREEN_LIVE) {
                com.lechixy.kick.data.local.PlayerSettingsManager.setAutoFullscreenLive(newValue)
            }
            coroutineScope.launch {
                repository.set(key, newValue)
            }
        },
        modifier = modifier,
        description = description,
        icon = icon,
        enabled = enabled
    )
}

@Composable
fun SettingSwitch(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    description: String? = null,
    icon: Int? = null,
    enabled: Boolean = true
) {
    SettingCard(
        title = title,
        modifier = modifier,
        description = description,
        icon = icon,
        enabled = enabled,
        onClick = if (enabled) { { onCheckedChange(!checked) } } else null,
        action = {
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                enabled = enabled
            )
        }
    )
}