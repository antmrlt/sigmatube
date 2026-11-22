package app.sigmatube.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray

/**
 * Watch and search history, stored on the device only. Videos played in SigmaTube are not
 * reported to YouTube, so the account's own history wouldn't contain them.
 */
class LocalHistory(context: Context, private val settings: AppSettings) {

    private val prefs = context.getSharedPreferences("history", Context.MODE_PRIVATE)

    private val _watched = MutableStateFlow(readVideos())
    val watched: StateFlow<List<VideoItem>> = _watched

    /** What was played in the music player page: "Écoutés récemment" and music recommendations. */
    private val _music = MutableStateFlow(readVideos(KEY_MUSIC))
    val music: StateFlow<List<VideoItem>> = _music

    private val _searches = MutableStateFlow(readSearches())
    val searches: StateFlow<List<String>> = _searches

    fun addWatched(video: VideoItem) {
        if (settings.state.value.pauseWatchHistory) return
        val next = (listOf(video) + _watched.value.filterNot { it.url == video.url }).take(MAX_VIDEOS)
        _watched.value = next
        prefs.edit().putString(KEY_VIDEOS, JSONArray(next.map { it.toJson() }).toString()).apply()
    }

    fun clearWatched() {
        _watched.value = emptyList()
        _music.value = emptyList()
        prefs.edit().remove(KEY_VIDEOS).remove(KEY_MUSIC).apply()
    }

    fun addMusic(video: VideoItem) {
        if (settings.state.value.pauseWatchHistory) return
        val next = (listOf(video) + _music.value.filterNot { it.url == video.url }).take(MAX_MUSIC)
        _music.value = next
        prefs.edit().putString(KEY_MUSIC, JSONArray(next.map { it.toJson() }).toString()).apply()
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

    private fun readVideos(key: String = KEY_VIDEOS): List<VideoItem> = runCatching {
        val arr = JSONArray(prefs.getString(key, "[]"))
        (0 until arr.length()).mapNotNull { videoFromJson(arr.getJSONObject(it)) }
    }.getOrDefault(emptyList())

    private companion object {
        const val KEY_VIDEOS = "videos"
        const val KEY_SEARCHES = "searches"
        const val KEY_MUSIC = "music"
        const val MAX_MUSIC = 50
        const val MAX_VIDEOS = 200
        const val MAX_SEARCHES = 50
    }
}
