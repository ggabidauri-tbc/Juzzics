package com.example.juzzics.features.musics.di

import com.example.juzzics.features.home.vm.HomeVM
import com.example.juzzics.features.musics.ui.vm.MusicVM
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val musicViewModelsModule = module {
    viewModelOf(::MusicVM)
    viewModelOf(::HomeVM)
}