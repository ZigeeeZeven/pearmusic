package com.endfield.pearmusic.data.scanner

import android.content.Context
import android.media.AudioFormat
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.util.Log
import com.endfield.pearmusic.data.local.Song
import kotlinx.coroutines.*
import org.jaudiotagger.audio.AudioFileIO
import java.io.File
import kotlin.math.pow

class MusicScanner(private val context: Context) {

    private data class MediaStoreMetadata(
        val title: String,
        val artist: String,
        val album: String,
        val duration: Long,
        val albumId: Long?
    )

    /**
     * Fast directory scanner: Returns track lists almost instantly by relying
     * purely on MediaStore/DocumentUtils database cursor lookups.
     */
    suspend fun scanFolders(folderUris: List<Uri>): ScanResult = withContext(Dispatchers.IO) {
        val songs = mutableListOf<Song>()
        val playlists = mutableListOf<M3UPlaylist>()
        val startTime = System.currentTimeMillis()

        // 1. Fetch indexed MediaStore tags in a single pass
        val mediaStoreCache = fetchMediaStoreMetadata()

        coroutineScope {
            folderUris.forEach { uri ->
                if (uri.toString().startsWith("content://")) {
                    try {
                        val rootDocId = DocumentsContract.getTreeDocumentId(uri)
                        val rootUri = DocumentsContract.buildDocumentUriUsingTree(uri, rootDocId)
                        scanDirectoryFast(uri, rootUri, songs, playlists, mediaStoreCache)
                    } catch (e: Exception) {
                        Log.e("MusicScanner", "Error scanning folder tree $uri", e)
                    }
                }
            }
        }

        val duration = System.currentTimeMillis() - startTime
        Log.i("MusicScanner", "Fast scan completed in ${duration}ms. Found ${songs.size} songs.")

        ScanResult(songs, playlists)
    }

    private suspend fun scanDirectoryFast(
        treeUri: Uri,
        parentUri: Uri,
        songs: MutableList<Song>,
        playlists: MutableList<M3UPlaylist>,
        mediaStoreCache: Map<String, MediaStoreMetadata>
    ) {
        val children = DocumentUtils.listChildren(context, treeUri, parentUri)
        val musicFiles = mutableListOf<DocumentUtils.DocumentInfo>()

        for (doc in children) {
            if (doc.isDirectory) {
                scanDirectoryFast(treeUri, doc.uri, songs, playlists, mediaStoreCache)
            } else {
                val name = doc.displayName
                val extension = name.substringAfterLast('.', "").lowercase()
                when (extension) {
                    "mp3", "flac", "wav", "m4a", "ogg" -> musicFiles.add(doc)
                    "m3u", "m3u8" -> processPlaylistFileOptimized(doc, playlists)
                }
            }
        }

        if (musicFiles.isNotEmpty()) {
            val results = musicFiles.map { doc ->
                extractSongFromDocInfo(doc, parentUri, mediaStoreCache)
            }
            synchronized(songs) {
                songs.addAll(results)
            }
            yield()
        }
    }

    private fun extractSongFromDocInfo(
        doc: DocumentUtils.DocumentInfo,
        parentUri: Uri,
        mediaStoreCache: Map<String, MediaStoreMetadata>
    ): Song {
        val uriString = doc.uri.toString()
        val fileName = doc.displayName
        val key = "${fileName}_${doc.size}"
        val msMetadata = mediaStoreCache[key]

        return Song(
            mediaId = uriString,
            title = msMetadata?.title?.ifEmpty { null } ?: fileName.substringBeforeLast('.'),
            artist = msMetadata?.artist ?: "Unknown Artist",
            album = msMetadata?.album ?: "Unknown Album",
            uri = uriString,
            fileName = fileName,
            duration = msMetadata?.duration ?: 0L,
            folderPath = parentUri.toString(),
            folderName = "Unknown",
            format = fileName.substringAfterLast('.', "").lowercase(),
            isLossless = fileName.lowercase().let { it.endsWith(".flac") || it.endsWith(".wav") },
            albumId = msMetadata?.albumId
        )
    }

