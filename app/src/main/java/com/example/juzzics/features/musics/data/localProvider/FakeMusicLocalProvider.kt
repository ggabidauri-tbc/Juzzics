package com.example.juzzics.features.musics.data.localProvider

import android.content.ContentUris
import android.net.Uri
import com.example.juzzics.features.musics.data.model.MusicFileDto

/**
 * Debug helper: wraps the real provider and returns a few made-up songs when the device
 * has no music (e.g. a fresh emulator), so the Musics screen can be tested.
 *
 * The fake songs can't actually play (the player has no file for them), so there's no sound,
 * no progress, and no auto-next. Scenes, swipes, lyrics search etc. all work.
 */
class FakeMusicLocalProvider(private val real: MusicLocalProvider) : MusicLocalProvider {

    override suspend fun getAllLocalMusicFiles(): Result<List<MusicFileDto>> =
        real.getAllLocalMusicFiles().map { songs -> songs.ifEmpty { fakeSongs } }

    private val fakeSongs = listOf(
        fake(1, "Ghost Love Score", "Nightwish", 10 * 60 + 2),
        fake(2, "Nothing Else Matters", "Metallica", 6 * 60 + 28),
        fake(3, "Bohemian Rhapsody", "Queen", 5 * 60 + 55),
        fake(4, "Wish You Were Here", "Pink Floyd", 5 * 60 + 34),
        fake(5, "Hurt", "Johnny Cash", 3 * 60 + 38),
        fake(6, "Shape of My Heart", "Sting", 4 * 60 + 38),
    )

    private fun fake(id: Long, title: String, artist: String, seconds: Int) = MusicFileDto(
        id = -id, // negative so it can never match a real MediaStore id
        title = title,
        artist = artist,
        data = "Fake album",
        duration = seconds * 1000L,
        icon = ContentUris.withAppendedId(Uri.parse("content://media/external/audio/albumart"), 0),
        dateAdded = 1_700_000_000L + id * 86_400, // one "day" apart, so recently-added sort differs
    )
}
