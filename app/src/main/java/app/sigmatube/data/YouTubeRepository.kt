package app.sigmatube.data

import androidx.core.text.HtmlCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.Image
import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.MediaFormat
import org.schabi.newpipe.extractor.Page
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.channel.ChannelInfo
import org.schabi.newpipe.extractor.comments.CommentsInfo
import org.schabi.newpipe.extractor.comments.CommentsInfoItem
import org.schabi.newpipe.extractor.channel.tabs.ChannelTabInfo
import org.schabi.newpipe.extractor.channel.tabs.ChannelTabs
import org.schabi.newpipe.extractor.linkhandler.ListLinkHandler
import org.schabi.newpipe.extractor.playlist.PlaylistInfo
import org.schabi.newpipe.extractor.search.SearchInfo
import org.schabi.newpipe.extractor.services.youtube.linkHandler.YoutubeSearchQueryHandlerFactory
import org.schabi.newpipe.extractor.stream.AudioTrackType
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.stream.Description
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.StreamType
import java.util.concurrent.ConcurrentHashMap

/**
 * Single entry point to YouTube data. Everything NewPipe-specific stays in here so the UI
 * only ever sees our own models.
 */
class YouTubeRepository {

    private val service = ServiceList.YouTube

    /** Trending categories (Music, Gaming, Movies…), without the live one. */
    suspend fun categories(): List<FeedCategory> = io {
        service.kioskList.availableKiosks
            .filterNot { it.contains("live", ignoreCase = true) }
            .map { FeedCategory(it, categoryLabel(it)) }
            .sortedBy { CATEGORY_ORDER.indexOfFirst { key -> it.id.contains(key, true) }.let { i -> if (i < 0) 99 else i } }
    }

    suspend fun category(id: String): List<VideoItem> = io {
        val extractor = service.kioskList.getExtractorById(id, null)
        extractor.fetchPage()
        extractor.initialPage.items.toVideoItems()
    }

    /** "Tout" when signed out: every category interleaved, lives excluded. */
    suspend fun discover(): List<VideoItem> = coroutineScope {
        val lists = categories().map { c -> async { runCatching { category(c.id) }.getOrDefault(emptyList()) } }.awaitAll()
        val mixed = mutableListOf<VideoItem>()
        val max = lists.maxOfOrNull { it.size } ?: 0
        for (i in 0 until max) lists.forEach { list -> list.getOrNull(i)?.let(mixed::add) }
        if (mixed.isEmpty()) error("Aucune vidéo disponible pour le moment")
        mixed.distinctBy { it.url }
    }

    private fun categoryLabel(id: String): String = when {
        id.contains("music", true) -> "Musique"
        id.contains("gaming", true) -> "Jeux vidéo"
        id.contains("movie", true) -> "Films"
        id.contains("podcast", true) -> "Podcasts"
        id.contains("trending", true) -> "Tendances"
        else -> id.replace('_', ' ').replaceFirstChar { it.uppercase() }
    }

    suspend fun suggestions(query: String): List<String> = io {
        if (query.isBlank()) emptyList() else service.suggestionExtractor.suggestionList(query)
    }

    suspend fun search(query: String, page: Page? = null): PagedResult<VideoItem> = io {
        val handler = service.searchQHFactory.fromQuery(
            query, listOf(YoutubeSearchQueryHandlerFactory.VIDEOS), "",
        )
        if (page == null) {
            val info = SearchInfo.getInfo(service, handler)
            PagedResult(info.relatedItems.toVideoItems(), info.nextPage)
        } else {
            val more = SearchInfo.getMoreItems(service, handler, page)
            PagedResult(more.items.toVideoItems(), more.nextPage)
        }
    }

    suspend fun video(url: String): VideoDetails = io {
        val info = StreamInfo.getInfo(service, url)
        VideoDetails(
            url = info.url,
            title = info.name,
            description = info.description.toPlainText(),
            thumbnail = info.thumbnails.best(),
            channelName = info.uploaderName,
            channelUrl = info.uploaderUrl,
            channelAvatar = info.uploaderAvatars.best(),
            channelVerified = info.isUploaderVerified,
            subscriberCount = info.uploaderSubscriberCount,
            viewCount = info.viewCount,
            likeCount = info.likeCount,
            uploadDate = info.uploadDate?.offsetDateTime(),
            uploadedText = info.textualUploadDate,
            durationSeconds = info.duration,
            isLive = info.streamType.isLive(),
            related = info.relatedItems.toVideoItems(),
            streams = streamOptions(info),
            subtitles = info.subtitles
                .filter { it.isUrl && it.format == MediaFormat.VTT }
                .map { SubtitleTrack(it.content, it.languageTag, it.displayLanguageName, it.isAutoGenerated) },
        )
    }

    suspend fun comments(videoUrl: String, page: Page? = null): CommentsPage = io {
        if (page == null) {
            val info = CommentsInfo.getInfo(service, videoUrl)
            CommentsPage(
                comments = info.relatedItems.map { it.toComment() },
                nextPage = info.nextPage,
                totalCount = info.commentsCount,
                disabled = info.isCommentsDisabled,
            )
        } else {
            val more = CommentsInfo.getMoreItems(service, videoUrl, page)
            CommentsPage(more.items.map { it.toComment() }, more.nextPage, -1, false)
        }
    }

