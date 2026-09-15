package com.endfield.pearmusic.ui.player

import android.app.Activity
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.foundation.Canvas
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.media3.common.Player
import coil.compose.AsyncImage
import com.endfield.pearmusic.ui.coil.MediaArtRequest
import androidx.compose.ui.tooling.preview.Preview
import com.endfield.pearmusic.data.local.Song
import com.endfield.pearmusic.lyrics.LyricLine
import com.endfield.pearmusic.ui.theme.PearMusicTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt

@Composable
fun NowPlayingScreen(
    viewModel: NowPlayingViewModel,
    onBack: () -> Unit,
    onEditLyrics: (Song) -> Unit,
    onManualSearchLyrics: () -> Unit
) {
    val songState by viewModel.currentSong
    val isPlaying by viewModel.isPlaying
    val currentPosition by viewModel.currentPosition
    val bufferedPosition by viewModel.bufferedPosition
    val isBuffering by viewModel.isBuffering
    val repeatMode by viewModel.repeatMode
    val lyrics by viewModel.lyrics
    val isLyricsLoading by viewModel.isLyricsLoading
    val queue by viewModel.queue
    val activeLyricIndex by viewModel.activeLyricIndex
    val lyricsDelay by viewModel.lyricsDelay

    // Smooth position interpolation for Karaoke animation
    var smoothPosition by remember { mutableLongStateOf(0L) }
    LaunchedEffect(currentPosition, isPlaying) {
        if (isPlaying) {
            val startPos = currentPosition
            val startTime = System.currentTimeMillis()
            while (true) {
                val elapsed = System.currentTimeMillis() - startTime
                smoothPosition = startPos + elapsed
                delay(16) // Target 60fps
            }
        } else {
            smoothPosition = currentPosition
        }
    }

    var showLyrics by remember { mutableStateOf(false) }
    var showQueue by remember { mutableStateOf(false) }
    var showLyricsDelayDialog by remember { mutableStateOf(false) }

    // REACTIVE LYRICS UPDATE: Force visibility when manually applied
    LaunchedEffect(viewModel) {
        viewModel.lyricsUpdateEvent.collect {
            showLyrics = true
        }
    }

    // Launcher for picking the .lrc file from phone storage
    val lyricsPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { selectedUri ->
            songState?.let { song ->
                viewModel.importLyricsFile(song, selectedUri)
            }
        }
    }

    // Ensure current song is synced when screen is opened
    LaunchedEffect(songState) {
        if (songState == null) {
            delay(100)
            if (viewModel.currentSong.value == null) {
                onBack()
            }
        }
    }

    val currentSong = songState ?: return

    // Swipe down to dismiss states and animation logic
    var offsetY by remember { mutableStateOf(0f) }
    val coroutineScope = rememberCoroutineScope()

    val swipeDownModifier = Modifier.pointerInput(Unit) {
        detectVerticalDragGestures(
            onVerticalDrag = { _, dragAmount ->
                offsetY = (offsetY + dragAmount).coerceAtLeast(0f)
            },
            onDragCancel = {
                coroutineScope.launch {
                    Animatable(offsetY).animateTo(
                        targetValue = 0f,
                        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)
                    ) {
                        offsetY = value
                    }
                }
            },
            onDragEnd = {
                if (offsetY > 250f) {
                    onBack()
                } else {
                    coroutineScope.launch {
                        Animatable(offsetY).animateTo(
                            targetValue = 0f,
                            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)
                        ) {
                            offsetY = value
                        }
                    }
                }
            }
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .offset { IntOffset(0, offsetY.roundToInt()) }
    ) {
        NowPlayingScreenContent(
            song = currentSong,
            isPlaying = isPlaying,
            isBuffering = isBuffering,
            currentPosition = currentPosition,
            smoothPosition = smoothPosition,
            bufferedPosition = bufferedPosition,
            repeatMode = repeatMode,
            lyrics = lyrics,
            isLyricsLoading = isLyricsLoading,
            activeLyricIndex = activeLyricIndex,
            onBack = onBack,
            onTogglePlay = { viewModel.togglePlayPause() },
            onSeek = { viewModel.seekTo(it) },
            onNext = { viewModel.skipToNext() },
            onPrevious = { viewModel.skipToPrevious() },
            onToggleRepeat = { viewModel.toggleRepeatMode() },
            onLyricsToggle = { showLyrics = !showLyrics },
            lyricsActive = showLyrics,
            onLyricClick = { viewModel.seekTo(it.startTime) },
            onEditLyrics = { onEditLyrics(currentSong) },
            onAddLyricsClick = { lyricsPickerLauncher.launch("*/*") },
            onManualSearchLyrics = onManualSearchLyrics,
            queue = queue,
            onQueueReorder = { from, to -> viewModel.reorderQueue(from, to) },
            onQueueClick = { showQueue = true },
            onLyricsDelayClick = { showLyricsDelayDialog = true },
            swipeDownModifier = swipeDownModifier
        )

        if (showQueue) {
            QueueBottomSheet(
                queue = queue,
                currentSong = currentSong,
                onDismiss = { showQueue = false },
                onSongClick = { song -> viewModel.playSong(song, queue) },
                onReorder = { from, to -> viewModel.reorderQueue(from, to) }
            )
        }

        if (showLyricsDelayDialog) {
            LyricsDelayDialog(
                currentDelay = lyricsDelay,
                onDelayChange = { viewModel.setLyricsDelay(it) },
                onDismiss = { showLyricsDelayDialog = false }
            )
        }
    }
}

