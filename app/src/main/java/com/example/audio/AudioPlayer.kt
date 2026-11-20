package com.example.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Build
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
import java.io.FileInputStream

data class PlaybackState(
    val isPlaying: Boolean = false,
    val currentRecordingId: Long? = null,
    val currentPositionMs: Int = 0,
    val durationMs: Int = 0,
    val errorMessage: String? = null
)

class AudioPlayer(private val context: Context) {

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var mediaPlayer: MediaPlayer? = null
    private val scope = CoroutineScope(Dispatchers.Main)
    private var progressJob: Job? = null
    private var focusRequest: AudioFocusRequest? = null

    private val _playbackState = MutableStateFlow(PlaybackState())
    val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()

    fun play(recordingId: Long, file: File) {
        if (!file.exists() || file.length() <= 44L) {
            Log.e("AudioPlayer", "Invalid or missing audio file: ${file.absolutePath}")
            _playbackState.value = _playbackState.value.copy(
                isPlaying = false,
                errorMessage = "Fichier audio introuvable ou vide."
            )
            return
        }

        // Resume if same file is paused
        if (_playbackState.value.currentRecordingId == recordingId && mediaPlayer != null) {
            try {
                requestAudioFocus()
                mediaPlayer?.start()
                _playbackState.value = _playbackState.value.copy(isPlaying = true, errorMessage = null)
                startProgressUpdates()
                return
            } catch (e: Exception) {
                Log.w("AudioPlayer", "Resume failed, recreating MediaPlayer", e)
            }
        }

        stop()

        try {
            // Ensure normal media routing (never stuck in communication/earpiece mode)
            if (audioManager.mode != AudioManager.MODE_NORMAL) {
                audioManager.mode = AudioManager.MODE_NORMAL
            }
            requestAudioFocus()

            val player = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                FileInputStream(file).use { fis ->
                    setDataSource(fis.fd)
                }
                prepare()
                setOnCompletionListener {
                    progressJob?.cancel()
                    abandonAudioFocus()
                    _playbackState.value = _playbackState.value.copy(
                        isPlaying = false,
                        currentPositionMs = 0
                    )
                }
                setOnErrorListener { _, what, extra ->
                    Log.e("AudioPlayer", "MediaPlayer error: what=$what extra=$extra")
                    stop()
                    _playbackState.value = _playbackState.value.copy(
                        errorMessage = "Impossible de lire ce fichier audio (code $what)."
                    )
                    true
                }
            }

            mediaPlayer = player
            player.start()

            _playbackState.value = PlaybackState(
                isPlaying = true,
                currentRecordingId = recordingId,
                currentPositionMs = 0,
                durationMs = player.duration.coerceAtLeast(1),
                errorMessage = null
            )
            startProgressUpdates()
        } catch (e: Exception) {
            Log.e("AudioPlayer", "Failed to start playback", e)
            stop()
            _playbackState.value = PlaybackState(
                errorMessage = "Erreur de lecture : ${e.localizedMessage ?: "Inconnue"}"
            )
        }
    }

    fun pause() {
        mediaPlayer?.let {
            try {
                if (it.isPlaying) {
                    it.pause()
                    _playbackState.value = _playbackState.value.copy(isPlaying = false)
                    progressJob?.cancel()
                }
            } catch (e: Exception) {
                Log.e("AudioPlayer", "Pause failed", e)
            }
        }
    }

    fun seekTo(positionMs: Int) {
        mediaPlayer?.let {
            try {
                it.seekTo(positionMs)
                _playbackState.value = _playbackState.value.copy(currentPositionMs = positionMs)
            } catch (e: Exception) {
                Log.e("AudioPlayer", "Seek failed", e)
            }
        }
    }

    fun clearError() {
        _playbackState.value = _playbackState.value.copy(errorMessage = null)
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
            abandonAudioFocus()
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
                    durationMs = total.coerceAtLeast(1),
                    isPlaying = true
                )
                delay(100)
            }
        }
    }

    private fun requestAudioFocus() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build()
                    )
                    .build()
                focusRequest = req
                audioManager.requestAudioFocus(req)
            } else {
                @Suppress("DEPRECATION")
                audioManager.requestAudioFocus(
                    null,
                    AudioManager.STREAM_MUSIC,
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
                )
            }
        } catch (e: Exception) {
            Log.w("AudioPlayer", "AudioFocus request failed", e)
        }
    }

    private fun abandonAudioFocus() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                focusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
                focusRequest = null
            } else {
                @Suppress("DEPRECATION")
                audioManager.abandonAudioFocus(null)
            }
        } catch (e: Exception) {
            Log.w("AudioPlayer", "AudioFocus abandon failed", e)
        }
    }
}
