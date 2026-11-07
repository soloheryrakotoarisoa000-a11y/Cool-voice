package com.example.audio.effects

import kotlin.math.abs

/**
 * High-performance real-time Overlap-Add Pitch Shifter.
 * Uses dual-grain overlapping with perfect complementary windowing (Unity Gain).
 * Designed for real-time mobile audio: zero memory allocation during processing,
 * low latency, and smooth pitch scaling from 0.4x to 2.5x.
 */
class GranularPitchShifter(
    private val grainWindowSize: Int = 1024 // ~23ms at 44.1kHz, excellent balance of vocal timbre & latency
) {
    private val bufferSize = grainWindowSize * 4
    private val circularBuffer = FloatArray(bufferSize)
    private var writeHead = 0

    // Grain phase offsets [0, grainWindowSize)
    private var phase0 = 0.0f
    private var phase1 = (grainWindowSize / 2).toFloat()

    fun reset() {
        circularBuffer.fill(0.0f)
        writeHead = 0
        phase0 = 0.0f
        phase1 = (grainWindowSize / 2).toFloat()
    }

    /**
     * Process a single audio sample with the target pitch ratio.
     * @param input Raw input sample [-1.0f .. 1.0f]
     * @param pitchRatio Scale factor (e.g., 0.7f for deep, 1.4f for high)
     * @return Transposed output sample
     */
    fun process(input: Float, pitchRatio: Float): Float {
        // Write current sample into circular buffer
        circularBuffer[writeHead] = input

        val windowF = grainWindowSize.toFloat()

        // Calculate normalized phase [0..1)
        val normPhase0 = phase0 / windowF
        val normPhase1 = phase1 / windowF

        // Complementary triangular window weights: w0 + w1 == 1.0 everywhere
        val w0 = 1.0f - abs(2.0f * normPhase0 - 1.0f)
        val w1 = 1.0f - abs(2.0f * normPhase1 - 1.0f)

        // Read positions for both grains relative to writeHead
        val sample0 = readInterpolated(writeHead - phase0)
        val sample1 = readInterpolated(writeHead - phase1)

        val output = sample0 * w0 + sample1 * w1

        // Advance write head
        writeHead++
        if (writeHead >= bufferSize) {
            writeHead = 0
        }

        // Advance grain phases based on pitch shift delta
        // If pitchRatio == 1.0, phase does not advance relative to time
        val delta = 1.0f - pitchRatio
        phase0 += delta
        phase1 += delta

        // Wrap phases within [0, grainWindowSize)
        while (phase0 < 0.0f) phase0 += windowF
        while (phase0 >= windowF) phase0 -= windowF

        while (phase1 < 0.0f) phase1 += windowF
        while (phase1 >= windowF) phase1 -= windowF

        return output
    }

    /**
     * Read from circular buffer with fractional linear interpolation
     */
    private fun readInterpolated(rawPos: Float): Float {
        var pos = rawPos
        while (pos < 0.0f) pos += bufferSize
        while (pos >= bufferSize) pos -= bufferSize

        val index0 = pos.toInt()
        val frac = pos - index0
        val index1 = if (index0 + 1 >= bufferSize) 0 else index0 + 1

        return circularBuffer[index0] * (1.0f - frac) + circularBuffer[index1] * frac
    }
}
