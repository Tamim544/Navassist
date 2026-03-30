package com.navassist.app

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import com.navassist.app.databinding.ActivityMainBinding
import com.navassist.app.service.BluetoothForegroundService

/**
 * Single-Activity host using Navigation Component + BottomNavigationView.
 *
 * 3 destination screens:
 *  • Dashboard  — live sensor readings
 *  • History    — stored readings from Room DB
 *  • Settings   — BT device selection, alert thresholds
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var navController: NavController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Set up Navigation Component
        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        navController = navHostFragment.navController

        val appBarConfig = AppBarConfiguration(
            setOf(R.id.dashboardFragment, R.id.historyFragment, R.id.settingsFragment)
        )
        setupActionBarWithNavController(navController, appBarConfig)
        binding.bottomNavigation.setupWithNavController(navController)

        // Start the foreground service so BT connection persists in background
        startForegroundService()
    }

    override fun onSupportNavigateUp(): Boolean {
        return navController.navigateUp() || super.onSupportNavigateUp()
    }

    private fun startForegroundService() {
        val intent = Intent(this, BluetoothForegroundService::class.java)
        androidx.core.content.ContextCompat.startForegroundService(this, intent)
    }
}
