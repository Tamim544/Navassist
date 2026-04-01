package com.navassist.app.database

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface SensorReadingDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(reading: SensorReading): Long

    /** Return all readings, newest first. */
    @Query("SELECT * FROM sensor_readings ORDER BY timestamp DESC")
    fun getAllReadings(): LiveData<List<SensorReading>>

    /** Paged retrieval for the History screen. */
    @Query("SELECT * FROM sensor_readings ORDER BY timestamp DESC LIMIT :limit OFFSET :offset")
    suspend fun getReadingsPaged(limit: Int, offset: Int): List<SensorReading>

    /** Last N readings for ARC feature extraction. */
    @Query("SELECT * FROM sensor_readings ORDER BY timestamp DESC LIMIT :n")
    suspend fun getLastN(n: Int): List<SensorReading>

    /** Total number of stored readings. */
    @Query("SELECT COUNT(*) FROM sensor_readings")
    suspend fun count(): Int

    /** Delete readings older than a given timestamp. */
    @Query("DELETE FROM sensor_readings WHERE timestamp < :beforeTimestamp")
    suspend fun deleteOlderThan(beforeTimestamp: Long): Int

    /** Clear all data. */
    @Query("DELETE FROM sensor_readings")
    suspend fun deleteAll()
}
