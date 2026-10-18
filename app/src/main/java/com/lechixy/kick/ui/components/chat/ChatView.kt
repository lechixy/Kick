package com.lechixy.kick.ui.components.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lechixy.kick.R
import com.lechixy.kick.data.model.SubscriberBadge
import com.lechixy.kick.data.model.chat.ChatMessage
import com.lechixy.kick.data.model.chat.PinnedMessageInfo
import com.lechixy.kick.data.remote.ChatConnectionState
import com.lechixy.kick.data.settings.LocalSettingsRepository
import com.lechixy.kick.data.settings.SettingsKeys
import com.lechixy.kick.ui.settings.SettingCard
import com.lechixy.kick.ui.settings.SettingSlider
import com.lechixy.kick.ui.settings.SettingSwitch
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatView(
    messages: List<ChatMessage>,
    connectionState: ChatConnectionState,
    modifier: Modifier = Modifier,
    subscriberBadges: List<SubscriberBadge> = emptyList(),
    userColors: Map<String, String> = emptyMap(),
    pinnedMessage: PinnedMessageInfo? = null,
    streamerUsername: String? = null,
    streamerSlug: String? = null,
    onConnectClick: () -> Unit = {},
    onDisconnectClick: () -> Unit = {}
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
    val isUserDragging by listState.interactionSource.collectIsDraggedAsState()
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

    LaunchedEffect(isAtBottom, isUserDragging) {
        if (isAtBottom && !isUserDragging) {
            autoScrollEnabled = true
            unreadCount = 0
        } else if (isUserDragging && !isAtBottom) {
            autoScrollEnabled = false
        }
    }

    val lastMessageId = messages.lastOrNull()?.id

    LaunchedEffect(lastMessageId) {
        if (lastMessageId != null && messages.isNotEmpty()) {
            if (autoScrollEnabled && !isUserDragging) {
                listState.scrollToItem(messages.lastIndex)
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
            onToggleOptions = { showOptions = true }
        )

        // Chat Options Modal BottomSheet
        if (showOptions) {
            val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            ModalBottomSheet(
                onDismissRequest = { showOptions = false },
                sheetState = sheetState,
                sheetMaxWidth = 640.dp,
                dragHandle = { BottomSheetDefaults.DragHandle() },
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .padding(bottom = 16.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Sohbet Ayarları",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        SettingSwitch(
                            key = SettingsKeys.AUTO_CONNECT_CHAT,
                            title = "Otomatik Bağlan",
                            description = "Kanal ekranına girildiğinde sohbete otomatik bağlan"
                        )

                        val isDisconnected = connectionState == ChatConnectionState.DISCONNECTED
                        SettingCard(
                            title = "Sohbet Bağlantısını Kes",
                            description = if (isDisconnected) {
                                "Sohbet bağlantısı şu anda kapalı"
                            } else {
                                "Mevcut sohbet odası bağlantısını sonlandır"
                            },
                            enabled = !isDisconnected,
                            onClick = {
                                onDisconnectClick()
                                showOptions = false
                            }
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
                            valueRange = 0f..25f,
                            steps = 24,
                            valueFormatter = { "${it}dp" }
                        )

                        Spacer(modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars))
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
                        ChatConnectionState.DISCONNECTED -> {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text(
                                    text = if (!autoConnectChat) {
                                        "Otomatik sohbet bağlantısı kapalı."
                                    } else {
                                        "Sohbet bağlantısı kesildi."
                                    },
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
                            fontSizeSp = fontSize,
                            subscriberBadges = subscriberBadges,
                            userColors = userColors,
                            streamerUsername = streamerUsername,
                            streamerSlug = streamerSlug
                        )
                    }
                }
            }

            // Pinned Message Overlay at top of chat
            PinnedMessageOverlay(
                pinnedMessage = pinnedMessage,
                fontSizeSp = fontSize,
                subscriberBadges = subscriberBadges,
                streamerUsername = streamerUsername,
                streamerSlug = streamerSlug,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
            )

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
                                val lastIdx = messages.lastIndex
                                if (lastIdx >= 0) {
                                    listState.scrollToItem(lastIdx)
                                }
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
private fun PinnedMessageOverlay(
    pinnedMessage: PinnedMessageInfo?,
    fontSizeSp: Int,
    subscriberBadges: List<SubscriberBadge>,
    streamerUsername: String?,
    streamerSlug: String?,
    modifier: Modifier = Modifier
) {
    var cachedPinnedMessage by remember { mutableStateOf<PinnedMessageInfo?>(null) }
    var isHidden by remember { mutableStateOf(false) }
    var enterFromHide by remember { mutableStateOf(false) }
    var isExpanded by remember { mutableStateOf(false) }
    val scaleAnim = remember { Animatable(0f) }

    if (pinnedMessage != null) {
        cachedPinnedMessage = pinnedMessage
    }

    val currentMessageId = pinnedMessage?.message?.id
    val currentContent = pinnedMessage?.message?.content

    LaunchedEffect(currentMessageId, currentContent) {
        if (pinnedMessage != null) {
            enterFromHide = false
            isHidden = false
            isExpanded = false
            scaleAnim.snapTo(0f)
            scaleAnim.animateTo(
                targetValue = 1f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioLowBouncy,
                    stiffness = Spring.StiffnessMediumLow
                )
            )
        }
    }

    val showCard = pinnedMessage != null && !isHidden
    val showUnhidePin = pinnedMessage != null && isHidden

    Box(modifier = modifier) {
        // Main Pinned Message Surface
        AnimatedVisibility(
            visible = showCard,
            enter = if (enterFromHide) {
                slideInHorizontally(
                    animationSpec = tween(durationMillis = 280, delayMillis = 140),
                    initialOffsetX = { fullWidth -> fullWidth + 40 }
                ) + fadeIn(animationSpec = tween(durationMillis = 220, delayMillis = 140))
            } else {
                fadeIn(animationSpec = tween(durationMillis = 120))
            },
            exit = slideOutHorizontally(
                animationSpec = tween(durationMillis = 260),
                targetOffsetX = { fullWidth -> fullWidth + 40 }
            ) + fadeOut(animationSpec = tween(durationMillis = 200)),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
        ) {
            val activePin = cachedPinnedMessage ?: return@AnimatedVisibility
            val msg = activePin.message ?: return@AnimatedVisibility
            val sender = msg.sender
            val pinnedBy = activePin.pinnedBy

            val headerFontSizeSp = (fontSizeSp - 2).coerceAtLeast(10)
            val mutedColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f)
            val primaryColor = MaterialTheme.colorScheme.primary

            val senderColor = remember(sender?.identity?.color, primaryColor) {
                ChatTextParser.parseHexColor(sender?.identity?.color, primaryColor)
            }
            val senderBadges = remember(sender?.identity, subscriberBadges) {
                ChatTextParser.resolveBadges(sender?.identity, subscriberBadges)
            }
            val sentByHeader = ChatTextParser.parseUserHeaderWithBadges(
                prefixText = "Sent by ",
                prefixColor = mutedColor,
                senderName = sender?.username ?: "Unknown",
                senderColor = senderColor,
                fontSizeSp = headerFontSizeSp,
                badges = senderBadges,
                leadingIconRes = R.drawable.push_pin_24dp,
                leadingIconTint = primaryColor
            )

            val parsedBody = ChatTextParser.parseContentOnly(
                content = msg.content,
                fontSizeSp = fontSizeSp,
                streamerUsername = streamerUsername,
                streamerSlug = streamerSlug
            )

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.96f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                shadowElevation = 4.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 6.dp)
                    .graphicsLayer {
                        scaleX = scaleAnim.value
                        scaleY = scaleAnim.value
                    }
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { isExpanded = !isExpanded }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .animateContentSize(
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioNoBouncy,
                                stiffness = Spring.StiffnessMedium
                            )
                        )
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Row 1: PIN_ICON Sent by BADGES USERNAME ... Hide button
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = sentByHeader.annotatedString,
                            inlineContent = sentByHeader.inlineContent,
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontSize = headerFontSizeSp.sp,
                                lineHeight = (headerFontSizeSp + 4).sp
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 8.dp)
                        )

                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .clickable {
                                    enterFromHide = true
                                    isHidden = true
                                }
                        ) {
                            Text(
                                text = "Hide",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }

                    // Row 2: Message content (1 line when collapsed, all lines when expanded)
                    Text(
                        text = parsedBody.annotatedString,
                        inlineContent = parsedBody.inlineContent,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = fontSizeSp.sp,
                            lineHeight = (fontSizeSp + 5).sp
                        ),
                        maxLines = if (isExpanded) Int.MAX_VALUE else 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Row 3: Pinned by BADGES USERNAME (no icon, only when expanded)
                    if (isExpanded && pinnedBy != null) {
                        val isPinnedBySameSender = remember(sender, pinnedBy) {
                            sender != null && (
                                (sender.id != null && pinnedBy.id != null && sender.id == pinnedBy.id) ||
                                    (sender.username.isNotBlank() && sender.username.equals(pinnedBy.username, ignoreCase = true)) ||
                                    (!sender.slug.isNullOrBlank() && sender.slug.equals(pinnedBy.slug, ignoreCase = true))
                                )
                        }

                        if (isPinnedBySameSender) {
                            Text(
                                text = "Mesajın sahibi tarafından sabitlendi",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    color = mutedColor,
                                    fontWeight = FontWeight.Medium,
                                    fontSize = headerFontSizeSp.sp,
                                    lineHeight = (headerFontSizeSp + 4).sp
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 2.dp)
                            )
                        } else {
                            val pinnedByColor = remember(pinnedBy.identity?.color, primaryColor) {
                                ChatTextParser.parseHexColor(pinnedBy.identity?.color, primaryColor)
                            }
                            val pinnedByBadges = remember(pinnedBy.identity, subscriberBadges) {
                                ChatTextParser.resolveBadges(pinnedBy.identity, subscriberBadges)
                            }
                            val pinnedByHeader = ChatTextParser.parseUserHeaderWithBadges(
                                prefixText = "Pinned by ",
                                prefixColor = mutedColor,
                                senderName = pinnedBy.username,
                                senderColor = pinnedByColor,
                                fontSizeSp = headerFontSizeSp,
                                badges = pinnedByBadges,
                                leadingIconRes = null
                            )

                            Text(
                                text = pinnedByHeader.annotatedString,
                                inlineContent = pinnedByHeader.inlineContent,
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontSize = headerFontSizeSp.sp,
                                    lineHeight = (headerFontSizeSp + 4).sp
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 2.dp)
                            )
                        }
                    }
                }
            }
        }

        // Small Pin Icon Button on the top-right when hidden
        AnimatedVisibility(
            visible = showUnhidePin,
            enter = slideInHorizontally(
                animationSpec = tween(durationMillis = 250, delayMillis = 200),
                initialOffsetX = { fullWidth -> fullWidth + 40 }
            ) + fadeIn(animationSpec = tween(durationMillis = 200, delayMillis = 200)),
            exit = slideOutHorizontally(
                animationSpec = tween(durationMillis = 180),
                targetOffsetX = { fullWidth -> fullWidth + 40 }
            ) + fadeOut(animationSpec = tween(durationMillis = 150)),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 6.dp, end = 10.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.96f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                shadowElevation = 4.dp,
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { isHidden = false }
            ) {
                Box(
                    modifier = Modifier.padding(8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(R.drawable.push_pin_24dp),
                        contentDescription = "Show pinned message",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
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
            text = "",
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
                    painter = painterResource(if (showOptions) R.drawable.settings_24dp_e3e3e3_fill1_wght400_grad0_opsz24 else R.drawable.settings_24dp_e3e3e3_fill0_wght400_grad0_opsz24),
                    contentDescription = "Chat Settings",
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
