package com.jmusic.app

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.Duration
import java.util.concurrent.TimeUnit

/**
 * Searches public YouTube metadata through the official YouTube Data API.
 *
 * This class returns catalog metadata and official watch URLs only. It does not
 * extract media URLs or bypass YouTube playback, DRM, ads, or subscription controls.
 */
data class OnlineTrack(
    val videoId: String,
    val title: String,
    /** Channel name with the duration label appended when YouTube provides it. */
    val artistOrChannel: String,
    val thumbnailUrl: String,
    val watchUrl: String = "https://www.youtube.com/watch?v=$videoId"
)

class YouTubeMusicRepository(
    private val apiKey: String = BuildConfig.YOUTUBE_API_KEY,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .callTimeout(20, TimeUnit.SECONDS)
        .build()
) {
    suspend fun search(query: String, maxResults: Int = 20): List<OnlineTrack> = withContext(Dispatchers.IO) {
        val normalizedQuery = query.trim()
        if (normalizedQuery.isEmpty()) return@withContext emptyList()
        if (normalizedQuery.length > 100) throw IllegalArgumentException("Search is limited to 100 characters.")
        if (apiKey.isBlank()) throw IllegalStateException(
            "YouTube API key is missing. Add YOUTUBE_API_KEY to your local Gradle properties."
        )

        val encodedQuery = URLEncoder.encode(normalizedQuery, StandardCharsets.UTF_8.name())
        val url = "https://www.googleapis.com/youtube/v3/search" +
            "?part=snippet&type=video&videoEmbeddable=true&maxResults=${maxResults.coerceIn(1, 25)}" +
            "&q=$encodedQuery&key=$apiKey"
        val request = Request.Builder().url(url).get().build()

        val searchResults = client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                val message = runCatching {
                    JSONObject(body).optJSONObject("error")?.optString("message")
                }.getOrNull().orEmpty()
                throw IOException("YouTube search failed (${response.code}): ${message.ifBlank { "Check API key, quota, and network." }}")
            }

            val root = JSONObject(body)
            val items = root.optJSONArray("items") ?: return@withContext emptyList()
            buildList {
                for (index in 0 until items.length()) {
                    val item = items.optJSONObject(index) ?: continue
                    val id = item.optJSONObject("id")?.optString("videoId").orEmpty()
                    val snippet = item.optJSONObject("snippet") ?: continue
                    val title = snippet.optString("title").orEmpty()
                    if (id.isBlank() || title.isBlank()) continue
                    val thumbnails = snippet.optJSONObject("thumbnails")
                    val thumbnail = thumbnails?.optJSONObject("high")?.optString("url")
                        ?: thumbnails?.optJSONObject("medium")?.optString("url")
                        ?: thumbnails?.optJSONObject("default")?.optString("url")
                        ?: ""
                    add(
                        OnlineTrack(
                            videoId = id,
                            title = title,
                            artistOrChannel = snippet.optString("channelTitle", "YouTube"),
                            thumbnailUrl = thumbnail
                        )
                    )
                }
            }
        }

        if (searchResults.isEmpty()) return@withContext emptyList()

        // The search.list response doesn't include duration; videos.list supplies it.
        // If the optional detail request fails, still show the search results.
        val durationById = runCatching { fetchDurations(searchResults.map { it.videoId }) }
            .getOrDefault(emptyMap())

        searchResults.map { track ->
            val duration = durationById[track.videoId]
            if (duration.isNullOrBlank()) track
            else track.copy(artistOrChannel = "${track.artistOrChannel} · $duration")
        }
    }

    private fun fetchDurations(videoIds: List<String>): Map<String, String> {
        if (videoIds.isEmpty()) return emptyMap()
        val ids = videoIds.distinct().joinToString(",")
        val url = "https://www.googleapis.com/youtube/v3/videos" +
            "?part=contentDetails&id=$ids&key=$apiKey"
        val request = Request.Builder().url(url).get().build()

        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw IOException("YouTube duration lookup failed (HTTP ${response.code}).")
            val items = JSONObject(body).optJSONArray("items") ?: return emptyMap()
            buildMap {
                for (index in 0 until items.length()) {
                    val item = items.optJSONObject(index) ?: continue
                    val id = item.optString("id")
                    val isoDuration = item.optJSONObject("contentDetails")?.optString("duration").orEmpty()
                    val formatted = formatDuration(isoDuration)
                    if (id.isNotBlank() && formatted.isNotBlank()) put(id, formatted)
                }
            }
        }
    }

    private fun formatDuration(isoDuration: String): String = runCatching {
        val seconds = Duration.parse(isoDuration).seconds
        if (seconds < 0) return ""
        val hours = seconds / 3600
        val minutes = (seconds % 3600) / 60
        val remainingSeconds = seconds % 60
        if (hours > 0) "$hours:${minutes.toString().padStart(2, '0')}:${remainingSeconds.toString().padStart(2, '0')}"
        else "$minutes:${remainingSeconds.toString().padStart(2, '0')}"
    }.getOrDefault("")
}
