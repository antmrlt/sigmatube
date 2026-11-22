package app.sigmatube.data

import org.json.JSONObject

/** Compact JSON form of a [VideoItem], for the lists stored on the device (history, playlists). */
fun VideoItem.toJson(): JSONObject = JSONObject()
    .put("url", url).put("title", title).put("thumbnail", thumbnail)
    .put("channelName", channelName).put("channelUrl", channelUrl).put("channelAvatar", channelAvatar)
    .put("duration", durationSeconds).put("views", viewCount).put("viewsText", viewsText)
    .put("uploadedText", uploadedText).put("isLive", isLive)

fun videoFromJson(o: JSONObject): VideoItem? {
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
