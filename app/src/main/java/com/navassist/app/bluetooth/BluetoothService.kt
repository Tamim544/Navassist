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

class BluetoothService(
    private val bluetoothAdapter: BluetoothAdapter?,
    private val listener: BluetoothListener
) {
    companion object {
        private const val TAG = "BluetoothService"
        val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
        private const val RECONNECT_DELAY_MS = 3000L
    }

    data class SensorData(val distanceCm: Int, val ldrValue: Int)

    interface BluetoothListener {
        fun onConnected(deviceName: String)
        fun onDisconnected()
        fun onSensorData(data: SensorData)
        fun onError(message: String)
    }

    private data class ArduinoPacket(val d: Int = 0, val l: Int = 0)

    private val gson = Gson()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private var socket: BluetoothSocket? = null
    private var readerJob: Job? = null
    private var targetDevice: BluetoothDevice? = null

    @Volatile var isConnected = false
        private set

    fun connect(deviceAddress: String) {
        if (bluetoothAdapter == null) {
            listener.onError("Bluetooth adapter is null")
            return
        }
        readerJob?.cancel()
        readerJob = scope.launch {
            connectLoop(deviceAddress)
        }
    }

    fun disconnect() {
        readerJob?.cancel()
        closeSocket()
        isConnected = false
        listener.onDisconnected()
    }

    fun getPairedDevices(): Set<BluetoothDevice> {
        if (bluetoothAdapter == null) return emptySet()
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

    private suspend fun CoroutineScope.connectLoop(address: String) {
        while (isActive) {
            if (bluetoothAdapter == null) break
            try {
                val device = bluetoothAdapter.getRemoteDevice(address)
                targetDevice = device

                Log.d(TAG, "Connecting to ${device.name} ($address)…")

                @Suppress("MissingPermission")
                val newSocket = device.createRfcommSocketToServiceRecord(SPP_UUID)
                socket = newSocket

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
                delay(RECONNECT_DELAY_MS)
            } catch (e: SecurityException) {
                Log.e(TAG, "Bluetooth permission missing", e)
                withContext(Dispatchers.Main) { listener.onError("Bluetooth permission denied") }
                break
            }
        }
    }

    private suspend fun CoroutineScope.readLoop(socket: BluetoothSocket) {
        try {
            val reader = BufferedReader(InputStreamReader(socket.inputStream))
            while (isActive) {
                val line = reader.readLine() ?: break
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
        } catch (_: JsonSyntaxException) {
            Log.w(TAG, "Malformed JSON ignored: $trimmed")
        }
    }

    private fun closeSocket() {
        try { socket?.close() } catch (_: IOException) {}
        socket = null
    }
}
