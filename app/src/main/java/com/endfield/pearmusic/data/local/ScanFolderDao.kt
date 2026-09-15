package com.endfield.pearmusic.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ScanFolderDao {
    @Query("SELECT * FROM scan_folders ORDER BY dateAdded DESC")
    fun getAllScanFolders(): Flow<List<ScanFolder>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(scanFolder: ScanFolder)

    @Delete
    suspend fun delete(scanFolder: ScanFolder)

    @Query("SELECT * FROM scan_folders")
    suspend fun getAllScanFoldersList(): List<ScanFolder>
}
