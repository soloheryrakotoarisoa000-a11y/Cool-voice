package com.example

import com.example.audio.effects.GranularPitchShifter
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin

class GranularPitchShifterTest {

    @Test
    fun testPitchShiftUpAndDown() {
        val shifter = GranularPitchShifter(grainWindowSize = 1024)
        val numSamples = 2048

        // Pitch Shift Up (e.g. 1.5x)
        var outputEnergyUp = 0.0
        for (i in 0 until numSamples) {
            val angle = 2.0 * PI * 440.0 * i / 44100.0
            val input = sin(angle).toFloat()
            val out = shifter.process(input, pitchRatio = 1.5f)
            outputEnergyUp += (out * out)
            assertFalse("Output should not be NaN", out.isNaN())
            assertFalse("Output should not be infinite", out.isInfinite())
        }
        assertTrue("Output energy for pitch shift up must be positive", outputEnergyUp > 0.0)

        // Pitch Shift Down (e.g. 0.7x)
        shifter.reset()
        var outputEnergyDown = 0.0
        for (i in 0 until numSamples) {
            val angle = 2.0 * PI * 440.0 * i / 44100.0
            val input = sin(angle).toFloat()
            val out = shifter.process(input, pitchRatio = 0.7f)
            outputEnergyDown += (out * out)
            assertFalse("Output should not be NaN", out.isNaN())
        }
        assertTrue("Output energy for pitch shift down must be positive", outputEnergyDown > 0.0)
    }
}
