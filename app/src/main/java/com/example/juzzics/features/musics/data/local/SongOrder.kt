package com.example.juzzics.features.musics.data.local

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Transaction

/** Your own (drag-to-reorder) position of a song in the song list. */
@Entity(tableName = "song_order")
data class SongOrderEntity(
    @PrimaryKey val songId: Long,
    val position: Int,
)

@Dao
abstract class SongOrderDao {
    @Query("SELECT * FROM song_order")
    abstract suspend fun getAll(): List<SongOrderEntity>

    @Query("DELETE FROM song_order")
    abstract suspend fun deleteAll()

    @Insert
    abstract suspend fun insertAll(order: List<SongOrderEntity>)

    @Transaction
    open suspend fun replaceAll(songIds: List<Long>) {
        deleteAll()
        insertAll(songIds.mapIndexed { index, id -> SongOrderEntity(id, index) })
    }
}
