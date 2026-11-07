package com.example.audio

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.os.Process
import android.util.Log
import com.example.audio.effects.DspProcessor
import com.example.audio.effects.VoiceEffect
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
import java.util.concurrent.atomic.AtomicBoolean

data class AudioEngineState(
    val isRunning: Boolean = false,
    val isRecording: Boolean = false,
    val activeEffect: VoiceEffect = VoiceEffect.ROBOT,
    val sampleRate: Int = 44100,
    val latencyMs: Int = 30,
    val sessionDurationSeconds: Long = 0,
    val recordingDurationSeconds: Long = 0,
    val isFeedbackProtectionActive: Boolean = false,
    val isHeadphonesConnected: Boolean = false,
    val currentOutputDevice: String = "Haut-parleur du téléphone",
    val errorMessage: String? = null
)

class AudioEngine(private val context: Context) {

    private val dsp = DspProcessor(sampleRate = 44100.0f)
    private val visualizer = AudioVisualizerState(barCount = 32)
    private val recorder = AudioRecorder(context)
    private val deviceHelper = AudioDeviceHelper(context)

    private val isAudioRunning = AtomicBoolean(false)
    private var audioThread: Thread? = null

    private var audioRecord: AudioRecord? = null
    private var audioTrack: AudioTrack? = null

    private val scope = CoroutineScope(Dispatchers.Default)
    private var timerJob: Job? = null

    private var currentSampleRate = 44100
    private var antiFeedbackEnabled = true

    private val _engineState = MutableStateFlow(AudioEngineState())
    val engineState: StateFlow<AudioEngineState> = _engineState.asStateFlow()

    val visualizerData: StateFlow<FloatArray> = visualizer.visualizerData

    init {
        deviceHelper.startListening()
        scope.launch {
            deviceHelper.deviceState.collect { dev ->
                val feedbackProtect = antiFeedbackEnabled && !dev.isHeadphonesConnected
                _engineState.value = _engineState.value.copy(
                    isHeadphonesConnected = dev.isHeadphonesConnected,
                    currentOutputDevice = dev.deviceName,
                    isFeedbackProtectionActive = feedbackProtect
                )
                // Adjust DSP volume under speaker anti-feedback
                if (feedbackProtect) {
                    dsp.masterVolume = 0.45f
                } else {
                    dsp.masterVolume = 1.0f
                }
            }
        }
    }

    fun setSampleRate(rate: Int) {
        if (currentSampleRate != rate) {
            currentSampleRate = rate
            dsp.sampleRate = rate.toFloat()
            dsp.configureFiltersForCurrentEffect()
            _engineState.value = _engineState.value.copy(sampleRate = rate)
        }
    }

    fun setAntiFeedbackEnabled(enabled: Boolean) {
        antiFeedbackEnabled = enabled
        val feedbackProtect = enabled && !_engineState.value.isHeadphonesConnected
        _engineState.value = _engineState.value.copy(isFeedbackProtectionActive = feedbackProtect)
        if (feedbackProtect) {
            dsp.masterVolume = 0.45f
        } else {
            dsp.masterVolume = 1.0f
        }
    }

    fun setEffect(effect: VoiceEffect) {
        dsp.setEffect(effect)
        _engineState.value = _engineState.value.copy(activeEffect = effect)
    }

    fun setIntensity(value: Float) {
        dsp.intensity = value.coerceIn(0.0f, 1.0f)
    }

    fun setPitchFactor(value: Float) {
        dsp.pitchFactor = value.coerceIn(0.4f, 2.2f)
    }

    fun setDepth(value: Float) {
        dsp.depth = value.coerceIn(0.0f, 1.0f)
    }

    fun setMasterVolume(value: Float) {
        dsp.masterVolume = value.coerceIn(0.0f, 2.0f)
    }

