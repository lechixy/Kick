package com.lechixy.kick.ui.components

import android.Manifest
import android.annotation.SuppressLint
import android.app.PictureInPictureParams
import android.content.ComponentName
import android.content.pm.PackageManager
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Rational
import android.view.ViewGroup
import android.view.WindowManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.media3.ui.PlayerView
import com.lechixy.kick.PlayerMode
import com.lechixy.kick.R
import com.lechixy.kick.data.model.ChannelDetail
import com.lechixy.kick.data.model.getBannerUrl
import com.lechixy.kick.data.settings.LocalSettingsRepository
import com.lechixy.kick.data.settings.SettingsKeys
import com.lechixy.kick.service.EXTRA_LOW_LATENCY
import com.lechixy.kick.service.PlaybackService
import com.lechixy.kick.service.buildPlaybackMediaItem
import com.lechixy.kick.ui.screens.channel.findActivity
import com.lechixy.kick.ui.settings.SettingCard
import com.lechixy.kick.ui.settings.SettingSwitch
import com.lechixy.kick.util.CastUtils
import com.lechixy.kick.util.FormatUtils
import com.lechixy.kick.util.FormatUtils.formatDuration
import com.lechixy.kick.util.FormatUtils.formatLiveOffset
import com.lechixy.kick.util.FormatUtils.parseUtcStartTimeToMillis
import com.lechixy.kick.util.NotificationUtils
import com.lechixy.kick.util.PlayerUtils
import com.lechixy.kick.util.VideoQualityOption
import androidx.compose.animation.core.tween
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import com.lechixy.kick.util.buildOptimizedImageRequest
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

