package com.jmusic.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import net.openid.appauth.*
import java.util.UUID
import java.util.concurrent.Executors

private val Green = Color(0xFF1DB954)
private val Dark = Color(0xFF101010)
private val CardDark = Color(0xFF202020)
private val Muted = Color(0xFFB3B3B3)

class MainActivity : ComponentActivity() {
    private lateinit var authService: AuthorizationService
    private lateinit var repo: SpotifyRepository
    private val executor = Executors.newSingleThreadExecutor()

    private var artists by mutableStateOf<List<SpotifyArtist>>(emptyList())
    private var tracks by mutableStateOf<List<SpotifyTrack>>(emptyList())
    private var playlists by mutableStateOf<List<SpotifyPlaylist>>(emptyList())
    private var selectedTrack by mutableStateOf<SpotifyTrack?>(null)
    private var loading by mutableStateOf(false)
    private var message by mutableStateOf<String?>(null)
    private var connected by mutableStateOf(false)

    private val authLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val data = result.data ?: return@registerForActivityResult
        val response = AuthorizationResponse.fromIntent(data)
        val exception = AuthorizationException.fromIntent(data)
        if (response == null) {
            message = exception?.errorDescription ?: "Spotify login cancelled."
            return@registerForActivityResult
        }
        authService.performTokenRequest(response.createTokenExchangeRequest()) { tokenResponse, tokenException ->
            val token = tokenResponse?.accessToken
            runOnUiThread {
                if (token != null) {
                    repo.saveAccessToken(token)
                    connected = true
                    message = "Spotify connected."
                    loadPlaylists()
                } else message = tokenException?.errorDescription ?: "Could not obtain Spotify token."
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        authService = AuthorizationService(this)
        repo = SpotifyRepository(this)
        connected = repo.getAccessToken() != null

        setContent {
            MaterialTheme(colorScheme = darkColorScheme(primary = Green, background = Dark, surface = CardDark)) {
                Surface(Modifier.fillMaxSize(), color = Dark) {
                    val track = selectedTrack
                    if (track != null) NowPlayingScreen(
                        track = track,
                        onBack = { selectedTrack = null },
                        onPlay = { openUrl(track.spotifyUrl) }
                    ) else BrowseScreen(
                        connected = connected,
                        artists = artists,
                        tracks = tracks,
                        playlists = playlists,
                        loading = loading,
                        message = message,
                        onConnect = { connectSpotify() },
                        onSearch = { search(it) },
                        onRefresh = { loadPlaylists() },
                        onTrackClick = { selectedTrack = it },
                        onOpenArtist = { openUrl(it.spotifyUrl) },
                        onOpenPlaylist = { openUrl(it.spotifyUrl) },
                        onClearMessage = { message = null }
                    )
                }
            }
        }
        if (connected) loadPlaylists()
    }

    private fun connectSpotify() {
        if (SpotifyConfig.CLIENT_ID == "YOUR_SPOTIFY_CLIENT_ID") {
            message = "Add your Spotify Client ID in SpotifyConfig.kt first."
            return
        }
        val config = AuthorizationServiceConfiguration(Uri.parse(SpotifyConfig.AUTH_ENDPOINT), Uri.parse(SpotifyConfig.TOKEN_ENDPOINT))
        val request = AuthorizationRequest.Builder(config, SpotifyConfig.CLIENT_ID, ResponseTypeValues.CODE, Uri.parse(SpotifyConfig.REDIRECT_URI))
            .setScope(SpotifyConfig.SCOPES)
            .setCodeVerifier(UUID.randomUUID().toString() + UUID.randomUUID().toString())
            .build()
        authLauncher.launch(authService.getAuthorizationRequestIntent(request))
    }

    private fun search(query: String) {
        if (!connected) {
            message = "Connect Spotify before searching artists and songs."
            return
        }
        loading = true
        message = null
        executor.execute {
            try {
                val result = repo.search(query)
                runOnUiThread {
                    artists = result.artists
                    tracks = result.tracks
                    loading = false
                    if (artists.isEmpty() && tracks.isEmpty()) message = "No artists or songs found. Try another search."
                }
            } catch (e: Exception) {
                runOnUiThread { loading = false; message = e.message ?: "Search failed." }
            }
        }
    }

    private fun loadPlaylists() {
        if (repo.getAccessToken() == null) return
        loading = true
        executor.execute {
            try {
                val loaded = repo.getPlaylists()
                runOnUiThread { playlists = loaded; loading = false }
            } catch (e: Exception) {
                runOnUiThread { loading = false; message = e.message ?: "Playlist sync failed." }
            }
        }
    }

    private fun openUrl(url: String) {
        if (url.isBlank()) return
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }

    override fun onDestroy() {
        authService.dispose()
        executor.shutdown()
        super.onDestroy()
    }
}

@Composable
private fun BrowseScreen(
    connected: Boolean,
    artists: List<SpotifyArtist>,
    tracks: List<SpotifyTrack>,
    playlists: List<SpotifyPlaylist>,
    loading: Boolean,
    message: String?,
    onConnect: () -> Unit,
    onSearch: (String) -> Unit,
    onRefresh: () -> Unit,
    onTrackClick: (SpotifyTrack) -> Unit,
    onOpenArtist: (SpotifyArtist) -> Unit,
    onOpenPlaylist: (SpotifyPlaylist) -> Unit,
    onClearMessage: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    var showPlaylists by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().padding(horizontal = 18.dp)) {
        Spacer(Modifier.height(20.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(48.dp).clip(RoundedCornerShape(16.dp)).background(Green), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.MusicNote, null, tint = Color.Black, modifier = Modifier.size(30.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("JMusic", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
                Text("Find your next favourite", color = Muted, style = MaterialTheme.typography.bodyMedium)
            }
            if (connected) IconButton(onClick = onRefresh) { Icon(Icons.Default.Refresh, "Refresh playlists") }
        }
        Spacer(Modifier.height(20.dp))
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Search artists, songs, albums") },
            singleLine = true,
            leadingIcon = { Icon(Icons.Default.Search, null) },
            trailingIcon = {
                IconButton(onClick = { onSearch(query.trim()) }) {
                    Icon(Icons.Default.Search, "Search Spotify", tint = Green)
                }
            }
        )
        Spacer(Modifier.height(10.dp))
        if (!connected) {
            Button(onClick = onConnect, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Green, contentColor = Color.Black)) {
                Text("Connect Spotify", fontWeight = FontWeight.Bold)
            }
            Text("Connect your account to search the Spotify catalog and sync playlists.", color = Muted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp))
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = !showPlaylists, onClick = { showPlaylists = false }, label = { Text("Discover") })
                FilterChip(selected = showPlaylists, onClick = { showPlaylists = true }, label = { Text("My playlists") })
            }
        }
        if (loading) {
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(Modifier.fillMaxWidth(), color = Green)
        }
        message?.let {
            Text(it, color = if (it.contains("connected", true)) Green else Color(0xFFFF8A80), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 8.dp).clickable { onClearMessage() })
        }
        if (showPlaylists && connected) {
            LazyColumn(contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item { SectionHeading("Your playlists", "Open in Spotify to play") }
                items(playlists, key = { "playlist-${it.id}" }) { playlist ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(CardDark).clickable { onOpenPlaylist(playlist) }.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.MusicNote, null, tint = Green, modifier = Modifier.size(36.dp))
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(playlist.name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("${playlist.trackCount} tracks", color = Muted, style = MaterialTheme.typography.bodySmall)
                        }
                        Icon(Icons.Default.OpenInNew, "Open playlist", tint = Muted)
                    }
                }
            }
        } else {
            LazyColumn(contentPadding = PaddingValues(top = 10.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (artists.isEmpty() && tracks.isEmpty()) {
                    item {
                        Column(Modifier.fillMaxWidth().padding(top = 32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.Search, null, tint = Green, modifier = Modifier.size(52.dp))
                            Spacer(Modifier.height(10.dp))
                            Text("Search artists and songs", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            Text("Artwork, track details, and links to Spotify.", color = Muted)
                        }
                    }
                }
                if (artists.isNotEmpty()) {
                    item { SectionHeading("Artists", "Tap to open artist profile") }
                    items(artists, key = { "artist-${it.id}" }) { artist ->
                        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(CardDark).clickable { onOpenArtist(artist) }.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            if (artist.imageUrl.isNotBlank()) AsyncImage(artist.imageUrl, contentDescription = "Artwork for ${artist.name}", modifier = Modifier.size(62.dp).clip(RoundedCornerShape(50)), contentScale = ContentScale.Fit)
                            else Icon(Icons.Default.MusicNote, null, tint = Green, modifier = Modifier.size(62.dp).padding(12.dp))
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(artist.name, fontWeight = FontWeight.SemiBold)
                                Text("Artist", color = Muted, style = MaterialTheme.typography.bodySmall)
                            }
                            Icon(Icons.Default.OpenInNew, "Open artist", tint = Muted)
                        }
                    }
                }
                if (tracks.isNotEmpty()) {
                    item { SectionHeading("Songs", "${tracks.size} results · tap for Now Playing") }
                    items(tracks, key = { "track-${it.id}" }) { track ->
                        TrackRow(track = track, onClick = { onTrackClick(track) })
                    }
                }
                item {
                    Text("Music metadata and artwork provided by Spotify. Tap a result to open its page in Spotify.", color = Muted, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 12.dp))
                }
            }
        }
    }
}

