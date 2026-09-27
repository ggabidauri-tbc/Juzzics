package com.example.juzzics.features.musics.data.localProvider

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.example.juzzics.features.musics.data.model.MusicFileDto

class MusicLocalProviderImpl(private val context: Context) : MusicLocalProvider {
    // on the IO thread: a big library would otherwise freeze the UI while loading
    override suspend fun getAllLocalMusicFiles() = withContext(Dispatchers.IO) { runCatching {
        val contentResolver = context.contentResolver
        val uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} = 1"
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DATE_ADDED,
        )
        val cursor = contentResolver.query(uri, projection, selection, null, null)
        val musicFiles = mutableListOf<MusicFileDto>()
        while (cursor?.moveToNext() == true) {
            val id = cursor.getLong(0)
            val title = cursor.getString(1)
            val artist = cursor.getString(2)
            val album = cursor.getString(3)
            val duration = cursor.getLong(4)
            val albumId = cursor.getLong(5)
            val dateAdded = cursor.getLong(6)

            val iconUri = ContentUris.withAppendedId(
                Uri.parse("content://media/external/audio/albumart"), albumId
            )

            musicFiles.add(
                MusicFileDto(id, title, artist, album, duration, iconUri, dateAdded = dateAdded)
            )
        }
        cursor?.close()
        musicFiles.sortedBy { it.title }
    } }
}
