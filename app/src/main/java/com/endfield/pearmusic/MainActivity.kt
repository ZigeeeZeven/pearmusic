package com.endfield.pearmusic

import android.app.Application
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.*
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.navigation3.ListDetailSceneStrategy
import androidx.compose.material3.adaptive.navigation3.rememberListDetailSceneStrategy
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.endfield.pearmusic.data.MusicRepository
import com.endfield.pearmusic.data.repository.ThemeMode
import com.endfield.pearmusic.playback.PlaybackManager
import com.endfield.pearmusic.ui.library.*
import com.endfield.pearmusic.ui.library.OnlineSongsScreen
import com.endfield.pearmusic.ui.library.OnlineAlbumsScreen
import com.endfield.pearmusic.ui.library.OnlineArtistsScreen
import com.endfield.pearmusic.ui.library.OnlinePlaylistsScreen
import com.endfield.pearmusic.ui.library.OnlineAlbumDetailScreen
import com.endfield.pearmusic.ui.library.OnlineArtistDetailScreen
import com.endfield.pearmusic.ui.library.OnlinePlaylistDetailScreen
import com.endfield.pearmusic.ui.lyrics.LyricsEditorScreen
import com.endfield.pearmusic.ui.lyrics.LyricsEditorViewModel
import com.endfield.pearmusic.ui.lyrics.LyricsSearchScreen
import com.endfield.pearmusic.ui.player.MiniPlayer
import com.endfield.pearmusic.ui.player.NowPlayingScreen
import com.endfield.pearmusic.ui.player.NowPlayingViewModel
import com.endfield.pearmusic.ui.settings.FolderManagementScreen
import com.endfield.pearmusic.ui.settings.FolderManagementViewModel
import com.endfield.pearmusic.ui.settings.SettingsScreen
import com.endfield.pearmusic.ui.settings.SettingsViewModel
import com.endfield.pearmusic.ui.theme.PearMusicTheme
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds

class MainActivity : ComponentActivity() {
    @OptIn(ExperimentalMaterial3AdaptiveApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val app = application as PearMusicApplication
            val repository = app.repository
            val playbackManager = app.playbackManager

            val libraryViewModel: LibraryViewModel = viewModel {
                LibraryViewModel(repository, app.folderRepository)
            }
            val nowPlayingViewModel: NowPlayingViewModel = viewModel {
            NowPlayingViewModel(app, playbackManager, app.lrcLibService, repository)
        }
            val settingsViewModel: SettingsViewModel = viewModel {
                SettingsViewModel(app.settingsRepository)
            }
            val onlineMusicViewModel: OnlineMusicViewModel = viewModel {
                OnlineMusicViewModel(app.driveRepository)
            }

            val themeMode by settingsViewModel.themeMode.collectAsState()

            var isLoading by remember { mutableStateOf(value = true) }
            LaunchedEffect(Unit) {
                // Reduce delay for faster app entry, especially on restarts
                delay(800.milliseconds)
                isLoading = false
            }

            PearMusicTheme(themeMode = themeMode) {
                if (isLoading) {
                    SplashScreen()
                } else {
                    MainContent(
                        app,
                        repository,
                        app.folderRepository,
                        playbackManager,
                        libraryViewModel,
                        nowPlayingViewModel,
                        settingsViewModel,
                        onlineMusicViewModel,
                    )
                }
            }
        }
    }
}

