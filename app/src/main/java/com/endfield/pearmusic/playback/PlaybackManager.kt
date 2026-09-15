package com.endfield.pearmusic.playback

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import android.os.Bundle
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.endfield.pearmusic.data.MusicRepository
import com.endfield.pearmusic.data.local.Song
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.*
import kotlin.time.Duration.Companion.milliseconds

class PlaybackManager(private val context: Context, private val repository: MusicRepository) {
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var mediaController: MediaController? = null
    private val playbackScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private val _currentSong = mutableStateOf<Song?>(null)
    val currentSong: State<Song?> = _currentSong

    private val _isPlaying = mutableStateOf(false)
    val isPlaying: State<Boolean> = _isPlaying

    private val _queue = mutableStateOf<List<Song>>(emptyList())
    val queue: State<List<Song>> = _queue

    private val _repeatMode = mutableStateOf(Player.REPEAT_MODE_OFF) 
    val repeatMode: State<Int> = _repeatMode

    private val _currentPosition = mutableStateOf(0L)
    val currentPosition: State<Long> = _currentPosition

    private val _bufferedPosition = mutableStateOf(0L)
    val bufferedPosition: State<Long> = _bufferedPosition

    private val _isBuffering = mutableStateOf(false)
    val isBuffering: State<Boolean> = _isBuffering

    private var positionUpdateJob: Job? = null

    init {
        val sessionToken = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        controllerFuture = MediaController.Builder(context, sessionToken).buildAsync()
        controllerFuture?.addListener({
            try {
                mediaController = controllerFuture?.get()
                setupController()
            } catch (e: Exception) {
                android.util.Log.e("PlaybackManager", "Failed to connect to MediaController", e)
            }
        }, MoreExecutors.directExecutor())
    }

    private fun setupController() {
        val controller = mediaController ?: return
        
        // Initial state sync
        _isPlaying.value = controller.isPlaying
        _repeatMode.value = controller.repeatMode
        updateCurrentSongFromController()
        syncQueueFromController()

        controller.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _isPlaying.value = isPlaying
                if (isPlaying) startPositionUpdate() else stopPositionUpdate()
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                updateCurrentSongFromController()
            }

            override fun onTracksChanged(tracks: Tracks) {
                updateTechnicalStats()
            }

            override fun onRepeatModeChanged(repeatMode: Int) {
                _repeatMode.value = repeatMode
            }

            override fun onPlaybackStateChanged(state: Int) {
                _isBuffering.value = state == Player.STATE_BUFFERING
                if (state == Player.STATE_IDLE || state == Player.STATE_ENDED) {
                    stopPositionUpdate()
                }
            }