@Composable
private fun SectionHeading(title: String, subtitle: String) {
    Column(Modifier.padding(top = 10.dp, bottom = 2.dp)) {
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(subtitle, color = Muted, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun TrackRow(track: SpotifyTrack, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(CardDark).clickable(onClick = onClick).padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (track.artworkUrl.isNotBlank()) {
            AsyncImage(track.artworkUrl, contentDescription = "Album artwork for ${track.name}", modifier = Modifier.size(64.dp).clip(RoundedCornerShape(8.dp)), contentScale = ContentScale.Fit)
        } else {
            Box(Modifier.size(64.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xFF303030)), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.MusicNote, null, tint = Green)
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(track.name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(track.artistNames, color = Muted, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(track.albumName, color = Muted.copy(alpha = 0.75f), style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Icon(Icons.Default.PlayArrow, "View track", tint = Green, modifier = Modifier.size(28.dp))
    }
}

@Composable
private fun NowPlayingScreen(track: SpotifyTrack, onBack: () -> Unit, onPlay: () -> Unit) {
    Column(
        Modifier.fillMaxSize().background(
            androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color(0xFF294E3B), Dark, Dark))
        ).padding(horizontal = 22.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") }
            Text("NOW PLAYING", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Icon(Icons.Default.MusicNote, null, tint = Green)
        }
        Spacer(Modifier.height(24.dp))
        if (track.artworkUrl.isNotBlank()) {
            AsyncImage(
                model = track.artworkUrl,
                contentDescription = "Album artwork for ${track.name}",
                modifier = Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(16.dp)),
                contentScale = ContentScale.Fit
            )
        } else {
            Box(Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(16.dp)).background(CardDark), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.MusicNote, null, tint = Green, modifier = Modifier.size(96.dp))
            }
        }
        Spacer(Modifier.height(24.dp))
        Column(Modifier.fillMaxWidth()) {
            Text(track.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(track.artistNames, style = MaterialTheme.typography.titleMedium, color = Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (track.albumName.isNotBlank()) Text(track.albumName, style = MaterialTheme.typography.bodySmall, color = Muted.copy(alpha = 0.8f))
        }
        Spacer(Modifier.height(20.dp))
        Button(
            onClick = onPlay,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(28.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Green, contentColor = Color.Black)
        ) {
            Icon(Icons.Default.PlayArrow, null, modifier = Modifier.size(28.dp))
            Spacer(Modifier.width(8.dp))
            Text("Play in Spotify", fontWeight = FontWeight.Bold)
        }
        Text("Playback is handled by the official Spotify app.", color = Muted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 12.dp, bottom = 8.dp))
        Text("Artwork © Spotify and its licensors · Open the track in Spotify for full details.", color = Muted, style = MaterialTheme.typography.labelSmall)
    }
}
