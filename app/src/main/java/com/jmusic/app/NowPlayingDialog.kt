package com.jmusic.app

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.media3.common.Player
import kotlinx.coroutines.launch
import coil.compose.AsyncImage

private val PlayerGreen = Color(0xFF1ED760)
private val PlayerMuted = Color(0xFFB3B3B3)

@Composable
internal fun NowPlayingDialog(
    title: String,
    artist: String,
    artwork: Uri?,
    isPlaying: Boolean,
    durationMs: Long,
    positionMs: Long,
    shuffleEnabled: Boolean,
    repeatMode: Int,
    onDismiss: () -> Unit,
    onToggle: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onSeek: (Long) -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeat: () -> Unit
) {
    var showLyrics by remember { mutableStateOf(false) }
    var lyricsLoading by remember { mutableStateOf(false) }
    var lyricsError by remember { mutableStateOf<String?>(null) }
    var lyricsResult by remember { mutableStateOf<LyricsResult?>(null) }
    val lyricsScope = rememberCoroutineScope()
    val activeLyricIndex = lyricsResult?.syncedLines?.indexOfLast { it.timeMs <= positionMs } ?: -1

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth().fillMaxHeight(0.96f).padding(10.dp),
            shape = RoundedCornerShape(28.dp),
            color = Color(0xFF121212)
        ) {
            Column(
                modifier = Modifier.fillMaxSize().padding(horizontal = 22.dp, vertical = 18.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("NOW PLAYING", color = PlayerMuted, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close full player", tint = PlayerMuted)
                    }
                }
                Spacer(Modifier.weight(0.5f))
                if (artwork != null) {
                    AsyncImage(
                        model = artwork,
                        contentDescription = "Album artwork",
                        modifier = Modifier.fillMaxWidth().weight(4f).clip(RoundedCornerShape(22.dp)),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(
                        modifier = Modifier.fillMaxWidth().weight(4f).clip(RoundedCornerShape(22.dp)).background(Color(0xFF242424)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Album, contentDescription = null, tint = PlayerGreen, modifier = Modifier.size(100.dp))
                    }
                }
                Spacer(Modifier.height(22.dp))
                Text(
                    title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center
                )
                Text(artist, color = PlayerMuted, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                TextButton(
                    onClick = {
                        showLyrics = !showLyrics
                        if (showLyrics && lyricsResult == null && !lyricsLoading) {
                            lyricsScope.launch {
                                lyricsLoading = true
                                lyricsError = null
                                try {
                                    lyricsResult = LyricsRepository().findLyrics(title, artist)
                                    if (lyricsResult == null) lyricsError = "Lyrics were not found for this track."
                                } catch (error: Exception) {
                                    lyricsError = error.message ?: "Could not load lyrics. Check your internet connection."
                                } finally {
                                    lyricsLoading = false
                                }
                            }
                        }
                    }
                ) { Text(if (showLyrics) "Hide lyrics" else "Show lyrics", color = PlayerGreen) }
                if (showLyrics) {
                    Surface(
                        modifier = Modifier.fillMaxWidth().heightIn(max = 190.dp),
                        color = Color(0xFF1D1D1D),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        when {
                            lyricsLoading -> Box(Modifier.fillMaxWidth().padding(18.dp), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(color = PlayerGreen, modifier = Modifier.size(26.dp))
                            }
                            lyricsError != null -> Text(lyricsError!!, color = PlayerMuted, modifier = Modifier.padding(14.dp), style = MaterialTheme.typography.bodySmall)
                            lyricsResult?.syncedLines?.isNotEmpty() == true -> {
                                val lines = lyricsResult!!.syncedLines
                                LazyColumn(
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    items(lines) { line ->
                                        val lineIndex = lines.indexOf(line)
                                        Text(
                                            line.text,
                                            color = if (lineIndex == activeLyricIndex) PlayerGreen else PlayerMuted,
                                            fontWeight = if (lineIndex == activeLyricIndex) FontWeight.Bold else FontWeight.Normal,
                                            style = if (lineIndex == activeLyricIndex) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium
                                        )
                                    }
                                }
                            }
                            !lyricsResult?.plainLyrics.isNullOrBlank() -> {
                                LazyColumn(Modifier.fillMaxWidth().padding(14.dp)) {
                                    item { Text(lyricsResult!!.plainLyrics, color = PlayerMuted, style = MaterialTheme.typography.bodyMedium) }
                                }
                            }
                            else -> Text("No lyrics available.", color = PlayerMuted, modifier = Modifier.padding(14.dp))
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                Slider(
                    value = if (durationMs > 0L) positionMs.coerceIn(0L, durationMs).toFloat() / durationMs.toFloat() else 0f,
                    onValueChange = { fraction -> if (durationMs > 0L) onSeek((fraction * durationMs).toLong()) },
                    enabled = durationMs > 0L,
                    modifier = Modifier.fillMaxWidth(),
                    colors = SliderDefaults.colors(thumbColor = PlayerGreen, activeTrackColor = PlayerGreen)
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(formatPlayerTime(positionMs), color = PlayerMuted, style = MaterialTheme.typography.labelSmall)
                    Text(formatPlayerTime(durationMs), color = PlayerMuted, style = MaterialTheme.typography.labelSmall)
                }
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onToggleShuffle) {
                        Icon(Icons.Default.Shuffle, contentDescription = "Toggle shuffle", tint = if (shuffleEnabled) PlayerGreen else PlayerMuted)
                    }
                    IconButton(onClick = onPrevious, modifier = Modifier.size(54.dp)) {
                        Icon(Icons.Default.SkipPrevious, contentDescription = "Previous track", modifier = Modifier.size(34.dp))
                    }
                    IconButton(
                        onClick = onToggle,
                        modifier = Modifier.size(68.dp).clip(CircleShape).background(PlayerGreen)
                    ) {
                        Icon(
                            if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isPlaying) "Pause" else "Play",
                            tint = Color.Black,
                            modifier = Modifier.size(38.dp)
                        )
                    }
                    IconButton(onClick = onNext, modifier = Modifier.size(54.dp)) {
                        Icon(Icons.Default.SkipNext, contentDescription = "Next track", modifier = Modifier.size(34.dp))
                    }
                    IconButton(onClick = onCycleRepeat) {
                        Icon(
                            Icons.Default.Repeat,
                            contentDescription = "Cycle repeat mode",
                            tint = if (repeatMode == Player.REPEAT_MODE_OFF) PlayerMuted else PlayerGreen
                        )
                    }
                }
                Text(
                    "Repeat: ${when (repeatMode) { Player.REPEAT_MODE_ALL -> "All"; Player.REPEAT_MODE_ONE -> "One"; else -> "Off" }}",
                    color = if (repeatMode == Player.REPEAT_MODE_OFF) PlayerMuted else PlayerGreen,
                    style = MaterialTheme.typography.labelMedium
                )
                Spacer(Modifier.weight(0.5f))
            }
        }
    }
}

private fun formatPlayerTime(milliseconds: Long): String {
    val seconds = (milliseconds.coerceAtLeast(0L) / 1000L).toInt()
    return "%d:%02d".format(seconds / 60, seconds % 60)
}
