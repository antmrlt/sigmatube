package app.sigmatube.data

import android.content.Context
import androidx.compose.runtime.staticCompositionLocalOf
import app.sigmatube.data.auth.channelIdFromUrl
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Channels the user chose to hide; stored locally and filtered out of feeds, search and suggestions. */
class HiddenChannels(context: Context) {

    private val prefs = context.getSharedPreferences("hidden_channels", Context.MODE_PRIVATE)
    private val _ids = MutableStateFlow(prefs.getStringSet(KEY, emptySet()).orEmpty().toSet())
    val ids: StateFlow<Set<String>> = _ids

    fun isHidden(channelId: String) = channelId in _ids.value

    fun setHidden(channelId: String, hidden: Boolean) {
        val next = if (hidden) _ids.value + channelId else _ids.value - channelId
        prefs.edit().putStringSet(KEY, next).apply()
        _ids.value = next
    }

    private companion object {
        const val KEY = "ids"
    }
}

/** Current hidden channel ids, provided at the root of the UI. */
val LocalHiddenChannels = staticCompositionLocalOf { emptySet<String>() }

fun List<VideoItem>.withoutHidden(hidden: Set<String>): List<VideoItem> =
    if (hidden.isEmpty()) this else filterNot { channelIdFromUrl(it.channelUrl) in hidden }
