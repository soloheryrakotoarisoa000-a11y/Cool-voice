package com.example.audio

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.os.Build
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
import kotlin.math.abs

data class AudioEngineState(
    val isRunning: Boolean = false,
    val isMonitoringOutput: Boolean = false,
    val isRecording: Boolean = false,
    val monitorDuringRecording: Boolean = false,
    val activeEffect: VoiceEffect = VoiceEffect.ROBOT,
    val intensity: Float = VoiceEffect.ROBOT.defaultIntensity,
    val pitchFactor: Float = VoiceEffect.ROBOT.defaultPitchFactor,
    val depth: Float = VoiceEffect.ROBOT.defaultDepth,
    val masterVolume: Float = 1.0f,
    val sampleRate: Int = 44100,
    val latencyMs: Int = 24,
    val sessionDurationSeconds: Long = 0,
    val recordingDurationSeconds: Long = 0,
    val isFeedbackProtectionActive: Boolean = false,
    val isHeadphonesConnected: Boolean = false,
    val currentOutputDevice: String = "Haut-parleur du téléphone",
    val micInputLevel: Float = 0f,
    val errorMessage: String? = null
)

class AudioEngine(private val context: Context) {

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val dsp = DspProcessor(sampleRate = 44100.0f)
    private val visualizer = AudioVisualizerState(barCount = 32)
    private val recorder = AudioRecorder(context)
    private val deviceHelper = AudioDeviceHelper(context)

    private val isAudioRunning = AtomicBoolean(false)
    @Volatile private var liveMonitoringEnabled = false
    @Volatile private var wasStartedJustForRecording = false
    private var audioThread: Thread? = null

    private var audioRecord: AudioRecord? = null
    private var audioTrack: AudioTrack? = null
    private var focusRequest: AudioFocusRequest? = null

    private val scope = CoroutineScope(Dispatchers.Default)
    private var timerJob: Job? = null

    private var preferredSampleRate = 44100
    private var activeSampleRate = 44100
    private var antiFeedbackEnabled = true
    private var userVolumeSetting = 1.0f

    private val _engineState = MutableStateFlow(AudioEngineState())
    val engineState: StateFlow<AudioEngineState> = _engineState.asStateFlow()

    val visualizerData: StateFlow<FloatArray> = visualizer.visualizerData

    init {
        deviceHelper.startListening()
        scope.launch {
            deviceHelper.deviceState.collect { dev ->
                val feedbackProtect = antiFeedbackEnabled && !dev.isHeadphonesConnected
                dsp.speakerAntiFeedback = feedbackProtect
                _engineState.value = _engineState.value.copy(
                    isHeadphonesConnected = dev.isHeadphonesConnected,
                    currentOutputDevice = dev.deviceName,
                    isFeedbackProtectionActive = feedbackProtect
                )
            }
        }
    }

    fun setSampleRate(rate: Int) {
        if (preferredSampleRate != rate) {
            preferredSampleRate = rate
            if (!isAudioRunning.get()) {
                activeSampleRate = rate
                dsp.sampleRate = rate.toFloat()
                dsp.configureFiltersForCurrentEffect()
                _engineState.value = _engineState.value.copy(sampleRate = rate)
            }
        }
    }

    fun setAntiFeedbackEnabled(enabled: Boolean) {
        antiFeedbackEnabled = enabled
        val feedbackProtect = enabled && !_engineState.value.isHeadphonesConnected
        dsp.speakerAntiFeedback = feedbackProtect
        _engineState.value = _engineState.value.copy(isFeedbackProtectionActive = feedbackProtect)
    }

    fun setMonitorDuringRecording(enabled: Boolean) {
        _engineState.value = _engineState.value.copy(monitorDuringRecording = enabled)
        if (_engineState.value.isRecording && wasStartedJustForRecording) {
            liveMonitoringEnabled = enabled
            _engineState.value = _engineState.value.copy(isMonitoringOutput = enabled)
        }
    }

    fun setEffect(effect: VoiceEffect) {
        dsp.setEffect(effect)
        _engineState.value = _engineState.value.copy(
            activeEffect = effect,
            intensity = effect.defaultIntensity,
            pitchFactor = effect.defaultPitchFactor,
            depth = effect.defaultDepth
        )
    }

    fun setIntensity(value: Float) {
        val clamped = value.coerceIn(0.0f, 1.0f)
        dsp.intensity = clamped
        _engineState.value = _engineState.value.copy(intensity = clamped)
    }