@Preview(showBackground = true, device = "spec:width=411dp,height=891dp,dpi=420")
@Composable
fun NowPlayingScreenPreview() {
    val mockSong = Song(
        mediaId = "1",
        title = "Imagine",
        artist = "John Lennon",
        album = "Imagine",
        uri = "mock_uri",
        fileName = "mock_file.flac",
        duration = 180000,
        folderPath = "/music",
        folderName = "Classic",
        samplingRate = 48000,
        bitDepth = 24,
        isLossless = true
    )

    PearMusicTheme {
        Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
            NowPlayingScreenContent(
                song = mockSong,
                isPlaying = true,
                isBuffering = false,
                currentPosition = 30000,
                smoothPosition = 30000,
                bufferedPosition = 60000,
                repeatMode = Player.REPEAT_MODE_OFF,
                lyrics = listOf(
                    LyricLine(0, "Imagine there's no heaven"),
                    LyricLine(5000, "It's easy if you try"),
                    LyricLine(10000, "No hell below us"),
                    LyricLine(15000, "Above us only sky")
                ),
                activeLyricIndex = 2,
                onBack = {},
                onTogglePlay = {},
                onSeek = {},
                onNext = {},
                onPrevious = {},
                onToggleRepeat = {},
                onLyricsToggle = {},
                lyricsActive = false,
                onLyricClick = {},
                onEditLyrics = {},
                onAddLyricsClick = {},
                onManualSearchLyrics = {},
                queue = emptyList(),
                onQueueReorder = { _, _ -> },
                onQueueClick = {},
                onLyricsDelayClick = {},
                isLyricsLoading = false,
                swipeDownModifier = Modifier
            )
        }
    }
}

