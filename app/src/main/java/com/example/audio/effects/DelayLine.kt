package com.example.audio.effects

/**
 * Circular buffer delay line for real-time delay and echo.
 * Zero allocations in the process loop.
 */
class DelayLine(maxDelaySamples: Int = 44100) {

    private val buffer = FloatArray(maxDelaySamples)
    private var writeIndex = 0
    private val bufferSize = maxDelaySamples

    fun reset() {
        buffer.fill(0.0f)
        writeIndex = 0
    }

    /**
     * Write input and return delayed sample with linear interpolation for non-integer delay.
     */
    fun process(input: Float, delaySamples: Float, feedback: Float): Float {
        // Read position
        var readPos = writeIndex - delaySamples
        while (readPos < 0) {
            readPos += bufferSize
        }
        while (readPos >= bufferSize) {
            readPos -= bufferSize
        }

        val index0 = readPos.toInt()
        val frac = readPos - index0
        val index1 = if (index0 + 1 >= bufferSize) 0 else index0 + 1

        val delayedSample = buffer[index0] * (1.0f - frac) + buffer[index1] * frac

        // Write feedback + input into buffer
        val toWrite = input + delayedSample * feedback
        // Soft saturate internal buffer to prevent runaway feedback
        val clamped = toWrite.coerceIn(-2.0f, 2.0f)
        buffer[writeIndex] = clamped

        writeIndex++
        if (writeIndex >= bufferSize) {
            writeIndex = 0
        }

        return delayedSample
    }
}
