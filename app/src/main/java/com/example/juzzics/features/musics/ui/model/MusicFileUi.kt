package com.example.juzzics.features.musics.ui.model

import android.net.Uri
import android.os.Parcelable
import androidx.navigation.NavType
import com.example.juzzics.features.musics.domain.model.MusicFileDomain
import com.google.gson.annotations.SerializedName
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
    val isDragged: Boolean = false,
    val lyrics: String = ""
) : Parcelable

fun MusicFileDomain.toUi() = MusicFileUi(id, title, artist, data, duration, icon, isPlaying)