@SuppressLint("UseKtx")
@androidx.annotation.OptIn(UnstableApi::class)
@kotlin.OptIn(FlowPreview::class, ExperimentalMaterial3Api::class)
@Composable
fun KickPlayerView(
    playbackUrl: String,
    previewThumbnailUrl: String? = null,
    channelName: String = "",
    streamTitle: String = "",
    channel: ChannelDetail? = null,
    isFullScreen: Boolean = false,
    isTheaterMode: Boolean = false,
    isChatVisible: Boolean = true,
    onToggleChat: () -> Unit = {},
    isInPipMode: Boolean = false,
    isMini: Boolean = false,
    pendingAutoFullscreen: Boolean = false,
    onConsumeAutoFullscreen: (Boolean) -> Unit = {},
    onFullScreenChange: (Boolean) -> Unit = {},
    onToggleTheater: () -> Unit = {},
    onMinimize: () -> Unit = {},
    onClose: () -> Unit = {},
    onControlsVisibilityChanged: (Boolean) -> Unit = {},
    onPlayerCoordinatesChanged: (Rect) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }

    val player by rememberMediaController()

    var hasRenderedFirstFrame by remember(playbackUrl) { mutableStateOf(false) }
    var isBuffering by remember { mutableStateOf(true) }
    var isPlayerReady by remember { mutableStateOf(false) }
    var loadedMediaId by remember { mutableStateOf<String?>(null) }
    var isPlaying by remember { mutableStateOf(true) }
    var isMuted by remember { mutableStateOf(false) }
    var volume by remember { mutableFloatStateOf(1f) }
    var playbackError by remember { mutableStateOf<String?>(null) }
    var showControls by remember { mutableStateOf(false) }
    var isAdjustingVolume by remember { mutableStateOf(false) }

    var showSettingsDialog by remember { mutableStateOf(false) }
    var settingsPage by remember { mutableStateOf(PlayerSettingsPage.MAIN) }
    var sleepTimerMinutes by remember { mutableIntStateOf(0) }
    var remainingTimerSeconds by remember { mutableIntStateOf(0) }

    val settingsRepository = LocalSettingsRepository.current
    val coroutineScope = rememberCoroutineScope()
    val preferredQuality by settingsRepository
        .get(SettingsKeys.VIDEO_QUALITY)
        .collectAsStateWithLifecycle(initialValue = SettingsKeys.VIDEO_QUALITY.defaultValue)

    var selectedQualityHeight by remember { mutableIntStateOf(preferredQuality) }
    val availableQualities = remember { mutableStateListOf<VideoQualityOption>() }
    var currentVideoHeight by remember { mutableIntStateOf(0) }

    LaunchedEffect(preferredQuality) {
        selectedQualityHeight = preferredQuality
    }

    LaunchedEffect(player, selectedQualityHeight, availableQualities.toList()) {
        applyQualityToPlayer(player, selectedQualityHeight, availableQualities)
    }

    val latestIsFullScreen by rememberUpdatedState(isFullScreen)
    val latestOnFullScreenChange by rememberUpdatedState(onFullScreenChange)
    val toggleFullScreen: () -> Unit = remember {
        { latestOnFullScreenChange(!latestIsFullScreen) }
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted && sleepTimerMinutes > 0) {
            NotificationUtils.updateTimerNotification(context, sleepTimerMinutes * 60)
        }
    }

    /**
     * Sleep timer
     */
    LaunchedEffect(sleepTimerMinutes) {
        if (sleepTimerMinutes > 0) {
            remainingTimerSeconds = sleepTimerMinutes * 60
            NotificationUtils.updateTimerNotification(context, remainingTimerSeconds)

            while (remainingTimerSeconds > 0 && isPlaying) {
                delay(1000.milliseconds)
                remainingTimerSeconds--
                if (remainingTimerSeconds > 0 && remainingTimerSeconds % 60 == 0) {
                    NotificationUtils.updateTimerNotification(context, remainingTimerSeconds)
                }
            }

            if (remainingTimerSeconds <= 0) {
                player?.pause()
                sleepTimerMinutes = 0
                NotificationUtils.showTimerEndedNotification(context)
            }
        } else {
            NotificationUtils.cancelTimerNotification(context)
        }
    }

    var streamDuration by remember { mutableStateOf("00:00:00") }
    val startTimeString = channel?.livestream?.startTime // "2026-10-09 16:29:59"

    LaunchedEffect(startTimeString) {
        val startMillis = parseUtcStartTimeToMillis(startTimeString)

        if (startMillis <= 0L) {
            streamDuration = "00:00:00"
            return@LaunchedEffect
        }

        while (isActive) {
            val currentMillis = System.currentTimeMillis()
            val elapsedMillis = (currentMillis - startMillis).coerceAtLeast(0L)

            streamDuration = formatDuration(elapsedMillis)

            delay(1000.milliseconds)
        }
    }

    val pipEnabled by settingsRepository
        .get(SettingsKeys.PIP_ENABLED)
        .collectAsStateWithLifecycle(initialValue = SettingsKeys.PIP_ENABLED.defaultValue)
    val lowLatencyEnabled by settingsRepository
        .get(SettingsKeys.LOW_LATENCY)
        .collectAsStateWithLifecycle(initialValue = SettingsKeys.LOW_LATENCY.defaultValue)
    val autoFullscreenLive by settingsRepository
        .get(SettingsKeys.AUTO_FULLSCREEN_LIVE)
        .collectAsStateWithLifecycle(initialValue = SettingsKeys.AUTO_FULLSCREEN_LIVE.defaultValue)
    var showCastDialog by remember { mutableStateOf(false) }
    var playerBounds by remember { mutableStateOf<Rect?>(null) }

    LaunchedEffect(isPlaying, pipEnabled, activity) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || activity == null) return@LaunchedEffect
        snapshotFlow { playerBounds }
            .debounce(150.milliseconds)
            .collect { bounds ->
                activity.setPictureInPictureParams(
                    buildPipParams(autoEnter = isPlaying && pipEnabled, sourceRect = bounds)
                )
            }
    }

    DisposableEffect(activity) {
        onDispose {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                activity?.setPictureInPictureParams(
                    buildPipParams(
                        autoEnter = false,
                        sourceRect = null
                    )
                )
            }
        }
    }

    DisposableEffect(isPlaying) {
        if (isPlaying) activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }

    LaunchedEffect(player, isMuted, volume) {
        player?.volume = if (isMuted) 0f else volume
    }

    LaunchedEffect(showControls, isAdjustingVolume) {
        if (showControls && !isAdjustingVolume) {
            delay(3500.milliseconds)
            showControls = false
        }
    }

    LaunchedEffect(showControls) {
        onControlsVisibilityChanged(showControls)
    }

    LaunchedEffect(isMini) { if (isMini) showControls = false }

    var liveOffsetMs by remember { mutableLongStateOf(0L) }
    var isAtLiveEdge by remember { mutableStateOf(true) }
    val isLiveStream = channel?.livestream != null || player?.isCurrentMediaItemLive == true

    LaunchedEffect(player, isLiveStream, lowLatencyEnabled) {
        val p = player ?: return@LaunchedEffect
        if (!isLiveStream) {
            isAtLiveEdge = true
            liveOffsetMs = 0L
            return@LaunchedEffect
        }

        while (isActive) {
            val currentOffset = p.distanceToLiveEdgeMs()

            liveOffsetMs = currentOffset
            val fallBehindThreshold = if (lowLatencyEnabled) 10500L else 14500L
            val catchUpThreshold = if (lowLatencyEnabled) 9000L else 12500L
            isAtLiveEdge = if (isAtLiveEdge) {
                currentOffset <= fallBehindThreshold
            } else {
                currentOffset <= catchUpThreshold
            }

            delay(250.milliseconds)
        }
    }

    /**
     * Player listener
     */
    DisposableEffect(player) {
        val p = player
        if (p == null) {
            onDispose { }
        } else {
            fun refreshQualities(tracks: Tracks) {
                availableQualities.clear()
                availableQualities.addAll(tracks.toQualityOptions())
            }

            var previousPlayWhenReady = p.playWhenReady
            val listener = object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    isBuffering = playbackState == Player.STATE_BUFFERING
                    isPlayerReady = playbackState == Player.STATE_READY
                    if (playbackState == Player.STATE_READY && p.playWhenReady) {
                        hasRenderedFirstFrame = true
                    }
                    loadedMediaId = p.currentMediaItem?.mediaId
                }

                override fun onRenderedFirstFrame() {
                    hasRenderedFirstFrame = true
                    isPlayerReady = true
                    loadedMediaId = p.currentMediaItem?.mediaId
                }

                override fun onIsPlayingChanged(playing: Boolean) {
                    isPlaying = playing
                    if (playing) {
                        hasRenderedFirstFrame = true
                    }
                }

                override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                    if (playWhenReady && !previousPlayWhenReady) {
                        val live = channel?.livestream != null || p.isCurrentMediaItemLive
                        if (live) {
                            if (p.distanceToLiveEdgeMs() > 15000L) {
                                p.seekToDefaultPosition()
                            }
                        }
                    }
                    previousPlayWhenReady = playWhenReady
                }

                override fun onVideoSizeChanged(videoSize: VideoSize) {
                    if (videoSize.height > 0) currentVideoHeight = videoSize.height
                }

                override fun onTracksChanged(tracks: Tracks) {
                    refreshQualities(tracks)
                }

                override fun onPlayerError(error: PlaybackException) {
                    playbackError = error.message ?: "Oynatma hatası"
                    isBuffering = false
                    isPlayerReady = false
                }
            }

            isPlaying = p.isPlaying
            if (p.isPlaying || p.playbackState == Player.STATE_READY) {
                hasRenderedFirstFrame = true
            }
            isBuffering = p.playbackState == Player.STATE_BUFFERING
            isPlayerReady = p.playbackState == Player.STATE_READY
            loadedMediaId = p.currentMediaItem?.mediaId
            refreshQualities(p.currentTracks)

            p.addListener(listener)
            onDispose { p.removeListener(listener) }
        }
    }

    val stableId = channel?.slug ?: playbackUrl
    LaunchedEffect(player, stableId, lowLatencyEnabled) {
        val p = player ?: return@LaunchedEffect
        if (playbackUrl.isBlank()) return@LaunchedEffect

        playbackError = null

        val currentItem = p.currentMediaItem
        val alreadyLoaded = currentItem?.mediaId == stableId &&
                currentItem.mediaMetadata.extras?.getBoolean(
                    EXTRA_LOW_LATENCY,
                    lowLatencyEnabled
                ) == lowLatencyEnabled
        if (alreadyLoaded) {
            loadedMediaId = stableId
            isPlayerReady = p.playbackState == Player.STATE_READY
            if (p.playbackState == Player.STATE_IDLE) p.prepare()
            return@LaunchedEffect
        }

        isBuffering = true
        isPlayerReady = false
        loadedMediaId = stableId
        val pic: String = (channel?.getBannerUrl() ?: channel?.user?.profilePic
        ?: "https://placehold.co/600x400/EEE/31343C?font=open-sans&text=No%20banner") as String

        val isLive = channel?.livestream != null
        val mediaItem = buildPlaybackMediaItem(
            playbackUrl = playbackUrl,
            mediaId = stableId,
            isLive = isLive,
            title = streamTitle.ifBlank { "Canlı Yayın" },
            channelName = channelName.ifBlank { "Kick" },
            viewersText = channel?.livestream?.viewerCount
                ?.let {
                    "${FormatUtils.formatViewersCount(it)} ${
                        context.applicationContext.getString(
                            R.string.viewers
                        )
                    }"
                },
            artwork = pic.toUri(),
            startTimeMs = parseUtcStartTimeToMillis(channel?.livestream?.startTime),
            isLowLatency = lowLatencyEnabled
        )

        p.setMediaItem(mediaItem)
        p.prepare()
        p.play()
    }

    LaunchedEffect(
        pendingAutoFullscreen,
        autoFullscreenLive,
        isPlayerReady,
        loadedMediaId,
        stableId,
        channel?.livestream,
        isMini,
        isInPipMode
    ) {
        if (
            pendingAutoFullscreen &&
            channel?.livestream != null &&
            isPlayerReady &&
            loadedMediaId == stableId &&
            !isMini &&
            !isInPipMode
        ) {
            onConsumeAutoFullscreen(autoFullscreenLive)
        }
    }

    Box(
        modifier = modifier
            .background(Color.Black)
            .onGloballyPositioned { coordinates ->
                val b = coordinates.boundsInWindow()
                val rect = Rect(b.left.toInt(), b.top.toInt(), b.right.toInt(), b.bottom.toInt())
                if (playerBounds != rect) {
                    playerBounds = rect
                    onPlayerCoordinatesChanged(rect)
                }
            }
            .pointerInput(isInPipMode, isMini) {
                if (!isInPipMode && !isMini) {
                    detectTapGestures(
                        onDoubleTap = { toggleFullScreen() },
                        onTap = { showControls = !showControls }
                    )
                }
            },
        contentAlignment = Alignment.Center
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = false
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }
            },
            update = { playerView ->
                playerView.player = player
                playerView.keepScreenOn = isPlaying
            },
            onRelease = { it.player = null },
            modifier = Modifier.fillMaxSize()
        )

        val overlayThumbnail = remember(previewThumbnailUrl, channel) {
            previewThumbnailUrl
                ?: channel?.livestream?.thumbnail?.url
                ?: channel?.getBannerUrl()
        }

        AnimatedVisibility(
            visible = !hasRenderedFirstFrame && !overlayThumbnail.isNullOrEmpty(),
            enter = fadeIn(),
            exit = fadeOut(tween(300))
        ) {
            AsyncImage(
                model = remember(overlayThumbnail) {
                    buildOptimizedImageRequest(
                        context = context,
                        data = overlayThumbnail,
                        targetWidthDp = 640,
                        targetHeightDp = 360
                    )
                },
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }

        if (isBuffering && playbackError == null) {
            CircularProgressIndicator(
                modifier = Modifier.size(if (isMini) 24.dp else 44.dp)
            )
        }

        val qualityLabel = remember(selectedQualityHeight, currentVideoHeight) {
            when {
                selectedQualityHeight > 0 -> "${selectedQualityHeight}p"
                currentVideoHeight > 0 -> "${currentVideoHeight}p (Auto)"
                else -> "Auto"
            }
        }
        val viewersText = remember(channel?.livestream?.viewerCount) {
            "${FormatUtils.formatViewersCount(channel?.livestream?.viewerCount)} ${
                context.applicationContext.getString(
                    R.string.viewers
                )
            }"
        }

        val handleTogglePlay = {
            val p = player
            if (p != null) {
                if (isPlaying) {
                    p.pause()
                } else {
                    val live = channel?.livestream != null || p.isCurrentMediaItemLive
                    if (live) {
                        p.seekToDefaultPosition()
                    }
                    p.play()
                }
            }
        }

        val handleGoToLive = {
            val p = player
            if (p != null) {
                isAtLiveEdge = true
                p.seekToDefaultPosition()
                p.play()
            }
        }

        AnimatedVisibility(
            visible = showControls && playbackError == null && !isInPipMode && !isMini,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            PlayerControlsOverlay(
                isFullScreen = isFullScreen,
                isTheaterMode = isTheaterMode,
                isChatVisible = isChatVisible,
                onToggleChat = onToggleChat,
                isPlaying = isPlaying,
                isMuted = isMuted,
                volume = volume,
                isSettingsDialogVisible = showSettingsDialog,
                isCastDialogVisible = showCastDialog,
                qualityLabel = qualityLabel,
                sleepRemainingSeconds = if (sleepTimerMinutes > 0) remainingTimerSeconds else 0,
                streamTitle = streamTitle,
                viewersText = viewersText,
                streamDuration = streamDuration,
                isLive = isLiveStream,
                isAtLiveEdge = isAtLiveEdge,
                behindLiveText = formatLiveOffset(liveOffsetMs),
                onGoToLive = handleGoToLive,
                onTogglePlay = handleTogglePlay,
                onToggleMute = { isMuted = !isMuted },
                onVolumeChange = { newVol ->
                    isAdjustingVolume = true
                    volume = newVol
                    isMuted = false
                },
                onVolumeChangeFinished = {
                    isAdjustingVolume = false
                },
                onOpenSettings = { showSettingsDialog = true },
                onOpenCastDialog = { showCastDialog = true },
                onToggleTheater = onToggleTheater,
                onToggleFullScreen = toggleFullScreen,
                // Tam ekrandayken önce tam ekrandan çık, normalde ise mini player'a geç.
                onMinimize = { if (isFullScreen) onFullScreenChange(false) else onMinimize() }
            )
        }

        if (isMini && !isInPipMode) {
            MiniControlsOverlay(
                isPlaying = isPlaying,
                onTogglePlay = handleTogglePlay,
                onClose = onClose
            )
        }

        if (showSettingsDialog) {
            val closeSettings: () -> Unit = {
                showSettingsDialog = false
                settingsPage = PlayerSettingsPage.MAIN
            }

            val selectSleepTimer: (Int) -> Unit = { mins ->
                sleepTimerMinutes = mins
                closeSettings()

                if (mins == 0) {
                    remainingTimerSeconds = 0
                    NotificationUtils.cancelTimerNotification(context)
                } else {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        val hasPermission =
                            ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.POST_NOTIFICATIONS
                            ) == PackageManager.PERMISSION_GRANTED

                        if (hasPermission) {
                            NotificationUtils.updateTimerNotification(context, mins * 60)
                        } else {
                            notificationPermissionLauncher.launch(
                                Manifest.permission.POST_NOTIFICATIONS
                            )
                        }
                    } else {
                        NotificationUtils.updateTimerNotification(context, mins * 60)
                    }
                }
            }

            val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            ModalBottomSheet(
                onDismissRequest = closeSettings,
                sheetState = sheetState,
                sheetMaxWidth = 640.dp,
                dragHandle = { BottomSheetDefaults.DragHandle() },
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                ) {
//                    // Header
//                    Row(
//                        modifier = Modifier
//                            .fillMaxWidth()
//                            .padding(horizontal = 16.dp, vertical = 4.dp),
//                        verticalAlignment = Alignment.CenterVertically
//                    ) {
//                        AnimatedVisibility(
//                            visible = settingsPage != PlayerSettingsPage.MAIN
//                        ) {
//                            IconButton(
//                                onClick = { settingsPage = PlayerSettingsPage.MAIN },
//                                modifier = Modifier.size(36.dp)
//                            ) {
//                                Icon(
//                                    painter = painterResource(id = R.drawable.arrow_back_24dp_e3e3e3_fill0_wght400_grad0_opsz24),
//                                    contentDescription = "Back"
//                                )
//                            }
//                            Spacer(modifier = Modifier.width(16.dp))
//                        }
//                        Text(
//                            text = when (settingsPage) {
//                                PlayerSettingsPage.MAIN -> stringResource(R.string.settings)
//                                PlayerSettingsPage.QUALITY -> "Quality"
//                                PlayerSettingsPage.SLEEP_TIMER -> "Sleep Timer"
//                                PlayerSettingsPage.MORE -> "More"
//                            },
//                            style = MaterialTheme.typography.titleLarge,
//                            fontWeight = FontWeight.Bold,
//                            modifier = Modifier.weight(1f)
//                        )
//                        IconButton(
//                            onClick = closeSettings,
//                            modifier = Modifier.size(36.dp)
//                        ) {
//                            Icon(
//                                painter = painterResource(id = R.drawable.close_24dp_e3e3e3_fill0_wght400_grad0_opsz24),
//                                contentDescription = "Close"
//                            )
//                        }
//                    }

//                    HorizontalDivider(modifier = Modifier.padding(top = 4.dp, bottom = 8.dp))

                    AnimatedVisibility(
                        visible = settingsPage != PlayerSettingsPage.MAIN
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = {
                                    settingsPage = PlayerSettingsPage.MAIN
                                },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.arrow_back_24dp_e3e3e3_fill0_wght400_grad0_opsz24),
                                    contentDescription = "Back"
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = when (settingsPage) {
                                    PlayerSettingsPage.MAIN -> stringResource(R.string.settings)
                                    PlayerSettingsPage.QUALITY -> "Quality"
                                    PlayerSettingsPage.SLEEP_TIMER -> "Sleep Timer"
                                    PlayerSettingsPage.MORE -> "More"
                                },
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    AnimatedContent(
                        targetState = settingsPage,
                        transitionSpec = {
                            if (initialState == PlayerSettingsPage.MAIN) {
                                (slideInHorizontally { it } + fadeIn()).togetherWith(
                                    slideOutHorizontally { -it } + fadeOut())
                            } else {
                                (slideInHorizontally { -it } + fadeIn()).togetherWith(
                                    slideOutHorizontally { it } + fadeOut())
                            }
                        },
                        label = "PlayerSettingsPageTransition"
                    ) { targetPage ->
                        when (targetPage) {
                            PlayerSettingsPage.MAIN -> {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp)
                                ) {
                                    PlayerSettingsCategoryRow(
                                        icon = R.drawable.hd_24dp_e3e3e3_fill0_wght400_grad0_opsz24,
                                        title = "Quality",
                                        value = qualityLabel,
                                        onClick = { settingsPage = PlayerSettingsPage.QUALITY }
                                    )
                                    PlayerSettingsCategoryRow(
                                        icon = R.drawable.timer_24dp_e3e3e3_fill0_wght400_grad0_opsz24,
                                        title = "Sleep Timer",
                                        value = if (sleepTimerMinutes > 0) "${(remainingTimerSeconds + 59) / 60}m" else "Off",
                                        onClick = { settingsPage = PlayerSettingsPage.SLEEP_TIMER }
                                    )
                                    PlayerSettingsCategoryRow(
                                        icon = R.drawable.tune_24dp_e3e3e3_fill0_wght400_grad0_opsz24,
                                        title = "More",
                                        value = null,
                                        onClick = { settingsPage = PlayerSettingsPage.MORE }
                                    )
                                }
                            }

                            PlayerSettingsPage.QUALITY -> {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp)
                                        .verticalScroll(rememberScrollState()),
                                    verticalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    PlayerSettingsOptionRow(
                                        title = "Auto",
                                        subtitle = "According to network speed",
                                        selected = selectedQualityHeight == -1,
                                        onClick = {
                                            selectedQualityHeight = -1
                                            coroutineScope.launch {
                                                settingsRepository.set(SettingsKeys.VIDEO_QUALITY, -1)
                                            }
                                            applyQualityToPlayer(player, -1, availableQualities)
                                            closeSettings()
                                        }
                                    )

                                    availableQualities.forEach { quality ->
                                        PlayerSettingsOptionRow(
                                            title = quality.label,
                                            subtitle = quality.dataPerHourText,
                                            selected = selectedQualityHeight == quality.height,
                                            onClick = {
                                                selectedQualityHeight = quality.height
                                                coroutineScope.launch {
                                                    settingsRepository.set(
                                                        SettingsKeys.VIDEO_QUALITY,
                                                        quality.height
                                                    )
                                                }
                                                applyQualityToPlayer(
                                                    player,
                                                    quality.height,
                                                    availableQualities
                                                )
                                                closeSettings()
                                            }
                                        )
                                    }
                                }
                            }

                            PlayerSettingsPage.SLEEP_TIMER -> {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp)
                                        .verticalScroll(rememberScrollState()),
                                    verticalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    listOf(0, 15, 30, 45, 60).forEach { mins ->
                                        PlayerSettingsOptionRow(
                                            title = if (mins == 0) "Off" else "$mins minutes",
                                            subtitle = if (mins != 0 && sleepTimerMinutes == mins)
                                                "${(remainingTimerSeconds + 59) / 60}m left" else null,
                                            selected = sleepTimerMinutes == mins,
                                            onClick = { selectSleepTimer(mins) }
                                        )
                                    }
                                }
                            }

                            PlayerSettingsPage.MORE -> {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp)
                                        .verticalScroll(rememberScrollState()),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    SettingSwitch(
                                        key = SettingsKeys.PIP_ENABLED,
                                        title = "Resim İçinde Resim (PiP)",
                                        description = "Uygulamadan çıkıldığında yayını küçük ekranda oynat"
                                    )

                                    SettingSwitch(
                                        key = SettingsKeys.LOW_LATENCY,
                                        title = "Düşük Gecikme Modu (Low Latency)",
                                        description = "Web sitesi ile anlık canlı yayın (~2s gecikme)"
                                    )

                                    SettingSwitch(
                                        key = SettingsKeys.AUTO_FULLSCREEN_LIVE,
                                        title = "Otomatik Tam Ekran (Auto Fullscreen)",
                                        description = "Kanala girildiğinde yayın canlıysa ve oynatıcı yüklendiyse hemen tam ekrana geç"
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars))
                }
            }
        }

        if (showCastDialog) {
            AlertDialog(
                onDismissRequest = { showCastDialog = false },
                modifier = Modifier.fillMaxWidth(0.95f),
                title = {
                    Text("Cast")
                },
                text = {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        /*
                         * Google Cast / Chromecast
                         */
                        item {
                            SettingCard(
                                title = "Google Cast / Chromecast",
                                description = "Android TV, Google TV veya Chromecast cihazlarına doğrudan kablosuz yansıtma yapmak için kullanılır.",
                                icon = R.drawable.cast_24dp_e3e3e3_fill0_wght400_grad0_opsz24,
                                onClick = {
                                    showCastDialog = false
                                    runCatching {
                                        val castIntent =
                                            android.content.Intent("android.settings.CAST_SETTINGS")
                                                .apply {
                                                    addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                                }
                                        context.startActivity(castIntent)
                                    }
                                }
                            )
                        }
                        /**
                         * Web Video Caster
                         */
                        item {
                            SettingCard(
                                title = "Web Video Caster",
                                description = "Samsung (Tizen), LG (webOS), Roku, Fire TV, Apple TV ve DLNA destekli televizyonlara yayın göndermek için en mükemmel yöntemdir.",
                                icon = R.drawable.webvideocasterlogo,
                                onClick = {
                                    showCastDialog = false
                                    val poster =
                                        channel?.getBannerUrl() ?: channel?.user?.profilePic
                                    CastUtils.launchWebVideoCaster(
                                        context = context,
                                        playbackUrl = playbackUrl,
                                        streamTitle = streamTitle,
                                        channelName = channelName,
                                        posterUrl = poster
                                    )
                                }
                            )
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showCastDialog = false }) {
                        Text("Kapat")
                    }
                }
            )
        }
    }
}

