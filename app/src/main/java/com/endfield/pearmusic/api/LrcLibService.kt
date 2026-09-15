package com.endfield.pearmusic.api

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import retrofit2.http.GET
import retrofit2.http.Query

@JsonClass(generateAdapter = true)
data class LrcLibResponse(
    val id: Long,
    val name: String,
    val artistName: String,
    val albumName: String?,
    val duration: Double?,
    val instrumental: Boolean,
    val plainLyrics: String?,
    val syncedLyrics: String?
)

/**
 * LRCLIB API Client
 * Target: https://lrclib.net/api/
 * Note: A custom User-Agent header is required for all requests.
 */
interface LrcLibService {
    @GET("get")
    suspend fun getLyrics(
        @Query("artist_name") artist: String,
        @Query("track_name") title: String,
        @Query("album_name") album: String? = null,
        @Query("duration") duration: Int? = null
    ): LrcLibResponse?

    @GET("search")
    suspend fun searchLyrics(
        @Query("q") query: String
    ): List<LrcLibResponse>
}
