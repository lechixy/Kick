package com.lechixy.kick.ui.components.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lechixy.kick.R
import com.lechixy.kick.data.model.chat.ChatMessage
import com.lechixy.kick.data.remote.ChatConnectionState
import com.lechixy.kick.data.settings.LocalSettingsRepository
import com.lechixy.kick.data.settings.SettingsKeys
import com.lechixy.kick.ui.settings.SettingSection
import com.lechixy.kick.ui.settings.SettingSlider
import com.lechixy.kick.ui.settings.SettingSwitch
import kotlinx.coroutines.launch

@Composable
fun ChatView(
    messages: List<ChatMessage>,
    connectionState: ChatConnectionState,
    modifier: Modifier = Modifier,
    onConnectClick: () -> Unit = {}
) {
    val repository = LocalSettingsRepository.current
    val fontSize by repository
        .get(SettingsKeys.CHAT_FONT_SIZE)
        .collectAsStateWithLifecycle(initialValue = SettingsKeys.CHAT_FONT_SIZE.defaultValue)
    val autoConnectChat by repository
        .get(SettingsKeys.AUTO_CONNECT_CHAT)
        .collectAsStateWithLifecycle(initialValue = SettingsKeys.AUTO_CONNECT_CHAT.defaultValue)
    val messageSpacing by repository
        .get(SettingsKeys.CHAT_MESSAGE_SPACING)
        .collectAsStateWithLifecycle(initialValue = SettingsKeys.CHAT_MESSAGE_SPACING.defaultValue)

    var showOptions by remember { mutableStateOf(false) }

    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    var autoScrollEnabled by remember { mutableStateOf(true) }
    var unreadCount by remember { mutableIntStateOf(0) }

    val isAtBottom by remember {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            val totalItems = layoutInfo.totalItemsCount
            if (totalItems == 0) return@derivedStateOf true
            val lastVisibleItem = layoutInfo.visibleItemsInfo.lastOrNull() ?: return@derivedStateOf true
            lastVisibleItem.index >= totalItems - 2
        }
    }

    LaunchedEffect(isAtBottom) {
        if (isAtBottom) {
            autoScrollEnabled = true
            unreadCount = 0
        }
    }

    LaunchedEffect(listState.isScrollInProgress) {
        if (listState.isScrollInProgress && !isAtBottom) {
            autoScrollEnabled = false
        }
    }

    val lastMessageId = remember(messages) { messages.lastOrNull()?.id }

    LaunchedEffect(lastMessageId) {
        if (lastMessageId != null) {
            if (autoScrollEnabled) {
                listState.scrollToItem(messages.size - 1)
                unreadCount = 0
            } else {
                unreadCount++
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        // Chat Header with Options button
        ChatHeader(
            connectionState = connectionState,
            showOptions = showOptions,
            onToggleOptions = { showOptions = !showOptions }
        )

        // Expandable Chat Options Panel
        AnimatedVisibility(
            visible = showOptions,
            enter = fadeIn() + expandVertically(
                expandFrom = Alignment.Top
            ),
            exit = fadeOut() + shrinkVertically(
                shrinkTowards = Alignment.Top
            )
        ) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                ) {
                    SettingSection(
                        title = "Sohbet Ayarları"
                    ) {
                        SettingSwitch(
                            key = SettingsKeys.AUTO_CONNECT_CHAT,
                            title = "Otomatik Bağlan",
                            description = "Kanal ekranına girildiğinde sohbete otomatik bağlan"
                        )

                        SettingSlider(
                            key = SettingsKeys.CHAT_FONT_SIZE,
                            title = "Yazı Boyutu",
                            description = "Sohbet mesajlarının yazı tipi boyutu",
                            valueRange = 10f..24f,
                            steps = 13,
                            valueFormatter = { "${it}sp" }
                        )

                        SettingSlider(
                            key = SettingsKeys.CHAT_MESSAGE_SPACING,
                            title = "Mesaj Aralığı",
                            description = "Mesajlar arasındaki dikey aralık",
                            valueRange = 0f..12f,
                            steps = 11,
                            valueFormatter = { "${it}dp" }
                        )
                    }
                }
            }
        }

        // Chat Message List
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            if (messages.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    when (connectionState) {
                        ChatConnectionState.CONNECTING -> {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    strokeWidth = 2.5.dp
                                )
                                Text(
                                    text = "Sohbete bağlanılıyor...",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            }
                        }
                        ChatConnectionState.DISCONNECTED if !autoConnectChat -> {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text(
                                    text = "Otomatik sohbet bağlantısı kapalı.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.outline
                                )
                                Button(
                                    onClick = onConnectClick,
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text("Sohbete Bağlan")
                                }
                            }
                        }
                        else -> {
                            Text(
                                text = "Henüz mesaj yok. İlk mesajı bekle!",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(messageSpacing.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 8.dp)
                ) {
                    items(
                        items = messages,
                        key = { it.id }
                    ) { message ->
                        ChatMessageItem(
                            message = message,
                            fontSizeSp = fontSize
                        )
                    }
                }
            }

            // Floating "Scroll to bottom" button with unread counter
            androidx.compose.animation.AnimatedVisibility(
                visible = !isAtBottom && messages.isNotEmpty(),
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 12.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    shadowElevation = 6.dp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .clickable {
                            autoScrollEnabled = true
                            unreadCount = 0
                            scope.launch {
                                listState.scrollToItem(messages.size - 1)
                            }
                        }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.keyboard_arrow_down_24dp_e3e3e3_fill0_wght400_grad0_opsz24),
                            contentDescription = "Scroll to bottom",
                            modifier = Modifier.size(18.dp)
                        )
                        val buttonText = if (unreadCount > 0) "En son mesajlara git (+$unreadCount)" else "En son mesajlara git"
                        Text(
                            text = buttonText,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ChatHeader(
    connectionState: ChatConnectionState,
    showOptions: Boolean,
    onToggleOptions: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = "CHAT",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val (statusText, statusColor) = when (connectionState) {
                ChatConnectionState.CONNECTED -> "Canlı" to MaterialTheme.colorScheme.primary
                ChatConnectionState.CONNECTING -> "Bağlanıyor..." to MaterialTheme.colorScheme.secondary
                ChatConnectionState.DISCONNECTED -> "Çevrimdışı" to MaterialTheme.colorScheme.outline
                ChatConnectionState.ERROR -> "Hata" to MaterialTheme.colorScheme.error
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(statusColor)
                )

                Text(
                    text = statusText,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            IconButton(
                onClick = onToggleOptions,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    painter = painterResource(if (showOptions) R.drawable.close_24dp_e3e3e3_fill0_wght400_grad0_opsz24 else R.drawable.settings_24dp_e3e3e3_fill0_wght400_grad0_opsz24),
                    contentDescription = "Chat Settings",
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