@Composable
private fun rememberMediaController(): State<Player?> {
    val context = LocalContext.current
    val playerState = remember { mutableStateOf<Player?>(null) }

    DisposableEffect(context) {
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        future.addListener(
            { playerState.value = runCatching { future.get() }.getOrNull() },
            ContextCompat.getMainExecutor(context)
        )
        onDispose {
            val p = playerState.value
            val changing = context.findActivity()?.isChangingConfigurations == true
            if (!changing) {
                p?.pause()
                p?.stop()
                p?.clearMediaItems()      // session/bildirim tamamen temizlensin
            }
            // Komutun servise ulaşması için release'i biraz geciktir
            Handler(Looper.getMainLooper()).postDelayed(
                { MediaController.releaseFuture(future) },
                if (changing) 0L else 500L
            )
            playerState.value = null
        }
    }
    return playerState
}

@androidx.annotation.OptIn(UnstableApi::class)
private fun applyQualityToPlayer(
    player: Player?,
    selectedHeight: Int,
    availableQualities: List<VideoQualityOption>
) {
    val p = player ?: return
    if (selectedHeight == -1) {
        p.trackSelectionParameters = p.trackSelectionParameters
            .buildUpon()
            .clearOverridesOfType(C.TRACK_TYPE_VIDEO)
            .setMaxVideoSize(Int.MAX_VALUE, Int.MAX_VALUE)
            .setMinVideoSize(0, 0)
            .build()
    } else {
        val resolvedHeight = if (availableQualities.isNotEmpty()) {
            availableQualities.firstOrNull { it.height == selectedHeight }?.height
                ?: availableQualities.firstOrNull { it.height <= selectedHeight }?.height
                ?: availableQualities.lastOrNull()?.height
                ?: selectedHeight
        } else {
            selectedHeight
        }

        val targetWidth = resolvedHeight * 16 / 9
        p.trackSelectionParameters = p.trackSelectionParameters
            .buildUpon()
            .clearOverridesOfType(C.TRACK_TYPE_VIDEO)
            .setMaxVideoSize(targetWidth, resolvedHeight)
            .setMinVideoSize(targetWidth, resolvedHeight)
            .build()
    }
}

