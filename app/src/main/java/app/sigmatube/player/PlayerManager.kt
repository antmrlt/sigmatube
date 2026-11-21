package app.sigmatube.player

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MimeTypes
import android.net.Uri
import java.util.Locale
import app.sigmatube.data.SubtitleTrack
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.dash.DashMediaSource
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.exoplayer.source.SingleSampleMediaSource
import app.sigmatube.data.PlaybackSource
import app.sigmatube.data.StreamOptions
import app.sigmatube.data.VideoDetails
import app.sigmatube.data.extractor.OkHttpDownloader
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper

/**
 * Owns the single app-wide ExoPlayer. Keeping it outside of any screen is what will let us
 * add background playback / a mini-player later without reworking the UI.
 */
@OptIn(UnstableApi::class)
class PlayerManager(context: Context) {

    /**
     * googlevideo.com rejects (403) stream requests whose User-Agent doesn't match the client
     * the URL was issued for, so we pick the header per request instead of one global UA.
     */
    private val httpFactory = DefaultHttpDataSource.Factory().setAllowCrossProtocolRedirects(true)

    private val dataSourceFactory = ResolvingDataSource.Factory(httpFactory) { spec ->
        spec.withAdditionalHeaders(headersFor(spec.uri.toString()))
    }

    /** Progressive streams: same headers, but fetched in bounded chunks (see [ChunkedDataSource]). */
    private val progressiveFactory = ResolvingDataSource.Factory(ChunkedDataSource.Factory(httpFactory)) { spec ->
        spec.withAdditionalHeaders(headersFor(spec.uri.toString()))
    }

    private fun headersFor(url: String): Map<String, String> = when {
        YoutubeParsingHelper.isVisionOsStreamingUrl(url) ->
            mapOf("User-Agent" to YoutubeParsingHelper.getVisionOsUserAgent(NewPipe.getPreferredLocalization()))
        YoutubeParsingHelper.isWebStreamingUrl(url) -> mapOf(
            "User-Agent" to OkHttpDownloader.USER_AGENT,
            "Origin" to "https://www.youtube.com",
            "Referer" to "https://www.youtube.com/",
        )
        else -> mapOf("User-Agent" to OkHttpDownloader.USER_AGENT)
    }

    val player: ExoPlayer = ExoPlayer.Builder(context)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                .build(),
            /* handleAudioFocus = */ true,
        )
        .setHandleAudioBecomingNoisy(true)
        .setWakeMode(C.WAKE_MODE_NETWORK)
        .setSeekBackIncrementMs(SEEK_INCREMENT_MS)
        .setSeekForwardIncrementMs(SEEK_INCREMENT_MS)
        .build()

    /** URL of the video currently loaded in the player, if any. */
    var currentUrl: String? = null
        private set

    /** Label of the quality actually playing (e.g. "1080p"), null for live/adaptive. */
    var currentQualityLabel: String? = null
        private set

    /**
     * Whether the current video may keep playing outside the app (music, or per settings).
     * When false the service shows no media notification at all.
     */
    @Volatile var backgroundAllowed: Boolean = false

    /** Whoever started the current playback; only they may stop/pause it (guards against stale screens). */
    private var owner: Any? = null

    fun play(
        owner: Any,
        video: VideoDetails,
        startPositionMs: Long = 0,
        preferredHeight: Int? = null,
        autoMax: Int = StreamOptions.AUTO_MAX_HEIGHT,
    ) {
        val source = video.streams.resolve(preferredHeight, autoMax)
        val mediaSource = buildMediaSource(video, source) ?: return
        this.owner = owner
        currentUrl = video.url
        currentQualityLabel = when (source) {
            is PlaybackSource.Merged -> source.label
            is PlaybackSource.Progressive -> source.label
            else -> null
        }
        player.setMediaSource(mediaSource, startPositionMs)
        player.prepare()
        player.playWhenReady = true
    }

    /** A new screen/ViewModel takes over the playback that is already running (app reopened). */
    fun adopt(owner: Any) {
        this.owner = owner
    }

    fun stop(owner: Any) {
        if (owner !== this.owner) return
        player.stop()
        player.clearMediaItems()
        currentUrl = null
        this.owner = null
    }

    fun pause(owner: Any) {
        if (owner === this.owner) player.pause()
    }

    private fun buildMediaSource(video: VideoDetails, source: PlaybackSource): MediaSource? {
        val metadata = MediaMetadata.Builder()
            .setTitle(video.title)
            .setArtist(video.channelName)
            .setArtworkUri(video.thumbnail?.let { android.net.Uri.parse(it) })
            .build()

        fun item(uri: String) = MediaItem.Builder()
            .setUri(uri)
            .setMediaId(video.url)
            .setMediaMetadata(metadata)
            .build()

        val progressive = ProgressiveMediaSource.Factory(progressiveFactory)
        val main = when (source) {
            is PlaybackSource.Merged -> MergingMediaSource(
                /* adjustPeriodTimeOffsets = */ true,
                /* clipDurations = */ true,
                progressive.createMediaSource(item(source.videoUrl)),
                progressive.createMediaSource(item(source.audioUrl)),
            )
            is PlaybackSource.Progressive -> progressive.createMediaSource(item(source.url))
            is PlaybackSource.Hls -> HlsMediaSource.Factory(dataSourceFactory).createMediaSource(item(source.url))
            is PlaybackSource.Dash -> DashMediaSource.Factory(dataSourceFactory).createMediaSource(item(source.url))
            PlaybackSource.Unavailable -> null
        } ?: return null
        val caption = pickSubtitle(video) ?: return main
        val subtitleConfig = MediaItem.SubtitleConfiguration.Builder(Uri.parse(caption.url))
            .setMimeType(MimeTypes.TEXT_VTT)
            .setLanguage(caption.languageTag)
            .setLabel(caption.label)
            .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
            .build()
        val subtitleSource = SingleSampleMediaSource.Factory(dataSourceFactory).createMediaSource(subtitleConfig, C.TIME_UNSET)
        return MergingMediaSource(true, true, main, subtitleSource)
    }

    /** Device language first (human-made before auto-generated), then English, then anything. */
    private fun pickSubtitle(video: VideoDetails): SubtitleTrack? {
        val lang = Locale.getDefault().language
        val tracks = video.subtitles
        return tracks.firstOrNull { it.languageTag.startsWith(lang) && !it.autoGenerated }
            ?: tracks.firstOrNull { it.languageTag.startsWith("en") && !it.autoGenerated }
            ?: tracks.firstOrNull { !it.autoGenerated }
            ?: tracks.firstOrNull { it.languageTag.startsWith(lang) }
            ?: tracks.firstOrNull()
    }

    fun setCaptionsEnabled(enabled: Boolean) {
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, !enabled)
            .build()
    }

    private companion object {
        const val SEEK_INCREMENT_MS = 10_000L
    }
}
