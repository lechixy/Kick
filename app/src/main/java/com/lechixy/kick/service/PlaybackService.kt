package com.lechixy.kick.service

import android.content.Intent
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
import androidx.media3.exoplayer.DefaultLoadControl
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

        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                /* minBufferMs = */ 8000,
                /* maxBufferMs = */ 30000,
                /* bufferForPlaybackMs = */ 2500,
                /* bufferForPlaybackAfterRebufferMs = */ 3500
            )
            .build()

        val player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
            .setLoadControl(loadControl)
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

    @OptIn(UnstableApi::class)
    override fun onTaskRemoved(rootIntent: Intent?) {
        pauseAllPlayersAndStopSelf()   // Media3: tüm player'ları durdurup servisi kapatır
    }
}

const val EXTRA_IS_LIVE = "kick_is_live"
const val EXTRA_START_TIME_MS = "kick_start_time_ms"

@OptIn(UnstableApi::class)
fun buildPlaybackMediaItem(
    playbackUrl: String,
    mediaId: String,
    isLive: Boolean,
    title: String,
    channelName: String,
    viewersText: String?,
    artwork: Uri,
    startTimeMs: Long = 0L,
    vodDurationMs: Long? = null,
    isLowLatency: Boolean = false
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
        .apply {
            if (!isLive && vodDurationMs != null && vodDurationMs > 0) setDurationMs(vodDurationMs)
        }
        .setExtras(Bundle().apply {
            putBoolean(EXTRA_IS_LIVE, isLive)
            putLong(EXTRA_START_TIME_MS, startTimeMs)
        })
        .build()

    val builder = MediaItem.Builder()
        .setMediaId(mediaId)
        .setUri(playbackUrl.toUri())
        .setMediaMetadata(metadata)

    if (isLive) {
        val targetOffset = if (isLowLatency) 2500L else 5000L
        val minOffset = if (isLowLatency) 1200L else 2500L
        val maxOffset = if (isLowLatency) 6000L else 12000L

        val liveConfig = MediaItem.LiveConfiguration.Builder()
            .setTargetOffsetMs(targetOffset)
            .setMinOffsetMs(minOffset)
            .setMaxOffsetMs(maxOffset)
            .setMinPlaybackSpeed(0.97f)
            .setMaxPlaybackSpeed(1.03f)
            .build()
        builder.setLiveConfiguration(liveConfig)
    }

    return builder.build()
}

@UnstableApi
class LiveAwarePlayer(player: Player) : ForwardingPlayer(player)
