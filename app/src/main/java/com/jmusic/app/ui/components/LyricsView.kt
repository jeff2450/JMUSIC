package com.jmusic.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jmusic.app.model.LyricsData
import com.jmusic.app.ui.theme.GreenAccent
import com.jmusic.app.ui.theme.TextMuted

@Composable
fun LyricsView(
    lyricsData: LyricsData?,
    isLoading: Boolean,
    currentPositionMs: Long,
    onSeekTo: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        contentAlignment = Alignment.Center
    ) {
        if (isLoading) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(color = GreenAccent)
                Spacer(modifier = Modifier.height(12.dp))
                Text("Searching lyrics on LRCLIB…", color = TextMuted, style = MaterialTheme.typography.bodyMedium)
            }
            return@Box
        }

        if (lyricsData == null) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "No lyrics found for this track",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = TextMuted
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Synced lyrics are fetched online when available.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center
                )
            }
            return@Box
        }

        if (lyricsData.isSynced && lyricsData.syncedLines.isNotEmpty()) {
            val listState = rememberLazyListState()

            val activeIndex = lyricsData.syncedLines.indexOfLast { it.timestampMs <= currentPositionMs }.coerceAtLeast(0)

            LaunchedEffect(activeIndex) {
                if (activeIndex >= 0 && activeIndex < lyricsData.syncedLines.size) {
                    listState.animateScrollToItem(index = activeIndex)
                }
            }

            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(vertical = 120.dp),
                horizontalAlignment = Alignment.Start
            ) {
                itemsIndexed(lyricsData.syncedLines) { index, line ->
                    val isActive = index == activeIndex
                    val color by animateColorAsState(
                        targetValue = if (isActive) GreenAccent else Color.White.copy(alpha = 0.4f),
                        animationSpec = tween(300),
                        label = "lyric_color"
                    )

                    Text(
                        text = line.text.ifBlank { "♪" },
                        color = color,
                        fontSize = if (isActive) 22.sp else 18.sp,
                        fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                        lineHeight = 28.sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSeekTo(line.timestampMs) }
                            .padding(vertical = 8.dp)
                    )
                }
            }
        } else if (!lyricsData.plainLyrics.isNullOrBlank()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 24.dp)
            ) {
                Text(
                    text = lyricsData.plainLyrics,
                    color = Color.White.copy(alpha = 0.9f),
                    style = MaterialTheme.typography.bodyLarge,
                    lineHeight = 26.sp
                )
            }
        }
    }
}
