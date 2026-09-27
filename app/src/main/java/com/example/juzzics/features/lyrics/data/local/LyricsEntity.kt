package com.example.juzzics.features.lyrics.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Lyrics tied to a song (by MediaStore id). [synced] is LRC text with timestamps, if known. */
@Entity(tableName = "lyrics")
data class LyricsEntity(
    @PrimaryKey val songId: Long,
    val lyrics: String,
    val synced: String? = null,
)
