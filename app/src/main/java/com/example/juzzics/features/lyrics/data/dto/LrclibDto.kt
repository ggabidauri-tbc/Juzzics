package com.example.juzzics.features.lyrics.data.dto

import com.google.gson.annotations.SerializedName

/** one result of https://lrclib.net/api/search */
data class LrclibDto(
    @SerializedName("id") val id: Long?,
    @SerializedName("trackName") val trackName: String?,
    @SerializedName("artistName") val artistName: String?,
    /** seconds */
    @SerializedName("duration") val duration: Double?,
    @SerializedName("instrumental") val instrumental: Boolean?,
    @SerializedName("plainLyrics") val plainLyrics: String?,
    @SerializedName("syncedLyrics") val syncedLyrics: String?,
)
