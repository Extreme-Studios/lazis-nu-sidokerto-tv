package lazisnu.sidokerto.navigation

import android.app.Activity
import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import lazisnu.sidokerto.data.local.PlaylistLocalStore
import lazisnu.sidokerto.data.model.PortalPost
import lazisnu.sidokerto.data.model.Song
import lazisnu.sidokerto.data.repository.ApiSongRepository
import lazisnu.sidokerto.data.repository.PortalRepository
import lazisnu.sidokerto.data.repository.PlaylistRepository
import lazisnu.sidokerto.data.repository.SongRegistrationRepository
import android.util.Log
import lazisnu.sidokerto.ui.screens.*
import lazisnu.sidokerto.ui.screens.PortalPosterSubmissionScreen
import lazisnu.sidokerto.ui.components.AdBanner
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

sealed class AppScreen(val route: String, val label: String, val icon: ImageVector) {
    data object Home : AppScreen("home", "Beranda", Icons.Default.Home)
    data object Library : AppScreen("library", "Koleksi", Icons.AutoMirrored.Filled.List)
    data object Live : AppScreen("live", "Live", Icons.Default.PlayArrow)
    data object PortalKita : AppScreen("portal_kita", "Board Info", Icons.Default.Info)
    data object About : AppScreen("about", "Profil", Icons.Default.AccountCircle)
}

