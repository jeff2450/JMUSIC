package com.jmusic.app

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

data class LyricLine(val timeMs: Long, val text: String)
data class LyricsResult(val plainLyrics: String, val syncedLines: List<LyricLine>, val source: String = "LRCLIB")

class LyricsRepository(
    private val client: OkHttpClient = OkHttpClient.Builder().callTimeout(12, TimeUnit.SECONDS).build()
) {
    suspend fun findLyrics(title: String, artist: String): LyricsResult? = withContext(Dispatchers.IO) {
        val normalizedTitle = title.trim()
        if (normalizedTitle.isBlank() || normalizedTitle.equals("Choose a song", true)) return@withContext null

        val url = okhttp3.HttpUrl.Builder()
            .scheme("https").host("lrclib.net")
            .addPathSegment("api").addPathSegment("get")
            .addQueryParameter("track_name", normalizedTitle)
            .addQueryParameter("artist_name", artist.trim().ifBlank { "Unknown artist" })
            .build()
        val request = Request.Builder().url(url)
            .header("User-Agent", "JMUSIC Android Music Player").get().build()

        client.newCall(request).execute().use { response ->
            if (response.code == 404) return@withContext null
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw IOException("Lyrics lookup failed with HTTP " + response.code)
            val json = JSONObject(body)
            val plain = json.optString("plainLyrics").trim()
            val synced = parseSyncedLyrics(json.optString("syncedLyrics"))
            if (plain.isBlank() && synced.isEmpty()) null else LyricsResult(plain, synced)
        }
    }

    private fun parseSyncedLyrics(raw: String): List<LyricLine> {
        val timestamp = Regex("""\[(\d{1,2}):(\d{2})(?:\.(\d{1,3}))?\]""")
        return raw.lineSequence().flatMap { line ->
            val matches = timestamp.findAll(line).toList()
            if (matches.isEmpty()) emptySequence()
            else {
                val text = line.replace(timestamp, "").trim()
                matches.asSequence().map { match ->
                    val minutes = match.groupValues[1].toLongOrNull() ?: 0L
                    val seconds = match.groupValues[2].toLongOrNull() ?: 0L
                    val fractionRaw = match.groupValues[3]
                    val fractionMs = when (fractionRaw.length) {
                        1 -> (fractionRaw.toLongOrNull() ?: 0L) * 100L
                        2 -> (fractionRaw.toLongOrNull() ?: 0L) * 10L
                        3 -> fractionRaw.toLongOrNull() ?: 0L
                        else -> 0L
                    }
                    LyricLine((minutes * 60L + seconds) * 1000L + fractionMs, text)
                }
            }
        }.filter { it.text.isNotBlank() }.sortedBy { it.timeMs }.toList()
    }
}
