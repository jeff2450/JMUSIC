# JMusic 🎧

JMusic is an Android Studio starter for a Spotify companion app built with Kotlin and Jetpack Compose.

## Features
- Dark-themed Android interface
- Spotify OAuth login foundation using Authorization Code + PKCE
- Fetch the current user's playlists
- Open playlist links in Spotify or a browser
- Search shortcut that opens Spotify search

## Setup
1. Open this repository in Android Studio.
2. Create/configure an app at https://developer.spotify.com/dashboard if your account is eligible.
3. Set this exact redirect URI in Spotify settings: `jmusic://callback`
4. Put your Spotify Client ID in `app/src/main/java/com/jmusic/app/SpotifyConfig.kt`.
5. Run on an Android device and tap Connect Spotify.

## Important
- Spotify API eligibility, endpoints, scopes, and limits depend on Spotify's current developer rules.
- This app does not bypass Spotify Premium, ads, DRM, or playback restrictions. Playback opens in the official Spotify app.
- Playlist entries are metadata, not downloadable audio.
- This is a starter project and has not been verified as a production release. Review token storage and refresh handling before publishing.
