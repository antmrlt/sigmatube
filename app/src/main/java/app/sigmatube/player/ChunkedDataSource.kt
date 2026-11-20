package app.sigmatube.player

import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.TransferListener

/**
 * googlevideo.com throttles (or silently stalls) long open-ended range requests on big
 * video-only streams, which is why 1080p never got past the first seconds. This source
 * transparently splits every read into bounded `Range` requests of [chunkSize] bytes, the way
 * YouTube's own players do.
 */
@OptIn(UnstableApi::class)
class ChunkedDataSource(
    private val upstream: DataSource,
    private val chunkSize: Long,
) : DataSource {

    private var spec: DataSpec? = null
    private var position = 0L
    /** Bytes left to serve for the caller's request, or LENGTH_UNSET for "to the end". */
    private var remaining = C.LENGTH_UNSET.toLong()
    private var chunkLeft = 0L
    private var lastChunk = false
    private var opened = false

    override fun addTransferListener(transferListener: TransferListener) = upstream.addTransferListener(transferListener)

    override fun open(dataSpec: DataSpec): Long {
        spec = dataSpec
        position = dataSpec.position
        remaining = dataSpec.length
        openChunk()
        return dataSpec.length
    }

    private fun openChunk() {
        val base = spec ?: return
        val wanted = if (remaining == C.LENGTH_UNSET.toLong()) chunkSize else minOf(chunkSize, remaining)
        val chunkSpec = base.buildUpon().setPosition(position).setLength(wanted).build()
        val got = try {
            upstream.open(chunkSpec)
        } catch (e: HttpDataSource.InvalidResponseCodeException) {
            // 416: we asked past the end of the file, i.e. the previous chunk was the last one.
            if (e.responseCode == 416 && position > base.position) {
                lastChunk = true
                chunkLeft = 0
                opened = false
                return
            }
            throw e
        }
        opened = true
        chunkLeft = if (got == C.LENGTH_UNSET.toLong()) wanted else got
        lastChunk = got != C.LENGTH_UNSET.toLong() && got < wanted ||
            (remaining != C.LENGTH_UNSET.toLong() && remaining <= wanted)
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        while (true) {
            if (remaining == 0L) return C.RESULT_END_OF_INPUT
            if (!opened) {
                if (lastChunk) return C.RESULT_END_OF_INPUT
                openChunk()
                if (!opened) return C.RESULT_END_OF_INPUT
            }
            val n = upstream.read(buffer, offset, length)
            if (n == C.RESULT_END_OF_INPUT) {
                upstream.close()
                opened = false
                // Server ended a chunk early (or this was the last one): real end of stream.
                if (lastChunk || chunkLeft > 0) return C.RESULT_END_OF_INPUT
                continue
            }
            position += n
            chunkLeft -= n
            if (remaining != C.LENGTH_UNSET.toLong()) remaining -= n
            return n
        }
    }

    override fun getUri(): Uri? = upstream.uri ?: spec?.uri

    override fun getResponseHeaders(): Map<String, List<String>> = upstream.responseHeaders

    override fun close() {
        if (opened) upstream.close()
        opened = false
        spec = null
    }

    class Factory(private val upstream: DataSource.Factory, private val chunkSize: Long = DEFAULT_CHUNK) : DataSource.Factory {
        override fun createDataSource(): DataSource = ChunkedDataSource(upstream.createDataSource(), chunkSize)
    }

    companion object {
        const val DEFAULT_CHUNK = 1L shl 20 // 1 MiB
    }
}
