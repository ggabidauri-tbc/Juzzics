package com.example.juzzics.features.playlists.data.local

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import com.example.juzzics.common.database.songFromColumns
import com.example.juzzics.features.musics.domain.model.MusicFileDomain
import kotlinx.coroutines.flow.Flow

/** A song with a heart (shown as the "Liked songs" playlist). */
@Entity(tableName = "liked_songs")
data class LikedSongEntity(
    @PrimaryKey val songId: Long,
    val likedAt: Long,
    val title: String?,
    val artist: String?,
    val album: String?,
    val duration: Long,
    val iconUri: String,
) {
    fun toDomain() = songFromColumns(songId, title, artist, album, duration, iconUri)
}

fun MusicFileDomain.toLikedSong(likedAt: Long) = LikedSongEntity(
    songId = id,
    likedAt = likedAt,
    title = title,
    artist = artist,
    album = data,
    duration = duration,
    iconUri = icon.toString(),
)

@Dao
interface LikedSongsDao {
    /** newest likes first */
    @Query("SELECT * FROM liked_songs ORDER BY likedAt DESC")
    fun observeAll(): Flow<List<LikedSongEntity>>

    @Query("SELECT songId FROM liked_songs")
    fun observeIds(): Flow<List<Long>>

    @Query("SELECT EXISTS(SELECT 1 FROM liked_songs WHERE songId = :songId)")
    suspend fun isLiked(songId: Long): Boolean

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun like(song: LikedSongEntity)

    @Query("DELETE FROM liked_songs WHERE songId = :songId")
    suspend fun unlike(songId: Long)
}
