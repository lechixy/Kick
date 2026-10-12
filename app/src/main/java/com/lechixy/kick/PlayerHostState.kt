package com.lechixy.kick

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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

    val isChannelVisible get() = mode == PlayerMode.Expanded || mode == PlayerMode.Fullscreen

    var dragOffsetY by mutableFloatStateOf(0f)

    fun open(slug: String) {
        if (this.slug != slug) channel = null
        this.slug = slug
        pageSlug = slug
        mode = PlayerMode.Expanded
        dragOffsetY = 0f
    }

    fun onChannelLoaded(slug: String, detail: ChannelDetail) {
        if (slug != this.slug) return
        channel = detail
        if (detail.livestream == null) {            // yayın yoksa player/mini olmasın
            when (mode) {
                PlayerMode.Fullscreen -> mode = PlayerMode.Expanded
                PlayerMode.Mini -> close()
                else -> Unit
            }
        }
    }

    fun setFullscreen(enabled: Boolean) {
        if (enabled && mode == PlayerMode.Expanded) mode = PlayerMode.Fullscreen
        else if (!enabled && mode == PlayerMode.Fullscreen) mode = PlayerMode.Expanded
    }

    fun minimize() {
        if (slug == null || mode == PlayerMode.Hidden) return
        val offline = channel != null && channel?.livestream == null
        if (offline) close() else mode = PlayerMode.Mini   // yayın yokken mini'ye geçme, direkt kapan
    }

    fun expand() {
        if (slug != null) mode = PlayerMode.Expanded
        dragOffsetY = 0f
    }

    fun close() {
        mode = PlayerMode.Hidden
        slug = null
        channel = null
        pageSlug = null
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