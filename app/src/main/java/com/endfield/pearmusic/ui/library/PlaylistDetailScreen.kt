package com.endfield.pearmusic.ui.library

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import com.endfield.pearmusic.data.local.Playlist
import com.endfield.pearmusic.data.local.PlaylistWithSongs
import com.endfield.pearmusic.data.local.Song
import com.endfield.pearmusic.ui.coil.MediaArtRequest
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistDetailScreen(
    playlistId: Long,
    viewModel: LibraryViewModel,
    onBack: () -> Unit,
    onSongClick: (Song, List<Song>) -> Unit,
) {
    val playlistWithSongs by viewModel.getPlaylistWithSongs(playlistId).collectAsState(initial = null)
    val songs by viewModel.getOrderedSongsInPlaylist(playlistId).collectAsState(initial = emptyList())
    val allSongs by viewModel.songs.collectAsState()

    var showSongPicker by remember { mutableStateOf(value = false) }
    
    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            playlistWithSongs?.playlist?.let { playlist ->
                viewModel.updatePlaylist(playlist.copy(customImageUri = it.toString()))
            }
        }
    }

    // Drag and drop state
    var draggedItemIndex by remember { mutableIntStateOf(-1) }
    var dragOffsetY by remember { mutableFloatStateOf(0f) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(playlistWithSongs?.playlist?.name ?: "Playlist", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showSongPicker = true }) {
                Icon(Icons.Rounded.Add, contentDescription = "Add Songs")
            }
        }
    ) { padding ->
        if (songs.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    Icons.Rounded.MusicNote,
                    contentDescription = null,
                    modifier = Modifier.padding(16.dp),
                    tint = MaterialTheme.colorScheme.outline
                )
                Text("No songs in playlist", style = MaterialTheme.typography.bodyMedium)
                Button(onClick = { showSongPicker = true }, modifier = Modifier.padding(16.dp)) {
                    Text("Add Songs")
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(bottom = 80.dp)
            ) {
                item {
                    val representativeSong = songs.firstOrNull()
                    val playlist = playlistWithSongs?.playlist
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(contentAlignment = Alignment.BottomEnd) {
                            Card(
                                modifier = Modifier
                                    .size(200.dp)
                                    .clickable { imagePicker.launch("image/*") },
                                shape = RoundedCornerShape(12.dp),
                                elevation = CardDefaults.cardElevation(defaultElevation = 12.dp)
                            ) {
                                AsyncImage(
                                    model = if (playlist?.customImageUri != null) {
                                        playlist.customImageUri
                                    } else {
                                        MediaArtRequest(representativeSong?.uri, representativeSong?.folderPath, representativeSong?.coverArtUrl, representativeSong?.albumId)
                                    },
                                    contentDescription = "Playlist Art",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop,
                                    error = ColorPainter(MaterialTheme.colorScheme.primaryContainer)
                                )
                            }
                            
                            SmallFloatingActionButton(
                                onClick = { imagePicker.launch("image/*") },
                                modifier = Modifier.padding(8.dp),
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                            ) {
                                Icon(Icons.Rounded.Edit, contentDescription = "Change Playlist Image", modifier = Modifier.size(16.dp))
                            }
                        }
                        Spacer(modifier = Modifier.height(20.dp))
                        Text(
                            text = playlist?.name ?: "",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            maxLines = 2,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        Text(
                            text = "${songs.size} songs",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }
                itemsIndexed(songs, key = { _, song -> song.id }) { index, song ->
                    val isDragging = index == draggedItemIndex
                    val offset = if (isDragging) dragOffsetY else 0f
                    
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .zIndex(if (isDragging) 1f else 0f)
                            .offset { IntOffset(0, offset.roundToInt()) }
                    ) {
                        PlaylistSongItem(
                            song = song,
                            onClick = {
                                onSongClick(song, songs)
                            },
                            onRemove = {
                                viewModel.removeSongFromPlaylist(playlistId, song.id)
                            },
                            dragHandle = {
                                Icon(
                                    imageVector = Icons.Rounded.DragHandle,
                                    contentDescription = "Drag to reorder",
                                    modifier = Modifier
                                        .padding(8.dp)
                                        .pointerInput(index) {
                                            detectDragGesturesAfterLongPress(
                                                onDragStart = {
                                                    draggedItemIndex = index
                                                },
                                                onDrag = { change, dragAmount ->
                                                    change.consume()
                                                    dragOffsetY += dragAmount.y
                                                    
                                                    // Simple swap logic
                                                    val targetIndex = (index + (dragOffsetY / 64.dp.toPx())).roundToInt()
                                                        .coerceIn(0, songs.size - 1)
                                                    
                                                    if (targetIndex != draggedItemIndex) {
                                                        viewModel.reorderPlaylist(playlistId, draggedItemIndex, targetIndex)
                                                        draggedItemIndex = targetIndex
                                                        dragOffsetY = 0f
                                                    }
                                                },
                                                onDragEnd = {
                                                    draggedItemIndex = -1
                                                    dragOffsetY = 0f
                                                },
                                                onDragCancel = {
                                                    draggedItemIndex = -1
                                                    dragOffsetY = 0f
                                                }
                                            )
                                        }
                                )
                            }
                        )
                    }
                }
            }
        }
    }

    if (showSongPicker) {
        SongPickerDialog(
            allSongs = allSongs,
            playlistSongs = songs,
            playlistId = playlistId,
            viewModel = viewModel,
            onDismiss = { showSongPicker = false }
        )
    }
}

