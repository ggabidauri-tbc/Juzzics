package com.example.juzzics.features.nearby.di

import com.example.juzzics.features.nearby.data.NearbyManager
import com.example.juzzics.features.nearby.data.OfflineMaps
import com.example.juzzics.features.nearby.data.ReceivedSongs
import com.example.juzzics.features.nearby.ui.vm.NearbyVM
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val nearbyModule = module {
    single { ReceivedSongs(androidContext(), get()) }
    single { NearbyManager(androidContext(), get(), get(), get(), get()) }
    single { OfflineMaps(androidContext()) }
    viewModelOf(::NearbyVM)
}
