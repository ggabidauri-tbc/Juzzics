package com.example.juzzics.features.lyrics.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import com.example.juzzics.common.base.BaseHandler
import com.example.juzzics.common.base.extensions.with2
import com.example.juzzics.common.base.viewModel.Action
import com.example.juzzics.common.base.viewModel.BaseState
import com.example.juzzics.common.base.viewModel.UiEvent
import com.example.juzzics.common.base.viewModel.invoke
import com.example.juzzics.common.base.viewModel.not
import com.example.juzzics.common.util.converters.ofHeight
import com.example.juzzics.features.lyrics.domain.model.LyricsDomain
import com.example.juzzics.features.lyrics.ui.vm.FetchLyricsVM
import kotlinx.coroutines.flow.SharedFlow

@Composable
fun FetchLyricsScreen(
    states: BaseState,
    uiEvent: SharedFlow<UiEvent>,
    onAction: (Action) -> Unit
) {
    with2(first = states, second = FetchLyricsVM) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            uiEvent.BaseHandler {
                Column(
                    Modifier
                        .fillMaxSize()
                        .padding(top = 10.ofHeight()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    TextField(
                        value = !ARTIST,
                        onValueChange = { onAction(FetchLyricsVM.UpdateArtistAction(it)) })
                    TextField(
                        value = !TITLE,
                        onValueChange = { onAction(FetchLyricsVM.UpdateTitleAction(it)) })
                    Button(onClick = { onAction(FetchLyricsVM.FetchLyricsAction) }) {
                        Text("Search Lyrics")
                    }
                    Text(
                        text = LYRICS<LyricsDomain>()?.lyrics.orEmpty(),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.verticalScroll(rememberScrollState(0))
                    )
                }
            }
        }
    }
}
