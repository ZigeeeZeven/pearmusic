package com.endfield.pearmusic.ui.player

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshotFlow
import androidx.documentfile.provider.DocumentFile
import androidx.core.net.toUri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.endfield.pearmusic.api.LrcLibResponse
import com.endfield.pearmusic.api.LrcLibService
import com.endfield.pearmusic.data.local.Song
import com.endfield.pearmusic.lyrics.LyricLine
import com.endfield.pearmusic.lyrics.LrcParser
import com.endfield.pearmusic.playback.PlaybackManager
import com.endfield.pearmusic.util.LyricsEmbedder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class NowPlayingViewModel(
    private val application: Application,
    private val playbackManager: PlaybackManager,
    private val lrcLibService: LrcLibService,
    private val repository: com.endfield.pearmusic.data.MusicRepository
) : AndroidViewModel(application) {
    val currentSong: State<Song?> = playbackManager.currentSong
    val isPlaying: State<Boolean> = playbackManager.isPlaying
    val currentPosition: State<Long> = playbackManager.currentPosition
    val bufferedPosition: State<Long> = playbackManager.bufferedPosition
    val isBuffering: State<Boolean> = playbackManager.isBuffering
    val queue: State<List<Song>> = playbackManager.queue
    val repeatMode: State<Int> = playbackManager.repeatMode

    private val _lyrics = mutableStateOf<List<LyricLine>>(emptyList())
    val lyrics: State<List<LyricLine>> = _lyrics

    private val _isLyricsLoading = mutableStateOf(false)
    val isLyricsLoading: State<Boolean> = _isLyricsLoading

    private val _lyricsDelay = mutableLongStateOf(0L)
    val lyricsDelay: State<Long> = _lyricsDelay

    // UI Event for when lyrics are successfully updated manually
    private val _lyricsUpdateEvent = MutableSharedFlow<Unit>()
    val lyricsUpdateEvent: SharedFlow<Unit> = _lyricsUpdateEvent.asSharedFlow()

    private var loadedLyricsMediaId: String? = null
    private var isManuallyOverridden: Boolean = false

    val activeLyricIndex = derivedStateOf {
        val position = currentPosition.value
        val lines = _lyrics.value
        val delay = _lyricsDelay.value
        if (lines.isEmpty()) -1
        else {
            lines.indexOfLast { (it.startTime + delay) <= position }
        }
    }

    init {
        viewModelScope.launch {
            snapshotFlow { playbackManager.currentSong.value }.collect { song ->
                if (song != null) {
                    if (loadedLyricsMediaId != song.mediaId) {
                        isManuallyOverridden = false
                        _lyricsDelay.longValue = song.lyricsDelay
                        loadLyrics(song)
                    }
                } else {
                    _lyrics.value = emptyList()
                    loadedLyricsMediaId = null
                    isManuallyOverridden = false
                    _lyricsDelay.longValue = 0L
                }
            }
        }
    }

    fun playSong(song: Song, queue: List<Song> = listOf(song)) {
        playbackManager.playSong(song, queue)
    }

    fun skipToNext() {
        playbackManager.skipToNext()
    }

    fun skipToPrevious() {
        playbackManager.skipToPrevious()
    }

    fun toggleRepeatMode() {
        playbackManager.toggleRepeatMode()
    }

    fun loadLyrics(song: Song) {
        if (isManuallyOverridden && loadedLyricsMediaId == song.mediaId) return

        viewModelScope.launch {
            _isLyricsLoading.value = true
            _lyrics.value = emptyList()
            loadedLyricsMediaId = song.mediaId
            
            if (song.uri.startsWith("http")) {
                // Online track: Skip local scanning, go straight to LRCLib
                fetchLyricsFromLrcLib(song)
                return@launch
            }

            val localLyrics = withContext(Dispatchers.IO) {
                try {
                    val uri = song.uri.toUri()
                    var foundContent: String? = null

                    // Priority 1: Sidecar .lrc file
                    if (uri.scheme == "content") {
                        val folderUri = song.folderPath.toUri()
                        val folderDoc = DocumentFile.fromTreeUri(application, folderUri)
                        if (folderDoc != null && folderDoc.isDirectory) {
                            val doc = DocumentFile.fromSingleUri(application, uri)
                            val fileName = doc?.name ?: song.fileName
                            val baseName = if (fileName.contains(".")) fileName.substringBeforeLast(".") else fileName
                            
                            // Try finding by exact filename match first (most efficient)
                            val targetLrcName = "$baseName.lrc"
                            var lrcFile = folderDoc.findFile(targetLrcName)
                            
                            // Fallback to case-insensitive search if not found
                            if (lrcFile == null || !lrcFile.exists()) {
                                lrcFile = folderDoc.listFiles().find { it.name.equals(targetLrcName, ignoreCase = true) }
                            }
                            
                            if (lrcFile != null && lrcFile.exists()) {
                                foundContent = application.contentResolver.openInputStream(lrcFile.uri)?.use { it.bufferedReader().readText() }
                            }
                        }
                    }

                    // Priority 2: Embedded lyrics in the file
                    if (foundContent == null && !song.uri.startsWith("http")) {
                        foundContent = LyricsEmbedder.extractEmbeddedLyrics(application, uri)
                    }
                    
                    foundContent
                } catch (e: Exception) {
                    Log.e("NowPlayingViewModel", "Local lyrics scan failed", e)
                    null
                }
            }

            if (!localLyrics.isNullOrBlank()) {
                Log.d("NowPlayingViewModel", "Found local/embedded lyrics")
                _lyrics.value = LrcParser.parse(localLyrics)
                _isLyricsLoading.value = false
            } else {
                Log.d("NowPlayingViewModel", "No local lyrics found, starting online search")
                fetchLyricsFromLrcLib(song)
            }
        }
    }

    private fun fetchLyricsFromLrcLib(song: Song) {
        viewModelScope.launch {
            try {
                var artist = song.artist
                var title = song.title

                if (song.uri.startsWith("http")) {
                    // Try to extract better title/artist from filename if it's Online Music
                    val fileName = song.fileName.substringBeforeLast('.')
                    if (fileName.contains(" - ")) {
                        artist = fileName.substringBefore(" - ").trim()
                        title = fileName.substringAfter(" - ").trim()
                    } else {
                        title = fileName.trim()
                    }
                }

                // If artist/title is still unknown, exact match will definitely fail
                if (artist == "Unknown Artist" || title.isBlank()) {
                    performSearchFallback(song)
                    return@launch
                }

                val cleanedArtist = artist.replace(';', ',')
                val response = withContext(Dispatchers.IO) {
                    try {
                        lrcLibService.getLyrics(
                            artist = cleanedArtist,
                            title = title,
                            album = if (song.uri.startsWith("http")) null else song.album,
                            duration = (song.duration / 1000).toInt().takeIf { it > 0 }
                        )
                    } catch (e: Exception) {
                        null
                    }
                }
                
                if (currentSong.value?.mediaId == song.mediaId && !isManuallyOverridden) {
                    if (response != null) {
                        applyLyricsToUi(response) 
                        if (!song.uri.startsWith("http")) {
                            embedLyricsInFile(song, response.syncedLyrics ?: response.plainLyrics ?: "")
                        }
                    } else {
                        // Fallback to search if GET returned nothing (no exact match)
                        performSearchFallback(song)
                    }
                }
            } catch (e: Exception) {
                // Unexpected error, try fallback
                if (!isManuallyOverridden) {
                    performSearchFallback(song)
                }
            }
        }
    }

    private fun performSearchFallback(song: Song) {
        viewModelScope.launch {
            try {
                // Create a search query: prioritize Title + Artist
                val query = if (song.artist != "Unknown Artist" && song.artist != "Loading Tags...") {
                    "${song.title} ${song.artist}"
                } else {
                    song.fileName.substringBeforeLast('.')
                }

                Log.d("NowPlayingViewModel", "Performing fallback search for: $query")
                val results = withContext(Dispatchers.IO) {
                    try {
                        lrcLibService.searchLyrics(query)
                    } catch (e: Exception) {
                        Log.e("NowPlayingViewModel", "Search failed", e)
                        emptyList()
                    }
                }
                
                if (results.isNotEmpty() && currentSong.value?.mediaId == song.mediaId && !isManuallyOverridden) {
                    // Take the best match: try to find one with synced lyrics first
                    val bestMatchResult = results.find { it.syncedLyrics != null } 
                        ?: results.find { it.plainLyrics != null }
                        ?: results.first()
                    
                    var bestMatch = bestMatchResult
                    
                    // If the search result is missing lyrics content, fetch the full entry
                    if (bestMatch.syncedLyrics == null && bestMatch.plainLyrics == null) {
                        try {
                            val fullLyrics = withContext(Dispatchers.IO) {
                                lrcLibService.getLyrics(
                                    artist = bestMatch.artistName,
                                    title = bestMatch.name,
                                    album = bestMatch.albumName,
                                    duration = bestMatch.duration?.toInt()
                                )
                            }
                            if (fullLyrics != null) {
                                bestMatch = fullLyrics
                            }
                        } catch (e: Exception) {
                            Log.e("NowPlayingViewModel", "Failed to fetch full lyrics for best match", e)
                        }
                    }

                    if (bestMatch.syncedLyrics != null || bestMatch.plainLyrics != null) {
                        applyLyricsToUi(bestMatch)
                        if (!song.uri.startsWith("http")) {
                            embedLyricsInFile(song, bestMatch.syncedLyrics ?: bestMatch.plainLyrics ?: "")
                        }
                    } else {
                        Log.w("NowPlayingViewModel", "No lyrics content found in best match")
                    }
                }
            } catch (e: Exception) {
                Log.e("NowPlayingViewModel", "Fallback search error", e)
            } finally {
                if (currentSong.value?.mediaId == song.mediaId) {
                    _isLyricsLoading.value = false
                }
            }
        }
    }

    suspend fun performManualSearch(query: String): List<LrcLibResponse> = withContext(Dispatchers.IO) {
        try {
            lrcLibService.searchLyrics(query)
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun applySelectedLyrics(response: LrcLibResponse) {
        val song = currentSong.value ?: return
        
        isManuallyOverridden = true
        loadedLyricsMediaId = song.mediaId
        
        viewModelScope.launch {
            var fullResponse = response
            // If the search result is missing lyrics, try to fetch full lyrics using the specific metadata
            if (response.syncedLyrics == null && response.plainLyrics == null) {
                try {
                    val fetched = withContext(Dispatchers.IO) {
                        lrcLibService.getLyrics(
                            artist = response.artistName,
                            title = response.name,
                            album = response.albumName,
                            duration = response.duration?.toInt()
                        )
                    }
                    if (fetched != null) {
                        fullResponse = fetched
                    }
                } catch (e: Exception) {
                    Log.e("NowPlayingViewModel", "Failed to fetch full lyrics for manual search", e)
                }
            }

            applyLyricsToUi(fullResponse)
            _lyricsUpdateEvent.emit(Unit)
            
            embedLyricsInFile(song, fullResponse.syncedLyrics ?: fullResponse.plainLyrics ?: "")
        }
    }

    private fun applyLyricsToUi(response: LrcLibResponse) {
        viewModelScope.launch {
            val lines = withContext(Dispatchers.IO) {
                response.syncedLyrics?.let { synced ->
                    LrcParser.parse(synced)
                } ?: response.plainLyrics?.let { plain ->
                    plain.lines().mapIndexed { index, s -> LyricLine(index * 2000L, s) }
                } ?: emptyList()
            }
            _lyrics.value = lines
            _isLyricsLoading.value = false
        }
    }

    fun importLyricsFile(song: Song, uri: Uri) {
        viewModelScope.launch {
            val lyricContent = withContext(Dispatchers.IO) {
                try {
                    application.contentResolver.openInputStream(uri)?.use { inputStream ->
                        inputStream.bufferedReader().readText()
                    }
                } catch (e: Exception) {
                    null
                }
            }

            if (lyricContent != null) {
                isManuallyOverridden = true
                loadedLyricsMediaId = song.mediaId
                _lyrics.value = LrcParser.parse(lyricContent)
                _lyricsUpdateEvent.emit(Unit)
                embedLyricsInFile(song, lyricContent)
            }
        }
    }

    private fun embedLyricsInFile(song: Song, content: String) {
        if (content.isBlank() || song.uri.startsWith("http")) return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // EMBED lyrics into the song file itself
                LyricsEmbedder.embedLyrics(application, song.uri, content)
            } catch (e: Exception) {
                Log.e("NowPlayingViewModel", "Failed to embed lyrics", e)
            }
        }
    }

    fun togglePlayPause() {
        playbackManager.togglePlayPause()
    }

    fun seekTo(position: Long) {
        playbackManager.seekTo(position)
    }

    fun reorderQueue(from: Int, to: Int) {
        playbackManager.reorderQueue(from, to)
    }

    fun setLyricsDelay(delay: Long) {
        _lyricsDelay.longValue = delay
        currentSong.value?.let { song ->
            viewModelScope.launch(Dispatchers.IO) {
                repository.updateLyricsDelay(song.id, delay)
            }
        }
    }
}
