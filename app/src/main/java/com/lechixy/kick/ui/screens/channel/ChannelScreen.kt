package com.lechixy.kick.ui.screens.channel

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsIgnoringVisibility
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.lechixy.kick.PlayerHostState
import com.lechixy.kick.PlayerMode
import com.lechixy.kick.R
import com.lechixy.kick.data.local.FollowManager
import com.lechixy.kick.data.model.ChannelDetail
import com.lechixy.kick.data.model.ChannelEvent
import com.lechixy.kick.data.model.ChannelVideo
import com.lechixy.kick.data.model.getBannerUrl
import com.lechixy.kick.data.settings.LocalSettingsRepository
import com.lechixy.kick.data.settings.SettingsKeys
import com.lechixy.kick.ui.components.AnimatedCounterText
import com.lechixy.kick.ui.components.CHAT_WIDTH_FRACTION
import com.lechixy.kick.ui.components.LiveBadge
import com.lechixy.kick.ui.components.PlayerChromeButton
import com.lechixy.kick.ui.components.TagChip
import com.lechixy.kick.ui.components.VerifiedBadge
import com.lechixy.kick.ui.components.VideoCard
import com.lechixy.kick.ui.components.WIDE_BREAKPOINT_DP
import com.lechixy.kick.ui.components.chat.ChatView
import com.lechixy.kick.ui.components.resolveEventChipColors
import com.lechixy.kick.ui.components.resolveLanguageChipColors
import com.lechixy.kick.util.FormatUtils
import com.lechixy.kick.util.buildOptimizedImageRequest
import kotlinx.coroutines.flow.first

