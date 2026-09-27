package com.example.juzzics.features.lyrics.data.service

import com.example.juzzics.features.lyrics.data.dto.LrclibDto
import retrofit2.http.GET
import retrofit2.http.Query

/** LRCLIB: free lyrics database with time-synced lyrics, no API key */
interface LrclibService {
    /** by title (+ artist; null leaves it out) */
    @GET("api/search")
    suspend fun search(
        @Query("track_name") title: String,
        @Query("artist_name") artist: String?,
    ): List<LrclibDto>

    /** free text, matched against title, artist and album */
    @GET("api/search")
    suspend fun searchText(@Query("q") query: String): List<LrclibDto>
}
