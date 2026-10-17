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
import androidx.media3.exoplayer.DefaultLivePlaybackSpeedControl
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.drm.DrmSessionManagerProvider
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.hls.playlist.DefaultHlsPlaylistParserFactory
import androidx.media3.exoplayer.hls.playlist.HlsMediaPlaylist
import androidx.media3.exoplayer.hls.playlist.HlsMultivariantPlaylist
import androidx.media3.exoplayer.hls.playlist.HlsPlaylist
import androidx.media3.exoplayer.hls.playlist.HlsPlaylistParserFactory
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy
import androidx.media3.exoplayer.upstream.ParsingLoadable
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.lechixy.kick.data.local.PlayerSettingsManager
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.util.Locale

class PlaybackService : MediaSessionService() {
    private var mediaSession: MediaSession? = null

    @OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()
        PlayerSettingsManager.init(this)

        val dataSourceFactory = DefaultHttpDataSource.Factory()
            .setUserAgent(
                "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 " +
                    "(KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"
            )
            .setConnectTimeoutMs(8000)
            .setReadTimeoutMs(8000)
            .setAllowCrossProtocolRedirects(true)
            .setDefaultRequestProperties(
                mapOf("Origin" to "https://kick.com", "Referer" to "https://kick.com/")
            )

        // 2sn'lik HLS segmentlerinde tek segmentle bile takılmadan oynatmaya devam edebilmesi için
        // bufferForPlaybackAfterRebufferMs değeri 2000ms altında (1200ms) tutulmalıdır.
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                /* minBufferMs = */ 2500,
                /* maxBufferMs = */ 10000,
                /* bufferForPlaybackMs = */ 600,
                /* bufferForPlaybackAfterRebufferMs = */ 1200
            )
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()

        val liveSpeedControl = DefaultLivePlaybackSpeedControl.Builder()
            .setFallbackMinPlaybackSpeed(0.97f)
            .setFallbackMaxPlaybackSpeed(1.03f)
            .setTargetLiveOffsetIncrementOnRebufferMs(200L)
            .build()

        val hlsParserFactory = KickHlsPlaylistParserFactory {
            PlayerSettingsManager.lowLatencyEnabled.value
        }

        val hlsMediaSourceFactory = HlsMediaSource.Factory(dataSourceFactory)
            .setPlaylistParserFactory(hlsParserFactory)
            .setAllowChunklessPreparation(true)

        val defaultMediaSourceFactory = DefaultMediaSourceFactory(dataSourceFactory)

        val mediaSourceFactory = object : MediaSource.Factory {
            override fun setDrmSessionManagerProvider(
                drmSessionManagerProvider: DrmSessionManagerProvider
            ): MediaSource.Factory {
                hlsMediaSourceFactory.setDrmSessionManagerProvider(drmSessionManagerProvider)
                defaultMediaSourceFactory.setDrmSessionManagerProvider(drmSessionManagerProvider)
                return this
            }

            override fun setLoadErrorHandlingPolicy(
                loadErrorHandlingPolicy: LoadErrorHandlingPolicy
            ): MediaSource.Factory {
                hlsMediaSourceFactory.setLoadErrorHandlingPolicy(loadErrorHandlingPolicy)
                defaultMediaSourceFactory.setLoadErrorHandlingPolicy(loadErrorHandlingPolicy)
                return this
            }

            override fun getSupportedTypes(): IntArray = defaultMediaSourceFactory.supportedTypes

            override fun createMediaSource(mediaItem: MediaItem): MediaSource {
                val path = mediaItem.localConfiguration?.uri?.path.orEmpty()
                return if (path.endsWith(".mp4", ignoreCase = true)) {
                    defaultMediaSourceFactory.createMediaSource(mediaItem)
                } else {
                    hlsMediaSourceFactory.createMediaSource(mediaItem)
                }
            }
        }

        val player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(mediaSourceFactory)
            .setLoadControl(loadControl)
            .setLivePlaybackSpeedControl(liveSpeedControl)
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

/**
 * Kick (Amazon IVS) canlı yayınlarında düşük gecikme (#EXT-X-PREFETCH / LHLS) etiketlerini
 * ExoPlayer'ın anlayacağı standart #EXTINF segmentine dönüştürür ve #EXT-X-TARGETDURATION:6
 * kaynaklı 6 saniyelik manifest yenileme gecikmesini 2-3 saniyeye indirir.
 */
@UnstableApi
private class KickHlsPlaylistParserFactory(
    private val isLowLatencyEnabled: () -> Boolean
) : HlsPlaylistParserFactory {

    private val delegate = DefaultHlsPlaylistParserFactory()

    override fun createPlaylistParser(): ParsingLoadable.Parser<HlsPlaylist> =
        KickHlsPlaylistParser(delegate.createPlaylistParser(), isLowLatencyEnabled)

    override fun createPlaylistParser(
        multivariantPlaylist: HlsMultivariantPlaylist,
        previousMediaPlaylist: HlsMediaPlaylist?
    ): ParsingLoadable.Parser<HlsPlaylist> =
        KickHlsPlaylistParser(
            delegate.createPlaylistParser(multivariantPlaylist, previousMediaPlaylist),
            isLowLatencyEnabled
        )
}

