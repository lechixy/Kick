package com.lechixy.kick

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.lechixy.kick.data.model.ChannelDetail
import kotlin.math.roundToInt

/**
 * Hidden     : oturum yok (player hiç yok, PiP kapalı, normal uygulama)
 * Expanded   : ChannelScreen açık, player sayfanın üstünde (16:9)
 * Fullscreen : ChannelScreen açık, player tam ekran
 * Mini       : ChannelScreen kapalı, player herhangi bir ekranın üstünde yüzen mini pencere
 */
enum class PlayerMode { Hidden, Expanded, Fullscreen, Mini }

/**
 * Player oturumunun TEK doğruluk kaynağı. KickApp'te yaşar, NavHost'un ÜSTÜNDEKİ PlayerHost'u besler.
 * Player artık ChannelScreen'in içinde değil; bu yüzden ekran değişse de yayın durmaz.
 */
@Stable
class PlayerHostState(
    initialMode: PlayerMode = PlayerMode.Hidden,
    initialSlug: String? = null,
    initialChatWidthFraction: Float = 0.35f
) {
    var mode by mutableStateOf(initialMode)
        private set
    var slug by mutableStateOf(initialSlug)
        private set
    var pageSlug by mutableStateOf(initialSlug)   // sayfada gösterilen kanal (oturumdan farklı olabilir)
        private set

    /** Son yüklenen kanal detayı: ChannelScreen kapansa (VM ölse) bile mini player bununla yaşar. */
    var channel by mutableStateOf<ChannelDetail?>(null)
        private set

    var isExiting by mutableStateOf(false)
        private set

    val isChannelVisible get() = (mode == PlayerMode.Expanded || mode == PlayerMode.Fullscreen) && !isExiting

    var dragOffsetY by mutableFloatStateOf(0f)

    var pendingAutoFullscreen by mutableStateOf(false)
        private set

    var isTheaterMode by mutableStateOf(false)

    var isChatVisible by mutableStateOf(true)

    var chatWidthFraction by mutableFloatStateOf(initialChatWidthFraction.coerceIn(0.18f, 0.60f))

    /**
     * Yan sohbet panelinin açılma oranı (0f = tamamen kapalı, 1f = tamamen açık).
     * Sadece layout ve draw fazlarında okunur -> sürükleme/animasyon sırasında recomposition olmaz,
     * PlayerHost ve ChannelScreen aynı karede aynı değeri görür (1 karelik SideEffect gecikmesi yok).
     */
    val chatOpenProgress = Animatable(0f, visibilityThreshold = 0.0005f)

    var isDirectChatDrag by mutableStateOf(false)

    var channelHeaderHeightPx by mutableIntStateOf(0)

    var isControlsVisible by mutableStateOf(false)

    fun effectiveChatFraction(hasSideChat: Boolean): Float {
        if (!hasSideChat) return 0f
        return (chatWidthFraction * chatOpenProgress.value.coerceIn(0f, 1f)).coerceIn(0f, 0.60f)
    }

    fun sideChatWidthPx(totalWidthPx: Int, hasSideChat: Boolean): Int {
        if (!hasSideChat || totalWidthPx <= 0) return 0
        return (totalWidthPx * effectiveChatFraction(hasSideChat = true)).roundToInt()
    }

    suspend fun snapChatOpenProgress(progress: Float) {
        chatOpenProgress.snapTo(progress.coerceIn(0f, 1f))
    }

    suspend fun animateChatOpenProgressTo(target: Float, initialVelocity: Float = 0f) {
        chatOpenProgress.animateTo(
            targetValue = target.coerceIn(0f, 1f),
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessMediumLow,
                visibilityThreshold = 0.0005f
            ),
            initialVelocity = initialVelocity
        )
    }

    fun isSideChatOpen(isWide: Boolean): Boolean {
        return if (mode == PlayerMode.Fullscreen) {
            isTheaterMode && isChatVisible
        } else {
            isWide && isChatVisible
        }
    }

    fun setSideChatVisible(visible: Boolean, isWide: Boolean) {
        if (mode == PlayerMode.Fullscreen) {
            if (visible) {
                if (!isTheaterMode || !isChatVisible) {
                    isTheaterMode = true
                    isChatVisible = true
                }
            } else {
                if (isTheaterMode || isChatVisible) {
                    isChatVisible = false
                    isTheaterMode = false
                }
            }
        } else if (isWide) {
            if (isChatVisible != visible) {
                isChatVisible = visible
            }
        }
    }

    fun toggleTheaterMode() {
        isTheaterMode = !isTheaterMode
        if (isTheaterMode) {
            isChatVisible = true
        }
    }

    fun toggleChatVisibility() {
        if (mode == PlayerMode.Fullscreen) {
            if (isTheaterMode && isChatVisible) {
                isChatVisible = false
                isTheaterMode = false
            } else {
                isTheaterMode = true
                isChatVisible = true
            }
        } else {
            isChatVisible = !isChatVisible
        }
    }

    fun updateChatWidthFraction(fraction: Float) {
        chatWidthFraction = fraction.coerceIn(0.18f, 0.60f)
    }

    var expansionSource by mutableStateOf<com.lechixy.kick.ui.components.ExpansionSource?>(null)
        private set

    var activeLiveAnnouncementId by mutableStateOf<Long?>(null)
        private set

    fun triggerLiveAnnouncement(announcementId: Long) {
        activeLiveAnnouncementId = announcementId
    }

    fun clearLiveAnnouncement(announcementId: Long) {
        if (activeLiveAnnouncementId == announcementId) {
            activeLiveAnnouncementId = null
        }
    }

    fun open(slug: String, source: com.lechixy.kick.ui.components.ExpansionSource? = null) {
        if (this.slug != slug) {
            channel = null
            activeLiveAnnouncementId = null
        }
        this.slug = slug
        pageSlug = slug
        this.expansionSource = if (mode == PlayerMode.Hidden) source else null
        isExiting = false
        mode = PlayerMode.Expanded
        dragOffsetY = 0f
        pendingAutoFullscreen = true
    }

    fun clearExpansionSource() {
        expansionSource = null
    }

    fun onChannelLoaded(slug: String, detail: ChannelDetail) {
        if (slug != this.slug) return
        if (channel != detail) {
            channel = detail
        }
        if (detail.livestream == null) {            // yayın yoksa player/mini olmasın
            pendingAutoFullscreen = false
            activeLiveAnnouncementId = null
            isTheaterMode = false
            when (mode) {
                PlayerMode.Fullscreen -> mode = PlayerMode.Expanded
                PlayerMode.Mini -> close()
                else -> Unit
            }
        }
    }

    fun consumeAutoFullscreen(enterFullscreen: Boolean) {
        if (!pendingAutoFullscreen) return
        pendingAutoFullscreen = false
        if (enterFullscreen && mode == PlayerMode.Expanded && channel?.livestream != null) {
            mode = PlayerMode.Fullscreen
        }
    }

    fun setFullscreen(enabled: Boolean) {
        pendingAutoFullscreen = false
        if (enabled && mode == PlayerMode.Expanded) mode = PlayerMode.Fullscreen
        else if (!enabled && mode == PlayerMode.Fullscreen) mode = PlayerMode.Expanded
    }

    fun requestClose() {
        if (mode == PlayerMode.Hidden || isExiting) return
        isExiting = true
    }

    fun finishClose() {
        isExiting = false
        close()
    }

    fun minimize() {
        pendingAutoFullscreen = false
        if (slug == null || mode == PlayerMode.Hidden) return
        val offline = channel == null || channel?.livestream == null
        if (offline) requestClose() else mode = PlayerMode.Mini   // yayın yokken mini'ye geçme, direkt kapan
    }

    fun expand() {
        if (slug != null) mode = PlayerMode.Expanded
        dragOffsetY = 0f
    }

    fun close() {
        isExiting = false
        pendingAutoFullscreen = false
        activeLiveAnnouncementId = null
        mode = PlayerMode.Hidden
        slug = null
        channel = null
        pageSlug = null
        isTheaterMode = false
        isChatVisible = true
        isDirectChatDrag = false
        expansionSource = null
    }

    companion object {
        val Saver = listSaver<PlayerHostState, Any?>(
            save = { listOf(it.mode.name, it.slug, it.chatWidthFraction) },
            restore = {
                PlayerHostState(
                    initialMode = PlayerMode.valueOf(it[0] as String),
                    initialSlug = it[1] as String?,
                    initialChatWidthFraction = (it.getOrNull(2) as? Float) ?: 0.35f
                )
            }
        )
    }
}

@Composable
fun rememberPlayerHostState(): PlayerHostState =
    rememberSaveable(saver = PlayerHostState.Saver) { PlayerHostState() }