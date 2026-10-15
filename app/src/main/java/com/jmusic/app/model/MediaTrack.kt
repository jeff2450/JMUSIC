package com.jmusic.app.model

import android.net.Uri

enum class TrackSource {
    LOCAL,
    YOUTUBE,
    SPOTIFY,
    DIRECT
}

data class MediaTrack(
    val id: String,
    val title: String,
    val artist: String,
    val album: String = "",
    val durationMs: Long = 0L,
    val artworkUrl: String? = null,
    val streamUrl: String? = null,
    val localUri: Uri? = null,
    val source: TrackSource = TrackSource.LOCAL,
    val isFavorite: Boolean = false
)

data class LyricsLine(
    val timestampMs: Long,
    val text: String
)

data class LyricsData(
    val title: String,
    val artist: String,
    val isSynced: Boolean = false,
    val plainLyrics: String? = null,
    val syncedLines: List<LyricsLine> = emptyList()
)

enum class RepeatMode {
    OFF,
    REPEAT_ONE,
    REPEAT_ALL
}
