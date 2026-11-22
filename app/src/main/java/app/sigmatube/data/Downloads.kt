package app.sigmatube.data

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import app.sigmatube.player.streamHeaders
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.OutputStream
import java.net.URLDecoder

/**
 * Saves a video (or just its audio) into Téléchargements/SigmaTube. Files are fetched in bounded
 * `Range` chunks, since googlevideo.com stalls long open-ended requests (see ChunkedDataSource).
 */
class Downloads(
    private val context: Context,
    private val http: OkHttpClient,
    private val repository: YouTubeRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var nextNotificationId = 4000

    fun download(video: VideoItem, audioOnly: Boolean) {
        val notificationId = nextNotificationId++
        toast("Téléchargement de « ${video.title} »…")
        scope.launch {
            runCatching {
                val details = repository.video(video.url)
                val streams = details.streams
                val url = if (audioOnly) streams.audioUrl else streams.muxed.maxByOrNull { it.height }?.url
                url ?: error(if (audioOnly) "Aucune piste audio disponible" else "Aucun fichier vidéo téléchargeable pour cette vidéo")
                val mime = mimeOf(url) ?: if (audioOnly) "audio/mp4" else "video/mp4"
                val ext = when {
                    audioOnly && mime.contains("mp4") -> "m4a"
                    mime.contains("webm") -> "webm"
                    mime.contains("3gpp") -> "3gp"
                    else -> "mp4"
                }
                val name = "${sanitize(details.title)}.$ext"
                save(name, mime) { out -> fetch(url, out) { done, total -> progress(notificationId, details.title, done, total) } }
                name
            }.onSuccess { name ->
                finish(notificationId, video.title, "Téléchargement terminé")
                toast("Enregistré dans Téléchargements/SigmaTube : $name")
            }.onFailure { e ->
                finish(notificationId, video.title, "Échec du téléchargement")
                toast("Échec du téléchargement : ${e.message ?: "erreur inconnue"}")
            }
        }
    }

    private fun fetch(url: String, out: OutputStream, onProgress: (Long, Long) -> Unit) {
        val headers = streamHeaders(url)
        var position = 0L
        var total = -1L
        var lastReport = 0L
        while (total < 0 || position < total) {
            val request = Request.Builder().url(url).apply {
                headers.forEach { (k, v) -> header(k, v) }
                header("Range", "bytes=$position-${position + CHUNK - 1}")
            }.build()
            http.newCall(request).execute().use { response ->
                if (response.code == 416) return
                if (!response.isSuccessful) error("erreur ${response.code}")
                val body = response.body ?: error("réponse vide")
                if (response.code == 206) {
                    response.header("Content-Range")?.substringAfter('/')?.toLongOrNull()?.let { total = it }
                } else {
                    total = body.contentLength()
                }
                val start = position
                body.byteStream().use { input ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        out.write(buffer, 0, n)
                        position += n
                        if (position - lastReport > 512 * 1024) {
                            lastReport = position
                            onProgress(position, total)
                        }
                    }
                }
                // A full (200) answer, or a server that sent nothing more: we're done.
                if (response.code != 206 || position == start) return
            }
        }
    }

    private fun save(name: String, mime: String, write: (OutputStream) -> Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = context.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, name)
                put(MediaStore.Downloads.MIME_TYPE, mime)
                put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/SigmaTube")
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val uri: Uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: error("stockage indisponible")
            try {
                resolver.openOutputStream(uri)?.use(write) ?: error("stockage indisponible")
                resolver.update(uri, ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }, null, null)
            } catch (e: Throwable) {
                resolver.delete(uri, null, null)
                throw e
            }
        } else {
            // Android 8-9: the app's own folder, no storage permission needed.
            val dir = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "SigmaTube").apply { mkdirs() }
            val file = File(dir, name)
            try {
                file.outputStream().use(write)
            } catch (e: Throwable) {
                file.delete()
                throw e
            }
        }
    }

    // region Notifications

    private val notifications = NotificationManagerCompat.from(context)

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(NotificationManager::class.java)
            if (manager.getNotificationChannel(CHANNEL) == null) {
                manager.createNotificationChannel(NotificationChannel(CHANNEL, "Téléchargements", NotificationManager.IMPORTANCE_LOW))
            }
        }
    }

    @SuppressLint("MissingPermission") // guarded by areNotificationsEnabled()
    private fun progress(id: Int, title: String, done: Long, total: Long) {
        if (!notifications.areNotificationsEnabled()) return
        ensureChannel()
        val percent = if (total > 0) (done * 100 / total).toInt() else 0
        notifications.notify(
            id,
            NotificationCompat.Builder(context, CHANNEL)
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setContentTitle(title)
                .setContentText("Téléchargement… $percent %")
                .setProgress(100, percent, total <= 0)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .build(),
        )
    }

    @SuppressLint("MissingPermission")
    private fun finish(id: Int, title: String, text: String) {
        if (!notifications.areNotificationsEnabled()) return
        ensureChannel()
        notifications.notify(
            id,
            NotificationCompat.Builder(context, CHANNEL)
                .setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setContentTitle(title)
                .setContentText(text)
                .setAutoCancel(true)
                .build(),
        )
    }

    // endregion

    private suspend fun toastOnMain(text: String) = withContext(Dispatchers.Main) {
        Toast.makeText(context, text, Toast.LENGTH_LONG).show()
    }

    private fun toast(text: String) {
        scope.launch { toastOnMain(text) }
    }

    private fun mimeOf(url: String): String? =
        Regex("[?&]mime=([^&]+)").find(url)?.groupValues?.get(1)?.let { URLDecoder.decode(it, "UTF-8") }

    private fun sanitize(title: String) =
        title.replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]"), " ").replace(Regex("\\s+"), " ").trim().take(120).ifEmpty { "video" }

    private companion object {
        const val CHANNEL = "downloads"
        const val CHUNK = 8L * 1024 * 1024
    }
}
