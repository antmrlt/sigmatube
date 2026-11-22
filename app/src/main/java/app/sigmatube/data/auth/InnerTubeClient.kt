package app.sigmatube.data.auth

import app.sigmatube.data.PagedResult
import app.sigmatube.data.VideoItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import org.schabi.newpipe.extractor.Page
import org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper
import java.security.MessageDigest
import java.util.Locale

enum class LikeStatus { None, Like, Dislike }

class SubscriptionsFeed(
    val shelfTitle: String?,
    val shelf: List<VideoItem>,
    val videos: List<VideoItem>,
    val nextPage: Page?,
)

/**
 * Minimal authenticated client for YouTube's internal "InnerTube" API, used only for what
 * NewPipe can't do: the personalised home feed, the subscriptions feed and account info.
 */
class InnerTubeClient(
    private val http: OkHttpClient,
    private val account: AccountManager,
) {

    suspend fun homeFeed(page: Page? = null): PagedResult<VideoItem> = browse("FEwhat_to_watch", page)

    /** Account playlists: "LL" = liked videos, "WL" = watch later. */
    suspend fun accountPlaylist(id: String, page: Page? = null): PagedResult<VideoItem> = browse("VL$id", page)

    suspend fun subscriptionsFeed(page: Page? = null): PagedResult<VideoItem> = browse("FEsubscriptions", page)

    suspend fun accountInfo(): AccountInfo? = withContext(Dispatchers.IO) {
        runCatching {
            val json = post("account/account_menu", JSONObject())
            val header = findFirst(json, "activeAccountHeaderRenderer") ?: return@runCatching null
            AccountInfo(
                name = header.optJSONObject("accountName")?.let(::text),
                handle = header.optJSONObject("channelHandle")?.let(::text),
                avatar = header.optJSONObject("accountPhoto")?.let(::lastThumb),
            )
        }.getOrNull()
    }

    /**
     * "Musique" feed. YouTube's charts API (used by NewPipe) now refuses requests, so we read the
     * official chart playlists shown on the YouTube Music channel ("Les 20 clips tendance France",
     * "Top 100 des clips musicaux France"…), localised by the request's country.
     */
    suspend fun musicFeed(): List<VideoItem> = withContext(Dispatchers.IO) {
        val home = post("browse", JSONObject().put("browseId", MUSIC_CHANNEL_ID), requireAuth = false)
        val charts = mutableListOf<Pair<String, String>>() // id to title
        walk(home) { key, obj ->
            if (key == "lockupViewModel" && obj.optString("contentType") == "LOCKUP_CONTENT_TYPE_PLAYLIST") {
                val title = obj.optJSONObject("metadata")?.optJSONObject("lockupMetadataViewModel")
                    ?.optJSONObject("title")?.optString("content").orEmpty()
                charts += obj.optString("contentId") to title
            }
        }
        // Music videos (not audio-only tracks), country charts before worldwide ones, trending first.
        val clips = charts.filter { (_, t) -> t.contains("clip", true) || t.contains("video", true) || t.contains("vidéo", true) }
            .ifEmpty { charts }
            .sortedBy { (_, t) -> if (t.contains("tendance", true) || t.contains("trending", true)) 0 else 1 }
            .take(2)
        val videos = clips.flatMap { (id, _) ->
            runCatching { collectVideos(post("browse", JSONObject().put("browseId", "VL$id"), requireAuth = false)) }.getOrDefault(emptyList())
        }
        // Fallback: the channel's own "new music videos" shelf.
        videos.ifEmpty { collectVideos(home) }.distinctBy { it.url }.ifEmpty { error("Aucune vidéo disponible pour le moment") }
    }

    /** "Films" feed: the most viewed official trailers of the month (the charts API is gone too). */
    suspend fun trailersFeed(): List<VideoItem> = withContext(Dispatchers.IO) {
        val query = if (Locale.getDefault().language == "fr") "bande annonce officielle" else "official trailer"
        val json = post(
            "search",
            JSONObject().put("query", query).put("params", SEARCH_MOST_VIEWED_THIS_MONTH),
            requireAuth = false,
        )
        collectVideos(json).filterNot { it.isLive }.ifEmpty { error("Aucune vidéo disponible pour le moment") }
    }

    /** Most viewed videos of the month for a search (music recommendations: "Hits du mois"…). */
    suspend fun mostViewedThisMonth(query: String): List<VideoItem> = withContext(Dispatchers.IO) {
        val json = post("search", JSONObject().put("query", query).put("params", SEARCH_MOST_VIEWED_THIS_MONTH), requireAuth = false)
        collectVideos(json).filterNot { it.isLive }
    }

    /** Current rating of a video by the signed-in user. */
    suspend fun likeStatus(videoId: String): LikeStatus = withContext(Dispatchers.IO) {
        val json = post("next", JSONObject().put("videoId", videoId))
        var status: String? = null
        walk(json) { key, obj ->
            if (status == null && key == "likeStatusEntity") status = obj.optString("likeStatus")
        }
        when (status) {
            "LIKE" -> LikeStatus.Like
            "DISLIKE" -> LikeStatus.Dislike
            else -> LikeStatus.None
        }
    }

    suspend fun rate(videoId: String, status: LikeStatus) = withContext(Dispatchers.IO) {
        val endpoint = when (status) {
            LikeStatus.Like -> "like/like"
            LikeStatus.Dislike -> "like/dislike"
            LikeStatus.None -> "like/removelike"
        }
        post(endpoint, JSONObject().put("target", JSONObject().put("videoId", videoId)))
        Unit
    }

    private suspend fun browse(browseId: String, page: Page?): PagedResult<VideoItem> = withContext(Dispatchers.IO) {
        val json = browseJson(browseId, page)
        PagedResult(collectVideos(json), findContinuation(json)?.let { Page("browse", it) })
    }

    /**
     * Subscriptions with YouTube's "Most relevant" shelf split out (first page only), so it can
     * be shown as a horizontal row above the chronological feed.
     */
    suspend fun subscriptionsWithShelf(page: Page? = null): SubscriptionsFeed = withContext(Dispatchers.IO) {
        val json = browseJson("FEsubscriptions", page)
        var shelfTitle: String? = null
        var shelf = emptyList<VideoItem>()
        if (page == null) {
            var found = false
            walk(json) { key, obj ->
                if (!found && (key == "richShelfRenderer" || key == "shelfRenderer")) {
                    val items = collectVideos(obj)
                    if (items.isNotEmpty()) {
                        found = true
                        shelf = items
                        shelfTitle = obj.optJSONObject("title")?.let(::text)
                    }
                }
            }
        }
        val shelfUrls = shelf.map { it.url }.toSet()
        SubscriptionsFeed(
            shelfTitle = shelfTitle,
            shelf = shelf,
            videos = collectVideos(json).filterNot { it.url in shelfUrls },
            nextPage = findContinuation(json)?.let { Page("browse", it) },
        )
    }


    // region Comments (InnerTube: gives replies, sorting and the per-comment like tokens)

    /** First page of comments for a video (works signed out; like tokens only when signed in). */
    suspend fun comments(videoId: String): YtCommentsPage = withContext(Dispatchers.IO) {
        val watch = post("next", JSONObject().put("videoId", videoId), requireAuth = false)
        var token: String? = null
        walk(watch) { key, obj ->
            if (token == null && key == "itemSectionRenderer" && obj.optString("sectionIdentifier") == "comment-item-section") {
                token = findFirst(obj, "continuationCommand")?.optString("token")?.takeIf { it.isNotEmpty() }
            }
        }
        val t = token ?: return@withContext YtCommentsPage(emptyList(), null, emptyList(), null, disabled = true)
        commentsPage(t)
    }

    /** A comments page (first page, a sort option, or the next page). */
    suspend fun commentsPage(token: String): YtCommentsPage = withContext(Dispatchers.IO) {
        val json = post("next", JSONObject().put("continuation", token), requireAuth = false)
        val entities = entities(json)
        val comments = mutableListOf<YtComment>()
        var next: String? = null
        val items = continuationItems(json)
        for (i in 0 until items.length()) {
            val item = items.optJSONObject(i) ?: continue
            item.optJSONObject("commentThreadRenderer")?.let { thread ->
                val vm = findFirst(thread, "commentViewModel")?.let { it.optJSONObject("commentViewModel") ?: it }
                val repliesToken = thread.optJSONObject("replies")?.let { r ->
                    findFirst(r, "continuationCommand")?.optString("token")?.takeIf { it.isNotEmpty() }
                }
                vm?.let { parseComment(it, entities, repliesToken) }?.let(comments::add)
            }
            item.optJSONObject("continuationItemRenderer")?.let { c ->
                findFirst(c, "continuationCommand")?.optString("token")?.takeIf { it.isNotEmpty() }?.let { next = it }
            }
        }
        val sorts = mutableListOf<SortChip>()
        walk(json) { key, obj ->
            if (key != "sortFilterSubMenuRenderer" || sorts.isNotEmpty()) return@walk
            val menu = obj.optJSONArray("subMenuItems") ?: return@walk
            for (i in 0 until menu.length()) {
                val it = menu.optJSONObject(i) ?: continue
                val t = findFirst(it, "continuationCommand")?.optString("token").orEmpty()
                if (t.isNotEmpty()) sorts += SortChip(it.optString("title"), t, it.optBoolean("selected"))
            }
        }
        var count: String? = null
        walk(json) { key, obj ->
            if (count == null && key == "commentsHeaderRenderer") {
                count = obj.optJSONObject("countText")?.let(::text) ?: obj.optJSONObject("commentsCount")?.let(::text)
            }
        }
        YtCommentsPage(comments, next, sorts, count, disabled = false)
    }

    /** Replies of a comment, 10 at a time. */
    suspend fun commentReplies(token: String): YtCommentsPage = withContext(Dispatchers.IO) {
        val json = post("next", JSONObject().put("continuation", token), requireAuth = false)
        val entities = entities(json)
        val replies = mutableListOf<YtComment>()
        var next: String? = null
        val items = continuationItems(json)
        for (i in 0 until items.length()) {
            val item = items.optJSONObject(i) ?: continue
            item.optJSONObject("commentViewModel")?.let { parseComment(it, entities, null) }?.let(replies::add)
            item.optJSONObject("continuationItemRenderer")?.let { c ->
                findFirst(c, "continuationCommand")?.optString("token")?.takeIf { it.isNotEmpty() }?.let { next = it }
            }
        }
        YtCommentsPage(replies, next, emptyList(), null, disabled = false)
    }

    /** Sends a like/dislike/undo action token from a comment's toolbar. */
    suspend fun performCommentAction(action: String) = withContext(Dispatchers.IO) {
        post("comment/perform_comment_action", JSONObject().put("actions", JSONArray().put(action)))
        Unit
    }

    /** Adds a video to "À regarder plus tard". */
    suspend fun addToWatchLater(videoId: String) = withContext(Dispatchers.IO) {
        post(
            "browse/edit_playlist",
            JSONObject()
                .put("playlistId", "WL")
                .put("actions", JSONArray().put(JSONObject().put("addedVideoId", videoId).put("action", "ACTION_ADD_VIDEO"))),
        )
        Unit
    }

    /** Removes a video from "À regarder plus tard". */
    suspend fun removeFromWatchLater(videoId: String) = withContext(Dispatchers.IO) {
        post(
            "browse/edit_playlist",
            JSONObject()
                .put("playlistId", "WL")
                .put("actions", JSONArray().put(JSONObject().put("removedVideoId", videoId).put("action", "ACTION_REMOVE_VIDEO_BY_VIDEO_ID"))),
        )
        Unit
    }

    /** Whether the video is already in "À regarder plus tard" (YouTube's "Enregistrer" dialog data). */
    suspend fun isInWatchLater(videoId: String): Boolean = withContext(Dispatchers.IO) {
        val json = post("playlist/get_add_to_playlist", JSONObject().put("videoIds", JSONArray().put(videoId)))
        var inList = false
        walk(json) { key, obj ->
            if (key == "playlistAddToOptionRenderer" && obj.optString("playlistId") == "WL") {
                inList = obj.optString("containsSelectedVideos") == "ALL"
            }
        }
        inList
    }

    private fun continuationItems(json: JSONObject): JSONArray {
        val endpoints = json.optJSONArray("onResponseReceivedEndpoints") ?: return JSONArray()
        val all = JSONArray()
        for (i in 0 until endpoints.length()) {
            val e = endpoints.optJSONObject(i) ?: continue
            val items = e.optJSONObject("reloadContinuationItemsCommand")?.optJSONArray("continuationItems")
                ?: e.optJSONObject("appendContinuationItemsAction")?.optJSONArray("continuationItems")
                ?: continue
            for (j in 0 until items.length()) all.put(items.get(j))
        }
        return all
    }

    /** frameworkUpdates mutations, indexed by entity key. */
    private fun entities(json: JSONObject): Map<String, JSONObject> {
        val map = HashMap<String, JSONObject>()
        val mutations = json.optJSONObject("frameworkUpdates")?.optJSONObject("entityBatchUpdate")?.optJSONArray("mutations")
            ?: return map
        for (i in 0 until mutations.length()) {
            val m = mutations.optJSONObject(i) ?: continue
            val payload = m.optJSONObject("payload") ?: continue
            val key = m.optString("entityKey")
            payload.keys().forEach { k -> payload.optJSONObject(k)?.let { map[key.ifEmpty { it.optString("key") }] = it } }
        }
        return map
    }

    private fun parseComment(vm: JSONObject, entities: Map<String, JSONObject>, repliesToken: String?): YtComment? {
        val entity = entities[vm.optString("commentKey")] ?: return null
        val props = entity.optJSONObject("properties") ?: return null
        val author = entity.optJSONObject("author")
        val toolbar = entity.optJSONObject("toolbar")
        val state = entities[vm.optString("toolbarStateKey")]
        val surface = entities[vm.optString("toolbarSurfaceKey")]
        fun action(name: String) = surface?.optJSONObject(name)?.let { findFirst(it, "performCommentActionEndpoint") }
            ?.optString("action")?.takeIf { it.isNotEmpty() }
        val likeState = when (state?.optString("likeState")) {
            "TOOLBAR_LIKE_STATE_LIKED" -> LikeStatus.Like
            "TOOLBAR_LIKE_STATE_DISLIKED" -> LikeStatus.Dislike
            else -> LikeStatus.None
        }
        return YtComment(
            id = props.optString("commentId"),
            author = author?.optString("displayName").orEmpty(),
            authorAvatar = author?.optString("avatarThumbnailUrl")?.takeIf { it.isNotEmpty() },
            authorChannelId = author?.optString("channelId")?.takeIf { it.startsWith("UC") },
            isCreator = author?.optBoolean("isCreator") == true,
            isVerified = author?.optBoolean("isVerified") == true,
            text = props.optJSONObject("content")?.optString("content").orEmpty(),
            publishedText = props.optString("publishedTime"),
            likeCountText = toolbar?.optString("likeCountNotliked")?.takeIf { it.isNotBlank() },
            replyCountText = toolbar?.optString("replyCount")?.takeIf { it.isNotBlank() && it != "0" },
            repliesToken = repliesToken,
            isHearted = state?.optString("heartState") == "TOOLBAR_HEART_STATE_HEARTED",
            isPinned = vm.has("pinnedText"),
            likeState = likeState,
            likeAction = action("likeCommand"),
            unlikeAction = action("unlikeCommand"),
            dislikeAction = action("dislikeCommand"),
            undislikeAction = action("undislikeCommand"),
        )
    }

    // endregion

    // region Channels

    suspend fun channelPage(channelId: String): ChannelPage = withContext(Dispatchers.IO) {
        val json = post("browse", JSONObject().put("browseId", channelId), requireAuth = false)
        val tabs = mutableListOf<ChannelTab>()
        walk(json) { key, obj ->
            if (key != "tabRenderer") return@walk
            val endpoint = obj.optJSONObject("endpoint") ?: return@walk
            val params = endpoint.optJSONObject("browseEndpoint")?.optString("params").orEmpty()
            val url = endpoint.optJSONObject("commandMetadata")?.optJSONObject("webCommandMetadata")?.optString("url").orEmpty()
            val kind = when (url.substringAfterLast('/')) {
                "featured" -> ChannelTabKind.Home
                "videos" -> ChannelTabKind.Videos
                "streams" -> ChannelTabKind.Live
                "playlists" -> ChannelTabKind.Playlists
                "posts", "community" -> ChannelTabKind.Posts
                else -> null
            }
            if (kind != null && params.isNotEmpty() && tabs.none { it.kind == kind }) tabs += ChannelTab(kind, params)
        }
        ChannelPage(tabs.sortedBy { it.kind.ordinal }, if (account.isLoggedIn) subscribedState(json) else null)
    }

    private fun subscribedState(json: JSONObject): Boolean? {
        var subscribed: Boolean? = null
        walk(json) { key, obj ->
            if (subscribed != null) return@walk
            when (key) {
                "subscriptionStateEntity" -> if (obj.has("subscribed")) subscribed = obj.optBoolean("subscribed")
                "subscribeButtonRenderer" -> if (obj.has("subscribed")) subscribed = obj.optBoolean("subscribed")
            }
        }
        return subscribed
    }

    suspend fun setSubscribed(channelId: String, subscribe: Boolean) = withContext(Dispatchers.IO) {
        val endpoint = if (subscribe) "subscription/subscribe" else "subscription/unsubscribe"
        post(endpoint, JSONObject().put("channelIds", JSONArray().put(channelId)))
        Unit
    }

    /** Videos tab; pass a sort chip token or a continuation page to load more / re-sort. */
    suspend fun channelVideos(channelId: String, params: String, token: String? = null): ChannelVideosPage =
        withContext(Dispatchers.IO) {
            val json = tabJson(channelId, params, token)
            val sorts = mutableListOf<SortChip>()
            walk(json) { key, obj ->
                if (key != "chipViewModel") return@walk
                val t = findFirst(obj, "continuationCommand")?.optString("token").orEmpty()
                if (t.isNotEmpty()) sorts += SortChip(obj.optString("text"), t, obj.optBoolean("selected"))
            }
            ChannelVideosPage(collectVideos(json), sorts, findContinuation(json)?.let { Page("browse", it) })
        }

    suspend fun channelVideosSimple(channelId: String, params: String, page: Page? = null): PagedResult<VideoItem> =
        withContext(Dispatchers.IO) {
            val json = tabJson(channelId, params, page?.id)
            PagedResult(collectVideos(json), findContinuation(json)?.let { Page("browse", it) })
        }

    suspend fun channelPlaylists(channelId: String, params: String, page: Page? = null): PagedResult<PlaylistItem> =
        withContext(Dispatchers.IO) {
            val json = tabJson(channelId, params, page?.id)
            PagedResult(collectPlaylists(json), findContinuation(json)?.let { Page("browse", it) })
        }

    suspend fun channelPosts(channelId: String, params: String, page: Page? = null): PagedResult<ChannelPost> =
        withContext(Dispatchers.IO) {
            val json = tabJson(channelId, params, page?.id)
            val posts = mutableListOf<ChannelPost>()
            walk(json) { key, obj -> if (key == "backstagePostRenderer") parsePost(obj)?.let(posts::add) }
            PagedResult(posts.distinctBy { it.id }, findContinuation(json)?.let { Page("browse", it) })
        }

    /** "Accueil" tab: the channel's shelves (latest videos, featured playlists…). */
    suspend fun channelHome(channelId: String, params: String): List<ChannelShelf> = withContext(Dispatchers.IO) {
        val json = tabJson(channelId, params, null)
        val shelves = mutableListOf<ChannelShelf>()
        walk(json) { key, obj ->
            if (key != "shelfRenderer") return@walk
            val title = obj.optJSONObject("title")?.let(::text).orEmpty()
            val videos = collectVideos(obj)
            val playlists = collectPlaylists(obj)
            if (title.isNotEmpty() && (videos.isNotEmpty() || playlists.isNotEmpty())) {
                shelves += ChannelShelf(title, videos, playlists)
            }
        }
        shelves
    }

    /** The signed-in user's subscriptions, in YouTube's relevance order (from the side guide). */
    suspend fun subscribedChannels(): List<SubscribedChannel> = withContext(Dispatchers.IO) {
        val json = post("guide", JSONObject())
        val section = findFirst(json, "guideSubscriptionsSectionRenderer") ?: return@withContext emptyList()
        val channels = mutableListOf<SubscribedChannel>()
        walk(section) { key, obj ->
            if (key != "guideEntryRenderer") return@walk
            val id = obj.optJSONObject("navigationEndpoint")?.optJSONObject("browseEndpoint")?.optString("browseId").orEmpty()
            if (!id.startsWith("UC")) return@walk
            channels += SubscribedChannel(
                id = id,
                name = obj.optJSONObject("formattedTitle")?.let(::text) ?: obj.optString("title"),
                avatar = obj.optJSONObject("thumbnail")?.let(::lastThumb),
                hasNewContent = obj.optString("presentationStyle").contains("NEW_CONTENT"),
            )
        }
        channels.distinctBy { it.id }
    }

    private fun tabJson(channelId: String, params: String, token: String?): JSONObject {
        val body = JSONObject()
        if (token != null) body.put("continuation", token) else body.put("browseId", channelId).put("params", params)
        return post("browse", body, requireAuth = false)
    }

    private fun collectPlaylists(node: Any): List<PlaylistItem> {
        val items = mutableListOf<PlaylistItem>()
        walk(node) { key, obj ->
            when (key) {
                "lockupViewModel" -> parsePlaylistLockup(obj)?.let(items::add)
                "gridPlaylistRenderer", "playlistRenderer" -> {
                    val id = obj.optString("playlistId")
                    if (id.isNotEmpty()) {
                        items += PlaylistItem(
                            id = id,
                            title = obj.optJSONObject("title")?.let(::text).orEmpty(),
                            thumbnail = findFirst(obj, "thumbnail")?.let(::lastThumb),
                            countText = obj.optJSONObject("videoCountShortText")?.let(::text)
                                ?: obj.optJSONObject("videoCountText")?.let(::text),
                        )
                    }
                }
            }
        }
        return items.distinctBy { it.id }
    }

    private fun parsePlaylistLockup(o: JSONObject): PlaylistItem? {
        val type = o.optString("contentType")
        if (type != "LOCKUP_CONTENT_TYPE_PLAYLIST" && type != "LOCKUP_CONTENT_TYPE_PODCAST") return null
        val id = o.optString("contentId").takeIf { it.isNotEmpty() } ?: return null
        val image = o.optJSONObject("contentImage")
        var badge: String? = null
        walk(image ?: JSONObject()) { key, obj -> if (badge == null && key == "thumbnailBadgeViewModel") badge = obj.optString("text") }
        return PlaylistItem(
            id = id,
            title = o.optJSONObject("metadata")?.optJSONObject("lockupMetadataViewModel")
                ?.optJSONObject("title")?.optString("content").orEmpty(),
            thumbnail = image?.let { findFirst(it, "image") }?.optJSONArray("sources")
                ?.let { it.optJSONObject(it.length() - 1)?.optString("url") },
            countText = badge,
        )
    }

    private fun parsePost(o: JSONObject): ChannelPost? {
        val id = o.optString("postId").takeIf { it.isNotEmpty() } ?: return null
        val attachment = o.optJSONObject("backstageAttachment")
        val images = mutableListOf<String>()
        var video: VideoItem? = null
        if (attachment != null) {
            walk(attachment) { key, obj ->
                if (key == "backstageImageRenderer") obj.optJSONObject("image")?.let(::lastThumb)?.let(images::add)
                if (key == "videoRenderer" && video == null) video = parseVideoRenderer(obj)
            }
        }
        return ChannelPost(
            id = id,
            author = o.optJSONObject("authorText")?.let(::text),
            authorAvatar = o.optJSONObject("authorThumbnail")?.let(::lastThumb),
            text = o.optJSONObject("contentText")?.let(::text).orEmpty(),
            publishedText = o.optJSONObject("publishedTimeText")?.let(::text),
            likesText = o.optJSONObject("voteCount")?.let(::text),
            images = images,
            video = video,
        )
    }

    // endregion

    private fun browseJson(browseId: String, page: Page?): JSONObject {
        val body = JSONObject()
        if (page?.id != null) body.put("continuation", page.id) else body.put("browseId", browseId)
        return post("browse", body)
    }

    private fun collectVideos(node: Any): List<VideoItem> {
        val videos = mutableListOf<VideoItem>()
        walk(node) { key, obj ->
            when (key) {
                "videoRenderer", "gridVideoRenderer", "playlistVideoRenderer" -> parseVideoRenderer(obj)?.let(videos::add)
                "lockupViewModel" -> parseLockup(obj)?.let(videos::add)
            }
        }
        return videos.distinctBy { it.url }
    }

    private fun findContinuation(node: Any): String? {
        var token: String? = null
        walk(node) { key, obj ->
            if (key == "continuationItemRenderer") {
                findFirst(obj, "continuationCommand")?.optString("token")?.takeIf { it.isNotEmpty() }?.let { token = it }
            }
        }
        return token
    }

    private fun post(endpoint: String, body: JSONObject, requireAuth: Boolean = true): JSONObject {
        val cookies = account.cookies.value
        val sapisid = cookies?.let(AccountManager::sapisid)
        if (requireAuth && (cookies == null || sapisid == null)) error("Non connecté")
        val locale = Locale.getDefault()
        body.put(
            "context",
            JSONObject().put(
                "client",
                JSONObject()
                    .put("clientName", "WEB")
                    .put("clientVersion", runCatching { YoutubeParsingHelper.getClientVersion() }.getOrDefault(FALLBACK_CLIENT_VERSION))
                    .put("hl", locale.language)
                    .put("gl", locale.country.ifBlank { "US" }),
            ),
        )
        val builder = Request.Builder()
            .url("https://www.youtube.com/youtubei/v1/$endpoint?prettyPrint=false")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .header("X-Origin", ORIGIN)
            .header("Origin", ORIGIN)
            .header("User-Agent", DESKTOP_UA)
        if (cookies != null && sapisid != null) {
            builder.header("Cookie", cookies)
                .header("Authorization", sapisidHash(sapisid))
                .header("X-Goog-AuthUser", "0")
        }
        val request = builder.build()
        http.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (response.code == 401 || response.code == 403) error("Session expirée, reconnecte-toi")
            if (!response.isSuccessful) error("Erreur YouTube (${response.code})")
            return JSONObject(text)
        }
    }

    private fun sapisidHash(sapisid: String): String {
        val ts = System.currentTimeMillis() / 1000
        val digest = MessageDigest.getInstance("SHA-1").digest("$ts $sapisid $ORIGIN".toByteArray())
        return "SAPISIDHASH ${ts}_${digest.joinToString("") { "%02x".format(it) }}"
    }

    // region JSON parsing (tolerant: YouTube changes these shapes often)

    /** Classic renderer used by subscriptions / older layouts. */
    private fun parseVideoRenderer(o: JSONObject): VideoItem? {
        val id = o.optString("videoId").takeIf { it.isNotEmpty() } ?: return null
        val owner = o.optJSONObject("ownerText") ?: o.optJSONObject("shortBylineText")
        val channelId = owner?.optJSONArray("runs")?.optJSONObject(0)
            ?.optJSONObject("navigationEndpoint")?.optJSONObject("browseEndpoint")?.optString("browseId")
        val videoInfo = o.optJSONObject("videoInfo")?.optJSONArray("runs")?.let { runs ->
            (0 until runs.length()).map { runs.optJSONObject(it).optString("text") }.filter { it.isNotBlank() && it.trim() != "•" }
        }
        val isLive = o.optJSONArray("badges")?.toString()?.contains("LIVE") == true ||
            o.optJSONArray("thumbnailOverlays")?.toString()?.contains("\"LIVE\"") == true
        return VideoItem(
            url = "https://www.youtube.com/watch?v=$id",
            title = o.optJSONObject("title")?.let(::text).orEmpty(),
            thumbnail = o.optJSONObject("thumbnail")?.let(::lastThumb),
            channelName = owner?.let(::text),
            channelUrl = channelId?.takeIf { it.isNotEmpty() }?.let { "https://www.youtube.com/channel/$it" },
            channelAvatar = findFirst(o, "channelThumbnailWithLinkRenderer")?.optJSONObject("thumbnail")?.let(::lastThumb),
            durationSeconds = o.optJSONObject("lengthText")?.let(::text)?.let(::parseDuration) ?: -1,
            viewCount = -1,
            viewsText = o.optJSONObject("shortViewCountText")?.let(::text) ?: o.optJSONObject("viewCountText")?.let(::text)
                ?: videoInfo?.getOrNull(0),
            uploadDate = null,
            uploadedText = o.optJSONObject("publishedTimeText")?.let(::text) ?: videoInfo?.getOrNull(1),
            watchedFraction = findFirst(o, "thumbnailOverlayResumePlaybackRenderer")
                ?.optInt("percentDurationWatched", -1)?.takeIf { it > 0 }?.let { it / 100f },
            isLive = isLive,
        )
    }

    /** New "lockup" view model used by the home grid since 2025. */
    private fun parseLockup(o: JSONObject): VideoItem? {
        if (o.optString("contentType") != "LOCKUP_CONTENT_TYPE_VIDEO") return null
        val id = o.optString("contentId").takeIf { it.isNotEmpty() } ?: return null
        val meta = o.optJSONObject("metadata")?.optJSONObject("lockupMetadataViewModel") ?: return null
        val rows = meta.optJSONObject("metadata")?.optJSONObject("contentMetadataViewModel")
            ?.optJSONArray("metadataRows")
        fun row(i: Int): List<String> = rows?.optJSONObject(i)?.optJSONArray("metadataParts")?.let { parts ->
            (0 until parts.length()).mapNotNull { parts.optJSONObject(it)?.optJSONObject("text")?.optString("content") }
        }.orEmpty()
        val badges = mutableListOf<String>()
        walk(o.optJSONObject("contentImage") ?: JSONObject()) { key, obj ->
            if (key == "thumbnailBadgeViewModel") badges += obj.optString("text")
        }
        val avatar = findFirst(meta, "avatarViewModel")?.optJSONObject("image")?.optJSONArray("sources")
            ?.optJSONObject(0)?.optString("url")
        val channelId = findFirst(meta, "browseEndpoint")?.optString("browseId")
        val hasChannelRow = (rows?.length() ?: 0) >= 2
        val stats = if (hasChannelRow) row(1) else row(0)
        val isLive = badges.any { it.contains("DIRECT", true) || it.contains("LIVE", true) }
        return VideoItem(
            url = "https://www.youtube.com/watch?v=$id",
            title = meta.optJSONObject("title")?.optString("content").orEmpty(),
            thumbnail = o.optJSONObject("contentImage")?.let { findFirst(it, "image") }
                ?.optJSONArray("sources")?.let { it.optJSONObject(it.length() - 1)?.optString("url") },
            channelName = if (hasChannelRow) row(0).firstOrNull() else null,
            channelUrl = channelId?.takeIf { it.startsWith("UC") }?.let { "https://www.youtube.com/channel/$it" },
            channelAvatar = avatar,
            durationSeconds = badges.firstNotNullOfOrNull(::parseDuration) ?: -1,
            viewCount = -1,
            viewsText = stats.getOrNull(0),
            uploadDate = null,
            uploadedText = stats.getOrNull(1),
            isLive = isLive,
            watchedFraction = findFirst(o, "thumbnailOverlayProgressBarViewModel")
                ?.optInt("startPercent", -1)?.takeIf { it > 0 }?.let { it / 100f },
        )
    }

    private fun text(o: JSONObject): String? =
        o.optString("simpleText").takeIf { it.isNotEmpty() }
            ?: o.optJSONArray("runs")?.let { runs -> (0 until runs.length()).joinToString("") { runs.optJSONObject(it).optString("text") } }

    private fun lastThumb(o: JSONObject): String? =
        o.optJSONArray("thumbnails")?.let { it.optJSONObject(it.length() - 1)?.optString("url") }
            ?.let { if (it.startsWith("//")) "https:$it" else it }

    private fun parseDuration(text: String): Long? {
        val parts = text.trim().split(":").map { it.toLongOrNull() ?: return null }
        if (parts.size !in 2..3) return null
        return parts.fold(0L) { acc, n -> acc * 60 + n }
    }

    /** Depth-first walk calling [visit] for every (key, object) pair. */
    private fun walk(node: Any?, visit: (String, JSONObject) -> Unit) {
        when (node) {
            is JSONObject -> node.keys().forEach { key ->
                val child = node.opt(key)
                if (child is JSONObject) visit(key, child)
                walk(child, visit)
            }
            is JSONArray -> for (i in 0 until node.length()) walk(node.opt(i), visit)
        }
    }

    private fun findFirst(node: Any?, wanted: String): JSONObject? {
        var found: JSONObject? = null
        walk(node) { key, obj -> if (found == null && key == wanted) found = obj }
        return found
    }

    // endregion

    private companion object {
        const val ORIGIN = "https://www.youtube.com"
        const val FALLBACK_CLIENT_VERSION = "2.20251101.00.00"
        private const val MUSIC_CHANNEL_ID = "UC-9-kyTW8ZkZNDHQJ6FgpwQ"
        /** Search filters: videos uploaded this month, sorted by view count. */
        private const val SEARCH_MOST_VIEWED_THIS_MONTH = "CAMSBAgEEAE%3D"
        const val DESKTOP_UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/140.0.0.0 Safari/537.36"
    }
}
