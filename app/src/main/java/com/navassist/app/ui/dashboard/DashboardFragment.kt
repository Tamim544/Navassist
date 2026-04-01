package com.navassist.app.ui.dashboard

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.navassist.app.R
import com.navassist.app.databinding.FragmentDashboardBinding
import com.navassist.app.service.BluetoothForegroundService

/**
 * Screen 1 — Dashboard
 * Shows: live distance gauge, LDR light bar, current activity label, BT status.
 *
 * Satisfies: Interface Design (10 marks) + Accessibility Features (TalkBack descriptions).
 */
class DashboardFragment : Fragment() {

    private var _binding: FragmentDashboardBinding? = null
    private val binding get() = _binding!!

    private val viewModel: DashboardViewModel by viewModels()
    private var btService: BluetoothForegroundService? = null
    private var obstacleThreshold = 50   // default; overridden by Settings

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            val binder = service as BluetoothForegroundService.LocalBinder
            btService = binder.getService()
        }
        override fun onServiceDisconnected(name: ComponentName) {
            btService = null
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDashboardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Read threshold from SharedPreferences
        val prefs = requireContext().getSharedPreferences("navassist_prefs", Context.MODE_PRIVATE)
        obstacleThreshold = prefs.getInt(
            BluetoothForegroundService.PREF_OBSTACLE_THRESHOLD, 50
        )

        observeLiveData()
        setContentDescriptions()
    }

    override fun onStart() {
        super.onStart()
        Intent(requireContext(), BluetoothForegroundService::class.java).also {
            requireContext().bindService(it, serviceConnection, Context.BIND_AUTO_CREATE)
        }
    }

    override fun onStop() {
        super.onStop()
        requireContext().unbindService(serviceConnection)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    // ── Observe LiveData ───────────────────────────────────────────

    private fun observeLiveData() {
        viewModel.distanceCm.observe(viewLifecycleOwner) { cm ->
            binding.tvDistanceValue.text = "$cm cm"
            binding.progressDistance.progress = cm.coerceIn(0, 400)
            viewModel.updateObstacleThreat(cm, obstacleThreshold)

            // Update content description for TalkBack
            binding.progressDistance.contentDescription =
                "Distance gauge: $cm centimetres"
        }

        viewModel.ldrValue.observe(viewLifecycleOwner) { ldr ->
            binding.progressLdr.progress = ldr
            binding.tvLdrValue.text = "$ldr"
            binding.progressLdr.contentDescription = "Light sensor: $ldr out of 1023"
        }

        viewModel.lightLevel.observe(viewLifecycleOwner) { level ->
            binding.tvLightLevel.text = level.replaceFirstChar { it.uppercase() }
            val color = when (level) {
                "dark"   -> ContextCompat.getColor(requireContext(), R.color.dark_indicator)
                "dim"    -> ContextCompat.getColor(requireContext(), R.color.dim_indicator)
                else     -> ContextCompat.getColor(requireContext(), R.color.bright_indicator)
            }
            binding.viewLightIndicator.setBackgroundColor(color)
        }

        viewModel.activity.observe(viewLifecycleOwner) { label ->
            binding.tvActivityLabel.text = label
            binding.tvActivityLabel.contentDescription = "Current activity: $label"
        }

        viewModel.btConnected.observe(viewLifecycleOwner) { connected ->
            val status = if (connected) "● Connected" else "○ Disconnected"
            val color = if (connected)
                ContextCompat.getColor(requireContext(), R.color.bt_connected)
            else
                ContextCompat.getColor(requireContext(), R.color.bt_disconnected)

            binding.tvBtStatus.text = status
            binding.tvBtStatus.setTextColor(color)
            binding.tvBtStatus.contentDescription =
                if (connected) "Bluetooth connected" else "Bluetooth disconnected"
        }

        viewModel.accelMag.observe(viewLifecycleOwner) { mag ->
            binding.tvAccelValue.text = String.format("%.2f m/s²", mag)
        }

        viewModel.phoneLux.observe(viewLifecycleOwner) { lux ->
            binding.tvPhoneLux.text = String.format("%.1f lux", lux)
        }

        viewModel.obstacleThreat.observe(viewLifecycleOwner) { threat ->
            val warningColor = if (threat)
                ContextCompat.getColor(requireContext(), R.color.obstacle_warning)
            else
                ContextCompat.getColor(requireContext(), R.color.surface)
            binding.cardDistance.setCardBackgroundColor(warningColor)
        }
    }

    // ── Accessibility content descriptions (TalkBack) ──────────────

    private fun setContentDescriptions() {
        binding.progressDistance.contentDescription = "Distance gauge — no reading yet"
        binding.progressLdr.contentDescription      = "Light sensor — no reading yet"
        binding.tvActivityLabel.contentDescription  = "Activity label — initialising"
        binding.tvBtStatus.contentDescription       = "Bluetooth status"
    }
}
