package app.sigmatube.data

import android.content.Context
import androidx.compose.runtime.staticCompositionLocalOf
import app.sigmatube.data.auth.channelIdFromUrl
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Channels the user chose to hide ("Ne pas recommander la chaîne") and videos marked
 * "Pas intéressé"; stored locally and filtered out of feeds, search and suggestions.
 */
class HiddenChannels(context: Context) {

    private val prefs = context.getSharedPreferences("hidden_channels", Context.MODE_PRIVATE)
    private val _ids = MutableStateFlow(prefs.getStringSet(KEY, emptySet()).orEmpty().toSet())
    val ids: StateFlow<Set<String>> = _ids

    private val _videos = MutableStateFlow(prefs.getStringSet(KEY_VIDEOS, emptySet()).orEmpty().toSet())
    /** Ids of videos marked "Pas intéressé". */
    val videos: StateFlow<Set<String>> = _videos

    fun isHidden(channelId: String) = channelId in _ids.value

    fun setHidden(channelId: String, hidden: Boolean) {
        val next = if (hidden) _ids.value + channelId else _ids.value - channelId
        prefs.edit().putStringSet(KEY, next).apply()
        _ids.value = next
    }

    fun setVideoHidden(videoId: String, hidden: Boolean) {
        val next = if (hidden) _videos.value + videoId else _videos.value - videoId
        prefs.edit().putStringSet(KEY_VIDEOS, next).apply()
        _videos.value = next
    }

    fun clearVideos() = _videos.value.forEach { setVideoHidden(it, false) }

    /** Channel ids and video ids together, as used by [withoutHidden]. */
    fun all(): Set<String> = _ids.value + _videos.value

    private companion object {
        const val KEY = "ids"
        const val KEY_VIDEOS = "videos"
    }
}

/** Hidden channel ids plus "Pas intéressé" video ids, provided at the root of the UI. */
val LocalHiddenChannels = staticCompositionLocalOf { emptySet<String>() }

/** Drops videos from hidden channels and videos marked "Pas intéressé" (both kinds of id share [hidden]). */
fun List<VideoItem>.withoutHidden(hidden: Set<String>): List<VideoItem> =
    if (hidden.isEmpty()) this else filterNot { channelIdFromUrl(it.channelUrl) in hidden || videoIdOf(it.url) in hidden }
