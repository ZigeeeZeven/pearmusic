package com.endfield.pearmusic.ui.lyrics

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.endfield.pearmusic.api.LrcLibResponse
import com.endfield.pearmusic.ui.player.NowPlayingViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LyricsSearchScreen(
    viewModel: NowPlayingViewModel,
    onBack: () -> Unit
) {
    val song = viewModel.currentSong.value ?: return
    var artistQuery by remember { mutableStateOf(song.artist) }
    var titleQuery by remember { mutableStateOf(song.title) }
    
    var searchResults by remember { mutableStateOf<List<LrcLibResponse>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }

    // Trigger initial search
    LaunchedEffect(Unit) {
        isSearching = true
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Search Lyrics", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            // Search Fields
            OutlinedTextField(
                value = artistQuery,
                onValueChange = { artistQuery = it },
                label = { Text("Artist") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = titleQuery,
                onValueChange = { titleQuery = it },
                label = { Text("Title") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            
            // Search Button
            Button(
                onClick = { isSearching = true },
                modifier = Modifier
                    .padding(vertical = 16.dp)
                    .align(Alignment.End)
            ) {
                Icon(Icons.Rounded.Search, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Search")
            }

            // Search logic
            LaunchedEffect(isSearching) {
                if (isSearching) {
                    val query = "${artistQuery} ${titleQuery}"
                    searchResults = viewModel.performManualSearch(query)
                    isSearching = false
                }
            }

            if (isSearching) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (searchResults.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No lyrics found", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 16.dp)
                ) {
                    items(searchResults) { result ->
                        SearchResultItem(result) {
                            viewModel.applySelectedLyrics(result)
                            onBack()
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SearchResultItem(result: LrcLibResponse, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = result.name ?: "Unknown Track",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = result.artistName ?: "Unknown Artist",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (result.albumName != null) {
                Text(
                    text = result.albumName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                val hasSynced = result.syncedLyrics != null
                AssistChip(
                    onClick = { },
                    label = { Text(if (hasSynced) "SYNCED" else "PLAIN") },
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = if (hasSynced) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer,
                        labelColor = if (hasSynced) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSecondaryContainer
                    )
                )
                Spacer(modifier = Modifier.width(8.dp))
                val duration = result.duration?.let { d ->
                    val min = (d / 60).toInt()
                    val sec = (d % 60).toInt()
                    "${min}:${sec.toString().padStart(2, '0')}"
                } ?: ""
                Text(text = duration, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}
