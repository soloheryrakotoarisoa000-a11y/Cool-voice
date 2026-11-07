package com.example.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface RecordingDao {

    @Query("SELECT * FROM recordings ORDER BY createdAt DESC")
    fun getAllRecordings(): Flow<List<RecordingEntity>>

    @Query("SELECT * FROM recordings ORDER BY createdAt DESC LIMIT :limit")
    fun getRecentRecordings(limit: Int): Flow<List<RecordingEntity>>

    @Query("SELECT * FROM recordings WHERE id = :id")
    suspend fun getRecordingById(id: Long): RecordingEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(recording: RecordingEntity): Long

    @Query("UPDATE recordings SET title = :newTitle WHERE id = :id")
    suspend fun updateTitle(id: Long, newTitle: String)

    @Delete
    suspend fun delete(recording: RecordingEntity)

    @Query("DELETE FROM recordings WHERE id = :id")
    suspend fun deleteById(id: Long)
}
