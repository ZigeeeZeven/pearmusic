package com.endfield.pearmusic.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import androidx.room.Delete
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaylistDao {
    @Query("SELECT * FROM playlists ORDER BY name ASC")
    fun getAllPlaylists(): Flow<List<Playlist>>

    @Query("SELECT * FROM playlists")
    suspend fun getAllPlaylistsOnce(): List<Playlist>

    @Query("""
        SELECT p.id, p.name, p.description, p.customImageUri, 
               (SELECT s.uri FROM songs s JOIN playlist_song_cross_ref pscr ON s.id = pscr.songId WHERE pscr.playlistId = p.id ORDER BY pscr.position ASC LIMIT 1) as representativeSongUri,
               (SELECT s.folderPath FROM songs s JOIN playlist_song_cross_ref pscr ON s.id = pscr.songId WHERE pscr.playlistId = p.id ORDER BY pscr.position ASC LIMIT 1) as representativeFolderPath
        FROM playlists p 
        ORDER BY p.name ASC
    """)
    fun getAllPlaylistsInfo(): Flow<List<PlaylistInfo>>

    @Transaction
    @Query("SELECT * FROM playlists WHERE id = :playlistId")
    fun getPlaylistWithSongs(playlistId: Long): Flow<PlaylistWithSongs?>

    @Query("SELECT s.* FROM songs s JOIN playlist_song_cross_ref pscr ON s.id = pscr.songId WHERE pscr.playlistId = :playlistId ORDER BY pscr.position ASC")
    fun getOrderedSongsInPlaylist(playlistId: Long): Flow<List<Song>>

    @Insert
    suspend fun insertPlaylist(playlist: Playlist): Long

    @Update
    suspend fun updatePlaylist(playlist: Playlist)

    @Delete
    suspend fun deletePlaylist(playlist: Playlist)

    @Query("SELECT MAX(position) FROM playlist_song_cross_ref WHERE playlistId = :playlistId")
    suspend fun getMaxOrder(playlistId: Long): Int?

    @Insert(onConflict = androidx.room.OnConflictStrategy.IGNORE)
    suspend fun insertPlaylistSongCrossRef(crossRef: PlaylistSongCrossRef)

    @Query("DELETE FROM playlist_song_cross_ref WHERE playlistId = :playlistId AND songId = :songId")
    suspend fun deletePlaylistSongCrossRef(playlistId: Long, songId: Long)
}
