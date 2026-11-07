package com.example.audio

import android.media.MediaPlayer
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

data class PlaybackState(
    val isPlaying: Boolean = false,
    val currentRecordingId: Long? = null,
    val currentPositionMs: Int = 0,
    val durationMs: Int = 0
)

class AudioPlayer {

    private var mediaPlayer: MediaPlayer? = null
    private val scope = CoroutineScope(Dispatchers.Main)
    private var progressJob: Job? = null

    private val _playbackState = MutableStateFlow(PlaybackState())
    val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()

    fun play(recordingId: Long, file: File) {
        if (!file.exists()) {
            Log.e("AudioPlayer", "File does not exist: ${file.absolutePath}")
            return
        }

        // If the same file is already loaded and paused, resume it
        if (_playbackState.value.currentRecordingId == recordingId && mediaPlayer != null) {
            mediaPlayer?.start()
            _playbackState.value = _playbackState.value.copy(isPlaying = true)
            startProgressUpdates()
            return
        }

        stop()

        try {
            val player = MediaPlayer().apply {
                setDataSource(file.absolutePath)
                prepare()
                setOnCompletionListener {
                    _playbackState.value = _playbackState.value.copy(
                        isPlaying = false,
                        currentPositionMs = 0
                    )
                    progressJob?.cancel()
                }
            }
            mediaPlayer = player
            player.start()

            _playbackState.value = PlaybackState(
                isPlaying = true,
                currentRecordingId = recordingId,
                currentPositionMs = 0,
                durationMs = player.duration
            )
            startProgressUpdates()
        } catch (e: Exception) {
            Log.e("AudioPlayer", "Failed to start playback", e)
            stop()
        }
    }

    fun pause() {
        mediaPlayer?.let {
            if (it.isPlaying) {
                it.pause()
                _playbackState.value = _playbackState.value.copy(isPlaying = false)
                progressJob?.cancel()
            }
        }
    }

    fun seekTo(positionMs: Int) {
        mediaPlayer?.let {
            it.seekTo(positionMs)
            _playbackState.value = _playbackState.value.copy(currentPositionMs = positionMs)
        }
    }

    fun stop() {
        progressJob?.cancel()
        progressJob = null
        try {
            mediaPlayer?.apply {
                if (isPlaying) {
                    stop()
                }
                release()
            }
        } catch (e: Exception) {
            Log.e("AudioPlayer", "Error releasing MediaPlayer", e)
        } finally {
            mediaPlayer = null
            _playbackState.value = PlaybackState()
        }
    }

    private fun startProgressUpdates() {
        progressJob?.cancel()
        progressJob = scope.launch {
            while (isActive && mediaPlayer?.isPlaying == true) {
                val current = mediaPlayer?.currentPosition ?: 0
                val total = mediaPlayer?.duration ?: 0
                _playbackState.value = _playbackState.value.copy(
                    currentPositionMs = current,
                    durationMs = total,
                    isPlaying = true
                )
                delay(100)
            }
        }
    }
}
