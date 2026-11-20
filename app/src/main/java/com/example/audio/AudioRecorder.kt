package com.example.audio

import android.content.Context
import android.util.Log
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Streams transformed 16-bit PCM audio directly into a standard WAV (RIFF/PCM) file.
 * Uses buffered I/O and a pre-allocated byte buffer to avoid stalling the real-time audio thread.
 */
class AudioRecorder(private val context: Context) {

    private var bufferedStream: BufferedOutputStream? = null
    private var currentFile: File? = null
    private var totalBytesWritten: Long = 0

    @Volatile
    private var isRecording: Boolean = false
    private var currentSampleRate: Int = 44100
    private val lock = Any()

    // Pre-allocated byte buffer (sized for up to 4096 samples = 8192 bytes)
    private var byteBufferCache = ByteArray(8192)

    val recordingActive: Boolean
        get() = isRecording

    /**
     * Start recording transformed PCM audio to a new WAV file.
     */
    fun startRecording(effectName: String, sampleRate: Int = 44100): File? {
        synchronized(lock) {
            if (isRecording) return currentFile

            currentSampleRate = sampleRate
            totalBytesWritten = 0

            val recordingsDir = File(context.filesDir, "recordings")
            if (!recordingsDir.exists() && !recordingsDir.mkdirs()) {
                Log.e("AudioRecorder", "Failed to create recordings directory")
                return null
            }

            val dateFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
            val timestamp = dateFormat.format(Date())
            val sanitizedEffect = effectName.lowercase(Locale.ROOT).replace(" ", "_")
            val file = File(recordingsDir, "coolvoice_${sanitizedEffect}_$timestamp.wav")

            return try {
                val fos = FileOutputStream(file)
                val bos = BufferedOutputStream(fos, 16384)
                // Write placeholder 44-byte WAV header
                val placeholderHeader = ByteArray(44)
                bos.write(placeholderHeader)

                bufferedStream = bos
                currentFile = file
                isRecording = true
                Log.d("AudioRecorder", "Recording started: ${file.absolutePath} at $sampleRate Hz")
                file
            } catch (e: Exception) {
                Log.e("AudioRecorder", "Failed to start recording", e)
                isRecording = false
                bufferedStream = null
                currentFile = null
                null
            }
        }
    }

    /**
     * Write a chunk of transformed 16-bit PCM shorts to the WAV file without allocating memory.
     */
    fun writeChunk(pcmData: ShortArray, length: Int) {
        if (!isRecording || length <= 0) return

        synchronized(lock) {
            if (!isRecording) return
            val bos = bufferedStream ?: return
            val requiredBytes = length * 2
            if (byteBufferCache.size < requiredBytes) {
                byteBufferCache = ByteArray(requiredBytes)
            }

            var byteIdx = 0
            for (i in 0 until length) {
                val sample = pcmData[i].toInt()
                byteBufferCache[byteIdx++] = (sample and 0xFF).toByte()
                byteBufferCache[byteIdx++] = ((sample shr 8) and 0xFF).toByte()
            }

            try {
                bos.write(byteBufferCache, 0, requiredBytes)
                totalBytesWritten += requiredBytes
            } catch (e: Exception) {
                Log.e("AudioRecorder", "Error writing audio chunk", e)
            }
        }
    }

    /**
     * Finish recording, update WAV header with actual audio size, and return the recording metadata.
     */
    fun stopRecording(): RecordingResult? {
        synchronized(lock) {
            if (!isRecording) return null
            isRecording = false

            val file = currentFile
            try {
                bufferedStream?.flush()
                bufferedStream?.close()
                bufferedStream = null

                if (file != null && file.exists()) {
                    if (totalBytesWritten > 0) {
                        updateWavHeader(file, totalBytesWritten, currentSampleRate, channels = 1, bitsPerSample = 16)
                        val durationMs = (totalBytesWritten * 1000L) / (currentSampleRate * 2L)
                        Log.d(
                            "AudioRecorder",
                            "Recording stopped. Size: $totalBytesWritten bytes, Duration: $durationMs ms, File: ${file.length()} bytes"
                        )
                        return RecordingResult(
                            file = file,
                            durationMs = durationMs.coerceAtLeast(100L),
                            fileSizeBytes = file.length()
                        )
                    } else {
                        Log.w("AudioRecorder", "Recording had 0 audio bytes; removing empty file.")
                        file.delete()
                    }
                }
            } catch (e: Exception) {
                Log.e("AudioRecorder", "Error stopping recording", e)
            } finally {
                bufferedStream = null
                currentFile = null
            }
            return null
        }
    }

    private fun updateWavHeader(
        file: File,
        audioDataLength: Long,
        sampleRate: Int,
        channels: Short,
        bitsPerSample: Short
    ) {
        val totalDataLen = audioDataLength + 36
        val byteRate = sampleRate * channels * (bitsPerSample / 8)
        val blockAlign = (channels * (bitsPerSample / 8)).toShort()

        val header = ByteArray(44)
        val buffer = ByteBuffer.wrap(header)
        buffer.order(ByteOrder.LITTLE_ENDIAN)

        buffer.put("RIFF".toByteArray())
        buffer.putInt(totalDataLen.toInt())
        buffer.put("WAVE".toByteArray())

        buffer.put("fmt ".toByteArray())
        buffer.putInt(16)
        buffer.putShort(1) // PCM
        buffer.putShort(channels)
        buffer.putInt(sampleRate)
        buffer.putInt(byteRate)
        buffer.putShort(blockAlign)
        buffer.putShort(bitsPerSample)

        buffer.put("data".toByteArray())
        buffer.putInt(audioDataLength.toInt())

        RandomAccessFile(file, "rw").use { raf ->
            raf.seek(0)
            raf.write(header)
        }
    }

    data class RecordingResult(
        val file: File,
        val durationMs: Long,
        val fileSizeBytes: Long
    )
}