@Composable
fun NowPlayingScreenContent(
    song: Song,
    isPlaying: Boolean,
    isBuffering: Boolean,
    currentPosition: Long,
    smoothPosition: Long,
    bufferedPosition: Long,
    repeatMode: Int,
    lyrics: List<LyricLine>,
    isLyricsLoading: Boolean,
    activeLyricIndex: Int,
    onBack: () -> Unit,
    onTogglePlay: () -> Unit,
    onSeek: (Long) -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onToggleRepeat: () -> Unit,
    onLyricsToggle: () -> Unit,
    lyricsActive: Boolean,
    onLyricClick: (LyricLine) -> Unit,
    onEditLyrics: () -> Unit,
    onAddLyricsClick: () -> Unit,
    onManualSearchLyrics: () -> Unit,
    queue: List<Song>,
    onQueueReorder: (Int, Int) -> Unit,
    onQueueClick: () -> Unit,
    onLyricsDelayClick: () -> Unit,
    swipeDownModifier: Modifier
) {
    var internalShowControls by remember { mutableStateOf(true) }
    var showMenu by remember { mutableStateOf(false) }

    LaunchedEffect(lyricsActive) {
        if (!lyricsActive) {
            internalShowControls = true
        }
    }

    LaunchedEffect(internalShowControls, isPlaying, lyricsActive) {
        if (lyricsActive && internalShowControls && isPlaying) {
            delay(5000)
            internalShowControls = false
        }
    }

    val isFullscreenLyrics = lyricsActive && !internalShowControls

    // Manage System Status Bar Visibility
    val view = LocalView.current
    DisposableEffect(isFullscreenLyrics) {
        val window = (view.context as? Activity)?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }

        if (isFullscreenLyrics) {
            controller?.hide(WindowInsetsCompat.Type.statusBars())
        } else {
            controller?.show(WindowInsetsCompat.Type.statusBars())
        }

        onDispose {
            controller?.show(WindowInsetsCompat.Type.statusBars())
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(lyricsActive) {
                detectTapGestures(
                    onTap = {
                        if (lyricsActive) {
                            internalShowControls = !internalShowControls
                        }
                    }
                )
            }
    ) {
        // Blurred Background (Album Art)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            AsyncImage(
                model = MediaArtRequest(song.uri, song.folderPath, song.coverArtUrl, song.albumId),
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .blur(40.dp),
                contentScale = ContentScale.Crop,
                alpha = 0.45f
            )
        }

        // Gradient Overlay
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Black.copy(alpha = 0.3f), Color.Black.copy(alpha = 0.7f))
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(swipeDownModifier)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = "Back", tint = Color.White)
                }

                AnimatedVisibility(
                    visible = internalShowControls,
                    enter = fadeIn() + slideInVertically(initialOffsetY = { -it }),
                    exit = fadeOut() + slideOutVertically(targetOffsetY = { -it })
                ) {
                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Rounded.MoreHoriz, contentDescription = "More", tint = Color.White)
                        }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false },
                            modifier = Modifier.background(MaterialTheme.colorScheme.surface)
                        ) {
                            DropdownMenuItem(
                                text = { Text("Add Lyrics From Storage") },
                                onClick = {
                                    showMenu = false
                                    onAddLyricsClick()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Manual Search Lyrics") },
                                onClick = {
                                    showMenu = false
                                    onManualSearchLyrics()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Edit Lyrics") },
                                onClick = {
                                    showMenu = false
                                    onEditLyrics()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Lyrics Delay") },
                                onClick = {
                                    showMenu = false
                                    onLyricsDelayClick()
                                }
                            )
                        }
                    }
                }
            }

            // Album Art and Lyrics / No Lyrics View
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.TopCenter
            ) {
                AnimatedContent(
                    targetState = lyricsActive,
                    transitionSpec = {
                        if (targetState) {
                            (slideInVertically { height -> height } + fadeIn()).togetherWith(
                                slideOutVertically { height -> -height } + fadeOut())
                        } else {
                            (slideInVertically { height -> -height } + fadeIn()).togetherWith(
                                slideOutVertically { height -> height } + fadeOut())
                        }.using(
                            SizeTransform(clip = false)
                        )
                    },
                    label = "LyricsArtTransition"
                ) { targetLyricsActive ->
                    if (!targetLyricsActive) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .then(swipeDownModifier),
                            contentAlignment = Alignment.Center
                        ) {
                            AnimatedAlbumArt(song = song, isPlaying = isPlaying, isBuffering = isBuffering)
                        }
                    } else {
                        if (isLyricsLoading) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                                modifier = Modifier.fillMaxSize()
                            ) {
                                CircularProgressIndicator(
                                    color = Color.White.copy(alpha = 0.7f),
                                    modifier = Modifier.size(48.dp)
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = "Loading lyrics...",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color.White.copy(alpha = 0.5f)
                                )
                            }
                        } else if (lyrics.isEmpty()) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .then(swipeDownModifier)
                                    .padding(24.dp)
                            ) {
                                Icon(
                                    Icons.Rounded.Lyrics,
                                    contentDescription = null,
                                    modifier = Modifier.size(64.dp),
                                    tint = Color.White.copy(alpha = 0.4f)
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = "No Lyrics Found",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = Color.White.copy(alpha = 0.7f),
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Add an .lrc file from the menu or try manual search",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color.White.copy(alpha = 0.5f),
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(24.dp))
                                Button(
                                    onClick = onManualSearchLyrics,
                                    colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.2f))
                                ) {
                                    Text("Search Lyrics Online", color = Color.White)
                                }
                            }
                        } else {
                            LyricsView(
                                lyrics = lyrics,
                                activeIndex = activeLyricIndex,
                                currentPosition = currentPosition,
                                smoothPosition = smoothPosition,
                                mediaId = song.mediaId,
                                onLyricClick = {
                                    onLyricClick(it)
                                    internalShowControls = true
                                }
                            )
                        }
                    }
                }
            }

            // Song Info and Badges Section
            AnimatedVisibility(
                visible = true,
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = song.title,
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(2.dp))

                    Text(
                        text = song.artist,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.7f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    // High-Fidelity Badges
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val sampleRate = song.samplingRate ?: 0
                        val bitDepth = song.bitDepth ?: 16
                        val isHighRes = sampleRate >= 48000 || bitDepth > 16
                        val format = song.format?.lowercase() ?: ""
                        val isFlacOrWav = format == "flac" || format == "wav" || song.isLossless

                        if (isFlacOrWav && !isHighRes) {
                            Badge("LOSSLESS")
                        }
                        if (isHighRes) {
                            Badge("HI-RES")
                        }
                        Text(
                            text = "${song.samplingRate?.let { String.format("%.1f", it / 1000.0) } ?: "44.1"} kHz / ${song.bitDepth ?: 16} Bit",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.5f)
                        )
                        if (isPlaying) {
                            Text(
                                text = " • Normalized",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                            )
                        }
                    }
                }
            }

            // Playback Controls
            PlaybackControls(
                isPlaying = isPlaying,
                isBuffering = isBuffering,
                currentPosition = currentPosition,
                bufferedPosition = bufferedPosition,
                duration = song.duration,
                repeatMode = repeatMode,
                onTogglePlay = {
                    onTogglePlay()
                    internalShowControls = true
                },
                onSeek = {
                    onSeek(it)
                    internalShowControls = true
                },
                onNext = {
                    onNext()
                    internalShowControls = true
                },
                onPrevious = {
                    onPrevious()
                    internalShowControls = true
                },
                onToggleRepeat = {
                    onToggleRepeat()
                    internalShowControls = true
                },
                onLyricsToggle = {
                    onLyricsToggle()
                    internalShowControls = true
                },
                lyricsActive = lyricsActive,
                onEditLyrics = {
                    onEditLyrics()
                    internalShowControls = true
                },
                onQueueClick = onQueueClick,
                isMinimal = isFullscreenLyrics
            )
        }
    }
}


