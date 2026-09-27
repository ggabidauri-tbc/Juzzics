package com.example.juzzics.features.playlists.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.example.juzzics.features.musics.domain.model.MusicFileDomain
import kotlinx.coroutines.flow.Flow

@Dao
abstract class PlaylistDao {

    @Transaction
    @Query("SELECT * FROM playlists ORDER BY createdAt")
    abstract fun observePlaylists(): Flow<List<PlaylistWithSongs>>

    @Query("SELECT COUNT(*) FROM playlists")
    abstract suspend fun count(): Int

    @Insert
    abstract suspend fun insertPlaylist(playlist: PlaylistEntity): Long

    @Query("UPDATE playlists SET name = :name WHERE id = :playlistId")
    abstract suspend fun rename(playlistId: Long, name: String)

    @Query("DELETE FROM playlists WHERE id = :playlistId")
    abstract suspend fun deletePlaylistRow(playlistId: Long)

    @Query("DELETE FROM playlist_songs WHERE playlistId = :playlistId")
    abstract suspend fun deleteSongsOf(playlistId: Long)

    @Query("SELECT COALESCE(MAX(position), -1) FROM playlist_songs WHERE playlistId = :playlistId")
    abstract suspend fun lastPosition(playlistId: Long): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertSong(song: PlaylistSongEntity)

    @Query("DELETE FROM playlist_songs WHERE playlistId = :playlistId AND songId = :songId")
    abstract suspend fun removeSong(playlistId: Long, songId: Long)

    @Query("UPDATE playlist_songs SET position = :position WHERE playlistId = :playlistId AND songId = :songId")
    abstract suspend fun setPosition(playlistId: Long, songId: Long, position: Int)

    @Transaction
    open suspend fun deletePlaylist(playlistId: Long) {
        deleteSongsOf(playlistId)
        deletePlaylistRow(playlistId)
    }

    /** adds [song] at the end; does nothing if it's already in the playlist */
    @Transaction
    open suspend fun addSong(playlistId: Long, song: MusicFileDomain) {
        insertSong(song.toPlaylistSong(playlistId, position = lastPosition(playlistId) + 1))
    }

    /** saves the playlist's new order */
    @Transaction
    open suspend fun reorder(playlistId: Long, songIds: List<Long>) {
        songIds.forEachIndexed { index, songId -> setPosition(playlistId, songId, index) }
    }
}
