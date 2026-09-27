package com.example.juzzics.features.player.stream

import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.TransferListener
import java.io.File
import java.io.IOException
import java.io.InterruptedIOException
import java.io.RandomAccessFile

/**
 * Reads a file that may still be arriving ([GrowingFiles]): at the end of what's written so
 * far it waits for more, so a song can play while it streams in.
 */
@OptIn(UnstableApi::class)
class GrowingFileDataSource : BaseDataSource(/* isNetwork = */ false) {
    private var file: RandomAccessFile? = null
    private var path: String? = null
    private var uri: Uri? = null
    private var position = 0L
    private var bytesRemaining = C.LENGTH_UNSET.toLong()

    override fun open(dataSpec: DataSpec): Long {
        transferInitializing(dataSpec)
        val filePath = dataSpec.uri.path ?: throw IOException("No path")
        path = filePath
        uri = dataSpec.uri
        position = dataSpec.position
        // its final size (known from the sender) lets the player jump within the song
        val total = GrowingFiles.totalBytes(filePath)
        // the start of the file must be there (or arrive soon)
        waitFor(filePath, position)
        file = RandomAccessFile(File(filePath), "r").also { it.seek(position) }
        bytesRemaining = when {
            dataSpec.length != C.LENGTH_UNSET.toLong() -> dataSpec.length
            total > 0 -> (total - position).coerceAtLeast(0)
            else -> C.LENGTH_UNSET.toLong()
        }
        transferStarted(dataSpec)
        return bytesRemaining
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        if (bytesRemaining == 0L) return C.RESULT_END_OF_INPUT
        val filePath = path ?: return C.RESULT_END_OF_INPUT
        val available = waitFor(filePath, position)
        if (available <= 0) return C.RESULT_END_OF_INPUT
        var toRead = minOf(length.toLong(), available)
        if (bytesRemaining != C.LENGTH_UNSET.toLong()) toRead = minOf(toRead, bytesRemaining)
        val read = file?.read(buffer, offset, toRead.toInt()) ?: return C.RESULT_END_OF_INPUT
        if (read < 0) return C.RESULT_END_OF_INPUT
        position += read
        if (bytesRemaining != C.LENGTH_UNSET.toLong()) bytesRemaining -= read
        bytesTransferred(read)
        return read
    }

    /**
     * bytes readable from [from]: waits while the file is still arriving and has nothing there
     * yet. 0 = the end of a complete file.
     */
    private fun waitFor(filePath: String, from: Long): Long {
        var waited = 0L
        while (true) {
            val written = GrowingFiles.written(filePath)
                ?: return (File(filePath).length() - from).coerceAtLeast(0) // complete (or never growing)
            if (written > from) return written - from
            if (GrowingFiles.failed(filePath)) throw IOException("The song stopped arriving")
            if (waited > MAX_WAIT_MS) throw IOException("The song isn't arriving")
            try {
                Thread.sleep(POLL_MS)
            } catch (e: InterruptedException) {
                // the player gave up on this read (e.g. a seek)
                Thread.currentThread().interrupt()
                throw InterruptedIOException()
            }
            waited += POLL_MS
        }
    }

    override fun getUri(): Uri? = uri

    override fun close() {
        uri = null
        try {
            file?.close()
        } finally {
            file = null
            path = null
            transferEnded()
        }
    }

    private companion object {
        const val POLL_MS = 15L
        const val MAX_WAIT_MS = 30_000L
    }
}

/**
 * The player's data sources: files still arriving go through [GrowingFileDataSource],
 * everything else as usual ([DefaultDataSource]).
 */
@OptIn(UnstableApi::class)
class StreamAwareDataSourceFactory(context: Context) : DataSource.Factory {
    private val default = DefaultDataSource.Factory(context)

    override fun createDataSource(): DataSource = StreamAwareDataSource(default.createDataSource())
}

@OptIn(UnstableApi::class)
private class StreamAwareDataSource(private val default: DataSource) : DataSource {
    private val listeners = mutableListOf<TransferListener>()
    private var current: DataSource? = null

    override fun addTransferListener(transferListener: TransferListener) {
        listeners += transferListener
        default.addTransferListener(transferListener)
    }

    override fun open(dataSpec: DataSpec): Long {
        val path = dataSpec.uri.path
        val source = if (dataSpec.uri.scheme == "file" && path != null && GrowingFiles.isGrowing(path)) {
            GrowingFileDataSource().also { growing -> listeners.forEach(growing::addTransferListener) }
        } else default
        current = source
        return source.open(dataSpec)
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
        current?.read(buffer, offset, length) ?: C.RESULT_END_OF_INPUT

    override fun getUri(): Uri? = current?.uri

    override fun getResponseHeaders(): Map<String, List<String>> = current?.responseHeaders ?: emptyMap()

    override fun close() {
        try {
            current?.close()
        } finally {
            current = null
        }
    }
}
