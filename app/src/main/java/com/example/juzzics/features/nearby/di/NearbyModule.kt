package com.example.juzzics.features.nearby.di

import com.example.juzzics.features.nearby.data.NearbyManager
import com.example.juzzics.features.nearby.ui.vm.NearbyVM
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val nearbyModule = module {
    single { NearbyManager(androidContext(), get(), get()) }
    viewModelOf(::NearbyVM)
}
