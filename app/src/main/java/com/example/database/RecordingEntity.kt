package com.example.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "recordings")
data class RecordingEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val filePath: String,
    val createdAt: Long = System.currentTimeMillis(),
    val durationMs: Long,
    val format: String = "WAV PCM 16-bit",
    val effectName: String,
    val fileSizeBytes: Long
)
