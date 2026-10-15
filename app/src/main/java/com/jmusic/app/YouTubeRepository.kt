package com.jmusic.app

import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

data class YouTubeTrack(
    val id: String,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val artworkUrl: String
)

class YouTubeRepository {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    suspend fun search(query: String, apiKey: String = "AIzaSyDgI-uBJOWvWgRkXxGusKQfbofo4hAK7W8"): List<YouTubeTrack> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()

        if (apiKey.isNotBlank()) {
            try {
                val apiResults = searchWithDataApi(query, apiKey.trim())
                if (apiResults.isNotEmpty()) {
                    return@withContext apiResults
                }
            } catch (_: Exception) {
                // Fallback to InnerTube search if API key fails
            }
        }

        val requestBody = JSONObject().apply {
            put("context", JSONObject().apply {
                put("client", JSONObject().apply {
                    put("clientName", "ANDROID")
                    put("clientVersion", "19.09.37")
                    put("androidSdkVersion", 34)
                    put("hl", "en")
                    put("gl", "US")
                })
            })
            put("query", query)
        }.toString().toRequestBody(jsonMediaType)

        val request = Request.Builder()
            .url("https://www.youtube.com/youtubei/v1/search")
            .header("User-Agent", "com.google.android.youtube/19.09.37 (Linux; U; Android 14; en_US)")
            .post(requestBody)
            .build()

        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw IOException("YouTube search failed (HTTP ${response.code}).")
            }
            return@withContext parseSearchResults(body)
        }
    }

    private fun searchWithDataApi(query: String, apiKey: String): List<YouTubeTrack> {
        val encodedQuery = Uri.encode(query)
        val url = "https://www.googleapis.com/youtube/v3/search?part=snippet&maxResults=25&type=video&q=$encodedQuery&key=$apiKey"

        val request = Request.Builder()
            .url(url)
            .get()
            .build()

        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw IOException("YouTube API v3 Error (HTTP ${response.code})")
            }
            return parseDataApiSearchResults(body)
        }
    }

    private fun parseDataApiSearchResults(jsonString: String): List<YouTubeTrack> {
        val tracks = mutableListOf<YouTubeTrack>()
        try {
            val root = JSONObject(jsonString)
            val items = root.optJSONArray("items") ?: return emptyList()

            for (i in 0 until items.length()) {
                val item = items.optJSONObject(i) ?: continue
                val idObj = item.optJSONObject("id") ?: continue
                val videoId = idObj.optString("videoId")
                if (videoId.isBlank()) continue

                val snippet = item.optJSONObject("snippet") ?: continue
                val title = snippet.optString("title")
                val artist = snippet.optString("channelTitle", "YouTube")

                val thumbnails = snippet.optJSONObject("thumbnails")
                val highThumb = thumbnails?.optJSONObject("high")?.optString("url")
                    ?: thumbnails?.optJSONObject("medium")?.optString("url")
                    ?: thumbnails?.optJSONObject("default")?.optString("url")
                    ?: "https://img.youtube.com/vi/$videoId/hqdefault.jpg"

                val cleanTitle = unescapeHtml(title)
                val cleanArtist = unescapeHtml(artist)

                tracks.add(
                    YouTubeTrack(
                        id = videoId,
                        title = cleanTitle,
                        artist = cleanArtist,
                        album = "YouTube Music",
                        durationMs = 0L,
                        artworkUrl = highThumb
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return tracks
    }

    private fun unescapeHtml(str: String): String {
        return str
            .replace("&quot;", "\"")
            .replace("&amp;", "&")
            .replace("&#39;", "'")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
    }

    suspend fun getAudioStreamUrl(videoId: String, apiKey: String = "AIzaSyDgI-uBJOWvWgRkXxGusKQfbofo4hAK7W8"): String = withContext(Dispatchers.IO) {
        val errors = mutableListOf<String>()

        // 1. Try IOS Client
        try {
            val url = fetchStreamIOS(videoId, apiKey)
            if (url.isNotBlank()) return@withContext url
        } catch (e: Exception) {
            errors.add("IOS: ${e.message}")
        }

        // 2. Try ANDROID_VR Client
        try {
            val url = fetchStreamAndroidVR(videoId, apiKey)
            if (url.isNotBlank()) return@withContext url
        } catch (e: Exception) {
            errors.add("ANDROID_VR: ${e.message}")
        }

        // 3. Try WEB_REMIX Client
        try {
            val url = fetchStreamWebRemix(videoId, apiKey)
            if (url.isNotBlank()) return@withContext url
        } catch (e: Exception) {
            errors.add("WEB_REMIX: ${e.message}")
        }

        // 4. Try ANDROID Client
        try {
            val url = fetchStreamAndroid(videoId, apiKey)
            if (url.isNotBlank()) return@withContext url
        } catch (e: Exception) {
            errors.add("ANDROID: ${e.message}")
        }

        throw IOException("Failed to fetch stream details (${errors.joinToString("; ")})")
    }

    private fun fetchStreamIOS(videoId: String, apiKey: String): String {
        val clientJson = JSONObject().apply {
            put("clientName", "IOS")
            put("clientVersion", "19.29.1")
            put("deviceModel", "iPhone16,2")
            put("osName", "iPhone")
            put("osVersion", "17.5.1.21F90")
            put("hl", "en")
            put("gl", "US")
        }
        val userAgent = "com.google.ios.youtube/19.29.1 (iPhone16,2; U; CPU iOS 17_5_1 like Mac OS X; en_US)"
        return sendPlayerRequest(videoId, clientJson, userAgent, apiKey)
    }

    private fun fetchStreamAndroidVR(videoId: String, apiKey: String): String {
        val clientJson = JSONObject().apply {
            put("clientName", "ANDROID_VR")
            put("clientVersion", "1.51.12")
            put("deviceModel", "Oculus Quest 3")
            put("osName", "Android")
            put("osVersion", "12")
            put("androidSdkVersion", 32)
            put("hl", "en")
            put("gl", "US")
        }
        val userAgent = "com.google.android.apps.youtube.vr.oculus/1.51.12 (Linux; U; Android 12; en_US)"
        return sendPlayerRequest(videoId, clientJson, userAgent, apiKey)
    }

    private fun fetchStreamWebRemix(videoId: String, apiKey: String): String {
        val clientJson = JSONObject().apply {
            put("clientName", "WEB_REMIX")
            put("clientVersion", "1.20241028.01.00")
            put("hl", "en")
            put("gl", "US")
        }
        val userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/129.0.0.0 Safari/537.36"
        return sendPlayerRequest(videoId, clientJson, userAgent, apiKey)
    }

    private fun fetchStreamAndroid(videoId: String, apiKey: String): String {
        val clientJson = JSONObject().apply {
            put("clientName", "ANDROID")
            put("clientVersion", "19.09.37")
            put("androidSdkVersion", 34)
            put("hl", "en")
            put("gl", "US")
        }
        val userAgent = "com.google.android.youtube/19.09.37 (Linux; U; Android 14; en_US)"
        return sendPlayerRequest(videoId, clientJson, userAgent, apiKey)
    }

    private fun sendPlayerRequest(videoId: String, clientJson: JSONObject, userAgent: String, apiKey: String): String {
        val requestBody = JSONObject().apply {
            put("context", JSONObject().apply {
                put("client", clientJson)
            })
            put("videoId", videoId)
            put("contentCheckOk", true)
            put("racyCheckOk", true)
        }.toString().toRequestBody(jsonMediaType)

        val baseUrl = if (apiKey.isNotBlank()) {
            "https://www.youtube.com/youtubei/v1/player?key=$apiKey"
        } else {
            "https://www.youtube.com/youtubei/v1/player"
        }

        val request = Request.Builder()
            .url(baseUrl)
            .header("User-Agent", userAgent)
            .post(requestBody)
            .build()

        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                val clientName = clientJson.optString("clientName")
                throw IOException("$clientName HTTP ${response.code}")
            }
            return extractBestAudioUrl(body)
        }
    }

    private fun parseSearchResults(jsonString: String): List<YouTubeTrack> {
        val tracks = mutableListOf<YouTubeTrack>()
        try {
            val root = JSONObject(jsonString)
            val videoObjects = mutableListOf<JSONObject>()
            collectVideoRenderers(root, videoObjects)

            val seenIds = mutableSetOf<String>()
            for (obj in videoObjects) {
                val track = parseVideoRenderer(obj)
                if (track != null && seenIds.add(track.id)) {
                    tracks.add(track)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return tracks
    }

    private fun collectVideoRenderers(node: Any, out: MutableList<JSONObject>) {
        when (node) {
            is JSONObject -> {
                val keys = node.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    if (key == "videoRenderer" || key == "compactVideoRenderer" || key == "musicResponsiveListItemRenderer" || key == "gridVideoRenderer" || key == "playlistVideoRenderer") {
                        node.optJSONObject(key)?.let { out.add(it) }
                    } else {
                        node.opt(key)?.let { collectVideoRenderers(it, out) }
                    }
                }
            }
            is JSONArray -> {
                for (i in 0 until node.length()) {
                    node.opt(i)?.let { collectVideoRenderers(it, out) }
                }
            }
        }
    }

    private fun parseVideoRenderer(renderer: JSONObject): YouTubeTrack? {
        val videoId = renderer.optString("videoId").takeIf { it.isNotBlank() }
            ?: renderer.optJSONObject("navigationEndpoint")
                ?.optJSONObject("watchEndpoint")
                ?.optString("videoId")
                ?.takeIf { it.isNotBlank() }
            ?: return null

        // Title
        var title = ""
        val titleObj = renderer.optJSONObject("title")
        if (titleObj != null) {
            val titleRuns = titleObj.optJSONArray("runs")
            if (titleRuns != null && titleRuns.length() > 0) {
                val sb = StringBuilder()
                for (i in 0 until titleRuns.length()) {
                    sb.append(titleRuns.optJSONObject(i)?.optString("text").orEmpty())
                }
                title = sb.toString()
            } else {
                title = titleObj.optString("simpleText").orEmpty()
            }
        }
        if (title.isBlank()) {
            val flexColumns = renderer.optJSONArray("flexColumns")
            if (flexColumns != null && flexColumns.length() > 0) {
                val firstCol = flexColumns.optJSONObject(0)
                    ?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")
                    ?.optJSONObject("text")
                    ?.optJSONArray("runs")
                if (firstCol != null && firstCol.length() > 0) {
                    title = firstCol.optJSONObject(0)?.optString("text").orEmpty()
                }
            }
        }
        if (title.isBlank()) return null

        // Artist / Channel
        var artist = "YouTube"
        val ownerRuns = renderer.optJSONObject("ownerText")?.optJSONArray("runs")
            ?: renderer.optJSONObject("longBylineText")?.optJSONArray("runs")
            ?: renderer.optJSONObject("shortBylineText")?.optJSONArray("runs")
            ?: renderer.optJSONObject("subtitle")?.optJSONArray("runs")
        if (ownerRuns != null && ownerRuns.length() > 0) {
            artist = ownerRuns.optJSONObject(0)?.optString("text") ?: "YouTube"
        } else {
            val flexColumns = renderer.optJSONArray("flexColumns")
            if (flexColumns != null && flexColumns.length() > 1) {
                val secondCol = flexColumns.optJSONObject(1)
                    ?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")
                    ?.optJSONObject("text")
                    ?.optJSONArray("runs")
                if (secondCol != null && secondCol.length() > 0) {
                    artist = secondCol.optJSONObject(0)?.optString("text") ?: "YouTube"
                }
            }
        }

        // Artwork
        val thumbnails = renderer.optJSONObject("thumbnail")?.optJSONArray("thumbnails")
            ?: renderer.optJSONObject("thumbnailRenderer")?.optJSONObject("playlistVideoThumbnailRenderer")?.optJSONObject("thumbnail")?.optJSONArray("thumbnails")
        val artworkUrl = if (thumbnails != null && thumbnails.length() > 0) {
            thumbnails.optJSONObject(thumbnails.length() - 1)?.optString("url").orEmpty()
        } else {
            "https://img.youtube.com/vi/$videoId/hqdefault.jpg"
        }

        // Duration
        val lengthText = renderer.optJSONObject("lengthText")?.optString("simpleText")
            ?: renderer.optJSONObject("lengthText")?.optJSONArray("runs")?.optJSONObject(0)?.optString("text")
        val durationMs = parseDurationToMs(lengthText)

        return YouTubeTrack(
            id = videoId,
            title = title,
            artist = artist,
            album = "YouTube Music",
            durationMs = durationMs,
            artworkUrl = artworkUrl
        )
    }

    private fun extractBestAudioUrl(jsonString: String): String {
        val root = JSONObject(jsonString)
        val playabilityStatus = root.optJSONObject("playabilityStatus")
        val status = playabilityStatus?.optString("status")
        if (status != null && status != "OK") {
            val reason = playabilityStatus.optString("reason", "Video is unavailable or age-restricted.")
            throw IOException("Cannot play track: $reason")
        }

        val streamingData = root.optJSONObject("streamingData")
            ?: throw IOException("No streaming data available for this track.")

        val adaptiveFormats = streamingData.optJSONArray("adaptiveFormats")
            ?: JSONArray()

        val audioCandidates = mutableListOf<AudioFormatCandidate>()

        for (i in 0 until adaptiveFormats.length()) {
            val format = adaptiveFormats.optJSONObject(i) ?: continue
            val mimeType = format.optString("mimeType").orEmpty()
            if (!mimeType.startsWith("audio/")) continue

            var url = format.optString("url").orEmpty()
            var isCipher = false
            if (url.isBlank()) {
                val cipher = format.optString("signatureCipher").ifBlank { format.optString("cipher") }
                if (cipher.isNotBlank()) {
                    url = parseCipherUrl(cipher)
                    isCipher = true
                }
            }

            if (url.isNotBlank()) {
                val bitrate = format.optInt("bitrate", 0)
                audioCandidates.add(AudioFormatCandidate(url, bitrate, mimeType, isCipher))
            }
        }

        val directCandidates = audioCandidates.filter { !it.isCipher }
        if (directCandidates.isNotEmpty()) {
            return directCandidates.maxByOrNull { it.bitrate }!!.url
        }

        if (audioCandidates.isNotEmpty()) {
            return audioCandidates.maxByOrNull { it.bitrate }!!.url
        }

        val formats = streamingData.optJSONArray("formats") ?: JSONArray()
        for (i in 0 until formats.length()) {
            val format = formats.optJSONObject(i) ?: continue
            val url = format.optString("url").orEmpty()
            if (url.isNotBlank()) {
                return url
            }
        }

        throw IOException("No playable audio stream found.")
    }

    private fun parseCipherUrl(cipher: String): String {
        val params = cipher.split("&")
        var url = ""
        for (param in params) {
            val pair = param.split("=")
            if (pair.size == 2 && pair[0] == "url") {
                url = Uri.decode(pair[1])
                break
            }
        }
        return url
    }

    private fun parseDurationToMs(durationStr: String?): Long {
        if (durationStr.isNullOrBlank()) return 0L
        val parts = durationStr.split(":")
        return try {
            when (parts.size) {
                3 -> (parts[0].toLong() * 3600 + parts[1].toLong() * 60 + parts[2].toLong()) * 1000
                2 -> (parts[0].toLong() * 60 + parts[1].toLong()) * 1000
                1 -> parts[0].toLong() * 1000
                else -> 0L
            }
        } catch (_: Exception) {
            0L
        }
    }

    private data class AudioFormatCandidate(
        val url: String,
        val bitrate: Int,
        val mimeType: String,
        val isCipher: Boolean
    )
}
