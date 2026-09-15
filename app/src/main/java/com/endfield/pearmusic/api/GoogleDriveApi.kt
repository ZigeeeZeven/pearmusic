package com.endfield.pearmusic.api

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import retrofit2.http.GET
import retrofit2.http.Query

@JsonClass(generateAdapter = true)
data class DriveFileListResponse(
    val files: List<DriveFile>
)

@JsonClass(generateAdapter = true)
data class DriveFile(
    val id: String,
    val name: String,
    val mimeType: String,
    val thumbnailLink: String? = null,
    val size: String? = null
)

interface GoogleDriveApi {
    @GET("files")
    suspend fun listFiles(
        @Query("q") query: String,
        @Query("fields") fields: String = "files(id, name, mimeType, thumbnailLink, size)",
        @Query("key") apiKey: String? = null,
        @Query("pageSize") pageSize: Int = 1000
    ): DriveFileListResponse
}
