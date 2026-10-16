# JMusic 🎧 — V3

JMusic is an Android music player built with Kotlin, Jetpack Compose, AndroidX Media3/ExoPlayer, and YouTube catalog search.

## Features
- **YouTube catalog search** for songs, artists, and videos using the official YouTube Data API v3
- Search results with thumbnails, titles, channel names, and video durations when available
- Open selected videos in the official YouTube app or website
- Browse and play audio files stored on the phone
- Favorites and playlists for local music
- Play/pause, seek, previous/next, shuffle/repeat, queue, and a persistent mini-player
- Background playback and Android media controls for local files and authorized direct audio streams
- Search Internet Archive recordings with open-license metadata and play supported original audio files
- Add an authorized direct HTTPS audio URL or compatible HLS stream
- Dark interface

## Configure the YouTube API key
1. In Google Cloud Console, enable **YouTube Data API v3** for your project.
2. Create an API key and apply appropriate restrictions and quota monitoring.
3. Add the following to your user-level Gradle properties file (do not commit your real key):
   ```properties
   YOUTUBE_API_KEY=YOUR_API_KEY
   ```
   On Windows this is commonly `%USERPROFILE%\.gradle\gradle.properties`.
4. Open the repository in Android Studio, sync Gradle, and run the app.
5. Open the **YouTube** tab, search for a song or artist, and tap a result to open it in YouTube.

The app calls `search.list` for results and `videos.list` to request duration metadata. A duration may be unavailable for some results. If the duration lookup fails, search results still appear.

## Playback limitations
The YouTube Data API provides metadata and video IDs, not unrestricted audio-stream URLs. JMusic does not extract YouTube audio or bypass YouTube playback, advertisements, subscriptions, DRM, or regional restrictions. Selected YouTube videos open in the official YouTube app/site; YouTube background audio is not played through JMusic's Media3 service.

Media3 background playback is intended for local files and sources that explicitly permit direct streaming, such as supported open-license recordings or authorized direct audio/HLS URLs. Review the [YouTube API Services Developer Policies](https://developers.google.com/youtube/terms/developer-policies) before distributing the app.

## Project
https://github.com/jeff2450/JMUSIC
