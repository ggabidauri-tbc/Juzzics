package com.example.juzzics.features.home.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface PlayHistoryDao {
    @Insert
    suspend fun insert(play: PlayHistoryEntity)

    /** latest play of each song, newest first */
    @Query(
        """
        SELECT * FROM play_history
        WHERE id IN (SELECT MAX(id) FROM play_history GROUP BY songId)
        ORDER BY playedAt DESC
        LIMIT :limit
        """
    )
    fun observeRecentlyPlayed(limit: Int): Flow<List<PlayHistoryEntity>>

    @Query(
        """
        SELECT songId, title, artist, album, duration, iconUri, COUNT(*) AS playCount
        FROM play_history
        GROUP BY songId
        ORDER BY playCount DESC, MAX(playedAt) DESC
        LIMIT :limit
        """
    )
    fun observeMostPlayed(limit: Int): Flow<List<MostPlayedRow>>
}
