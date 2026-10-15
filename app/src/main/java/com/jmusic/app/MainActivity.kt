package com.jmusic.app

import android.Manifest
import android.content.ContentUris
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Stream
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
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

private val JGreen = Color(0xFF1ED760)
private val JBackground = Color(0xFF101010)
private val JCard = Color(0xFF202020)
private val JMuted = Color(0xFFB3B3B3)

data class LocalSong(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val uri: Uri,
    val artworkUri: Uri?
)

class MainActivity : ComponentActivity() {
    private lateinit var player: ExoPlayer
    private var songs by mutableStateOf<List<LocalSong>>(emptyList())
    private var permissionGranted by mutableStateOf(false)
    private var isPlaying by mutableStateOf(false)
    private var currentTitle by mutableStateOf("Choose a song")
    private var currentArtist by mutableStateOf("Your music, your player")
    private var currentArtwork by mutableStateOf<Uri?>(null)
    private var statusMessage by mutableStateOf("Choose music from your phone or add an authorized stream URL.")
    private var currentIndex by mutableIntStateOf(-1)
    private var durationMs by mutableLongStateOf(0L)
    private var positionMs by mutableLongStateOf(0L)
    private var activeSource by mutableStateOf("phone")

    private val audioPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        permissionGranted = granted
        if (granted) loadLocalSongs() else statusMessage = "Allow audio access to show music stored on your phone."
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        player = ExoPlayer.Builder(this).build()
        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) { isPlaying = playing }
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                currentTitle = mediaItem?.mediaMetadata?.title?.toString() ?: "Unknown track"
                currentArtist = mediaItem?.mediaMetadata?.artist?.toString() ?: "Unknown artist"
                currentArtwork = mediaItem?.mediaMetadata?.artworkUri
                durationMs = player.duration.coerceAtLeast(0L)
                positionMs = player.currentPosition.coerceAtLeast(0L)
            }
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) durationMs = player.duration.coerceAtLeast(0L)
                if (playbackState == Player.STATE_ENDED) isPlaying = false
            }
            override fun onPlayerError(error: PlaybackException) {
                statusMessage = "Can't play this source. Check the URL, network, format, and streaming permission."
            }
        })
        permissionGranted = hasAudioPermission()
        if (permissionGranted) loadLocalSongs()
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(primary = JGreen, background = JBackground, surface = JCard)) {
                Surface(Modifier.fillMaxSize(), color = JBackground) {
                    JMusicHome(
                        songs = songs,
                        permissionGranted = permissionGranted,
                        isPlaying = isPlaying,
                        currentTitle = currentTitle,
                        currentArtist = currentArtist,
                        currentArtwork = currentArtwork,
                        statusMessage = statusMessage,
                        activeSource = activeSource,
                        durationMs = durationMs,
                        positionMs = positionMs,
                        player = player,
                        onRequestPermission = { requestAudioPermission() },
                        onRefresh = { loadLocalSongs() },
                        onPlaySong = { song -> playLocalSong(song) },
                        onPlayOnline = { title, url -> playOnlineStream(title, url) },
                        onTogglePlayback = { if (player.isPlaying) player.pause() else if (player.mediaItemCount > 0) player.play() },
                        onPrevious = { if (player.hasPreviousMediaItem()) player.seekToPreviousMediaItem() else if (currentIndex > 0) playLocalSong(songs[currentIndex - 1]) },
                        onNext = { if (player.hasNextMediaItem()) player.seekToNextMediaItem() else if (currentIndex in 0 until songs.lastIndex) playLocalSong(songs[currentIndex + 1]) },
                        onSeek = { position -> player.seekTo(position) },
                        onClear = { player.stop(); currentTitle = "Choose a song"; currentArtist = "Your music, your player"; currentArtwork = null; durationMs = 0; positionMs = 0; currentIndex = -1 }
                    )
                }
            }
        }
    }

    private fun hasAudioPermission(): Boolean {
        val permission = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE
        return ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestAudioPermission() {
        val permission = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE
        audioPermission.launch(permission)
    }

    private fun loadLocalSongs() {
        if (!hasAudioPermission()) {
            permissionGranted = false
            return
        }
        val list = mutableListOf<LocalSong>()
        val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.ALBUM_ID
        )
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
        val sortOrder = "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC"
        try {
            contentResolver.query(collection, projection, selection, null, sortOrder)?.use { cursor: Cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val albumColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                val albumIdColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idColumn)
                    val albumId = cursor.getLong(albumIdColumn)
                    val artwork = if (albumId > 0) Uri.parse("content://media/external/audio/albumart/$albumId") else null
                    list += LocalSong(
                        id = id,
                        title = cursor.getString(titleColumn) ?: "Unknown track",
                        artist = cursor.getString(artistColumn)?.takeIf { it != "<unknown>" } ?: "Unknown artist",
                        album = cursor.getString(albumColumn) ?: "",
                        durationMs = cursor.getLong(durationColumn),
                        uri = ContentUris.withAppendedId(collection, id),
                        artworkUri = artwork
                    )
                }
            }
            songs = list
            statusMessage = if (list.isEmpty()) "No local music found. Add audio files to your phone or use an authorized stream URL." else "Found ${list.size} songs on your phone."
        } catch (e: Exception) {
            statusMessage = "Couldn't read local music: ${e.message ?: "unknown error"}"
        }
    }

    private fun playLocalSong(song: LocalSong) {
        activeSource = "phone"
        currentIndex = songs.indexOfFirst { it.id == song.id }
        val mediaItems = songs.map { item ->
            MediaItem.Builder()
                .setUri(item.uri)
                .setMediaId(item.id.toString())
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(item.title)
                        .setArtist(item.artist)
                        .setAlbumTitle(item.album)
                        .setArtworkUri(item.artworkUri)
                        .build()
                ).build()
        }
        player.setMediaItems(mediaItems, currentIndex.coerceAtLeast(0), 0L)
        player.prepare()
        player.play()
        currentTitle = song.title
        currentArtist = song.artist
        currentArtwork = song.artworkUri
        statusMessage = "Playing music stored on this phone."
    }

    private fun playOnlineStream(title: String, urlInput: String) {
        val url = urlInput.trim()
        val parsed = runCatching { Uri.parse(url) }.getOrNull()
        if (parsed == null || parsed.scheme != "https" || parsed.host.isNullOrBlank()) {
            statusMessage = "Enter a valid HTTPS audio-stream URL that you own or are authorized to use."
            return
        }
        if (!url.lowercase(Locale.ROOT).let { it.endsWith(".mp3") || it.endsWith(".m4a") || it.endsWith(".aac") || it.endsWith(".wav") || it.endsWith(".ogg") || it.endsWith(".opus") || it.endsWith(".m3u8") || it.contains("?") }) {
            statusMessage = "Use a direct audio URL or a compatible HLS (.m3u8) stream URL."
            return
        }
        activeSource = "online"
        currentIndex = -1
        val safeTitle = title.trim().ifBlank { parsed.lastPathSegment?.substringBeforeLast('.') ?: "Online stream" }
        val item = MediaItem.Builder().setUri(parsed).setMediaMetadata(
            MediaMetadata.Builder().setTitle(safeTitle).setArtist("Online stream").build()
        ).build()
        player.setMediaItem(item)
        player.prepare()
        player.play()
        currentTitle = safeTitle
        currentArtist = "Online stream"
        currentArtwork = null
        statusMessage = "Opening authorized stream inside JMusic…"
    }

    override fun onDestroy() {
        player.release()
        super.onDestroy()
    }
}