    suspend fun channel(url: String): ChannelDetails = io {
        val info = ChannelInfo.getInfo(service, url)
        info.tabs.firstOrNull { ChannelTabs.VIDEOS in it.contentFilters }?.let { tabCache[url] = it }
        ChannelDetails(
            id = info.id,
            url = info.url,
            name = info.name,
            avatar = info.avatars.best(),
            banner = info.banners.best(),
            subscriberCount = info.subscriberCount,
            description = info.description.orEmpty(),
        )
    }

    suspend fun channelVideos(url: String, page: Page? = null): PagedResult<VideoItem> = io {
        val tab = videosTab(url) ?: return@io PagedResult(emptyList(), null)
        if (page == null) {
            val info = ChannelTabInfo.getInfo(service, tab)
            PagedResult(info.relatedItems.toVideoItems(), info.nextPage)
        } else {
            val more = ChannelTabInfo.getMoreItems(service, tab, page)
            PagedResult(more.items.toVideoItems(), more.nextPage)
        }
    }

    suspend fun playlist(url: String): Pair<PlaylistDetails, PagedResult<VideoItem>> = io {
        val info = PlaylistInfo.getInfo(service, url)
        PlaylistDetails(
            url = info.url,
            title = info.name,
            uploader = info.uploaderName,
            thumbnail = info.thumbnails.best(),
            videoCount = info.streamCount,
        ) to PagedResult(info.relatedItems.toVideoItems(), info.nextPage)
    }

    suspend fun playlistMore(url: String, page: Page): PagedResult<VideoItem> = io {
        val more = PlaylistInfo.getMoreItems(service, url, page)
        PagedResult(more.items.toVideoItems(), more.nextPage)
    }

    private val tabCache = ConcurrentHashMap<String, ListLinkHandler>()

    private fun videosTab(channelUrl: String): ListLinkHandler? =
        tabCache[channelUrl] ?: ChannelInfo.getInfo(service, channelUrl).tabs
            .firstOrNull { ChannelTabs.VIDEOS in it.contentFilters }
            ?.also { tabCache[channelUrl] = it }

    // region mapping

    private fun List<InfoItem>.toVideoItems(): List<VideoItem> =
        filterIsInstance<StreamInfoItem>().map { it.toVideoItem() }.distinctBy { it.url }

    private fun StreamInfoItem.toVideoItem() = VideoItem(
        url = url,
        title = name,
        thumbnail = thumbnails.best(),
        channelName = uploaderName,
        channelUrl = uploaderUrl,
        channelAvatar = uploaderAvatars.best(),
        durationSeconds = duration,
        viewCount = viewCount,
        uploadDate = uploadDate?.offsetDateTime(),
        uploadedText = textualUploadDate,
        isLive = streamType.isLive(),
    )

    private fun CommentsInfoItem.toComment() = Comment(
        id = commentId ?: url.orEmpty(),
        author = uploaderName.orEmpty(),
        authorAvatar = uploaderAvatars.best(),
        text = commentText.toPlainText(),
        likeCount = likeCount,
        replyCount = replyCount,
        uploadDate = uploadDate?.offsetDateTime(),
        uploadedText = textualUploadDate,
        isPinned = isPinned,
        isHearted = isHeartedByUploader,
    )

    private fun List<Image>.best(): String? =
        maxByOrNull { it.height }?.url ?: lastOrNull()?.url

    private fun StreamType.isLive() =
        this == StreamType.LIVE_STREAM || this == StreamType.AUDIO_LIVE_STREAM

    private fun Description?.toPlainText(): String = when {
        this == null -> ""
        type() == Description.Type.HTML ->
            HtmlCompat.fromHtml(content(), HtmlCompat.FROM_HTML_MODE_COMPACT).toString().trim()
        else -> content().orEmpty().trim()
    }

    // endregion

    /**
     * Collects every playable stream. Video-only streams are grouped per resolution, preferring
     * H.264 (decodes everywhere), then VP9; AV1 is skipped since many devices can't decode it.
     */
    private fun streamOptions(info: StreamInfo): StreamOptions {
        val qualities = info.videoOnlyStreams
            .filter {
                it.isUrl && it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP && it.height > 0 &&
                    (it.format == MediaFormat.MPEG_4 || it.format == MediaFormat.WEBM) &&
                    it.codec?.startsWith("av01") != true
            }
            .groupBy { it.height to (it.fps > 30) }
            .map { (_, streams) ->
                val best = streams.firstOrNull { it.codec?.startsWith("avc1") == true } ?: streams.first()
                VideoQuality(best.height, best.fps, best.content)
            }
            .sortedWith(compareByDescending<VideoQuality> { it.height }.thenByDescending { it.fps })

        val audio = info.audioStreams
            .filter {
                it.isUrl && it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP &&
                    it.format == MediaFormat.M4A &&
                    (it.audioTrackType == null || it.audioTrackType == AudioTrackType.ORIGINAL)
            }
            .maxByOrNull { it.averageBitrate }

        val muxed = info.videoStreams
            .filter { it.isUrl && it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP }
            .map { VideoQuality(it.height, it.fps, it.content) }

        return StreamOptions(
            qualities = qualities,
            audioUrl = audio?.content,
            muxed = muxed,
            hlsUrl = info.hlsUrl?.takeIf { it.isNotBlank() },
            dashUrl = info.dashMpdUrl?.takeIf { it.isNotBlank() },
            isLive = info.streamType.isLive(),
        )
    }

    private companion object {
        val CATEGORY_ORDER = listOf("trending", "music", "gaming", "movie", "podcast")
    }

    private suspend fun <T> io(block: suspend () -> T): T = withContext(Dispatchers.IO) { block() }

}
