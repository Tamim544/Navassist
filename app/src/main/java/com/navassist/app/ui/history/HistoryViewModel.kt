package com.navassist.app.ui.history

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import com.navassist.app.database.AppDatabase
import com.navassist.app.database.SensorReading

/**
 * ViewModel for the History screen.
 * Exposes all stored readings from Room via LiveData.
 */
class HistoryViewModel(application: Application) : AndroidViewModel(application) {

    private val dao = AppDatabase.getInstance(application).sensorReadingDao()

    /** Reactive list of all readings, newest first. */
    val allReadings: LiveData<List<SensorReading>> = dao.getAllReadings()
}
