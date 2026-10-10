package com.lechixy.kick.ui.components

import android.content.pm.ActivityInfo
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.systemBarsIgnoringVisibility
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastAny
import androidx.compose.ui.util.fastForEach
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import coil3.compose.AsyncImage
import com.lechixy.kick.PlayerHostState
import com.lechixy.kick.PlayerMode
import com.lechixy.kick.ui.screens.channel.findActivity
import com.lechixy.kick.util.buildOptimizedImageRequest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

internal const val WIDE_BREAKPOINT_DP = 700
internal const val CHAT_WIDTH_FRACTION = 0.35f

private const val MINI_DEFAULT_WIDTH_DP = 200f
private const val MINI_MIN_WIDTH_DP = 140f
private const val MINI_MAX_WIDTH_DP = 360f
private const val MINI_PADDING_DP = 12f

/**
 * NavHost'un ÜSTÜNDE duran tek player katmanı.
 *
 * Tüm görünüm TEK bir değerden türer: frameT  (-1 = mini, 0 = inline 16:9, 1 = tam ekran).
 * Çerçeve (konum+boyut) layout fazında hesaplanır -> animasyon/sürükleme sırasında recomposition yok,
 * KickPlayerView hiç yeniden oluşmaz, video durmaz.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PlayerHost(
    host: PlayerHostState,
    isInPipMode: Boolean,
    onMinimize: () -> Unit,
    onExpand: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (host.slug == null || host.mode == PlayerMode.Hidden) return

    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val mode = host.mode

    val frameT = remember { Animatable(0f) }
    var dragT by remember { mutableFloatStateOf(0f) }
    var container by remember { mutableStateOf(IntSize.Zero) }
    var area by remember { mutableStateOf(IntRect.Zero) }

    // Mini pencere: konum, hareket aralığının oranı olarak tutulur (0..1) => boyut/rotasyon değişse de köşede kalır.
    var miniWidthDp by remember { mutableFloatStateOf(MINI_DEFAULT_WIDTH_DP) }
    var fx by remember { mutableFloatStateOf(1f) }
    var fy by remember { mutableFloatStateOf(1f) }

    val targetT = when {
        isInPipMode || mode == PlayerMode.Fullscreen -> 1f
        mode == PlayerMode.Mini -> -1f
        else -> 0f
    }
    LaunchedEffect(targetT, isInPipMode) {
        // PiP'de pencereyi sistem küçülttüğü için animasyon yok (snap)
        if (isInPipMode) frameT.snapTo(targetT)
        else frameT.animateTo(targetT, tween(300, easing = FastOutSlowInEasing))
    }

    ImmersiveSystemBars(isFullScreen = mode == PlayerMode.Fullscreen, progress = frameT.asState())

    fun px(dp: Float) = with(density) { dp.dp.toPx() }

    fun inlineRect(): IntRect {
        val wide = container.width >= with(density) { WIDE_BREAKPOINT_DP.dp.roundToPx() }
        val w = if (wide) (area.width * (1f - CHAT_WIDTH_FRACTION)).roundToInt() else area.width
        return IntRect(area.left, area.top, area.left + w, area.top + (w * 9f / 16f).roundToInt())
    }

    fun miniWidthPx(): Float {
        val minW = px(MINI_MIN_WIDTH_DP)
        val maxW = max(minW, min(area.width * 0.7f, px(MINI_MAX_WIDTH_DP)))
        return px(miniWidthDp).coerceIn(minW, maxW)
    }

    fun miniRangeX() = (area.width - miniWidthPx() - 2 * px(MINI_PADDING_DP)).coerceAtLeast(1f)
    fun miniRangeY() =
        (area.height - miniWidthPx() * 9f / 16f - 2 * px(MINI_PADDING_DP)).coerceAtLeast(1f)

    fun miniRect(): IntRect {
        val w = miniWidthPx()
        val h = w * 9f / 16f
        val pad = px(MINI_PADDING_DP)
        val l = area.left + pad + fx * miniRangeX()
        val t = area.top + pad + fy * miniRangeY()
        return IntRect(l.roundToInt(), t.roundToInt(), (l + w).roundToInt(), (t + h).roundToInt())
    }

    fun lerpRect(a: IntRect, b: IntRect, f: Float) = IntRect(
        (a.left + (b.left - a.left) * f).roundToInt(),
        (a.top + (b.top - a.top) * f).roundToInt(),
        (a.right + (b.right - a.right) * f).roundToInt(),
        (a.bottom + (b.bottom - a.bottom) * f).roundToInt()
    )

    fun currentT() = frameT.value + dragT
    fun frameAt(t: Float): IntRect =
        if (t >= 0f) lerpRect(
            inlineRect(),
            IntRect(0, 0, container.width, container.height),
            t.coerceAtMost(1f)
        )
        else lerpRect(inlineRect(), miniRect(), (-t).coerceAtMost(1f))

    fun snapMini() {
        val rx = miniRangeX();
        val ry = miniRangeY()
        val dx = min(fx, 1f - fx) * rx
        val dy = min(fy, 1f - fy) * ry
        val cornerReach = miniWidthPx() * 0.6f
        var tx = fx;
        var ty = fy
        if (dx <= dy) {
            tx = fx.roundToInt().toFloat()
            if (dy < cornerReach) ty = fy.roundToInt().toFloat()
        } else {
            ty = fy.roundToInt().toFloat()
            if (dx < cornerReach) tx = fx.roundToInt().toFloat()
        }
        val sx = fx;
        val sy = fy
        scope.launch {
            animate(sx, tx, animationSpec = spring(stiffness = 400f)) { v, _ ->
                fx = v
            }
        }
        scope.launch {
            animate(sy, ty, animationSpec = spring(stiffness = 400f)) { v, _ ->
                fy = v
            }
        }
    }

    var rootOrigin by remember { mutableStateOf(Offset.Zero) }
    Box(
        modifier
            .fillMaxSize()
            .onSizeChanged { container = it }
            .onGloballyPositioned { rootOrigin = it.positionInRoot() }
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.systemBarsIgnoringVisibility.union(WindowInsets.displayCutout))
        ) {
            Spacer(
                Modifier
                    .fillMaxSize()
                    .onGloballyPositioned { c ->
                        val p = c.positionInRoot() - rootOrigin
                        area = IntRect(
                            p.x.roundToInt(), p.y.roundToInt(),
                            p.x.roundToInt() + c.size.width, p.y.roundToInt() + c.size.height
                        )
                    }
            )
        }

        Box(
            Modifier
                .offset { frameAt(currentT()).let { IntOffset(it.left, it.top) } }
                .layout { measurable, _ ->
                    val f = frameAt(currentT())
                    val p = measurable.measure(
                        Constraints.fixed(
                            f.width.coerceAtLeast(0),
                            f.height.coerceAtLeast(0)
                        )
                    )
                    layout(p.width, p.height) { p.place(0, 0) }
                }
                .graphicsLayer {
                    val m = (-currentT()).coerceIn(0f, 1f)
                    shape = RoundedCornerShape(12.dp.toPx() * m)
                    clip = true
                    shadowElevation = 12.dp.toPx() * m
                }
                // --- Mini: sürükle + pinch ile boyutlandır + bırakınca kenara/köşeye oturt; tek tık -> genişlet
                .pointerInput(mode, isInPipMode) {
                    if (mode != PlayerMode.Mini || isInPipMode) return@pointerInput
                    detectTapGestures(onTap = { onExpand() })
                }
                .pointerInput(mode, isInPipMode) {
                    if (mode != PlayerMode.Mini || isInPipMode) return@pointerInput
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        var moved = false
                        var accum = Offset.Zero
                        do {
                            val event = awaitPointerEvent()
                            if (event.changes.fastAny { it.isConsumed }) break
                            val pan = event.calculatePan()
                            val zoom = event.calculateZoom()
                            accum += pan
                            if (!moved && (accum.getDistance() > viewConfiguration.touchSlop || zoom != 1f)) moved =
                                true
                            if (moved) {
                                miniWidthDp = (miniWidthDp * zoom).coerceIn(
                                    MINI_MIN_WIDTH_DP,
                                    MINI_MAX_WIDTH_DP
                                )
                                fx = (fx + pan.x / miniRangeX()).coerceIn(0f, 1f)
                                fy = (fy + pan.y / miniRangeY()).coerceIn(0f, 1f)
                                event.changes.fastForEach { if (it.positionChanged()) it.consume() }
                            }
                        } while (event.changes.fastAny { it.pressed })
                        if (moved) snapMini()
                    }
                }
                // --- Inline/Tam ekran: dikey sürükleme. Sürüklerken sadece biraz büyür/küçülür, bırakınca karar verilir.
                .pointerInput(mode, isInPipMode) {
                    if (mode == PlayerMode.Mini || isInPipMode) return@pointerInput
                    var total = 0f
                    val commitPx = 56.dp.toPx()
                    val travelPx = 400.dp.toPx()
                    detectVerticalDragGestures(
                        onDragStart = { total = 0f },
                        onVerticalDrag = { _, dy ->
                            total += dy
                            val raw = -total / travelPx
                            dragT = if (mode == PlayerMode.Fullscreen) raw.coerceIn(-0.3f, 0f)
                            else raw.coerceIn(-0.3f, 0.3f)
                        },
                        onDragCancel = {
                            scope.launch {
                                frameT.snapTo(frameT.value + dragT); dragT = 0f
                                frameT.animateTo(targetT, tween(250))
                            }
                        },
                        onDragEnd = {
                            val up = total < -commitPx
                            val down = total > commitPx
                            val commit = when {
                                mode == PlayerMode.Expanded && up -> {
                                    { host.setFullscreen(true) }
                                }

                                mode == PlayerMode.Expanded && down -> onMinimize
                                mode == PlayerMode.Fullscreen && down -> {
                                    { host.setFullscreen(false) }
                                }

                                else -> null
                            }
                            scope.launch {
                                // Bırakılan noktadan devam et (zıplama yok)
                                frameT.snapTo(frameT.value + dragT); dragT = 0f
                                if (commit != null) commit() else frameT.animateTo(
                                    targetT,
                                    tween(250)
                                )
                            }
                        }
                    )
                }
        ) {
            PlayerSurface(
                host = host,
                mode = mode,
                isInPipMode = isInPipMode,
                onMinimize = onMinimize,
                onClose = onClose
            )
        }
    }
}

/**
 * Player surface
 */
