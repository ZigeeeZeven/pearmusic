package com.endfield.pearmusic.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "songs",
    indices = [
        Index(value = ["mediaId"], unique = true),
        Index(value = ["uri"]),
        Index(value = ["album"]),
        Index(value = ["artist"]),
        Index(value = ["folderPath"])
    ]
)
data class Song(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val mediaId: String,
    val title: String,
    val artist: String,
    val album: String,
    val uri: String,
    val fileName: String = "",
    val duration: Long,
    val folderPath: String,
    val folderName: String,
    val samplingRate: Int? = null,
    val bitDepth: Int? = null,
    val bitrate: Int? = null,
    val format: String? = null,
    val isLossless: Boolean = false,
    val isFloat: Boolean = false,
    val replayGain: Float? = null,
    val coverArtUrl: String? = null,
    val albumId: Long? = null,
    val lyricsDelay: Long = 0L
)
