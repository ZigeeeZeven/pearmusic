package com.endfield.pearmusic.ui.library

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

sealed interface LibraryDestination : NavKey {
    @Serializable
    data object Home : LibraryDestination

    @Serializable
    data object Songs : LibraryDestination

    @Serializable
    data object Albums : LibraryDestination

    @Serializable
    data object Artists : LibraryDestination

    @Serializable
    data object Playlists : LibraryDestination

    @Serializable
    data object Folders : LibraryDestination

    @Serializable
    data object FolderManagement : LibraryDestination

    @Serializable
    data object Settings : LibraryDestination

    @Serializable
    data object OnlineMusic : LibraryDestination

    @Serializable
    data object OnlineSongs : LibraryDestination

    @Serializable
    data object OnlineAlbums : LibraryDestination

    @Serializable
    data object OnlineArtists : LibraryDestination

    @Serializable
    data object OnlinePlaylists : LibraryDestination

    @Serializable
    data class OnlineAlbumDetail(val albumName: String) : LibraryDestination

    @Serializable
    data class OnlineArtistDetail(val artistName: String) : LibraryDestination

    @Serializable
    data class OnlinePlaylistDetail(val playlistName: String) : LibraryDestination

    @Serializable
    data object LyricsSearch : LibraryDestination

    @Serializable
    data object Search : LibraryDestination // <--- Add this line for Search support

    @Serializable
    data class AlbumDetail(val albumName: String) : LibraryDestination

    @Serializable
    data class ArtistDetail(val artistName: String) : LibraryDestination

    @Serializable
    data class PlaylistDetail(val playlistId: Long) : LibraryDestination

    @Serializable
    data class FolderDetail(val folderPath: String, val folderName: String) : LibraryDestination

    @Serializable
    data object Player : LibraryDestination

    @Serializable
    data class LyricsEditor(val songMediaId: String) : LibraryDestination
}