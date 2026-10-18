package com.lechixy.kick.ui.screens.channel

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsIgnoringVisibility
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
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
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
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

enum class ChannelTab(val title: String) {
    CHAT("Chat"),
    HOME("Home"),
    ABOUT("About"),
    VIDEOS("Videos"),
    CLIPS("Clips")
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChannelScreen(
    channelSlug: String,
    modifier: Modifier = Modifier,
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

        BoxWithConstraints(Modifier.fillMaxSize()) {
            val screenWidthDp = maxWidth
            val isWide = screenWidthDp >= WIDE_BREAKPOINT_DP.dp
            val bannerWidthPx = remember(screenWidthDp, density) {
                with(density) { screenWidthDp.toPx().toInt() }
            }

            Row(
                Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(inlineInsets)
            ) {
                Column(
                    Modifier
                        .weight(if (isWide) 1f - CHAT_WIDTH_FRACTION else 1f)
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
                        Spacer(Modifier
                            .fillMaxWidth()
                            .aspectRatio(16f / 9f))   // canlı: player burada oturuyor
                    }

                    if (channel != null) {
                        val isLive = channel.livestream != null
                        val lastLiveText = remember(state.videos) {
                            val latestVideo = state.videos.firstOrNull()
                            FormatUtils.formatLastLive(latestVideo?.startTime ?: latestVideo?.createdAt, true)
                        }

                        ChannelHeaderInfo(
                            channel = channel,
                            event = state.event,
                            isLive = isLive,
                            lastLiveText = lastLiveText,
                            isFollowing = isFollowing,
                            onFollowClick = { FollowManager.toggleFollow(channelSlug) },
                            context = context
                        )

                        ChannelTabsRow(
                            selectedTab = selectedTab,
                            onTabSelected = { selectedTab = it }
                        )

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
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f)
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

                if (isWide && channel != null) {
                    Row(
                        Modifier
                            .fillMaxHeight()
                            .weight(CHAT_WIDTH_FRACTION)
                    ) {
                        VerticalDivider(
                            thickness = 1.dp,
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )
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
                                .weight(1f)
                                .fillMaxHeight()
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
    context: Context
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
                        TagChip(tag = category.name)
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
                    text = "followers",
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
                        text = tab.title,
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
