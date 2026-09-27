package com.example.juzzics.common.database

import android.net.Uri
import com.example.juzzics.features.musics.domain.model.MusicFileDomain

/**
 * Song details saved in the database next to playlists/history, so those lists can show
 * songs without querying the device's MediaStore again.
 * Entities store these as plain columns: songId, title, artist, album, duration, iconUri.
 */
fun songFromColumns(
    songId: Long,
    title: String?,
    artist: String?,
    album: String?,
    duration: Long,
    iconUri: String,
) = MusicFileDomain(
    id = songId,
    title = title,
    artist = artist,
    data = album,
    duration = duration,
    icon = Uri.parse(iconUri),
)
