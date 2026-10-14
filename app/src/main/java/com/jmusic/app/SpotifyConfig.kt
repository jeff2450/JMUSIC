package com.jmusic.app

object SpotifyConfig {
    const val CLIENT_ID = "YOUR_SPOTIFY_CLIENT_ID"
    const val REDIRECT_URI = "jmusic://callback"
    const val AUTH_ENDPOINT = "https://accounts.spotify.com/authorize"
    const val TOKEN_ENDPOINT = "https://accounts.spotify.com/api/token"
    const val API_BASE = "https://api.spotify.com/v1"
    const val SCOPES = "playlist-read-private playlist-read-collaborative"
}
