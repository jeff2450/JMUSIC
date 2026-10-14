package com.jmusic.app

import android.content.Context
import android.net.Uri
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException

data class SpotifyPlaylist(
    val id: String,
    val name: String,
    val description: String,
    val trackCount: Int,
    val spotifyUrl: String
)

data class SpotifyArtist(
    val id: String,
    val name: String,
    val imageUrl: String,
    val spotifyUrl: String
)

data class SpotifyTrack(
    val id: String,
    val name: String,
    val artistNames: String,
    val albumName: String,
    val artworkUrl: String,
    val durationMs: Long,
    val spotifyUrl: String
)

data class SpotifySearchResults(
    val artists: List<SpotifyArtist>,
    val tracks: List<SpotifyTrack>
)

class SpotifyRepository(context: Context) {
    private val client = OkHttpClient()
    private val prefs = context.getSharedPreferences("jmusic_session", Context.MODE_PRIVATE)

    fun saveAccessToken(token: String) {
        prefs.edit().putString("access_token", token).apply()
    }

    fun getAccessToken(): String? = prefs.getString("access_token", null)

    @Throws(IOException::class)
    fun search(query: String): SpotifySearchResults {
        val token = getAccessToken() ?: throw IOException("Connect Spotify first to search the catalog.")
        if (query.isBlank()) return SpotifySearchResults(emptyList(), emptyList())

        val url = Uri.parse("${SpotifyConfig.API_BASE}/search")
            .buildUpon()
            .appendQueryParameter("q", query)
            .appendQueryParameter("type", "artist,track")
            .appendQueryParameter("limit", "10")
            .appendQueryParameter("market", "TZ")
            .build().toString()

        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $token")
            .build()

        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw IOException("Spotify search failed (HTTP ${response.code}). Check your app's API access and try again.")
            val json = JSONObject(body)

            val artistItems = json.optJSONObject("artists")?.optJSONArray("items")
            val artists = (0 until (artistItems?.length() ?: 0)).mapNotNull { index ->
                val item = artistItems?.optJSONObject(index) ?: return@mapNotNull null
                SpotifyArtist(
                    id = item.optString("id"),
                    name = item.optString("name", "Unknown artist"),
                    imageUrl = item.optJSONArray("images")?.optJSONObject(0)?.optString("url").orEmpty(),
                    spotifyUrl = item.optJSONObject("external_urls")?.optString("spotify").orEmpty()
                )
            }

            val trackItems = json.optJSONObject("tracks")?.optJSONArray("items")
            val tracks = (0 until (trackItems?.length() ?: 0)).mapNotNull { index ->
                val item = trackItems?.optJSONObject(index) ?: return@mapNotNull null
                val artistArray = item.optJSONArray("artists")
                val artistNames = (0 until (artistArray?.length() ?: 0))
                    .mapNotNull { artistArray?.optJSONObject(it)?.optString("name") }
                    .joinToString(", ")
                val album = item.optJSONObject("album")
                SpotifyTrack(
                    id = item.optString("id"),
                    name = item.optString("name", "Unknown track"),
                    artistNames = artistNames.ifBlank { "Unknown artist" },
                    albumName = album?.optString("name").orEmpty(),
                    artworkUrl = album?.optJSONArray("images")?.optJSONObject(0)?.optString("url").orEmpty(),
                    durationMs = item.optLong("duration_ms", 0L),
                    spotifyUrl = item.optJSONObject("external_urls")?.optString("spotify").orEmpty()
                )
            }
            return SpotifySearchResults(artists, tracks)
        }
    }

    @Throws(IOException::class)
    fun getPlaylists(): List<SpotifyPlaylist> {
        val token = getAccessToken() ?: throw IOException("Connect Spotify first.")
        val request = Request.Builder()
            .url("${SpotifyConfig.API_BASE}/me/playlists?limit=50")
            .header("Authorization", "Bearer $token")
            .build()
        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw IOException("Spotify playlist request failed (HTTP ${response.code}).")
            val items = JSONObject(body).optJSONArray("items") ?: return emptyList()
            return (0 until items.length()).mapNotNull { i ->
                val item = items.optJSONObject(i) ?: return@mapNotNull null
                SpotifyPlaylist(
                    id = item.optString("id"),
                    name = item.optString("name", "Untitled playlist"),
                    description = item.optString("description"),
                    trackCount = item.optJSONObject("tracks")?.optInt("total", 0) ?: 0,
                    spotifyUrl = item.optJSONObject("external_urls")?.optString("spotify").orEmpty()
                )
            }
        }
    }
}
