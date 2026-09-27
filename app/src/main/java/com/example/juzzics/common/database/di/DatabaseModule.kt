package com.example.juzzics.common.database.di

import androidx.room.Room
import com.example.juzzics.common.database.JuzzicsDatabase
import com.example.juzzics.common.songs.SongSettings
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val databaseModule = module {
    single {
        Room.databaseBuilder(androidContext(), JuzzicsDatabase::class.java, "juzzics.db")
            // still in development: a schema change just recreates the database.
            // Before a real release, replace this with proper migrations.
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()
    }
    single { get<JuzzicsDatabase>().lyricsDao() }
    single { get<JuzzicsDatabase>().playlistDao() }
    single { get<JuzzicsDatabase>().playHistoryDao() }
    single { get<JuzzicsDatabase>().songOrderDao() }
    single { get<JuzzicsDatabase>().likedSongsDao() }
    single { SongSettings(androidContext()) }
}
