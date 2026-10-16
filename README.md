# JMusic 🎧 — V3

JMusic is an Android music player built with Kotlin, Jetpack Compose, AndroidX Media3/ExoPlayer, and an optional YouTube catalog-search integration.

## V3 features
- **In-app audio playback** using ExoPlayer
- **YouTube catalog search**: Search for songs and artists, view thumbnails and channel names, and open selected results in the official YouTube player
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
- YouTube search uses the documented YouTube Data API v3 `search.list` endpoint for catalog metadata only.
- Media3 dependencies use version 1.8.0, with all Media3 artifacts kept on the same version.
- Local file access uses `READ_MEDIA_AUDIO` on Android 13+ and `READ_EXTERNAL_STORAGE` on older versions.

## Project
https://github.com/jeff2450/JMUSIC

## ArchiveTune-inspired development

JMusic keeps its Kotlin + Jetpack Compose + Media3 architecture while building toward a music-first experience: online discovery, phone library, playlists/favorites, lyrics, and background controls.

### Online catalog prototype
- `YouTubeMusicRepository.kt` uses the official YouTube Data API v3 `search.list` endpoint to retrieve video titles, channel names, and thumbnails.
- This is **catalog search only**; it does not provide playable audio URLs. YouTube playback should use an approved YouTube embedded player or the official YouTube app/site, subject to YouTube's terms and playback restrictions.
- Create a Google Cloud project, enable YouTube Data API v3, create an API key, and restrict it to the extent supported for your app.
- For local development, add this to your user-level Gradle properties file (do not commit a real key):
  ```properties
  YOUTUBE_API_KEY=YOUR_API_KEY
  ```
  On Windows this is commonly `%USERPROFILE%\\.gradle\\gradle.properties`. The app currently reads this property into BuildConfig for the repository prototype. A production release should proxy API requests through a backend or use an appropriate key-management approach; API keys embedded in an APK can be extracted.
- The YouTube search tab provides a search field, displays results with thumbnails and channel names, and opens a selected result in the official YouTube app/browser. Local phone playback and authorized direct-stream playback remain available in separate tabs.
- A Media3 `MediaSessionService`, reliable background playback, notification/lock-screen controls, and a fully connected favorites/playlist library are still future work.

### Important playback boundary
The YouTube Data API returns metadata and video IDs, not unrestricted audio streams. YouTube's developer policies prohibit separating audio from audiovisual content, scraping, and undocumented API access. JMusic therefore does not extract audio-only stream URLs or bypass DRM, ads, subscriptions, regional restrictions, or other service controls. Background playback and lock-screen controls are intended for local files and sources that explicitly permit them. Read the [YouTube API Services Developer Policies](https://developers.google.com/youtube/terms/developer-policies) before distributing an app that uses the API.

### License note
ArchiveTune is a separate GPLv3 project. JMusic is being inspired by its general user experience, not copying its implementation or branding. If code is later copied or adapted from ArchiveTune, review and comply with GPLv3 obligations and keep ArchiveTune's name/logo separate.
