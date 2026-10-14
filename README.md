# JMusic 🎧 — V3

JMusic is an Android music player built with Kotlin, Jetpack Compose, and AndroidX Media3/ExoPlayer.

## V3 features
- **In-app audio playback** using ExoPlayer (not the Spotify app)
- Browse and filter audio files stored on the phone
- Runtime audio-library permission handling (Android 13+ and older Android versions)
- Album artwork where available from Android MediaStore
- Play/pause, seek, previous/next, and a persistent in-app mini-player while browsing
- Add an authorized direct HTTPS audio URL or compatible HLS stream to play online
- Dark interface and local playback queue

## Run it
1. Open the repository in Android Studio.
2. Let Gradle sync dependencies.
3. Run on an Android phone or emulator.
4. Open **Phone library** and grant audio access.
5. Tap a local track to play it inside JMusic.
6. To test online playback, open **Online stream** and enter a direct HTTPS audio URL you own or have permission to stream. A normal Spotify/YouTube webpage URL is not a direct audio stream.

## What online streaming means in this version
V3 includes the ExoPlayer engine and an input for a directly playable authorized stream URL. It does **not** include a hosted music catalog, artist-upload backend, content licensing, or a source of commercial music. You must provide a server/stream URL for online playback. Common formats include MP3/AAC and compatible HLS streams, depending on the server and device.

Spotify's Web API returns catalog metadata and links, not unrestricted playable audio URLs. Spotify search results therefore cannot be passed to ExoPlayer as if they were audio files. Do not bypass DRM, subscription checks, ads, or service restrictions.

## Implementation notes
- Media3 dependencies use version 1.8.0, with all Media3 artifacts kept on the same version.
- Local file access uses `READ_MEDIA_AUDIO` on Android 13+ and `READ_EXTERNAL_STORAGE` on older versions.
- This version does not yet include a MediaSession foreground service, notification controls, background-playback lifecycle, or a production streaming backend.
- This change has been committed to GitHub, but it has **not** been built or tested on a device in this environment. Run Gradle sync/build and verify local and online playback on a real device.

## Project
https://github.com/jeff2450/JMUSIC
