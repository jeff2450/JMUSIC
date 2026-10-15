package com.jmusic.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stream
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jmusic.app.ui.theme.CardBackground
import com.jmusic.app.ui.theme.GreenAccent
import com.jmusic.app.ui.theme.TextMuted

@Composable
fun DirectStreamScreen(
    onPlayStream: (title: String, url: String) -> Unit,
    modifier: Modifier = Modifier
) {
    var title by remember { mutableStateOf("") }
    var streamUrl by remember { mutableStateOf("") }

    val presetStreams = listOf(
        "Lofi Hip Hop Stream" to "https://stream.zeno.fm/f3wvbbqmdg8uv",
        "Classic Radio Stream" to "https://stream.zeno.fm/034x7812z7zuv"
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "Direct Stream URL",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
        Text(
            text = "Play direct HTTPS audio files (MP3, AAC, FLAC) or HLS streams",
            style = MaterialTheme.typography.bodySmall,
            color = TextMuted
        )

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = title,
            onValueChange = { title = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Stream Title (Optional)") },
            singleLine = true
        )

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = streamUrl,
            onValueChange = { streamUrl = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("HTTPS Audio Stream URL") },
            placeholder = { Text("https://example.com/stream.mp3") },
            singleLine = true
        )

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = { onPlayStream(title, streamUrl) },
            modifier = Modifier.fillMaxWidth(),
            enabled = streamUrl.isNotBlank(),
            colors = ButtonDefaults.buttonColors(containerColor = GreenAccent, contentColor = Color.Black)
        ) {
            Icon(Icons.Default.PlayArrow, null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Play Stream in JMusic", fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Sample Streams",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )

        Spacer(modifier = Modifier.height(8.dp))

        presetStreams.forEach { (name, url) ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(CardBackground)
                    .clickable {
                        title = name
                        streamUrl = url
                        onPlayStream(name, url)
                    }
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Stream, null, tint = GreenAccent)
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(name, fontWeight = FontWeight.Bold, color = Color.White)
                    Text(url, style = MaterialTheme.typography.labelSmall, color = TextMuted)
                }
                Icon(Icons.Default.PlayArrow, "Play", tint = GreenAccent)
            }
        }
    }
}