@Composable
fun LazisNuSidokertoApp(repository: ApiSongRepository = ApiSongRepository()) {
    val context = LocalContext.current
    val activity = context as? Activity
    val scope = rememberCoroutineScope()
    val playlistRepository = remember { PlaylistRepository(PlaylistLocalStore(context)) }
    val portalRepository = remember { PortalRepository() }

    val tabs = listOf(
        AppScreen.Home,
        AppScreen.Library,
        AppScreen.Live,
        AppScreen.PortalKita,
        AppScreen.About
    )
    var currentTab by remember { mutableStateOf<AppScreen>(AppScreen.Home) }
    val tabHistory = remember { mutableStateListOf<AppScreen>().apply { add(AppScreen.Home) } }
    var selectedArtist by remember { mutableStateOf<String?>(null) }
    var selectedAlbum by remember { mutableStateOf<Pair<String, String>?>(null) }
    var selectedMovie by remember { mutableStateOf<Song?>(null) }
    var playingMovie by remember { mutableStateOf<Song?>(null) }
    var selectedPortalPost by remember { mutableStateOf<PortalPost?>(null) }
    var showPortalSubmission by remember { mutableStateOf(false) }
    var showRegistrationFromAbout by remember { mutableStateOf(false) }
    var uploadFormMode by remember { mutableStateOf(UploadFormMode.Video) }
    var songs by remember { mutableStateOf<List<Song>>(emptyList()) }
    var isLoadingSongs by remember { mutableStateOf(true) }
    var isRefreshingSongs by remember { mutableStateOf(false) }
    var songsError by remember { mutableStateOf<String?>(null) }
    val genres = remember(songs) { repository.getGenres(songs) }
    val artists = remember(songs) { repository.getArtists(songs) }
    val playlists by playlistRepository.playlists.collectAsState(initial = emptyList())
    var portalPosts by remember { mutableStateOf<List<PortalPost>>(emptyList()) }
    var isLoadingPortal by remember { mutableStateOf(true) }
    var portalError by remember { mutableStateOf<String?>(null) }
    val portalCategories = remember { portalRepository.getCategories() }
    val isInitialLoading = isLoadingSongs || isLoadingPortal
    val configuration = LocalConfiguration.current
    val isPlayerLandscapeFullscreen = playingMovie != null && configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    fun navigateTo(screen: AppScreen) {
        selectedArtist = null
        selectedAlbum = null
        selectedMovie = null
        playingMovie = null
        selectedPortalPost = null
        showPortalSubmission = false
        showRegistrationFromAbout = false
        if (currentTab != screen) {
            currentTab = screen
            tabHistory.add(screen)
        }
    }

    fun openSong(song: Song) {
        selectedMovie = song
    }

    fun refreshSongs(showBlocking: Boolean, forceRefresh: Boolean = false) {
        scope.launch {
            if (showBlocking) isLoadingSongs = true else isRefreshingSongs = true
            repository.fetchSongs(null, forceRefresh).onSuccess { apiSongs ->
                songs = apiSongs
                songsError = null
            }.onFailure { err ->
                songsError = err.message ?: "Gagal memuat data."
            }
            isLoadingSongs = false
            isRefreshingSongs = false
        }
    }

    fun refreshPortalPosts(showBlocking: Boolean = true) {
        scope.launch {
            if (showBlocking) isLoadingPortal = true
            portalRepository.fetchActivePosts().onSuccess { posts ->
                portalPosts = posts
                portalError = null
            }.onFailure { error ->
                portalError = error.message ?: "Gagal memuat Board Info."
            }
            isLoadingPortal = false
        }
    }

    LaunchedEffect(Unit) {
        scope.launch {
            try {
                val registrationRepository = SongRegistrationRepository()
                registrationRepository.importYoutubeChannel("https://www.youtube.com/@lazisnu_sidokerto")
                    .onSuccess {
                        Log.d("LazisNuApp", "Background channel scan succeeded, refreshing songs list.")
                        refreshSongs(showBlocking = false)
                    }
                    .onFailure { err ->
                        Log.e("LazisNuApp", "Background channel scan failed: ${err.message}")
                    }
            } catch (e: Exception) {
                Log.e("LazisNuApp", "Error during background channel scan: ${e.message}")
            }
        }
        refreshSongs(showBlocking = true)
        refreshPortalPosts(showBlocking = true)
    }

    LaunchedEffect(currentTab) {
        if (currentTab == AppScreen.Live) {
            refreshSongs(showBlocking = false, forceRefresh = true)
        }
    }

    BackHandler {
        when {
            playingMovie != null -> playingMovie = null
            selectedMovie != null -> selectedMovie = null
            showPortalSubmission -> showPortalSubmission = false
            showRegistrationFromAbout -> showRegistrationFromAbout = false
            selectedPortalPost != null -> selectedPortalPost = null
            selectedAlbum != null -> selectedAlbum = null
            selectedArtist != null -> selectedArtist = null
            tabHistory.size > 1 -> {
                tabHistory.removeAt(tabHistory.lastIndex)
                currentTab = tabHistory.last()
            }
            else -> activity?.moveTaskToBack(true)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            bottomBar = {
                if (!isPlayerLandscapeFullscreen) {
                    Column {
                        AdBanner()
                        NavigationBar(
                            containerColor = MaterialTheme.colorScheme.surface,
                            tonalElevation = 4.dp
                        ) {
                        tabs.forEach { screen ->
                            NavigationBarItem(
                                selected = currentTab == screen,
                                onClick = { navigateTo(screen) },
                                icon = { Icon(screen.icon, contentDescription = screen.label) },
                                label = { Text(screen.label, fontSize = 10.sp, fontWeight = FontWeight.Bold) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = MaterialTheme.colorScheme.primary,
                                    selectedTextColor = MaterialTheme.colorScheme.primary,
                                    unselectedIconColor = MaterialTheme.colorScheme.outline,
                                    unselectedTextColor = MaterialTheme.colorScheme.outline,
                                    indicatorColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                                )
                            )
                        }
                    }
                    }
                }
            }
        ) { innerPadding ->
            val modifier = if (isPlayerLandscapeFullscreen) Modifier else Modifier.padding(innerPadding)

            when {
            playingMovie != null -> {
                val movie = playingMovie!!
                VideoPlayerScreen(
                    title = movie.title,
                    rawUrl = movie.youtubeUrl.ifBlank { movie.movieUrl },
                    year = movie.year,
                    description = movie.synopsis.ifBlank { movie.description },
                    modifier = modifier,
                    onBack = { playingMovie = null }
                )
            }
            selectedMovie != null -> {
                MovieDetailScreen(
                    movie = selectedMovie!!,
                    modifier = modifier,
                    canEditMetadata = false,
                    onWatchFilm = { url ->
                        playingMovie = selectedMovie
                        scope.launch {
                            repository.updateFilmView(selectedMovie!!.id, url)
                            refreshSongs(false)
                        }
                    },
                    onSaveMetadata = { title, year, deskripsi ->
                        Result.failure(IllegalStateException("Fitur admin sudah dihapus."))
                    },
                    onMetadataSaved = { title, year, deskripsi ->
                        selectedMovie = selectedMovie?.copy(
                            title = title,
                            year = year,
                            synopsis = deskripsi,
                            description = deskripsi,
                            releaseYear = year
                        )
                        refreshSongs(false)
                    },
                    onDeleteFilm = {
                        Result.failure(IllegalStateException("Fitur admin sudah dihapus."))
                    },
                    onFilmDeleted = {
                        selectedMovie = null
                        refreshSongs(false)
                    }
                )
            }
            showPortalSubmission -> {
                PortalPosterSubmissionScreen(
                    modifier = modifier,
                    onBack = { showPortalSubmission = false },
                    onSubmitted = { showPortalSubmission = false; refreshPortalPosts(true) }
                )
            }
            showRegistrationFromAbout -> {
                SubmitSongScreen(modifier = modifier, mode = uploadFormMode, onBack = { showRegistrationFromAbout = false }, onSubmitted = { refreshSongs(false) })
            }
            selectedPortalPost != null -> {
                PortalPostDetailScreen(post = selectedPortalPost!!, modifier = modifier, onBack = { selectedPortalPost = null })
            }
            selectedAlbum != null -> {
                AlbumDetailScreen(
                    artistName = selectedAlbum!!.first,
                    albumName = selectedAlbum!!.second,
                    songs = songs,
                    modifier = modifier,
                    onBack = { selectedAlbum = null },
                    onSongClick = { openSong(it) }
                )
            }
            selectedArtist != null -> {
                ArtistDetailScreen(
                    artistName = selectedArtist!!,
                    songs = songs,
                    errorMessage = songsError,
                    modifier = modifier,
                    onBack = { selectedArtist = null },
                    onSongClick = { openSong(it) },
                    onAlbumClick = { selectedAlbum = selectedArtist!! to it }
                )
            }
            else -> when (currentTab) {
                AppScreen.Home -> HomeScreen(
                    songs = songs,
                    artists = artists,
                    errorMessage = songsError,
                    isLoading = isLoadingSongs,
                    isRefreshing = isRefreshingSongs,
                    onRefresh = { refreshSongs(false) },
                    modifier = modifier,
                    onSongClick = { openSong(it) },
                    onArtistClick = { selectedArtist = it }
                )
                AppScreen.Library -> LibraryScreen(
                    songs = songs,
                    genres = genres,
                    playlists = playlists,
                    errorMessage = songsError,
                    isLoading = isLoadingSongs,
                    isRefreshing = isRefreshingSongs,
                    onRefresh = { refreshSongs(false) },
                    modifier = modifier,
                    onSongClick = { openSong(it) },
                    onArtistClick = { selectedArtist = it },
                    onAddSongToPlaylist = { pId, sId -> scope.launch { playlistRepository.addSongToPlaylist(pId, sId) } }
                )
                AppScreen.Live -> LiveStreamListScreen(
                    songs = songs,
                    modifier = modifier,
                    isLoading = isLoadingSongs,
                    errorMessage = songsError,
                    onRefresh = { refreshSongs(false, forceRefresh = true) },
                    onOpenLive = { song -> playingMovie = song },
                    onEditLive = { song ->
                        selectedMovie = song
                    },
                    canEdit = false,
                    adminUsername = ""
                )
                AppScreen.PortalKita -> PortalKitaScreen(
                    posts = portalPosts,
                    categories = portalCategories,
                    isLoading = isLoadingPortal,
                    errorMessage = portalError,
                    modifier = modifier,
                    onRefresh = { refreshPortalPosts(true) },
                    onPostPreview = { selectedPortalPost = it },
                    onOpenPosterSubmission = { showPortalSubmission = true }
                )
                AppScreen.About -> AboutScreen(modifier = modifier)
            }
            }
        }

        if (isInitialLoading) {
            InitialLoadingOverlay()
        }
    }
}

@Composable
private fun InitialLoadingOverlay() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFFF8FCF8), Color(0xFFF3F8F3), Color.White)
                )
            )
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(84.dp)
                    .background(Color.White, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(42.dp),
                    color = MaterialTheme.colorScheme.primary,
                    strokeWidth = 4.dp
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            Text(
                text = "Memuat konten...",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color.Black,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Menyiapkan video, informasi, dan data profil",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.Black.copy(alpha = 0.68f),
                textAlign = TextAlign.Center
            )
        }
    }
}