@Composable
fun SongPickerDialog(
    allSongs: List<Song>,
    playlistSongs: List<Song>,
    playlistId: Long,
    viewModel: LibraryViewModel,
    onDismiss: () -> Unit
) {
    val playlistSongIds = remember(playlistSongs) {
        playlistSongs.asSequence().map { it.id }.toSet()
    }
    var selectedSongs by remember { mutableStateOf(setOf<Long>()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Songs to Playlist") },
        text = {
            LazyColumn(modifier = Modifier.fillMaxWidth()) {
                items(allSongs) { song ->
                    val isInPlaylist = playlistSongIds.contains(song.id)
                    val isSelected = selectedSongs.contains(song.id)

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = !isInPlaylist) {
                                if (isInPlaylist) return@clickable
                                selectedSongs = if (isSelected) {
                                    selectedSongs - song.id
                                } else {
                                    selectedSongs + song.id
                                }
                            }
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Checkbox(
                            checked = isSelected,
                            onCheckedChange = { checked ->
                                if (isInPlaylist) return@Checkbox
                                selectedSongs = if (checked) {
                                    selectedSongs + song.id
                                } else {
                                    selectedSongs - song.id
                                }
                            },
                            enabled = !isInPlaylist
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = song.title,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = song.artist,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        if (isInPlaylist) {
                            Icon(
                                Icons.Rounded.Check,
                                contentDescription = "Already in playlist",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(4.dp)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    viewModel.addSongsToPlaylist(playlistId, selectedSongs.toList())
                    onDismiss()
                },
                enabled = selectedSongs.isNotEmpty()
            ) {
                Text("Add ${selectedSongs.size} Song${if (selectedSongs.size != 1) "s" else ""}")
            }
        },
        dismissButton = {
            Button(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun PlaylistSongItem(
    song: Song,
    onClick: () -> Unit,
    onRemove: () -> Unit,
    dragHandle: @Composable () -> Unit = {}
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        color = Color.Transparent
    ) {
        Row(
            modifier = Modifier
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            dragHandle()
            AsyncImage(
                model = MediaArtRequest(song.uri, song.folderPath, song.coverArtUrl, song.albumId),
                contentDescription = null,
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Crop,
                error = ColorPainter(MaterialTheme.colorScheme.primaryContainer)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = song.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = song.artist,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            IconButton(onClick = onRemove) {
                Icon(
                    imageVector = Icons.Rounded.Delete,
                    contentDescription = "Remove Song from Playlist",
                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f)
                )
            }
        }
    }
}
