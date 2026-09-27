package com.example.juzzics.features.musics.data.model

import android.net.Uri
import com.example.juzzics.features.musics.domain.model.MusicFileDomain

data class MusicFileDto(
    val id: Long,
    val title: String?,
    val artist: String?,
    val data: String?,
    val duration: Long,
    val icon: Uri,
    val isPlaying: Boolean = false,
    val dateAdded: Long = 0,
)

fun MusicFileDto.toDomain() =
    MusicFileDomain(id, title, artist, data, duration, icon, isPlaying, dateAdded)