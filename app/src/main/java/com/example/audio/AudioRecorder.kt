package com.example.audio

import android.content.Context
import android.util.Log
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
 */
class AudioRecorder(private val context: Context) {

    private var outputStream: FileOutputStream? = null
    private var currentFile: File? = null
    private var totalBytesWritten: Long = 0
    private var isRecording: Boolean = false
    private var currentSampleRate: Int = 44100
    private var startTimeMillis: Long = 0

    val recordingActive: Boolean
        get() = isRecording

    /**
     * Start recording transformed PCM audio to a new WAV file.
     */
    fun startRecording(effectName: String, sampleRate: Int = 44100): File? {
        if (isRecording) return currentFile

        currentSampleRate = sampleRate
        totalBytesWritten = 0
        startTimeMillis = System.currentTimeMillis()

        val recordingsDir = File(context.filesDir, "recordings")
        if (!recordingsDir.exists()) {
            recordingsDir.mkdirs()
        }

        val dateFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
        val timestamp = dateFormat.format(Date())
        val sanitizedEffect = effectName.lowercase().replace(" ", "_")
        val file = File(recordingsDir, "coolvoice_${sanitizedEffect}_$timestamp.wav")

        try {
            val fos = FileOutputStream(file)
            // Write placeholder 44-byte WAV header
            val placeholderHeader = ByteArray(44)
            fos.write(placeholderHeader)

            outputStream = fos
            currentFile = file
            isRecording = true
            Log.d("AudioRecorder", "Recording started: ${file.absolutePath}")
            return file
        } catch (e: Exception) {
            Log.e("AudioRecorder", "Failed to start recording", e)
            isRecording = false
            return null
        }
    }

    /**
     * Write a chunk of transformed 16-bit PCM shorts to the WAV file.
     */
    fun writeChunk(pcmData: ShortArray, length: Int) {
        if (!isRecording) return
        val fos = outputStream ?: return

        try {
            val byteBuffer = ByteBuffer.allocate(length * 2)
            byteBuffer.order(ByteOrder.LITTLE_ENDIAN)
            for (i in 0 until length) {
                byteBuffer.putShort(pcmData[i])
            }
            val bytes = byteBuffer.array()
            fos.write(bytes)
            totalBytesWritten += bytes.size
        } catch (e: Exception) {
            Log.e("AudioRecorder", "Error writing audio chunk", e)
        }
    }

    /**
     * Finish recording, update WAV header with actual audio size, and return the recording duration in ms.
     */
    fun stopRecording(): RecordingResult? {
        if (!isRecording) return null
        isRecording = false

        val file = currentFile
        try {
            outputStream?.flush()
            outputStream?.close()
            outputStream = null

            if (file != null && file.exists() && totalBytesWritten > 0) {
                // Update WAV header with accurate chunk sizes
                updateWavHeader(file, totalBytesWritten, currentSampleRate, channels = 1, bitsPerSample = 16)
                val durationMs = (totalBytesWritten * 1000L) / (currentSampleRate * 2L)
                Log.d("AudioRecorder", "Recording stopped. Size: $totalBytesWritten bytes, Duration: $durationMs ms")
                return RecordingResult(
                    file = file,
                    durationMs = durationMs,
                    fileSizeBytes = file.length()
                )
            }
        } catch (e: Exception) {
            Log.e("AudioRecorder", "Error stopping recording", e)
        } finally {
            outputStream = null
            currentFile = null
        }
        return null
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

        // RIFF chunk descriptor
        buffer.put("RIFF".toByteArray())
        buffer.putInt(totalDataLen.toInt())
        buffer.put("WAVE".toByteArray())

        // "fmt " sub-chunk
        buffer.put("fmt ".toByteArray())
        buffer.putInt(16) // Subchunk1Size for PCM
        buffer.putShort(1) // AudioFormat: 1 = PCM
        buffer.putShort(channels)
        buffer.putInt(sampleRate)
        buffer.putInt(byteRate)
        buffer.putShort(blockAlign)
        buffer.putShort(bitsPerSample)

        // "data" sub-chunk
        buffer.put("data".toByteArray())
        buffer.putInt(audioDataLength.toInt())

        val raf = RandomAccessFile(file, "rw")
        raf.seek(0)
        raf.write(header)
        raf.close()
    }

    data class RecordingResult(
        val file: File,
        val durationMs: Long,
        val fileSizeBytes: Long
    )
}
