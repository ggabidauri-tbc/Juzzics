package com.example.juzzics.common.songs

import android.content.Context
import java.io.File

/**
 * Where songs friends sent over Nearby are kept (app cache). Those songs have negative ids,
 * so they never mix with the phone's own (MediaStore) songs.
 */
object ReceivedSongFiles {
    fun dir(context: Context): File = File(context.cacheDir, "nearby_songs").apply { mkdirs() }

    fun song(context: Context, songId: Long, extension: String): File =
        File(dir(context), "song_${-songId}.$extension")

    fun artwork(context: Context, songId: Long): File = File(dir(context), "art_${-songId}.jpg")
}
