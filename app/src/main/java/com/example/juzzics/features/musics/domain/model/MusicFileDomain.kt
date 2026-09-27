package com.example.juzzics.features.musics.domain.model

import android.net.Uri

data class MusicFileDomain(
    val id: Long,
    val title: String?,
    val artist: String?,
    val data: String?,
    val duration: Long,
    val icon: Uri,
    val isPlaying: Boolean = false,
    /** when the file was added to the device, seconds since epoch (0 if unknown) */
    val dateAdded: Long = 0,
)