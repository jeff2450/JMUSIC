package com.jmusic.app

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * Searches Internet Archive for audio recordings whose metadata declares
 * a Creative Commons or public-domain license. Playback uses the archive's
 * original audio file URL; no third-party audio extraction is performed.
 */
data class ArchiveTrack(
    val identifier: String,
    val title: String,
    val creator: String,
    val streamUrl: String,
    val artworkUrl: String?
)

class InternetArchiveRepository {
    private val client = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(18, TimeUnit.SECONDS)
        .build()

    suspend fun search(searchTerm: String): List<ArchiveTrack> = withContext(Dispatchers.IO) {
        val term = searchTerm.trim()
        require(term.isNotBlank()) { "Enter a song title or artist to search." }
        require(term.length <= 100) { "Search is limited to 100 characters." }

        val safeTerm = term.replace("\\", " ").replace("\"", " ")
        val query = "mediatype:audio AND (title:\"$safeTerm\" OR creator:\"$safeTerm\")"
        val encodedQuery = URLEncoder.encode(query, "UTF-8")
        val searchUrl = "https://archive.org/advancedsearch.php?q=$encodedQuery&fl[]=identifier,title,creator&rows=18&page=1&output=json"
        val root = JSONObject(getText(searchUrl))
        val docs = root.optJSONObject("response")?.optJSONArray("docs")
            ?: throw IllegalStateException("Internet Archive returned an unexpected search response.")

        val results = mutableListOf<ArchiveTrack>()
        for (i in 0 until docs.length()) {
            val doc = docs.optJSONObject(i) ?: continue
            val identifier = doc.optString("identifier").trim()
            if (identifier.isBlank()) continue
            try {
                val metadataRoot = JSONObject(getText("https://archive.org/metadata/$identifier"))
                val metadata = metadataRoot.optJSONObject("metadata") ?: continue
                val license = metadata.optString("licenseurl").lowercase()
                val rights = metadata.optString("rights").lowercase()
                val hasReusableLicense = license.contains("creativecommons.org") ||
                    license.contains("publicdomain.org") ||
                    license.contains("creativecommons.org/publicdomain") ||
                    rights.contains("public domain")
                if (!hasReusableLicense) continue

                val files = metadataRoot.optJSONArray("files") ?: continue
                var audioName: String? = null
                for (j in 0 until files.length()) {
                    val file = files.optJSONObject(j) ?: continue
                    val name = file.optString("name")
                    val format = file.optString("format").lowercase()
                    val lowerName = name.lowercase()
                    if ((lowerName.endsWith(".mp3") || lowerName.endsWith(".ogg") ||
                         lowerName.endsWith(".m4a") || lowerName.endsWith(".flac")) &&
                        !lowerName.contains("_spectrogram") &&
                        !lowerName.contains("_thumb") &&
                        (format.contains("mp3") || format.contains("ogg") ||
                         format.contains("mpeg-4") || format.contains("flac") ||
                         format.contains("audio"))) {
                        audioName = name
                        break
                    }
                }
                val audio = audioName ?: continue
                val artwork = findArtworkFile(files)
                val encodedIdentifier = URLEncoder.encode(identifier, "UTF-8").replace("+", "%20")
                val encodedFile = audio.split("/").joinToString("/") {
                    URLEncoder.encode(it, "UTF-8").replace("+", "%20")
                }
                results += ArchiveTrack(
                    identifier = identifier,
                    title = doc.optString("title").ifBlank { metadata.optString("title").ifBlank { identifier } },
                    creator = doc.optString("creator").ifBlank {
                        metadata.optString("creator").ifBlank { "Internet Archive contributor" }
                    },
                    streamUrl = "https://archive.org/download/$encodedIdentifier/$encodedFile",
                    artworkUrl = artwork?.let {
                        "https://archive.org/download/$encodedIdentifier/" +
                            it.split("/").joinToString("/") { part ->
                                URLEncoder.encode(part, "UTF-8").replace("+", "%20")
                            }
                    }
                )
                if (results.size >= 8) break
            } catch (_: Exception) {
                // One unavailable or malformed archive item should not fail the whole search.
            }
        }
        results
    }

    private fun getText(url: String): String {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "JMUSIC-Android/0.6 (audio catalog search)")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IllegalStateException("Internet Archive request failed (HTTP ${response.code}).")
            return response.body?.string() ?: throw IllegalStateException("Internet Archive returned an empty response.")
        }
    }

    private fun findArtworkFile(files: org.json.JSONArray): String? {
        for (index in 0 until files.length()) {
            val file = files.optJSONObject(index) ?: continue
            val name = file.optString("name").lowercase()
            if (name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".png")) {
                if (name.contains("cover") || name.contains("art") || name.contains("item")) {
                    return file.optString("name")
                }
            }
        }
        return null
    }
}
