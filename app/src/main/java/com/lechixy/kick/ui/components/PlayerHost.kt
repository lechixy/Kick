package com.lechixy.kick.ui.components

import android.content.pm.ActivityInfo
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.lerp as lerpColor
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.lerp as lerpDp
import androidx.compose.ui.unit.roundToIntRect
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
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
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
import androidx.compose.ui.input.pointer.util.VelocityTracker
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
import com.lechixy.kick.data.model.getBannerUrl
import com.lechixy.kick.ui.screens.channel.findActivity
import com.lechixy.kick.util.buildOptimizedImageRequest
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

internal const val WIDE_BREAKPOINT_DP = 700
internal const val CHAT_WIDTH_FRACTION = 0.35f

private const val MINI_DEFAULT_WIDTH_DP = 200f
private const val MINI_MIN_WIDTH_DP = 140f
private const val MINI_MAX_WIDTH_DP = 500f
private const val MINI_PADDING_DP = 16f

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
    pageContent: @Composable (String) -> Unit,
    modifier: Modifier = Modifier
) {
    if (host.slug == null || host.mode == PlayerMode.Hidden) return

    val context = LocalContext.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val mode = host.mode

    val expansion = host.expansionSource
    val expandProgress = remember { Animatable(0f) }
    var isExpanding by remember { mutableStateOf(false) }
    var previewThumbnailUrl by remember { mutableStateOf<String?>(null) }

    val exitAlpha = remember { Animatable(1f) }
    val exitScale = remember { Animatable(1f) }

    LaunchedEffect(host.isExiting) {
        if (host.isExiting) {
            launch {
                exitAlpha.animateTo(
                    targetValue = 0f,
                    animationSpec = tween(durationMillis = 350, easing = FastOutSlowInEasing)
                )
            }
            launch {
                exitScale.animateTo(
                    targetValue = 0.94f,
                    animationSpec = tween(durationMillis = 350, easing = FastOutSlowInEasing)
                )
            }
            host.finishClose()
            exitAlpha.snapTo(1f)
            exitScale.snapTo(1f)
        }
    }

    val frameT = remember { Animatable(0f) }
    var releaseVelocity by remember { mutableFloatStateOf(0f) }
    val settleJobs = remember { mutableListOf<Job>() }
    var pageAlpha by remember { mutableFloatStateOf(0f) }   // 0'dan başlar -> açılışta fade-in
    var fsScale by remember { mutableFloatStateOf(1f) }
    var fsDy by remember { mutableFloatStateOf(0f) }
    val showPage by remember {
        derivedStateOf {
            host.mode == PlayerMode.Expanded || host.mode == PlayerMode.Fullscreen || pageAlpha > 0.01f || isExpanding
        }
    }
    val loaded = host.channel
    val playerVisible =
        loaded == null || loaded.livestream != null   // yüklenirken shell, canlıysa player, offline'da yok
    var dragScale by remember { mutableFloatStateOf(1f) }   // madde 3
    var playerDy by remember { mutableFloatStateOf(0f) }    // madde 4
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

    LaunchedEffect(mode) {
        animate(
            pageAlpha,
            if (mode == PlayerMode.Mini) 0f else 1f,
            animationSpec = tween(250)
        ) { v, _ -> pageAlpha = v }
    }

    val hasContainer = container.width > 0 && container.height > 0
    LaunchedEffect(expansion, hasContainer) {
        if (expansion != null && hasContainer) {
            previewThumbnailUrl = expansion.thumbnailUrl
            isExpanding = true
            pageAlpha = 1f
            expandProgress.snapTo(0f)
            expandProgress.animateTo(
                targetValue = 1f,
                animationSpec = tween(
                    durationMillis = 360,
                    easing = CubicBezierEasing(0.16f, 1.0f, 0.30f, 1.0f)
                )
            )
            isExpanding = false
            host.clearExpansionSource()
        } else if (expansion == null) {
            isExpanding = false
        }
    }

    LaunchedEffect(targetT, isInPipMode) {
        if (isInPipMode) {
            frameT.snapTo(targetT); return@LaunchedEffect
        }
        val v = releaseVelocity; releaseVelocity = 0f
        if (v != 0f) frameT.animateTo(
            targetT,
            spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessMediumLow
            ),
            initialVelocity = v
        ) else frameT.animateTo(targetT, tween(300, easing = FastOutSlowInEasing))
        if (targetT == 0f) {
            fsScale = 1f; fsDy = 0f
        }
    }

    ImmersiveSystemBars(isFullScreen = mode == PlayerMode.Fullscreen, progress = frameT.asState())

    fun px(dp: Float) = with(density) { dp.dp.toPx() }


    val theaterProgress by animateFloatAsState(
        targetValue = if (host.isTheaterMode) 1f else 0f,
        animationSpec = tween(300, easing = FastOutSlowInEasing),
        label = "theaterProgress"
    )

    fun inlineRect(): IntRect {
        val wide = container.width >= with(density) { WIDE_BREAKPOINT_DP.dp.roundToPx() }
        val effectiveChatFraction = if (wide) host.animatedChatFraction else 0f
        val w = if (wide) (area.width * (1f - effectiveChatFraction)).roundToInt() else area.width
        val playerHeight = (w * 9f / 16f).roundToInt()
        val headerPx = if (host.channelHeaderHeightPx > 0) host.channelHeaderHeightPx else (area.height * 0.24f).roundToInt()
        val theaterTargetHeight = (area.height - headerPx).coerceIn(0, area.height)
        val h = if (wide) {
            (playerHeight + (theaterTargetHeight - playerHeight) * theaterProgress).roundToInt()
        } else {
            playerHeight
        }
        return IntRect(area.left, area.top, area.left + w, area.top + h)
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

    fun currentT() = frameT.value
    fun scaledBottomCenter(r: IntRect, s: Float): IntRect {
        val cx = (r.left + r.right) / 2f
        val w = r.width * s;
        val h = r.height * s
        return IntRect(
            (cx - w / 2).roundToInt(),
            (r.bottom - h).roundToInt(),
            (cx + w / 2).roundToInt(),
            r.bottom
        )
    }

    fun fullRect(): IntRect {
        val effectiveChatFraction = host.animatedChatFraction
        val totalW = container.width * fsScale
        val w = totalW * (1f - effectiveChatFraction)
        val h = container.height * fsScale
        val l = (container.width - totalW) / 2f
        val t = (container.height - h) / 2f + fsDy
        return IntRect(l.roundToInt(), t.roundToInt(), (l + w).roundToInt(), (t + h).roundToInt())
    }

    fun frameAt(t: Float): IntRect {
        val base = scaledBottomCenter(inlineRect(), dragScale)
        return if (t >= 0f) lerpRect(base, fullRect(), t.coerceAtMost(1f))
        else lerpRect(base, miniRect(), (-t).coerceAtMost(1f))
    }

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
            .graphicsLayer {
                scaleX = exitScale.value
                scaleY = exitScale.value
                alpha = exitAlpha.value
            }
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

        val p = expandProgress.value
        val destRect = IntRect(0, 0, container.width, container.height)
        val sourceCardRect = expansion?.cardRect?.roundToIntRect() ?: destRect
        val sourceMediaRect = expansion?.mediaRect?.roundToIntRect() ?: sourceCardRect
        val targetMediaRect = inlineRect()

        val shellRect = if (isExpanding) lerpRect(sourceCardRect, destRect, p) else destRect
        val shellRadius = if (isExpanding) lerpDp(expansion?.cornerRadius ?: 12.dp, 0.dp, p) else 0.dp
        val shellElevation = if (isExpanding) lerpDp(8.dp, 0.dp, p) else 0.dp
        val currentMediaRect = if (isExpanding) lerpRect(sourceMediaRect, targetMediaRect, p) else targetMediaRect

        val cardDetailsAlpha = if (isExpanding) (1f - (p / 0.18f)).coerceIn(0f, 1f) else 0f
        val destContentAlpha = if (isExpanding) ((p - 0.22f) / 0.78f).coerceIn(0f, 1f) else pageAlpha
        val destSlideUpPx = if (isExpanding) with(density) { (18.dp.toPx() * (1f - destContentAlpha)) } else 0f

        if (showPage) {
            Box(
                Modifier
                    .fillMaxSize()
                    .let { mod ->
                        if (isExpanding) {
                            mod
                                .offset { IntOffset(shellRect.left, shellRect.top) }
                                .layout { measurable, _ ->
                                    val pl = measurable.measure(
                                        Constraints.fixed(
                                            shellRect.width.coerceAtLeast(0),
                                            shellRect.height.coerceAtLeast(0)
                                        )
                                    )
                                    layout(pl.width, pl.height) { pl.place(0, 0) }
                                }
                                .shadow(shellElevation, RoundedCornerShape(shellRadius))
                                .clip(RoundedCornerShape(shellRadius))
                                .background(MaterialTheme.colorScheme.background)
                        } else {
                            mod
                                .graphicsLayer { translationY = playerDy; alpha = pageAlpha }
                                .background(MaterialTheme.colorScheme.background)
                        }
                    }
                    .pointerInput(Unit) {
                        detectTapGestures { }
                    }
            ) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            if (isExpanding) {
                                translationY = destSlideUpPx
                                alpha = destContentAlpha
                            }
                        }
                ) {
                    host.pageSlug?.let { s -> key(s) { pageContent(s) } }
                }
            }
        }

        if (isExpanding && expansion != null) {
            if (!expansion.thumbnailUrl.isNullOrEmpty() || expansion.isStreamCard) {
                // Expanding 16:9 Hero Media Layer
                Box(
                    Modifier
                        .offset { IntOffset(currentMediaRect.left, currentMediaRect.top) }
                        .layout { measurable, _ ->
                            val pl = measurable.measure(
                                Constraints.fixed(
                                    currentMediaRect.width.coerceAtLeast(0),
                                    currentMediaRect.height.coerceAtLeast(0)
                                )
                            )
                            layout(pl.width, pl.height) { pl.place(0, 0) }
                        }
                        .clip(
                            RoundedCornerShape(
                                topStart = shellRadius,
                                topEnd = shellRadius,
                                bottomStart = if (p > 0.7f) 0.dp else shellRadius * (1f - p),
                                bottomEnd = if (p > 0.7f) 0.dp else shellRadius * (1f - p)
                            )
                        )
                        .background(Color.Black)
                ) {
                    if (!expansion.thumbnailUrl.isNullOrEmpty()) {
                        AsyncImage(
                            model = remember(expansion.thumbnailUrl) {
                                buildOptimizedImageRequest(
                                    context = context,
                                    data = expansion.thumbnailUrl,
                                    targetWidthDp = 400,
                                    targetHeightDp = 225
                                )
                            },
                            contentDescription = expansion.title,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    }

                    if (expansion.isLive && p < 0.6f) {
                        Box(
                            Modifier
                                .align(Alignment.BottomStart)
                                .padding(8.dp)
                                .graphicsLayer { alpha = (1f - p / 0.6f).coerceIn(0f, 1f) }
                        ) {
                            LiveBadge()
                        }
                    }
                }
            }

            // Card Source Details (Phase 1: quickly fades out in first ~70ms)
            if (cardDetailsAlpha > 0.01f) {
                val mediaH = if (!expansion.thumbnailUrl.isNullOrEmpty() || expansion.isStreamCard) currentMediaRect.height else 0
                val detailsTop = shellRect.top + mediaH
                val detailsHeight = (shellRect.height - mediaH).coerceAtLeast(0)
                Box(
                    Modifier
                        .offset { IntOffset(shellRect.left, detailsTop) }
                        .layout { measurable, _ ->
                            val pl = measurable.measure(
                                Constraints.fixed(shellRect.width.coerceAtLeast(0), detailsHeight)
                            )
                            layout(pl.width, pl.height) { pl.place(0, 0) }
                        }
                        .clip(
                            RoundedCornerShape(
                                bottomStart = shellRadius,
                                bottomEnd = shellRadius
                            )
                        )
                        .graphicsLayer { alpha = cardDetailsAlpha }
                ) {
                    if (expansion.isStreamCard) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            if (!expansion.avatarUrl.isNullOrEmpty()) {
                                AsyncImage(
                                    model = remember(expansion.avatarUrl) {
                                        buildOptimizedImageRequest(
                                            context = context,
                                            data = expansion.avatarUrl,
                                            targetWidthDp = 44,
                                            targetHeightDp = 44
                                        )
                                    },
                                    contentDescription = expansion.username,
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape),
                                    contentScale = ContentScale.Crop
                                )
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = expansion.title ?: "No Title",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = expansion.username ?: "",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                expansion.category?.let { cat ->
                                    Text(
                                        text = cat,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    } else {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            if (!expansion.avatarUrl.isNullOrEmpty()) {
                                AsyncImage(
                                    model = remember(expansion.avatarUrl) {
                                        buildOptimizedImageRequest(
                                            context = context,
                                            data = expansion.avatarUrl,
                                            targetWidthDp = 48,
                                            targetHeightDp = 48
                                        )
                                    },
                                    contentDescription = expansion.username,
                                    modifier = Modifier
                                        .size(46.dp)
                                        .clip(CircleShape),
                                    contentScale = ContentScale.Crop
                                )
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = expansion.title ?: "",
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = expansion.username ?: "",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            }
                        }
                    }
                }
            }
        }

        if (playerVisible && !isExpanding) {
            Box(
                Modifier
                    .offset {
                        frameAt(currentT()).let {
                            IntOffset(
                                it.left,
                                it.top + playerDy.roundToInt()
                            )
                        }
                    }
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
                        var committed = false
                        val tracker = VelocityTracker()
                        val commitPx = 56.dp.toPx()
                        val limitPx = 180.dp.toPx()
                        val travelPx = 240.dp.toPx()
                        val scaleDistPx = 160.dp.toPx()
                        val back = spring<Float>(stiffness = 300f)

                        fun fsDyMax() =
                            container.height / 2f - 0.2f * container.height * fsScale  // videonun %30'u dışarıda

                        fun cancelSettle() {
                            settleJobs.forEach { it.cancel() }; settleJobs.clear()
                        }

                        fun settle() {
                            cancelSettle()
                            settleJobs += scope.launch {
                                launch {
                                    animate(
                                        dragScale,
                                        1f,
                                        animationSpec = back
                                    ) { v, _ -> dragScale = v }
                                }
                                launch {
                                    animate(
                                        playerDy,
                                        0f,
                                        animationSpec = back
                                    ) { v, _ -> playerDy = v }
                                }
                                launch {
                                    animate(
                                        pageAlpha,
                                        1f,
                                        animationSpec = back
                                    ) { v, _ -> pageAlpha = v }
                                }
                                launch {
                                    animate(
                                        fsScale,
                                        1f,
                                        animationSpec = back
                                    ) { v, _ -> fsScale = v }
                                }
                                launch {
                                    animate(fsDy, 0f, animationSpec = back) { v, _ ->
                                        fsDy = v
                                    }
                                }
                            }
                        }

                        fun goMini() {
                            committed = true
                            scope.launch {
                                animate(
                                    playerDy,
                                    0f,
                                    animationSpec = back
                                ) { v, _ -> playerDy = v }
                            }
                            onMinimize()
                        }

                        detectVerticalDragGestures(
                            onDragStart = {
                                cancelSettle()
                                committed = false
                                tracker.resetTracking()
                                total = when (mode) {
                                    PlayerMode.Fullscreen -> fsDy
                                    PlayerMode.Expanded ->
                                        if (dragScale > 1f) {
                                            // dragScale = 1 + 0.2 * up / (up + travel)  ->  up'ı geri çöz
                                            val k = ((dragScale - 1f) / 0.2f).coerceIn(0f, 0.95f)
                                            -(travelPx * k / (1f - k))
                                        } else playerDy

                                    else -> 0f
                                }
                            },
                            onVerticalDrag = { change, dy ->
                                if (committed) return@detectVerticalDragGestures
                                total += dy
                                tracker.addPosition(change.uptimeMillis, change.position)
                                when (mode) {
                                    PlayerMode.Expanded -> {
                                        val up = (-total).coerceAtLeast(0f)
                                        dragScale = 1f + 0.2f * up / (up + travelPx)
                                        val down = total.coerceIn(0f, limitPx)
                                        playerDy = down
                                        pageAlpha =
                                            1f - down / limitPx          // kaydıkça fade, arkadaki ekran belirir
                                        if (total >= limitPx) goMini()
                                    }

                                    PlayerMode.Fullscreen -> {
                                        val y = total.coerceAtLeast(0f)
                                        fsScale =
                                            1f - 0.1f * (y / scaleDistPx).coerceAtMost(1f)   // scale burada durur
                                        fsDy =
                                            y.coerceAtMost(fsDyMax())                           // kayma devam eder
                                    }

                                    else -> Unit
                                }
                            },
                            onDragCancel = { settle() },
                            onDragEnd = {
                                if (committed) return@detectVerticalDragGestures
                                val vy = tracker.calculateVelocity().y
                                val tVel = -vy / container.height.coerceAtLeast(1)
                                when {
                                    mode == PlayerMode.Expanded && (total < -commitPx || vy < -1800f) -> scope.launch {
                                        releaseVelocity = tVel.coerceAtLeast(0.5f)
                                        launch {
                                            animate(
                                                dragScale,
                                                1f,
                                                animationSpec = back
                                            ) { v, _ -> dragScale = v }
                                        }
                                        host.setFullscreen(true)
                                    }

                                    mode == PlayerMode.Expanded && (total > 0.6f * limitPx || vy > 1800f) -> goMini()
                                    mode == PlayerMode.Fullscreen && (fsDy > 0.45f * fsDyMax() || vy > 1800f) -> {
                                        releaseVelocity = tVel.coerceAtMost(-0.5f)
                                        host.setFullscreen(false)   // bulunduğu çerçeveden 16:9'a doğru devam eder
                                    }

                                    else -> settle()
                                }
                            }
                        )
                    }
            ) {
                PlayerSurface(
                    host = host,
                    mode = mode,
                    previewThumbnailUrl = previewThumbnailUrl,
                    isInPipMode = isInPipMode,
                    onMinimize = onMinimize,
                    onClose = onClose
                )
            }
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
    previewThumbnailUrl: String? = null,
    isInPipMode: Boolean,
    onMinimize: () -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val channel = host.channel
    val isLive = channel?.livestream != null
    val playbackUrl = channel?.playbackUrl

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black), contentAlignment = Alignment.Center
    ) {
        if (channel != null && isLive && !playbackUrl.isNullOrBlank()) {
            // PiP parametreleri (sourceRectHint, autoEnter) KickPlayerView içinde yönetiliyor.
            KickPlayerView(
                playbackUrl = playbackUrl,
                previewThumbnailUrl = previewThumbnailUrl,
                channelName = channel.user?.username ?: channel.slug,
                streamTitle = channel.livestream.sessionTitle ?: "Canlı Yayın",
                channel = channel,
                isFullScreen = mode == PlayerMode.Fullscreen,
                isTheaterMode = host.isTheaterMode,
                isChatVisible = host.isChatVisible,
                onToggleChat = host::toggleChatVisibility,
                isInPipMode = isInPipMode,
                isMini = mode == PlayerMode.Mini,
                pendingAutoFullscreen = host.pendingAutoFullscreen,
                onConsumeAutoFullscreen = host::consumeAutoFullscreen,
                onFullScreenChange = host::setFullscreen,
                onToggleTheater = host::toggleTheaterMode,
                onMinimize = onMinimize,
                onClose = onClose,
                onControlsVisibilityChanged = { host.isControlsVisible = it },
                modifier = Modifier.fillMaxSize()
            )
        } else {
            val bannerUrl = remember(channel) { channel?.getBannerUrl(targetWidthPx = 1080) }
            val displayImage = if (!bannerUrl.isNullOrEmpty()) bannerUrl else previewThumbnailUrl
            if (!displayImage.isNullOrEmpty()) {
                AsyncImage(
                    model = remember(displayImage) {
                        buildOptimizedImageRequest(
                            context = context,
                            data = displayImage,
                            targetWidthDp = 640,
                            targetHeightDp = 360
                        )
                    },
                    contentDescription = "Banner",
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