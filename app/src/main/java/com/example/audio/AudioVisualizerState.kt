package com.example.audio

import android.os.SystemClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Manages audio waveform visualization samples with UI-friendly throttling (~25-30 FPS).
 */
class AudioVisualizerState(val barCount: Int = 32) {

    private val samples = FloatArray(barCount) { 0.05f }
    private val _visualizerData = MutableStateFlow(samples.clone())
    val visualizerData: StateFlow<FloatArray> = _visualizerData.asStateFlow()

    private val _currentPeak = MutableStateFlow(0f)
    val currentPeak: StateFlow<Float> = _currentPeak.asStateFlow()

    private var lastEmitMs = 0L

    fun updateWithPeak(peak: Float) {
        val target = peak.coerceIn(0.04f, 1.0f)
        // Shift left
        System.arraycopy(samples, 1, samples, 0, barCount - 1)
        val prev = samples[barCount - 2]
        samples[barCount - 1] = prev * 0.30f + target * 0.70f

        val now = SystemClock.uptimeMillis()
        if (now - lastEmitMs >= 33L) {
            lastEmitMs = now
            _currentPeak.value = peak
            _visualizerData.value = samples.clone()
        }
    }

    fun reset() {
        samples.fill(0.04f)
        _currentPeak.value = 0f
        _visualizerData.value = samples.clone()
    }
}
