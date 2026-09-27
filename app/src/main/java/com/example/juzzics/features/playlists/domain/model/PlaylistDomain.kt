package com.example.juzzics.features.playlists.domain.model

import com.example.juzzics.features.musics.domain.model.MusicFileDomain

data class PlaylistDomain(
    val id: String,
    val name: String,
    val songs: List<MusicFileDomain> = emptyList()
)
