package com.endfield.pearmusic.data.repository

import android.media.MediaMetadataRetriever
import android.util.Log
import com.endfield.pearmusic.api.GoogleDriveApi
import com.endfield.pearmusic.data.local.Song
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import okhttp3.OkHttpClient
import java.io.File
import java.io.FileOutputStream
import okhttp3.Request

class DriveAudioRepository(
    private val context: android.content.Context,
    private val driveApi: GoogleDriveApi,
    private val songDao: com.endfield.pearmusic.data.local.SongDao,
    private val okHttpClient: OkHttpClient,
    private val apiKey: String? = null
) {
    private val folderId = "1jlKwbZVegXvz6DFhwwrCItmB4ADp9Sre"
    private val artCacheDir = File(context.cacheDir, "online_art").apply { mkdirs() }

    fun getOnlineTracks(forceRefresh: Boolean = false): Flow<List<Song>> = flow {
        var hasEmittedAnything = false
        try {
            // 1. Emit cached songs immediately
            val allCachedOnlineSongs = songDao.getAllSongsOnce().filter { it.mediaId.startsWith("online_") }
            if (allCachedOnlineSongs.isNotEmpty()) {
                emit(allCachedOnlineSongs)
                hasEmittedAnything = true
            }

            Log.d("DriveAudioRepo", "Fetching tracks from folder: $folderId")
            val query = "'$folderId' in parents and trashed = false"
            val response = try {
                driveApi.listFiles(
                    query = query, 
                    apiKey = apiKey
                )
            } catch (e: Exception) {
                Log.e("DriveAudioRepo", "Network error fetching file list: ${e.message}")
                if (!hasEmittedAnything) emit(emptyList())
                return@flow
            }
            
            val playlistFiles = mutableListOf<com.endfield.pearmusic.api.DriveFile>()
            val audioFiles = mutableListOf<com.endfield.pearmusic.api.DriveFile>()

            for (file in response.files) {
                val name = file.name.lowercase()
                val mime = file.mimeType.lowercase()
                
                if (name.endsWith(".m3u8") || name.endsWith(".m3u") || mime.contains("mpegurl")) {
                    playlistFiles.add(file)
                } else if (mime.contains("audio/") || 
                    name.endsWith(".mp3") || name.endsWith(".flac") || 
                    name.endsWith(".wav") || name.endsWith(".m4a") || name.endsWith(".ogg")) {
                    audioFiles.add(file)
                }
            }

            // 2. Build current list: use cache if available, otherwise placeholder
            val folderSongs = audioFiles.map { file ->
                val mediaId = "online_${file.id}"
                allCachedOnlineSongs.find { it.mediaId == mediaId } ?: createPlaceholderSong(file)
            }.toMutableList()
            
            // Only emit if we actually found files on Drive, or if we have nothing from cache
            if (folderSongs.isNotEmpty() || !hasEmittedAnything) {
                emit(folderSongs.toList())
                hasEmittedAnything = true
            }

            // 3. Handle playlists IMMEDIATELY so they show up while tags are loading
            val playlistSongs = mutableListOf<Song>()
            for (file in playlistFiles) {
                try {
                    val parsed = fetchAndParseM3u8(file.id, file.name, folderSongs)
                    playlistSongs.addAll(parsed)
                } catch (e: Exception) {
                    Log.w("DriveAudioRepo", "Failed to parse playlist ${file.name}", e)
                }
            }
            
            // DEDUPLICATE: onlineSongs will be everything, but we avoid adding the same file twice to the MAIN list
            // However, playlist versions have different mediaId, so we keep them for filtering purposes
            val allOnlineSongs = (folderSongs + playlistSongs).toMutableList()
            if (playlistSongs.isNotEmpty()) {
                emit(allOnlineSongs.toList())
            }

            // 4. Identify songs for tag fetching - include ALL versions that are placeholders
            val toFetch = allOnlineSongs.filter { song ->
                song.mediaId.startsWith("online_") && !song.mediaId.contains("_m3u8_") &&
                (song.artist == "Loading Tags..." || song.artist == "Unknown Artist" || song.duration == 0L || forceRefresh)
            }

            if (toFetch.isNotEmpty()) {
                val semaphore = Semaphore(1)
                val updatedSongsBuffer = mutableListOf<Song>()
                
                toFetch.forEach { songToUpdate ->
                    try {
                        semaphore.withPermit {
                            val driveFile = audioFiles.find { "online_${it.id}" == songToUpdate.mediaId }
                            if (driveFile != null) {
                                val updatedSong = mapDriveFileToSong(driveFile)
                                songDao.insertSongs(listOf(updatedSong))
                                
                                // Update ALL instances in the current list (main track + all playlist entries referencing it)
                                var changed = false
                                for (i in allOnlineSongs.indices) {
                                    val current = allOnlineSongs[i]
                                    if (current.mediaId == updatedSong.mediaId) {
                                        allOnlineSongs[i] = updatedSong
                                        changed = true
                                    } else if (current.mediaId.endsWith("_" + updatedSong.mediaId)) {
                                        // Update playlist entry that references this song
                                        allOnlineSongs[i] = updatedSong.copy(
                                            mediaId = current.mediaId,
                                            folderPath = current.folderPath,
                                            folderName = current.folderName
                                        )
                                        changed = true
                                    }
                                }
                                
                                if (changed) {
                                    updatedSongsBuffer.add(updatedSong)
                                    if (updatedSongsBuffer.size >= 3 || updatedSongsBuffer.size == toFetch.size) {
                                        emit(allOnlineSongs.toList())
                                        updatedSongsBuffer.clear()
                                    }
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Log.w("DriveAudioRepo", "Failed to fetch tags for ${songToUpdate.fileName}", e)
                    }
                }
                
                if (updatedSongsBuffer.isNotEmpty()) {
                    emit(allOnlineSongs.toList())
                }
            }

        } catch (e: Exception) {
            Log.e("DriveAudioRepo", "Error fetching online tracks", e)
            if (!hasEmittedAnything) emit(emptyList())
        }
    }.flowOn(Dispatchers.IO)

    private fun createPlaceholderSong(file: com.endfield.pearmusic.api.DriveFile): Song {
        val streamUrl = "https://drive.google.com/uc?export=download&id=${file.id}"
        val format = file.name.substringAfterLast('.', "").lowercase()
        return Song(
            mediaId = "online_${file.id}",
            title = file.name.substringBeforeLast('.'),
            artist = "Loading Tags...",
            album = "Google Drive",
            uri = streamUrl,
            fileName = file.name,
            duration = 0L,
            folderPath = "online_drive",
            folderName = "Google Drive",
            coverArtUrl = file.thumbnailLink?.replace("=s220", "=s500"),
            format = format,
            isLossless = format == "flac" || format == "wav"
        )
    }

    private fun mapDriveFileToSong(file: com.endfield.pearmusic.api.DriveFile): Song {
        val streamUrl = "https://drive.google.com/uc?export=download&id=${file.id}"
        val cacheFile = File(artCacheDir, "${file.id}.jpg")
        var coverArtPath: String? = if (cacheFile.exists()) cacheFile.absolutePath else null

        val retriever = MediaMetadataRetriever()
        var title = file.name.substringBeforeLast('.')
        var artist = "Unknown Artist"
        var album = "Google Drive"
        var duration = 0L

        try {
            // Use User-Agent for Google Drive streaming
            val headers = HashMap<String, String>()
            headers["User-Agent"] = "PearMusic (https://github.com/endfield/pearmusic)"
            
            retriever.setDataSource(streamUrl, headers)
            title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE) ?: title
            artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST) 
                ?: retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST)
                ?: artist
            album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM) ?: album
            duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: duration

            // Extract and cache album art if not already cached
            if (!cacheFile.exists()) {
                val artBytes = try { retriever.embeddedPicture } catch (e: Exception) { null }
                if (artBytes != null) {
                    FileOutputStream(cacheFile).use { it.write(artBytes) }
                    coverArtPath = cacheFile.absolutePath
                }
            }
        } catch (e: Exception) {
            Log.w("DriveAudioRepo", "Failed to peek tags for ${file.name}: ${e.message}")
        } finally {
            try { retriever.release() } catch (e: Exception) {}
        }

        val format = file.name.substringAfterLast('.', "").lowercase()
        val isLossless = format == "flac" || format == "wav"
        
        // Ensure coverArtUrl is correctly set to high-res thumbnail if cache path is empty
        val finalCoverArt = if (coverArtPath.isNullOrEmpty()) {
            file.thumbnailLink?.replace("=s220", "=s500")
        } else {
            coverArtPath
        }

        return Song(
            mediaId = "online_${file.id}",
            title = title,
            artist = artist,
            album = album,
            uri = streamUrl,
            fileName = file.name,
            duration = duration,
            folderPath = "online_drive",
            folderName = "Google Drive",
            coverArtUrl = finalCoverArt,
            format = format,
            isLossless = isLossless
        )
    }

    private suspend fun fetchAndParseM3u8(fileId: String, fileName: String, availableSongs: List<Song>): List<Song> {
        val streamUrl = "https://drive.google.com/uc?export=download&id=$fileId"
        val request = Request.Builder()
            .url(streamUrl)
            .header("User-Agent", "PearMusic (https://github.com/endfield/pearmusic)")
            .build()
        
        return try {
            val response = okHttpClient.newCall(request).execute()
            val content = response.body?.source()?.readString(Charsets.UTF_8) ?: return emptyList()
            
            Log.d("DriveAudioRepo", "Playlist ${fileName} content length: ${content.length}")
            val songs = mutableListOf<Song>()
            content.lines().forEach { line ->
                // Clean up line: remove UTF-8 BOM if present, and trim
                val trimmed = line.trim().replace("\uFEFF", "")
                if (trimmed.isNotEmpty() && !trimmed.startsWith("#")) {
                    Log.d("DriveAudioRepo", "Playlist line: $trimmed")
                    if (trimmed.startsWith("http")) {
                        // ... existing URL handling ...
                        val urlFileName = trimmed.substringAfterLast('/').substringBeforeLast('.')
                        var urlArtist = "Online Stream"
                        var urlTitle = urlFileName
                        val urlAlbum = fileName.substringBeforeLast('.')

                        val separators = listOf(" - ", " – ", " — ", " ~ ")
                        for (sep in separators) {
                            if (urlFileName.contains(sep)) {
                                val parts = urlFileName.split(sep)
                                if (parts.size >= 2) {
                                    urlArtist = parts[0].trim()
                                    urlTitle = parts.subList(1, parts.size).joinToString(sep).trim()
                                }
                                break
                            }
                        }

                        songs.add(Song(
                            mediaId = "online_m3u8_${trimmed.hashCode()}",
                            title = urlTitle,
                            artist = urlArtist,
                            album = urlAlbum,
                            uri = trimmed,
                            fileName = trimmed.substringAfterLast('/'),
                            duration = 0L,
                            folderPath = "online_m3u8",
                            folderName = fileName,
                            format = trimmed.substringAfterLast('.', "").lowercase()
                        ))
                    } else {
                        // Attempt to match filename in the same folder
                        val nameOnly = trimmed.substringAfterLast('/').substringAfterLast('\\')
                        val matched = availableSongs.find { 
                            it.fileName.equals(nameOnly, ignoreCase = true) || 
                            it.title.equals(nameOnly.substringBeforeLast('.'), ignoreCase = true)
                        }
                        if (matched != null) {
                            Log.d("DriveAudioRepo", "Matched playlist item: $nameOnly")
                            songs.add(matched.copy(
                                mediaId = "online_m3u8_${fileName}_${matched.mediaId}",
                                folderPath = "online_m3u8",
                                folderName = fileName
                            ))
                        } else {
                            Log.w("DriveAudioRepo", "Could not match playlist item: $nameOnly")
                        }
                    }
                }
            }
            songs
        } catch (e: Exception) {
            Log.e("DriveAudioRepo", "Error fetching playlist $fileName", e)
            emptyList()
        }
    }
}
