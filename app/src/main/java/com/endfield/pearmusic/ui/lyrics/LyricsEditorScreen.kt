package com.endfield.pearmusic.ui.lyrics

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.endfield.pearmusic.lyrics.LyricLine
import com.endfield.pearmusic.ui.player.formatTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LyricsEditorScreen(
    viewModel: LyricsEditorViewModel,
    onBack: () -> Unit
) {
    val song by viewModel.song
    val rawLyrics by viewModel.rawLyrics
    val lyricLines by viewModel.lyricLines
    val currentLineIndex by viewModel.currentLineIndex
    val currentPosition by viewModel.currentPosition
    val isPlaying by viewModel.isPlaying
    
    val filePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { viewModel.loadLyricsFromUri(it) }
    }


    var isEditingRaw by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Lyrics Editor", style = MaterialTheme.typography.titleMedium)
                        song?.let {
                            Text(
                                "${it.title} - ${it.artist}",
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { isEditingRaw = !isEditingRaw }) {
                        Icon(
                            @Suppress("DEPRECATION")
                            if (isEditingRaw) Icons.Rounded.List else Icons.Rounded.EditNote,
                            contentDescription = if (isEditingRaw) "View List" else "Edit Text"
                        )
                    }
                    IconButton(onClick = { filePicker.launch("*/*") }) {
                        Icon(Icons.Rounded.Upload, contentDescription = "Load .lrc")
                    }
                    IconButton(onClick = { viewModel.saveLyrics() }) {
                        Icon(Icons.Rounded.Save, contentDescription = "Save")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (isEditingRaw) {
                OutlinedTextField(
                    value = rawLyrics,
                    onValueChange = { viewModel.updateRawLyrics(it) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(16.dp),
                    label = { Text("Raw Lyrics") },
                    placeholder = { Text("Enter lyrics here, one line per timestamp...") }
                )
            } else {
                LyricLinesList(
                    lines = lyricLines,
                    currentIndex = currentLineIndex,
                    currentPosition = currentPosition,
                    onLineClick = { viewModel.setCurrentLine(it) },
                    modifier = Modifier.weight(1f)
                )
            }

            EditorPlaybackControls(
                isPlaying = isPlaying,
                currentPosition = currentPosition,
                duration = song?.duration ?: 0L,
                onTogglePlay = { viewModel.togglePlayPause() },
                onMarkTimestamp = { viewModel.markTimestamp() },
                onSeek = { viewModel.seekTo(it) }
            )
        }
    }
}

@Composable
fun LyricLinesList(
    lines: List<LyricLine>,
    currentIndex: Int,
    currentPosition: Long,
    onLineClick: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()

    LaunchedEffect(currentIndex) {
        if (lines.isNotEmpty()) {
            listState.animateScrollToItem(currentIndex, scrollOffset = -200)
        }
    }

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(16.dp)
    ) {
        itemsIndexed(lines) { index, line ->
            val isCurrent = index == currentIndex
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .clickable { onLineClick(index) },
                colors = CardDefaults.cardColors(
                    containerColor = if (isCurrent) MaterialTheme.colorScheme.primaryContainer 
                                     else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = formatTime(line.startTime),
                        style = MaterialTheme.typography.labelMedium,
                        color = if (isCurrent) MaterialTheme.colorScheme.primary 
                                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.width(60.dp)
                    )
                    Text(
                        text = if (line.content.isBlank()) "--- [Instrumental] ---" else line.content,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                        color = if (line.content.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                    if (isCurrent) {
                        val nextLine = if (index < lines.size - 1) lines[index + 1] else null
                        val lineDuration = nextLine?.let { it.startTime - line.startTime } ?: 4000L
                        
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            AnimatedVisibility(
                                visible = (index == 0 && currentPosition < line.startTime && line.startTime > 0) ||
                                          (currentPosition >= line.startTime && nextLine != null && nextLine.content.isNotBlank()),
                                enter = fadeIn() + expandHorizontally(),
                                exit = fadeOut() + shrinkHorizontally()
                            ) {
                                val progress = if (index == 0 && currentPosition < line.startTime) {
                                    (currentPosition.toFloat() / line.startTime).coerceIn(0f, 1f)
                                } else {
                                    ((currentPosition - line.startTime).toFloat() / lineDuration.coerceAtLeast(1L)).coerceIn(0f, 1f)
                                }
                                LyricCountdownIndicator(progress)
                            }
                            
                            // Only show play arrow if countdown is NOT active
                            if (!((index == 0 && currentPosition < line.startTime && line.startTime > 0) ||
                                  (currentPosition >= line.startTime && nextLine != null && nextLine.content.isNotBlank()))) {
                                Icon(
                                    Icons.Rounded.PlayArrow,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun LyricCountdownIndicator(progress: Float) {
    Row(
        modifier = Modifier.padding(horizontal = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(3) { i ->
            val segmentStart = i / 3f
            val dotProgress = ((progress - segmentStart) * 3f).coerceIn(0f, 1f)
            val isActive = dotProgress > 0f

            Box(
                modifier = Modifier
                    .size(6.dp)
                    .background(
                        if (isActive) MaterialTheme.colorScheme.primary 
                        else MaterialTheme.colorScheme.primary.copy(alpha = 0.2f), 
                        CircleShape
                    )
            )
        }
    }
}

@Composable
fun EditorPlaybackControls(
    isPlaying: Boolean,
    currentPosition: Long,
    duration: Long,
    onTogglePlay: () -> Unit,
    onMarkTimestamp: () -> Unit,
    onSeek: (Long) -> Unit
) {
    Surface(
        tonalElevation = 8.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Slider(
                value = currentPosition.toFloat(),
                onValueChange = { onSeek(it.toLong()) },
                valueRange = 0f..duration.toFloat().coerceAtLeast(1f)
            )
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(formatTime(currentPosition), style = MaterialTheme.typography.labelSmall)
                Text(formatTime(duration), style = MaterialTheme.typography.labelSmall)
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onTogglePlay) {
                    Icon(
                        if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = "Play/Pause",
                        modifier = Modifier.size(32.dp)
                    )
                }

                Button(
                    onClick = onMarkTimestamp,
                    modifier = Modifier.height(56.dp).weight(1f).padding(horizontal = 16.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(Icons.Rounded.Timer, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("MARK TIMESTAMP")
                }
            }
        }
    }
}
