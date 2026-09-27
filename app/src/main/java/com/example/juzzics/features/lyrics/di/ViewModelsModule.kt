package com.example.juzzics.features.lyrics.di

import com.example.juzzics.features.lyrics.ui.vm.FetchLyricsVM
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val lyricsVmModule = module {
    viewModelOf(::FetchLyricsVM)
}