@UnstableApi
private class KickHlsPlaylistParser(
    private val delegate: ParsingLoadable.Parser<HlsPlaylist>,
    private val isLowLatencyEnabled: () -> Boolean
) : ParsingLoadable.Parser<HlsPlaylist> {

    override fun parse(uri: Uri, inputStream: InputStream): HlsPlaylist {
        val rawBytes = inputStream.readBytes()
        val text = rawBytes.toString(Charsets.UTF_8)

        val isLiveMediaPlaylist = text.contains("#EXTINF:") &&
            !text.contains("#EXT-X-ENDLIST") &&
            !text.contains("#EXT-X-PLAYLIST-TYPE:VOD")

        if (!isLiveMediaPlaylist) {
            return delegate.parse(uri, ByteArrayInputStream(rawBytes))
        }

        val transformedText = transformLiveMediaPlaylist(text, isLowLatencyEnabled())
        return delegate.parse(
            uri,
            ByteArrayInputStream(transformedText.toByteArray(Charsets.UTF_8))
        )
    }

    private fun transformLiveMediaPlaylist(text: String, lowLatency: Boolean): String {
        val lines = text.lineSequence().toList()

        // Yayındaki gerçek segment süresini bul (Kick yayınlarında genellikle 2.000sn veya 4.167sn).
        var lastDurationRaw = "2.000"
        var lastDurationSec = 2.0
        for (line in lines) {
            if (line.startsWith("#EXTINF:")) {
                val durPart = line.substringAfter("#EXTINF:").substringBefore(",").trim()
                val parsed = durPart.toDoubleOrNull()
                if (parsed != null && parsed > 0.0) {
                    lastDurationRaw = durPart
                    lastDurationSec = parsed
                }
            }
        }

        // Kick her zaman #EXT-X-TARGETDURATION:6 gönderdiği için ExoPlayer manifesti 6 saniyede bir
        // yeniliyor. Bunu segment süresine göre 2 veya 3 saniyeye çekerek takılmayı önlüyoruz.
        val targetDurationInt = if (lastDurationSec <= 2.5) 2 else 3

        // Başlangıç konumunu (#EXT-X-START:TIME-OFFSET) segment süresine göre belirle:
        // Low latency açıkken 1.5 segment geriden başlar (son tamamlanmış CDN segmentinin tam
        // keyframe başından başlar, bu sırada aktif #EXT-X-PREFETCH segmenti arkadan akmaya devam eder).
        val startOffsetMultiplier = if (lowLatency) 1.5 else 2.5
        val startOffsetSec = lastDurationSec * startOffsetMultiplier
        val hasStartTag = lines.any { it.startsWith("#EXT-X-START:") }

        val sb = StringBuilder(text.length + 256)
        var prefetchConverted = false
        var startTagInjected = hasStartTag

        for (rawLine in lines) {
            val line = rawLine.trimEnd('\r')
            when {
                line == "#EXTM3U" -> {
                    sb.append(line).append('\n')
                    if (!startTagInjected) {
                        sb.append(
                            String.format(
                                Locale.US,
                                "#EXT-X-START:TIME-OFFSET=-%.3f\n",
                                startOffsetSec
                            )
                        )
                        startTagInjected = true
                    }
                }

                line.startsWith("#EXT-X-TARGETDURATION:") -> {
                    sb.append("#EXT-X-TARGETDURATION:").append(targetDurationInt).append('\n')
                }

                line.startsWith("#EXT-X-PREFETCH:") -> {
                    // İlk #EXT-X-PREFETCH satırı o an sunucuda kodlanmakta olan canlı (chunked)
                    // segmenttir. Low latency açıkken bunu aktif segment olarak ekliyoruz;
                    // henüz başlamamış olan ikinci prefetch satırını ise atlıyoruz.
                    if (lowLatency && !prefetchConverted) {
                        val prefetchUrl = line.substringAfter("#EXT-X-PREFETCH:").trim()
                        if (prefetchUrl.isNotEmpty()) {
                            sb.append("#EXTINF:").append(lastDurationRaw).append(",live\n")
                            sb.append(prefetchUrl).append('\n')
                            prefetchConverted = true
                        }
                    }
                }

                else -> {
                    sb.append(line).append('\n')
                }
            }
        }

        return sb.toString()
    }
}

const val EXTRA_IS_LIVE = "kick_is_live"
const val EXTRA_START_TIME_MS = "kick_start_time_ms"
const val EXTRA_LOW_LATENCY = "kick_low_latency"

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
    isLowLatency: Boolean = true
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
            putBoolean(EXTRA_LOW_LATENCY, isLowLatency)
        })
        .build()

    val builder = MediaItem.Builder()
        .setMediaId(mediaId)
        .setUri(playbackUrl.toUri())
        .setMediaMetadata(metadata)

    if (isLive) {
        // Target offset doğrudan manifest içine enjekte edilen #EXT-X-START:TIME-OFFSET
        // üzerinden yayıncının segment süresine (2sn veya 4.167sn) göre dinamik hesaplanır.
        val liveConfig = MediaItem.LiveConfiguration.Builder()
            .setMinPlaybackSpeed(0.97f)
            .setMaxPlaybackSpeed(if (isLowLatency) 1.03f else 1.02f)
            .build()
        builder.setLiveConfiguration(liveConfig)
    }

    return builder.build()
}

@UnstableApi
class LiveAwarePlayer(player: Player) : ForwardingPlayer(player)

