package com.example

import com.example.database.RecordingEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RecordingEntityTest {

    @Test
    fun testRecordingEntityProperties() {
        val entity = RecordingEntity(
            id = 42,
            title = "Enregistrement Robot Cool Voice",
            filePath = "/data/user/0/com.aistudio.coolvoice/files/recordings/robot.wav",
            durationMs = 6500,
            format = "WAV PCM 16-bit",
            effectName = "ROBOT",
            fileSizeBytes = 573344
        )

        assertEquals(42L, entity.id)
        assertEquals("Enregistrement Robot Cool Voice", entity.title)
        assertEquals("ROBOT", entity.effectName)
        assertEquals(6500L, entity.durationMs)
        assertEquals("WAV PCM 16-bit", entity.format)
        assertTrue(entity.fileSizeBytes > 0)
    }
}
