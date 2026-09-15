package com.endfield.pearmusic.ui.library

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.endfield.pearmusic.data.MusicRepository
import com.endfield.pearmusic.data.local.AlbumInfo
import com.endfield.pearmusic.data.local.FolderInfo
import com.endfield.pearmusic.data.local.Playlist
import com.endfield.pearmusic.data.local.PlaylistInfo
import com.endfield.pearmusic.data.local.PlaylistWithSongs
import com.endfield.pearmusic.data.local.Song
import com.endfield.pearmusic.data.repository.FolderRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class LibraryViewModel(
    private val repository: MusicRepository,
    private val folderRepository: FolderRepository
) : ViewModel() {

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _scanEvent = MutableSharedFlow<String>()
    val scanEvent: SharedFlow<String> = _scanEvent.asSharedFlow()

    val songs: StateFlow<List<Song>> = repository.allSongs
        .map { list -> list.filter { !it.mediaId.startsWith("online_") } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val albumsInfo: StateFlow<List<AlbumInfo>> = repository.allAlbumsInfo
        .map { list -> list.filter { info -> !info.representativeSongUri.startsWith("http") } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val artists: StateFlow<List<String>> = repository.allArtists
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val playlists: StateFlow<List<Playlist>> = repository.allPlaylists
        .map { list -> list.filter { p -> !p.name.startsWith("online_") } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val playlistsInfo: StateFlow<List<PlaylistInfo>> = repository.allPlaylistsInfo
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val folders: StateFlow<List<FolderInfo>> = repository.allFolders
        .map { list -> list.filter { it.folderPath != "online_drive" && it.folderPath != "online_m3u8" } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /**
     * Refactored Fast Refresh:
     * - Only runs the lightweight index sync.
     * - Completely skips background per-song MediaMetadataRetriever looping.
     * - Keeps disk and network I/O zeroed out until a track is played.
     */
    fun refreshLibrary() {
        if (_isScanning.value) return

        viewModelScope.launch {
            _isScanning.value = true
            try {
                val folderUris = folderRepository.getFoldersList().map { Uri.parse(it.uri) }
                repository.syncMusic(folderUris)
                _scanEvent.emit("Scan completed")
            } catch (e: Exception) {
                _scanEvent.emit("Scan failed: ${e.message}")
            } finally {
                _isScanning.value = false
            }
        }
    }

    fun getSongsByAlbum(album: String) = repository.getSongsByAlbum(album)
    fun getSongsByArtist(artist: String) = repository.getSongsByArtist(artist)
    fun getSongsByFolder(folderPath: String) = repository.getSongsByFolder(folderPath)
    fun getPlaylistWithSongs(playlistId: Long) = repository.getPlaylistWithSongs(playlistId)
    fun getOrderedSongsInPlaylist(playlistId: Long) = repository.getOrderedSongsInPlaylist(playlistId)

    fun createPlaylist(name: String) {
        viewModelScope.launch {
            repository.createPlaylist(name)
        }
    }

    fun updatePlaylist(playlist: Playlist) {
        viewModelScope.launch {
            repository.updatePlaylist(playlist)
        }
    }

    fun deletePlaylist(playlist: Playlist) {
        viewModelScope.launch {
            repository.deletePlaylist(playlist)
        }
    }

    fun addSongToPlaylist(playlistId: Long, songId: Long) {
        viewModelScope.launch {
            repository.addSongToPlaylist(playlistId, songId)
        }
    }

    fun addSongsToPlaylist(playlistId: Long, songIds: List<Long>) {
        viewModelScope.launch {
            songIds.forEach { songId ->
                repository.addSongToPlaylist(playlistId, songId)
            }
        }
    }

    fun removeSongFromPlaylist(playlistId: Long, songId: Long) {
        viewModelScope.launch {
            repository.removeSongFromPlaylist(playlistId, songId)
        }
    }

    fun reorderPlaylist(playlistId: Long, fromIndex: Int, toIndex: Int) {
        viewModelScope.launch {
            repository.reorderPlaylist(playlistId, fromIndex, toIndex)
        }
    }

    fun importM3u8Playlist(uri: Uri) {
        viewModelScope.launch {
            try {
                _isScanning.value = true
                val result = repository.importM3u8(uri)
                if (result) {
                    _scanEvent.emit("Playlist imported successfully")
                } else {
                    _scanEvent.emit("Could not match any songs in playlist")
                }
            } catch (e: Exception) {
                _scanEvent.emit("Import failed: ${e.message}")
            } finally {
                _isScanning.value = false
            }
        }
    }
}