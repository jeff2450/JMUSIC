package com.jmusic.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Stream
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.jmusic.app.data.LocalMusicRepository
import com.jmusic.app.data.LyricsRepository
import com.jmusic.app.model.LyricsData
import com.jmusic.app.model.MediaTrack
import com.jmusic.app.model.TrackSource
import com.jmusic.app.player.MusicPlayerManager
import com.jmusic.app.ui.components.EqualizerSheet
import com.jmusic.app.ui.components.ExpandedPlayerModal
import com.jmusic.app.ui.components.MiniPlayer
import com.jmusic.app.ui.screens.DirectStreamScreen
import com.jmusic.app.ui.screens.LocalLibraryScreen
import com.jmusic.app.ui.screens.SpotifyScreen
import com.jmusic.app.ui.screens.YouTubeScreen
import com.jmusic.app.ui.theme.BackgroundDark
import com.jmusic.app.ui.theme.CardBackground
import com.jmusic.app.ui.theme.GreenAccent
import com.jmusic.app.ui.theme.JMusicTheme
import com.jmusic.app.ui.theme.SurfaceDark
import com.jmusic.app.ui.theme.TextMuted
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private lateinit var playerManager: MusicPlayerManager
    private lateinit var youtubeRepository: YouTubeRepository
    private lateinit var localRepository: LocalMusicRepository
    private lateinit var spotifyRepository: SpotifyRepository
    private lateinit var lyricsRepository: LyricsRepository

    private var localTracks by mutableStateOf<List<MediaTrack>>(emptyList())
    private var youtubeResults by mutableStateOf<List<YouTubeTrack>>(emptyList())
    private var spotifyResults by mutableStateOf<List<SpotifyTrack>>(emptyList())

    private var permissionGranted by mutableStateOf(false)
    private var isSearchingYouTube by mutableStateOf(false)
    private var isExtractingStream by mutableStateOf(false)
    private var isSearchingSpotify by mutableStateOf(false)

    private var lyricsData by mutableStateOf<LyricsData?>(null)
    private var isLyricsLoading by mutableStateOf(false)

    private var favoriteTrackIds by mutableStateOf<Set<String>>(emptySet())

    private var statusMessage by mutableStateOf("Welcome to JMusic V3")

    private val audioPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        permissionGranted = granted
        if (granted) loadLocalSongs()
        else statusMessage = "Audio permission denied."
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        playerManager = MusicPlayerManager(this)
        youtubeRepository = YouTubeRepository()
        localRepository = LocalMusicRepository(contentResolver)
        spotifyRepository = SpotifyRepository(this)
        lyricsRepository = LyricsRepository()

        permissionGranted = hasAudioPermission()
        if (permissionGranted) loadLocalSongs()

        setContent {
            JMusicTheme {
                MainAppScreen()
            }
        }
    }

    private fun hasAudioPermission(): Boolean {
        val permission = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE
        return ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestAudioPermission() {
        val permission = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE
        audioPermissionLauncher.launch(permission)
    }

    private fun loadLocalSongs() {
        if (!hasAudioPermission()) {
            permissionGranted = false
            return
        }
        val list = localRepository.getLocalTracks()
        localTracks = list
        statusMessage = "Found ${list.size} songs on device"
    }

    private fun searchYouTube(query: String) {
        if (query.isBlank()) return
        lifecycleScope.launch {
            isSearchingYouTube = true
            statusMessage = "Searching YouTube Music…"
            try {
                val results = youtubeRepository.search(query)
                youtubeResults = results
                statusMessage = "Found ${results.size} tracks on YouTube Music"
            } catch (e: Exception) {
                statusMessage = "YouTube error: ${e.message ?: "Failed search"}"
            } finally {
                isSearchingYouTube = false
            }
        }
    }

    private fun playYouTubeTrack(ytTrack: YouTubeTrack) {
        lifecycleScope.launch {
            isExtractingStream = true
            statusMessage = "Extracting stream for ${ytTrack.title}…"
            try {
                val streamUrl = youtubeRepository.getAudioStreamUrl(ytTrack.id)
                val track = MediaTrack(
                    id = ytTrack.id,
                    title = ytTrack.title,
                    artist = ytTrack.artist,
                    album = ytTrack.album,
                    durationMs = ytTrack.durationMs,
                    artworkUrl = ytTrack.artworkUrl,
                    streamUrl = streamUrl,
                    source = TrackSource.YOUTUBE
                )
                playerManager.playTrack(track)
                statusMessage = "Playing ${ytTrack.title}"
                fetchLyricsForTrack(track)
            } catch (e: Exception) {
                statusMessage = "Stream error: ${e.message ?: "Unavailable stream"}"
            } finally {
                isExtractingStream = false
            }
        }
    }

    private fun playSpotifyTrack(spotifyTrack: SpotifyTrack) {
        searchYouTube("${spotifyTrack.name} ${spotifyTrack.artistNames}")
    }

    private fun playDirectStream(title: String, url: String) {
        val safeTitle = title.ifBlank { "Direct Stream" }
        val track = MediaTrack(
            id = url.hashCode().toString(),
            title = safeTitle,
            artist = "Online Stream",
            streamUrl = url,
            source = TrackSource.DIRECT
        )
        playerManager.playTrack(track)
        statusMessage = "Playing $safeTitle"
    }

    private fun fetchLyricsForTrack(track: MediaTrack) {
        lifecycleScope.launch {
            isLyricsLoading = true
            lyricsData = null
            lyricsData = lyricsRepository.fetchLyrics(track.title, track.artist)
            isLyricsLoading = false
        }
    }

    @Composable
    private fun MainAppScreen() {
        var selectedNavTab by remember { mutableIntStateOf(0) } // 0: YT, 1: Phone, 2: Spotify, 3: Direct
        var isPlayerModalOpen by remember { mutableStateOf(false) }
        var isEqualizerSheetOpen by remember { mutableStateOf(false) }

        val currentTrack = playerManager.currentTrack

        LaunchedEffect(currentTrack) {
            if (currentTrack != null) {
                fetchLyricsForTrack(currentTrack)
            }
        }

        Scaffold(
            bottomBar = {
                Column {
                    // Mini Player
                    if (currentTrack != null) {
                        val duration = playerManager.durationMs
                        val position = playerManager.currentPositionMs
                        val fraction = if (duration > 0) position.toFloat() / duration.toFloat() else 0f

                        MiniPlayer(
                            track = currentTrack,
                            isPlaying = playerManager.isPlaying,
                            progressFraction = fraction,
                            onTogglePlayPause = { playerManager.togglePlayPause() },
                            onNext = { playerManager.skipNext() },
                            onClick = { isPlayerModalOpen = true }
                        )
                    }

                    // Bottom Navigation Bar
                    NavigationBar(
                        containerColor = SurfaceDark,
                        contentColor = GreenAccent
                    ) {
                        NavigationBarItem(
                            selected = selectedNavTab == 0,
                            onClick = { selectedNavTab = 0 },
                            icon = { Icon(Icons.Default.MusicNote, null) },
                            label = { Text("YouTube") },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = Color.Black,
                                selectedTextColor = GreenAccent,
                                indicatorColor = GreenAccent,
                                unselectedIconColor = TextMuted,
                                unselectedTextColor = TextMuted
                            )
                        )

                        NavigationBarItem(
                            selected = selectedNavTab == 1,
                            onClick = { selectedNavTab = 1 },
                            icon = { Icon(Icons.Default.LibraryMusic, null) },
                            label = { Text("Phone") },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = Color.Black,
                                selectedTextColor = GreenAccent,
                                indicatorColor = GreenAccent,
                                unselectedIconColor = TextMuted,
                                unselectedTextColor = TextMuted
                            )
                        )

                        NavigationBarItem(
                            selected = selectedNavTab == 2,
                            onClick = { selectedNavTab = 2 },
                            icon = { Icon(Icons.Default.QueueMusic, null) },
                            label = { Text("Spotify") },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = Color.Black,
                                selectedTextColor = GreenAccent,
                                indicatorColor = GreenAccent,
                                unselectedIconColor = TextMuted,
                                unselectedTextColor = TextMuted
                            )
                        )

                        NavigationBarItem(
                            selected = selectedNavTab == 3,
                            onClick = { selectedNavTab = 3 },
                            icon = { Icon(Icons.Default.Stream, null) },
                            label = { Text("Direct") },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = Color.Black,
                                selectedTextColor = GreenAccent,
                                indicatorColor = GreenAccent,
                                unselectedIconColor = TextMuted,
                                unselectedTextColor = TextMuted
                            )
                        )
                    }
                }
            },
            containerColor = BackgroundDark
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                // Header Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(GreenAccent),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.LibraryMusic, null, tint = Color.Black, modifier = Modifier.size(24.dp))
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text("JMusic", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black, color = Color.White)
                        Text(statusMessage, style = MaterialTheme.typography.bodySmall, color = TextMuted, maxLines = 1)
                    }

                    IconButton(onClick = { isEqualizerSheetOpen = true }) {
                        Icon(Icons.Default.Equalizer, "Equalizer Settings", tint = GreenAccent)
                    }
                }

                // Main Screens
                when (selectedNavTab) {
                    0 -> YouTubeScreen(
                        searchResults = youtubeResults,
                        isSearching = isSearchingYouTube,
                        isExtractingStream = isExtractingStream,
                        onSearch = { searchYouTube(it) },
                        onPlayTrack = { playYouTubeTrack(it) }
                    )
                    1 -> LocalLibraryScreen(
                        tracks = localTracks,
                        hasPermission = permissionGranted,
                        onRequestPermission = { requestAudioPermission() },
                        onRefresh = { loadLocalSongs() },
                        onPlayTrack = { track ->
                            val index = localTracks.indexOf(track)
                            playerManager.setQueue(localTracks, index)
                        }
                    )
                    2 -> SpotifyScreen(
                        spotifyTracks = spotifyResults,
                        isSearching = isSearchingSpotify,
                        isConnected = spotifyRepository.getAccessToken() != null,
                        onConnectSpotify = {
                            // Spotify Auth placeholder
                        },
                        onSearchSpotify = { query ->
                            lifecycleScope.launch {
                                isSearchingSpotify = true
                                try {
                                    val res = spotifyRepository.search(query)
                                    spotifyResults = res.tracks
                                } catch (e: Exception) {
                                    statusMessage = "Spotify error: ${e.message}"
                                } finally {
                                    isSearchingSpotify = false
                                }
                            }
                        },
                        onPlaySpotifyTrack = { playSpotifyTrack(it) }
                    )
                    3 -> DirectStreamScreen(
                        onPlayStream = { title, url -> playDirectStream(title, url) }
                    )
                }
            }
        }

        // Expanded Player Modal
        if (isPlayerModalOpen && currentTrack != null) {
            val isFav = favoriteTrackIds.contains(currentTrack.id)
            ExpandedPlayerModal(
                track = currentTrack,
                isPlaying = playerManager.isPlaying,
                positionMs = playerManager.currentPositionMs,
                durationMs = playerManager.durationMs,
                repeatMode = playerManager.repeatMode,
                isShuffleEnabled = playerManager.isShuffleEnabled,
                playbackSpeed = playerManager.playbackSpeed,
                pitch = playerManager.pitch,
                queue = playerManager.queue.value,
                currentIndex = playerManager.currentIndex,
                lyricsData = lyricsData,
                isLyricsLoading = isLyricsLoading,
                isFavorite = isFav,
                onToggleFavorite = {
                    favoriteTrackIds = if (isFav) favoriteTrackIds - currentTrack.id else favoriteTrackIds + currentTrack.id
                },
                onTogglePlayPause = { playerManager.togglePlayPause() },
                onSeekTo = { playerManager.seekTo(it) },
                onSkipPrevious = { playerManager.skipPrevious() },
                onSkipNext = { playerManager.skipNext() },
                onToggleRepeatMode = { playerManager.toggleRepeatMode() },
                onToggleShuffle = { playerManager.toggleShuffle() },
                onOpenEqualizer = { isEqualizerSheetOpen = true },
                onSelectQueueTrack = { index ->
                    playerManager.setQueue(playerManager.queue.value, index)
                },
                onDismiss = { isPlayerModalOpen = false }
            )
        }

        // Equalizer Sheet
        if (isEqualizerSheetOpen) {
            EqualizerSheet(
                playbackSpeed = playerManager.playbackSpeed,
                pitch = playerManager.pitch,
                onSpeedChange = { speed -> playerManager.setSpeedAndPitch(speed, playerManager.pitch) },
                onPitchChange = { pitch -> playerManager.setSpeedAndPitch(playerManager.playbackSpeed, pitch) },
                onDismiss = { isEqualizerSheetOpen = false }
            )
        }
    }

    override fun onDestroy() {
        playerManager.release()
        super.onDestroy()
    }
}
