package com.example.juzzics.features.musics.ui.components

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.core.graphics.drawable.toBitmap
import androidx.palette.graphics.Palette
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * A calm background color taken from the song's artwork (dark or light to match the theme),
 * or null while loading / when there's no artwork.
 */
@Composable
fun rememberArtworkColor(artwork: Any?): Color? {
    val context = LocalContext.current
    val darkTheme = isSystemInDarkTheme()
    var color by remember(artwork) { mutableStateOf<Color?>(null) }

    LaunchedEffect(artwork, darkTheme) {
        if (artwork == null) return@LaunchedEffect
        val request = ImageRequest.Builder(context)
            .data(artwork)
            .allowHardware(false) // Palette needs to read the pixels
            .size(128)
            .build()
        val drawable = (context.imageLoader.execute(request) as? SuccessResult)?.drawable
            ?: return@LaunchedEffect
        val palette = withContext(Dispatchers.Default) { Palette.from(drawable.toBitmap()).generate() }
        val swatch = if (darkTheme) {
            palette.darkMutedSwatch ?: palette.darkVibrantSwatch ?: palette.dominantSwatch
        } else {
            palette.lightMutedSwatch ?: palette.lightVibrantSwatch ?: palette.dominantSwatch
        }
        color = swatch?.rgb?.let { Color(it) }
    }
    return color
}