@Composable
private fun JMusicHome(
    songs: List<LocalSong>,
    permissionGranted: Boolean,
    isPlaying: Boolean,
    currentTitle: String,
    currentArtist: String,
    currentArtwork: Uri?,
    statusMessage: String,
    activeSource: String,
    durationMs: Long,
    positionMs: Long,
    player: ExoPlayer,
    onRequestPermission: () -> Unit,
    onRefresh: () -> Unit,
    onPlaySong: (LocalSong) -> Unit,
    onPlayOnline: (String, String) -> Unit,
    onTogglePlayback: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onSeek: (Long) -> Unit,
    onClear: () -> Unit
) {
    var tab by remember { mutableStateOf("phone") }
    var streamTitle by remember { mutableStateOf("") }
    var streamUrl by remember { mutableStateOf("") }
    var query by remember { mutableStateOf("") }
    var shownPosition by remember { mutableLongStateOf(0L) }

    LaunchedEffect(player) {
        while (true) {
            shownPosition = player.currentPosition.coerceAtLeast(0L)
            delay(500)
        }
    }

    val filteredSongs = remember(songs, query) {
        songs.filter { query.isBlank() || it.title.contains(query, true) || it.artist.contains(query, true) || it.album.contains(query, true) }
    }

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 18.dp)) {
            Spacer(Modifier.height(20.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(48.dp).clip(RoundedCornerShape(15.dp)).background(JGreen), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.LibraryMusic, null, tint = Color.Black, modifier = Modifier.size(28.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("JMusic", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
                    Text("Your music. Your player.", color = JMuted, style = MaterialTheme.typography.bodyMedium)
                }
                Icon(Icons.Default.QueueMusic, null, tint = JGreen, modifier = Modifier.size(28.dp))
            }
            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = tab == "phone", onClick = { tab = "phone" }, label = { Text("Phone library") }, leadingIcon = { Icon(Icons.Default.LibraryMusic, null) })
                FilterChip(selected = tab == "online", onClick = { tab = "online" }, label = { Text("Online stream") }, leadingIcon = { Icon(Icons.Default.Stream, null) })
            }
            Spacer(Modifier.height(10.dp))
            if (tab == "online") {
                var onlineQuery by remember { mutableStateOf("") }
                var onlineTracks by remember { mutableStateOf<List<OnlineTrack>>(emptyList()) }
                var selectedOnlineTrack by remember { mutableStateOf<OnlineTrack?>(null) }
                var searchingOnline by remember { mutableStateOf(false) }
                var onlineError by remember { mutableStateOf("") }
                val scope = rememberCoroutineScope()
                val repository = remember { YouTubeMusicRepository() }

                Text("Discover music", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("Search YouTube videos and play them using YouTube's embedded player.", color = JMuted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp, bottom = 10.dp))
                OutlinedTextField(
                    value = onlineQuery,
                    onValueChange = { onlineQuery = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Search songs, artists, albums…") },
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Default.Search, null) }
                )
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = {
                        scope.launch {
                            searchingOnline = true
                            onlineError = ""
                            try {
                                onlineTracks = repository.search(onlineQuery)
                                if (onlineTracks.isEmpty()) onlineError = "No results found. Try another search."
                            } catch (error: Exception) {
                                onlineError = error.message ?: "Search failed. Check your connection and API key."
                            } finally {
                                searchingOnline = false
                            }
                        }
                    },
                    enabled = onlineQuery.isNotBlank() && !searchingOnline,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = JGreen, contentColor = Color.Black)
                ) {
                    Icon(Icons.Default.Search, null)
                    Spacer(Modifier.width(8.dp))
                    Text(if (searchingOnline) "Searching…" else "Search YouTube")
                }
                if (onlineError.isNotBlank()) {
                    Text(onlineError, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 8.dp))
                }
                selectedOnlineTrack?.let { track ->
                    Spacer(Modifier.height(10.dp))
                    Text(track.title, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(track.artistOrChannel, color = JMuted, style = MaterialTheme.typography.bodySmall)
                    AndroidView(
                        modifier = Modifier.fillMaxWidth().height(220.dp).padding(top = 8.dp),
                        factory = { context ->
                            WebView(context).apply {
                                settings.javaScriptEnabled = true
                                settings.domStorageEnabled = true
                                webChromeClient = WebChromeClient()
                                webViewClient = WebViewClient()
                                val safeId = track.videoId.filter { it.isLetterOrDigit() || it == '-' || it == '_' }
                                val html = """
                                    <!doctype html><html><head><meta name="viewport" content="width=device-width, initial-scale=1">
                                    <style>html,body{margin:0;padding:0;background:#101010;height:100%;}iframe{border:0;width:100%;height:100%;}</style>
                                    </head><body><iframe src="https://www.youtube.com/embed/$safeId?playsinline=1&rel=0"
                                    title="YouTube player" allow="accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture; web-share"
                                    referrerpolicy="strict-origin-when-cross-origin" allowfullscreen></iframe></body></html>
                                """.trimIndent()
                                loadDataWithBaseURL("https://www.youtube.com", html, "text/html", "UTF-8", null)
                            }
                        },
                        update = { view ->
                            val safeId = track.videoId.filter { it.isLetterOrDigit() || it == '-' || it == '_' }
                            val html = """
                                <!doctype html><html><head><meta name="viewport" content="width=device-width, initial-scale=1">
                                <style>html,body{margin:0;padding:0;background:#101010;height:100%;}iframe{border:0;width:100%;height:100%;}</style>
                                </head><body><iframe src="https://www.youtube.com/embed/$safeId?playsinline=1&rel=0"
                                title="YouTube player" allow="accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture; web-share"
                                referrerpolicy="strict-origin-when-cross-origin" allowfullscreen></iframe></body></html>
                            """.trimIndent()
                            view.loadDataWithBaseURL("https://www.youtube.com", html, "text/html", "UTF-8", null)
                        }
                    )
                    TextButton(onClick = { selectedOnlineTrack = null }) { Text("Close video") }
                }
                LazyColumn(
                    modifier = Modifier.weight(1f, fill = false),
                    contentPadding = PaddingValues(vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(onlineTracks, key = { it.videoId }) { track ->
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(JCard)
                                .clickable { selectedOnlineTrack = track }.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AsyncImage(
                                model = track.thumbnailUrl.ifBlank { null },
                                contentDescription = "Thumbnail for ${track.title}",
                                modifier = Modifier.size(68.dp).clip(RoundedCornerShape(8.dp)),
                                contentScale = ContentScale.Crop
                            )
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(track.title, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Text(track.artistOrChannel, color = JMuted, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            Icon(Icons.Default.PlayArrow, "Play in embedded player", tint = JGreen)
                        }
                    }
                }
                Text("YouTube playback follows YouTube's availability, ads, account, and background-play restrictions. This is not an unrestricted audio stream.", color = JMuted, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(vertical = 6.dp))
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Your songs", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    TextButton(onClick = onRefresh) { Text("Refresh") }
                }
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Filter songs, artists, albums") },
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Default.AudioFile, null) }
                )
                if (!permissionGranted) {
                    Column(Modifier.fillMaxWidth().padding(top = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.LibraryMusic, null, tint = JGreen, modifier = Modifier.size(54.dp))
                        Text("Let JMusic find your music", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text("Audio permission is used to list songs stored on this phone.", color = JMuted)
                        Button(onClick = onRequestPermission, modifier = Modifier.padding(top = 12.dp), colors = ButtonDefaults.buttonColors(containerColor = JGreen, contentColor = Color.Black)) { Text("Allow audio access") }
                    }
                } else if (filteredSongs.isEmpty()) {
                    Column(Modifier.fillMaxWidth().padding(top = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Album, null, tint = JGreen, modifier = Modifier.size(48.dp))
                        Text(if (songs.isEmpty()) "No music found yet" else "No matching songs", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(if (songs.isEmpty()) "Copy supported audio files onto your phone, then refresh." else "Try another search.", color = JMuted)
                    }
                } else {
                    Text("${filteredSongs.size} tracks", color = JMuted, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(vertical = 6.dp))
                    LazyColumn(contentPadding = PaddingValues(bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(filteredSongs, key = { it.id }) { song ->
                            Row(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(JCard).clickable { onPlaySong(song) }.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (song.artworkUri != null) {
                                    AsyncImage(model = song.artworkUri, contentDescription = "Album artwork for ${song.title}", modifier = Modifier.size(54.dp).clip(RoundedCornerShape(8.dp)), contentScale = ContentScale.Crop)
                                } else {
                                    Box(Modifier.size(54.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xFF303030)), contentAlignment = Alignment.Center) { Icon(Icons.Default.Album, null, tint = JGreen) }
                                }
                                Spacer(Modifier.width(11.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(song.title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(song.artist, color = JMuted, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    if (song.album.isNotBlank()) Text(song.album, color = JMuted, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                                Text(formatTime(song.durationMs), color = JMuted, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }
        }
        MiniPlayer(
            title = currentTitle,
            artist = currentArtist,
            artwork = currentArtwork,
            isPlaying = isPlaying,
            durationMs = durationMs,
            positionMs = shownPosition,
            source = activeSource,
            onToggle = onTogglePlayback,
            onPrevious = onPrevious,
            onNext = onNext,
            onSeek = onSeek,
            onClear = onClear
        )
        Text(statusMessage, color = JMuted, style = MaterialTheme.typography.labelSmall, modifier = Modifier.fillMaxWidth().background(Color(0xFF171717)).padding(horizontal = 16.dp, vertical = 8.dp))
    }
}

@Composable
private fun MiniPlayer(
    title: String,
    artist: String,
    artwork: Uri?,
    isPlaying: Boolean,
    durationMs: Long,
    positionMs: Long,
    source: String,
    onToggle: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onSeek: (Long) -> Unit,
    onClear: () -> Unit
) {
    Column(Modifier.fillMaxWidth().background(Color(0xFF242424)).padding(horizontal = 16.dp, vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (artwork != null) AsyncImage(model = artwork, contentDescription = "Current album artwork", modifier = Modifier.size(54.dp).clip(RoundedCornerShape(8.dp)), contentScale = ContentScale.Crop)
            else Box(Modifier.size(54.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xFF303030)), contentAlignment = Alignment.Center) {
                Icon(if (source == "online") Icons.Default.Stream else Icons.Default.Album, null, tint = JGreen)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(artist, color = JMuted, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            IconButton(onClick = onClear) { Icon(Icons.Default.Stop, "Stop playback", tint = JMuted) }
        }
        Slider(
            value = if (durationMs > 0) positionMs.coerceIn(0L, durationMs).toFloat() / durationMs.toFloat() else 0f,
            onValueChange = { fraction -> if (durationMs > 0) onSeek((fraction * durationMs).toLong()) },
            enabled = durationMs > 0,
            modifier = Modifier.fillMaxWidth().height(28.dp),
            colors = SliderDefaults.colors(thumbColor = JGreen, activeTrackColor = JGreen)
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(formatTime(positionMs), color = JMuted, style = MaterialTheme.typography.labelSmall)
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onPrevious) { Icon(Icons.Default.SkipPrevious, "Previous track") }
            IconButton(onClick = onToggle, modifier = Modifier.size(48.dp).clip(CircleShape).background(JGreen)) {
                Icon(if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, if (isPlaying) "Pause" else "Play", tint = Color.Black, modifier = Modifier.size(30.dp))
            }
            IconButton(onClick = onNext) { Icon(Icons.Default.SkipNext, "Next track") }
            Spacer(Modifier.weight(1f))
            Text(formatTime(durationMs), color = JMuted, style = MaterialTheme.typography.labelSmall)
        }
    }
}

private fun formatTime(milliseconds: Long): String {
    val totalSeconds = (milliseconds.coerceAtLeast(0L) / 1000L).toInt()
    return "%d:%02d".format(totalSeconds / 60, totalSeconds % 60)
}
