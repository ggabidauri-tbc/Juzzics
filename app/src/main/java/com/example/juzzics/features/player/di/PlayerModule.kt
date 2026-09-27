package com.example.juzzics.features.player.di

import com.example.juzzics.features.player.PlayerController
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val playerModule = module {
    single { PlayerController(androidContext(), get()) }
}
