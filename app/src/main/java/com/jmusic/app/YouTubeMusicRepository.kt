package com.jmusic.app

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit

/**
 * Searches public YouTube music/video metadata through the official YouTube Data API.
 *
 * This class intentionally returns catalog metadata and YouTube watch URLs only. It does
 * not extract media URLs or bypass YouTube playback, DRM, ads, or subscription controls.
 */
data class OnlineTrack(
    val videoId: String,
    val title: String,
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
        if (apiKey.isBlank()) throw IllegalStateException(
            "YouTube API key is missing. Add YOUTUBE_API_KEY to your local Gradle properties."
        )

        val encodedQuery = URLEncoder.encode(normalizedQuery, StandardCharsets.UTF_8.name())
        val url = "https://www.googleapis.com/youtube/v3/search" +
            "?part=snippet&type=video&videoEmbeddable=true&maxResults=${maxResults.coerceIn(1, 25)}" +
            "&q=$encodedQuery&key=$apiKey"
        val request = Request.Builder().url(url).get().build()

        client.newCall(request).execute().use { response ->
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
    }
}
