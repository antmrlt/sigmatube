package app.sigmatube.data

import android.content.Context
import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONObject

private val VIDEO_ID = Regex("""(?:v=|youtu\.be/|/shorts/|/live/)([A-Za-z0-9_-]{11})""")

fun videoIdOf(url: String?): String? = url?.let { VIDEO_ID.find(it)?.groupValues?.get(1) }

/**
 * How far each video was watched in SigmaTube (stored on the device), used for the red
 * progress bar under thumbnails and to resume playback where it stopped.
 */
class WatchProgress(context: Context) {

    private val prefs = context.getSharedPreferences("watch_progress", Context.MODE_PRIVATE)
    private val entries = LinkedHashMap<String, Pair<Long, Long>>() // id -> (position, duration)
    private val _fractions = MutableStateFlow<Map<String, Float>>(emptyMap())
    /** video id -> watched fraction (0..1). */
    val fractions: StateFlow<Map<String, Float>> = _fractions

    init {
        runCatching {
            val json = JSONObject(prefs.getString(KEY, "{}") ?: "{}")
            json.keys().forEach { id ->
                val parts = json.optString(id).split(",")
                if (parts.size == 2) entries[id] = parts[0].toLong() to parts[1].toLong()
            }
        }
        publish()
    }

    fun save(url: String, positionMs: Long, durationMs: Long) {
        val id = videoIdOf(url) ?: return
        if (durationMs <= 0 || positionMs < MIN_POSITION_MS) return
        // Near the end counts as fully watched.
        val pos = if (positionMs >= durationMs - END_MARGIN_MS) durationMs else positionMs
        entries.remove(id)
        entries[id] = pos to durationMs
        while (entries.size > MAX_ENTRIES) entries.remove(entries.keys.first())
        persist()
        publish()
    }

    /** Where to resume, or 0 if never started / already finished. */
    fun resumePosition(url: String): Long {
        val (pos, dur) = entries[videoIdOf(url) ?: return 0] ?: return 0
        return if (pos >= dur) 0 else pos
    }

    fun clear() {
        entries.clear()
        persist()
        publish()
    }

    private fun publish() {
        _fractions.value = entries.mapValues { (_, v) -> (v.first.toFloat() / v.second).coerceIn(0f, 1f) }
    }

    private fun persist() {
        val json = JSONObject()
        entries.forEach { (id, v) -> json.put(id, "${v.first},${v.second}") }
        prefs.edit().putString(KEY, json.toString()).apply()
    }

    private companion object {
        const val KEY = "entries"
        const val MAX_ENTRIES = 1000
        const val MIN_POSITION_MS = 5_000L
        const val END_MARGIN_MS = 10_000L
    }
}

/** Watched fractions by video id, provided at the root of the UI. */
val LocalWatchProgress = staticCompositionLocalOf { emptyMap<String, Float>() }
