package com.example

import com.example.audio.effects.DspProcessor
import com.example.audio.effects.VoiceEffect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin

class DspProcessorTest {

    private lateinit var dsp: DspProcessor

    @Before
    fun setUp() {
        dsp = DspProcessor(sampleRate = 44100.0f)
    }

    @Test
    fun testAllTwelveEffectsExist() {
        val effects = VoiceEffect.values()
        assertEquals("There must be exactly 12 vocal effects", 12, effects.size)

        val expectedIds = setOf(
            "robot", "femme", "homme", "enfant", "monstre", "grave",
            "aigue", "echo", "extraterrestre", "dessin_anime", "personne_agee", "bebe"
        )
        val actualIds = effects.map { it.id }.toSet()
        assertEquals("All 12 effect IDs must match specifications", expectedIds, actualIds)
    }

    @Test
    fun testEachEffectTransformsSignal() {
        val bufferSize = 512
        val input = ShortArray(bufferSize)
        val output = ShortArray(bufferSize)

        // Generate a 440Hz test sine wave
        for (i in 0 until bufferSize) {
            val angle = 2.0 * PI * 440.0 * i / 44100.0
            input[i] = (sin(angle) * 16000.0).toInt().toShort()
        }

        // Test every single effect
        for (effect in VoiceEffect.values()) {
            dsp.reset()
            dsp.setEffect(effect)
            output.fill(0)

            val peak = dsp.processBlock(input, output, bufferSize)

            assertTrue("Effect ${effect.name} must output a non-zero peak", peak > 0.0f)
            assertTrue("Effect ${effect.name} peak must be <= 1.0f", peak <= 1.0f)

            // Ensure output buffer contains audio data
            var hasNonZero = false
            for (sample in output) {
                if (sample.toInt() != 0) {
                    hasNonZero = true
                    break
                }
            }
            assertTrue("Effect ${effect.name} must produce non-zero audio samples", hasNonZero)
        }
    }

    @Test
    fun testDifferentEffectsProduceDifferentOutputs() {
        val bufferSize = 512
        val input = ShortArray(bufferSize)
        for (i in 0 until bufferSize) {
            val angle = 2.0 * PI * 300.0 * i / 44100.0
            input[i] = (sin(angle) * 20000.0).toInt().toShort()
        }

        val outputRobot = ShortArray(bufferSize)
        dsp.reset()
        dsp.setEffect(VoiceEffect.ROBOT)
        dsp.processBlock(input, outputRobot, bufferSize)

        val outputMonster = ShortArray(bufferSize)
        dsp.reset()
        dsp.setEffect(VoiceEffect.MONSTRE)
        dsp.processBlock(input, outputMonster, bufferSize)

        val outputEcho = ShortArray(bufferSize)
        dsp.reset()
        dsp.setEffect(VoiceEffect.ECHO)
        dsp.processBlock(input, outputEcho, bufferSize)

        // Check difference between Robot and Monster
        var robotMonsterDiff = 0L
        for (i in 0 until bufferSize) {
            robotMonsterDiff += kotlin.math.abs(outputRobot[i] - outputMonster[i])
        }
        assertTrue("ROBOT and MONSTRE must produce distinctly different signals", robotMonsterDiff > 1000)

        // Check difference between Robot and Echo
        var robotEchoDiff = 0L
        for (i in 0 until bufferSize) {
            robotEchoDiff += kotlin.math.abs(outputRobot[i] - outputEcho[i])
        }
        assertTrue("ROBOT and ECHO must produce distinctly different signals", robotEchoDiff > 1000)
    }

    @Test
    fun testParameterAdjustmentsDoNotCrash() {
        val bufferSize = 256
        val input = ShortArray(bufferSize) { (it * 10).toShort() }
        val output = ShortArray(bufferSize)

        dsp.setEffect(VoiceEffect.FEMME)
        dsp.intensity = 0.2f
        dsp.pitchFactor = 1.8f
        dsp.depth = 0.9f
        dsp.masterVolume = 0.5f

        dsp.processBlock(input, output, bufferSize)

        dsp.intensity = 1.0f
        dsp.pitchFactor = 0.5f
        dsp.processBlock(input, output, bufferSize)

        assertEquals(0.5f, dsp.pitchFactor, 0.001f)
        assertEquals(1.0f, dsp.intensity, 0.001f)
    }
}
