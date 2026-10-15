# JMusic 🎧 — V3

JMusic is an Android music player built with Kotlin, Jetpack Compose, and AndroidX Media3/ExoPlayer.

## V3 features
- **In-app audio playback** using ExoPlayer
- **YouTube Music online search & stream playback**: Search tracks, view title/artist/duration/artwork, and stream audio directly inside ExoPlayer
- Browse and filter audio files stored on the phone
- Runtime audio-library permission handling (Android 13+ and older Android versions)
- Album artwork where available from Android MediaStore and online tracks
- Play/pause, seek, previous/next, and a persistent in-app mini-player while browsing
- Add an authorized direct HTTPS audio URL or compatible HLS stream to play custom streams
- Dark interface and local playback queue

## Run it
1. Open the repository in Android Studio.
2. Let Gradle sync dependencies.
3. Run on an Android phone or emulator.
4. Open **YouTube Music** tab, search for a track or artist, and tap any result to play audio inside JMusic.
5. Open **Phone library** and grant audio access to play local songs.
6. Open **Direct URL** to play custom direct HTTPS audio or HLS stream URLs.

## Implementation notes
- YouTube search & stream extraction uses direct Innertube endpoints to fetch audio stream URLs (`audio/mp4`, `audio/webm`) played natively in ExoPlayer.
- Media3 dependencies use version 1.8.0, with all Media3 artifacts kept on the same version.
- Local file access uses `READ_MEDIA_AUDIO` on Android 13+ and `READ_EXTERNAL_STORAGE` on older versions.

## Project
https://github.com/jeff2450/JMUSIC
