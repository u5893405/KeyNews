package com.example.keynews.data.db

// CodeCleaner_Start_2f58c52f-01a2-4925-be72-c42f19834734
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Delete
import com.example.keynews.data.model.ScheduledReading

@Dao
interface ScheduledReadingDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertScheduledReading(sr: ScheduledReading): Long

    @Query("SELECT * FROM scheduled_reading")
    suspend fun getAllScheduledReadings(): List<ScheduledReading>

    @Delete
    suspend fun deleteScheduledReading(scheduledReading: ScheduledReading): Int // Delete entity

    @Query("DELETE FROM scheduled_reading WHERE id = :srId")
    suspend fun deleteScheduledReadingById(srId: Long): Int
}
// CodeCleaner_End_2f58c52f-01a2-4925-be72-c42f19834734

