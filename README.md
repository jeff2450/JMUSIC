# JMusic 🎧

JMusic is an Android Studio starter for a Spotify companion app built with Kotlin and Jetpack Compose.

## Version 0.2.0 features
- Dark, polished interface
- Spotify OAuth login foundation using Authorization Code + PKCE
- Search the Spotify catalog for artists and tracks
- Artist results with artist images and links to Spotify
- Song lists with album artwork, artist names, and album titles
- A full-screen Now Playing-style track detail view
- Sync your Spotify playlists
- Playback opens the official Spotify app; JMusic does not stream or download Spotify audio

## Setup
1. Open this repository in Android Studio.
2. Create/configure an app at https://developer.spotify.com/dashboard if your account is eligible.
3. Set this exact redirect URI in Spotify settings: `jmusic://callback`
4. Put your Spotify Client ID in `app/src/main/java/com/jmusic/app/SpotifyConfig.kt`.
5. Run on an Android device and tap **Connect Spotify**.
6. Search an artist or song. Tap a song to see its artwork and details, then tap **Play in Spotify**.

## Important limitations
- Spotify API eligibility, endpoints, scopes, and limits depend on Spotify's current developer rules. If search or playlist sync returns 401/403, verify your developer app access and account authorization.
- This app does not bypass Spotify Premium, ads, DRM, or playback restrictions. Playback is handled by the official Spotify app.
- The Now Playing-style screen is a JMusic track-details screen; it does not read or control Spotify's live playback state.
- Spotify artwork is displayed without cropping and alongside links/attribution to Spotify, per Spotify platform guidance.
- This starter has not been compiled or tested on a physical device. Review OAuth token security, refresh handling, and PKCE implementation before publishing. Access tokens are currently stored in SharedPreferences for starter simplicity; do not ship this storage approach as-is.
