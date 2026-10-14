package com.jmusic.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import net.openid.appauth.*
import java.util.UUID
import java.util.concurrent.Executors

private val Green = Color(0xFF1DB954)
private val Dark = Color(0xFF101010)
private val CardDark = Color(0xFF202020)

class MainActivity : ComponentActivity() {
    private lateinit var authService: AuthorizationService
    private lateinit var repo: SpotifyRepository
    private val executor = Executors.newSingleThreadExecutor()
    private var playlists by mutableStateOf<List<SpotifyPlaylist>>(emptyList())
    private var loading by mutableStateOf(false)
    private var message by mutableStateOf<String?>(null)

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
                    loadPlaylists()
                } else message = tokenException?.errorDescription ?: "Could not obtain Spotify token."
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        authService = AuthorizationService(this)
        repo = SpotifyRepository(this)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(primary = Green, background = Dark, surface = CardDark)) {
                Surface(Modifier.fillMaxSize(), color = Dark) {
                    Column(Modifier.fillMaxSize().padding(20.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.MusicNote, null, tint = Green, modifier = Modifier.size(36.dp))
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text("JMusic", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                                Text("Your music, one place", color = Color.LightGray)
                            }
                        }
                        Spacer(Modifier.height(24.dp))
                        var query by remember { mutableStateOf("") }
                        OutlinedTextField(
                            value = query, onValueChange = { query = it }, modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text("Search artists, songs, albums") }, singleLine = true,
                            leadingIcon = { Icon(Icons.Default.Search, null) },
                            trailingIcon = { IconButton(onClick = { openUrl("https://open.spotify.com/search/${Uri.encode(query)}") }) { Icon(Icons.Default.OpenInNew, "Search in Spotify") } }
                        )
                        Spacer(Modifier.height(18.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Your playlists", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                            if (repo.getAccessToken() != null) IconButton(onClick = { loadPlaylists() }) { Icon(Icons.Default.Refresh, "Refresh") }
                        }
                        if (repo.getAccessToken() == null) {
                            Button(onClick = { connectSpotify() }, modifier = Modifier.fillMaxWidth()) { Text("Connect Spotify") }
                            Spacer(Modifier.height(8.dp))
                            Text("Sync playlist details and open them in Spotify for playback.", color = Color.LightGray)
                        } else if (loading) {
                            LinearProgressIndicator(Modifier.fillMaxWidth())
                            Text("Syncing playlists…", color = Color.LightGray)
                        } else if (playlists.isEmpty()) {
                            Text("No playlists loaded yet. Check API access, then refresh.", color = Color.LightGray)
                        } else {
                            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                items(playlists, key = { it.id }) { playlist ->
                                    Card(Modifier.fillMaxWidth().clickable { openUrl(playlist.spotifyUrl) }) {
                                        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.MusicNote, null, tint = Green, modifier = Modifier.size(34.dp))
                                            Spacer(Modifier.width(12.dp))
                                            Column(Modifier.weight(1f)) {
                                                Text(playlist.name, fontWeight = FontWeight.SemiBold)
                                                Text("${playlist.trackCount} tracks", color = Color.LightGray)
                                            }
                                            Icon(Icons.Default.OpenInNew, "Open in Spotify")
                                        }
                                    }
                                }
                            }
                        }
                        message?.let { Spacer(Modifier.height(10.dp)); Text(it, color = Color(0xFFFF8A80)) }
                    }
                }
            }
        }
        if (repo.getAccessToken() != null) loadPlaylists()
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

    private fun loadPlaylists() {
        loading = true
        executor.execute {
            try {
                val loaded = repo.getPlaylists()
                runOnUiThread { playlists = loaded; loading = false; message = null }
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
