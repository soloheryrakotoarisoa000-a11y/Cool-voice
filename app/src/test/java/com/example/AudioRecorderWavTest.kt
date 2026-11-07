package com.example

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Validates WAV RIFF format creation and header integrity without requiring Robolectric.
 */
class AudioRecorderWavTest {

    @Test
    fun testWavHeaderFormatCalculation() {
        val sampleRate = 44100
        val channels: Short = 1
        val bitsPerSample: Short = 16
        val audioDataLength = 88200L // 1 second of mono 16-bit audio at 44.1kHz

        val durationMs = (audioDataLength * 1000L) / (sampleRate * 2L)
        assertEquals(1000L, durationMs)

        val totalDataLen = audioDataLength + 36
        val byteRate = sampleRate * channels * (bitsPerSample / 8)
        val blockAlign = (channels * (bitsPerSample / 8)).toShort()

        val header = ByteArray(44)
        val buffer = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)

        buffer.put("RIFF".toByteArray())
        buffer.putInt(totalDataLen.toInt())
        buffer.put("WAVE".toByteArray())
        buffer.put("fmt ".toByteArray())
        buffer.putInt(16)
        buffer.putShort(1)
        buffer.putShort(channels)
        buffer.putInt(sampleRate)
        buffer.putInt(byteRate)
        buffer.putShort(blockAlign)
        buffer.putShort(bitsPerSample)
        buffer.put("data".toByteArray())
        buffer.putInt(audioDataLength.toInt())

        // Create temporary test file
        val tempFile = File.createTempFile("test_audio", ".wav")
        val raf = RandomAccessFile(tempFile, "rw")
        raf.write(header)

        // Write sample data
        val samples = ShortArray(44100) { 500 }
        val byteBuffer = ByteBuffer.allocate(samples.size * 2).order(ByteOrder.LITTLE_ENDIAN)
        for (s in samples) byteBuffer.putShort(s)
        raf.write(byteBuffer.array())
        raf.close()

        assertTrue(tempFile.exists())
        assertEquals(44 + 88200L, tempFile.length())

        // Read back header and verify
        val verifyRaf = RandomAccessFile(tempFile, "r")
        val verifyHeader = ByteArray(44)
        verifyRaf.readFully(verifyHeader)
        verifyRaf.close()

        val readBuffer = ByteBuffer.wrap(verifyHeader).order(ByteOrder.LITTLE_ENDIAN)
        val riffTag = ByteArray(4)
        readBuffer.get(riffTag)
        assertEquals("RIFF", String(riffTag))

        val readTotalLen = readBuffer.int
        assertEquals(tempFile.length().toInt() - 8, readTotalLen)

        val waveTag = ByteArray(4)
        readBuffer.get(waveTag)
        assertEquals("WAVE", String(waveTag))

        val fmtTag = ByteArray(4)
        readBuffer.get(fmtTag)
        assertEquals("fmt ", String(fmtTag))

        assertEquals(16, readBuffer.int) // subchunk1Size
        assertEquals(1.toShort(), readBuffer.short) // PCM format
        assertEquals(1.toShort(), readBuffer.short) // channels
        assertEquals(44100, readBuffer.int) // sample rate

        tempFile.delete()
    }
}
