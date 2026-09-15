package com.endfield.pearmusic.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Delete
import androidx.room.Update
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface SongDao {
    @Query("SELECT * FROM songs ORDER BY title ASC")
    fun getAllSongs(): Flow<List<Song>>

    @Query("SELECT * FROM songs ORDER BY title ASC")
    suspend fun getAllSongsList(): List<Song>

    @Query("SELECT * FROM songs")
    suspend fun getAllSongsOnce(): List<Song>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSongs(songs: List<Song>)

    @Update
    suspend fun updateSong(song: Song)

    @Update
    suspend fun updateSongs(songs: List<Song>)

    @Delete
    suspend fun deleteSong(song: Song)

    @Query("DELETE FROM songs")
    suspend fun deleteAllSongs()

    @Query("SELECT * FROM songs WHERE mediaId = :mediaId")
    suspend fun getSongByMediaId(mediaId: String): Song?

    @Query("SELECT * FROM songs WHERE id = :id")
    suspend fun getSongById(id: Long): Song?

    @Query("SELECT * FROM songs WHERE fileName = :fileName LIMIT 1")
    suspend fun findSongByFileName(fileName: String): Song?

    @Query("SELECT * FROM songs WHERE uri = :uri LIMIT 1")
    suspend fun findSongByUri(uri: String): Song?

    @Query("UPDATE songs SET lyricsDelay = :delay WHERE id = :songId")
    suspend fun updateLyricsDelay(songId: Long, delay: Long)

    @Query("SELECT DISTINCT album FROM songs ORDER BY album ASC")
    fun getAllAlbums(): Flow<List<String>>

    @Query("SELECT album as albumName, artist as artistName, uri as representativeSongUri, folderPath as representativeFolderPath, COUNT(*) as songCount FROM songs GROUP BY album ORDER BY album ASC")
    fun getAlbumsWithSongCount(): Flow<List<AlbumInfo>>

    @Query("SELECT album as albumName, artist as artistName, uri as representativeSongUri, folderPath as representativeFolderPath, COUNT(*) as songCount FROM songs GROUP BY album ORDER BY album ASC")
    fun getAllAlbumsInfo(): Flow<List<AlbumInfo>>

    @Query("SELECT DISTINCT artist FROM songs ORDER BY artist ASC")
    fun getAllArtists(): Flow<List<String>>

    @Query("SELECT * FROM songs WHERE album = :album ORDER BY title ASC")
    fun getSongsByAlbum(album: String): Flow<List<Song>>

    @Query("SELECT * FROM songs WHERE artist = :artist ORDER BY title ASC")
    fun getSongsByArtist(artist: String): Flow<List<Song>>

    @Query("SELECT DISTINCT folderPath, folderName FROM songs")
    fun getAllFolders(): Flow<List<FolderInfo>>

    @Query("""
        SELECT 
            p.id, 
            p.name, 
            p.description, 
            p.customImageUri,
            (SELECT uri FROM songs JOIN playlist_song_cross_ref ON songs.id = playlist_song_cross_ref.songId WHERE playlist_song_cross_ref.playlistId = p.id ORDER BY playlist_song_cross_ref.position ASC LIMIT 1) as representativeSongUri,
            (SELECT folderPath FROM songs JOIN playlist_song_cross_ref ON songs.id = playlist_song_cross_ref.songId WHERE playlist_song_cross_ref.playlistId = p.id ORDER BY playlist_song_cross_ref.position ASC LIMIT 1) as representativeFolderPath
        FROM playlists p
        ORDER BY p.name ASC
    """)
    fun getAllPlaylistsInfo(): Flow<List<PlaylistInfo>>

    @Query("SELECT * FROM songs WHERE folderPath = :folderPath ORDER BY title ASC")
    fun getSongsByFolder(folderPath: String): Flow<List<Song>>

    // Playlist operations
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylist(playlist: Playlist): Long

    @Update
    suspend fun updatePlaylist(playlist: Playlist)

    @Delete
    suspend fun deletePlaylist(playlist: Playlist)

    @Query("SELECT * FROM playlists ORDER BY name ASC")
    fun getAllPlaylists(): Flow<List<Playlist>>

    @Query("SELECT * FROM playlists ORDER BY name ASC")
    suspend fun getAllPlaylistsList(): List<Playlist>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylistSongCrossRef(crossRef: PlaylistSongCrossRef)

    @Query("DELETE FROM playlist_song_cross_ref WHERE playlistId = :playlistId")
    suspend fun clearPlaylistSongs(playlistId: Long)

    @Query("DELETE FROM playlist_song_cross_ref WHERE playlistId = :playlistId AND songId = :songId")
    suspend fun removeSongFromPlaylist(playlistId: Long, songId: Long)

    @Transaction
    @Query("SELECT * FROM playlists WHERE id = :playlistId")
    fun getPlaylistWithSongs(playlistId: Long): Flow<PlaylistWithSongs>

    @Query("""
        SELECT songs.* FROM songs 
        JOIN playlist_song_cross_ref ON songs.id = playlist_song_cross_ref.songId 
        WHERE playlist_song_cross_ref.playlistId = :playlistId 
        ORDER BY playlist_song_cross_ref.position ASC
    """)
    fun getOrderedSongsInPlaylist(playlistId: Long): Flow<List<Song>>

    @Query("SELECT * FROM playlist_song_cross_ref WHERE playlistId = :playlistId ORDER BY position ASC")
    suspend fun getPlaylistCrossRefs(playlistId: Long): List<PlaylistSongCrossRef>

    @Update
    suspend fun updatePlaylistSongCrossRefs(crossRefs: List<PlaylistSongCrossRef>)
}

data class FolderInfo(
    val folderPath: String,
    val folderName: String
)

data class AlbumInfo(
    val albumName: String,
    val artistName: String,
    val representativeSongUri: String,
    val representativeFolderPath: String,
    val songCount: Int = 0
)

