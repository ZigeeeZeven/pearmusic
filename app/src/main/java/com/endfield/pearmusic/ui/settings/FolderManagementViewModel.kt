package com.endfield.pearmusic.ui.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.endfield.pearmusic.data.MusicRepository
import com.endfield.pearmusic.data.local.ScanFolder
import com.endfield.pearmusic.data.repository.FolderRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class FolderManagementViewModel(
    private val folderRepository: FolderRepository,
    private val musicRepository: MusicRepository
) : ViewModel() {

    val scanFolders: StateFlow<List<ScanFolder>> = folderRepository.allScanFolders
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun addFolder(uri: Uri, displayName: String) {
        viewModelScope.launch {
            folderRepository.addFolder(uri, displayName)
            refreshLibrary()
        }
    }

    fun removeFolder(scanFolder: ScanFolder) {
        viewModelScope.launch {
            folderRepository.removeFolder(scanFolder)
            refreshLibrary()
        }
    }

    private fun refreshLibrary() {
        viewModelScope.launch {
            val folderUris = folderRepository.getFoldersList().map { Uri.parse(it.uri) }
            musicRepository.syncMusic(folderUris)
        }
    }
}
