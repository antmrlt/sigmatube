package app.sigmatube.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject

/**
 * Watch and search history, stored on the device only. Videos played in SigmaTube are not
 * reported to YouTube, so the account's own history wouldn't contain them.
 */
class LocalHistory(context: Context, private val settings: AppSettings) {

    private val prefs = context.getSharedPreferences("history", Context.MODE_PRIVATE)

    private val _watched = MutableStateFlow(readVideos())
    val watched: StateFlow<List<VideoItem>> = _watched

    private val _searches = MutableStateFlow(readSearches())
    val searches: StateFlow<List<String>> = _searches

    fun addWatched(video: VideoItem) {
        if (settings.state.value.pauseWatchHistory) return
        val next = (listOf(video) + _watched.value.filterNot { it.url == video.url }).take(MAX_VIDEOS)
        _watched.value = next
        prefs.edit().putString(KEY_VIDEOS, JSONArray(next.map(::toJson)).toString()).apply()
    }

    fun clearWatched() {
        _watched.value = emptyList()
        prefs.edit().remove(KEY_VIDEOS).apply()
    }

    fun addSearch(query: String) {
        if (settings.state.value.pauseSearchHistory) return
        val q = query.trim().takeIf { it.isNotEmpty() } ?: return
        val next = (listOf(q) + _searches.value.filterNot { it.equals(q, ignoreCase = true) }).take(MAX_SEARCHES)
        _searches.value = next
        prefs.edit().putString(KEY_SEARCHES, JSONArray(next).toString()).apply()
    }

    fun removeSearch(query: String) {
        val next = _searches.value - query
        _searches.value = next
        prefs.edit().putString(KEY_SEARCHES, JSONArray(next).toString()).apply()
    }

    fun clearSearches() {
        _searches.value = emptyList()
        prefs.edit().remove(KEY_SEARCHES).apply()
    }

    private fun readSearches(): List<String> = runCatching {
        val arr = JSONArray(prefs.getString(KEY_SEARCHES, "[]"))
        (0 until arr.length()).map { arr.getString(it) }
    }.getOrDefault(emptyList())

    private fun readVideos(): List<VideoItem> = runCatching {
        val arr = JSONArray(prefs.getString(KEY_VIDEOS, "[]"))
        (0 until arr.length()).mapNotNull { fromJson(arr.getJSONObject(it)) }
    }.getOrDefault(emptyList())

    private fun toJson(v: VideoItem) = JSONObject()
        .put("url", v.url).put("title", v.title).put("thumbnail", v.thumbnail)
        .put("channelName", v.channelName).put("channelUrl", v.channelUrl).put("channelAvatar", v.channelAvatar)
        .put("duration", v.durationSeconds).put("views", v.viewCount).put("viewsText", v.viewsText)
        .put("uploadedText", v.uploadedText).put("isLive", v.isLive)

    private fun fromJson(o: JSONObject): VideoItem? {
        val url = o.optString("url").takeIf { it.isNotEmpty() } ?: return null
        fun str(k: String) = o.optString(k).takeIf { it.isNotEmpty() && it != "null" }
        return VideoItem(
            url = url,
            title = o.optString("title"),
            thumbnail = str("thumbnail"),
            channelName = str("channelName"),
            channelUrl = str("channelUrl"),
            channelAvatar = str("channelAvatar"),
            durationSeconds = o.optLong("duration", -1),
            viewCount = o.optLong("views", -1),
            uploadDate = null,
            uploadedText = str("uploadedText"),
            isLive = o.optBoolean("isLive"),
            viewsText = str("viewsText"),
        )
    }

    private companion object {
        const val KEY_VIDEOS = "videos"
        const val KEY_SEARCHES = "searches"
        const val MAX_VIDEOS = 200
        const val MAX_SEARCHES = 50
    }
}
