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

/**
 * Minimal authenticated client for YouTube's internal "InnerTube" API, used only for what
 * NewPipe can't do: the personalised home feed, the subscriptions feed and account info.
 */
class InnerTubeClient(
    private val http: OkHttpClient,
    private val account: AccountManager,
) {

    suspend fun homeFeed(page: Page? = null): PagedResult<VideoItem> = browse("FEwhat_to_watch", page)

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
        val body = JSONObject()
        if (page?.id != null) body.put("continuation", page.id) else body.put("browseId", browseId)
        val json = post("browse", body)
        val videos = mutableListOf<VideoItem>()
        var continuation: String? = null
        walk(json) { key, obj ->
            when (key) {
                "videoRenderer", "gridVideoRenderer" -> parseVideoRenderer(obj)?.let(videos::add)
                "lockupViewModel" -> parseLockup(obj)?.let(videos::add)
                "continuationCommand" -> obj.optString("token").takeIf { it.isNotEmpty() }?.let { continuation = it }
            }
        }
        PagedResult(videos.distinctBy { it.url }, continuation?.let { Page("browse", it) })
    }

    private fun post(endpoint: String, body: JSONObject): JSONObject {
        val cookies = account.cookies.value ?: error("Non connecté")
        val sapisid = AccountManager.sapisid(cookies) ?: error("Session invalide, reconnecte-toi")
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
        val request = Request.Builder()
            .url("https://www.youtube.com/youtubei/v1/$endpoint?prettyPrint=false")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .header("Cookie", cookies)
            .header("Authorization", sapisidHash(sapisid))
            .header("X-Origin", ORIGIN)
            .header("Origin", ORIGIN)
            .header("X-Goog-AuthUser", "0")
            .header("User-Agent", DESKTOP_UA)
            .build()
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
            viewsText = o.optJSONObject("shortViewCountText")?.let(::text) ?: o.optJSONObject("viewCountText")?.let(::text),
            uploadDate = null,
            uploadedText = o.optJSONObject("publishedTimeText")?.let(::text),
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
        val stats = row(1)
        val isLive = badges.any { it.contains("DIRECT", true) || it.contains("LIVE", true) }
        return VideoItem(
            url = "https://www.youtube.com/watch?v=$id",
            title = meta.optJSONObject("title")?.optString("content").orEmpty(),
            thumbnail = o.optJSONObject("contentImage")?.let { findFirst(it, "image") }
                ?.optJSONArray("sources")?.let { it.optJSONObject(it.length() - 1)?.optString("url") },
            channelName = row(0).firstOrNull(),
            channelUrl = channelId?.takeIf { it.startsWith("UC") }?.let { "https://www.youtube.com/channel/$it" },
            channelAvatar = avatar,
            durationSeconds = badges.firstNotNullOfOrNull(::parseDuration) ?: -1,
            viewCount = -1,
            viewsText = stats.getOrNull(0),
            uploadDate = null,
            uploadedText = stats.getOrNull(1),
            isLive = isLive,
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
        const val DESKTOP_UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/140.0.0.0 Safari/537.36"
    }
}