    @SuppressLint("MissingPermission")
    @Synchronized
    fun start(): Boolean {
        if (isAudioRunning.get()) return true

        try {
            val channelInConfig = AudioFormat.CHANNEL_IN_MONO
            val channelOutConfig = AudioFormat.CHANNEL_OUT_MONO
            val audioEncoding = AudioFormat.ENCODING_PCM_16BIT

            val minRecBufferSize = AudioRecord.getMinBufferSize(currentSampleRate, channelInConfig, audioEncoding)
            val minTrackBufferSize = AudioTrack.getMinBufferSize(currentSampleRate, channelOutConfig, audioEncoding)

            if (minRecBufferSize <= 0 || minTrackBufferSize <= 0) {
                _engineState.value = _engineState.value.copy(
                    errorMessage = "Configuration audio non supportée pour $currentSampleRate Hz"
                )
                return false
            }

            // Low-latency chunk size (512 or 1024 samples)
            val chunkSize = 1024
            val recBufferSize = (minRecBufferSize * 2).coerceAtLeast(chunkSize * 4)
            val trackBufferSize = (minTrackBufferSize * 2).coerceAtLeast(chunkSize * 4)

            // Try VOICE_COMMUNICATION first for hardware echo suppression, fallback to MIC
            var record: AudioRecord? = null
            try {
                record = AudioRecord(
                    MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                    currentSampleRate,
                    channelInConfig,
                    audioEncoding,
                    recBufferSize
                )
            } catch (e: Exception) {
                Log.w("AudioEngine", "VOICE_COMMUNICATION source failed, falling back to MIC", e)
            }

            if (record == null || record.state != AudioRecord.STATE_INITIALIZED) {
                record = AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    currentSampleRate,
                    channelInConfig,
                    audioEncoding,
                    recBufferSize
                )
            }

            if (record.state != AudioRecord.STATE_INITIALIZED) {
                _engineState.value = _engineState.value.copy(
                    errorMessage = "Impossible d'initialiser le microphone (AudioRecord)."
                )
                return false
            }

            val audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()

            val audioFormat = AudioFormat.Builder()
                .setEncoding(audioEncoding)
                .setSampleRate(currentSampleRate)
                .setChannelMask(channelOutConfig)
                .build()

            val track = AudioTrack.Builder()
                .setAudioAttributes(audioAttributes)
                .setAudioFormat(audioFormat)
                .setBufferSizeInBytes(trackBufferSize)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
                .build()

            if (track.state != AudioTrack.STATE_INITIALIZED) {
                record.release()
                _engineState.value = _engineState.value.copy(
                    errorMessage = "Impossible d'initialiser la sortie audio (AudioTrack)."
                )
                return false
            }

            audioRecord = record
            audioTrack = track

            dsp.reset()
            record.startRecording()
            track.play()

            isAudioRunning.set(true)

            // Estimated latency in milliseconds
            val estimatedLatency = ((chunkSize.toFloat() / currentSampleRate) * 1000f * 1.5f).toInt()
            _engineState.value = _engineState.value.copy(
                isRunning = true,
                latencyMs = estimatedLatency,
                errorMessage = null,
                sessionDurationSeconds = 0
            )

            startTimer()

            // High-priority audio processing thread
            audioThread = Thread({
                Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)

                val inputBuffer = ShortArray(chunkSize)
                val outputBuffer = ShortArray(chunkSize)

                while (isAudioRunning.get()) {
                    val readCount = record.read(inputBuffer, 0, chunkSize)
                    if (readCount > 0) {
                        // Apply DSP effect
                        val peak = dsp.processBlock(inputBuffer, outputBuffer, readCount)
                        visualizer.updateWithPeak(peak)

                        // If recording is active, stream transformed PCM to WAV file
                        if (recorder.recordingActive) {
                            recorder.writeChunk(outputBuffer, readCount)
                        }

                        // Write to audio output
                        track.write(outputBuffer, 0, readCount)
                    } else if (readCount < 0) {
                        Log.w("AudioEngine", "AudioRecord read error: $readCount")
                        break
                    }
                }
            }, "CoolVoiceAudioThread").apply { start() }

            return true
        } catch (e: Exception) {
            Log.e("AudioEngine", "Fatal error starting audio engine", e)
            _engineState.value = _engineState.value.copy(
                errorMessage = "Erreur audio : ${e.localizedMessage ?: "Inconnue"}"
            )
            stop()
            return false
        }
    }

    @Synchronized
    fun stop() {
        if (!isAudioRunning.get()) return

        isAudioRunning.set(false)
        stopRecording()

        timerJob?.cancel()
        timerJob = null

        try {
            audioThread?.join(500)
        } catch (e: Exception) {
            Log.e("AudioEngine", "Error waiting for audio thread", e)
        }
        audioThread = null

        try {
            audioRecord?.apply {
                if (state == AudioRecord.STATE_INITIALIZED) {
                    stop()
                }
                release()
            }
        } catch (e: Exception) {
            Log.e("AudioEngine", "Error releasing AudioRecord", e)
        } finally {
            audioRecord = null
        }

        try {
            audioTrack?.apply {
                if (state == AudioTrack.STATE_INITIALIZED) {
                    stop()
                }
                release()
            }
        } catch (e: Exception) {
            Log.e("AudioEngine", "Error releasing AudioTrack", e)
        } finally {
            audioTrack = null
        }

        visualizer.reset()
        _engineState.value = _engineState.value.copy(
            isRunning = false,
            isRecording = false,
            sessionDurationSeconds = 0,
            recordingDurationSeconds = 0
        )
    }

    fun startRecording(): File? {
        if (!isAudioRunning.get()) {
            val started = start()
            if (!started) return null
        }
        val file = recorder.startRecording(
            effectName = _engineState.value.activeEffect.name,
            sampleRate = currentSampleRate
        )
        if (file != null) {
            _engineState.value = _engineState.value.copy(
                isRecording = true,
                recordingDurationSeconds = 0
            )
        }
        return file
    }

    fun stopRecording(): AudioRecorder.RecordingResult? {
        if (!recorder.recordingActive) return null
        val result = recorder.stopRecording()
        _engineState.value = _engineState.value.copy(
            isRecording = false,
            recordingDurationSeconds = 0
        )
        return result
    }

    private fun startTimer() {
        timerJob?.cancel()
        timerJob = scope.launch {
            while (isActive && isAudioRunning.get()) {
                delay(1000)
                val current = _engineState.value
                val newRecSec = if (current.isRecording) current.recordingDurationSeconds + 1 else 0
                _engineState.value = current.copy(
                    sessionDurationSeconds = current.sessionDurationSeconds + 1,
                    recordingDurationSeconds = newRecSec
                )
            }
        }
    }

    fun release() {
        stop()
        deviceHelper.stopListening()
    }
}
