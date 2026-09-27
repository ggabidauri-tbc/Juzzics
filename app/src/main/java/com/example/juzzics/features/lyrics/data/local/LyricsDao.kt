package com.example.juzzics.features.lyrics.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface LyricsDao {
    @Query("SELECT * FROM lyrics WHERE songId = :songId")
    fun observeLyrics(songId: Long): Flow<LyricsEntity?>

    @Query("SELECT songId FROM lyrics")
    suspend fun songIds(): List<Long>

    @Upsert
    suspend fun save(lyrics: LyricsEntity)
}
