package com.lechixy.kick.ui.screens.channel

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.height
import androidx.compose.ui.input.nestedscroll.nestedScroll
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsIgnoringVisibility
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.key
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.lechixy.kick.PlayerHostState
import com.lechixy.kick.PlayerMode
import com.lechixy.kick.R
import com.lechixy.kick.data.model.ChannelClip
import com.lechixy.kick.data.model.ChannelDetail
import com.lechixy.kick.data.model.ChannelEvent
import com.lechixy.kick.data.model.ChannelVideo
import com.lechixy.kick.data.model.RecordedPlayback
import com.lechixy.kick.data.model.VideoThumbnail
import com.lechixy.kick.data.model.getBannerUrl
import com.lechixy.kick.data.model.toRecording
import com.lechixy.kick.data.settings.LocalSettingsRepository
import com.lechixy.kick.data.settings.SettingsKeys
import com.lechixy.kick.ui.components.AnimatedCounterText
import com.lechixy.kick.ui.components.CHAT_WIDTH_FRACTION
import com.lechixy.kick.ui.components.LiveBadge
import com.lechixy.kick.ui.components.TagChip
import com.lechixy.kick.ui.components.VerifiedBadge
import com.lechixy.kick.ui.components.VideoCard
import com.lechixy.kick.ui.components.WIDE_BREAKPOINT_DP
import com.lechixy.kick.ui.components.account.rememberAccountFollowAction
import com.lechixy.kick.ui.components.chat.ChatView
import com.lechixy.kick.ui.components.resolveEventChipColors
import com.lechixy.kick.ui.components.resolveLanguageChipColors
import com.lechixy.kick.util.FormatUtils
import com.lechixy.kick.util.buildOptimizedImageRequest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

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

    val settingsRepository = LocalSettingsRepository.current
    val scope = rememberCoroutineScope()

    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val initialIsWide = configuration.screenWidthDp >= WIDE_BREAKPOINT_DP
    var selectedTab by remember {
        mutableStateOf(if (initialIsWide) ChannelTab.HOME else ChannelTab.CHAT)
    }

    val followAction = rememberAccountFollowAction(channelSlug)

    var isProfilePhotoExpanded by remember(channelSlug) { mutableStateOf(false) }
    val isControlsVisible = playerHost?.isControlsVisible ?: true
    val isChatVisible = playerHost?.isChatVisible ?: true
    val chatWidthFraction = playerHost?.chatWidthFraction ?: CHAT_WIDTH_FRACTION
    val isTheaterMode = playerHost?.isTheaterMode ?: false

    val theaterAnim = remember {
        Animatable(if (isTheaterMode) 1f else 0f, visibilityThreshold = 0.0005f)
    }
    LaunchedEffect(isTheaterMode) {
        theaterAnim.animateTo(
            targetValue = if (isTheaterMode) 1f else 0f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessMediumLow,
                visibilityThreshold = 0.0005f
            )
        )
    }

    LaunchedEffect(channelSlug) {
        val autoConnect = settingsRepository.get(SettingsKeys.AUTO_CONNECT_CHAT).first()
        viewModel.loadChannel(channelSlug, autoConnectChat = autoConnect)
    }

    var previousLivestreamId by remember(channelSlug) { mutableStateOf<Long?>(null) }
    var hasInitializedChannel by remember(channelSlug) { mutableStateOf(false) }

    LaunchedEffect(state.channel) {
        state.channel?.let { channel ->
            onChannelLoaded(channel)
            val currentLivestreamId = channel.livestream?.id
            if (!hasInitializedChannel) {
                hasInitializedChannel = true
                if (currentLivestreamId == null && selectedTab == ChannelTab.CHAT) {
                    selectedTab = ChannelTab.HOME
                }
            } else if (previousLivestreamId == null && currentLivestreamId != null && !initialIsWide && playerHost?.recording == null) {
                selectedTab = ChannelTab.CHAT
            } else if (previousLivestreamId != null && currentLivestreamId == null && selectedTab == ChannelTab.CHAT) {
                selectedTab = ChannelTab.HOME
            }
            previousLivestreamId = currentLivestreamId
        }
    }

    LaunchedEffect(state.liveAnnouncementId) {
        val announcementId = state.liveAnnouncementId ?: return@LaunchedEffect
        playerHost?.triggerLiveAnnouncement(announcementId)
        viewModel.consumeLiveAnnouncement(announcementId)
    }

    val inlineInsets = WindowInsets.systemBarsIgnoringVisibility.union(WindowInsets.displayCutout)
    val density = LocalDensity.current

    val channel = state.channel
    val offline = channel != null && channel.livestream == null && playerHost?.recording == null
    var offlineCollapsed by remember(channelSlug) { mutableStateOf(false) }
    LaunchedEffect(offline) { if (!offline) offlineCollapsed = false }
    val offlineCollapse = animateFloatAsState(
        targetValue = if (offline && offlineCollapsed) 1f else 0f,
        animationSpec = tween(440), label = "offline_banner_collapse"
    )
    androidx.compose.runtime.DisposableEffect(playerHost, offlineCollapse) {
        playerHost?.offlineCollapseProgress = offlineCollapse
        onDispose {
            if (playerHost?.offlineCollapseProgress === offlineCollapse) playerHost?.offlineCollapseProgress = null
        }
    }
    val channelContentState = key(channelSlug, selectedTab) { rememberLazyListState() }
    val offlineScroll = remember(offline, channelContentState) {
        OfflineBannerScrollConnection(
            enabled = { offline },
            atTop = { !channelContentState.canScrollBackward },
            onCollapsedChange = { offlineCollapsed = it }
        )
    }
    val sharedChat = remember(viewModel, channel?.id, playerHost?.recording?.id) {
        movableContentOf<ChannelDetail, (() -> Unit)?, Modifier> { ch, onHide, chatMod ->
            if (playerHost?.recording != null) {
                Box(chatMod, contentAlignment = Alignment.Center) {
                    Text("Kayıt sohbeti yakında eklenecek.", modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.outline)
                }
            } else {
                ChannelChatSection(
                    viewModel = viewModel,
                    channel = ch,
                    onHideChat = onHide,
                    modifier = chatMod
                )
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .pointerInput(Unit) {
                detectTapGestures { }
            }
    ) {
        BoxWithConstraints(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(inlineInsets)
        ) {
            val screenWidthDp = maxWidth
            val isWide = screenWidthDp >= WIDE_BREAKPOINT_DP.dp
            val visibleTabs = remember(isWide) {
                if (isWide) ChannelTab.entries.filter { it != ChannelTab.CHAT }
                else ChannelTab.entries
            }
            val effectiveSelectedTab = if (isWide && selectedTab == ChannelTab.CHAT) {
                ChannelTab.HOME
            } else {
                selectedTab
            }

            LaunchedEffect(isWide) {
                if (isWide && selectedTab == ChannelTab.CHAT) {
                    selectedTab = ChannelTab.HOME
                }
            }
            val bannerWidthPx = remember(screenWidthDp, density) {
                with(density) { screenWidthDp.toPx().toInt() }
            }

            var isDraggingHandle by remember { mutableStateOf(false) }
            var dragAccumulatedPx by remember { mutableFloatStateOf(0f) }
            var dragStartFraction by remember { mutableFloatStateOf(chatWidthFraction) }

            val isFullScreen = playerHost?.mode == PlayerMode.Fullscreen
            val hasSideChat = isWide || isFullScreen
            val isChatActuallyVisible =
                playerHost?.isSideChatOpen(isWide) ?: (isWide && isChatVisible)

            Layout(
                contents = listOf(
                    {
                        Column(
                            Modifier.fillMaxSize().nestedScroll(offlineScroll)
                        ) {
                            // Player'ın oturduğu yer (PlayerHost bu alanı işliyor)
                            if (channel != null && channel.livestream == null && playerHost?.recording == null) {
                                val bannerUrl = remember(channel, bannerWidthPx) {
                                    channel.getBannerUrl(targetWidthPx = bannerWidthPx)
                                }
                                OfflineChannelBanner(
                                    channel = channel,
                                    bannerUrl = bannerUrl,
                                    collapseProgress = offlineCollapse,
                                    onCollapse = { offlineCollapsed = true },
                                    onBackClick = onBackClick
                                )
                            } else {
                                Spacer(
                                    Modifier
                                        .fillMaxWidth()
                                        .layout { measurable, constraints ->
                                            val colWidth = constraints.maxWidth
                                            val fullHeightPx = (colWidth * 9f / 16f).roundToInt()
                                            val compactHeightPx = 84.dp.roundToPx().coerceAtMost(fullHeightPx)
                                            val normalHeightPx = (fullHeightPx + (compactHeightPx - fullHeightPx) * offlineCollapse.value).roundToInt()
                                            val hPx = if (isWide) {
                                                val totalH = constraints.maxHeight
                                                val headerPx =
                                                    if ((playerHost?.channelHeaderHeightPx ?: 0) > 0) {
                                                        playerHost!!.channelHeaderHeightPx
                                                    } else {
                                                        (totalH * 0.24f).roundToInt()
                                                    }
                                                val theaterTargetPx =
                                                    (totalH - headerPx).coerceIn(0, totalH)
                                                (normalHeightPx + (theaterTargetPx - normalHeightPx) * theaterAnim.value).roundToInt()
                                            } else {
                                                normalHeightPx
                                            }
                                            val placeable = measurable.measure(
                                                Constraints.fixed(colWidth, hPx.coerceAtLeast(0))
                                            )
                                            layout(placeable.width, placeable.height) {
                                                placeable.place(0, 0)
                                            }
                                        }
                                )
                            }

                            if (channel != null) {
                                val isLive = channel.livestream != null && playerHost?.recording == null
                                val hasPlayback = isLive || playerHost?.recording != null
                                val lastLiveText = remember(state.videos) {
                                    val latestVideo = state.videos.firstOrNull()
                                    FormatUtils.formatLastLive(
                                        latestVideo?.startTime ?: latestVideo?.createdAt,
                                        true
                                    )
                                }

                                // ChannelHeaderInfo: geniş ekranda (tablet) theater mod dahil her zaman görünür, mobilde kontrollerle senkronize
                                AnimatedVisibility(
                                    visible = isProfilePhotoExpanded || if (isWide || !hasPlayback) true else isControlsVisible,
                                    enter = expandVertically(tween(300)) + fadeIn(tween(300)),
                                    exit = shrinkVertically(tween(300)) + fadeOut(tween(300))
                                ) {
                                    ChannelHeaderInfo(
                                        channel = channel,
                                        event = state.event,
                                        lastHourGiftedSubsRank = state.lastHourGiftedSubsRank,
                                        recording = playerHost?.recording,
                                        onPlayLive = { playerHost?.playLive() },
                                        onProfilePhotoExpandedChange = { isProfilePhotoExpanded = it },
                                        isLive = isLive,
                                        lastLiveText = lastLiveText,
                                        isFollowing = followAction.following,
                                        followLabel = followAction.label,
                                        followEnabled = followAction.enabled,
                                        onFollowClick = followAction.onClick,
                                        context = context,
                                        onHeightMeasured = { heightPx ->
                                            if (heightPx > 0 && playerHost?.channelHeaderHeightPx != heightPx) {
                                                playerHost?.channelHeaderHeightPx = heightPx
                                            }
                                        }
                                    )
                                }

                                // ChannelTabsRow: geniş ekranda theater modda gizlenir, mobilde kontrollerle senkronize
                                AnimatedVisibility(
                                    visible = if (isWide) (!isTheaterMode || !hasPlayback) else (!hasPlayback || isControlsVisible),
                                    enter = expandVertically(tween(300)) + fadeIn(tween(300)),
                                    exit = shrinkVertically(tween(300)) + fadeOut(tween(300))
                                ) {
                                    ChannelTabsRow(
                                        tabs = visibleTabs,
                                        selectedTab = effectiveSelectedTab,
                                        onTabSelected = { selectedTab = it }
                                    )
                                }

                                if (effectiveSelectedTab == ChannelTab.CHAT && !hasSideChat) {
                                    sharedChat(
                                        channel,
                                        null,
                                        Modifier
                                            .fillMaxWidth()
                                            .weight(1f)
                                    )
                                } else {
                                    AnimatedVisibility(
                                        visible = if (isWide) (!isTheaterMode || !hasPlayback) else true,
                                        enter = expandVertically(tween(300)) + fadeIn(tween(300)),
                                        exit = shrinkVertically(tween(300)) + fadeOut(tween(300)),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .weight(1f)
                                    ) {
                                        LazyColumn(
                                            state = channelContentState,
                                            modifier = Modifier.fillMaxSize()
                                        ) {
                                            item {
                                                ChannelTabContent(
                                                    tab = effectiveSelectedTab,
                                                    channel = channel,
                                                    videos = state.videos,
                                                    clips = state.clips,
                                                    clipsError = state.clipsError,
                                                    isClipsLoading = state.isClipsLoading,
                                                    onRetryClips = viewModel::retryClips,
                                                    onPlayRecording = { playerHost?.playRecording(it) },
                                                    onNavigateToVideos = {
                                                        selectedTab = ChannelTab.VIDEOS
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }
                            } else {
                                Box(
                                    Modifier
                                        .fillMaxWidth()
                                        .weight(1f)
                                ) {
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
                    },
                    {
                        // Yan sohbet paneli: hasSideChat aktifken her zaman compose'lu kalır,
                        // böylece sola kaydırmanın ilk karesinde sıfırdan oluşturulmaz, GPU'da kayarak açılır.
                        if (hasSideChat && channel != null) {
                            Box(
                                Modifier
                                    .fillMaxSize()
                                    .graphicsLayer {
                                        val openProgress = (playerHost?.chatOpenProgress?.value
                                            ?: if (isChatActuallyVisible) 1f else 0f).coerceIn(0f, 1f)
                                        alpha = openProgress
                                    }
                            ) {
                                sharedChat(
                                    channel,
                                    { playerHost?.setSideChatVisible(visible = false, isWide = isWide) },
                                    Modifier.fillMaxSize()
                                )

                                if (isChatActuallyVisible || isDraggingHandle) {
                                    ChatResizeHandle(
                                        isDragging = isDraggingHandle,
                                        onDragStart = {
                                            isDraggingHandle = true
                                            dragAccumulatedPx = 0f
                                            dragStartFraction = chatWidthFraction
                                        },
                                        onDragEnd = {
                                            isDraggingHandle = false
                                            val currentFraction = playerHost?.chatWidthFraction
                                            if (currentFraction != null && playerHost.isSideChatOpen(isWide)) {
                                                scope.launch {
                                                    settingsRepository.set(
                                                        SettingsKeys.CHAT_WIDTH_FRACTION,
                                                        currentFraction
                                                    )
                                                }
                                            }
                                        },
                                        onDrag = { dragAmountPx ->
                                            dragAccumulatedPx += dragAmountPx
                                            val screenWidthPx = with(density) { screenWidthDp.toPx() }
                                            if (screenWidthPx > 0f) {
                                                val deltaFraction = -dragAccumulatedPx / screenWidthPx
                                                val newFraction = dragStartFraction + deltaFraction
                                                if (newFraction < 0.18f) {
                                                    playerHost?.updateChatWidthFraction(dragStartFraction)
                                                    playerHost?.setSideChatVisible(
                                                        visible = false,
                                                        isWide = isWide
                                                    )
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
                ),
                modifier = Modifier.fillMaxSize()
            ) { (mainMeasurables, sideChatMeasurables), constraints ->
                val totalW = constraints.maxWidth
                val totalH = constraints.maxHeight
                val openProgress = if (hasSideChat) {
                    (playerHost?.chatOpenProgress?.value
                        ?: if (isChatActuallyVisible) 1f else 0f).coerceIn(0f, 1f)
                } else {
                    0f
                }
                val targetChatW = (totalW * chatWidthFraction).roundToInt().coerceAtLeast(1)
                val currentChatW = if (hasSideChat) (targetChatW * openProgress).roundToInt() else 0

                // Tam ekranda sol sütun player'ın altında kaldığı için sabit genişlikte tutulur ->
                // tam ekranda chat kaydırılırken arkadaki sayfa yeniden ölçülmez.
                val mainColW = if (!hasSideChat || isFullScreen) {
                    totalW
                } else {
                    (totalW - currentChatW).coerceAtLeast(0)
                }

                val mainPlaceable = mainMeasurables.firstOrNull()?.measure(
                    Constraints.fixed(mainColW, totalH)
                )

                val sideChatMeasureW = if (isDraggingHandle) {
                    currentChatW.coerceAtLeast(1)
                } else {
                    targetChatW
                }
                val sideChatPlaceable = sideChatMeasurables.firstOrNull()?.measure(
                    Constraints.fixed(sideChatMeasureW, totalH)
                )

                layout(totalW, totalH) {
                    // Keep inline measurements for the return transition, but never draw
                    // channel details behind the fullscreen player or fading side chat.
                    if (!isFullScreen) mainPlaceable?.place(0, 0)
                    if (sideChatPlaceable != null && currentChatW > 0) {
                        sideChatPlaceable.place(totalW - currentChatW, 0)
                    }
                }
            }
        }
    }
}

@Composable
private fun ChannelChatSection(
    viewModel: ChannelViewModel,
    channel: ChannelDetail,
    onHideChat: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    val chatMessages by viewModel.chatMessages.collectAsState()
    val chatConnectionState by viewModel.chatConnectionState.collectAsState()
    val pinnedMessage by viewModel.pinnedMessage.collectAsState()
    val userColors by viewModel.userColors.collectAsState()
    val chatSettings by viewModel.chatSettings.collectAsState()
    val pointsUpdated by viewModel.pointsUpdated.collectAsState()

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
        onHideChat = onHideChat,
        composer = { com.lechixy.kick.ui.components.account.AccountChatComposer(
            channel, chatConnectionState, chatSettings, pointsUpdated,
            rulesSeen = { generation -> viewModel.rulesSeen(generation, channel.id) },
            markRulesSeen = { generation -> viewModel.markRulesSeen(generation, channel.id) }) },
        modifier = modifier
    )
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
    lastHourGiftedSubsRank: Int?,
    isLive: Boolean,
    lastLiveText: String,
    isFollowing: Boolean,
    onFollowClick: () -> Unit,
    followLabel: String,
    followEnabled: Boolean,
    context: Context,
    recording: RecordedPlayback? = null,
    onPlayLive: () -> Unit = {},
    onProfilePhotoExpandedChange: (Boolean) -> Unit = {},
    onHeightMeasured: (Int) -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight(Alignment.Top, unbounded = true)
            .onSizeChanged { onHeightMeasured(it.height) }
    ) {
        val liveAvailable = isLive && channel.livestream != null
        val recordingAvailable = recording != null
        if ((liveAvailable) || recordingAvailable) {
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
                        text = when(liveAvailable) {
                            true -> channel.livestream.sessionTitle ?: "No title"
                            false -> recording?.title ?: "No title"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 8.dp)
                    )
                    if (liveAvailable) LiveBadge()
                    if (recordingAvailable) {
                        Text(
                            text = FormatUtils.formatLastLive(recording.startTime),
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
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
                        val categories = if (liveAvailable) {
                            channel.livestream.categories.map { it.name }
                        } else {
                            recording?.categories?.map { it.name }
                        }

                        categories?.forEach { name ->
                            val translatedName = when (name) {
                                "Just Chatting" -> stringResource(R.string.category_just_chatting)
                                else -> name
                            }
                            TagChip(tag = translatedName)
                        }
                        // Event
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
                        if (liveAvailable) {
                            channel.livestream.tags.forEach { tag ->
                                TagChip(tag = tag)
                            }
                        }

                        val languageLabel = if (liveAvailable) {
                            channel.livestream.language?.takeIf { it.isNotBlank() }
                        } else {
                            recording?.language?.takeIf { it.isNotBlank() }
                        }
                        if (!languageLabel.isNullOrBlank()) {
                            val defaultContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                            val defaultContentColor = MaterialTheme.colorScheme.primary
                            val (langContainerColor, langContentColor) = remember(
                                languageLabel,
                                defaultContainerColor,
                                defaultContentColor
                            ) {
                                resolveLanguageChipColors(
                                    language = languageLabel,
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

                    val viewerCount = if (liveAvailable) {
                        channel.livestream.viewerCount
                    } else {
                        recording?.views?.toInt()
                    }

                    if (viewerCount != null) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                painter = if (liveAvailable) painterResource(R.drawable.group_24dp_e3e3e3_fill0_wght400_grad0_opsz24) else painterResource(R.drawable.visibility_24dp_e3e3e3_fill0_wght400_grad0_opsz24),
                                contentDescription = if (liveAvailable) stringResource(R.string.viewers) else stringResource(R.string.views),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            androidx.compose.runtime.key(channel.id) {
                                AnimatedCounterText(
                                    count = viewerCount,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Text(
                                text = if (liveAvailable) stringResource(R.string.viewers) else stringResource(R.string.views),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
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
            com.lechixy.kick.ui.components.ExpandableProfilePhoto(
                url = channel.user?.profilePic,
                username = channel.user?.username,
                onExpandedChange = onProfilePhotoExpandedChange,
                modifier = Modifier.size(56.dp)
            )

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp), itemVerticalAlignment = Alignment.CenterVertically) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(text = channel.user?.username ?: channel.slug, style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                        if (channel.verified) VerifiedBadge(modifier = Modifier.size(16.dp), colors = listOf(
                            MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.onPrimaryContainer))
                    }
                    lastHourGiftedSubsRank?.let { com.lechixy.kick.ui.components.LastHourGiftedSubsChip(it) }
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

                if (!isLive && recording == null) {
                    Text(
                        text = lastLiveText,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            if (recordingAvailable) {
                Button(
                    onClick = onPlayLive,
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = stringResource(R.string.play_live),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Button(
                onClick = onFollowClick,
                enabled = followEnabled,
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
                    text = followLabel,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun ChannelTabsRow(
    tabs: List<ChannelTab>,
    selectedTab: ChannelTab,
    onTabSelected: (ChannelTab) -> Unit
) {
    val selectedIndex = tabs.indexOf(selectedTab).coerceAtLeast(0)
    PrimaryTabRow(
        selectedTabIndex = selectedIndex,
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.primary
    ) {
        tabs.forEach { tab ->
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
    clips: List<ChannelClip>,
    clipsError: String?,
    isClipsLoading: Boolean,
    onRetryClips: () -> Unit,
    onPlayRecording: (RecordedPlayback) -> Unit,
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
                                VideoCard(video = video, onClick = {
                                    video.toRecording()?.let(onPlayRecording)
                                })
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
                            VideoCard(video = video, onClick = {
                                    video.toRecording()?.let(onPlayRecording)
                                })
                        }
                    }
                }
            }
        }

        ChannelTab.CLIPS -> {
            if (isClipsLoading) {
                Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            } else if (clipsError != null) {
                Column(Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(clipsError, color = MaterialTheme.colorScheme.outline)
                    TextButton(onClick = onRetryClips) { Text("Tekrar dene") }
                }
            } else if (clips.isEmpty()) {
                Text("Klip bulunamadı.", modifier = Modifier.padding(32.dp), color = MaterialTheme.colorScheme.outline)
            } else {
                FlowRow(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp), maxItemsInEachRow = 3) {
                    clips.forEach { clip ->
                        Box(Modifier.weight(1f, fill = false).widthIn(min = 280.dp)) {
                            VideoCard(video = ChannelVideo(
                                id = 0, sessionTitle = clip.title, duration = (clip.duration * 1000).toLong(),
                                views = clip.views, thumbnail = VideoThumbnail(src = clip.thumbnailUrl)
                            ), progressId = clip.id, onClick = { onPlayRecording(clip.toRecording()) })
                        }
                    }
                }
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

