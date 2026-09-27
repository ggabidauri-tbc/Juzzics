package com.example.juzzics

import android.app.Application
import com.example.juzzics.features.lyrics.di.lyricsRepoModule
import com.example.juzzics.features.lyrics.di.lyricsUseCasesModule
import com.example.juzzics.features.lyrics.di.lyricsVmModule
import com.example.juzzics.features.lyrics.di.serviceModule
import com.example.juzzics.features.musics.di.musicLocalDataModule
import com.example.juzzics.features.musics.di.musicRepoModule
import com.example.juzzics.features.musics.di.musicUseCasesModule
import com.example.juzzics.features.musics.di.musicViewModelsModule
import com.example.juzzics.features.playlists.di.playlistsRepoModule
import com.example.juzzics.features.playlists.di.playlistsUseCasesModule
import com.example.juzzics.features.playlists.di.playlistsViewModelsModule
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import org.koin.core.module.Module

class JuzzicsApp : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(applicationContext)
            modules(
                mutableListOf<Module>().apply {
                    addAll(musicsModules())
                    addAll(lyricsModules())
                    addAll(playlistsModules())
                }.toList()
            )
        }
    }

    private fun musicsModules() =
        listOf(musicLocalDataModule, musicRepoModule, musicUseCasesModule, musicViewModelsModule)

    private fun lyricsModules() =
        listOf(lyricsRepoModule, serviceModule, lyricsUseCasesModule, lyricsVmModule)

    private fun playlistsModules() =
        listOf(playlistsRepoModule, playlistsUseCasesModule, playlistsViewModelsModule)
}
