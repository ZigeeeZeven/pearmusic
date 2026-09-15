package com.endfield.pearmusic.ui.coil

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Log
import coil.ImageLoader
import coil.decode.DataSource
import coil.decode.ImageSource
import coil.fetch.FetchResult
import coil.fetch.Fetcher
import coil.fetch.SourceResult
import coil.request.Options
import com.endfield.pearmusic.data.scanner.DocumentUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okio.Buffer
import okio.buffer
import okio.source
import java.io.File
import java.util.concurrent.ConcurrentHashMap

class MediaArtFetcher(
    private val context: Context,
    private val data: MediaArtRequest,
    @Suppress("UNUSED_PARAMETER") options: Options
) : Fetcher {

    companion object {
        private val memoryArtCache = ConcurrentHashMap<String, ByteArray>()
        private val folderArtCache = ConcurrentHashMap<String, Uri>()
        private val foldersWithoutArt = java.util.Collections.newSetFromMap(ConcurrentHashMap<String, Boolean>())
    }

    override suspend fun fetch(): FetchResult? {
        return withContext(Dispatchers.IO) {
            val cacheKey = data.albumId?.toString() ?: data.uri ?: data.folderPath ?: return@withContext null

            // 0. Instant Memory Lookup
            memoryArtCache[cacheKey]?.let { cachedBytes ->
                return@withContext SourceResult(
                    source = ImageSource(Buffer().write(cachedBytes), context),
                    mimeType = "image/*",
                    dataSource = DataSource.MEMORY
                )
            }

            // 1. Online Cover Art URL
            if (!data.coverArtUrl.isNullOrEmpty()) {
                val onlineResult = fetchOnlineArt(data.coverArtUrl, cacheKey)
                if (onlineResult != null) return@withContext onlineResult
            }

            // 2. Folder Art Fallback (Usually High Resolution)
            val folderArt = getFolderArtCached(data.folderPath)
            if (folderArt != null) {
                try {
                    context.contentResolver.openInputStream(folderArt)?.use { inputStream ->
                        val bytes = inputStream.readBytes()
                        if (bytes.isNotEmpty()) {
                            memoryArtCache[cacheKey] = bytes
                            return@withContext SourceResult(
                                source = ImageSource(Buffer().write(bytes), context),
                                mimeType = "image/*",
                                dataSource = DataSource.DISK
                            )
                        }
                    }
                } catch (_: Exception) {}
            }

            // 3. Full Embedded Art (High Fidelity)
            if (!data.uri.isNullOrEmpty()) {
                val embeddedArt = getEmbeddedArtFull(data.uri)
                if (embeddedArt != null) {
                    memoryArtCache[cacheKey] = embeddedArt
                    return@withContext SourceResult(
                        source = ImageSource(Buffer().write(embeddedArt), context),
                        mimeType = "image/*",
                        dataSource = DataSource.DISK
                    )
                }
            }

            // 4. Local MediaStore Album Art (Fallback, might be low res)
            if (data.albumId != null && data.albumId > 0) {
                try {
                    val albumArtUri = Uri.parse("content://media/external/audio/albumart/${data.albumId}")
                    context.contentResolver.openInputStream(albumArtUri)?.use { inputStream ->
                        val bytes = inputStream.readBytes()
                        if (bytes.isNotEmpty()) {
                            memoryArtCache[cacheKey] = bytes
                            return@withContext SourceResult(
                                source = ImageSource(Buffer().write(bytes), context),
                                mimeType = "image/*",
                                dataSource = DataSource.DISK
                            )
                        }
                    }
                } catch (_: Exception) {}
            }

            null
        }
    }

    private suspend fun fetchOnlineArt(url: String, cacheKey: String): FetchResult? {
        if (url.startsWith("/")) {
            val file = File(url)
            if (file.exists()) {
                try {
                    return SourceResult(
                        source = ImageSource(file.source().buffer(), context),
                        mimeType = "image/*",
                        dataSource = DataSource.DISK
                    )
                } catch (_: Exception) {}
            }
        }

        try {
            val client = (context.applicationContext as? com.endfield.pearmusic.PearMusicApplication)?.okHttpClient
                ?: okhttp3.OkHttpClient()
            val request = okhttp3.Request.Builder().url(url).build()
            val response = client.newCall(request).execute()
            response.body?.let { body ->
                val bytes = body.bytes()
                memoryArtCache[cacheKey] = bytes
                return SourceResult(
                    source = ImageSource(Buffer().write(bytes), context),
                    mimeType = "image/*",
                    dataSource = DataSource.NETWORK
                )
            }
        } catch (e: Exception) {
            Log.w("MediaArtFetcher", "Failed to fetch online art", e)
        }
        return null
    }

    private fun getEmbeddedArtFull(uriString: String): ByteArray? {
        val retriever = MediaMetadataRetriever()
        try {
            val uri = Uri.parse(uriString)
            
            // Skip cloud URIs for full metadata retriever to avoid full file download
            if (uriString.contains("com.google.android.apps.docs.storage") ||
                uriString.contains("com.google.android.apps.docs")) {
                return null
            }
            
            retriever.setDataSource(context, uri)
            return retriever.embeddedPicture
        } catch (e: Exception) {
            Log.w("MediaArtFetcher", "Failed to extract full embedded art from $uriString", e)
            return null
        } finally {
            try { retriever.release() } catch (_: Exception) {}
        }
    }

    private fun getFolderArtCached(folderPath: String?): Uri? {
        if (folderPath.isNullOrEmpty()) return null
        folderArtCache[folderPath]?.let { return it }
        if (foldersWithoutArt.contains(folderPath)) return null

        val art = getFolderArt(folderPath)
        if (art != null) {
            folderArtCache[folderPath] = art
        } else {
            foldersWithoutArt.add(folderPath)
        }
        return art
    }

    private fun getFolderArt(folderPath: String?): Uri? {
        if (folderPath.isNullOrEmpty()) return null
        try {
            if (folderPath.startsWith("content://")) {
                val folderUri = Uri.parse(folderPath)
                val treeUri = findTreeUri(folderUri) ?: return null
                val children = DocumentUtils.listChildren(context, treeUri, folderUri)
                val artFileNames = listOf("cover.jpg", "cover.png", "folder.jpg", "folder.png", "album.jpg", "album.png")

                for (name in artFileNames) {
                    val match = children.find { it.displayName.equals(name, ignoreCase = true) }
                    if (match != null && !match.isDirectory) return match.uri
                }

                return children.firstOrNull { doc ->
                    val name = doc.displayName.lowercase()
                    !doc.isDirectory && (name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".png"))
                }?.uri
            } else {
                val folder = File(folderPath)
                if (!folder.isDirectory) return null
                val artFileNames = listOf("cover.jpg", "cover.png", "folder.jpg", "folder.png", "album.jpg", "album.png")
                for (name in artFileNames) {
                    val file = File(folder, name)
                    if (file.exists()) return Uri.fromFile(file)
                }
                return folder.listFiles()?.firstOrNull { file ->
                    val name = file.name.lowercase()
                    file.isFile && (name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".png"))
                }?.let { Uri.fromFile(it) }
            }
        } catch (e: Exception) {
            Log.e("MediaArtFetcher", "Error searching folder art", e)
            return null
        }
    }

    private fun findTreeUri(documentUri: Uri): Uri? {
        val paths = documentUri.pathSegments
        if (paths.size >= 2 && paths[0] == "tree") {
            return Uri.Builder()
                .scheme(documentUri.scheme)
                .authority(documentUri.authority)
                .appendPath("tree")
                .appendPath(paths[1])
                .build()
        }
        return null
    }

    class Factory(private val context: Context) : Fetcher.Factory<MediaArtRequest> {
        override fun create(data: MediaArtRequest, options: Options, imageLoader: ImageLoader): Fetcher {
            return MediaArtFetcher(context, data, options)
        }
    }
}