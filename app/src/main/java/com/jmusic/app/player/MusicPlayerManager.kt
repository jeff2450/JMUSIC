package com.jmusic.app.player

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.jmusic.app.model.MediaTrack
import com.jmusic.app.model.RepeatMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class MusicPlayerManager(context: Context) {
    val player: ExoPlayer = ExoPlayer.Builder(context).build()

    var currentTrack by mutableStateOf<MediaTrack?>(null)
    var isPlaying by mutableStateOf(false)
    var isBuffering by mutableStateOf(false)
    var durationMs by mutableLongStateOf(0L)
    var currentPositionMs by mutableLongStateOf(0L)
    var repeatMode by mutableStateOf(RepeatMode.OFF)
    var isShuffleEnabled by mutableStateOf(false)
    var playbackSpeed by mutableFloatStateOf(1.0f)
    var pitch by mutableFloatStateOf(1.0f)

    var queue = mutableStateOf<List<MediaTrack>>(emptyList())
    var currentIndex by mutableIntStateOf(-1)

    var errorMessage by mutableStateOf<String?>(null)

    private val scope = CoroutineScope(Dispatchers.Main + Job())
    private var progressJob: Job? = null

    init {
        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
                if (playing) startProgressTracker() else stopProgressTracker()
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                isBuffering = playbackState == Player.STATE_BUFFERING
                if (playbackState == Player.STATE_READY) {
                    durationMs = player.duration.coerceAtLeast(0L)
                } else if (playbackState == Player.STATE_ENDED) {
                    isPlaying = false
                }
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                val index = player.currentMediaItemIndex
                if (index in queue.value.indices) {
                    currentIndex = index
                    currentTrack = queue.value[index]
                } else {
                    val metadata = mediaItem?.mediaMetadata
                    if (metadata != null) {
                        currentTrack = MediaTrack(
                            id = mediaItem.mediaId.ifBlank { "unknown" },
                            title = metadata.title?.toString() ?: "Unknown track",
                            artist = metadata.artist?.toString() ?: "Unknown artist",
                            album = metadata.albumTitle?.toString() ?: "",
                            artworkUrl = metadata.artworkUri?.toString()
                        )
                    }
                }
                durationMs = player.duration.coerceAtLeast(0L)
                currentPositionMs = player.currentPosition.coerceAtLeast(0L)
            }

            override fun onPlayerError(error: PlaybackException) {
                errorMessage = "Playback error: ${error.message ?: "Stream unavailable"}"
                isBuffering = false
            }
        })
    }

    private fun startProgressTracker() {
        progressJob?.cancel()
        progressJob = scope.launch {
            while (isActive) {
                currentPositionMs = player.currentPosition.coerceAtLeast(0L)
                durationMs = player.duration.coerceAtLeast(0L)
                delay(300)
            }
        }
    }

    private fun stopProgressTracker() {
        progressJob?.cancel()
    }

    fun playTrack(track: MediaTrack) {
        setQueue(listOf(track), 0)
    }

    fun setQueue(tracks: List<MediaTrack>, startIndex: Int = 0) {
        if (tracks.isEmpty()) return
        queue.value = tracks
        currentIndex = startIndex.coerceIn(tracks.indices)
        currentTrack = tracks[currentIndex]

        player.clearMediaItems()
        val mediaItems = tracks.map { track ->
            val uri = track.localUri ?: track.streamUrl?.let { Uri.parse(it) } ?: Uri.EMPTY
            MediaItem.Builder()
                .setMediaId(track.id)
                .setUri(uri)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(track.title)
                        .setArtist(track.artist)
                        .setAlbumTitle(track.album)
                        .setArtworkUri(track.artworkUrl?.let { Uri.parse(it) })
                        .build()
                ).build()
        }

        player.setMediaItems(mediaItems, currentIndex, 0L)
        player.prepare()
        player.play()
    }

    fun togglePlayPause() {
        if (player.isPlaying) {
            player.pause()
        } else if (player.mediaItemCount > 0) {
            player.play()
        }
    }

    fun seekTo(positionMs: Long) {
        player.seekTo(positionMs)
        currentPositionMs = positionMs
    }

    fun skipNext() {
        if (player.hasNextMediaItem()) {
            player.seekToNextMediaItem()
        }
    }

    fun skipPrevious() {
        if (player.hasPreviousMediaItem()) {
            player.seekToPreviousMediaItem()
        } else {
            player.seekTo(0L)
        }
    }

    fun seekForward(ms: Long = 10000L) {
        val target = (player.currentPosition + ms).coerceAtMost(player.duration)
        seekTo(target)
    }

    fun seekBackward(ms: Long = 10000L) {
        val target = (player.currentPosition - ms).coerceAtLeast(0L)
        seekTo(target)
    }

    fun toggleRepeatMode() {
        repeatMode = when (repeatMode) {
            RepeatMode.OFF -> RepeatMode.REPEAT_ALL
            RepeatMode.REPEAT_ALL -> RepeatMode.REPEAT_ONE
            RepeatMode.REPEAT_ONE -> RepeatMode.OFF
        }
        player.repeatMode = when (repeatMode) {
            RepeatMode.OFF -> Player.REPEAT_MODE_OFF
            RepeatMode.REPEAT_ALL -> Player.REPEAT_MODE_ALL
            RepeatMode.REPEAT_ONE -> Player.REPEAT_MODE_ONE
        }
    }

    fun toggleShuffle() {
        isShuffleEnabled = !isShuffleEnabled
        player.shuffleModeEnabled = isShuffleEnabled
    }

    fun setSpeedAndPitch(speed: Float, newPitch: Float = 1.0f) {
        playbackSpeed = speed
        pitch = newPitch
        player.playbackParameters = PlaybackParameters(speed, newPitch)
    }

    fun release() {
        stopProgressTracker()
        player.release()
    }
}
