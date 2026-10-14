package com.jmusic.app

import android.content.Context
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

class SpotifyRepository(context: Context) {
    private val client = OkHttpClient()
    private val prefs = context.getSharedPreferences("jmusic_session", Context.MODE_PRIVATE)

    fun saveAccessToken(token: String) {
        prefs.edit().putString("access_token", token).apply()
    }
    fun getAccessToken(): String? = prefs.getString("access_token", null)

    @Throws(IOException::class)
    fun getPlaylists(): List<SpotifyPlaylist> {
        val token = getAccessToken() ?: throw IOException("Connect Spotify first.")
        val request = Request.Builder()
            .url("${SpotifyConfig.API_BASE}/me/playlists?limit=50")
            .header("Authorization", "Bearer $token")
            .build()
        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw IOException("Spotify returned HTTP ${response.code}: $body")
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