            override fun onTimelineChanged(timeline: androidx.media3.common.Timeline, reason: Int) {
                if (reason == Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED) {
                    syncQueueFromController()
                }
            }
        })
        
        if (controller.isPlaying) startPositionUpdate()
    }

    private fun updateCurrentSongFromController() {
        val controller = mediaController ?: return
        val item = controller.currentMediaItem ?: return
        
        playbackScope.launch {
            val song = repository.getSongByMediaId(item.mediaId) ?: mapMediaItemToSong(item)
            _currentSong.value = song
            updateTechnicalStats()

            // Fetch detailed technical metadata (bit depth, etc.) on demand
            if (!song.uri.startsWith("http")) {
                val detailed = repository.loadSongMetadataOnly(song)
                if (detailed != null && _currentSong.value?.mediaId == detailed.mediaId) {
                    _currentSong.value = detailed
                }
            }
        }
    }

    private fun syncQueueFromController() {
        val controller = mediaController ?: return
        val items = mutableListOf<MediaItem>()
        for (i in 0 until controller.mediaItemCount) {
            items.add(controller.getMediaItemAt(i))
        }
        
        playbackScope.launch {
            val songs = withContext(Dispatchers.Default) {
                items.map { item ->
                    repository.getSongByMediaId(item.mediaId) ?: mapMediaItemToSong(item)
                }
            }
            _queue.value = songs
        }
    }

    private fun mapMediaItemToSong(item: MediaItem): Song {
        val metadata = item.mediaMetadata
        val extras = metadata.extras ?: Bundle.EMPTY
        return Song(
            mediaId = item.mediaId,
            title = metadata.title?.toString() ?: "Unknown Title",
            artist = metadata.artist?.toString() ?: "Unknown Artist",
            album = metadata.albumTitle?.toString() ?: "Unknown Album",
            uri = item.localConfiguration?.uri?.toString() ?: "",
            duration = extras.getLong("android.media.metadata.DURATION", 0L),
            folderPath = "",
            folderName = "",
            coverArtUrl = extras.getString("cover_art_url"),
            replayGain = extras.getFloat("replay_gain", 1.0f)
        )
    }

    fun playSong(song: Song, queue: List<Song> = listOf(song)) {
        playbackScope.launch {
            val mediaItems = withContext(Dispatchers.Default) {
                queue.map { s -> createMediaItemFromSong(s) }
            }
            
            withContext(Dispatchers.Main) {
                mediaController?.let { controller ->
                    val startIndex = queue.indexOfFirst { it.mediaId == song.mediaId }.coerceAtLeast(0)
                    controller.setMediaItems(mediaItems, startIndex, 0L)
                    controller.prepare()
                    controller.play()
                }
            }
        }
    }

    private fun createMediaItemFromSong(s: Song): MediaItem {
        val extras = Bundle().apply {
            putLong("android.media.metadata.DURATION", s.duration)
            putFloat("replay_gain", s.replayGain ?: 1.0f)
            putString("cover_art_url", s.coverArtUrl)
        }

        val metadata = MediaMetadata.Builder()
            .setTitle(s.title)
            .setArtist(s.artist)
            .setAlbumTitle(s.album)
            .setDisplayTitle(s.title)
            .setSubtitle(s.artist)
            .setIsPlayable(true)
            .setExtras(extras)
            .build()
        
        val healedUri = if (s.uri.startsWith("content:/") && !s.uri.startsWith("content://")) {
            s.uri.replace("content:/", "content://")
        } else {
            s.uri
        }

        return MediaItem.Builder()
            .setMediaId(s.mediaId)
            .setUri(Uri.parse(healedUri))
            .setMediaMetadata(metadata)
            .build()
    }

    fun togglePlayPause() {
        mediaController?.let {
            if (it.isPlaying) it.pause() else it.play()
        }
    }

    fun skipToNext() = mediaController?.seekToNext()
    fun skipToPrevious() = mediaController?.seekToPrevious()

    fun toggleRepeatMode() {
        mediaController?.let {
            val nextMode = when (it.repeatMode) {
                Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                else -> Player.REPEAT_MODE_OFF
            }
            it.repeatMode = nextMode
            _repeatMode.value = nextMode
        }
    }

    fun seekTo(position: Long) {
        mediaController?.seekTo(position)
        _currentPosition.value = position
    }

    private fun startPositionUpdate() {
        stopPositionUpdate()
        positionUpdateJob = playbackScope.launch {
            while (isActive) {
                mediaController?.let {
                    if (it.isPlaying || it.playbackState == Player.STATE_BUFFERING) {
                        _currentPosition.value = it.currentPosition
                        _bufferedPosition.value = it.bufferedPosition
                    }
                }
                delay(250.milliseconds) // Slightly faster updates for smoother UI
            }
        }
    }

    private fun stopPositionUpdate() {
        positionUpdateJob?.cancel()
    }

    fun reorderQueue(fromIndex: Int, toIndex: Int) {
        mediaController?.moveMediaItem(fromIndex, toIndex)
    }

    private fun updateTechnicalStats() {
        val controller = mediaController ?: return
        val currentSong = _currentSong.value ?: return

        val tracks = controller.currentTracks
        for (group in tracks.groups) {
            if (group.type == androidx.media3.common.C.TRACK_TYPE_AUDIO && group.isSelected) {
                val format = group.getTrackFormat(0)
                
                // Only update if stats actually changed to avoid unnecessary recompositions
                if (format.sampleRate != currentSong.samplingRate || format.bitrate != currentSong.bitrate) {
                    val mimeType = format.sampleMimeType?.lowercase() ?: ""
                    val isLossless = mimeType.contains("flac") || mimeType.contains("wav") || 
                                   currentSong.format?.lowercase()?.let { it == "flac" || it == "wav" } == true

                    _currentSong.value = currentSong.copy(
                        samplingRate = if (format.sampleRate > 0) format.sampleRate else currentSong.samplingRate,
                        bitrate = if (format.bitrate > 0) format.bitrate else currentSong.bitrate,
                        format = format.sampleMimeType?.substringAfterLast('/') ?: currentSong.format,
                        isLossless = isLossless
                    )
                }
                break
            }
        }
    }

    fun release() {
        playbackScope.cancel()
        controllerFuture?.let {
            MediaController.releaseFuture(it)
        }
    }
}
