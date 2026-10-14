package com.jmusic.app

import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.text.Html
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import java.security.MessageDigest

data class YouTubeTrack(
    val videoId: String,
    val title: String,
    val channel: String,
    val thumbnailUrl: String
)

/**
 * Searches music videos with the official YouTube Data API v3.
 *
 * Only videos the owner allows to be embedded are returned (videoEmbeddable=true), so every
 * result can be played in YouTube's official embedded player. This class never touches audio
 * streams; playback is handled by the embedded player in MainActivity.
 */
class YouTubeRepository(private val context: Context) {
    private val client = OkHttpClient()

    @Throws(IOException::class)
    fun search(query: String): List<YouTubeTrack> {
        val key = BuildConfig.YOUTUBE_API_KEY
        if (key.isBlank()) throw IOException("Add YOUTUBE_API_KEY to local.properties, then rebuild the app.")
        if (query.isBlank()) return emptyList()

        val url = Uri.parse("https://www.googleapis.com/youtube/v3/search")
            .buildUpon()
            .appendQueryParameter("part", "snippet")
            .appendQueryParameter("type", "video")
            .appendQueryParameter("videoCategoryId", "10") // Music
            .appendQueryParameter("videoEmbeddable", "true")
            .appendQueryParameter("maxResults", "25")
            .appendQueryParameter("q", query)
            .appendQueryParameter("key", key)
            .build().toString()

        val builder = Request.Builder().url(url)
        // Lets you restrict the API key to this app in Google Cloud (Application restrictions > Android apps).
        builder.header("X-Android-Package", context.packageName)
        signingCertSha1()?.let { builder.header("X-Android-Cert", it) }

        client.newCall(builder.build()).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw IOException(errorMessage(response.code, body))

            val items = JSONObject(body).optJSONArray("items") ?: return emptyList()
            return (0 until items.length()).mapNotNull { index ->
                val item = items.optJSONObject(index) ?: return@mapNotNull null
                val videoId = item.optJSONObject("id")?.optString("videoId").orEmpty()
                if (videoId.isBlank()) return@mapNotNull null
                val snippet = item.optJSONObject("snippet") ?: return@mapNotNull null
                val thumbnails = snippet.optJSONObject("thumbnails")
                val thumbnail = thumbnails?.optJSONObject("high")?.optString("url")
                    ?: thumbnails?.optJSONObject("medium")?.optString("url")
                    ?: thumbnails?.optJSONObject("default")?.optString("url")
                    ?: ""
                YouTubeTrack(
                    videoId = videoId,
                    title = decode(snippet.optString("title", "Unknown title")),
                    channel = decode(snippet.optString("channelTitle", "Unknown channel")),
                    thumbnailUrl = thumbnail
                )
            }
        }
    }

    private fun errorMessage(code: Int, body: String): String {
        val reason = runCatching {
            JSONObject(body).getJSONObject("error").getJSONArray("errors").getJSONObject(0).getString("reason")
        }.getOrNull()
        return when (reason) {
            "quotaExceeded", "dailyLimitExceeded", "rateLimitExceeded" -> "YouTube search limit reached for today. Try again tomorrow."
            "keyInvalid" -> "YouTube rejected the API key. Check YOUTUBE_API_KEY in local.properties."
            "accessNotConfigured", "forbidden" -> "Enable YouTube Data API v3 for this key in Google Cloud, and check its restrictions."
            else -> "YouTube search failed (HTTP $code)."
        }
    }

    // The API returns titles with HTML entities such as &amp; and &#39;.
    private fun decode(text: String): String =
        Html.fromHtml(text, Html.FROM_HTML_MODE_LEGACY).toString()

    @Suppress("DEPRECATION")
    private fun signingCertSha1(): String? = try {
        val bytes = if (Build.VERSION.SDK_INT >= 28) {
            context.packageManager
                .getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
                .signingInfo?.apkContentsSigners?.firstOrNull()?.toByteArray()
        } else {
            context.packageManager
                .getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES)
                .signatures?.firstOrNull()?.toByteArray()
        }
        bytes?.let { MessageDigest.getInstance("SHA-1").digest(it).joinToString("") { b -> "%02X".format(b) } }
    } catch (e: Exception) {
        null
    }
}