@Composable
fun AnimatedAlbumArt(song: Song, isPlaying: Boolean, isBuffering: Boolean) {
    val scale by animateFloatAsState(
        targetValue = if (isPlaying && !isBuffering) 1f else 0.85f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "AlbumArtScale"
    )

    Box(contentAlignment = Alignment.Center) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.62f)
                .aspectRatio(1f)
                .graphicsLayer(scaleX = scale, scaleY = scale)
                .clip(RoundedCornerShape(20.dp)),
            elevation = CardDefaults.cardElevation(defaultElevation = 16.dp)
        ) {
            AsyncImage(
                model = MediaArtRequest(song.uri, song.folderPath, song.coverArtUrl, song.albumId),
                contentDescription = "Album Art",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }

        if (isBuffering) {
            CircularProgressIndicator(
                color = Color.White,
                modifier = Modifier.size(48.dp)
            )
        }
    }
}

@Composable
fun LyricsView(
    lyrics: List<LyricLine>,
    activeIndex: Int,
    currentPosition: Long,
    smoothPosition: Long,
    mediaId: String,
    onLyricClick: (LyricLine) -> Unit
) {
    val listState = rememberLazyListState()

    LaunchedEffect(mediaId, lyrics) {
        if (lyrics.isNotEmpty()) {
            listState.scrollToItem(0)
        }
    }

    LaunchedEffect(activeIndex) {
        if (lyrics.isNotEmpty()) {
            if (activeIndex >= 0) {
                listState.animateScrollToItem(activeIndex, scrollOffset = 0)
            } else {
                listState.animateScrollToItem(0)
            }
        }
    }

    val musicIcons = listOf("♪", "♫", "♬", "♩", "🎶", "...", "[music]", "[instrumental]", "(music)", "(instrumental)")

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 48.dp, bottom = 100.dp, start = 24.dp, end = 24.dp)
    ) {
        if (lyrics.isNotEmpty()) {
            val firstLine = lyrics[0]
            val leadInDuration = firstLine.startTime
            item {
                AnimatedVisibility(
                    visible = activeIndex < 0 && leadInDuration > 0 && smoothPosition < leadInDuration,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    val progress = (smoothPosition.toFloat() / leadInDuration).coerceIn(0f, 1f)
                    InstrumentalLine(
                        isActive = false,
                        progress = progress,
                        showCountdown = true,
                        onLyricClick = {}
                    )
                }
            }
        }

        itemsIndexed(lyrics) { index, lyric ->
            val isActiveLine = index == activeIndex || (activeIndex >= 0 && lyrics[activeIndex].startTime == lyric.startTime)
            val isPassed = index < activeIndex && !isActiveLine
            val nextLyric = if (index < lyrics.size - 1) lyrics[index + 1] else null
            val isInstrumental = lyric.content.isBlank() || musicIcons.any { lyric.content.contains(it, ignoreCase = true) }

            val lineDuration = nextLyric?.let { it.startTime - lyric.startTime } ?: 4000L

            val alpha by animateFloatAsState(
                targetValue = if (isActiveLine) 1f else if (isPassed) 0.3f else 0.5f,
                animationSpec = tween(600, easing = FastOutSlowInEasing),
                label = "LyricAlpha"
            )

            val verticalOffset by animateDpAsState(
                targetValue = if (isActiveLine) (-4).dp else 0.dp,
                animationSpec = spring(stiffness = Spring.StiffnessLow, dampingRatio = Spring.DampingRatioMediumBouncy),
                label = "LyricMotion"
            )

            val scale by animateFloatAsState(
                targetValue = if (isActiveLine) 1.08f else 1f,
                animationSpec = spring(stiffness = Spring.StiffnessMedium, dampingRatio = Spring.DampingRatioLowBouncy),
                label = "LyricScale"
            )

            val blurRadius by animateDpAsState(
                targetValue = if (isPassed) 4.dp else 0.dp,
                animationSpec = tween(600),
                label = "LyricBlur"
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer {
                        translationY = verticalOffset.toPx()
                    }
            ) {
                if (isInstrumental) {
                    val progress = ((smoothPosition - lyric.startTime).toFloat() / lineDuration.coerceAtLeast(1L)).coerceIn(0f, 1f)
                    InstrumentalLine(
                        isActive = false,
                        progress = progress,
                        showCountdown = isActiveLine,
                        onLyricClick = { onLyricClick(lyric) }
                    )
                } else {
                    if (lyric.words.isNotEmpty() && isActiveLine) {
                        KaraokeLyricLine(
                            lyric = lyric,
                            currentPosition = smoothPosition,
                            alpha = alpha,
                            scale = scale,
                            blurRadius = blurRadius,
                            onLyricClick = { onLyricClick(lyric) }
                        )
                    } else {
                        val annotatedContent = buildAnnotatedString {
                            if (lyric.words.isNotEmpty()) {
                                lyric.words.forEach { word ->
                                    val isWordActive = smoothPosition >= word.startTime
                                    val wordColor = if (isActiveLine) {
                                        if (isWordActive) Color.White else Color.White.copy(alpha = 0.5f)
                                    } else {
                                        Color.White
                                    }
                                    withStyle(style = SpanStyle(color = wordColor)) {
                                        append(word.text)
                                    }
                                }
                            } else {
                                append(lyric.content)
                            }
                        }

                        Text(
                            text = annotatedContent,
                            style = MaterialTheme.typography.headlineSmall,
                            color = Color.White,
                            fontWeight = if (isActiveLine) FontWeight.Bold else FontWeight.Normal,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp)
                                .graphicsLayer(alpha = alpha, scaleX = scale, scaleY = scale)
                                .blur(blurRadius)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isActiveLine) Color.White.copy(alpha = 0.15f) else Color.Transparent)
                                .clickable { onLyricClick(lyric) }
                                .padding(8.dp),
                            textAlign = TextAlign.Start
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun KaraokeLyricLine(
    lyric: LyricLine,
    currentPosition: Long,
    alpha: Float,
    scale: Float,
    blurRadius: Dp,
    onLyricClick: () -> Unit
) {
    val inactiveColor = Color.White.copy(alpha = 0.4f)
    val activeColor = Color.White
    val textStyle = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold)

    val textMeasurer = rememberTextMeasurer()
    val textLayoutResult = remember(lyric.content, textStyle) {
        textMeasurer.measure(lyric.content, textStyle)
    }

    val charProgress by remember(currentPosition, lyric.words) {
        derivedStateOf {
            if (lyric.words.isEmpty()) 0f
            else {
                var currentProg = 0f
                val estimatedLineEnd = lyric.startTime + 4000L

                for (i in lyric.words.indices) {
                    val word = lyric.words[i]
                    val nextWordStart = if (i < lyric.words.size - 1) lyric.words[i + 1].startTime else estimatedLineEnd
                    val wordLen = word.text.length

                    if (currentPosition >= nextWordStart) {
                        currentProg += wordLen
                    } else if (currentPosition >= word.startTime) {
                        val wordDuration = (nextWordStart - word.startTime).coerceAtLeast(1L)
                        val wordPercent = ((currentPosition - word.startTime).toFloat() / wordDuration).coerceIn(0f, 1f)
                        currentProg += wordLen * wordPercent
                        break
                    } else {
                        break
                    }
                }
                currentProg
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp)
            .graphicsLayer(alpha = alpha, scaleX = scale, scaleY = scale)
            .blur(blurRadius)
            .clip(RoundedCornerShape(8.dp))
            .background(Color.White.copy(alpha = 0.15f))
            .clickable { onLyricClick() }
            .padding(8.dp)
    ) {
        Text(
            text = lyric.content,
            style = textStyle,
            color = inactiveColor,
            textAlign = TextAlign.Start
        )

        Box(modifier = Modifier.drawWithContent {
            val totalChars = lyric.content.length
            if (totalChars > 0) {
                for (lineIndex in 0 until textLayoutResult.lineCount) {
                    val lineStart = textLayoutResult.getLineStart(lineIndex)
                    val lineEnd = textLayoutResult.getLineEnd(lineIndex)

                    val activeInLine = (charProgress - lineStart).coerceIn(0f, (lineEnd - lineStart).toFloat())

                    if (activeInLine > 0) {
                        val lineTop = textLayoutResult.getLineTop(lineIndex)
                        val lineBottom = textLayoutResult.getLineBottom(lineIndex)
                        val startX = textLayoutResult.getHorizontalPosition(lineStart, true)

                        val wholeCharsInLine = activeInLine.toInt()
                        val fraction = activeInLine - wholeCharsInLine

                        val endX = if (wholeCharsInLine >= (lineEnd - lineStart)) {
                            textLayoutResult.getHorizontalPosition(lineEnd, true)
                        } else {
                            val charIdx = lineStart + wholeCharsInLine
                            val charStartX = textLayoutResult.getHorizontalPosition(charIdx, true)
                            val charEndX = textLayoutResult.getHorizontalPosition(charIdx + 1, true)
                            charStartX + (charEndX - charStartX) * fraction
                        }

                        clipRect(
                            left = startX,
                            top = lineTop,
                            right = endX,
                            bottom = lineBottom
                        ) {
                            this@drawWithContent.drawContent()
                        }
                    }
                }
            }
        }) {
            Text(
                text = lyric.content,
                style = textStyle,
                color = activeColor,
                textAlign = TextAlign.Start
            )
        }
    }
}

@Composable
fun InstrumentalLine(
    isActive: Boolean,
    progress: Float,
    showCountdown: Boolean = false,
    onLyricClick: () -> Unit
) {
    if (!isActive && !showCountdown) return

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (isActive) Color.White.copy(alpha = 0.15f) else Color.Transparent)
            .clickable { onLyricClick() }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start
    ) {
        LyricCountdownIndicator(progress)
    }
}

@Composable
fun LyricCountdownIndicator(progress: Float) {
    Row(
        modifier = Modifier.padding(start = 4.dp, top = 8.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(3) { i ->
            val segmentStart = i / 3f
            val dotProgress = ((progress - segmentStart) * 3f).coerceIn(0f, 1f)
            val dotAlpha = 0.25f + (dotProgress * 0.75f)
            val dotScale = 0.7f + (dotProgress * 0.55f)

            Box(
                modifier = Modifier
                    .size(9.dp)
                    .graphicsLayer(alpha = dotAlpha, scaleX = dotScale, scaleY = dotScale)
                    .background(
                        Color.White,
                        CircleShape
                    )
            )
        }
    }
}

@Composable
fun PlaybackControls(
    isPlaying: Boolean,
    isBuffering: Boolean,
    currentPosition: Long,
    bufferedPosition: Long,
    duration: Long,
    repeatMode: Int,
    onTogglePlay: () -> Unit,
    onSeek: (Long) -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onToggleRepeat: () -> Unit,
    onLyricsToggle: () -> Unit,
    lyricsActive: Boolean,
    onEditLyrics: () -> Unit,
    onQueueClick: () -> Unit,
    isMinimal: Boolean = false
) {
    val verticalPadding by animateDpAsState(
        targetValue = if (isMinimal) 6.dp else 16.dp,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "PlaybackControlsPadding"
    )

    val navPadding by animateDpAsState(
        targetValue = if (isMinimal) 2.dp else 12.dp,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "NavPadding"
    )

    val iconSize by animateDpAsState(
        targetValue = if (isMinimal) 32.dp else 42.dp,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "IconSize"
    )

    val playButtonSize by animateDpAsState(
        targetValue = if (isMinimal) 50.dp else 72.dp,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "PlayButtonSize"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .padding(vertical = verticalPadding)
    ) {
        Column {
            var sliderValue by remember { mutableFloatStateOf(currentPosition.toFloat()) }
            var isDragging by remember { mutableStateOf(false) }

            LaunchedEffect(currentPosition) {
                if (!isDragging) {
                    sliderValue = currentPosition.toFloat()
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                LinearProgressIndicator(
                    progress = { (bufferedPosition.toFloat() / duration.toFloat().coerceAtLeast(1f)).coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .padding(horizontal = 4.dp),
                    color = Color.White.copy(alpha = 0.2f),
                    trackColor = Color.Transparent,
                )

                Slider(
                    value = sliderValue,
                    onValueChange = {
                        isDragging = true
                        sliderValue = it
                    },
                    onValueChangeFinished = {
                        onSeek(sliderValue.toLong())
                        isDragging = false
                    },
                    valueRange = 0f..duration.toFloat().coerceAtLeast(1f),
                    colors = SliderDefaults.colors(
                        thumbColor = Color.White,
                        activeTrackColor = Color.White,
                        inactiveTrackColor = Color.White.copy(alpha = 0.1f)
                    )
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(formatTime(currentPosition), color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
                Text(formatTime(duration), color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = navPadding),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onPrevious) {
                Icon(
                    Icons.Rounded.SkipPrevious,
                    contentDescription = "Previous",
                    tint = Color.White,
                    modifier = Modifier.size(iconSize)
                )
            }
            IconButton(onClick = onTogglePlay, modifier = Modifier.size(playButtonSize)) {
                Icon(
                    if (isPlaying) Icons.Rounded.PauseCircleFilled else Icons.Rounded.PlayCircleFilled,
                    contentDescription = "Play/Pause",
                    tint = Color.White,
                    modifier = Modifier.fillMaxSize()
                )
            }
            IconButton(onClick = onNext) {
                Icon(
                    Icons.Rounded.SkipNext,
                    contentDescription = "Next",
                    tint = Color.White,
                    modifier = Modifier.size(iconSize)
                )
            }
        }

        AnimatedVisibility(
            visible = !isMinimal,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                IconButton(onClick = onToggleRepeat) {
                    val icon = when (repeatMode) {
                        Player.REPEAT_MODE_ONE -> Icons.Rounded.RepeatOne
                        Player.REPEAT_MODE_ALL -> Icons.Rounded.Repeat
                        else -> Icons.Rounded.Repeat
                    }
                    val tint = if (repeatMode == Player.REPEAT_MODE_OFF) Color.White.copy(alpha = 0.5f) else Color.White
                    Icon(icon, contentDescription = "Repeat", tint = tint)
                }

                IconButton(onClick = onLyricsToggle) {
                    Icon(
                        Icons.Rounded.Lyrics,
                        contentDescription = "Lyrics",
                        tint = if (lyricsActive) Color.White else Color.White.copy(alpha = 0.5f)
                    )
                }

                IconButton(onClick = onQueueClick) {
                    Icon(Icons.AutoMirrored.Rounded.PlaylistPlay, contentDescription = "Queue", tint = Color.White.copy(alpha = 0.5f))
                }
            }
        }
    }
}

@Composable
fun Badge(text: String) {
    Surface(
        color = Color.White.copy(alpha = 0.2f),
        shape = RoundedCornerShape(4.dp),
        modifier = Modifier.padding(horizontal = 2.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = Color.White,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            fontWeight = FontWeight.Bold
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QueueBottomSheet(
    queue: List<Song>,
    currentSong: Song?,
    onDismiss: () -> Unit,
    onSongClick: (Song) -> Unit,
    onReorder: (Int, Int) -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        scrimColor = Color.Black.copy(alpha = 0.5f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp)
        ) {
            Text(
                "Up Next",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(16.dp)
            )

            LazyColumn(
                modifier = Modifier.fillMaxWidth()
            ) {
                itemsIndexed(queue) { index, song ->
                    val isCurrent = song.mediaId == currentSong?.mediaId
                    var dragOffsetY by remember { mutableFloatStateOf(0f) }
                    var isDragging by remember { mutableStateOf(false) }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .zIndex(if (isDragging) 1f else 0f)
                            .offset { IntOffset(0, dragOffsetY.roundToInt()) }
                            .clickable { onSongClick(song) }
                            .background(if (isCurrent) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f) else Color.Transparent)
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Rounded.MusicNote,
                            contentDescription = null,
                            tint = if (isCurrent) MaterialTheme.colorScheme.primary else Color.Gray,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                song.title,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                song.artist,
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.Gray
                            )
                        }
                        Icon(
                            Icons.Rounded.DragHandle,
                            contentDescription = "Reorder",
                            tint = Color.Gray,
                            modifier = Modifier.pointerInput(index) {
                                detectDragGesturesAfterLongPress(
                                    onDragStart = { isDragging = true },
                                    onDrag = { change, dragAmount ->
                                        change.consume()
                                        dragOffsetY += dragAmount.y
                                        val targetIndex = (index + (dragOffsetY / 64.dp.toPx())).roundToInt()
                                            .coerceIn(0, queue.size - 1)
                                        if (targetIndex != index) {
                                            onReorder(index, targetIndex)
                                            dragOffsetY = 0f
                                        }
                                    },
                                    onDragEnd = {
                                        isDragging = false
                                        dragOffsetY = 0f
                                    },
                                    onDragCancel = {
                                        isDragging = false
                                        dragOffsetY = 0f
                                    }
                                )
                            }
                        )
                    }
                }
            }
        }
    }
}

fun formatTime(millis: Long): String {
    val totalSeconds = millis / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}

@Composable
fun LyricsDelayDialog(
    currentDelay: Long,
    onDelayChange: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    var textValue by remember { mutableStateOf(currentDelay.toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Lyrics Delay (ms)") },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "Positive = Later, Negative = Earlier",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(16.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    IconButton(onClick = {
                        val newDelay = (textValue.toLongOrNull() ?: currentDelay) - 5
                        textValue = newDelay.toString()
                        onDelayChange(newDelay)
                    }) {
                        Icon(Icons.Rounded.Remove, contentDescription = "Decrease")
                    }

                    OutlinedTextField(
                        value = textValue,
                        onValueChange = {
                            textValue = it
                            it.toLongOrNull()?.let { delay -> onDelayChange(delay) }
                        },
                        modifier = Modifier.width(120.dp),
                        textStyle = LocalTextStyle.current.copy(textAlign = TextAlign.Center),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )

                    IconButton(onClick = {
                        val newDelay = (textValue.toLongOrNull() ?: currentDelay) + 5
                        textValue = newDelay.toString()
                        onDelayChange(newDelay)
                    }) {
                        Icon(Icons.Rounded.Add, contentDescription = "Increase")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Done")
            }
        }
    )
}