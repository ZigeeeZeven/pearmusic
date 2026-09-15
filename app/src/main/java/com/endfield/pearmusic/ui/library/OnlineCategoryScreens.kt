package com.endfield.pearmusic.ui.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.endfield.pearmusic.data.local.Song

@Composable
fun OnlineSongsScreen(
    viewModel: OnlineMusicViewModel,
    onBack: () -> Unit,
    onSongClick: (Song, List<Song>) -> Unit
) {
    val songs by viewModel.onlineSongsOnly.collectAsState()

    Scaffold(
        topBar = {
            OnlineCategoryHeader(title = "Online Songs", onBack = onBack)
        }
    ) { padding ->
        if (songs.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("No songs found", style = MaterialTheme.typography.bodyLarge)
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                items(songs, key = { it.mediaId }) { song ->
                    SongItem(song = song, onClick = { onSongClick(song, songs) })
                }
            }
        }
    }
}

@Composable
fun OnlineAlbumDetailScreen(
    albumName: String,
    viewModel: OnlineMusicViewModel,
    onBack: () -> Unit,
    onSongClick: (Song, List<Song>) -> Unit
) {
    val allSongs by viewModel.onlineSongs.collectAsState()
    val albumSongs = allSongs.filter { it.album == albumName }

    Scaffold(
        topBar = {
            OnlineCategoryHeader(title = albumName, onBack = onBack)
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            items(albumSongs, key = { it.mediaId }) { song ->
                SongItem(song = song, onClick = { onSongClick(song, albumSongs) })
            }
        }
    }
}

@Composable
fun OnlineArtistDetailScreen(
    artistName: String,
    viewModel: OnlineMusicViewModel,
    onBack: () -> Unit,
    onSongClick: (Song, List<Song>) -> Unit
) {
    val allSongs by viewModel.onlineSongs.collectAsState()
    val artistSongs = allSongs.filter { it.artist == artistName }

    Scaffold(
        topBar = {
            OnlineCategoryHeader(title = artistName, onBack = onBack)
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            items(artistSongs, key = { it.mediaId }) { song ->
                SongItem(song = song, onClick = { onSongClick(song, artistSongs) })
            }
        }
    }
}

@Composable
fun OnlinePlaylistDetailScreen(
    playlistName: String,
    viewModel: OnlineMusicViewModel,
    onBack: () -> Unit,
    onSongClick: (Song, List<Song>) -> Unit
) {
    val allSongs by viewModel.onlineSongs.collectAsState()
    val playlistSongs = allSongs.filter { it.folderPath == "online_m3u8" && it.folderName == playlistName }

    Scaffold(
        topBar = {
            OnlineCategoryHeader(title = playlistName, onBack = onBack)
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            items(playlistSongs, key = { it.mediaId }) { song ->
                SongItem(song = song, onClick = { onSongClick(song, playlistSongs) })
            }
        }
    }
}

@Composable
fun OnlineAlbumsScreen(
    viewModel: OnlineMusicViewModel,
    onBack: () -> Unit,
    onAlbumClick: (String) -> Unit
) {
    val albums by viewModel.onlineAlbums.collectAsState(initial = emptyList())

    Scaffold(
        topBar = {
            OnlineCategoryHeader(title = "Online Albums", onBack = onBack)
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            items(albums) { album ->
                OnlineCategoryListItem(name = album, icon = Icons.Rounded.Album) { onAlbumClick(album) }
            }
        }
    }
}

@Composable
fun OnlineArtistsScreen(
    viewModel: OnlineMusicViewModel,
    onBack: () -> Unit,
    onArtistClick: (String) -> Unit
) {
    val artists by viewModel.onlineArtists.collectAsState(initial = emptyList())

    Scaffold(
        topBar = {
            OnlineCategoryHeader(title = "Online Artists", onBack = onBack)
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            items(artists) { artist ->
                OnlineCategoryListItem(name = artist, icon = Icons.Rounded.Person) { onArtistClick(artist) }
            }
        }
    }
}

@Composable
fun OnlinePlaylistsScreen(
    viewModel: OnlineMusicViewModel,
    onBack: () -> Unit,
    onPlaylistClick: (String) -> Unit
) {
    val playlists by viewModel.onlinePlaylists.collectAsState(initial = emptyList())

    Scaffold(
        topBar = {
            OnlineCategoryHeader(title = "Online Playlists", onBack = onBack)
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            items(playlists) { playlist ->
                OnlineCategoryListItem(name = playlist, icon = Icons.AutoMirrored.Rounded.PlaylistPlay) { onPlaylistClick(playlist) }
            }
        }
    }
}

@Composable
fun OnlineCategoryHeader(title: String, onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .statusBarsPadding()
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
        }
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
            modifier = Modifier.padding(start = 8.dp)
        )
    }
}

@Composable
fun OnlineCategoryListItem(name: String, icon: ImageVector, onClick: () -> Unit) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp)
            )
            Text(
                text = name,
                style = MaterialTheme.typography.titleLarge.copy(fontSize = 18.sp),
                modifier = Modifier.padding(start = 16.dp)
            )
        }
        HorizontalDivider(
            modifier = Modifier.padding(horizontal = 16.dp),
            thickness = 0.5.dp,
            color = MaterialTheme.colorScheme.outlineVariant
        )
    }
}
