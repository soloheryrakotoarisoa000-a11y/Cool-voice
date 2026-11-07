package com.example.audio.effects

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Standard second-order IIR Biquad Filter (Direct Form I / II).
 * Zero allocations in the process loop.
 */
class BiquadFilter {

    private var b0 = 1.0f
    private var b1 = 0.0f
    private var b2 = 0.0f
    private var a1 = 0.0f
    private var a2 = 0.0f

    // State variables
    private var x1 = 0.0f
    private var x2 = 0.0f
    private var y1 = 0.0f
    private var y2 = 0.0f

    fun reset() {
        x1 = 0.0f
        x2 = 0.0f
        y1 = 0.0f
        y2 = 0.0f
    }

    /**
     * Configure as Low-Pass Filter
     */
    fun setLowPass(sampleRate: Float, cutoffFreq: Float, q: Float = 0.707f) {
        val w0 = (2.0 * PI * cutoffFreq / sampleRate).toFloat().coerceIn(0.001f, (PI - 0.01).toFloat())
        val alpha = (sin(w0.toDouble()) / (2.0 * q)).toFloat()
        val cosW0 = cos(w0.toDouble()).toFloat()

        val a0 = 1.0f + alpha
        b0 = ((1.0f - cosW0) / 2.0f) / a0
        b1 = (1.0f - cosW0) / a0
        b2 = ((1.0f - cosW0) / 2.0f) / a0
        a1 = (-2.0f * cosW0) / a0
        a2 = (1.0f - alpha) / a0
    }

    /**
     * Configure as High-Pass Filter
     */
    fun setHighPass(sampleRate: Float, cutoffFreq: Float, q: Float = 0.707f) {
        val w0 = (2.0 * PI * cutoffFreq / sampleRate).toFloat().coerceIn(0.001f, (PI - 0.01).toFloat())
        val alpha = (sin(w0.toDouble()) / (2.0 * q)).toFloat()
        val cosW0 = cos(w0.toDouble()).toFloat()

        val a0 = 1.0f + alpha
        b0 = ((1.0f + cosW0) / 2.0f) / a0
        b1 = (-(1.0f + cosW0)) / a0
        b2 = ((1.0f + cosW0) / 2.0f) / a0
        a1 = (-2.0f * cosW0) / a0
        a2 = (1.0f - alpha) / a0
    }

    /**
     * Configure as Band-Pass Filter (constant skirt gain)
     */
    fun setBandPass(sampleRate: Float, centerFreq: Float, q: Float = 1.0f) {
        val w0 = (2.0 * PI * centerFreq / sampleRate).toFloat().coerceIn(0.001f, (PI - 0.01).toFloat())
        val alpha = (sin(w0.toDouble()) / (2.0 * q)).toFloat()
        val cosW0 = cos(w0.toDouble()).toFloat()

        val a0 = 1.0f + alpha
        b0 = (alpha * q) / a0
        b1 = 0.0f
        b2 = -(alpha * q) / a0
        a1 = (-2.0f * cosW0) / a0
        a2 = (1.0f - alpha) / a0
    }

    /**
     * Configure as Peaking EQ
     */
    fun setPeaking(sampleRate: Float, centerFreq: Float, gainDb: Float, q: Float = 1.0f) {
        val a = Math.pow(10.0, (gainDb / 40.0).toDouble()).toFloat()
        val w0 = (2.0 * PI * centerFreq / sampleRate).toFloat().coerceIn(0.001f, (PI - 0.01).toFloat())
        val alpha = (sin(w0.toDouble()) / (2.0 * q)).toFloat()
        val cosW0 = cos(w0.toDouble()).toFloat()

        val a0 = 1.0f + alpha / a
        b0 = (1.0f + alpha * a) / a0
        b1 = (-2.0f * cosW0) / a0
        b2 = (1.0f - alpha * a) / a0
        a1 = (-2.0f * cosW0) / a0
        a2 = (1.0f - alpha / a) / a0
    }

    /**
     * Process a single sample
     */
    fun process(input: Float): Float {
        val out = b0 * input + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2
        x2 = x1
        x1 = input
        y2 = y1
        y1 = out
        return out
    }
}
