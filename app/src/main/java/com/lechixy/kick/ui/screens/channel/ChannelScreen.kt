package com.lechixy.kick.ui.screens.channel

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
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
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.lechixy.kick.R
import com.lechixy.kick.data.local.FollowManager
import com.lechixy.kick.data.model.ChannelDetail
import com.lechixy.kick.data.model.ChannelVideo
import com.lechixy.kick.ui.components.KickPlayerView
import com.lechixy.kick.ui.components.TagChip
import com.lechixy.kick.ui.components.VerifiedBadge
import com.lechixy.kick.ui.components.VideoCard
import com.lechixy.kick.ui.theme.KickVoltGreen
import com.lechixy.kick.util.FormatUtils
import com.lechixy.kick.util.buildOptimizedImageRequest
import kotlinx.coroutines.flow.first
import kotlin.math.roundToInt

enum class ChannelTab(val title: String) {
    HOME("Home"),
    ABOUT("About"),
    VIDEOS("Videos"),
    CLIPS("Clips")
}

private const val WIDE_BREAKPOINT_DP = 700
private const val CHAT_WIDTH_FRACTION = 0.35f // eski 1.3 : 0.7 oranı

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChannelScreen(
    channelSlug: String,
    isFullScreen: Boolean,
    isInPipMode: Boolean,
    onFullScreenChange: (Boolean) -> Unit,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ChannelViewModel = viewModel(),
    onChannelLoaded: (ChannelDetail) -> Unit = {}
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var selectedTab by remember { mutableStateOf(ChannelTab.HOME) }

    val followedSlugs by FollowManager.followedSlugs.collectAsState()
    val isFollowing = remember(followedSlugs, channelSlug) {
        FollowManager.isFollowing(channelSlug)
    }

    LaunchedEffect(channelSlug) { viewModel.loadChannel(channelSlug) }
    LaunchedEffect(state.channel) { state.channel?.let(onChannelLoaded) }

    // 0f = normal sayfa, 1f = tam ekran / PiP. Tüm layout bu TEK değerden türüyor.
    // PiP'de pencereyi sistem küçülttüğü için animasyon yok (snap).
    val immersive = isFullScreen || isInPipMode
    val progress = animateFloatAsState(
        targetValue = if (immersive) 1f else 0f,
        animationSpec = if (isInPipMode) snap() else tween(300, easing = FastOutSlowInEasing),
        label = "fullscreenProgress"
    )

    BackHandler(enabled = isFullScreen) { onFullScreenChange(false) }
    ImmersiveSystemBars(isFullScreen = isFullScreen, progress = progress)

    // Status bar / çentik boşluğu: bar gizlenince değişmesin diye "IgnoringVisibility",
    // ve progress arttıkça 0'a iner (player kenarlara kadar büyür).
    val inlineInsets = ScaledInsets(
        base = WindowInsets.systemBarsIgnoringVisibility.union(WindowInsets.displayCutout),
        factor = { 1f - progress.value }
    )

    val backgroundColor = MaterialTheme.colorScheme.background
    Box(
        modifier = modifier
            .fillMaxSize()
            .drawBehind {
                drawRect(
                    androidx.compose.ui.graphics.lerp(
                        backgroundColor,
                        Color.Black,
                        progress.value
                    )
                )
            }
    ) {
        val channel = state.channel
        when {
            // channel varken isLoading true olsa bile (yenileme) player ağacı bozulmasın diye ilk sırada
            channel != null -> {
                val isLive = channel.livestream != null
                val lastLiveText = remember(state.videos) {
                    val latestVideo = state.videos.firstOrNull()
                    FormatUtils.formatLastLive(latestVideo?.startTime ?: latestVideo?.createdAt)
                }

                BoxWithConstraints(Modifier.fillMaxSize()) {
                    val isWide = maxWidth >= WIDE_BREAKPOINT_DP.dp

                    // Ağaç yapısı portrait/landscape/fullscreen/PiP'de AYNI: player hep
                    // Row > Column > ilk eleman. Bu yüzden yeniden oluşmaz, video durmaz.
                    Row(
                        Modifier
                            .fillMaxSize()
                            .windowInsetsPadding(inlineInsets)
                    ) {
                        Column(
                            Modifier
                                .weight(1f)
                                .fillMaxHeight()
                        ) {
                            VideoPlayerBox(
                                isLive = isLive,
                                channel = channel,
                                isFullScreen = isFullScreen,
                                isInPipMode = isInPipMode,
                                onFullScreenChange = onFullScreenChange,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .animatedPlayerFrame { progress.value }
                            )

                            // Yorum/detay alanı: aşağı doğru "kayıp" gider (yükseklik 0'a iner),
                            // composition ve scroll pozisyonu korunur.
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f)
                                    .graphicsLayer { alpha = 1f - progress.value }
                            ) {
                                item {
                                    // TODO: ChannelLiveInfo
                                }
                                item {
                                    ChannelHeaderInfo(
                                        channel = channel,
                                        isLive = isLive,
                                        lastLiveText = lastLiveText,
                                        isFollowing = isFollowing,
                                        onFollowClick = { FollowManager.toggleFollow(channelSlug) },
                                        context = context
                                    )
                                }
                                item {
                                    ChannelTabsRow(
                                        selectedTab = selectedTab,
                                        onTabSelected = { selectedTab = it }
                                    )
                                }
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

                        // Sağ taraftaki chat paneli: sağa doğru "kayıp" gider.
                        if (isWide) {
                            Row(
                                Modifier
                                    .fillMaxHeight()
                                    .animatedWidthFraction(CHAT_WIDTH_FRACTION) { progress.value }
                                    .graphicsLayer { alpha = 1f - progress.value }
                            ) {
                                VerticalDivider(
                                    thickness = 1.dp,
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                )
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight()
                                        .background(
                                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier.padding(16.dp)
                                    ) {
                                        Text(
                                            text = "STREAM CHAT",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            text = "Chatroom ID: ${channel.chatroom?.id ?: "N/A"}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // TopAppBar yok; YouTube gibi player'ın sol üstünde yüzen geri butonu.
                    Box(
                        Modifier
                            .align(Alignment.TopStart)
                            .windowInsetsPadding(inlineInsets)
                    ) {
                        AnimatedVisibility(
                            visible = !immersive,
                            enter = fadeIn(),
                            exit = fadeOut()
                        ) {
                            BackButton(onClick = onBackClick)
                        }
                    }
                }
            }

            else -> {
                // Yükleniyor / hata: topbar olmadığı için geri butonu burada da var
                Box(
                    Modifier
                        .fillMaxSize()
                        .windowInsetsPadding(inlineInsets)
                ) {
                    BackButton(onClick = onBackClick, modifier = Modifier.align(Alignment.TopStart))
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
    }
}

@Composable
private fun BackButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    IconButton(
        onClick = onClick,
        modifier = modifier
            .padding(4.dp)
            .background(Color.Black.copy(alpha = 0.4f), CircleShape)
    ) {
        Icon(
            painter = painterResource(id = R.drawable.arrow_back_24dp_e3e3e3_fill0_wght400_grad0_opsz24),
            contentDescription = "Back",
            tint = Color.White
        )
    }
}

/**
 * Tam ekran animasyonu bittikten SONRA sistem barlarını gizler.
 * Girişte: önce yatay yöne dön, animasyon bitince barları gizle.
 * Çıkışta: barları hemen göster (animasyon sonunda "pat" diye gelmesin), sonra dikeye dön.
 */
@Composable
private fun ImmersiveSystemBars(isFullScreen: Boolean, progress: State<Float>) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }

    LaunchedEffect(isFullScreen, activity) {
        val act = activity ?: return@LaunchedEffect
        val controller = WindowCompat.getInsetsController(act.window, act.window.decorView)
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

        if (isFullScreen) {
            act.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            snapshotFlow { progress.value }.first { it >= 0.99f }
            controller.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            controller.show(WindowInsetsCompat.Type.systemBars())
            act.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    DisposableEffect(activity) {
        onDispose {
            activity?.let {
                WindowCompat.getInsetsController(it.window, it.window.decorView)
                    .show(WindowInsetsCompat.Type.systemBars())
                it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            }
        }
    }
}

/** Player yüksekliği: 16:9 (inline) -> mevcut alanın tamamı (fullscreen). Layout fazında okunur, recomposition yok. */
private fun Modifier.animatedPlayerFrame(progress: () -> Float): Modifier =
    layout { measurable, constraints ->
        val width = constraints.maxWidth
        val fullHeight = constraints.maxHeight
        val inlineHeight = (width * 9f / 16f).roundToInt()
        val height = (inlineHeight + (fullHeight - inlineHeight) * progress())
            .roundToInt()
            .coerceIn(0, fullHeight)
        val placeable = measurable.measure(Constraints.fixed(width, height))
        layout(width, height) { placeable.place(0, 0) }
    }

/** Genişliği (üst genişliğin) fraction'ı -> 0 arasında daraltır; içerik tam genişlikte ölçülüp kırpılır. */
private fun Modifier.animatedWidthFraction(fraction: Float, progress: () -> Float): Modifier =
    this
        .layout { measurable, constraints ->
            val fullWidth = (constraints.maxWidth * fraction).roundToInt()
            val width = (fullWidth * (1f - progress())).roundToInt()
            val placeable = measurable.measure(Constraints.fixed(fullWidth, constraints.maxHeight))
            layout(width, placeable.height) { placeable.place(0, 0) }
        }
        .clipToBounds()

/** Başka bir WindowInsets'i factor() ile ölçekler. windowInsetsPadding ile birlikte tüketim (consume) mantığı da çalışır. */
private class ScaledInsets(
    private val base: WindowInsets,
    private val factor: () -> Float
) : WindowInsets {
    override fun getLeft(density: Density, layoutDirection: LayoutDirection) =
        (base.getLeft(density, layoutDirection) * factor()).roundToInt()

    override fun getTop(density: Density) = (base.getTop(density) * factor()).roundToInt()

    override fun getRight(density: Density, layoutDirection: LayoutDirection) =
        (base.getRight(density, layoutDirection) * factor()).roundToInt()

    override fun getBottom(density: Density) = (base.getBottom(density) * factor()).roundToInt()
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
    isLive: Boolean,
    lastLiveText: String,
    isFollowing: Boolean,
    onFollowClick: () -> Unit,
    context: Context
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp, 16.dp, 16.dp, 0.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isLive && channel.livestream != null) {
            /**
             * Start/Left
             */
            Column(
                modifier = Modifier
                    .fillMaxWidth(0.6f)
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = channel.livestream.sessionTitle ?: "No title",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // TODO: Add tags chips
                    channel.livestream.tags.forEach { tag ->
                        TagChip(tag = tag)
                    }
                    TagChip(tag = channel.livestream.language ?: "")
                }
            }
            /**
             * Right/End
             */
            Column(
                verticalArrangement = Arrangement.spacedBy(2.dp),
                horizontalAlignment = Alignment.End
            ) {
                val green = KickVoltGreen
                val labelStyle = MaterialTheme.typography.labelSmall

                Surface(color = green, shape = RoundedCornerShape(4.dp)) {
                    Text(
                        text = "LIVE",
                        color = Color.Black,
                        style = labelStyle,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(6.dp, 2.dp)
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = FormatUtils.formatFollowersCount(channel.livestream.viewerCount.toString()),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "viewers",
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

            // Canlı yayın yoksa Last live gösterimi (onSurface rengi ile açık/koyu temaya uyumlu)
            if (!isLive) {
                Text(
                    text = lastLiveText,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        // Takip Butonu
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
                                text = "Daha Fazla",
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    // En fazla 4 video göster
                    val recentList = videos.take(4)
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        maxItemsInEachRow = 3
                    ) {
                        recentList.forEach { video ->
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
                    text = if (!channel.user?.bio.isNullOrBlank()) channel.user?.bio!! else "Biyografi eklenmemiş.",
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

// ChannelScreen.kt içindeki eski VideoPlayerBox'ın yerine geçer.
// Silinen importlar (artık gerekmiyor): PictureInPictureParams, Rational, Activity, Build (başka yerde kullanılmıyorsa)

@Composable
private fun VideoPlayerBox(
    isLive: Boolean,
    channel: ChannelDetail,
    isFullScreen: Boolean,
    isInPipMode: Boolean,
    onFullScreenChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier // boyutu ChannelScreen verir (animatedPlayerFrame)
) {
    val context = LocalContext.current

    // aspectRatio(16/9) KALDIRILDI: yükseklik artık fullscreen animasyonuyla dışarıdan geliyor.
    Box(
        modifier = modifier.background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        val playbackUrl = channel.playbackUrl

        if (isLive && !playbackUrl.isNullOrBlank()) {
            // PiP parametreleri (sourceRectHint, autoEnter) artık KickPlayerView içinde yönetiliyor.
            // Eski onPlayerCoordinatesChanged bloğu her layout'ta autoEnter=true set edip
            // oynatma/ayar durumunu eziyordu, o yüzden silindi.
            KickPlayerView(
                playbackUrl = playbackUrl,
                channelName = channel.user?.username ?: channel.slug,
                streamTitle = channel.livestream?.sessionTitle ?: "Canlı Yayın",
                channel = channel,
                isFullScreen = isFullScreen,
                isInPipMode = isInPipMode,
                onFullScreenChange = onFullScreenChange,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            val bannerUrl = channel.offlineBannerImage?.src ?: channel.bannerImage?.url
            if (!bannerUrl.isNullOrEmpty()) {
                AsyncImage(
                    model = remember(bannerUrl) {
                        buildOptimizedImageRequest(
                            context = context,
                            data = bannerUrl,
                            targetWidthDp = 640,
                            targetHeightDp = 360
                        )
                    },
                    contentDescription = "Offline Banner",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Text(
                    text = "CHANNEL OFFLINE",
                    color = Color.Gray,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}