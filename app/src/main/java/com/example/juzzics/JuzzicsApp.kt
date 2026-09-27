package com.example.juzzics

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import com.example.juzzics.common.artwork.SongArtwork
import com.example.juzzics.common.artwork.SongArtworkFetcher
import com.example.juzzics.common.artwork.SongArtworkKeyer
import com.example.juzzics.common.database.di.databaseModule
import com.example.juzzics.features.home.di.homeRepoModule
import com.example.juzzics.features.home.di.homeUseCasesModule
import com.example.juzzics.features.home.di.homeViewModelsModule
import com.example.juzzics.features.lyrics.di.lyricsRepoModule
import com.example.juzzics.features.lyrics.di.lyricsUseCasesModule
import com.example.juzzics.features.lyrics.di.serviceModule
import com.example.juzzics.features.musics.di.musicLocalDataModule
import com.example.juzzics.features.musics.di.musicRepoModule
import com.example.juzzics.features.musics.di.musicUseCasesModule
import com.example.juzzics.features.musics.di.musicViewModelsModule
import com.example.juzzics.features.nearby.di.nearbyModule
import com.example.juzzics.features.player.di.playerModule
import com.example.juzzics.features.playlists.di.playlistsRepoModule
import com.example.juzzics.features.playlists.di.playlistsUseCasesModule
import com.example.juzzics.features.playlists.di.playlistsViewModelsModule
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class JuzzicsApp : Application(), ImageLoaderFactory {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(applicationContext)
            modules(
                listOf(databaseModule, playerModule, nearbyModule) +
                        musicsModules() + lyricsModules() + playlistsModules() + homeModules()
            )
        }
    }

    /** Coil: loads song artwork (see [SongArtwork]) and fades images in */
    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .components {
            add(SongArtworkKeyer())
            add(SongArtworkFetcher.Factory())
        }
        .crossfade(true)
        .build()

    private fun musicsModules() =
        listOf(musicLocalDataModule, musicRepoModule, musicUseCasesModule, musicViewModelsModule)

    private fun lyricsModules() =
        listOf(lyricsRepoModule, serviceModule, lyricsUseCasesModule)

    private fun playlistsModules() =
        listOf(playlistsRepoModule, playlistsUseCasesModule, playlistsViewModelsModule)

    private fun homeModules() =
        listOf(homeRepoModule, homeUseCasesModule, homeViewModelsModule)
}
