package com.endfield.pearmusic.data

import android.content.Context
import android.net.Uri
import com.endfield.pearmusic.data.local.AlbumInfo
import com.endfield.pearmusic.data.local.FolderInfo
import com.endfield.pearmusic.data.local.Playlist
import com.endfield.pearmusic.data.local.PlaylistDao
import com.endfield.pearmusic.data.local.PlaylistInfo
import com.endfield.pearmusic.data.local.PlaylistSongCrossRef
import com.endfield.pearmusic.data.local.PlaylistWithSongs
import com.endfield.pearmusic.data.local.Song
import com.endfield.pearmusic.data.local.SongDao
import com.endfield.pearmusic.data.scanner.DocumentUtils
import com.endfield.pearmusic.data.scanner.MusicScanner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class MusicRepository(
    private val context: Context,
    private val songDao: SongDao,
    private val playlistDao: PlaylistDao,
    private val musicScanner: MusicScanner
) {

    val allSongs: Flow<List<Song>> = songDao.getAllSongs()
    val allAlbumsInfo: Flow<List<AlbumInfo>> = songDao.getAllAlbumsInfo()
    val allArtists: Flow<List<String>> = songDao.getAllArtists()
    val allPlaylists: Flow<List<Playlist>> = playlistDao.getAllPlaylists()
    val allPlaylistsInfo: Flow<List<PlaylistInfo>> = playlistDao.getAllPlaylistsInfo()
    val allFolders: Flow<List<FolderInfo>> = songDao.getAllFolders()

    suspend fun syncMusic(folderUris: List<Uri>) = withContext(Dispatchers.IO) {
        val scanResult = musicScanner.scanFolders(folderUris)
        songDao.insertSongs(scanResult.songs)
        
        // Auto-import M3U playlists found during scan
        scanResult.playlists.forEach { m3u ->
            val playlistId = createPlaylistIfNotExists(m3u.name.substringBeforeLast('.'))
            m3u.songPaths.forEach { path ->
                val fileName = path.substringAfterLast('/')
                val matchedSong = songDao.findSongByFileName(fileName) ?: songDao.findSongByUri(path)
                if (matchedSong != null) {
                    addSongToPlaylist(playlistId, matchedSong.id)
                }
            }
        }
    }

    private suspend fun createPlaylistIfNotExists(name: String): Long {
        val existing = playlistDao.getAllPlaylistsOnce().find { it.name == name }
        return existing?.id ?: playlistDao.insertPlaylist(Playlist(name = name))
    }

    suspend fun getAllSongsOnce(): List<Song> = withContext(Dispatchers.IO) {
        songDao.getAllSongsOnce()
    }

    suspend fun getSongByMediaId(mediaId: String): Song? = withContext(Dispatchers.IO) {
        songDao.getSongByMediaId(mediaId)
    }

    suspend fun loadSongMetadataOnly(song: Song): Song? = withContext(Dispatchers.IO) {
        val uri = Uri.parse(song.uri)
        val metadata = musicScanner.loadSongDetailedMetadata(uri)
        if (metadata.title != null || metadata.duration != null || metadata.bitDepth != null) {
            song.copy(
                title = metadata.title ?: song.title,
                artist = metadata.artist ?: song.artist,
                album = metadata.album ?: song.album,
                duration = metadata.duration ?: song.duration,
                samplingRate = metadata.samplingRate ?: song.samplingRate,
                bitDepth = metadata.bitDepth ?: song.bitDepth,
                isFloat = metadata.isFloat,
                bitrate = metadata.bitrate ?: song.bitrate
            )
        } else {
            null
        }
    }

    suspend fun updateSongs(songs: List<Song>) = withContext(Dispatchers.IO) {
        songDao.updateSongs(songs)
    }

    suspend fun updateLyricsDelay(songId: Long, delay: Long) = withContext(Dispatchers.IO) {
        songDao.updateLyricsDelay(songId, delay)
    }

    fun getSongsByAlbum(album: String): Flow<List<Song>> = songDao.getSongsByAlbum(album)
    fun getSongsByArtist(artist: String): Flow<List<Song>> = songDao.getSongsByArtist(artist)
    fun getSongsByFolder(folderPath: String): Flow<List<Song>> = songDao.getSongsByFolder(folderPath)
    fun getPlaylistWithSongs(playlistId: Long) = playlistDao.getPlaylistWithSongs(playlistId)
    fun getOrderedSongsInPlaylist(playlistId: Long): Flow<List<Song>> = playlistDao.getOrderedSongsInPlaylist(playlistId)

    suspend fun createPlaylist(name: String): Long = withContext(Dispatchers.IO) {
        playlistDao.insertPlaylist(Playlist(name = name))
    }

    suspend fun updatePlaylist(playlist: Playlist) = withContext(Dispatchers.IO) {
        playlistDao.updatePlaylist(playlist)
    }

    suspend fun deletePlaylist(playlist: Playlist) = withContext(Dispatchers.IO) {
        playlistDao.deletePlaylist(playlist)
    }

    suspend fun addSongToPlaylist(playlistId: Long, songId: Long) = withContext(Dispatchers.IO) {
        val nextOrder = (playlistDao.getMaxOrder(playlistId) ?: -1) + 1
        playlistDao.insertPlaylistSongCrossRef(PlaylistSongCrossRef(playlistId, songId, nextOrder))
    }

    suspend fun removeSongFromPlaylist(playlistId: Long, songId: Long) = withContext(Dispatchers.IO) {
        playlistDao.deletePlaylistSongCrossRef(playlistId, songId)
    }

    suspend fun reorderPlaylist(playlistId: Long, fromIndex: Int, toIndex: Int) = withContext(Dispatchers.IO) {
        // Implementation for playlist reordering
    }

    suspend fun importM3u8(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        val songPaths = musicScanner.parseM3u8Content(uri)
        if (songPaths.isEmpty()) return@withContext false
        
        val playlistName = DocumentUtils.getDisplayName(context, uri).substringBeforeLast('.')
        val playlistId = createPlaylistIfNotExists(playlistName)
        
        var matchCount = 0
        songPaths.forEach { path ->
            val fileName = path.substringAfterLast('/')
            // Try matching by FileName first
            var matchedSong = songDao.findSongByFileName(fileName)
            
            // If not found, try matching by Uri (if the M3U8 has content:// or absolute paths)
            if (matchedSong == null) {
                matchedSong = songDao.findSongByUri(path)
            }
            
            if (matchedSong != null) {
                addSongToPlaylist(playlistId, matchedSong.id)
                matchCount++
            }
        }
        android.util.Log.d("MusicRepository", "Imported M3U8: $playlistName. Matched $matchCount songs.")
        matchCount > 0
    }
}