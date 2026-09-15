package com.endfield.pearmusic.ui.lyrics

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.endfield.pearmusic.api.LrcLibService
import com.endfield.pearmusic.data.MusicRepository
import com.endfield.pearmusic.data.local.Song
import com.endfield.pearmusic.lyrics.LrcParser
import com.endfield.pearmusic.lyrics.LyricLine
import com.endfield.pearmusic.playback.PlaybackManager
import com.endfield.pearmusic.util.LyricsEmbedder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class LyricsEditorViewModel(
    private val application: Application,
    private val repository: MusicRepository,
    private val playbackManager: PlaybackManager,
    private val lrcLibService: LrcLibService,
    private val songMediaId: String
) : AndroidViewModel(application) {

    private val _song = mutableStateOf<Song?>(null)
    val song: State<Song?> = _song

    private val _rawLyrics = mutableStateOf("")
    val rawLyrics: State<String> = _rawLyrics

    private val _lyricLines = mutableStateOf<List<LyricLine>>(emptyList())
    val lyricLines: State<List<LyricLine>> = _lyricLines

    private val _currentLineIndex = mutableIntStateOf(0)
    val currentLineIndex: State<Int> = _currentLineIndex

    val currentPosition: State<Long> = playbackManager.currentPosition
    val isPlaying: State<Boolean> = playbackManager.isPlaying

    init {
        viewModelScope.launch {
            val song = repository.getSongByMediaId(songMediaId)
            _song.value = song
            song?.let { loadLyrics(it) }
        }
    }

    private fun loadLyrics(song: Song) {
        viewModelScope.launch {
            val lyricContent = withContext(Dispatchers.IO) {
                try {
                    val songUri = Uri.parse(song.uri)

                    // Priority 1: Check for matching local .lrc sidecar file in the folder
                    val sidecarLyrics = if (songUri.scheme == "content" || song.folderPath.startsWith("content://")) {
                        val folderUri = Uri.parse(song.folderPath)
                        val folderDoc = DocumentFile.fromTreeUri(application, folderUri)

                        if (folderDoc != null && folderDoc.isDirectory) {
                            val audioDoc = DocumentFile.fromSingleUri(application, songUri)
                            val audioFileName = audioDoc?.name ?: songUri.lastPathSegment ?: song.title
                            val audioBaseName = if (audioFileName.contains(".")) {
                                audioFileName.substringBeforeLast(".")
                            } else {
                                audioFileName
                            }

                            val allFiles = folderDoc.listFiles()
                            val matchedFile = allFiles.find { file ->
                                val name = file.name ?: return@find false
                                name.equals("$audioBaseName.lrc", ignoreCase = true) ||
                                        name.equals("${song.title}.lrc", ignoreCase = true)
                            } ?: allFiles.find { file ->
                                val name = file.name ?: return@find false
                                name.endsWith(".lrc", ignoreCase = true) &&
                                        (name.contains(audioBaseName, ignoreCase = true) || name.contains(song.title, ignoreCase = true))
                            }

                            matchedFile?.let { file ->
                                application.contentResolver.openInputStream(file.uri)?.use {
                                    it.bufferedReader().readText()
                                }
                            }
                        } else null
                    } else {
                        val path = songUri.path ?: song.uri
                        val audioFile = File(path)
                        val parentDir = audioFile.parentFile
                        val audioBaseName = audioFile.nameWithoutExtension

                        val possibleLrcFiles = listOfNotNull(
                            parentDir?.let { File(it, "$audioBaseName.lrc") },
                            parentDir?.let { File(it, "${song.title}.lrc") }
                        )

                        val existingLrc = possibleLrcFiles.find { it.exists() }
                        existingLrc?.readText() ?: parentDir?.listFiles()?.find { file ->
                            file.isFile && file.name.endsWith(".lrc", ignoreCase = true) &&
                                    (file.name.equals("$audioBaseName.lrc", ignoreCase = true) || file.name.equals("${song.title}.lrc", ignoreCase = true))
                        }?.readText()
                    }

                    // Return sidecar lyrics if found, otherwise check embedded
                    sidecarLyrics ?: if (!song.uri.startsWith("http")) {
                        LyricsEmbedder.extractEmbeddedLyrics(application, songUri)
                    } else null
                } catch (e: Exception) {
                    android.util.Log.e("LyricsEditor", "Error loading lyrics", e)
                    null
                }
            }

            val parsedLines = withContext(Dispatchers.IO) {
                lyricContent?.let { LrcParser.parse(it) } ?: emptyList()
            }
            _lyricLines.value = parsedLines
            _rawLyrics.value = parsedLines.joinToString("\n") { line -> line.content }
        }
    }

    fun updateRawLyrics(text: String) {
        _rawLyrics.value = text
        viewModelScope.launch {
            val currentLines = _lyricLines.value
            val newLines = withContext(Dispatchers.Default) {
                val lines = text.lines().filter { it.isNotBlank() }
                val contentMap = currentLines.associateBy { it.content }
                lines.map { content ->
                    contentMap[content] ?: LyricLine(0, content)
                }
            }
            _lyricLines.value = newLines
            if (_currentLineIndex.intValue >= newLines.size) {
                _currentLineIndex.intValue = (newLines.size - 1).coerceAtLeast(0)
            }
        }
    }

    fun markTimestamp() {
        if (_currentLineIndex.intValue < _lyricLines.value.size) {
            val position = playbackManager.currentPosition.value
            val currentLines = _lyricLines.value.toMutableList()
            val line = currentLines[_currentLineIndex.intValue]
            currentLines[_currentLineIndex.intValue] = line.copy(startTime = position)
            _lyricLines.value = currentLines
            if (_currentLineIndex.intValue < _lyricLines.value.size - 1) {
                _currentLineIndex.intValue++
            }
        }
    }

    fun setCurrentLine(index: Int) {
        _currentLineIndex.intValue = index.coerceIn(0, (_lyricLines.value.size - 1).coerceAtLeast(0))
    }

    fun togglePlayPause() {
        playbackManager.togglePlayPause()
    }

    fun seekTo(position: Long) {
        playbackManager.seekTo(position)
    }

    fun saveLyrics() {
        val song = _song.value ?: return
        if (song.uri.startsWith("http")) return
        
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val lrcContent = LrcParser.format(_lyricLines.value)
                // EMBED lyrics into the song file itself
                LyricsEmbedder.embedLyrics(application, song.uri, lrcContent)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun loadLyricsFromFile() {}

    fun loadLyricsFromUri(uri: Uri) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                try {
                    val lrcContent = application.contentResolver.openInputStream(uri)?.use { input ->
                        input.bufferedReader().readText()
                    }
                    lrcContent?.let {
                        val parsedLines = LrcParser.parse(it)
                        Pair(parsedLines, it)
                    }
                } catch (e: Exception) {
                    android.util.Log.e("LyricsEditor", "Error loading lyrics from file", e)
                    null
                }
            }
            result?.let { (parsedLines, raw) ->
                _lyricLines.value = parsedLines
                _rawLyrics.value = raw
            }
        }
    }

    fun searchLrcLib() {
        val song = _song.value ?: return
        viewModelScope.launch {
            try {
                val response = withContext(Dispatchers.IO) {
                    lrcLibService.getLyrics(
                        artist = song.artist,
                        title = song.title,
                        album = song.album,
                        duration = (song.duration / 1000).toInt()
                    )
                }
                val result = withContext(Dispatchers.IO) {
                    response?.syncedLyrics?.let { synced ->
                        val parsedLines = LrcParser.parse(synced)
                        Pair(parsedLines, synced)
                    }
                }
                result?.let { (parsedLines, raw) ->
                    _lyricLines.value = parsedLines
                    _rawLyrics.value = raw
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