    private fun fetchMediaStoreMetadata(): Map<String, MediaStoreMetadata> {
        val cache = mutableMapOf<String, MediaStoreMetadata>()
        val projection = arrayOf(
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.SIZE
        )

        try {
            context.contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection,
                null,
                null,
                null
            )?.use { cursor ->
                val titleIdx = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistIdx = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val albumIdx = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                val albumIdIdx = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
                val durationIdx = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                val nameIdx = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
                val sizeIdx = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)

                while (cursor.moveToNext()) {
                    val name = cursor.getString(nameIdx) ?: continue
                    val size = cursor.getLong(sizeIdx)
                    val key = "${name}_${size}"

                    cache[key] = MediaStoreMetadata(
                        title = cursor.getString(titleIdx) ?: "",
                        artist = cursor.getString(artistIdx) ?: "Unknown Artist",
                        album = cursor.getString(albumIdx) ?: "Unknown Album",
                        albumId = cursor.getLong(albumIdIdx),
                        duration = cursor.getLong(durationIdx)
                    )
                }
            }
        } catch (e: Exception) {
            Log.e("MusicScanner", "Failed to pre-fetch MediaStore metadata", e)
        }
        return cache
    }

    private fun processPlaylistFileOptimized(doc: DocumentUtils.DocumentInfo, playlists: MutableList<M3UPlaylist>) {
        val songPaths = parseM3u8Content(doc.uri)
        if (songPaths.isNotEmpty()) {
            playlists.add(M3UPlaylist(doc.displayName, songPaths, doc.uri.toString()))
        }
    }

    fun parseM3u8Content(uri: Uri): List<String> {
        val songPaths = mutableListOf<String>()
        try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                inputStream.bufferedReader(Charsets.UTF_8).useLines { lines ->
                    lines.forEach { line ->
                        val trimmed = line.trim().replace("\uFEFF", "")
                        if (trimmed.isNotEmpty() && !trimmed.startsWith("#")) {
                            songPaths.add(trimmed.replace('\\', '/'))
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("MusicScanner", "Error parsing playlist $uri", e)
        }
        return songPaths
    }

    /**
     * ON-DEMAND LOAD: Call this ONLY when the user clicks a song to play it.
     * Performs deeper ReplayGain parsing and PCM format probing.
     */
    suspend fun loadSongDetailedMetadata(uri: Uri): AudioMetadata = withContext(Dispatchers.IO) {
        val retriever = MediaMetadataRetriever()
        val uriString = uri.toString()
        val healedUri = if (uriString.startsWith("content:/") && !uriString.startsWith("content://")) {
            Uri.parse(uriString.replace(Regex("^content:/+"), "content://"))
        } else {
            uri
        }

        var title: String? = null
        var artist: String? = null
        var album: String? = null
        var duration: Long? = null
        var samplingRate: Int? = null
        var bitDepth: Int? = null
        var bitrate: Int? = null
        var isFloat = false
        var replayGain: Float? = null

        try {
            retriever.setDataSource(context, healedUri)
            title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
            artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
            album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)
            duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
            bitrate = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.toIntOrNull()

            // Extract ReplayGain via FileDescriptor
            try {
                context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                    val fdPath = "/proc/self/fd/${pfd.fd}"
                    val fdFile = File(fdPath)
                    val audioFile = AudioFileIO.read(fdFile)
                    val tag = audioFile.tag
                    
                    // FLAC and WAV store their actual PCM bit depth in the audio header.
                    // This is more reliable than the extractor's decoder output (often 16-bit).
                    val headerBitDepth = audioFile.audioHeader.bitsPerSample
                    if (headerBitDepth in setOf(8, 16, 24, 32)) {
                        bitDepth = headerBitDepth
                    }

                    if (tag != null) {
                        var gainStr: String? = tag.getFirst("REPLAYGAIN_TRACK_GAIN")
                        if (gainStr.isNullOrBlank()) {
                            val fields = tag.fields
                            while (fields.hasNext()) {
                                val field = fields.next()
                                val fieldContent = field.toString()
                                if (fieldContent.contains("replaygain_track_gain", ignoreCase = true)) {
                                    val match = Regex("([-+]?[0-9.]+)(\\s*dB)?", RegexOption.IGNORE_CASE)
                                        .find(fieldContent.substringAfter("replaygain_track_gain"))
                                    gainStr = match?.groupValues?.get(1)
                                    if (!gainStr.isNullOrBlank()) break
                                }
                            }
                        }
                        if (!gainStr.isNullOrBlank()) {
                            replayGain = parseReplayGain(gainStr)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w("MusicScanner", "Detailed ReplayGain scan skipped: ${e.message}")
            }

            val extractor = android.media.MediaExtractor()
            try {
                extractor.setDataSource(context, uri, null)
                if (extractor.trackCount > 0) {
                    val format = extractor.getTrackFormat(0)
                    if (format.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                        samplingRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                    }
                    if (format.containsKey(MediaFormat.KEY_PCM_ENCODING)) {
                        val encoding = format.getInteger(MediaFormat.KEY_PCM_ENCODING)
                        val decodedBitDepth = when (encoding) {
                            AudioFormat.ENCODING_PCM_8BIT -> 8
                            AudioFormat.ENCODING_PCM_16BIT -> 16
                            21 -> 24 // AudioFormat.ENCODING_PCM_24BIT_PACKED (API 31+)
                            22 -> 32 // AudioFormat.ENCODING_PCM_32BIT (API 31+)
                            AudioFormat.ENCODING_PCM_FLOAT -> 32
                            else -> null
                        }
                        // Keep the source-file depth obtained from the lossless header.
                        // Use decoder depth only when the container did not expose one.
                        if (bitDepth == null && decodedBitDepth != null) {
                            bitDepth = decodedBitDepth
                        }
                        isFloat = (encoding == AudioFormat.ENCODING_PCM_FLOAT)
                    }
                }
            } finally {
                extractor.release()
            }
        } catch (e: Exception) {
            Log.e("MusicScanner", "Error loading detailed metadata for $uri", e)
        } finally {
            try { retriever.release() } catch (_: Exception) {}
        }

        AudioMetadata(title, artist, album, duration, samplingRate, bitDepth, bitrate, isFloat, replayGain)
    }

    private fun parseReplayGain(gainStr: String?): Float? {
        if (gainStr == null) return null
        return try {
            val db = gainStr.replace(" dB", "").toFloat()
            10.0f.pow(db / 20.0f)
        } catch (e: Exception) {
            null
        }
    }

    data class ScanResult(
        val songs: List<Song>,
        val playlists: List<M3UPlaylist>
    )

    data class M3UPlaylist(
        val name: String,
        val songPaths: List<String>,
        val folderPath: String
    )

    data class AudioMetadata(
        val title: String?,
        val artist: String?,
        val album: String?,
        val duration: Long?,
        val samplingRate: Int?,
        val bitDepth: Int?,
        val bitrate: Int?,
        val isFloat: Boolean = false,
        val replayGain: Float? = null
    )
}
