package app.sigmatube.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class LocalPlaylist(
    val id: String,
    val name: String,
    val videos: List<VideoItem>,
) {
    val thumbnail: String? get() = videos.firstOrNull()?.let { hqThumbnail(it) }

    private fun hqThumbnail(v: VideoItem) = videoIdOf(v.url)?.let { "https://i.ytimg.com/vi/$it/hqdefault.jpg" } ?: v.thumbnail
}

/**
 * Playlists kept on the device: they work without an account and are never sent to YouTube.
 * A video appears at most once per playlist.
 */
class LocalPlaylists(context: Context) {

    private val prefs = context.getSharedPreferences("playlists", Context.MODE_PRIVATE)
    private val _playlists = MutableStateFlow(read())
    val playlists: StateFlow<List<LocalPlaylist>> = _playlists

    fun get(id: String): LocalPlaylist? = _playlists.value.firstOrNull { it.id == id }

    /** Creates a playlist (optionally with a first video) and returns its id. */
    fun create(name: String, first: VideoItem? = null): String {
        val playlist = LocalPlaylist(UUID.randomUUID().toString(), name.trim().ifEmpty { "Nouvelle playlist" }, listOfNotNull(first))
        save(listOf(playlist) + _playlists.value)
        return playlist.id
    }

    fun rename(id: String, name: String) = edit(id) { it.copy(name = name.trim().ifEmpty { it.name }) }

    fun delete(id: String) = save(_playlists.value.filterNot { it.id == id })

    fun add(id: String, video: VideoItem) = edit(id) { p ->
        if (p.videos.any { it.url == video.url }) p else p.copy(videos = p.videos + video)
    }

    fun remove(id: String, url: String) = edit(id) { p -> p.copy(videos = p.videos.filterNot { it.url == url }) }

    fun move(id: String, from: Int, to: Int) = edit(id) { p ->
        if (from !in p.videos.indices || to !in p.videos.indices) p
        else p.copy(videos = p.videos.toMutableList().apply { add(to, removeAt(from)) })
    }

    fun contains(id: String, url: String) = get(id)?.videos?.any { it.url == url } == true

    private fun edit(id: String, transform: (LocalPlaylist) -> LocalPlaylist) =
        save(_playlists.value.map { if (it.id == id) transform(it) else it })

    private fun save(list: List<LocalPlaylist>) {
        _playlists.value = list
        val json = JSONArray(list.map { p ->
            JSONObject().put("id", p.id).put("name", p.name).put("videos", JSONArray(p.videos.map { it.toJson() }))
        })
        prefs.edit().putString(KEY, json.toString()).apply()
    }

    private fun read(): List<LocalPlaylist> = runCatching {
        val arr = JSONArray(prefs.getString(KEY, "[]"))
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            val videos = o.optJSONArray("videos") ?: JSONArray()
            LocalPlaylist(
                id = o.getString("id"),
                name = o.optString("name"),
                videos = (0 until videos.length()).mapNotNull { videoFromJson(videos.getJSONObject(it)) },
            )
        }
    }.getOrDefault(emptyList())

    private companion object {
        const val KEY = "list"
    }
}
