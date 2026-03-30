package com.navassist.app.bluetooth

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.util.UUID

/**
 * Manages the Bluetooth SPP connection to the HC-05 module.
 *
 * • Connects via [BluetoothSocket] with the standard SPP UUID.
 * • Reads newline-delimited JSON on a background thread.
 * • Parses {"d":45,"l":312} packets and delivers [SensorData] via [listener].
 * • Auto-reconnects if the connection drops.
 */
class BluetoothService(
    private val bluetoothAdapter: BluetoothAdapter,
    private val listener: BluetoothListener
) {
    companion object {
        private const val TAG = "BluetoothService"

        /** Standard Serial Port Profile UUID */
        val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

        private const val RECONNECT_DELAY_MS = 3000L
    }

    /** Parsed data frame from a single Arduino JSON packet. */
    data class SensorData(
        val distanceCm: Int,
        val ldrValue: Int
    )

    /** Callback interface for delivering events to the app. */
    interface BluetoothListener {
        fun onConnected(deviceName: String)
        fun onDisconnected()
        fun onSensorData(data: SensorData)
        fun onError(message: String)
    }

    // ── Internal JSON data class matching Arduino output ──────────
    private data class ArduinoPacket(val d: Int = 0, val l: Int = 0)

    private val gson = Gson()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private var socket: BluetoothSocket? = null
    private var readerJob: Job? = null
    private var targetDevice: BluetoothDevice? = null

    @Volatile var isConnected = false
        private set

    // ── Public API ─────────────────────────────────────────────────

    /**
     * Start connecting to a paired HC-05 device identified by its MAC address or name.
     * Runs on a background coroutine; delivers events via [listener].
     */
    fun connect(deviceAddress: String) {
        readerJob?.cancel()
        readerJob = scope.launch {
            connectLoop(deviceAddress)
        }
    }

    /** Disconnect cleanly and stop the reader loop. */
    fun disconnect() {
        readerJob?.cancel()
        closeSocket()
        isConnected = false
        listener.onDisconnected()
    }

    /** Return a list of already-paired Bluetooth devices. */
    fun getPairedDevices(): Set<BluetoothDevice> {
        return try {
            @Suppress("MissingPermission")
            bluetoothAdapter.bondedDevices ?: emptySet()
        } catch (e: SecurityException) {
            Log.e(TAG, "Permission denied listing paired devices", e)
            emptySet()
        }
    }

    fun release() {
        scope.cancel()
        closeSocket()
    }

    // ── Internal ───────────────────────────────────────────────────

    private suspend fun connectLoop(address: String) {
        while (isActive) {
            try {
                val device = bluetoothAdapter.getRemoteDevice(address)
                targetDevice = device

                Log.d(TAG, "Connecting to ${device.name} ($address)…")

                @Suppress("MissingPermission")
                val newSocket = device.createRfcommSocketToServiceRecord(SPP_UUID)
                socket = newSocket

                // Cancel discovery to avoid slowing the connection
                @Suppress("MissingPermission")
                bluetoothAdapter.cancelDiscovery()

                @Suppress("MissingPermission")
                newSocket.connect()

                isConnected = true
                withContext(Dispatchers.Main) {
                    @Suppress("MissingPermission")
                    listener.onConnected(device.name ?: address)
                }

                readLoop(newSocket)

            } catch (e: IOException) {
                Log.w(TAG, "Connection failed: ${e.message}")
                closeSocket()
                isConnected = false
                withContext(Dispatchers.Main) { listener.onDisconnected() }
                delay(RECONNECT_DELAY_MS)   // wait before retry
            } catch (e: SecurityException) {
                Log.e(TAG, "Bluetooth permission missing", e)
                withContext(Dispatchers.Main) { listener.onError("Bluetooth permission denied") }
                break
            }
        }
    }

    private suspend fun readLoop(socket: BluetoothSocket) {
        try {
            val reader = BufferedReader(InputStreamReader(socket.inputStream))
            while (isActive) {
                val line = reader.readLine() ?: break  // null = stream closed
                parseLine(line)
            }
        } catch (e: IOException) {
            Log.w(TAG, "Read error: ${e.message}")
        }
        isConnected = false
        withContext(Dispatchers.Main) { listener.onDisconnected() }
    }

    private suspend fun parseLine(line: String) {
        val trimmed = line.trim()
        if (trimmed.isEmpty()) return
        try {
            val packet = gson.fromJson(trimmed, ArduinoPacket::class.java)
            val data = SensorData(
                distanceCm = packet.d.coerceIn(0, 400),
                ldrValue   = packet.l.coerceIn(0, 1023)
            )
            withContext(Dispatchers.Main) { listener.onSensorData(data) }
        } catch (e: JsonSyntaxException) {
            Log.w(TAG, "Malformed JSON ignored: $trimmed")
        }
    }

    private fun closeSocket() {
        try { socket?.close() } catch (_: IOException) {}
        socket = null
    }
}