@RequiresApi(Build.VERSION_CODES.S)
private fun buildPipParams(autoEnter: Boolean, sourceRect: Rect?): PictureInPictureParams =
    PictureInPictureParams.Builder()
        .setAspectRatio(Rational(16, 9))
        .setAutoEnterEnabled(autoEnter)
        .setSeamlessResizeEnabled(false) // video içerik için daha doğru geçiş
        .apply { sourceRect?.takeIf { !it.isEmpty }?.let { setSourceRectHint(it) } }
        .build()

@androidx.annotation.OptIn(UnstableApi::class)
private fun Tracks.toQualityOptions(): List<VideoQualityOption> =
    groups
        .asSequence()
        .filter { it.type == C.TRACK_TYPE_VIDEO }
        .flatMap { group -> (0 until group.length).map { group.getTrackFormat(it) } }
        .filter { it.height > 0 }
        .map { format ->
            VideoQualityOption(
                label = "${format.height}p",
                height = format.height,
                bitrate = format.bitrate,
                dataPerHourText = PlayerUtils.estimateDataUsagePerHour(
                    format.bitrate,
                    format.height
                )
            )
        }
        .distinctBy { it.height }
        .sortedByDescending { it.height }
        .toList()

/**
 * Player ayarları dialogundaki sayfalar (YouTube tarzı kategoriler).
 */
