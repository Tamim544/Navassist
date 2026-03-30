package com.navassist.app.database

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room entity representing a single sensor snapshot stored in the local database.
 *
 * Satisfies: "store sensed data in databases" requirement.
 */
@Entity(tableName = "sensor_readings")
data class SensorReading(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    /** Unix epoch timestamp in milliseconds */
    val timestamp: Long = System.currentTimeMillis(),

    /** Distance from HC-SR04 ultrasonic sensor, in centimetres (0–400) */
    val distanceCm: Int,

    /** Raw LDR ADC value (0–1023); higher = brighter in typical wiring */
    val ldrValue: Int,

    /** Classified light level: "dark", "dim", or "bright" */
    val lightLevel: String,

    /** ARC activity classification label */
    val activity: String,

    // Built-in smartphone accelerometer axes (m/s²)
    val accelerometerX: Float = 0f,
    val accelerometerY: Float = 0f,
    val accelerometerZ: Float = 0f,

    /** Smartphone ambient light sensor (lux) */
    val phoneLightLux: Float = 0f,

    /** Smartphone proximity sensor (cm) */
    val proximityDistance: Float = 0f
)
