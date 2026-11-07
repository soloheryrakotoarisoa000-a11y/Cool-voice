package com.example.audio.effects

import kotlin.math.PI
import kotlin.math.sin
import kotlin.math.tanh

/**
 * Real-time Audio DSP Processor implementing the 12 distinct vocal transformation effects.
 * Designed for zero allocation inside the processing loop to guarantee low latency
 * and prevent GC stutter during continuous live audio streaming.
 */
class DspProcessor(
    var sampleRate: Float = 44100.0f
) {
    // Current effect and adjustable parameters
    @Volatile var activeEffect: VoiceEffect = VoiceEffect.ROBOT
    @Volatile var intensity: Float = 0.85f   // 0.0f .. 1.0f (dry/wet and effect depth)
    @Volatile var pitchFactor: Float = 1.0f  // Multiplier for default pitch (0.5x .. 2.0x)
    @Volatile var depth: Float = 0.70f       // Resonance / tone depth (0.0f .. 1.0f)
    @Volatile var masterVolume: Float = 1.0f // 0.0f .. 2.0f

    // DSP components
    private val pitchShifter = GranularPitchShifter(grainWindowSize = 1024)
    private val delayLine1 = DelayLine(maxDelaySamples = 44100) // Up to 1s delay
    private val delayLine2 = DelayLine(maxDelaySamples = 44100)
    private val filter1 = BiquadFilter()
    private val filter2 = BiquadFilter()

    // Oscillators / LFO phases for modulation
    private var lfoPhase1 = 0.0f
    private var lfoPhase2 = 0.0f
    private var vibratoPhase = 0.0f

    init {
        configureFiltersForCurrentEffect()
    }

    fun reset() {
        pitchShifter.reset()
        delayLine1.reset()
        delayLine2.reset()
        filter1.reset()
        filter2.reset()
        lfoPhase1 = 0.0f
        lfoPhase2 = 0.0f
        vibratoPhase = 0.0f
        configureFiltersForCurrentEffect()
    }

    /**
     * Update active effect and reset filter coefficients seamlessly.
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
        when (activeEffect) {
            VoiceEffect.ROBOT -> {
                // Band-pass filter around vocal mid-range for radio/robotic sound
                filter1.setBandPass(sampleRate, 1200.0f, 1.2f)
                filter2.setHighPass(sampleRate, 300.0f)
            }
            VoiceEffect.FEMME -> {
                // High-shelf / peaking for female vocal clarity
                filter1.setPeaking(sampleRate, 2600.0f, gainDb = 5.0f, q = 1.2f)
                filter2.setHighPass(sampleRate, 180.0f)
            }
            VoiceEffect.HOMME -> {
                // Low-shelf warmth and mild high damping
                filter1.setPeaking(sampleRate, 220.0f, gainDb = 6.0f, q = 1.0f)
                filter2.setLowPass(sampleRate, 4000.0f)
            }
            VoiceEffect.ENFANT -> {
                // High presence and cut low rumble
                filter1.setHighPass(sampleRate, 220.0f)
                filter2.setPeaking(sampleRate, 3200.0f, gainDb = 4.0f, q = 1.0f)
            }
            VoiceEffect.MONSTRE -> {
                // Deep bass boost and low pass
                filter1.setLowPass(sampleRate, 2400.0f, q = 0.9f)
                filter2.setPeaking(sampleRate, 140.0f, gainDb = 7.0f, q = 1.5f)
            }
            VoiceEffect.GRAVE -> {
                // Low pass radio broadcaster
                filter1.setLowPass(sampleRate, 2200.0f)
                filter2.setPeaking(sampleRate, 180.0f, gainDb = 5.0f, q = 1.0f)
            }
            VoiceEffect.AIGUE -> {
                // High pass and treble sparkle
                filter1.setHighPass(sampleRate, 300.0f)
                filter2.setPeaking(sampleRate, 4000.0f, gainDb = 6.0f, q = 1.0f)
            }
            VoiceEffect.ECHO -> {
                // Gentle damping high pass for natural echo reflections
                filter1.setHighPass(sampleRate, 120.0f)
                filter2.setLowPass(sampleRate, 6000.0f)
            }
            VoiceEffect.EXTRATERRESTRE -> {
                // Cosmic resonant bandpass
                filter1.setBandPass(sampleRate, 1800.0f, 2.0f)
                filter2.setHighPass(sampleRate, 250.0f)
            }
            VoiceEffect.DESSIN_ANIME -> {
                filter1.setHighPass(sampleRate, 200.0f)
                filter2.setPeaking(sampleRate, 2800.0f, gainDb = 5.0f, q = 1.1f)
            }
            VoiceEffect.PERSONNE_AGEE -> {
                // Bandwidth limitation (vintage gramophone/telephone style)
                filter1.setHighPass(sampleRate, 380.0f)
                filter2.setLowPass(sampleRate, 3200.0f)
            }
            VoiceEffect.BEBE -> {
                filter1.setHighPass(sampleRate, 280.0f)
                filter2.setPeaking(sampleRate, 3500.0f, gainDb = 4.0f, q = 1.2f)
            }
        }
    }

    /**
     * Process an entire block of 16-bit PCM samples in place or from input to output.
     * @param input Raw microphone PCM shorts
     * @param output Destination buffer for transformed audio
     * @param length Number of samples to process
     * @return Peak amplitude [0.0f .. 1.0f] for real-time visualizer
     */
    fun processBlock(input: ShortArray, output: ShortArray, length: Int): Float {
        val effect = activeEffect
        val intVal = intensity
        val depVal = depth
        val pFactor = pitchFactor
        val vol = masterVolume

        var peakAmplitude = 0.0f
        val twoPi = (2.0 * PI).toFloat()
        val invSampleRate = 1.0f / sampleRate

        for (i in 0 until length) {
            val rawShort = input[i]
            val inSample = rawShort / 32768.0f // Normalized [-1.0 .. 1.0]

            var wetSample = inSample

            when (effect) {
                VoiceEffect.ROBOT -> {
                    // Ring modulation + overdrive + bandpass
                    val carrierFreq = 65.0f + depVal * 40.0f
                    val carrier = sin(lfoPhase1)
                    lfoPhase1 += twoPi * carrierFreq * invSampleRate
                    if (lfoPhase1 >= twoPi) lfoPhase1 -= twoPi

                    // Ring modulated signal
                    val modulated = inSample * carrier * 1.4f
                    // Controlled soft clipping
                    val clipped = softClip(modulated * (1.0f + intVal * 2.5f))
                    // Filter
                    wetSample = filter1.process(clipped)
                }

                VoiceEffect.FEMME -> {
                    // Pitch shift up + formant shaping
                    val targetPitch = (1.35f * pFactor).coerceIn(1.05f, 2.0f)
                    val shifted = pitchShifter.process(inSample, targetPitch)
                    val filtered = filter1.process(shifted)
                    wetSample = filter2.process(filtered)
                }

                VoiceEffect.HOMME -> {
                    // Pitch shift down + chest warmth
                    val targetPitch = (0.75f * pFactor).coerceIn(0.50f, 0.95f)
                    val shifted = pitchShifter.process(inSample, targetPitch)
                    val filtered = filter1.process(shifted)
                    wetSample = filter2.process(filtered)
                }

                VoiceEffect.ENFANT -> {
                    // Lively pitch up + brightness
                    val targetPitch = (1.45f * pFactor).coerceIn(1.10f, 2.1f)
                    val shifted = pitchShifter.process(inSample, targetPitch)
                    val filtered = filter1.process(shifted)
                    wetSample = filter2.process(filtered)
                }

                VoiceEffect.MONSTRE -> {
                    // Deep pitch shift + heavy saturation + low growl tremolo
                    val targetPitch = (0.60f * pFactor).coerceIn(0.40f, 0.85f)
                    val shifted = pitchShifter.process(inSample, targetPitch)

                    // Slow growl LFO (9Hz)
                    val growlMod = 0.85f + 0.15f * sin(lfoPhase1)
                    lfoPhase1 += twoPi * 9.0f * invSampleRate
                    if (lfoPhase1 >= twoPi) lfoPhase1 -= twoPi

                    val saturated = softClip(shifted * (1.2f + depVal * 2.0f)) * growlMod
                    val filtered = filter1.process(saturated)
                    wetSample = filter2.process(filtered)
                }

                VoiceEffect.GRAVE -> {
                    // Clean deep pitch down
                    val targetPitch = (0.70f * pFactor).coerceIn(0.50f, 0.90f)
                    val shifted = pitchShifter.process(inSample, targetPitch)
                    val filtered = filter1.process(shifted)
                    wetSample = filter2.process(filtered)
                }

                VoiceEffect.AIGUE -> {
                    // Fun high pitch
                    val targetPitch = (1.60f * pFactor).coerceIn(1.20f, 2.2f)
                    val shifted = pitchShifter.process(inSample, targetPitch)
                    val filtered = filter1.process(shifted)
                    wetSample = filter2.process(filtered)
                }

                VoiceEffect.ECHO -> {
                    // Dual delay: 240ms main tap + 120ms secondary tap
                    val delay1Samples = (0.24f * depVal + 0.05f) * sampleRate
                    val feedback1 = (0.45f * intVal).coerceIn(0.1f, 0.75f)
                    val delayed1 = delayLine1.process(inSample, delay1Samples, feedback1)

                    val delay2Samples = delay1Samples * 0.5f
                    val feedback2 = feedback1 * 0.5f
                    val delayed2 = delayLine2.process(inSample, delay2Samples, feedback2)

                    val filteredEcho = filter2.process(delayed1 * 0.7f + delayed2 * 0.3f)
                    wetSample = inSample * 0.8f + filteredEcho * 0.9f
                }

                VoiceEffect.EXTRATERRESTRE -> {
                    // Dual sine oscillator intermodulation + 6Hz tremolo
                    val carrier1 = sin(lfoPhase1)
                    val carrier2 = sin(lfoPhase2)
                    lfoPhase1 += twoPi * 280.0f * invSampleRate
                    lfoPhase2 += twoPi * 435.0f * invSampleRate
                    if (lfoPhase1 >= twoPi) lfoPhase1 -= twoPi
                    if (lfoPhase2 >= twoPi) lfoPhase2 -= twoPi

                    // Tremolo LFO
                    val tremolo = 0.75f + 0.25f * sin(vibratoPhase)
                    vibratoPhase += twoPi * 6.5f * invSampleRate
                    if (vibratoPhase >= twoPi) vibratoPhase -= twoPi

                    val ring = inSample * (carrier1 * 0.6f + carrier2 * 0.4f) * tremolo * 1.5f
                    wetSample = filter1.process(ring)
                }

                VoiceEffect.DESSIN_ANIME -> {
                    // High pitch + vibrato frequency modulation (5.5Hz)
                    val vibrato = sin(vibratoPhase) * (0.08f * depVal)
                    vibratoPhase += twoPi * 5.5f * invSampleRate
                    if (vibratoPhase >= twoPi) vibratoPhase -= twoPi

                    val targetPitch = (1.50f * pFactor + vibrato).coerceIn(1.10f, 2.1f)
                    val shifted = pitchShifter.process(inSample, targetPitch)
                    wetSample = filter2.process(shifted)
                }

                VoiceEffect.PERSONNE_AGEE -> {
                    // Slight pitch down + vocal tremor wobble (3.8Hz) + bandpass
                    val tremorAmp = 0.78f + 0.22f * sin(lfoPhase1)
                    val flutterPitch = 0.92f * pFactor + 0.03f * sin(vibratoPhase)
                    lfoPhase1 += twoPi * 3.8f * invSampleRate
                    vibratoPhase += twoPi * 4.2f * invSampleRate
                    if (lfoPhase1 >= twoPi) lfoPhase1 -= twoPi
                    if (vibratoPhase >= twoPi) vibratoPhase -= twoPi

                    val shifted = pitchShifter.process(inSample, flutterPitch)
                    val wobbled = shifted * tremorAmp
                    val filtered = filter1.process(wobbled)
                    wetSample = filter2.process(filtered)
                }

                VoiceEffect.BEBE -> {
                    // Very high pitch + cute micro-flutter
                    val microFlutter = 0.02f * sin(vibratoPhase)
                    vibratoPhase += twoPi * 7.5f * invSampleRate
                    if (vibratoPhase >= twoPi) vibratoPhase -= twoPi

                    val targetPitch = (1.75f * pFactor + microFlutter).coerceIn(1.30f, 2.4f)
                    val shifted = pitchShifter.process(inSample, targetPitch)
                    wetSample = filter2.process(shifted)
                }
            }

            // Blend dry/wet based on intensity
            val blended = inSample * (1.0f - intVal) + wetSample * intVal

            // Master volume & soft safety limiter to prevent digital wrap/crackling
            val amplified = blended * vol
            val limited = softLimit(amplified)

            // Convert back to 16-bit PCM short
            val outShort = (limited * 32767.0f).toInt().coerceIn(-32768, 32767).toShort()
            output[i] = outShort

            // Track peak amplitude for visualizer
            val absAmp = kotlin.math.abs(limited)
            if (absAmp > peakAmplitude) {
                peakAmplitude = absAmp
            }
        }

        return peakAmplitude.coerceIn(0.0f, 1.0f)
    }

    /**
     * Soft clipping overdrive curve (tanh approximation for speed)
     */
    private fun softClip(x: Float): Float {
        // Fast polynomial soft clipping: x - x^3 / 3 for |x| < 1.0
        return when {
            x < -1.5f -> -1.0f
            x > 1.5f -> 1.0f
            else -> (x - (x * x * x) / 5.0f).coerceIn(-1.0f, 1.0f)
        }
    }

    /**
     * Master limiter: prevents harsh clipping while preserving dynamics
     */
    private fun softLimit(x: Float): Float {
        return if (x > 0.95f || x < -0.95f) {
            tanh(x.toDouble()).toFloat()
        } else {
            x
        }
    }
}
