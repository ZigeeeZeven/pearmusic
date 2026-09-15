package com.endfield.pearmusic.data.repository

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.endfield.pearmusic.data.local.ScanFolder
import com.endfield.pearmusic.data.local.ScanFolderDao
import kotlinx.coroutines.flow.Flow

class FolderRepository(
    private val context: Context,
    private val scanFolderDao: ScanFolderDao
) {
    val allScanFolders: Flow<List<ScanFolder>> = scanFolderDao.getAllScanFolders()

    suspend fun addFolder(uri: Uri, displayName: String) {
        // Take persistable permission
        context.contentResolver.takePersistableUriPermission(
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        )
        
        val scanFolder = ScanFolder(
            uri = uri.toString(),
            displayName = displayName
        )
        scanFolderDao.insert(scanFolder)
    }

    suspend fun removeFolder(scanFolder: ScanFolder) {
        val uri = Uri.parse(scanFolder.uri)
        try {
            context.contentResolver.releasePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        } catch (e: Exception) {
            // Permission might have been already released or not found
        }
        scanFolderDao.delete(scanFolder)
    }

    suspend fun getFoldersList(): List<ScanFolder> {
        return scanFolderDao.getAllScanFoldersList()
    }
}
