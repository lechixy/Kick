package com.lechixy.kick.ui.components

import android.Manifest
import android.app.Activity
import android.app.PictureInPictureParams
import android.content.ComponentName
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.graphics.Rect
import android.os.Build
import android.util.Rational
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.media3.ui.PlayerView
import com.lechixy.kick.R
import com.lechixy.kick.data.local.PlayerSettingsManager
import com.lechixy.kick.data.model.ChannelDetail
import com.lechixy.kick.service.PlaybackService
import com.lechixy.kick.util.FormatUtils
import com.lechixy.kick.util.NotificationUtils
import com.lechixy.kick.util.PlayerUtils
import com.lechixy.kick.util.VideoQualityOption
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun KickPlayerView(
    playbackUrl: String,
    channelName: String = "",
    streamTitle: String = "",
    channel: ChannelDetail? = null,
    isInPipMode: Boolean = false,
    onPlayerCoordinatesChanged: (Rect) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val activity = remember(context) { context as? Activity }

    var isBuffering by remember { mutableStateOf(true) }
    var isPlaying by remember { mutableStateOf(true) }
    var isMuted by remember { mutableStateOf(false) }
    var volume by remember { mutableFloatStateOf(1f) }
    var playbackError by remember { mutableStateOf<String?>(null) }
    var showControls by remember { mutableStateOf(false) }
    var isFullScreen by remember { mutableStateOf(false) }

    var showSettingsDialog by remember { mutableStateOf(false) }
    var sleepTimerMinutes by remember { mutableIntStateOf(0) }
    var remainingTimerSeconds by remember { mutableIntStateOf(0) }

    var selectedQualityHeight by remember { mutableIntStateOf(-1) }
    val availableQualities = remember { mutableStateListOf<VideoQualityOption>() }
    var currentVideoHeight by remember { mutableIntStateOf(0) }

    var player by remember { mutableStateOf<Player?>(null) }

    DisposableEffect(context) {
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        future.addListener(
            { player = runCatching { future.get() }.getOrNull() },
            ContextCompat.getMainExecutor(context)
        )
        onDispose {
            player?.stop() // ekrandan çıkınca yayın dursun istiyorsan bırak, arka planda devam etsin istiyorsan sil
            MediaController.releaseFuture(future)
            player = null
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            activity?.window?.insetsController?.show(WindowInsets.Type.systemBars())
        }
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted && sleepTimerMinutes > 0) {
            NotificationUtils.updateTimerNotification(context, sleepTimerMinutes * 60)
        }
    }

    // Sleep timer
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
                player?.pause()          // ✅ DEĞİŞTİ: gerçekten durdurur (isPlaying listener'dan güncellenir)
                sleepTimerMinutes = 0
                NotificationUtils.showTimerEndedNotification(context)
            }
        } else {
            NotificationUtils.cancelTimerNotification(context)
        }
    }

    val pipEnabled by PlayerSettingsManager.pipEnabled.collectAsState()

    LaunchedEffect(isPlaying, pipEnabled) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && activity != null) {
            val pipParams = PictureInPictureParams.Builder()
                .setAspectRatio(Rational(16, 9))
                .setAutoEnterEnabled(isPlaying && pipEnabled)
                .build()
            activity.setPictureInPictureParams(pipParams)
        }
    }

    // Ekran açık tutma
    DisposableEffect(isPlaying) {
        if (isPlaying) activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }

    LaunchedEffect(player, isMuted, volume) {
        player?.volume = if (isMuted) 0f else volume
    }

    val toggleFullScreen = {
        val target = !isFullScreen
        isFullScreen = target
        activity?.let { act ->
            if (target) {
                act.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                act.window.insetsController?.hide(WindowInsets.Type.systemBars())
            } else {
                act.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                act.window.insetsController?.show(WindowInsets.Type.systemBars())
            }
        }
    }

    LaunchedEffect(showControls) {
        if (showControls) {
            delay(3500.milliseconds)
            showControls = false
        }
    }

    DisposableEffect(player) {
        val p = player
        if (p == null) {
            onDispose { }
        } else {
            fun updateQualities(tracks: Tracks) {
                availableQualities.clear()
                val parsed = mutableListOf<VideoQualityOption>()
                tracks.groups.forEach { group ->
                    if (group.type == C.TRACK_TYPE_VIDEO) {
                        for (i in 0 until group.length) {
                            val format = group.getTrackFormat(i)
                            if (format.height > 0) {
                                val height = format.height
                                val bitrate = format.bitrate
                                val usage = PlayerUtils.estimateDataUsagePerHour(bitrate, height)
                                parsed.add(
                                    VideoQualityOption(
                                        label = "${height}p",
                                        height = height,
                                        bitrate = bitrate,
                                        dataPerHourText = usage
                                    )
                                )
                            }
                        }
                    }
                }
                availableQualities.addAll(
                    parsed.distinctBy { it.height }.sortedByDescending { it.height }
                )
            }

            val listener = object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    isBuffering = playbackState == Player.STATE_BUFFERING
                }

                override fun onIsPlayingChanged(playing: Boolean) {
                    isPlaying = playing
                }

                override fun onVideoSizeChanged(videoSize: VideoSize) {
                    if (videoSize.height > 0) currentVideoHeight = videoSize.height
                }

                override fun onTracksChanged(tracks: Tracks) {
                    updateQualities(tracks)
                }

                override fun onPlayerError(error: PlaybackException) {
                    playbackError = error.message ?: "Oynatma hatası"
                    isBuffering = false
                }
            }

            // Servis zaten çalışıyorsa mevcut durumu al
            isPlaying = p.isPlaying
            isBuffering = p.playbackState == Player.STATE_BUFFERING
            updateQualities(p.currentTracks)

            p.addListener(listener)
            onDispose { p.removeListener(listener) } // ✅ release YOK, ömrünü servis yönetir
        }
    }

    // ✅ DEĞİŞTİ: HlsMediaSource yerine MediaItem veriyoruz (DefaultMediaSourceFactory HLS'i tanır)
    LaunchedEffect(player, playbackUrl, streamTitle, channelName) {
        val p = player ?: return@LaunchedEffect
        if (playbackUrl.isBlank()) return@LaunchedEffect

        playbackError = null
        isBuffering = true

        val metadata = MediaMetadata.Builder()
            .setTitle(if (streamTitle.isNotBlank()) streamTitle else "Canlı Yayın")
            .setArtist(if (channelName.isNotBlank()) channelName else "Kick")
            .build()

        val mediaItem = MediaItem.Builder()
            .setUri(playbackUrl.toUri())
            .setMediaMetadata(metadata)
            .build()

        p.setMediaItem(mediaItem)
        p.prepare()
        p.play()
    }

    val playerContent: @Composable (Modifier) -> Unit = { mod ->
        var totalDragY by remember { mutableFloatStateOf(0f) }

        Box(
            modifier = mod
                .background(Color.Black)
                .onGloballyPositioned { coordinates ->
                    val bounds = coordinates.boundsInWindow()
                    onPlayerCoordinatesChanged(
                        Rect(
                            bounds.left.toInt(),
                            bounds.top.toInt(),
                            bounds.right.toInt(),
                            bounds.bottom.toInt()
                        )
                    )
                }
                .pointerInput(isFullScreen, isInPipMode) {
                    if (!isInPipMode) {
                        detectTapGestures(
                            onDoubleTap = {
                                toggleFullScreen()
                            },
                            onTap = {
                                showControls = !showControls
                            }
                        )
                    }
                }
                .pointerInput(isFullScreen, isInPipMode) {
                    if (!isInPipMode) {
                        detectVerticalDragGestures(
                            onDragStart = { totalDragY = 0f },
                            onDragEnd = {
                                val threshold = 70f // Tetiklenme eşiği
                                if (!isFullScreen && totalDragY < -threshold) {
                                    // Yukarı kaydırıldı -> Tam ekrana geç
                                    toggleFullScreen()
                                } else if (isFullScreen && totalDragY > threshold) {
                                    // Aşağı kaydırıldı -> Tam ekrandan çık
                                    toggleFullScreen()
                                }
                                totalDragY = 0f
                            },
                            onDragCancel = { totalDragY = 0f },
                            onVerticalDrag = { _, dragAmount ->
                                totalDragY += dragAmount
                            }
                        )
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        useController = false
                        keepScreenOn = true
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                    }
                },
                update = { playerView ->
                    playerView.player = player          // ✅ EKSİK OLAN SATIR
                    playerView.keepScreenOn = isPlaying
                },
                modifier = Modifier.fillMaxSize()
            )

            if (isBuffering && playbackError == null) {
                CircularProgressIndicator(
                    color = Color(0xFF53FC18),
                    modifier = Modifier.size(44.dp)
                )
            }

            // Custom Kontroller Katmanı
            AnimatedVisibility(
                visible = showControls && playbackError == null && !isInPipMode,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.fillMaxSize()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.5f))
                ) {
                    // ÜST BAR: LIVE Rozeti + Aktif Kalite + Uyku Zamanlayıcısı
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.TopStart)
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 1. LIVE Rozeti
                        Surface(color = Color(0xFF53FC18), shape = RoundedCornerShape(4.dp)) {
                            Text(
                                text = "LIVE",
                                color = Color.Black,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }

                        // 2. Aktif Kalite Etiketi
                        val displayQuality = remember(selectedQualityHeight, currentVideoHeight) {
                            if (selectedQualityHeight > 0) "${selectedQualityHeight}p"
                            else if (currentVideoHeight > 0) "${currentVideoHeight}p (Auto)"
                            else "Auto"
                        }
                        Surface(
                            color = Color.Black.copy(alpha = 0.6f),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = displayQuality,
                                color = Color.White,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }

                        // 3. Uyku Zamanlayıcısı (Sadece aktif ve kalan süre > 0 ise görünür)
                        if (sleepTimerMinutes > 0 && remainingTimerSeconds > 0) {
                            Surface(
                                color = Color.Black.copy(alpha = 0.75f),
                                shape = RoundedCornerShape(6.dp),
                                border = BorderStroke(1.dp, Color(0xFF53FC18).copy(alpha = 0.6f))
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        painter = painterResource(id = R.drawable.timer_24dp_e3e3e3_fill0_wght400_grad0_opsz24),
                                        contentDescription = "Timer",
                                        tint = Color(0xFF53FC18),
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        text = "Uyku: ${(remainingTimerSeconds + 59) / 60}m",
                                        color = Color.White,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                    }

                    // ORTA: Play/Pause Butonu
                    IconButton(
                        onClick = { if (isPlaying) player?.pause() else player?.play() },
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
                            tint = Color.White,
                            modifier = Modifier.size(32.dp)
                        )
                    }

                    // ALT BAR: Sol Ses Slider + Sağ Ayarlar & Tam Ekran
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.BottomCenter)
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Sol: Ses Kontrolleri
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            IconButton(
                                onClick = {
                                    isMuted = !isMuted
                                    player?.volume = if (isMuted) 0f else volume
                                },
                                modifier = Modifier.size(32.dp)
                            ) {
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
                                onValueChange = { newVol ->
                                    volume = newVol
                                    isMuted = false
                                    player?.volume = newVol
                                },
                                valueRange = 0f..1f,
                                colors = SliderDefaults.colors(
                                    thumbColor = Color(0xFF53FC18),
                                    activeTrackColor = Color(0xFF53FC18),
                                    inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                                ),
                                modifier = Modifier.width(110.dp)
                            )

                            Text(
                                text = "${(if (isMuted) 0f else volume * 100).toInt()}%",
                                color = Color.White,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold
                            )

                            Text(
                                text = " | ",
                                color = Color.White.copy(alpha = 0.5f),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold
                            )

                            Text(
                                text = streamTitle,
                                color = Color.White,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold
                            )

                            Text(
                                text = " | ",
                                color = Color.White.copy(alpha = 0.5f),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold
                            )

                            Text(
                                text = "${FormatUtils.formatViewersCount(channel?.livestream?.viewerCount)} viewers",
                                color = Color.White,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        // Sağ: Ayarlar Butonu + Tam Ekran Butonu
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Ayarlar (YouTube gibi sağ altta)
                            IconButton(onClick = { showSettingsDialog = true }) {
                                Icon(
                                    painter = painterResource(id = R.drawable.settings_24dp_e3e3e3_fill0_wght400_grad0_opsz24),
                                    contentDescription = "Settings",
                                    tint = Color.White
                                )
                            }

                            // Tam Ekran
                            IconButton(onClick = toggleFullScreen as () -> Unit) {
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
        }
    }

    if (!isFullScreen) {
        playerContent(modifier)
    } else {
        // GERÇEK TAM EKRAN: Navigation Bar sızıntısı engellendi
        Dialog(
            onDismissRequest = { toggleFullScreen() },
            properties = DialogProperties(
                usePlatformDefaultWidth = false,
                dismissOnBackPress = true,
                decorFitsSystemWindows = false
            )
        ) {
            // Dialog penceresinin arka planını ve navigation bar rengini saf siyah yapma
            val dialogWindow = (LocalView.current.parent as? DialogWindowProvider)?.window
            SideEffect {
                dialogWindow?.let { win ->
                    win.navigationBarColor = android.graphics.Color.BLACK
                    win.statusBarColor = android.graphics.Color.BLACK
                    win.insetsController?.hide(WindowInsets.Type.navigationBars() or WindowInsets.Type.statusBars())
                    win.insetsController?.systemBarsBehavior =
                        WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
            ) {
                playerContent(Modifier.fillMaxSize())
            }
        }
    }

    if (showSettingsDialog) {
        AlertDialog(
            onDismissRequest = { showSettingsDialog = false },
            title = { Text("Player Ayarları") },
            text = {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    // PiP Açma/Kapatma Switch'i
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "Resim İçinde Resim (PiP)",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "Uygulamadan çıkıldığında yayını küçük ekranda oynat",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            }
                            Switch(
                                checked = pipEnabled,
                                onCheckedChange = { PlayerSettingsManager.setPipEnabled(it) }
                            )
                        }
                    }
                    item { HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp)) }

                    item {
                        Text(
                            text = "UYKU ZAMANLAYICISI",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf(0, 15, 30, 45, 60).forEach { mins ->
                                val selected = sleepTimerMinutes == mins
                                FilterChip(
                                    selected = selected,
                                    onClick = {
                                        sleepTimerMinutes = mins
                                        showSettingsDialog = false

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
                                                    NotificationUtils.updateTimerNotification(
                                                        context,
                                                        mins * 60
                                                    )
                                                } else {
                                                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                                }
                                            } else {
                                                NotificationUtils.updateTimerNotification(
                                                    context,
                                                    mins * 60
                                                )
                                            }
                                        }
                                    },
                                    label = { Text(if (mins == 0) "Kapalı" else "${mins}m") }
                                )
                            }
                        }
                    }

                    item { HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp)) }

                    item {
                        Text(
                            text = "YAYIN KALİTESİ",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedQualityHeight = -1
                                    player?.let {
                                        it.trackSelectionParameters =
                                            it.trackSelectionParameters
                                                .buildUpon()
                                                .clearOverridesOfType(C.TRACK_TYPE_VIDEO)
                                                .setMaxVideoSizeSd()
                                                .build()
                                    }
                                    showSettingsDialog = false
                                }
                                .padding(vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Otomatik",
                                fontWeight = if (selectedQualityHeight == -1) FontWeight.Bold else FontWeight.Normal,
                                color = if (selectedQualityHeight == -1) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                "Ağ hızına göre",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    }

                    items(availableQualities, key = { it.height }) { quality ->
                        val isSelected = selectedQualityHeight == quality.height
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedQualityHeight = quality.height
                                    player?.let {
                                        it.trackSelectionParameters =
                                            it.trackSelectionParameters
                                                .buildUpon()
                                                .setMaxVideoSize(
                                                    quality.height * 16 / 9,
                                                    quality.height
                                                )
                                                .setMinVideoSize(
                                                    quality.height * 16 / 9,
                                                    quality.height
                                                )
                                                .build()
                                    }
                                    showSettingsDialog = false
                                }
                                .padding(vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = quality.label,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                quality.dataPerHourText,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSettingsDialog = false }) {
                    Text("Kapat")
                }
            }
        )
    }
}