    fun setPitchFactor(value: Float) {
        val clamped = value.coerceIn(0.4f, 2.2f)
        dsp.pitchFactor = clamped
        _engineState.value = _engineState.value.copy(pitchFactor = clamped)
    }

    fun setDepth(value: Float) {
        val clamped = value.coerceIn(0.0f, 1.0f)
        dsp.depth = clamped
        _engineState.value = _engineState.value.copy(depth = clamped)
    }

    fun setMasterVolume(value: Float) {
        val clamped = value.coerceIn(0.0f, 1.5f)
        userVolumeSetting = clamped
        dsp.masterVolume = clamped
        _engineState.value = _engineState.value.copy(masterVolume = clamped)
    }

    fun clearError() {
        _engineState.value = _engineState.value.copy(errorMessage = null)
    }

    /**
     * Start live voice listening (Mic -> DSP -> Speaker/Headphones).
     */
    fun start(): Boolean {
        wasStartedJustForRecording = false
        liveMonitoringEnabled = true
        if (isAudioRunning.get()) {
            _engineState.value = _engineState.value.copy(isMonitoringOutput = true)
            return true
        }
        return startInternal(enableLiveOutput = true)
    }

    @SuppressLint("MissingPermission")
    @Synchronized
    private fun startInternal(enableLiveOutput: Boolean): Boolean {
        if (isAudioRunning.get()) return true

        try {
            // Ensure normal media routing and unmuted microphone
            if (audioManager.mode != AudioManager.MODE_NORMAL) {
                audioManager.mode = AudioManager.MODE_NORMAL
            }
            if (audioManager.isMicrophoneMute) {
                try {
                    audioManager.isMicrophoneMute = false
                } catch (_: SecurityException) {
                    // Ignored on restricted devices
                }
            }

            requestAudioFocus()

            val nativeRate = deviceHelper.getNativeSampleRate()
            val candidateRates = listOf(preferredSampleRate, nativeRate, 48000, 44100, 16000, 22050).distinct()

            var selectedRate = 44100
            var record: AudioRecord? = null
            var track: AudioTrack? = null
            var chunkSize = 1024

            for (rate in candidateRates) {
                val minRec = AudioRecord.getMinBufferSize(
                    rate,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT
                )
                val minTrack = AudioTrack.getMinBufferSize(
                    rate,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT
                )
                if (minRec <= 0 || minTrack <= 0) continue

                chunkSize = if (rate >= 44100) 1024 else 512
                val recBufBytes = (minRec * 2).coerceAtLeast(chunkSize * 8)
                val trackBufBytes = (minTrack * 2).coerceAtLeast(chunkSize * 6)

                val candidateRecord = createBestAudioRecord(rate, recBufBytes)
                if (candidateRecord == null) continue

                val candidateTrack = createCompatibleAudioTrack(rate, trackBufBytes)
                if (candidateTrack == null) {
                    candidateRecord.release()
                    continue
                }

                selectedRate = rate
                record = candidateRecord
                track = candidateTrack
                break
            }

            if (record == null || track == null) {
                abandonAudioFocus()
                _engineState.value = _engineState.value.copy(
                    isRunning = false,
                    isMonitoringOutput = false,
                    errorMessage = "Impossible d'initialiser le microphone ou la sortie audio sur cet appareil."
                )
                return false
            }

            activeSampleRate = selectedRate
            dsp.sampleRate = selectedRate.toFloat()
            dsp.masterVolume = userVolumeSetting
            dsp.speakerAntiFeedback = antiFeedbackEnabled && !_engineState.value.isHeadphonesConnected
            dsp.reset()

            record.startRecording()
            if (record.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
                record.release()
                track.release()
                abandonAudioFocus()
                _engineState.value = _engineState.value.copy(
                    isRunning = false,
                    isMonitoringOutput = false,
                    errorMessage = "Le microphone est occupé par une autre application ou refusé par le système."
                )
                return false
            }

            track.play()

            audioRecord = record
            audioTrack = track
            liveMonitoringEnabled = enableLiveOutput
            isAudioRunning.set(true)

            val estimatedLatency = ((chunkSize.toFloat() / selectedRate) * 1000f * 1.5f).toInt()
            _engineState.value = _engineState.value.copy(
                isRunning = true,
                isMonitoringOutput = enableLiveOutput,
                sampleRate = selectedRate,
                latencyMs = estimatedLatency,
                errorMessage = null,
                sessionDurationSeconds = 0
            )

            startTimer()

            val activeChunkSize = chunkSize
            audioThread = Thread({
                Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)

                val inputBuffer = ShortArray(activeChunkSize)
                val outputBuffer = ShortArray(activeChunkSize)
                var consecutiveEmptyReads = 0

                while (isAudioRunning.get()) {
                    val readCount = try {
                        record.read(inputBuffer, 0, activeChunkSize)
                    } catch (e: Exception) {
                        Log.e("AudioEngine", "AudioRecord read exception", e)
                        -1
                    }

                    if (readCount > 0) {
                        consecutiveEmptyReads = 0

                        // Compute raw input peak for VU meter
                        var rawPeak = 0
                        for (i in 0 until readCount) {
                            val a = abs(inputBuffer[i].toInt())
                            if (a > rawPeak) rawPeak = a
                        }
                        val normalizedInputPeak = (rawPeak / 32768.0f).coerceIn(0f, 1f)

                        // Apply DSP vocal transformation
                        val outputPeak = dsp.processBlock(inputBuffer, outputBuffer, readCount)
                        visualizer.updateWithPeak(maxOf(outputPeak, normalizedInputPeak * 0.6f))
                        _engineState.value = _engineState.value.copy(micInputLevel = normalizedInputPeak)

                        // Stream transformed PCM to WAV recorder if recording
                        if (recorder.recordingActive) {
                            recorder.writeChunk(outputBuffer, readCount)
                        }

                        // Output live audio to speaker / headphones when monitoring is enabled
                        if (liveMonitoringEnabled) {
                            val written = try {
                                track.write(outputBuffer, 0, readCount)
                            } catch (e: Exception) {
                                Log.w("AudioEngine", "AudioTrack write exception", e)
                                -1
                            }
                            if (written < 0) {
                                Log.w("AudioEngine", "AudioTrack write returned $written")
                            }
                        }
                    } else if (readCount == 0) {
                        consecutiveEmptyReads++
                        if (consecutiveEmptyReads > 100) {
                            Log.e("AudioEngine", "Too many consecutive 0-byte reads from microphone")
                            handleAudioThreadError("Le microphone ne renvoie aucun signal audio.")
                            break
                        }
                        try {
                            Thread.sleep(5)
                        } catch (_: InterruptedException) {
                            break
                        }
                    } else {
                        Log.e("AudioEngine", "AudioRecord read error code: $readCount")
                        handleAudioThreadError("Interruption du flux microphone (code $readCount).")
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

    @SuppressLint("MissingPermission")
    private fun createBestAudioRecord(sampleRate: Int, bufferBytes: Int): AudioRecord? {
        // Prefer MIC (raw clean audio without VoIP AEC muting the speaker loop), then DEFAULT, then VOICE_RECOGNITION
        val sources = listOf(
            MediaRecorder.AudioSource.MIC,
            MediaRecorder.AudioSource.DEFAULT,
            MediaRecorder.AudioSource.VOICE_RECOGNITION
        )
        for (source in sources) {
            try {
                val rec = AudioRecord(
                    source,
                    sampleRate,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    bufferBytes
                )
                if (rec.state == AudioRecord.STATE_INITIALIZED) {
                    Log.d("AudioEngine", "Initialized AudioRecord with source=$source at $sampleRate Hz")
                    return rec
                }
                rec.release()
            } catch (e: Exception) {
                Log.w("AudioEngine", "Failed AudioRecord source=$source at $sampleRate Hz", e)
            }
        }
        return null
    }

    private fun createCompatibleAudioTrack(sampleRate: Int, bufferBytes: Int): AudioTrack? {
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()

        val audioFormat = AudioFormat.Builder()
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .setSampleRate(sampleRate)
            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Try Low Latency first, then fall back to standard mode if unsupported
            for (perfMode in listOf(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY, AudioTrack.PERFORMANCE_MODE_NONE)) {
                try {
                    val track = AudioTrack.Builder()
                        .setAudioAttributes(audioAttributes)
                        .setAudioFormat(audioFormat)
                        .setBufferSizeInBytes(bufferBytes)
                        .setTransferMode(AudioTrack.MODE_STREAM)
                        .setPerformanceMode(perfMode)
                        .build()
                    if (track.state == AudioTrack.STATE_INITIALIZED) {
                        Log.d("AudioEngine", "Initialized AudioTrack perfMode=$perfMode at $sampleRate Hz")
                        return track
                    }
                    track.release()
                } catch (e: Exception) {
                    Log.w("AudioEngine", "AudioTrack init failed perfMode=$perfMode at $sampleRate Hz", e)
                }
            }
        } else {
            try {
                val track = AudioTrack.Builder()
                    .setAudioAttributes(audioAttributes)
                    .setAudioFormat(audioFormat)
                    .setBufferSizeInBytes(bufferBytes)
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build()
                if (track.state == AudioTrack.STATE_INITIALIZED) {
                    return track
                }
                track.release()
            } catch (e: Exception) {
                Log.w("AudioEngine", "AudioTrack pre-O init failed at $sampleRate Hz", e)
            }
        }
        return null
    }

    private fun handleAudioThreadError(message: String) {
        scope.launch {
            _engineState.value = _engineState.value.copy(errorMessage = message)
            stop()
        }
    }

    @Synchronized
    fun stop() {
        val wasRecording = recorder.recordingActive
        if (!isAudioRunning.get() && !wasRecording) {
            liveMonitoringEnabled = false
            _engineState.value = _engineState.value.copy(
                isRunning = false,
                isMonitoringOutput = false,
                isRecording = false,
                micInputLevel = 0f
            )
            return
        }

        isAudioRunning.set(false)
        liveMonitoringEnabled = false
        wasStartedJustForRecording = false

        if (wasRecording) {
            recorder.stopRecording()
        }

        timerJob?.cancel()
        timerJob = null

        val thread = audioThread
        audioThread = null
        if (thread != null && thread !== Thread.currentThread()) {
            try {
                thread.join(600)
            } catch (e: Exception) {
                Log.e("AudioEngine", "Error waiting for audio thread", e)
            }
        }

        try {
            audioRecord?.apply {
                if (state == AudioRecord.STATE_INITIALIZED && recordingState == AudioRecord.RECORDSTATE_RECORDING) {
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
                if (state == AudioTrack.STATE_INITIALIZED && playState == AudioTrack.PLAYSTATE_PLAYING) {
                    stop()
                }
                release()
            }
        } catch (e: Exception) {
            Log.e("AudioEngine", "Error releasing AudioTrack", e)
        } finally {
            audioTrack = null
        }

        abandonAudioFocus()
        visualizer.reset()
        _engineState.value = _engineState.value.copy(
            isRunning = false,
            isMonitoringOutput = false,
            isRecording = false,
            sessionDurationSeconds = 0,
            recordingDurationSeconds = 0,
            micInputLevel = 0f
        )
    }

    /**
     * Start recording transformed voice to WAV.
     * Works whether live listening is already running OR directly from standby (with or without headphones).
     */
    fun startRecording(): File? {
        if (!isAudioRunning.get()) {
            wasStartedJustForRecording = true
            val shouldMonitor = _engineState.value.isHeadphonesConnected || _engineState.value.monitorDuringRecording
            val started = startInternal(enableLiveOutput = shouldMonitor)
            if (!started) {
                wasStartedJustForRecording = false
                return null
            }
        } else {
            wasStartedJustForRecording = false
        }

        val file = recorder.startRecording(
            effectName = _engineState.value.activeEffect.name,
            sampleRate = activeSampleRate
        )
        if (file != null) {
            _engineState.value = _engineState.value.copy(
                isRecording = true,
                recordingDurationSeconds = 0,
                errorMessage = null
            )
        } else {
            _engineState.value = _engineState.value.copy(
                errorMessage = "Impossible de créer le fichier d'enregistrement WAV."
            )
            if (wasStartedJustForRecording) {
                stop()
            }
        }
        return file
    }

    /**
     * Stop WAV recording and return the saved file result.
     * Automatically stops the microphone if it was started solely for this recording.
     */
    fun stopRecording(): AudioRecorder.RecordingResult? {
        val result = if (recorder.recordingActive) {
            recorder.stopRecording()
        } else {
            null
        }

        _engineState.value = _engineState.value.copy(
            isRecording = false,
            recordingDurationSeconds = 0
        )

        if (wasStartedJustForRecording) {
            wasStartedJustForRecording = false
            stop()
        }

        if (result == null) {
            _engineState.value = _engineState.value.copy(
                errorMessage = "Enregistrement trop court ou aucun signal audio capturé."
            )
        }

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

    private fun requestAudioFocus() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
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
                    AudioManager.AUDIOFOCUS_GAIN
                )
            }
        } catch (e: Exception) {
            Log.w("AudioEngine", "AudioFocus request failed", e)
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
            Log.w("AudioEngine", "AudioFocus abandon failed", e)
        }
    }

    fun release() {
        stop()
        deviceHelper.stopListening()
    }
}
