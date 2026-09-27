package com.example.juzzics.features.musics.ui.components

/** 185000 -> "3:05" */
fun Long.toClock(): String {
    val totalSeconds = (this / 1000).coerceAtLeast(0)
    return "%d:%02d".format(totalSeconds / 60, totalSeconds % 60)
}
