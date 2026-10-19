package com.lechixy.kick

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
class PlayerHostState(initialMode: PlayerMode = PlayerMode.Hidden, initialSlug: String? = null) {
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

    var chatWidthFraction by mutableFloatStateOf(0.35f)

    var animatedChatFraction by mutableFloatStateOf(0.35f)

    var channelHeaderHeightPx by mutableIntStateOf(0)

    var isControlsVisible by mutableStateOf(false)

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

    fun open(slug: String, source: com.lechixy.kick.ui.components.ExpansionSource? = null) {
        if (this.slug != slug) channel = null
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
        channel = detail
        if (detail.livestream == null) {            // yayın yoksa player/mini olmasın
            pendingAutoFullscreen = false
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
        mode = PlayerMode.Hidden
        slug = null
        channel = null
        pageSlug = null
        isTheaterMode = false
        isChatVisible = true
        chatWidthFraction = 0.35f
        animatedChatFraction = 0.35f
        expansionSource = null
    }

    companion object {
        val Saver = listSaver<PlayerHostState, Any?>(
            save = { listOf(it.mode.name, it.slug) },
            restore = { PlayerHostState(PlayerMode.valueOf(it[0] as String), it[1] as String?) }
        )
    }
}

@Composable
fun rememberPlayerHostState(): PlayerHostState =
    rememberSaveable(saver = PlayerHostState.Saver) { PlayerHostState() }