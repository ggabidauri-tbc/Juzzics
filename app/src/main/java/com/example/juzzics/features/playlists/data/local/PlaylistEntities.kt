package com.example.juzzics.features.playlists.data.local

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation
import com.example.juzzics.common.database.songFromColumns
import com.example.juzzics.features.musics.domain.model.MusicFileDomain

@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long,
)

/** A song in a playlist; [position] is its place in the playlist's order. */
@Entity(
    tableName = "playlist_songs",
    primaryKeys = ["playlistId", "songId"],
    indices = [Index("playlistId")],
)
data class PlaylistSongEntity(
    val playlistId: Long,
    val songId: Long,
    val position: Int,
    val title: String?,
    val artist: String?,
    val album: String?,
    val duration: Long,
    val iconUri: String,
) {
    fun toDomain() = songFromColumns(songId, title, artist, album, duration, iconUri)
}

fun MusicFileDomain.toPlaylistSong(playlistId: Long, position: Int) = PlaylistSongEntity(
    playlistId = playlistId,
    songId = id,
    position = position,
    title = title,
    artist = artist,
    album = data,
    duration = duration,
    iconUri = icon.toString(),
)

data class PlaylistWithSongs(
    @Embedded val playlist: PlaylistEntity,
    @Relation(parentColumn = "id", entityColumn = "playlistId")
    val songs: List<PlaylistSongEntity>,
)
