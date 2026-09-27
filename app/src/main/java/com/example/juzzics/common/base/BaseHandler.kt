package com.example.juzzics.common.base

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.juzzics.common.base.viewModel.UiEvent
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow

/**
 * Shows a loader instead of [content] while [loading], toasts [UiEvent.Message]s and passes
 * every other event to [onEvent]. It's the screen's one collector of its ViewModel's events.
 */
@Composable
fun Flow<UiEvent>.BaseHandler(
    loading: Boolean,
    modifier: Modifier = Modifier,
    showLoader: Boolean = true,
    onEvent: suspend (UiEvent) -> Unit = {},
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val currentOnEvent by rememberUpdatedState(onEvent)
    LaunchedEffect(this) {
        collect {
            when (it) {
                is UiEvent.Message -> Toast.makeText(context, it.msg, Toast.LENGTH_SHORT).show()
                else -> currentOnEvent(it)
            }
        }
    }
    LoaderBox(loading = { showLoader && loading }, modifier, content)
}

/**
 * [content] stays on screen; while [loading] a small spinner shows on top of it. It only
 * appears if loading takes longer than a moment, so quick loads don't flash.
 */
@Composable
fun LoaderBox(
    loading: () -> Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val isLoading = loading()
    var showSpinner by remember { mutableStateOf(false) }
    LaunchedEffect(isLoading) {
        if (isLoading) {
            delay(300)
            showSpinner = true
        } else {
            showSpinner = false
        }
    }
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        content()
        AnimatedVisibility(
            visible = showSpinner,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            CircularProgressIndicator(
                modifier = Modifier
                    .padding(top = 24.dp)
                    .size(32.dp),
                strokeCap = StrokeCap.Round,
                strokeWidth = 3.dp,
            )
        }
    }
}
