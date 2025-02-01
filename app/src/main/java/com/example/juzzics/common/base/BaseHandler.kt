package com.example.juzzics.common.base

import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.juzzics.common.base.viewModel.UiEvent
import com.example.juzzics.common.util.converters.ofHeight
import kotlinx.coroutines.flow.SharedFlow

/** handles showing message and showing loader. */
@Composable
fun SharedFlow<UiEvent>.BaseHandler(
    showLoader: Boolean = true,
    content: @Composable () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val loadingState = remember { mutableStateOf(false) }
    LaunchedEffect(key1 = this@BaseHandler) {
        this@BaseHandler.collect {
            when (it) {
                is UiEvent.Message -> Toast.makeText(context, it.msg, Toast.LENGTH_SHORT).show()
                is UiEvent.Loading -> if (showLoader) loadingState.value = it.loading
            }
        }
    }
    LoaderBox(loading = { loadingState.value }, modifier, content)
}

@Composable
fun LoaderBox(
    loading: () -> Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    AnimatedContent(
        targetState = loading(),
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
        transitionSpec = {
            fadeIn() togetherWith fadeOut()
        }, label = ""
    ) { isLoading ->
        if (isLoading) LoadingContent() else content()
    }
}

@Composable
fun LoadingContent() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(bottom = 35.ofHeight())
    ) {
        CircularProgressIndicator(
            modifier = Modifier
                .size(40.dp)
                .align(Alignment.Center),
            strokeCap = StrokeCap.Butt,
            strokeWidth = 2.dp,
            color = Color.Green,
            trackColor = Color.Blue
        )
    }
}
