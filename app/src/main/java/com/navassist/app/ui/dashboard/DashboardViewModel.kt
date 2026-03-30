package com.navassist.app.ui.dashboard

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import com.navassist.app.service.BluetoothForegroundService

/**
 * ViewModel for the Dashboard screen.
 * Merges LiveData from BluetoothForegroundService into a single UI state.
 */
class DashboardViewModel(application: Application) : AndroidViewModel(application) {

    // Mirror service LiveData into ViewModel properties
    val distanceCm: LiveData<Int>    = BluetoothForegroundService.liveDistanceCm
    val ldrValue: LiveData<Int>      = BluetoothForegroundService.liveLdrValue
    val lightLevel: LiveData<String> = BluetoothForegroundService.liveLightLevel
    val activity: LiveData<String>   = BluetoothForegroundService.liveActivity
    val btConnected: LiveData<Boolean> = BluetoothForegroundService.liveBtConnected
    val accelMag: LiveData<Float>    = BluetoothForegroundService.liveAccelMag
    val phoneLux: LiveData<Float>    = BluetoothForegroundService.livePhoneLux

    /** True when distance ≤ current obstacle threshold (for visual warning). */
    private val _obstacleThreat = MutableLiveData(false)
    val obstacleThreat: LiveData<Boolean> = _obstacleThreat

    fun updateObstacleThreat(distanceCm: Int, threshold: Int) {
        _obstacleThreat.value = distanceCm <= threshold
    }
}
