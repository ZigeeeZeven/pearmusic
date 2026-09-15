package com.endfield.pearmusic.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [Song::class, Playlist::class, PlaylistSongCrossRef::class, ScanFolder::class], version = 3)
abstract class MusicDatabase : RoomDatabase() {
    abstract fun songDao(): SongDao
    abstract fun scanFolderDao(): ScanFolderDao
    abstract fun playlistDao(): PlaylistDao
}
