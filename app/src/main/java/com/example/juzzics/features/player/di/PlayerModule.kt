package com.example.juzzics.features.player.di

import com.example.juzzics.features.player.PlayerController
import com.example.juzzics.features.player.ui.vm.PlayerVM
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val playerModule = module {
    single { PlayerController(androidContext(), get()) }
    viewModelOf(::PlayerVM)
}
