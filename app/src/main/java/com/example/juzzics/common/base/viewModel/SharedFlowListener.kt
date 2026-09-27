package com.example.juzzics.common.base.viewModel

import android.annotation.SuppressLint
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.flow.Flow

/**
 * Collects a ViewModel's [UiEvent]s in a Composable. Events go to a single collector, so use
 * either this or [com.example.juzzics.common.base.BaseHandler] per screen, not both.
 */
@SuppressLint("ComposableNaming")
@Composable
fun Flow<UiEvent>.listen(onCollect: suspend (UiEvent) -> Unit) {
    LaunchedEffect(this) {
        collect { onCollect(it) }
    }
}
