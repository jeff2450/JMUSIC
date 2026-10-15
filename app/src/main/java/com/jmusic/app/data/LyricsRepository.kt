package com.jmusic.app.data

import com.jmusic.app.model.LyricsData
import com.jmusic.app.model.LyricsLine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

class LyricsRepository {
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    suspend fun fetchLyrics(title: String, artist: String): LyricsData? = withContext(Dispatchers.IO) {
        if (title.isBlank()) return@withContext null

        val cleanTitle = title.replace(Regex("(?i)\\(.*?(official|lyric|video|audio|remaster).*?\\)"), "")
            .replace(Regex("(?i)\\[.*?\\]"), "").trim()
        val cleanArtist = artist.replace(" - Topic", "").trim()

        // 1. Try exact match /api/get
        try {
            val encodedTitle = URLEncoder.encode(cleanTitle, "UTF-8")
            val encodedArtist = URLEncoder.encode(cleanArtist, "UTF-8")
            val url = "https://lrclib.net/api/get?track_name=$encodedTitle&artist_name=$encodedArtist"

            val request = Request.Builder().url(url).header("User-Agent", "JMusic/1.0").build()
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string().orEmpty()
                    val lyrics = parseLyricsJson(body, cleanTitle, cleanArtist)
                    if (lyrics != null) return@withContext lyrics
                }
            }
        } catch (_: Exception) {}

        // 2. Try search /api/search
        try {
            val query = URLEncoder.encode("$cleanTitle $cleanArtist", "UTF-8")
            val searchUrl = "https://lrclib.net/api/search?q=$query"

            val request = Request.Builder().url(searchUrl).header("User-Agent", "JMusic/1.0").build()
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string().orEmpty()
                    val array = JSONArray(body)
                    if (array.length() > 0) {
                        val firstMatch = array.optJSONObject(0)
                        if (firstMatch != null) {
                            val lyrics = parseLyricsJsonObject(firstMatch, cleanTitle, cleanArtist)
                            if (lyrics != null) return@withContext lyrics
                        }
                    }
                }
            }
        } catch (_: Exception) {}

        return@withContext null
    }

    private fun parseLyricsJson(jsonStr: String, title: String, artist: String): LyricsData? {
        return try {
            val obj = JSONObject(jsonStr)
            parseLyricsJsonObject(obj, title, artist)
        } catch (e: Exception) {
            null
        }
    }

    private fun parseLyricsJsonObject(obj: JSONObject, defaultTitle: String, defaultArtist: String): LyricsData? {
        val synced = obj.optString("syncedLyrics").takeIf { it.isNotBlank() }
        val plain = obj.optString("plainLyrics").takeIf { it.isNotBlank() }
        val trackTitle = obj.optString("trackName", defaultTitle)
        val artistName = obj.optString("artistName", defaultArtist)

        if (synced != null) {
            val lines = parseLrcLines(synced)
            if (lines.isNotEmpty()) {
                return LyricsData(
                    title = trackTitle,
                    artist = artistName,
                    isSynced = true,
                    plainLyrics = plain,
                    syncedLines = lines
                )
            }
        }

        if (plain != null) {
            return LyricsData(
                title = trackTitle,
                artist = artistName,
                isSynced = false,
                plainLyrics = plain
            )
        }

        return null
    }

    private fun parseLrcLines(lrcText: String): List<LyricsLine> {
        val lines = mutableListOf<LyricsLine>()
        val lrcRegex = Regex("\\[(\\d{2}):(\\d{2})\\.(\\d{2,3})\\](.*)")

        lrcText.lines().forEach { line ->
            val match = lrcRegex.find(line.trim())
            if (match != null) {
                val (minStr, secStr, msStr, text) = match.destructured
                val min = minStr.toLongOrNull() ?: 0L
                val sec = secStr.toLongOrNull() ?: 0L
                val msRaw = msStr.toLongOrNull() ?: 0L
                val ms = if (msStr.length == 2) msRaw * 10 else msRaw
                val totalMs = (min * 60 + sec) * 1000 + ms

                lines.add(LyricsLine(totalMs, text.trim()))
            }
        }
        return lines.sortedBy { it.timestampMs }
    }
}
