package com.jmusic.app

import android.Manifest
import android.content.ContentUris
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
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
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
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
    private lateinit var player: MediaController
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
    private var shuffleEnabled by mutableStateOf(false)
    private var repeatMode by mutableIntStateOf(Player.REPEAT_MODE_OFF)

    private val audioPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        permissionGranted = granted
        if (granted) loadLocalSongs() else statusMessage = "Allow audio access to show music stored on your phone."
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val sessionToken = SessionToken(this, ComponentName(this, PlaybackService::class.java))
        val controllerFuture = MediaController.Builder(this, sessionToken).buildAsync()
        controllerFuture.addListener({
            player = controllerFuture.get()
            shuffleEnabled = player.shuffleModeEnabled
            repeatMode = player.repeatMode
            player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) { isPlaying = playing }
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                currentTitle = mediaItem?.mediaMetadata?.title?.toString() ?: "Unknown track"
                currentArtist = mediaItem?.mediaMetadata?.artist?.toString() ?: "Unknown artist"
                currentArtwork = mediaItem?.mediaMetadata?.artworkUri
                currentIndex = songs.indexOfFirst { it.id.toString() == mediaItem?.mediaId }
                durationMs = player.duration.coerceAtLeast(0L)
                positionMs = player.currentPosition.coerceAtLeast(0L)
            }
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) durationMs = player.duration.coerceAtLeast(0L)
                if (playbackState == Player.STATE_ENDED) isPlaying = false
            }
            override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
                shuffleEnabled = shuffleModeEnabled
            }
            override fun onRepeatModeChanged(repeatModeValue: Int) {
                repeatMode = repeatModeValue
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
                        shuffleEnabled = shuffleEnabled,
                        repeatMode = repeatMode,
                        durationMs = durationMs,
                        positionMs = positionMs,
                        player = player,
                        onRequestPermission = { requestAudioPermission() },
                        onRefresh = { loadLocalSongs() },
                        onPlaySong = { song -> playLocalSong(song) },
                        onPlayOnline = { title, url -> playOnlineStream(title, url) },
                        onOpenYouTube = { url -> startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) },
                        onTogglePlayback = { if (player.isPlaying) player.pause() else if (player.mediaItemCount > 0) player.play() },
                        onPrevious = { if (player.hasPreviousMediaItem()) player.seekToPreviousMediaItem() else if (currentIndex > 0) playLocalSong(songs[currentIndex - 1]) },
                        onNext = { if (player.hasNextMediaItem()) player.seekToNextMediaItem() else if (currentIndex in 0 until songs.lastIndex) playLocalSong(songs[currentIndex + 1]) },
                        onSeek = { position -> player.seekTo(position) },
                        onToggleShuffle = { shuffleEnabled = !shuffleEnabled; player.shuffleModeEnabled = shuffleEnabled },
                        onCycleRepeat = { repeatMode = when (repeatMode) { Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL; Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE; else -> Player.REPEAT_MODE_OFF }; player.repeatMode = repeatMode },
                        onClear = { player.stop(); player.clearMediaItems(); currentTitle = "Choose a song"; currentArtist = "Your music, your player"; currentArtwork = null; durationMs = 0; positionMs = 0; currentIndex = -1; activeSource = "phone"; isPlaying = false }
                    )
                }
            }
        }
        }, ContextCompat.getMainExecutor(this))
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
        if (::player.isInitialized) player.release()
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
    shuffleEnabled: Boolean,
    repeatMode: Int,
    durationMs: Long,
    positionMs: Long,
    player: Player,
    onRequestPermission: () -> Unit,
    onRefresh: () -> Unit,
    onPlaySong: (LocalSong) -> Unit,
    onPlayOnline: (String, String) -> Unit,
    onOpenYouTube: (String) -> Unit,
    onTogglePlayback: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onSeek: (Long) -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeat: () -> Unit,
    onClear: () -> Unit
) {
    var tab by remember { mutableStateOf("phone") }
    var youtubeQuery by remember { mutableStateOf("") }
    var onlineTracks by remember { mutableStateOf<List<OnlineTrack>>(emptyList()) }
    var searchingYouTube by remember { mutableStateOf(false) }
    var youtubeError by remember { mutableStateOf<String?>(null) }
    val youtubeRepository = remember { YouTubeMusicRepository() }
    val scope = rememberCoroutineScope()
    var streamTitle by remember { mutableStateOf("") }
    var streamUrl by remember { mutableStateOf("") }
    var query by remember { mutableStateOf("") }
    var shownPosition by remember { mutableLongStateOf(0L) }
    var showQueue by remember { mutableStateOf(false) }

    LaunchedEffect(player) {
        while (true) {
            shownPosition = player.currentPosition.coerceAtLeast(0L)
            delay(500)
        }
    }

    if (showQueue) {
        AlertDialog(
            onDismissRequest = { showQueue = false },
            title = { Text("Playback queue (${player.mediaItemCount})") },
            text = {
                if (player.mediaItemCount == 0) {
                    Text("Your queue is empty. Play a song from your phone library to build a queue.", color = JMuted)
                } else {
                    LazyColumn(modifier = Modifier.heightIn(max = 420.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        items((0 until player.mediaItemCount).toList(), key = { it }) { index ->
                            val item = player.getMediaItemAt(index)
                            val selected = index == player.currentMediaItemIndex
                            Row(
                                Modifier.fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (selected) Color(0xFF263A2D) else JCard)
                                    .clickable {
                                        player.seekTo(index, 0L)
                                        player.play()
                                        showQueue = false
                                    }
                                    .padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("${index + 1}", color = if (selected) JGreen else JMuted, modifier = Modifier.width(28.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(item.mediaMetadata.title?.toString() ?: "Unknown track", fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(item.mediaMetadata.artist?.toString() ?: "Unknown artist", color = JMuted, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                                if (selected) Icon(Icons.Default.PlayArrow, "Currently selected", tint = JGreen)
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showQueue = false }) { Text("Done") } },
            containerColor = JCard
        )
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
                IconButton(onClick = { showQueue = true }) {
                    Icon(Icons.Default.QueueMusic, "Open playback queue", tint = JGreen, modifier = Modifier.size(28.dp))
                }
            }
            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = tab == "phone", onClick = { tab = "phone" }, label = { Text("Phone library") }, leadingIcon = { Icon(Icons.Default.LibraryMusic, null) })
                FilterChip(selected = tab == "youtube", onClick = { tab = "youtube" }, label = { Text("YouTube search") }, leadingIcon = { Icon(Icons.Default.Stream, null) })
                FilterChip(selected = tab == "online", onClick = { tab = "online" }, label = { Text("Audio URL") }, leadingIcon = { Icon(Icons.Default.AudioFile, null) })
            }
            Spacer(Modifier.height(10.dp))
            if (tab == "youtube") {
                Text("Discover music on YouTube", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("Search uses your YouTube Data API key. Tap a result to open the official YouTube player; JMusic does not extract audio from YouTube.", color = JMuted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp, bottom = 12.dp))
                OutlinedTextField(value = youtubeQuery, onValueChange = { youtubeQuery = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Song or artist") }, placeholder = { Text("Search music") }, singleLine = true)
                Spacer(Modifier.height(8.dp))
                Button(onClick = {
                    scope.launch {
                        searchingYouTube = true
                        youtubeError = null
                        try { onlineTracks = youtubeRepository.search(youtubeQuery) }
                        catch (error: Exception) { youtubeError = error.message ?: "YouTube search failed."; onlineTracks = emptyList() }
                        finally { searchingYouTube = false }
                    }
                }, enabled = youtubeQuery.isNotBlank() && !searchingYouTube, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = JGreen, contentColor = Color.Black)) {
                    Text(if (searchingYouTube) "Searching…" else "Search YouTube", fontWeight = FontWeight.Bold)
                }
                youtubeError?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp)) }
                if (onlineTracks.isNotEmpty()) {
                    LazyColumn(contentPadding = PaddingValues(vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(onlineTracks, key = { it.videoId }) { track ->
                            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(JCard).clickable { onOpenYouTube(track.watchUrl) }.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                if (track.thumbnailUrl.isNotBlank()) AsyncImage(model = track.thumbnailUrl, contentDescription = "Thumbnail for ${track.title}", modifier = Modifier.size(58.dp).clip(RoundedCornerShape(8.dp)), contentScale = ContentScale.Crop)
                                else Box(Modifier.size(58.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xFF303030)), contentAlignment = Alignment.Center) { Icon(Icons.Default.Stream, null, tint = JGreen) }
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(track.title, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    Text(track.artistOrChannel, color = JMuted, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text("Open in YouTube", color = JGreen, style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }
                } else if (youtubeError == null && !searchingYouTube) {
                    Text("Search for a song or artist to discover tracks.", color = JMuted, modifier = Modifier.padding(top = 12.dp))
                }
            } else if (tab == "online") {
                Text("Play a stream you own or are licensed to use", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("Paste a direct HTTPS audio URL or compatible HLS stream. Spotify track links are not audio stream URLs.", color = JMuted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp, bottom = 12.dp))
                OutlinedTextField(value = streamTitle, onValueChange = { streamTitle = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Track or station name (optional)") }, singleLine = true)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = streamUrl, onValueChange = { streamUrl = it }, modifier = Modifier.fillMaxWidth(), label = { Text("HTTPS audio stream URL") }, placeholder = { Text("https://example.com/audio.mp3") }, singleLine = true)
                Spacer(Modifier.height(12.dp))
                Button(onClick = { onPlayOnline(streamTitle, streamUrl) }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = JGreen, contentColor = Color.Black)) {
                    Icon(Icons.Default.PlayArrow, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Play inside JMusic", fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(12.dp))
                Text("Supported formats depend on the server and device. MP3/AAC/MP4 audio and compatible HLS streams are common choices.", color = JMuted, style = MaterialTheme.typography.bodySmall)
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
            shuffleEnabled = shuffleEnabled,
            repeatMode = repeatMode,
            onToggle = onTogglePlayback,
            onPrevious = onPrevious,
            onNext = onNext,
            onSeek = onSeek,
            onToggleShuffle = onToggleShuffle,
            onCycleRepeat = onCycleRepeat,
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
    shuffleEnabled: Boolean,
    repeatMode: Int,
    onToggle: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onSeek: (Long) -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeat: () -> Unit,
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
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onToggleShuffle) { Text(if (shuffleEnabled) "Shuffle: On" else "Shuffle: Off", color = if (shuffleEnabled) JGreen else JMuted) }
            TextButton(onClick = onCycleRepeat) { Text("Repeat: ${when (repeatMode) { Player.REPEAT_MODE_ALL -> "All"; Player.REPEAT_MODE_ONE -> "One"; else -> "Off" }}", color = if (repeatMode == Player.REPEAT_MODE_OFF) JMuted else JGreen) }
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
