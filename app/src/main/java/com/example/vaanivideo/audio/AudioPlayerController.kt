package com.example.vaanivideo.audio

import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.example.vaanivideo.data.model.NarrationFileItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class PlayerPlaybackState(
    val isPlaying: Boolean = false,
    val currentPositionMs: Long = 0L,
    val totalDurationMs: Long = 0L,
    val activeFileName: String = ""
)

class AudioPlayerController(private val context: Context) {

    private var exoPlayer: ExoPlayer? = null
    private var narrationFiles: List<NarrationFileItem> = emptyList()
    private var totalTimelineDurationMs: Long = 0L

    private val _playbackState = MutableStateFlow(PlayerPlaybackState())
    val playbackState: StateFlow<PlayerPlaybackState> = _playbackState.asStateFlow()

    private var progressPollingJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main)

    fun initialize(files: List<NarrationFileItem>) {
        release()
        narrationFiles = files
        totalTimelineDurationMs = files.sumOf { it.durationMs }

        val player = ExoPlayer.Builder(context).build()
        exoPlayer = player

        for (item in files) {
            val uri = Uri.parse(item.fileUriString)
            player.addMediaItem(MediaItem.fromUri(uri))
        }

        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                updateState()
                if (isPlaying) {
                    startProgressPolling()
                } else {
                    stopProgressPolling()
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                updateState()
            }
        })

        player.prepare()
        updateState()
    }

    private fun startProgressPolling() {
        progressPollingJob?.cancel()
        progressPollingJob = scope.launch {
            while (isActive) {
                updateState()
                delay(40) // Smooth ~25fps position updates for timeline editor
            }
        }
    }

    private fun stopProgressPolling() {
        progressPollingJob?.cancel()
        progressPollingJob = null
        updateState()
    }

    private fun calculateAbsolutePositionMs(): Long {
        val player = exoPlayer ?: return 0L
        val currentMediaIndex = player.currentMediaItemIndex
        val mediaPos = player.currentPosition

        if (currentMediaIndex in narrationFiles.indices) {
            val fileItem = narrationFiles[currentMediaIndex]
            return fileItem.startMsInTimeline + mediaPos
        }
        return mediaPos
    }

    private fun updateState() {
        val player = exoPlayer ?: return
        val absPos = calculateAbsolutePositionMs()
        val currentMediaIndex = player.currentMediaItemIndex
        val activeName = if (currentMediaIndex in narrationFiles.indices) {
            narrationFiles[currentMediaIndex].fileName
        } else ""

        _playbackState.value = PlayerPlaybackState(
            isPlaying = player.isPlaying,
            currentPositionMs = absPos.coerceIn(0L, totalTimelineDurationMs.coerceAtLeast(1L)),
            totalDurationMs = totalTimelineDurationMs,
            activeFileName = activeName
        )
    }

    fun play() {
        exoPlayer?.play()
    }

    fun pause() {
        exoPlayer?.pause()
    }

    fun togglePlayPause() {
        val player = exoPlayer ?: return
        if (player.isPlaying) {
            player.pause()
        } else {
            player.play()
        }
    }

    fun seekTo(timelinePositionMs: Long) {
        val player = exoPlayer ?: return
        val targetMs = timelinePositionMs.coerceIn(0L, totalTimelineDurationMs)

        // Find which media item contains this timeline position
        var targetFileIndex = 0
        var offsetInsideFile = targetMs
        for (i in narrationFiles.indices) {
            val file = narrationFiles[i]
            if (targetMs >= file.startMsInTimeline && targetMs <= file.endMsInTimeline) {
                targetFileIndex = i
                offsetInsideFile = targetMs - file.startMsInTimeline
                break
            }
        }

        player.seekTo(targetFileIndex, offsetInsideFile)
        updateState()
    }

    fun seekBy(deltaMs: Long) {
        val currentPos = calculateAbsolutePositionMs()
        seekTo(currentPos + deltaMs)
    }

    fun release() {
        stopProgressPolling()
        exoPlayer?.release()
        exoPlayer = null
    }
}
