package com.navassist.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.speech.tts.TextToSpeech
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.lifecycle.MutableLiveData
import com.navassist.app.MainActivity
import com.navassist.app.R
import com.navassist.app.arc.ActivityRecognizer
import com.navassist.app.bluetooth.BluetoothService
import com.navassist.app.database.AppDatabase
import com.navassist.app.database.SensorReading
import com.navassist.app.sensors.SmartphoneSensorManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.Locale

class BluetoothForegroundService : Service(),
    BluetoothService.BluetoothListener,
    SmartphoneSensorManager.SensorListener {

    companion object {
        private const val TAG = "BtFgService"
        private const val CHANNEL_ID  = "navassist_bt_channel"
        private const val NOTIF_ID    = 1001

        const val ACTION_CONNECT    = "com.navassist.app.CONNECT"
        const val ACTION_DISCONNECT = "com.navassist.app.DISCONNECT"
        const val EXTRA_DEVICE_ADDRESS = "device_address"

        const val PREF_OBSTACLE_THRESHOLD = "obstacle_threshold"
        const val PREF_TTS_SPEED          = "tts_speed"
        const val PREF_VIBRATION_ENABLED  = "vibration_enabled"
        const val PREF_TTS_ENABLED        = "tts_enabled"

        val liveDistanceCm   = MutableLiveData<Int>()
        val liveLdrValue     = MutableLiveData<Int>()
        val liveLightLevel   = MutableLiveData<String>()
        val liveActivity     = MutableLiveData<String>()
        val liveBtConnected  = MutableLiveData<Boolean>()
        val liveAccelMag     = MutableLiveData<Float>()
        val livePhoneLux     = MutableLiveData<Float>()
    }

    inner class LocalBinder : Binder() {
        fun getService(): BluetoothForegroundService = this@BluetoothForegroundService
    }
    private val binder = LocalBinder()
    override fun onBind(intent: Intent): IBinder = binder

    private lateinit var bluetoothService: BluetoothService
    private lateinit var sensorManager: SmartphoneSensorManager
    private lateinit var activityRecognizer: ActivityRecognizer
    private lateinit var tts: TextToSpeech
    private lateinit var database: AppDatabase

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private var lastVibrationTime = 0L
    private var lastTtsTime = 0L
    private val TTS_COOLDOWN_MS = 3000L

    private var accelX = 0f; private var accelY = 0f; private var accelZ = 9.81f
    private var phoneLux = 0f
    private var phoneProximity = 5f

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIF_ID, 
                buildNotification("Initialising…"), 
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
            )
        } else {
            startForeground(NOTIF_ID, buildNotification("Initialising…"))
        }

        database = AppDatabase.getInstance(applicationContext)
        activityRecognizer = ActivityRecognizer()

        sensorManager = SmartphoneSensorManager(this, this)
        sensorManager.register()

        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothService = BluetoothService(bluetoothManager.adapter, this)

        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts.language = Locale.US
                refreshSettings()
            }
        }
    }

    fun refreshSettings() {
        val prefs = getSharedPreferences("navassist_prefs", Context.MODE_PRIVATE)
        val speed = prefs.getFloat(PREF_TTS_SPEED, 1.0f)
        if (::tts.isInitialized) {
            tts.setSpeechRate(speed)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_CONNECT -> {
                val addr = intent.getStringExtra(EXTRA_DEVICE_ADDRESS)
                if (addr != null) {
                    bluetoothService.connect(addr)
                    updateNotification("Connecting to HC-05…")
                }
            }
            ACTION_DISCONNECT -> {
                bluetoothService.disconnect()
                updateNotification("Disconnected")
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        sensorManager.unregister()
        bluetoothService.release()
        if (::tts.isInitialized) tts.shutdown()
        super.onDestroy()
    }

    override fun onConnected(deviceName: String) {
        liveBtConnected.postValue(true)
        updateNotification("Connected to $deviceName")
        speak("Bluetooth connected to $deviceName")
    }

    override fun onDisconnected() {
        liveBtConnected.postValue(false)
        updateNotification("Bluetooth disconnected — reconnecting…")
    }

    override fun onSensorData(data: BluetoothService.SensorData) {
        val lightLevel = activityRecognizer.classifyLightLevel(data.ldrValue)
        val activity   = activityRecognizer.update(data.distanceCm, data.ldrValue, accelX, accelY, accelZ)
        
        liveDistanceCm.postValue(data.distanceCm)
        liveLdrValue.postValue(data.ldrValue)
        liveLightLevel.postValue(lightLevel)
        liveActivity.postValue(activity)

        triggerAlerts(data.distanceCm, lightLevel, activity)

        val reading = SensorReading(
            distanceCm = data.distanceCm, ldrValue = data.ldrValue,
            lightLevel = lightLevel, activity = activity,
            accelerometerX = accelX, accelerometerY = accelY, accelerometerZ = accelZ,
            phoneLightLux = phoneLux, proximityDistance = phoneProximity
        )
        serviceScope.launch { database.sensorReadingDao().insert(reading) }
    }

    override fun onError(message: String) {
        Log.e(TAG, "BT error: $message")
        updateNotification("Error: $message")
    }

    override fun onAccelerometerChanged(x: Float, y: Float, z: Float) {
        accelX = x; accelY = y; accelZ = z
        liveAccelMag.postValue(Math.sqrt((x * x + y * y + z * z).toDouble()).toFloat())
    }

    override fun onLightChanged(lux: Float) {
        phoneLux = lux
        livePhoneLux.postValue(lux)
    }

    override fun onProximityChanged(distance: Float) {
        phoneProximity = distance
    }

    private fun triggerAlerts(distanceCm: Int, lightLevel: String, activity: String) {
        val prefs = getSharedPreferences("navassist_prefs", Context.MODE_PRIVATE)
        val threshold = prefs.getInt(PREF_OBSTACLE_THRESHOLD, 50)
        val vibEnabled = prefs.getBoolean(PREF_VIBRATION_ENABLED, true)
        val ttsEnabled = prefs.getBoolean(PREF_TTS_ENABLED, true)
        val now = System.currentTimeMillis()

        if (ttsEnabled && now - lastTtsTime > TTS_COOLDOWN_MS) {
            when {
                lightLevel == "dark" -> { speak("Low light detected"); lastTtsTime = now }
                distanceCm < threshold -> { speak("Obstacle ${distanceCm} cm ahead"); lastTtsTime = now }
                activity == "Entering room" -> { speak("Entering room"); lastTtsTime = now }
            }
        }

        if (vibEnabled && distanceCm < threshold) {
            vibrateProximity(distanceCm, threshold)
        }
    }

    private fun speak(text: String) {
        if (!::tts.isInitialized) return
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "navassist_$text")
    }

    private fun vibrateProximity(distanceCm: Int, threshold: Int) {
        val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as android.os.Vibrator
        if (!vibrator.hasVibrator()) return
        val now = System.currentTimeMillis()
        val ratio = (distanceCm.toFloat() / threshold).coerceIn(0f, 1f)
        val offMs = (50 + (ratio * 450)).toLong()
        if (now - lastVibrationTime < offMs + 50L) return
        lastVibrationTime = now
        vibrator.vibrate(android.os.VibrationEffect.createOneShot(80, android.os.VibrationEffect.DEFAULT_AMPLITUDE))
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(CHANNEL_ID, "NavAssist Bluetooth", NotificationManager.IMPORTANCE_LOW)
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(status: String): Notification {
        val pendingIntent = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setContentTitle("NavAssist")
            .setContentText(status)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(status: String) {
        getSystemService(NotificationManager::class.java).notify(NOTIF_ID, buildNotification(status))
    }

    fun getPairedDevices() = bluetoothService.getPairedDevices()
    fun isBluetoothConnected() = bluetoothService.isConnected
}
