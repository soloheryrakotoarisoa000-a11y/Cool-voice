package com.example.audio.effects

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.math.tanh

/**
 * Real-time Audio DSP Processor implementing the 12 distinct vocal transformation effects.
 * Zero-allocation inside the processing loop to guarantee low latency and prevent GC stutter.
 */
class DspProcessor(
    var sampleRate: Float = 44100.0f
) {
    // Current effect and adjustable parameters
    @Volatile var activeEffect: VoiceEffect = VoiceEffect.ROBOT
    @Volatile var intensity: Float = VoiceEffect.ROBOT.defaultIntensity      // 0.0f .. 1.0f
    @Volatile var pitchFactor: Float = VoiceEffect.ROBOT.defaultPitchFactor // Direct target pitch ratio (0.4x .. 2.2x)
    @Volatile var depth: Float = VoiceEffect.ROBOT.defaultDepth              // 0.0f .. 1.0f
    @Volatile var masterVolume: Float = 1.0f                                 // 0.0f .. 1.5f
    @Volatile var speakerAntiFeedback: Boolean = false                       // Enables adaptive noise gate & soft limiter for speaker mode

    // DSP components (sized to support up to 96kHz safely)
    private val pitchShifter = GranularPitchShifter(grainWindowSize = 1024)
    private val delayLine1 = DelayLine(maxDelaySamples = 96000)
    private val delayLine2 = DelayLine(maxDelaySamples = 96000)
    private val filter1 = BiquadFilter()
    private val filter2 = BiquadFilter()
    private val speakerHighPass = BiquadFilter()

    // Oscillators / LFO phases
    private var lfoPhase1 = 0.0f
    private var lfoPhase2 = 0.0f
    private var vibratoPhase = 0.0f

    // Adaptive noise gate envelope state
    private var gateEnvelope = 0.0f
    private var gateGain = 1.0f

    init {
        configureFiltersForCurrentEffect()
    }

    fun reset() {
        pitchShifter.reset()
        delayLine1.reset()
        delayLine2.reset()
        filter1.reset()
        filter2.reset()
        speakerHighPass.reset()
        lfoPhase1 = 0.0f
        lfoPhase2 = 0.0f
        vibratoPhase = 0.0f
        gateEnvelope = 0.0f
        gateGain = 1.0f
        configureFiltersForCurrentEffect()
    }

    /**
     * Update active effect and load its default parameters cleanly.
     */
    fun setEffect(effect: VoiceEffect) {
        if (activeEffect != effect) {
            activeEffect = effect
            pitchFactor = effect.defaultPitchFactor
            intensity = effect.defaultIntensity
            depth = effect.defaultDepth
            configureFiltersForCurrentEffect()
        }
    }

    fun configureFiltersForCurrentEffect() {
        speakerHighPass.setHighPass(sampleRate, 95.0f, q = 0.707f)

        when (activeEffect) {
            VoiceEffect.ROBOT -> {
                filter1.setBandPass(sampleRate, 1200.0f, 1.1f)
                filter2.setHighPass(sampleRate, 260.0f)
            }
            VoiceEffect.FEMME -> {
                filter1.setPeaking(sampleRate, 2600.0f, gainDb = 5.0f, q = 1.1f)
                filter2.setHighPass(sampleRate, 180.0f)
            }
            VoiceEffect.HOMME -> {
                filter1.setPeaking(sampleRate, 210.0f, gainDb = 6.0f, q = 1.0f)
                filter2.setLowPass(sampleRate, 4200.0f)
            }
            VoiceEffect.ENFANT -> {
                filter1.setHighPass(sampleRate, 220.0f)
                filter2.setPeaking(sampleRate, 3200.0f, gainDb = 4.5f, q = 1.0f)
            }
            VoiceEffect.MONSTRE -> {
                filter1.setLowPass(sampleRate, 2400.0f, q = 0.9f)
                filter2.setPeaking(sampleRate, 150.0f, gainDb = 7.0f, q = 1.4f)
            }
            VoiceEffect.GRAVE -> {
                filter1.setLowPass(sampleRate, 2400.0f)
                filter2.setPeaking(sampleRate, 180.0f, gainDb = 5.5f, q = 1.0f)
            }
            VoiceEffect.AIGUE -> {
                filter1.setHighPass(sampleRate, 280.0f)
                filter2.setPeaking(sampleRate, 3800.0f, gainDb = 5.5f, q = 1.0f)
            }
            VoiceEffect.ECHO -> {
                filter1.setHighPass(sampleRate, 120.0f)
                filter2.setLowPass(sampleRate, 6000.0f)
            }
            VoiceEffect.EXTRATERRESTRE -> {
                filter1.setBandPass(sampleRate, 1750.0f, 1.6f)
                filter2.setHighPass(sampleRate, 220.0f)
            }
            VoiceEffect.DESSIN_ANIME -> {
                filter1.setHighPass(sampleRate, 200.0f)
                filter2.setPeaking(sampleRate, 2800.0f, gainDb = 5.0f, q = 1.1f)
            }
            VoiceEffect.PERSONNE_AGEE -> {
                filter1.setHighPass(sampleRate, 360.0f)
                filter2.setLowPass(sampleRate, 3300.0f)
            }
            VoiceEffect.BEBE -> {
                filter1.setHighPass(sampleRate, 260.0f)
                filter2.setPeaking(sampleRate, 3400.0f, gainDb = 4.5f, q = 1.1f)
            }
        }
    }

    /**
     * Process an entire block of 16-bit PCM samples.
     * @param input Raw microphone PCM shorts
     * @param output Destination buffer for transformed audio
     * @param length Number of samples to process
     * @return Peak output amplitude [0.0f .. 1.0f] for real-time visualizer
     */
    fun processBlock(input: ShortArray, output: ShortArray, length: Int): Float {
        val effect = activeEffect
        val intVal = intensity.coerceIn(0.0f, 1.0f)
        val depVal = depth.coerceIn(0.0f, 1.0f)
        val pFactor = pitchFactor.coerceIn(0.4f, 2.3f)
        val vol = masterVolume.coerceIn(0.0f, 2.0f)
        val antiFeedback = speakerAntiFeedback

        var peakAmplitude = 0.0f
        val twoPi = (2.0 * PI).toFloat()
        val invSampleRate = 1.0f / sampleRate.coerceAtLeast(8000.0f)

        // Pre-gain for clean raw AudioSource.MIC signals (~+3dB)
        val micPreGain = 1.45f

        for (i in 0 until length) {
            val rawSample = (input[i] / 32768.0f) * micPreGain

            // Remove sub-sonic DC rumble and apply adaptive gate in speaker mode
            var inSample = speakerHighPass.process(rawSample)
            if (antiFeedback) {
                val absIn = abs(inSample)
                // Fast attack, smooth release envelope follower
                gateEnvelope = if (absIn > gateEnvelope) {
                    gateEnvelope * 0.85f + absIn * 0.15f
                } else {
                    gateEnvelope * 0.9992f + absIn * 0.0008f
                }
                // Soft noise gate threshold (~ -36 dBFS) prevents ambient feedback buildup
                val targetGate = if (gateEnvelope > 0.014f) 1.0f else 0.12f
                gateGain = gateGain * 0.985f + targetGate * 0.015f
                inSample *= gateGain
            }

            var wetSample = inSample

            when (effect) {
                VoiceEffect.ROBOT -> {
                    val carrierFreq = 65.0f + depVal * 45.0f
                    val carrier = sin(lfoPhase1)
                    lfoPhase1 += twoPi * carrierFreq * invSampleRate
                    if (lfoPhase1 >= twoPi) lfoPhase1 -= twoPi

                    val modulated = inSample * carrier * 1.55f
                    val clipped = softClip(modulated * (1.0f + intVal * 2.2f))
                    wetSample = filter1.process(clipped)
                }

                VoiceEffect.FEMME -> {
                    val targetPitch = pFactor.coerceIn(1.05f, 2.1f)
                    val shifted = pitchShifter.process(inSample, targetPitch)
                    val filtered = filter1.process(shifted)
                    wetSample = filter2.process(filtered)
                }

                VoiceEffect.HOMME -> {
                    val targetPitch = pFactor.coerceIn(0.45f, 0.98f)
                    val shifted = pitchShifter.process(inSample, targetPitch)
                    val filtered = filter1.process(shifted)
                    wetSample = filter2.process(filtered)
                }

                VoiceEffect.ENFANT -> {
                    val targetPitch = pFactor.coerceIn(1.05f, 2.2f)
                    val shifted = pitchShifter.process(inSample, targetPitch)
                    val filtered = filter1.process(shifted)
                    wetSample = filter2.process(filtered)
                }

                VoiceEffect.MONSTRE -> {
                    val targetPitch = pFactor.coerceIn(0.40f, 0.95f)
                    val shifted = pitchShifter.process(inSample, targetPitch)

                    val growlMod = 0.82f + 0.18f * sin(lfoPhase1)
                    lfoPhase1 += twoPi * 9.5f * invSampleRate
                    if (lfoPhase1 >= twoPi) lfoPhase1 -= twoPi

                    val saturated = softClip(shifted * (1.25f + depVal * 1.8f)) * growlMod
                    val filtered = filter1.process(saturated)
                    wetSample = filter2.process(filtered)
                }

                VoiceEffect.GRAVE -> {
                    val targetPitch = pFactor.coerceIn(0.45f, 0.95f)
                    val shifted = pitchShifter.process(inSample, targetPitch)
                    val filtered = filter1.process(shifted)
                    wetSample = filter2.process(filtered)
                }

                VoiceEffect.AIGUE -> {
                    val targetPitch = pFactor.coerceIn(1.10f, 2.3f)
                    val shifted = pitchShifter.process(inSample, targetPitch)
                    val filtered = filter1.process(shifted)
                    wetSample = filter2.process(filtered)
                }

                VoiceEffect.ECHO -> {
                    val delay1Samples = ((0.22f * depVal + 0.06f) * sampleRate).coerceIn(200.0f, 95000.0f)
                    val feedback1 = (0.48f * intVal).coerceIn(0.08f, 0.72f)
                    val delayed1 = delayLine1.process(inSample, delay1Samples, feedback1)

                    val delay2Samples = (delay1Samples * 0.5f).coerceIn(100.0f, 95000.0f)
                    val feedback2 = feedback1 * 0.5f
                    val delayed2 = delayLine2.process(inSample, delay2Samples, feedback2)

                    val filteredEcho = filter2.process(delayed1 * 0.7f + delayed2 * 0.35f)
                    wetSample = inSample * 0.85f + filteredEcho * 0.95f
                }

                VoiceEffect.EXTRATERRESTRE -> {
                    val carrier1 = sin(lfoPhase1)
                    val carrier2 = sin(lfoPhase2)
                    lfoPhase1 += twoPi * (240.0f + depVal * 60.0f) * invSampleRate
                    lfoPhase2 += twoPi * (410.0f + depVal * 70.0f) * invSampleRate
                    if (lfoPhase1 >= twoPi) lfoPhase1 -= twoPi
                    if (lfoPhase2 >= twoPi) lfoPhase2 -= twoPi

                    val tremolo = 0.75f + 0.25f * sin(vibratoPhase)
                    vibratoPhase += twoPi * 6.5f * invSampleRate
                    if (vibratoPhase >= twoPi) vibratoPhase -= twoPi

                    val ring = inSample * (carrier1 * 0.6f + carrier2 * 0.4f) * tremolo * 1.65f
                    wetSample = filter1.process(ring)
                }

                VoiceEffect.DESSIN_ANIME -> {
                    val vibrato = sin(vibratoPhase) * (0.09f * depVal)
                    vibratoPhase += twoPi * 5.5f * invSampleRate
                    if (vibratoPhase >= twoPi) vibratoPhase -= twoPi

                    val targetPitch = (pFactor + vibrato).coerceIn(1.05f, 2.2f)
                    val shifted = pitchShifter.process(inSample, targetPitch)
                    wetSample = filter2.process(shifted)
                }

                VoiceEffect.PERSONNE_AGEE -> {
                    val tremorAmp = 0.76f + 0.24f * sin(lfoPhase1)
                    val flutterPitch = (pFactor + 0.035f * sin(vibratoPhase)).coerceIn(0.60f, 1.30f)
                    lfoPhase1 += twoPi * 3.8f * invSampleRate
                    vibratoPhase += twoPi * 4.4f * invSampleRate
                    if (lfoPhase1 >= twoPi) lfoPhase1 -= twoPi
                    if (vibratoPhase >= twoPi) vibratoPhase -= twoPi

                    val shifted = pitchShifter.process(inSample, flutterPitch)
                    val wobbled = shifted * tremorAmp
                    val filtered = filter1.process(wobbled)
                    wetSample = filter2.process(filtered)
                }

                VoiceEffect.BEBE -> {
                    val microFlutter = 0.025f * sin(vibratoPhase)
                    vibratoPhase += twoPi * 7.5f * invSampleRate
                    if (vibratoPhase >= twoPi) vibratoPhase -= twoPi

                    val targetPitch = (pFactor + microFlutter).coerceIn(1.15f, 2.35f)
                    val shifted = pitchShifter.process(inSample, targetPitch)
                    wetSample = filter2.process(shifted)
                }
            }

            // Blend dry/wet based on intensity
            val blended = inSample * (1.0f - intVal) + wetSample * intVal

            // Master volume & speaker safety compression
            val speakerScale = if (antiFeedback) 0.85f else 1.0f
            val amplified = blended * vol * speakerScale
            val limited = softLimit(amplified)

            val outShort = (limited * 32767.0f).toInt().coerceIn(-32768, 32767).toShort()
            output[i] = outShort

            val absAmp = abs(limited)
            if (absAmp > peakAmplitude) {
                peakAmplitude = absAmp
            }
        }

        return peakAmplitude.coerceIn(0.0f, 1.0f)
    }

    private fun softClip(x: Float): Float {
        return when {
            x < -1.5f -> -1.0f
            x > 1.5f -> 1.0f
            else -> (x - (x * x * x) / 5.0f).coerceIn(-1.0f, 1.0f)
        }
    }

    private fun softLimit(x: Float): Float {
        return if (x > 0.90f || x < -0.90f) {
            tanh(x.toDouble()).toFloat()
        } else {
            x
        }
    }
}
