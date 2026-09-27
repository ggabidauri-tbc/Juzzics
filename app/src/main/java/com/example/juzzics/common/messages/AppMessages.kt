package com.example.juzzics.common.messages

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch

/** A short note at the bottom of the screen, optionally with one action (usually "Undo"). */
data class AppMessage(
    val text: String,
    val actionLabel: String? = null,
    val onAction: (() -> Unit)? = null,
    val long: Boolean = false,
)

/**
 * App-wide snackbars: anything (ViewModels, managers) can say something without a dialog
 * the user has to dismiss. The app's root shows them above the mini player.
 */
object AppMessages {
    private val messages = MutableSharedFlow<AppMessage>(
        extraBufferCapacity = 8,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    fun show(text: String) {
        messages.tryEmit(AppMessage(text))
    }

    /** e.g. "Playlist deleted · Undo" */
    fun showWithUndo(text: String, undo: () -> Unit) {
        messages.tryEmit(AppMessage(text, actionLabel = "Undo", onAction = undo, long = true))
    }

    fun show(message: AppMessage) {
        messages.tryEmit(message)
    }

    /** shows the messages one after another in [host] */
    @Composable
    fun Collect(host: SnackbarHostState) {
        LaunchedEffect(host) {
            messages.collect { message ->
                // a new message replaces the one showing (no queue of old news)
                host.currentSnackbarData?.dismiss()
                launch {
                    val result = host.showSnackbar(
                        message = message.text,
                        actionLabel = message.actionLabel,
                        withDismissAction = message.actionLabel == null && message.long,
                        duration = if (message.long || message.actionLabel != null) SnackbarDuration.Long else SnackbarDuration.Short
                    )
                    if (result == SnackbarResult.ActionPerformed) message.onAction?.invoke()
                }
            }
        }
    }
}
