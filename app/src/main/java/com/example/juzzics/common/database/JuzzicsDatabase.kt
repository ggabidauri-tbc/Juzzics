package com.example.juzzics.common.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.example.juzzics.features.home.data.local.PlayHistoryDao
import com.example.juzzics.features.home.data.local.PlayHistoryEntity
import com.example.juzzics.features.lyrics.data.local.LyricsDao
import com.example.juzzics.features.lyrics.data.local.LyricsEntity
import com.example.juzzics.features.musics.data.local.SongOrderDao
import com.example.juzzics.features.musics.data.local.SongOrderEntity
import com.example.juzzics.features.playlists.data.local.LikedSongEntity
import com.example.juzzics.features.playlists.data.local.LikedSongsDao
import com.example.juzzics.features.playlists.data.local.PlaylistDao
import com.example.juzzics.features.playlists.data.local.PlaylistEntity
import com.example.juzzics.features.playlists.data.local.PlaylistSongEntity

@Database(
    entities = [
        LyricsEntity::class,
        PlaylistEntity::class,
        PlaylistSongEntity::class,
        PlayHistoryEntity::class,
        SongOrderEntity::class,
        LikedSongEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class JuzzicsDatabase : RoomDatabase() {
    abstract fun lyricsDao(): LyricsDao
    abstract fun playlistDao(): PlaylistDao
    abstract fun playHistoryDao(): PlayHistoryDao
    abstract fun songOrderDao(): SongOrderDao
    abstract fun likedSongsDao(): LikedSongsDao
}
