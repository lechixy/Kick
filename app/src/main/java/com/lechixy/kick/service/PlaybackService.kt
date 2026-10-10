package com.lechixy.kick.service

import android.net.Uri
import android.os.Bundle
import androidx.annotation.OptIn
import androidx.core.net.toUri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

class PlaybackService : MediaSessionService() {
    private var mediaSession: MediaSession? = null

    @OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()
        val dataSourceFactory = DefaultHttpDataSource.Factory()
            .setUserAgent("Mozilla/5.0 (Linux; Android 10; SM-G975F) ...")
            .setDefaultRequestProperties(
                mapOf("Origin" to "https://kick.com", "Referer" to "https://kick.com/")
            )

        val player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                    .build(),
                /* handleAudioFocus = */ true
            )
            .setHandleAudioBecomingNoisy(true)
            .build()

        mediaSession = MediaSession.Builder(this, LiveAwarePlayer(player)).build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo) = mediaSession

    override fun onDestroy() {
        mediaSession?.run {
            player.release()
            release()
        }
        mediaSession = null
        super.onDestroy()
    }
}

const val EXTRA_IS_LIVE = "kick_is_live"
const val EXTRA_START_TIME_MS = "kick_start_time_ms"

fun buildPlaybackMediaItem(
    playbackUrl: String,
    isLive: Boolean,
    title: String,
    channelName: String,
    viewersText: String?,
    artwork: Uri,
    startTimeMs: Long = 0L,
    vodDurationMs: Long? = null
): MediaItem {
    val subtitle = buildString {
        append(if (isLive) "🔴 Canlı" else "Kayıt")
        if (!viewersText.isNullOrBlank()) append(" • ").append(viewersText)
    }
    val metadata = MediaMetadata.Builder()
        .setTitle(title)
        .setArtist(channelName)
        .setAlbumArtist(channelName)
        .setAlbumTitle("Kick")
        .setSubtitle(subtitle)
        .setDescription(subtitle)
        .setArtworkUri(artwork)
        .setMediaType(MediaMetadata.MEDIA_TYPE_VIDEO)
        .setIsPlayable(true)
        .setIsBrowsable(false)
        .apply { if (!isLive && vodDurationMs != null && vodDurationMs > 0) setDurationMs(vodDurationMs) }
        .setExtras(Bundle().apply {
            putBoolean(EXTRA_IS_LIVE, isLive)
            putLong(EXTRA_START_TIME_MS, startTimeMs)
        })
        .build()

    return MediaItem.Builder()
        .setMediaId(playbackUrl)
        .setUri(playbackUrl.toUri())
        .setMediaMetadata(metadata)
        .build()
}

@UnstableApi
class LiveAwarePlayer(player: Player) : ForwardingPlayer(player) {

    private val isLiveItem: Boolean
        get() = wrappedPlayer.isCurrentMediaItemLive ||
                wrappedPlayer.currentMediaItem?.mediaMetadata?.extras?.getBoolean(EXTRA_IS_LIVE, false) == true

    private val seekCommands = intArrayOf(
        COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM,
        COMMAND_SEEK_BACK,
        COMMAND_SEEK_FORWARD,
        COMMAND_SEEK_TO_DEFAULT_POSITION
    )

    override fun getDuration(): Long = if (isLiveItem) LIVE_BAR_MS else super.getDuration()
    override fun getContentDuration(): Long = if (isLiveItem) LIVE_BAR_MS else super.getContentDuration()
    override fun getCurrentPosition(): Long = if (isLiveItem) LIVE_BAR_MS else super.getCurrentPosition()
    override fun getContentPosition(): Long = if (isLiveItem) LIVE_BAR_MS else super.getContentPosition()
    override fun getBufferedPosition(): Long = if (isLiveItem) LIVE_BAR_MS else super.getBufferedPosition()
    override fun getContentBufferedPosition(): Long =
        if (isLiveItem) LIVE_BAR_MS else super.getContentBufferedPosition()

    override fun getAvailableCommands(): Player.Commands =
        if (isLiveItem) super.getAvailableCommands().buildUpon().removeAll(*seekCommands).build()
        else super.getAvailableCommands()

    override fun isCommandAvailable(command: Int): Boolean =
        if (isLiveItem && command in seekCommands) false else super.isCommandAvailable(command)

    private companion object {
        const val LIVE_BAR_MS = 1_000L
    }
}
