package com.example.juzzics.common.artwork

import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.media.MediaMetadataRetriever
import android.os.Build
import android.provider.MediaStore
import android.util.Size
import coil.ImageLoader
import coil.decode.DataSource
import coil.fetch.DrawableResult
import coil.fetch.FetchResult
import coil.fetch.Fetcher
import coil.key.Keyer
import coil.request.Options
import com.example.juzzics.common.songs.ReceivedSongFiles
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Artwork of a song, for Coil: `AsyncImage(model = SongArtwork(song.id), ...)`.
 *
 * The old `content://media/external/audio/albumart/<id>` addresses don't work reliably on
 * Android 10+, so this asks the system for the song's thumbnail instead (or reads the picture
 * embedded in the file).
 */
data class SongArtwork(val songId: Long)

private const val ARTWORK_SIZE = 512

class SongArtworkFetcher(
    private val data: SongArtwork,
    private val options: Options,
) : Fetcher {

    override suspend fun fetch(): FetchResult {
        val context = options.context
        val bitmap = withContext(Dispatchers.IO) { loadSongArtwork(context, data.songId) }
            ?: error("No artwork for song ${data.songId}")
        return DrawableResult(
            drawable = BitmapDrawable(context.resources, bitmap),
            isSampled = true,
            dataSource = DataSource.DISK,
        )
    }

    class Factory : Fetcher.Factory<SongArtwork> {
        override fun create(data: SongArtwork, options: Options, imageLoader: ImageLoader): Fetcher =
            SongArtworkFetcher(data, options)
    }
}

/** lets Coil keep loaded artwork in its memory cache */
class SongArtworkKeyer : Keyer<SongArtwork> {
    override fun key(data: SongArtwork, options: Options): String = "song_artwork_${data.songId}"
}

/**
 * thumbnail from the system (Android 10+), else the picture embedded in the file; null if none.
 * Songs friends sent (negative ids) use the picture that came with them.
 */
fun loadSongArtwork(context: Context, songId: Long): Bitmap? {
    if (songId < 0) {
        val file = ReceivedSongFiles.artwork(context, songId)
        return if (file.exists()) BitmapFactory.decodeFile(file.absolutePath) else null
    }
    if (songId == 0L) return null
    val songUri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, songId)

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        runCatching {
            return context.contentResolver.loadThumbnail(songUri, Size(ARTWORK_SIZE, ARTWORK_SIZE), null)
        }
    }

    val retriever = MediaMetadataRetriever()
    return try {
        retriever.setDataSource(context, songUri)
        val bytes = retriever.embeddedPicture ?: return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= ARTWORK_SIZE) sample *= 2
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
    } catch (e: Exception) {
        null
    } finally {
        runCatching { retriever.release() }
    }
}
