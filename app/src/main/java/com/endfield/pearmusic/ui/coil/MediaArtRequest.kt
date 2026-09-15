package com.endfield.pearmusic.ui.coil

data class MediaArtRequest(
    val uri: String?,
    val folderPath: String?,
    val coverArtUrl: String? = null,
    val albumId: Long? = null
)
