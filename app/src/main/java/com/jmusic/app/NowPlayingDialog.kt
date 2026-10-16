package com.jmusic.app

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
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
                Spacer(Modifier.height(18.dp))
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
