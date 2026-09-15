package com.endfield.pearmusic.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "scan_folders")
data class ScanFolder(
    @PrimaryKey val uri: String,
    val displayName: String,
    val dateAdded: Long = System.currentTimeMillis()
)