@Composable
private fun PlayerSurface(
    host: PlayerHostState,
    mode: PlayerMode,
    isInPipMode: Boolean,
    onMinimize: () -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val channel = host.channel
    val isLive = channel?.livestream != null
    val playbackUrl = channel?.playbackUrl

    Box(Modifier
        .fillMaxSize()
        .background(Color.Black), contentAlignment = Alignment.Center) {
        if (channel != null && isLive && !playbackUrl.isNullOrBlank()) {
            // PiP parametreleri (sourceRectHint, autoEnter) KickPlayerView içinde yönetiliyor.
            KickPlayerView(
                playbackUrl = playbackUrl,
                channelName = channel.user?.username ?: channel.slug,
                streamTitle = channel.livestream.sessionTitle ?: "Canlı Yayın",
                channel = channel,
                isFullScreen = mode == PlayerMode.Fullscreen,
                isInPipMode = isInPipMode,
                isMini = mode == PlayerMode.Mini,
                onFullScreenChange = host::setFullscreen,
                onMinimize = onMinimize,
                onClose = onClose,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            val bannerUrl = channel?.offlineBannerImage?.src ?: channel?.bannerImage?.url
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
            } else if (channel != null) {
                Text("CHANNEL OFFLINE", color = Color.Gray, fontWeight = FontWeight.Bold)
            }
            if (!isInPipMode) {
                ShellChrome(mode = mode, onMinimize = {
                    if (mode == PlayerMode.Fullscreen) host.setFullscreen(false) else onMinimize()
                }, onClose = onClose)
            }
        }
    }
}

/**
 * Tam ekran animasyonu bittikten SONRA sistem barlarını gizler.
 * Girişte: önce yatay yöne dön, animasyon bitince barları gizle.
 * Çıkışta: barları hemen göster, sonra dikeye dön.
 */
@Composable
private fun ImmersiveSystemBars(isFullScreen: Boolean, progress: State<Float>) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    var hasBeenFullscreen by remember { mutableStateOf(false) }

    LaunchedEffect(isFullScreen, activity) {
        val act = activity ?: return@LaunchedEffect
        if (!isFullScreen && !hasBeenFullscreen) return@LaunchedEffect
        val controller = WindowCompat.getInsetsController(act.window, act.window.decorView)
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

        if (isFullScreen) {
            hasBeenFullscreen = true
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
                if (hasBeenFullscreen) it.requestedOrientation =
                    ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            }
        }
    }
}