private enum class PlayerSettingsPage { MAIN, QUALITY, SLEEP_TIMER, MORE }

@Composable
private fun PlayerSettingsCategoryRow(
    icon: Int,
    title: String,
    value: String?,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = painterResource(id = icon),
            contentDescription = null,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f)
        )
        if (value != null) {
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.outline
            )
            Spacer(modifier = Modifier.width(8.dp))
        }
        Icon(
            painter = painterResource(id = R.drawable.arrow_forward_ios_24dp_e3e3e3_fill0_wght400_grad0_opsz24),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.outline,
            modifier = Modifier.size(14.dp)
        )
    }
}

@Composable
private fun PlayerSettingsOptionRow(
    title: String,
    selected: Boolean,
    subtitle: String? = null,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Spacer(modifier = Modifier.width(4.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        }
    }
}

@Composable
internal fun PlayerControlsOverlay(
    isFullScreen: Boolean,
    isTheaterMode: Boolean = false,
    isChatVisible: Boolean = true,
    onToggleChat: () -> Unit = {},
    isPlaying: Boolean,
    isMuted: Boolean,
    volume: Float,
    isSettingsDialogVisible: Boolean,
    isCastDialogVisible: Boolean,
    qualityLabel: String,
    sleepRemainingSeconds: Int,
    streamTitle: String,
    viewersText: String,
    streamDuration: String,
    isLive: Boolean = false,
    isAtLiveEdge: Boolean = true,
    behindLiveText: String = "",
    onGoToLive: () -> Unit = {},
    onTogglePlay: () -> Unit,
    onToggleMute: () -> Unit,
    onVolumeChange: (Float) -> Unit,
    onVolumeChangeFinished: () -> Unit = {},
    onOpenSettings: () -> Unit,
    onOpenCastDialog: () -> Unit = {},
    onToggleTheater: () -> Unit = {},
    onToggleFullScreen: () -> Unit,
    onMinimize: () -> Unit,
    modifier: Modifier = Modifier
) {
    val labelStyle = MaterialTheme.typography.labelSmall

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.5f))
            .let {
                if (isFullScreen) it.windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal))
                else it
            }
    ) {
        /**
         * Top
         */
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .padding(start = 4.dp, top = 8.dp, end = 12.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            /**
             * Top Start/Left
             */
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                IconButton(
                    onClick = onMinimize,
                    modifier = modifier
                ) {
                    Icon(
                        painter = painterResource(R.drawable.keyboard_arrow_down_24dp_e3e3e3_fill0_wght400_grad0_opsz24),
                        contentDescription = "Minimize",
                        tint = Color.White,
                    )
                }
                Surface(color = Color.Black.copy(alpha = 0.6f), shape = RoundedCornerShape(4.dp)) {
                    Text(
                        text = qualityLabel,
                        color = Color.White,
                        style = labelStyle,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }

                if (sleepRemainingSeconds > 0) {
                    Surface(
                        color = Color.Black.copy(alpha = 0.75f),
                        shape = RoundedCornerShape(6.dp),
                        border = BorderStroke(
                            1.dp,
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.timer_24dp_e3e3e3_fill0_wght400_grad0_opsz24),
                                contentDescription = "Timer",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = "Sleep: ${(sleepRemainingSeconds + 59) / 60}m",
                                color = Color.White,
                                style = labelStyle,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
            /**
             * Top End/Right
             */
            val configuration = LocalConfiguration.current
            val isWide = configuration.screenWidthDp >= WIDE_BREAKPOINT_DP
            val showChatButton = isFullScreen || isTheaterMode || isWide

            Column(
                horizontalAlignment = Alignment.End,
//                verticalArrangement = Arrangement.spacedBy(0.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    IconButton(onClick = onOpenCastDialog) {
                        Icon(
                            painter = painterResource(id = if (isCastDialogVisible) R.drawable.cast_24dp_e3e3e3_fill1_wght400_grad0_opsz24 else R.drawable.cast_24dp_e3e3e3_fill0_wght400_grad0_opsz24),
                            contentDescription = "Cast",
                            tint = Color.White
                        )
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(
                            painter = painterResource(id = if (isSettingsDialogVisible) R.drawable.settings_24dp_e3e3e3_fill1_wght400_grad0_opsz24 else R.drawable.settings_24dp_e3e3e3_fill0_wght400_grad0_opsz24),
                            contentDescription = "Settings",
                            tint = Color.White
                        )
                    }
                }

                if (showChatButton) {
                    Surface(
                        onClick = onToggleChat,
                        shape = CircleShape,
                        color = Color.Black.copy(alpha = 0.65f),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.25f)),
                        shadowElevation = 4.dp
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.chat_24dp_e3e3e3_fill0_wght400_grad0_opsz24),
                                contentDescription = if (isChatVisible) "Sohbeti Gizle" else "Sohbeti Göster",
                                tint = if (isChatVisible) MaterialTheme.colorScheme.primary else Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = stringResource(R.string.chat),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White
                            )
                        }
                    }
                }
            }
        }

        /**
         * Play Icon / Center
         */
        IconButton(
            onClick = onTogglePlay,
            modifier = Modifier
                .align(Alignment.Center)
                .size(56.dp)
                .background(Color.Black.copy(alpha = 0.6f), CircleShape)
        ) {
            Icon(
                painter = painterResource(
                    id = if (isPlaying) R.drawable.pause_24dp_e3e3e3_fill1_wght400_grad0_opsz24
                    else R.drawable.play_arrow_24dp_e3e3e3_fill1_wght400_grad0_opsz24
                ),
                contentDescription = "Play/Pause",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp)
            )
        }

        /**
         * Bottom
         */
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            /**
             * Bottom Start/Left
             */
            val isCompact = !isFullScreen
            val itemSpacing = if (isCompact) 4.dp else 8.dp
            val sliderWidth = if (isCompact) 56.dp else 110.dp

            Row(
                modifier = Modifier.weight(1f, fill = false),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(itemSpacing)
            ) {
                IconButton(onClick = onToggleMute, modifier = Modifier.size(32.dp)) {
                    Icon(
                        painter = painterResource(
                            id = if (isMuted || volume == 0f) R.drawable.volume_off_24dp_e3e3e3_fill0_wght400_grad0_opsz24
                            else R.drawable.volume_up_24dp_e3e3e3_fill0_wght400_grad0_opsz24
                        ),
                        contentDescription = "Mute",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Slider(
                    value = if (isMuted) 0f else volume,
                    onValueChange = onVolumeChange,
                    onValueChangeFinished = onVolumeChangeFinished,
                    valueRange = 0f..1f,
                    modifier = Modifier.width(sliderWidth)
                )
                Text(
                    text = "${(if (isMuted) 0f else volume * 100).toInt()}%",
                    color = Color.White,
                    style = labelStyle,
                    fontWeight = FontWeight.SemiBold
                )
                Separator()
                Box(
                    modifier = Modifier
                        .size(if (isCompact) 8.dp else 10.dp)
                        .background(color = MaterialTheme.colorScheme.primary, shape = CircleShape)
                )
                Text(
                    text = streamDuration,
                    color = Color.White,
                    style = labelStyle,
                    fontWeight = FontWeight.SemiBold
                )

                if (isLive) {
                    Separator()
                    Surface(
                        onClick = { if (!isAtLiveEdge) onGoToLive() },
                        shape = RoundedCornerShape(4.dp),
                        modifier = Modifier.padding(start = 2.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .background(
                                        color = if (isAtLiveEdge) Color.White else Color.LightGray,
                                        shape = CircleShape
                                    )
                            )
                            Text(
                                text = if (isAtLiveEdge) {
                                    stringResource(R.string.live_badge)
                                } else {
                                    "${stringResource(R.string.live_badge)} $behindLiveText"
                                },
                                color = Color.White,
                                style = labelStyle,
                                fontWeight = FontWeight.Bold
                            )
                            if (!isAtLiveEdge) {
                                Text(
                                    text = "• ${stringResource(R.string.go_to_live)}",
                                    color = MaterialTheme.colorScheme.secondary,
                                    style = labelStyle,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }

            /**
             * Bottom End/Right
             */
            Row(
                modifier = Modifier.wrapContentWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                IconButton(onClick = onToggleTheater) {
                    Icon(
                        painter = painterResource(
                            id = if (isTheaterMode) R.drawable.theaters_24dp_e3e3e3_fill1_wght400_grad0_opsz24
                            else R.drawable.theaters_24dp_e3e3e3_fill0_wght400_grad0_opsz24
                        ),
                        contentDescription = "Theater Mode",
                        tint = if (isTheaterMode) MaterialTheme.colorScheme.primary else Color.White
                    )
                }
                IconButton(onClick = onToggleFullScreen) {
                    Icon(
                        painter = painterResource(
                            id = if (isFullScreen) R.drawable.fullscreen_exit_24dp_e3e3e3_fill0_wght400_grad0_opsz24
                            else R.drawable.fullscreen_24dp_e3e3e3_fill0_wght400_grad0_opsz24
                        ),
                        contentDescription = "Fullscreen",
                        tint = Color.White
                    )
                }
            }
        }
    }
}

/**
 * Miniplayer
 */
@Composable
internal fun MiniControlsOverlay(
    isPlaying: Boolean,
    onTogglePlay: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0f))
    ) {
        Row(
            Modifier
                .align(Alignment.TopEnd)
                .padding(4.dp)
        ) {
            PlayerChromeButton(
                painter = painterResource(R.drawable.close_24dp_e3e3e3_fill0_wght400_grad0_opsz24),
                contentDescription = "Close",
                size = 30.dp,
                onClick = onClose
            )
        }
        Row(
            Modifier
                .align(Alignment.BottomStart)
                .padding(4.dp)
        ) {
            PlayerChromeButton(
                painter = painterResource(
                    id = if (isPlaying) R.drawable.pause_24dp_e3e3e3_fill1_wght400_grad0_opsz24
                    else R.drawable.play_arrow_24dp_e3e3e3_fill1_wght400_grad0_opsz24
                ),
                contentDescription = "Play/Pause",
                size = 30.dp,
                onClick = onTogglePlay
            )
        }
    }
}