enum class ChannelTab(
    @StringRes val label: Int,
) {
    CHAT(R.string.chat),
    HOME(R.string.home),
    ABOUT(R.string.about),
    VIDEOS(R.string.videos),
    CLIPS(R.string.clips);

    fun getLabel(context: Context): String {
        return context.getString(this.label)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChannelScreen(
    channelSlug: String,
    modifier: Modifier = Modifier,
    playerHost: PlayerHostState? = null,
    viewModel: ChannelViewModel = viewModel(),
    onChannelLoaded: (ChannelDetail) -> Unit = {},
    onBackClick: () -> Unit = {}
) {
    val state by viewModel.uiState.collectAsState()
    val chatMessages by viewModel.chatMessages.collectAsState()
    val chatConnectionState by viewModel.chatConnectionState.collectAsState()
    val pinnedMessage by viewModel.pinnedMessage.collectAsState()
    val userColors by viewModel.userColors.collectAsState()

    val settingsRepository = LocalSettingsRepository.current

    val context = LocalContext.current
    var selectedTab by remember { mutableStateOf(ChannelTab.CHAT) }

    val followedSlugs by FollowManager.followedSlugs.collectAsState()
    val isFollowing = remember(followedSlugs, channelSlug) {
        FollowManager.isFollowing(channelSlug)
    }

    val isControlsVisible = playerHost?.isControlsVisible ?: true
    val isChatVisible = playerHost?.isChatVisible ?: true
    val chatWidthFraction = playerHost?.chatWidthFraction ?: CHAT_WIDTH_FRACTION
    val isTheaterMode = playerHost?.isTheaterMode ?: false

    LaunchedEffect(channelSlug) {
        val autoConnect = settingsRepository.get(SettingsKeys.AUTO_CONNECT_CHAT).first()
        viewModel.loadChannel(channelSlug, autoConnectChat = autoConnect)
    }

    LaunchedEffect(state.channel?.id, state.channel?.livestream?.id) {
        state.channel?.let { channel ->
            onChannelLoaded(channel)
            if (channel.livestream == null && selectedTab == ChannelTab.CHAT) {
                selectedTab = ChannelTab.HOME
            }
        }
    }

    val inlineInsets = WindowInsets.systemBarsIgnoringVisibility.union(WindowInsets.displayCutout)
    val density = LocalDensity.current

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .pointerInput(Unit) {
                detectTapGestures { }
            }
    ) {
        val channel = state.channel

        BoxWithConstraints(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(inlineInsets)
        ) {
            val screenWidthDp = maxWidth
            val screenHeightDp = maxHeight
            val isWide = screenWidthDp >= WIDE_BREAKPOINT_DP.dp
            val bannerWidthPx = remember(screenWidthDp, density) {
                with(density) { screenWidthDp.toPx().toInt() }
            }

            var isDraggingHandle by remember { mutableStateOf(false) }
            var dragAccumulatedPx by remember { mutableFloatStateOf(0f) }
            var dragStartFraction by remember { mutableFloatStateOf(chatWidthFraction) }

            val isFullScreen = playerHost?.mode == PlayerMode.Fullscreen
            val isChatActuallyVisible = if (isFullScreen) (isTheaterMode && isChatVisible) else (isWide && isChatVisible)
            val targetChatFraction = if (isChatActuallyVisible) chatWidthFraction else 0f
            val animatedChatFraction by animateFloatAsState(
                targetValue = targetChatFraction,
                animationSpec = if (isDraggingHandle) snap() else tween(300, easing = FastOutSlowInEasing),
                label = "chatWidthFraction"
            )

            LaunchedEffect(animatedChatFraction) {
                playerHost?.animatedChatFraction = animatedChatFraction
            }

            Row(
                Modifier.fillMaxSize()
            ) {
                Column(
                    Modifier
                        .weight(if (isWide) (1f - animatedChatFraction).coerceAtLeast(0.01f) else 1f)
                        .fillMaxHeight()
                ) {
                    // Player'ın oturduğu yer (PlayerHost bu alanı işliyor)
                    if (channel != null && channel.livestream == null) {
                        val bannerUrl = remember(channel, bannerWidthPx) {
                            channel.getBannerUrl(targetWidthPx = bannerWidthPx)
                        }
                        val bannerWidthDp = remember(screenWidthDp) { screenWidthDp.value.toInt() }
                        val bannerHeightDp = remember(screenWidthDp) { (screenWidthDp.value * 9f / 16f).toInt() }

                        Box(
                            Modifier
                                .fillMaxWidth()
                                .aspectRatio(16f / 9f)
                                .background(Color.Black),
                            contentAlignment = Alignment.Center
                        ) {
                            if (!bannerUrl.isNullOrEmpty()) {
                                AsyncImage(
                                    model = remember(bannerUrl, bannerWidthDp, bannerHeightDp) {
                                        buildOptimizedImageRequest(
                                            context = context,
                                            data = bannerUrl,
                                            targetWidthDp = bannerWidthDp,
                                            targetHeightDp = bannerHeightDp
                                        )
                                    },
                                    contentDescription = "Offline Banner",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Text("CHANNEL OFFLINE", color = Color.Gray, fontWeight = FontWeight.Bold)
                            }
                            // Player yokken kapatma tuşu da sayfada olmalı
                            PlayerChromeButton(
                                painter = painterResource(R.drawable.keyboard_arrow_down_24dp_e3e3e3_fill0_wght400_grad0_opsz24),
                                contentDescription = "Back",
                                modifier = Modifier
                                    .align(Alignment.TopStart)
                                    .padding(4.dp),
                                onClick = onBackClick
                            )
                        }
                    } else {
                        val theaterProgress by animateFloatAsState(
                            targetValue = if (isTheaterMode) 1f else 0f,
                            animationSpec = tween(300, easing = FastOutSlowInEasing),
                            label = "theaterProgress"
                        )
                        val normalHeightDp = remember(screenWidthDp, animatedChatFraction, isWide) {
                            val contentWidth = if (isWide) screenWidthDp * (1f - animatedChatFraction) else screenWidthDp
                            contentWidth * 9f / 16f
                        }
                        val headerHeightPx = playerHost?.channelHeaderHeightPx ?: 0
                        val headerHeightDp = if (headerHeightPx > 0) {
                            with(density) { headerHeightPx.toDp() }
                        } else {
                            screenHeightDp * 0.24f
                        }
                        val theaterTargetHeightDp = remember(screenHeightDp, headerHeightDp) {
                            (screenHeightDp - headerHeightDp).coerceAtLeast(0.dp)
                        }
                        val playerHeightDp = if (isWide) {
                            normalHeightDp + (theaterTargetHeightDp - normalHeightDp) * theaterProgress
                        } else {
                            normalHeightDp
                        }
                        Spacer(Modifier.fillMaxWidth().height(playerHeightDp))
                    }

                    if (channel != null) {
                        val isLive = channel.livestream != null
                        val lastLiveText = remember(state.videos) {
                            val latestVideo = state.videos.firstOrNull()
                            FormatUtils.formatLastLive(latestVideo?.startTime ?: latestVideo?.createdAt, true)
                        }

                        // ChannelHeaderInfo: geniş ekranda (tablet) theater mod dahil her zaman görünür, mobilde kontrollerle senkronize
                        AnimatedVisibility(
                            visible = if (isWide) true else isControlsVisible,
                            enter = expandVertically(tween(300)) + fadeIn(tween(300)),
                            exit = shrinkVertically(tween(300)) + fadeOut(tween(300))
                        ) {
                            ChannelHeaderInfo(
                                channel = channel,
                                event = state.event,
                                isLive = isLive,
                                lastLiveText = lastLiveText,
                                isFollowing = isFollowing,
                                onFollowClick = { FollowManager.toggleFollow(channelSlug) },
                                context = context,
                                onHeightMeasured = { heightPx ->
                                    if (heightPx > 0) {
                                        playerHost?.channelHeaderHeightPx = heightPx
                                    }
                                }
                            )
                        }

                        // ChannelTabsRow: geniş ekranda theater modda gizlenir, mobilde kontrollerle senkronize
                        AnimatedVisibility(
                            visible = if (isWide) !isTheaterMode else isControlsVisible,
                            enter = expandVertically(tween(300)) + fadeIn(tween(300)),
                            exit = shrinkVertically(tween(300)) + fadeOut(tween(300))
                        ) {
                            ChannelTabsRow(
                                selectedTab = selectedTab,
                                onTabSelected = { selectedTab = it }
                            )
                        }

                        if (selectedTab == ChannelTab.CHAT && !isWide) {
                            ChatView(
                                messages = chatMessages,
                                connectionState = chatConnectionState,
                                subscriberBadges = channel.subscriberBadges,
                                userColors = userColors,
                                pinnedMessage = pinnedMessage,
                                chatEvents = viewModel.chatEvents,
                                streamerUsername = channel.user?.username,
                                streamerSlug = channel.slug,
                                onConnectClick = viewModel::connectChat,
                                onDisconnectClick = viewModel::disconnectChat,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f)
                            )
                        } else {
                            AnimatedVisibility(
                                visible = if (isWide) !isTheaterMode else true,
                                enter = expandVertically(tween(300)) + fadeIn(tween(300)),
                                exit = shrinkVertically(tween(300)) + fadeOut(tween(300)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f)
                            ) {
                                LazyColumn(
                                    modifier = Modifier.fillMaxSize()
                                ) {
                                    item {
                                        ChannelTabContent(
                                            tab = selectedTab,
                                            channel = channel,
                                            videos = state.videos,
                                            onNavigateToVideos = { selectedTab = ChannelTab.VIDEOS }
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        Box(Modifier
                            .fillMaxWidth()
                            .weight(1f)) {
                            if (state.error != null) {
                                Text(
                                    text = state.error ?: "Unknown error",
                                    color = MaterialTheme.colorScheme.error,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier
                                        .align(Alignment.Center)
                                        .padding(16.dp)
                                )
                            } else {
                                CircularProgressIndicator(Modifier.align(Alignment.Center))
                            }
                        }
                    }
                }

                if (isWide && channel != null && animatedChatFraction > 0.001f) {
                    Box(
                        Modifier
                            .fillMaxHeight()
                            .weight(animatedChatFraction)
                    ) {
                        ChatView(
                            messages = chatMessages,
                            connectionState = chatConnectionState,
                            subscriberBadges = channel.subscriberBadges,
                            userColors = userColors,
                            pinnedMessage = pinnedMessage,
                            chatEvents = viewModel.chatEvents,
                            streamerUsername = channel.user?.username,
                            streamerSlug = channel.slug,
                            onConnectClick = viewModel::connectChat,
                            onDisconnectClick = viewModel::disconnectChat,
                            onHideChat = { playerHost?.isChatVisible = false },
                            modifier = Modifier.fillMaxSize()
                        )

                        ChatResizeHandle(
                            isDragging = isDraggingHandle,
                            onDragStart = {
                                isDraggingHandle = true
                                dragAccumulatedPx = 0f
                                dragStartFraction = chatWidthFraction
                            },
                            onDragEnd = {
                                isDraggingHandle = false
                            },
                            onDrag = { dragAmountPx ->
                                dragAccumulatedPx += dragAmountPx
                                val screenWidthPx = with(density) { screenWidthDp.toPx() }
                                if (screenWidthPx > 0f) {
                                    val deltaFraction = -dragAccumulatedPx / screenWidthPx
                                    val newFraction = dragStartFraction + deltaFraction
                                    if (newFraction < 0.18f) {
                                        playerHost?.isChatVisible = false
                                        isDraggingHandle = false
                                    } else {
                                        playerHost?.updateChatWidthFraction(newFraction)
                                    }
                                }
                            },
                            modifier = Modifier
                                .align(Alignment.CenterStart)
                                .fillMaxHeight()
                                .width(24.dp)
                                .offset(x = (-12).dp)
                        )
                    }
                }
            }
        }
    }
}

internal fun Context.findActivity(): Activity? {
    var ctx = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}

/**
 * Components
 */

@Composable
private fun ChannelHeaderInfo(
    channel: ChannelDetail,
    event: ChannelEvent?,
    isLive: Boolean,
    lastLiveText: String,
    isFollowing: Boolean,
    onFollowClick: () -> Unit,
    context: Context,
    onHeightMeasured: (Int) -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight(Alignment.Top, unbounded = true)
            .onSizeChanged { onHeightMeasured(it.height) }
    ) {
        if (isLive && channel.livestream != null) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp, 16.dp, 16.dp, 0.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = channel.livestream.sessionTitle ?: "No title",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 8.dp)
                    )
                    LiveBadge()
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FlowRow(
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        channel.livestream.categories.forEach { category ->
                            var translatedName = category.name
                            if (translatedName == "Just Chatting") translatedName = stringResource(R.string.category_just_chatting)
                            TagChip(tag = translatedName)
                        }
                        event?.title?.takeIf { it.isNotBlank() }?.let { eventTitle ->
                            val defaultContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                            val defaultContentColor = MaterialTheme.colorScheme.primary
                            val (eventContainerColor, eventContentColor) = remember(
                                event.palette?.accentPromoPoster,
                                defaultContainerColor,
                                defaultContentColor
                            ) {
                                resolveEventChipColors(
                                    accentHex = event.palette?.accentPromoPoster,
                                    defaultContainerColor = defaultContainerColor,
                                    defaultContentColor = defaultContentColor
                                )
                            }
                            TagChip(
                                tag = eventTitle,
                                icon = R.drawable.stars_2_24dp_e3e3e3_fill0_wght400_grad0_opsz24,
                                containerColor = eventContainerColor,
                                contentColor = eventContentColor
                            )
                        }
                        channel.livestream.tags.forEach { tag ->
                            TagChip(tag = tag)
                        }
                        val languageLabel = channel.livestream.language?.takeIf { it.isNotBlank() }
                            ?: channel.livestream.langIso?.takeIf { it.isNotBlank() }
                        if (!languageLabel.isNullOrBlank()) {
                            val defaultContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                            val defaultContentColor = MaterialTheme.colorScheme.primary
                            val (langContainerColor, langContentColor) = remember(
                                channel.livestream.langIso,
                                defaultContainerColor,
                                defaultContentColor
                            ) {
                                resolveLanguageChipColors(
                                    langIso = channel.livestream.langIso,
                                    defaultContainerColor = defaultContainerColor,
                                    defaultContentColor = defaultContentColor
                                )
                            }
                            TagChip(
                                tag = languageLabel,
                                containerColor = langContainerColor,
                                contentColor = langContentColor
                            )
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.group_24dp_e3e3e3_fill0_wght400_grad0_opsz24),
                            contentDescription = "Viewers",
                            tint = MaterialTheme.colorScheme.primary
                        )
                        androidx.compose.runtime.key(channel.id) {
                            AnimatedCounterText(
                                count = channel.livestream.viewerCount,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Text(
                            text = stringResource(R.string.viewers),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = remember(channel.user?.profilePic) {
                    buildOptimizedImageRequest(
                        context = context,
                        data = channel.user?.profilePic,
                        targetWidthDp = 64,
                        targetHeightDp = 64
                    )
                },
                contentDescription = channel.user?.username,
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape),
                contentScale = ContentScale.Crop
            )

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = channel.user?.username ?: channel.slug,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    if (channel.verified) {
                        VerifiedBadge(
                            modifier = Modifier.size(16.dp),
                            colors = listOf(
                                MaterialTheme.colorScheme.primary,
                                MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = FormatUtils.formatFollowersCount(channel.followersCount),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = stringResource(R.string.followers),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }

                if (!isLive) {
                    Text(
                        text = lastLiveText,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            Button(
                onClick = onFollowClick,
                shape = RoundedCornerShape(8.dp),
                colors = if (isFollowing) {
                    ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                },
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
            ) {
                Text(
                    text = if (isFollowing) "Following" else "Follow",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun ChannelTabsRow(
    selectedTab: ChannelTab,
    onTabSelected: (ChannelTab) -> Unit
) {
    PrimaryTabRow(
        selectedTabIndex = selectedTab.ordinal,
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.primary
    ) {
        ChannelTab.entries.forEach { tab ->
            Tab(
                selected = selectedTab == tab,
                onClick = { onTabSelected(tab) },
                text = {
                    Text(
                        text = tab.getLabel(LocalContext.current),
                        fontWeight = if (selectedTab == tab) FontWeight.Bold else FontWeight.Normal
                    )
                }
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChannelTabContent(
    tab: ChannelTab,
    channel: ChannelDetail,
    videos: List<ChannelVideo>,
    onNavigateToVideos: () -> Unit
) {
    when (tab) {
        ChannelTab.CHAT -> {
            // Managed in ChannelScreen main layout when selected
        }

        ChannelTab.HOME -> {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (videos.isNotEmpty()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Recent Broadcasts",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        TextButton(onClick = onNavigateToVideos) {
                            Text(
                                text = "More",
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    val recentLives = videos.take(4)
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        maxItemsInEachRow = 3
                    ) {
                        recentLives.forEach { video ->
                            Box(
                                modifier = Modifier
                                    .weight(1f, fill = false)
                                    .widthIn(min = 280.dp)
                            ) {
                                VideoCard(video = video)
                            }
                        }
                    }
                } else {
                    Text(
                        text = "Henüz içerik bulunmuyor.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
        }

        ChannelTab.ABOUT -> {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "About",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = if (!channel.user?.bio.isNullOrBlank()) channel.user.bio else "Biyografi eklenmemiş.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        ChannelTab.VIDEOS -> {
            if (videos.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Kayıtlı video bulunamadı.",
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            } else {
                FlowRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    maxItemsInEachRow = 3
                ) {
                    videos.forEach { video ->
                        Box(
                            modifier = Modifier
                                .weight(1f, fill = false)
                                .widthIn(min = 280.dp)
                        ) {
                            VideoCard(video = video)
                        }
                    }
                }
            }
        }

        ChannelTab.CLIPS -> {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Klipler yakında eklenecek.",
                    color = MaterialTheme.colorScheme.outline
                )
            }
        }
    }
}

@Composable
private fun ChatResizeHandle(
    isDragging: Boolean,
    onDragStart: () -> Unit,
    onDragEnd: () -> Unit,
    onDrag: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val glowAlpha by animateFloatAsState(
        targetValue = if (isDragging) 1f else 0f,
        animationSpec = tween(150),
        label = "handleGlow"
    )
    val handleColor = if (isDragging) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
    }

    Box(
        modifier = modifier
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { onDragStart() },
                    onDragEnd = { onDragEnd() },
                    onDragCancel = { onDragEnd() }
                ) { change, dragAmount ->
                    change.consume()
                    onDrag(dragAmount)
                }
            },
        contentAlignment = Alignment.Center
    ) {
        if (glowAlpha > 0.01f) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(10.dp)
                    .background(
                        brush = androidx.compose.ui.graphics.Brush.horizontalGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.primary.copy(alpha = 0f),
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.35f * glowAlpha),
                                MaterialTheme.colorScheme.primary.copy(alpha = 0f)
                            )
                        )
                    )
            )
        }

        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(1.5.dp)
                .background(handleColor)
        )

        if (glowAlpha > 0.01f) {
            Box(
                modifier = Modifier
                    .size(width = 4.dp, height = 40.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(MaterialTheme.colorScheme.primary)
            )
        }
    }
}

