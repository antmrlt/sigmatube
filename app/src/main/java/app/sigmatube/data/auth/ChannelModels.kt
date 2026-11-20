package app.sigmatube.data.auth

import app.sigmatube.data.VideoItem
import org.schabi.newpipe.extractor.Page

enum class ChannelTabKind(val label: String) {
    Home("Accueil"),
    Videos("Vidéos"),
    Live("En direct"),
    Playlists("Playlists"),
    Posts("Posts"),
}

data class ChannelTab(val kind: ChannelTabKind, val params: String)

/** What the channel page needs beyond NewPipe's header: available tabs + subscription state. */
data class ChannelPage(val tabs: List<ChannelTab>, val subscribed: Boolean?)

/** A sort chip ("Les plus récentes", "Populaires"…) and the token that loads it. */
data class SortChip(val label: String, val token: String, val selected: Boolean)

class ChannelVideosPage(val videos: List<VideoItem>, val sorts: List<SortChip>, val nextPage: Page?)

data class PlaylistItem(
    val id: String,
    val title: String,
    val thumbnail: String?,
    val countText: String?,
) {
    val url: String get() = "https://www.youtube.com/playlist?list=$id"
}

data class ChannelPost(
    val id: String,
    val author: String?,
    val authorAvatar: String?,
    val text: String,
    val publishedText: String?,
    val likesText: String?,
    val images: List<String>,
    val video: VideoItem?,
)

class ChannelShelf(val title: String, val videos: List<VideoItem>, val playlists: List<PlaylistItem>)

/** An entry of the subscriptions avatar row. */
data class SubscribedChannel(
    val id: String,
    val name: String,
    val avatar: String?,
    val hasNewContent: Boolean,
) {
    val url: String get() = "https://www.youtube.com/channel/$id"
}

private val CHANNEL_ID = Regex("""/channel/(UC[A-Za-z0-9_-]{22})""")

fun channelIdFromUrl(url: String?): String? = url?.let { CHANNEL_ID.find(it)?.groupValues?.get(1) }
