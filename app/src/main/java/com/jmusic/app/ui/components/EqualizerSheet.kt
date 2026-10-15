package com.jmusic.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jmusic.app.ui.theme.CardBackground
import com.jmusic.app.ui.theme.GreenAccent
import com.jmusic.app.ui.theme.SurfaceDark
import com.jmusic.app.ui.theme.TextMuted

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EqualizerSheet(
    playbackSpeed: Float,
    pitch: Float,
    onSpeedChange: (Float) -> Unit,
    onPitchChange: (Float) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = SurfaceDark
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
        ) {
            Text(
                text = "Audio Controls & Playback Speed",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Spacer(modifier = Modifier.height(16.dp))

            // Playback speed
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Speed:", style = MaterialTheme.typography.titleMedium, color = Color.White, modifier = Modifier.weight(1f))
                Text(String.format("%.2fx", playbackSpeed), color = GreenAccent, fontWeight = FontWeight.Bold)
            }
            Slider(
                value = playbackSpeed,
                onValueChange = { onSpeedChange(it) },
                valueRange = 0.5f..2.0f,
                steps = 5,
                colors = SliderDefaults.colors(thumbColor = GreenAccent, activeTrackColor = GreenAccent)
            )

            Row(modifier = Modifier.fillMaxWidth()) {
                listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f).forEach { speed ->
                    FilterChip(
                        selected = (playbackSpeed == speed),
                        onClick = { onSpeedChange(speed) },
                        label = { Text("${speed}x") },
                        modifier = Modifier.padding(end = 4.dp),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = GreenAccent,
                            selectedLabelColor = Color.Black
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Pitch
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Pitch:", style = MaterialTheme.typography.titleMedium, color = Color.White, modifier = Modifier.weight(1f))
                Text(String.format("%.2fx", pitch), color = GreenAccent, fontWeight = FontWeight.Bold)
            }
            Slider(
                value = pitch,
                onValueChange = { onPitchChange(it) },
                valueRange = 0.5f..1.5f,
                steps = 3,
                colors = SliderDefaults.colors(thumbColor = GreenAccent, activeTrackColor = GreenAccent)
            )

            Spacer(modifier = Modifier.height(16.dp))

            Row(modifier = Modifier.fillMaxWidth()) {
                TextButton(
                    onClick = {
                        onSpeedChange(1.0f)
                        onPitchChange(1.0f)
                    }
                ) {
                    Text("Reset Normal", color = GreenAccent)
                }
                Spacer(modifier = Modifier.weight(1f))
                TextButton(onClick = onDismiss) {
                    Text("Done", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
