package com.example

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.audio.AudioEngine
import com.example.audio.AudioEngineState
import com.example.audio.AudioPlayer
import com.example.audio.PlaybackState
import com.example.audio.effects.VoiceEffect
import com.example.database.AppDatabase
import com.example.database.RecordingEntity
import com.example.settings.AppSettings
import com.example.settings.ThemeMode
import com.example.settings.UserSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val audioEngine = AudioEngine(application)
    private val audioPlayer = AudioPlayer(application)
    private val database = AppDatabase.getInstance(application)
    private val recordingDao = database.recordingDao()
    private val appSettings = AppSettings(application)

    val engineState: StateFlow<AudioEngineState> = audioEngine.engineState
    val visualizerData: StateFlow<FloatArray> = audioEngine.visualizerData
    val playbackState: StateFlow<PlaybackState> = audioPlayer.playbackState

    private val _lastSavedRecording = MutableStateFlow<RecordingEntity?>(null)
    val lastSavedRecording: StateFlow<RecordingEntity?> = _lastSavedRecording.asStateFlow()

    val recordings: StateFlow<List<RecordingEntity>> = recordingDao.getAllRecordings()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val userSettings: StateFlow<UserSettings> = appSettings.settingsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UserSettings())

    init {
        viewModelScope.launch {
            userSettings.collect { settings ->
                audioEngine.setSampleRate(settings.sampleRate)
                audioEngine.setAntiFeedbackEnabled(settings.antiFeedbackEnabled)
            }
        }
    }

    // Audio Engine Controls
    fun startAudio(): Boolean {
        audioPlayer.stop()
        return audioEngine.start()
    }

    fun stopAudio() {
        audioEngine.stop()
    }

    fun setMonitorDuringRecording(enabled: Boolean) {
        audioEngine.setMonitorDuringRecording(enabled)
    }

    fun selectEffect(effect: VoiceEffect) {
        audioEngine.setEffect(effect)
    }

    fun setIntensity(value: Float) {
        audioEngine.setIntensity(value)
    }

    fun setPitchFactor(value: Float) {
        audioEngine.setPitchFactor(value)
    }

    fun setDepth(value: Float) {
        audioEngine.setDepth(value)
    }

    fun setVolume(value: Float) {
        audioEngine.setMasterVolume(value)
    }

    fun clearAudioError() {
        audioEngine.clearError()
    }

    fun clearPlaybackError() {
        audioPlayer.clearError()
    }

    // Recording Controls
    fun startRecording() {
        audioPlayer.stop()
        _lastSavedRecording.value = null
        audioEngine.startRecording()
    }

    fun stopRecording() {
        val result = audioEngine.stopRecording()
        if (result != null) {
            viewModelScope.launch {
                val currentEffect = engineState.value.activeEffect
                val dateFormat = SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault())
                val dateFormatted = dateFormat.format(Date())
                val effectLabel = currentEffect.name.replace("_", " ").lowercase(Locale.getDefault())
                    .replaceFirstChar { it.uppercase() }
                val title = "Voix $effectLabel ($dateFormatted)"

                val entity = RecordingEntity(
                    title = title,
                    filePath = result.file.absolutePath,
                    durationMs = result.durationMs,
                    format = "WAV PCM 16-bit",
                    effectName = currentEffect.name,
                    fileSizeBytes = result.fileSizeBytes
                )
                val newId = recordingDao.insert(entity)
                _lastSavedRecording.value = entity.copy(id = newId)
            }
        }
    }

    fun dismissLastRecordingPreview() {
        if (playbackState.value.currentRecordingId == _lastSavedRecording.value?.id) {
            audioPlayer.stop()
        }
        _lastSavedRecording.value = null
    }

    // Playback Controls
    fun playRecording(recording: RecordingEntity) {
        if (engineState.value.isRunning) {
            audioEngine.stop()
        }
        val file = File(recording.filePath)
        audioPlayer.play(recording.id, file)
    }

    fun pausePlayback() {
        audioPlayer.pause()
    }

    fun seekPlayback(positionMs: Int) {
        audioPlayer.seekTo(positionMs)
    }

    fun stopPlayback() {
        audioPlayer.stop()
    }

    // Library Operations
    fun renameRecording(recording: RecordingEntity, newTitle: String) {
        viewModelScope.launch {
            recordingDao.updateTitle(recording.id, newTitle)
            if (_lastSavedRecording.value?.id == recording.id) {
                _lastSavedRecording.value = _lastSavedRecording.value?.copy(title = newTitle)
            }
        }
    }

    fun deleteRecording(recording: RecordingEntity) {
        viewModelScope.launch {
            if (playbackState.value.currentRecordingId == recording.id) {
                audioPlayer.stop()
            }
            if (_lastSavedRecording.value?.id == recording.id) {
                _lastSavedRecording.value = null
            }
            try {
                val file = File(recording.filePath)
                if (file.exists()) {
                    file.delete()
                }
            } catch (e: Exception) {
                android.util.Log.e("MainViewModel", "Error deleting file", e)
            }
            recordingDao.delete(recording)
        }
    }

    // Settings Operations
    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch {
            appSettings.setThemeMode(mode)
        }
    }

    fun setSampleRate(rate: Int) {
        viewModelScope.launch {
            appSettings.setSampleRate(rate)
            audioEngine.setSampleRate(rate)
        }
    }

    fun setAntiFeedback(enabled: Boolean) {
        viewModelScope.launch {
            appSettings.setAntiFeedback(enabled)
            audioEngine.setAntiFeedbackEnabled(enabled)
        }
    }

    override fun onCleared() {
        super.onCleared()
        audioEngine.release()
        audioPlayer.stop()
    }
}
