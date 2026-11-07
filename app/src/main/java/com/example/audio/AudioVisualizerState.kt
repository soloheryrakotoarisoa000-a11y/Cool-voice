package com.example.audio

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Manages audio waveform visualization samples.
 */
class AudioVisualizerState(val barCount: Int = 36) {

    private val samples = FloatArray(barCount) { 0.05f }
    private val _visualizerData = MutableStateFlow(samples.clone())
    val visualizerData: StateFlow<FloatArray> = _visualizerData.asStateFlow()

    private var sampleCounter = 0

    fun updateWithPeak(peak: Float) {
        sampleCounter++
        // Shift left
        System.arraycopy(samples, 1, samples, 0, barCount - 1)
        // Add new sample with gentle smoothing
        val target = peak.coerceIn(0.04f, 1.0f)
        val prev = samples[barCount - 2]
        samples[barCount - 1] = prev * 0.35f + target * 0.65f

        _visualizerData.value = samples.clone()
    }

    fun reset() {
        samples.fill(0.04f)
        _visualizerData.value = samples.clone()
    }
}