@Composable
fun SplashScreen() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "Pear Music",
            style = MaterialTheme.typography.displayMedium,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 48.dp),
            contentAlignment = Alignment.BottomCenter
        ) {
            Text(
                text = "Created By: Rei Asanagi",
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White.copy(alpha = 0.6f)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun MainContent(
    application: Application,
    repository: MusicRepository,
    folderRepository: com.endfield.pearmusic.data.repository.FolderRepository,
    playbackManager: PlaybackManager,
    libraryViewModel: LibraryViewModel,
    nowPlayingViewModel: NowPlayingViewModel,
    settingsViewModel: SettingsViewModel,
    onlineMusicViewModel: OnlineMusicViewModel,
) {
    val backStack = rememberNavBackStack(LibraryDestination.Home)
    val listDetailStrategy = rememberListDetailSceneStrategy<NavKey>()

    val currentSong by nowPlayingViewModel.currentSong
    val isPlaying by nowPlayingViewModel.isPlaying

    val currentDestination = backStack.lastOrNull()
    val isPlayerOpen = currentDestination is LibraryDestination.Player
    val isSearchSelected = currentDestination is LibraryDestination.Search
    val isHomeSelected = currentDestination is LibraryDestination.Home
    val isOnlineSelected = currentDestination is LibraryDestination.OnlineMusic

    Scaffold(
        bottomBar = {
            if (!isPlayerOpen) {
                Column {
                    if (currentSong != null) {
                        MiniPlayer(
                            song = currentSong!!,
                            isPlaying = isPlaying,
                            onTogglePlay = { nowPlayingViewModel.togglePlayPause() },
                            onNext = { nowPlayingViewModel.skipToNext() },
                            onClick = { backStack.add(LibraryDestination.Player) }
                        )
                    }
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
                    ) {
                        NavigationBarItem(
                            selected = isHomeSelected,
                            onClick = {
                                if (!isHomeSelected) {
                                    backStack.clear()
                                    backStack.add(LibraryDestination.Home)
                                }
                            },
                            icon = { Icon(Icons.Rounded.LibraryMusic, contentDescription = null) },
                            label = { Text("Library") }
                        )
                        NavigationBarItem(
                            selected = isOnlineSelected,
                            onClick = {
                                if (!isOnlineSelected) {
                                    backStack.add(LibraryDestination.OnlineMusic)
                                }
                            },
                            icon = { Icon(Icons.Rounded.Cloud, contentDescription = null) },
                            label = { Text("Online") }
                        )
                        NavigationBarItem(
                            selected = isSearchSelected,
                            onClick = {
                                if (!isSearchSelected) {
                                    backStack.add(LibraryDestination.Search)
                                }
                            },
                            icon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                            label = { Text("Search") }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavDisplay(
            modifier = Modifier.padding(innerPadding),
            backStack = backStack,
            onBack = { if (backStack.size > 1) backStack.removeAt(backStack.size - 1) },
            sceneStrategy = listDetailStrategy,
            transitionSpec = {
                when {
                    (targetState == LibraryDestination.Player || targetState == LibraryDestination.Songs) -> {
                        (slideInVertically(
                            initialOffsetY = { it },
                            animationSpec = spring(stiffness = 400f)
                        ) + fadeIn()) togetherWith ExitTransition.KeepUntilTransitionsFinished
                    }
                    (initialState == LibraryDestination.Player || initialState == LibraryDestination.Songs) -> {
                        EnterTransition.None togetherWith (slideOutVertically(
                            targetOffsetY = { it },
                            animationSpec = spring(stiffness = 400f)
                        ) + fadeOut())
                    }
                    else -> {
                        slideInHorizontally(
                            initialOffsetX = { it },
                            animationSpec = tween(400)
                        ) + fadeIn() togetherWith slideOutHorizontally(
                            targetOffsetX = { -it / 3 },
                            animationSpec = tween(400)
                        ) + fadeOut()
                    }
                }
            },
            popTransitionSpec = {
                when (initialState) {
                    LibraryDestination.Player, LibraryDestination.Songs -> {
                        EnterTransition.None togetherWith (slideOutVertically(
                            targetOffsetY = { it },
                            animationSpec = spring(stiffness = 400f)
                        ) + fadeOut())
                    }
                    else -> {
                        (slideInHorizontally(
                            initialOffsetX = { -it / 3 },
                            animationSpec = tween(400)
                        ) + fadeIn()) togetherWith (slideOutHorizontally(
                            targetOffsetX = { it },
                            animationSpec = tween(400)
                        ) + fadeOut())
                    }
                }
            },
            entryProvider = entryProvider {
                entry<LibraryDestination.Home>(
                    metadata = ListDetailSceneStrategy.listPane(
                        detailPlaceholder = {
                            Text("Select a category to view content")
                        }
                    )
                ) {
                    LibraryHome(
                        viewModel = libraryViewModel,
                        onCategoryClick = { category ->
                            when (category) {
                                "Songs" -> backStack.add(LibraryDestination.Songs)
                                "Albums" -> backStack.add(LibraryDestination.Albums)
                                "Artists" -> backStack.add(LibraryDestination.Artists)
                                "Playlists" -> backStack.add(LibraryDestination.Playlists)
                                "Folders" -> backStack.add(LibraryDestination.Folders)
                            }
                        },
                        onAlbumClick = { album ->
                            backStack.add(LibraryDestination.AlbumDetail(albumName = album.albumName))
                        },
                        onSettingsClick = { backStack.add(LibraryDestination.Settings) },
                    )
                }
                entry<LibraryDestination.OnlineMusic>(
                    metadata = ListDetailSceneStrategy.detailPane()
                ) {
                    OnlineMusicScreen(
                        viewModel = onlineMusicViewModel,
                        onSongClick = { song, queue ->
                            nowPlayingViewModel.playSong(song, queue)
                            backStack.add(LibraryDestination.Player)
                        },
                        onCategoryClick = { category ->
                            when (category) {
                                "Songs" -> backStack.add(LibraryDestination.OnlineSongs)
                                "Albums" -> backStack.add(LibraryDestination.OnlineAlbums)
                                "Artists" -> backStack.add(LibraryDestination.OnlineArtists)
                                "Playlists" -> backStack.add(LibraryDestination.OnlinePlaylists)
                            }
                        },
                        onBack = { backStack.removeAt(backStack.size - 1) }
                    )
                }
                entry<LibraryDestination.OnlineSongs>(
                    metadata = ListDetailSceneStrategy.detailPane()
                ) {
                    OnlineSongsScreen(
                        viewModel = onlineMusicViewModel,
                        onBack = { backStack.removeAt(backStack.size - 1) },
                        onSongClick = { song: com.endfield.pearmusic.data.local.Song, queue: List<com.endfield.pearmusic.data.local.Song> ->
                            nowPlayingViewModel.playSong(song, queue)
                            backStack.add(LibraryDestination.Player)
                        }
                    )
                }
                entry<LibraryDestination.OnlineAlbums>(
                    metadata = ListDetailSceneStrategy.detailPane()
                ) {
                    OnlineAlbumsScreen(
                        viewModel = onlineMusicViewModel,
                        onBack = { backStack.removeAt(backStack.size - 1) },
                        onAlbumClick = { album ->
                            backStack.add(LibraryDestination.OnlineAlbumDetail(album))
                        }
                    )
                }
                entry<LibraryDestination.OnlineAlbumDetail>(
                    metadata = ListDetailSceneStrategy.detailPane()
                ) { key ->
                    OnlineAlbumDetailScreen(
                        albumName = key.albumName,
                        viewModel = onlineMusicViewModel,
                        onBack = { backStack.removeAt(backStack.size - 1) },
                        onSongClick = { song: com.endfield.pearmusic.data.local.Song, queue: List<com.endfield.pearmusic.data.local.Song> ->
                            nowPlayingViewModel.playSong(song, queue)
                            backStack.add(LibraryDestination.Player)
                        }
                    )
                }
                entry<LibraryDestination.OnlineArtists>(
                    metadata = ListDetailSceneStrategy.detailPane()
                ) {
                    OnlineArtistsScreen(
                        viewModel = onlineMusicViewModel,
                        onBack = { backStack.removeAt(backStack.size - 1) },
                        onArtistClick = { artist ->
                            backStack.add(LibraryDestination.OnlineArtistDetail(artist))
                        }
                    )
                }
                entry<LibraryDestination.OnlineArtistDetail>(
                    metadata = ListDetailSceneStrategy.detailPane()
                ) { key ->
                    OnlineArtistDetailScreen(
                        artistName = key.artistName,
                        viewModel = onlineMusicViewModel,
                        onBack = { backStack.removeAt(backStack.size - 1) },
                        onSongClick = { song: com.endfield.pearmusic.data.local.Song, queue: List<com.endfield.pearmusic.data.local.Song> ->
                            nowPlayingViewModel.playSong(song, queue)
                            backStack.add(LibraryDestination.Player)
                        }
                    )
                }
                entry<LibraryDestination.OnlinePlaylists>(
                    metadata = ListDetailSceneStrategy.detailPane()
                ) {
                    OnlinePlaylistsScreen(
                        viewModel = onlineMusicViewModel,
                        onBack = { backStack.removeAt(backStack.size - 1) },
                        onPlaylistClick = { playlist ->
                            backStack.add(LibraryDestination.OnlinePlaylistDetail(playlist))
                        }
                    )
                }
                entry<LibraryDestination.OnlinePlaylistDetail>(
                    metadata = ListDetailSceneStrategy.detailPane()
                ) { key ->
                    OnlinePlaylistDetailScreen(
                        playlistName = key.playlistName,
                        viewModel = onlineMusicViewModel,
                        onBack = { backStack.removeAt(backStack.size - 1) },
                        onSongClick = { song: com.endfield.pearmusic.data.local.Song, queue: List<com.endfield.pearmusic.data.local.Song> ->
                            nowPlayingViewModel.playSong(song, queue)
                            backStack.add(LibraryDestination.Player)
                        }
                    )
                }
                entry<LibraryDestination.Songs>(
                    metadata = ListDetailSceneStrategy.detailPane()
                ) {
                    SongsScreen(
                        viewModel = libraryViewModel,
                        onBack = { backStack.removeAt(backStack.size - 1) },
                        onSongClick = { song, queue ->
                            nowPlayingViewModel.playSong(song, queue)
                            backStack.add(LibraryDestination.Player)
                        }
                    )
                }
                entry<LibraryDestination.Albums>(
                    metadata = ListDetailSceneStrategy.detailPane()
                ) {
                    AlbumsScreen(
                        viewModel = libraryViewModel,
                        onBack = { backStack.removeAt(backStack.size - 1) },
                        onAlbumClick = { album ->
                            backStack.add(LibraryDestination.AlbumDetail(albumName = album))
                        }
                    )
                }
                entry<LibraryDestination.AlbumDetail>(
                    metadata = ListDetailSceneStrategy.detailPane()
                ) { key ->
                    AlbumDetailScreen(
                        albumName = key.albumName,
                        viewModel = libraryViewModel,
                        onBack = { backStack.removeAt(backStack.size - 1) },
                        onSongClick = { song, queue ->
                            nowPlayingViewModel.playSong(song, queue)
                            backStack.add(LibraryDestination.Player)
                        }
                    )
                }
                entry<LibraryDestination.Artists>(
                    metadata = ListDetailSceneStrategy.detailPane()
                ) {
                    ArtistsScreen(
                        viewModel = libraryViewModel,
                        onBack = { backStack.removeAt(backStack.size - 1) },
                        onArtistClick = { artistName ->
                            backStack.add(LibraryDestination.ArtistDetail(artistName))
                        }
                    )
                }
                entry<LibraryDestination.ArtistDetail>(
                    metadata = ListDetailSceneStrategy.detailPane()
                ) { key ->
                    ArtistDetailScreen(
                        artistName = key.artistName,
                        viewModel = libraryViewModel,
                        onBack = { backStack.removeAt(backStack.size - 1) },
                        onSongClick = { song, queue ->
                            nowPlayingViewModel.playSong(song, queue)
                            backStack.add(LibraryDestination.Player)
                        }
                    )
                }
                entry<LibraryDestination.Search>(
                    metadata = ListDetailSceneStrategy.detailPane()
                ) {
                    SearchScreen(
                        viewModel = libraryViewModel,
                        onBack = { backStack.removeAt(backStack.size - 1) },
                        onSongClick = { song, queue ->
                            nowPlayingViewModel.playSong(song, queue)
                            backStack.add(LibraryDestination.Player)
                        }
                    )
                }
                entry<LibraryDestination.Folders>(
                    metadata = ListDetailSceneStrategy.detailPane()
                ) {
                    FoldersScreen(
                        viewModel = libraryViewModel,
                        onBack = { backStack.removeAt(backStack.size - 1) },
                        onFolderClick = { /* Navigate to folder detail */ },
                        onManageFolders = { backStack.add(LibraryDestination.FolderManagement) }
                    )
                }
                entry<LibraryDestination.Playlists>(
                    metadata = ListDetailSceneStrategy.detailPane()
                ) {
                    PlaylistsScreen(
                        viewModel = libraryViewModel,
                        onBack = { backStack.removeAt(backStack.size - 1) },
                        onPlaylistClick = { playlist ->
                            backStack.add(LibraryDestination.PlaylistDetail(playlist.id))
                        },
                        onCreatePlaylist = { libraryViewModel.createPlaylist("New Playlist") },
                        onRenamePlaylist = { playlist, newName ->
                            libraryViewModel.updatePlaylist(playlist.copy(name = newName))
                        },
                        onDeletePlaylist = { playlist ->
                            libraryViewModel.deletePlaylist(playlist)
                        }
                    )
                }
                entry<LibraryDestination.PlaylistDetail>(
                    metadata = ListDetailSceneStrategy.detailPane()
                ) { key ->
                    PlaylistDetailScreen(
                        playlistId = key.playlistId,
                        viewModel = libraryViewModel,
                        onBack = { backStack.removeAt(backStack.size - 1) },
                        onSongClick = { song, queue ->
                            nowPlayingViewModel.playSong(song, queue)
                            backStack.add(LibraryDestination.Player)
                        }
                    )
                }
                entry<LibraryDestination.Player>(
                    metadata = ListDetailSceneStrategy.detailPane()
                ) {
                    NowPlayingScreen(
                        viewModel = nowPlayingViewModel,
                        onBack = { backStack.removeAt(backStack.size - 1) },
                        onEditLyrics = { song ->
                            backStack.add(LibraryDestination.LyricsEditor(song.mediaId))
                        },
                        onManualSearchLyrics = {
                            backStack.add(LibraryDestination.LyricsSearch)
                        }
                    )
                }
                entry<LibraryDestination.LyricsSearch>(
                    metadata = ListDetailSceneStrategy.detailPane()
                ) {
                    LyricsSearchScreen(
                        viewModel = nowPlayingViewModel,
                        onBack = { backStack.removeAt(backStack.size - 1) }
                    )
                }
                entry<LibraryDestination.LyricsEditor>(
                    metadata = ListDetailSceneStrategy.detailPane()
                ) { key ->
                    val lyricsEditorViewModel: LyricsEditorViewModel = viewModel {
                        LyricsEditorViewModel(application, repository, playbackManager, (application as PearMusicApplication).lrcLibService, key.songMediaId)
                    }
                    LyricsEditorScreen(
                        viewModel = lyricsEditorViewModel,
                        onBack = { backStack.removeAt(backStack.size - 1) }
                    )
                }
                entry<LibraryDestination.FolderManagement>(
                    metadata = ListDetailSceneStrategy.detailPane()
                ) {
                    val folderViewModel: FolderManagementViewModel = viewModel {
                        FolderManagementViewModel(folderRepository, repository)
                    }
                    FolderManagementScreen(
                        viewModel = folderViewModel,
                        onBack = { backStack.removeAt(backStack.size - 1) }
                    )
                }
                entry<LibraryDestination.Settings>(
                    metadata = ListDetailSceneStrategy.detailPane()
                ) {
                    SettingsScreen(
                        viewModel = settingsViewModel,
                        onBack = { backStack.removeAt(backStack.size - 1) }
                    )
                }
            }
        )
    }
}
