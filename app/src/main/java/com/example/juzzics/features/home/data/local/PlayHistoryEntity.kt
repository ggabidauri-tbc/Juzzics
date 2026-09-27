package com.example.juzzics.features.home.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.juzzics.common.database.songFromColumns
import com.example.juzzics.features.musics.domain.model.MusicFileDomain

/** One play of a song. */
@Entity(tableName = "play_history", indices = [Index("songId")])
data class PlayHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val songId: Long,
    val playedAt: Long,
    val title: String?,
    val artist: String?,
    val album: String?,
    val duration: Long,
    val iconUri: String,
) {
    fun toDomain() = songFromColumns(songId, title, artist, album, duration, iconUri)
}

fun MusicFileDomain.toPlay(playedAt: Long) = PlayHistoryEntity(
    songId = id,
    playedAt = playedAt,
    title = title,
    artist = artist,
    album = data,
    duration = duration,
    iconUri = icon.toString(),
)

data class MostPlayedRow(
    val songId: Long,
    val title: String?,
    val artist: String?,
    val album: String?,
    val duration: Long,
    val iconUri: String,
    val playCount: Int,
) {
    fun toDomain() = songFromColumns(songId, title, artist, album, duration, iconUri)
}