@Composable
internal fun ShellChrome(mode: PlayerMode, onMinimize: () -> Unit, onClose: () -> Unit) {
    Box(Modifier.fillMaxSize()) {
        if (mode == PlayerMode.Mini) {
            PlayerChromeButton(
                painter = painterResource(R.drawable.close_24dp_e3e3e3_fill0_wght400_grad0_opsz24),
                contentDescription = "Close",
                size = 30.dp,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp),
                onClick = onClose
            )
        } else {
            PlayerChromeButton(
                painter = painterResource(R.drawable.keyboard_arrow_down_24dp_e3e3e3_fill0_wght400_grad0_opsz24),
                contentDescription = "Minimize",
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(4.dp),
                onClick = onMinimize
            )
        }
    }
}

@Composable
internal fun PlayerChromeButton(
    painter: androidx.compose.ui.graphics.painter.Painter,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.Dp = 40.dp
) {
    IconButton(
        onClick = onClick,
        modifier = modifier
            .size(size)
            .background(Color.Black.copy(alpha = 0.4f), CircleShape)
    ) {
        Icon(
            painter = painter,
            contentDescription = contentDescription,
            tint = Color.White,
            modifier = Modifier.size(size * 0.6f)
        )
    }
}

@Composable
internal fun Separator() {
    Text(
        text = " | ",
        color = Color.White.copy(alpha = 0.5f),
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.SemiBold
    )
}

/**
 * Yayın verisindeki canlı konuma göre mesafeyi hesaplar.
 * Aktif HLS penceresi içindeyken (currentPosition > 1000L), cihaz saat farkından (clock skew)
 * etkilenmemek için doğrudan pencere sonuna olan mesafeyi (duration - currentPosition) kullanır;
 * yayın uzun süre duraklatılıp pencere gerisine düştüğünde ise currentLiveOffset değerini baz alır.
 */
private fun Player.distanceToLiveEdgeMs(): Long {
    val d = duration
    val pos = currentPosition
    val windowDistance =
        if (d != C.TIME_UNSET && d > 0) (d - pos).coerceAtLeast(0L) else C.TIME_UNSET
    if (windowDistance != C.TIME_UNSET && pos > 1000L) {
        return windowDistance
    }
    val live = currentLiveOffset
    if (live != C.TIME_UNSET && windowDistance != C.TIME_UNSET) {
        return maxOf(live, windowDistance).coerceAtLeast(0L)
    }
    if (live != C.TIME_UNSET) return live.coerceAtLeast(0L)
    return if (windowDistance != C.TIME_UNSET) windowDistance else 0L
}

