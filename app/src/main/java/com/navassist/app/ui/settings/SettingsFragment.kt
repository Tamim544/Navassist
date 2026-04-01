package com.navassist.app.ui.settings

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.navassist.app.databinding.FragmentSettingsBinding
import com.navassist.app.service.BluetoothForegroundService

/**
 * Screen 3 — Settings
 * • Select HC-05 from paired device list and connect / disconnect.
 * • Adjust obstacle alert threshold (cm).
 * • Toggle vibration on/off.
 * • Adjust TTS speech rate.
 * • Toggle speech on/off.
 */
class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    private var btService: BluetoothForegroundService? = null
    private var pairedDevices = listOf<BluetoothDevice>()

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            val binder = service as BluetoothForegroundService.LocalBinder
            btService = binder.getService()
            loadPairedDevices()
        }
        override fun onServiceDisconnected(name: ComponentName) {
            btService = null
        }
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        if (grants.values.all { it }) {
            loadPairedDevices()
        } else {
            Toast.makeText(requireContext(), "Bluetooth permissions required", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        loadPrefs()
        setupListeners()
    }

    override fun onStart() {
        super.onStart()
        Intent(requireContext(), BluetoothForegroundService::class.java).also {
            requireContext().bindService(it, serviceConnection, Context.BIND_AUTO_CREATE)
        }
        checkPermissions()
    }

    override fun onStop() {
        super.onStop()
        requireContext().unbindService(serviceConnection)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun loadPrefs() {
        val prefs = requireContext().getSharedPreferences("navassist_prefs", Context.MODE_PRIVATE)
        val threshold = prefs.getInt(BluetoothForegroundService.PREF_OBSTACLE_THRESHOLD, 50)
        val ttsSpeed  = prefs.getFloat(BluetoothForegroundService.PREF_TTS_SPEED, 1.0f)
        val vibration = prefs.getBoolean(BluetoothForegroundService.PREF_VIBRATION_ENABLED, true)
        val ttsEnabled = prefs.getBoolean(BluetoothForegroundService.PREF_TTS_ENABLED, true)

        binding.sliderThreshold.value = threshold.toFloat()
        binding.tvThresholdValue.text = "${threshold} cm"
        binding.sliderTtsSpeed.value  = (ttsSpeed * 10).toInt().toFloat()
        binding.tvTtsSpeedValue.text  = String.format("%.1f×", ttsSpeed)
        binding.switchVibration.isChecked = vibration
        binding.switchTts.isChecked = ttsEnabled
    }

    private fun savePrefs() {
        val prefs = requireContext().getSharedPreferences("navassist_prefs", Context.MODE_PRIVATE)
        prefs.edit()
            .putInt(BluetoothForegroundService.PREF_OBSTACLE_THRESHOLD,
                binding.sliderThreshold.value.toInt())
            .putFloat(BluetoothForegroundService.PREF_TTS_SPEED,
                binding.sliderTtsSpeed.value / 10f)
            .putBoolean(BluetoothForegroundService.PREF_VIBRATION_ENABLED,
                binding.switchVibration.isChecked)
            .putBoolean(BluetoothForegroundService.PREF_TTS_ENABLED,
                binding.switchTts.isChecked)
            .apply()
        
        // Notify the service to update its cached values immediately
        btService?.refreshSettings()
    }

    private fun setupListeners() {
        binding.sliderThreshold.addOnChangeListener { _, value, _ ->
            binding.tvThresholdValue.text = "${value.toInt()} cm"
            savePrefs()
        }

        binding.sliderTtsSpeed.addOnChangeListener { _, value, _ ->
            binding.tvTtsSpeedValue.text = String.format("%.1f×", value / 10f)
            savePrefs()
        }

        binding.switchVibration.setOnCheckedChangeListener { _, _ -> savePrefs() }
        binding.switchTts.setOnCheckedChangeListener { _, _ -> savePrefs() }

        binding.btnConnect.setOnClickListener {
            val selectedIdx = binding.spinnerDevices.selectedItemPosition
            if (selectedIdx < 0 || selectedIdx >= pairedDevices.size) {
                Toast.makeText(requireContext(), "Select a device first", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val device = pairedDevices[selectedIdx]
            val serviceIntent = Intent(requireContext(), BluetoothForegroundService::class.java).apply {
                action = BluetoothForegroundService.ACTION_CONNECT
                putExtra(BluetoothForegroundService.EXTRA_DEVICE_ADDRESS, device.address)
            }
            ContextCompat.startForegroundService(requireContext(), serviceIntent)
            Toast.makeText(requireContext(), "Connecting to ${device.name}…", Toast.LENGTH_SHORT).show()
        }

        binding.btnDisconnect.setOnClickListener {
            val serviceIntent = Intent(requireContext(), BluetoothForegroundService::class.java).apply {
                action = BluetoothForegroundService.ACTION_DISCONNECT
            }
            requireContext().startService(serviceIntent)
        }
    }

    @Suppress("MissingPermission")
    private fun loadPairedDevices() {
        val service = btService ?: return
        pairedDevices = service.getPairedDevices().toList()
        val names = pairedDevices.map { "${it.name} (${it.address})" }
        val adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_item,
            names
        )
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerDevices.adapter = adapter
    }

    private fun checkPermissions() {
        val needed = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED)
                needed.add(Manifest.permission.BLUETOOTH_CONNECT)
            if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED)
                needed.add(Manifest.permission.BLUETOOTH_SCAN)
        } else {
            if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED)
                needed.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        if (needed.isNotEmpty()) {
            permissionLauncher.launch(needed.toTypedArray())
        }
    }
}
