package com.endfield.pearmusic.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.endfield.pearmusic.data.local.Song
import com.endfield.pearmusic.data.repository.DriveAudioRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class OnlineMusicViewModel(
    private val driveRepository: DriveAudioRepository
) : ViewModel() {

    private val _onlineSongs = MutableStateFlow<List<Song>>(emptyList())
    val onlineSongs: StateFlow<List<Song>> = _onlineSongs.asStateFlow()

    // Deduplicated list for "All Songs" view - excludes playlist-specific duplicates
    val onlineSongsOnly: StateFlow<List<Song>> = onlineSongs
        .map { list -> list.filter { it.folderPath != "online_m3u8" } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    val onlineAlbums = onlineSongs.combine(MutableStateFlow(Unit)) { songs, _ ->
        songs.groupBy { it.album }.keys.toList().sorted()
    }

    val onlineArtists = onlineSongs.combine(MutableStateFlow(Unit)) { songs, _ ->
        songs.groupBy { it.artist }.keys.toList().sorted()
    }

    val onlinePlaylists = onlineSongs.combine(MutableStateFlow(Unit)) { songs, _ ->
        // Playlists are tracks where folderPath is "online_m3u8"
        songs.filter { it.folderPath == "online_m3u8" }
            .groupBy { it.folderName }
            .keys.toList().sorted()
    }

    init {
        refreshOnlineMusic()
    }

    fun refreshOnlineMusic() {
        viewModelScope.launch {
            _isLoading.value = true
            driveRepository.getOnlineTracks(forceRefresh = false).collect { tracks ->
                _onlineSongs.value = tracks
                _isLoading.value = false
            }
        }
    }
}
