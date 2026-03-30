package com.navassist.app.sensors

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager

/**
 * Registers listeners for built-in smartphone sensors:
 *  - TYPE_ACCELEROMETER  (movement detection / ARC step 4)
 *  - TYPE_LIGHT          (ambient light cross-check with LDR)
 *  - TYPE_PROXIMITY      (face/object proximity)
 *
 * Satisfies the "built-in smartphone sensors" requirement.
 */
class SmartphoneSensorManager(
    context: Context,
    private val listener: SensorListener
) : SensorEventListener {

    interface SensorListener {
        fun onAccelerometerChanged(x: Float, y: Float, z: Float)
        fun onLightChanged(lux: Float)
        fun onProximityChanged(distance: Float)
    }

    private val sensorManager =
        context.getSystemService(Context.SENSOR_SERVICE) as SensorManager

    private val accelerometer: Sensor? =
        sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val lightSensor: Sensor? =
        sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT)
    private val proximitySensor: Sensor? =
        sensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY)

    // Latest cached values (accessible without callback)
    var latestAccelX = 0f; private set
    var latestAccelY = 0f; private set
    var latestAccelZ = 9.81f; private set   // gravity default
    var latestLux = 0f; private set
    var latestProximity = 5f; private set   // "far" default

    // ── Lifecycle ──────────────────────────────────────────────────

    /** Call from onResume() / foreground service start. */
    fun register() {
        accelerometer?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
        lightSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
        proximitySensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
    }

    /** Call from onPause() / service stop. */
    fun unregister() {
        sensorManager.unregisterListener(this)
    }

    // ── SensorEventListener ────────────────────────────────────────

    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> {
                latestAccelX = event.values[0]
                latestAccelY = event.values[1]
                latestAccelZ = event.values[2]
                listener.onAccelerometerChanged(latestAccelX, latestAccelY, latestAccelZ)
            }
            Sensor.TYPE_LIGHT -> {
                latestLux = event.values[0]
                listener.onLightChanged(latestLux)
            }
            Sensor.TYPE_PROXIMITY -> {
                latestProximity = event.values[0]
                listener.onProximityChanged(latestProximity)
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {
        // No action needed for this application
    }

    // ── Helpers ────────────────────────────────────────────────────

    fun isSensorAvailable(type: Int): Boolean =
        sensorManager.getDefaultSensor(type) != null
}
