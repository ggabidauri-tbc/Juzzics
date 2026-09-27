package com.example.juzzics.features.nearby.data

import android.Manifest
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import com.example.juzzics.common.songs.ReceivedSongFiles
import com.example.juzzics.features.lyrics.domain.model.LyricsDomain
import com.example.juzzics.features.lyrics.domain.repo.LyricsRepo
import com.example.juzzics.features.musics.domain.model.MusicFileDomain
import com.example.juzzics.features.player.stream.GrowingFiles
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.atomic.AtomicLong

/** A song a friend sent over Nearby, kept in the app's cache. */
data class ReceivedSong(
    /** negative, see [ReceivedSongFiles] */
    val id: Long,
    val title: String,
    val artist: String,
    val durationMs: Long,
    val path: String,
    /** who sent it */
    val from: String,
    /** the sender lets friends keep their songs */
    val canSave: Boolean,
    /** already copied into the phone's music */
    val saved: Boolean = false,
    /** of the original file, e.g. "mp3" (the file here doesn't have it: it can arrive before we know) */
    val extension: String? = null,
)

/**
 * The last few songs friends sent: their files, pictures and lyrics, and copying one into
 * the phone's own music ("Save to my phone", only when the sender allows it).
 */
class ReceivedSongs(
    private val context: Context,
    private val lyricsRepo: LyricsRepo,
) {
    private val prefs = context.getSharedPreferences("nearby_received", Context.MODE_PRIVATE)
    private val gson = Gson()

    private val _songs = MutableStateFlow(load())
    /** newest first */
    val songs: StateFlow<List<ReceivedSong>> = _songs.asStateFlow()

    private val lastId = AtomicLong(-System.currentTimeMillis())

    /** a new (negative, unique) id for a song that's arriving */
    fun newId(): Long = lastId.decrementAndGet()

    fun songFile(songId: Long, extension: String): File = ReceivedSongFiles.song(context, songId, extension)

    /** the song file arrived and was written to [ReceivedSong.path]: keep its picture and lyrics too */
    suspend fun add(song: ReceivedSong, artworkJpeg: ByteArray?, lyrics: LyricsDomain?) = withContext(Dispatchers.IO) {
        artworkJpeg?.let { runCatching { ReceivedSongFiles.artwork(context, song.id).writeBytes(it) } }
        lyrics?.let { runCatching { lyricsRepo.saveLyrics(song.id, it) } }
        val all = listOf(song) + _songs.value
        val kept = all.take(KEEP_SONGS)
        // older ones: delete their files (cache space)
        all.drop(KEEP_SONGS).forEach { old ->
            File(old.path).delete()
            ReceivedSongFiles.artwork(context, old.id).delete()
        }
        _songs.value = kept
        persist()
    }

    /** it stopped arriving half-way */
    suspend fun remove(songId: Long) = withContext(Dispatchers.IO) {
        if (_songs.value.none { it.id == songId }) return@withContext
        ReceivedSongFiles.artwork(context, songId).delete()
        _songs.update { list -> list.filterNot { it.id == songId } }
        persist()
    }

    fun find(songId: Long): ReceivedSong? = _songs.value.find { it.id == songId }

    fun toMusicFile(song: ReceivedSong) = MusicFileDomain(
        id = song.id,
        title = song.title,
        artist = song.artist.ifBlank { null },
        // the player plays negative-id songs from this path
        data = song.path,
        duration = song.durationMs,
        icon = Uri.EMPTY,
    )

    /** Android 9 and older need the storage permission to add music files */
    fun needsStoragePermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) !=
                PackageManager.PERMISSION_GRANTED

    /** copies the song into Music/Juzzics, so it's one of the phone's own songs from now on */
    suspend fun saveToLibrary(songId: Long): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val song = find(songId) ?: error("That song isn't here any more")
            check(song.canSave) { "${song.from} didn't allow saving their songs" }
            val source = File(song.path)
            check(source.exists()) { "That song isn't here any more" }
            check(!GrowingFiles.isGrowing(song.path)) { "It's still arriving, try again in a moment" }
            val extension = song.extension?.ifBlank { null } ?: source.extension.takeUnless { it == "audio" || it.isBlank() } ?: "mp3"
            val name = listOf(song.artist, song.title).filter { it.isNotBlank() }.joinToString(" - ")
                .replace(Regex("""[\\/:*?"<>|]"""), " ").take(80).ifBlank { "Song" } + ".$extension"
            val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension.lowercase()) ?: "audio/mpeg"

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val resolver = context.contentResolver
                val values = ContentValues().apply {
                    put(MediaStore.Audio.Media.DISPLAY_NAME, name)
                    put(MediaStore.Audio.Media.MIME_TYPE, mime)
                    put(MediaStore.Audio.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MUSIC}/Juzzics")
                    put(MediaStore.Audio.Media.IS_PENDING, 1)
                }
                val collection = MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                val uri = resolver.insert(collection, values) ?: error("Couldn't create the file")
                try {
                    resolver.openOutputStream(uri)?.use { out -> source.inputStream().use { it.copyTo(out) } }
                        ?: error("Couldn't write the file")
                    resolver.update(uri, ContentValues().apply { put(MediaStore.Audio.Media.IS_PENDING, 0) }, null, null)
                } catch (e: Exception) {
                    resolver.delete(uri, null, null)
                    throw e
                }
                // its lyrics come along
                lyricsRepo.observeSavedLyrics(song.id).first()?.let { lyricsRepo.saveLyrics(ContentUris.parseId(uri), it) }
            } else {
                @Suppress("DEPRECATION")
                val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC), "Juzzics")
                dir.mkdirs()
                val target = File(dir, name)
                source.copyTo(target, overwrite = true)
                MediaScannerConnection.scanFile(context, arrayOf(target.absolutePath), arrayOf(mime), null)
            }
            _songs.update { list -> list.map { if (it.id == songId) it.copy(saved = true) else it } }
            persist()
        }
    }

    private fun persist() = prefs.edit { putString(KEY_SONGS, gson.toJson(_songs.value)) }

    private fun load(): List<ReceivedSong> = runCatching {
        val json = prefs.getString(KEY_SONGS, null) ?: return emptyList()
        val list: List<ReceivedSong> = gson.fromJson(json, object : TypeToken<List<ReceivedSong>>() {}.type)
        // the system may have cleared the cache
        list.filter { File(it.path).exists() }
    }.getOrDefault(emptyList())

    private companion object {
        const val KEY_SONGS = "songs"
        const val KEEP_SONGS = 15
    }
}
