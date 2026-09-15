package com.endfield.pearmusic.util

import android.content.Context
import android.net.Uri
import android.util.Log
import android.webkit.MimeTypeMap
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import java.io.File

object LyricsEmbedder {
    fun embedLyrics(context: Context, songUriString: String, lyrics: String) {
        if (lyrics.isBlank() || songUriString.startsWith("http")) return
        
        val songUri = Uri.parse(songUriString)
        val cr = context.contentResolver
        
        try {
            // 1. Identify file extension for JAudiotagger
            val extension = if (songUri.scheme == "content") {
                val mimeType = cr.getType(songUri)
                MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType) ?: "mp3"
            } else {
                val fileName = songUri.lastPathSegment ?: "song.mp3"
                if (fileName.contains(".")) fileName.substringAfterLast(".") else "mp3"
            }
            
            // 2. Copy content to a temp file with the same extension
            val tempFile = File.createTempFile("embed_lyrics", ".$extension", context.cacheDir)
            cr.openInputStream(songUri)?.use { input ->
                tempFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            } ?: run {
                Log.e("LyricsEmbedder", "Could not open input stream for $songUri")
                tempFile.delete()
                return
            }

            // 3. Read and update tags in the temp file
            try {
                val audioFile = AudioFileIO.read(tempFile)
                val tag = audioFile.tagOrCreateAndSetDefault
                // setField(FieldKey.LYRICS, ...) handles USLT for MP3 and LYRICS for FLAC
                tag.setField(FieldKey.LYRICS, lyrics)
                audioFile.commit()

                // 4. Write the modified temp file back to the original URI
                // We use "w" mode to overwrite the existing content
                cr.openOutputStream(songUri, "w")?.use { output ->
                    tempFile.inputStream().use { input ->
                        input.copyTo(output)
                    }
                } ?: run {
                    Log.e("LyricsEmbedder", "Could not open output stream for $songUri. Check SAF permissions.")
                }
            } catch (e: Exception) {
                Log.e("LyricsEmbedder", "JAudiotagger error: ${e.message}")
            } finally {
                // 5. Cleanup
                if (tempFile.exists()) tempFile.delete()
            }
        } catch (e: Exception) {
            Log.e("LyricsEmbedder", "General error during embedding: ${e.message}")
        }
    }

    fun extractEmbeddedLyrics(context: Context, songUri: Uri): String? {
        val cr = context.contentResolver
        var tempFile: File? = null
        return try {
            val extension = try {
                val mimeType = cr.getType(songUri)
                MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType) ?: "mp3"
            } catch (e: Exception) {
                "mp3"
            }

            tempFile = File.createTempFile("extract_lyrics", ".$extension", context.cacheDir)
            cr.openInputStream(songUri)?.use { input ->
                tempFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }

            val audioFile = AudioFileIO.read(tempFile)
            val tag = audioFile.tag
            tag?.getFirst(FieldKey.LYRICS)
        } catch (e: Exception) {
            Log.e("LyricsEmbedder", "Error extracting lyrics: ${e.message}")
            null
        } finally {
            tempFile?.delete()
        }
    }
}
