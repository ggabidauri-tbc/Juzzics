package com.example.juzzics.features.musics.ui.model

import android.net.Uri
import android.os.Parcelable
import com.example.juzzics.features.musics.domain.model.MusicFileDomain
import kotlinx.parcelize.Parcelize

@Parcelize
data class MusicFileUi(
    val id: Long,
    val title: String?,
    val artist: String?,
    val data: String?,
    val duration: Long,
    val icon: Uri,
    val isPlaying: Boolean = false,
    val lyrics: String = "",
    val dateAdded: Long = 0,
    /** LRC text of time-synced lyrics, if known */
    val syncedLyrics: String? = null,
) : Parcelable

fun MusicFileDomain.toUi() =
    MusicFileUi(id, title, artist, data, duration, icon, isPlaying, dateAdded = dateAdded)

fun MusicFileUi.toDomain() =
    MusicFileDomain(id, title, artist, data, duration, icon, isPlaying, dateAdded)

/** MediaStore uses "<unknown>" for songs without an artist */
val MusicFileUi.knownArtist: String
    get() = artist?.takeUnless { it.isBlank() || it == "<unknown>" }.orEmpty()
