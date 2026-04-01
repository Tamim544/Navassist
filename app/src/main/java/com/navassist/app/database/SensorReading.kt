package com.navassist.app.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sensor_readings")
data class SensorReading(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val distanceCm: Int,
    val ldrValue: Int,
    val lightLevel: String,
    val activity: String,
    val accelerometerX: Float = 0f,
    val accelerometerY: Float = 0f,
    val accelerometerZ: Float = 0f,
    val phoneLightLux: Float = 0f,
    val proximityDistance: Float = 0f
)
