package com.airpods.control.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.*
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.airpods.control.core.bluetooth.AirPodsDeviceManager
import com.airpods.control.core.data.AppPreferences
import com.airpods.control.core.bluetooth.AirPodsGattManager
import com.airpods.control.core.bluetooth.shizuku.ShizukuStatus
import com.airpods.control.core.service.AirPodsService
import com.airpods.control.core.ui.AirPodsTheme
import com.airpods.control.home.HomeScreen
import com.airpods.control.settings.SettingsScreen
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var deviceManager: AirPodsDeviceManager
    @Inject lateinit var gattManager: AirPodsGattManager
    @Inject lateinit var preferences: AppPreferences

    private val requiredPermissions = mutableListOf(
        Manifest.permission.BLUETOOTH,
        Manifest.permission.BLUETOOTH_ADMIN
    ).apply {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            add(Manifest.permission.BLUETOOTH_CONNECT)
            add(Manifest.permission.BLUETOOTH_SCAN)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private val locationPermissions = arrayOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION
    )

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        if (results.values.all { it }) {
            startAirPodsService()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            var shizukuStateText by remember { mutableStateOf("") }
            LaunchedEffect(Unit) {
                try {
                    gattManager.shizukuState.collect { shizukuStateText = it }
                } catch (_: Exception) { }
            }
            MaterialTheme(
                colorScheme = darkColorScheme(
                    background = AirPodsTheme.Colors.SurfaceDark,
                    surface = AirPodsTheme.Colors.CardBackground
                )
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = AirPodsTheme.Colors.SurfaceDark
                ) {
                    val navController = rememberNavController()
                    NavHost(navController, startDestination = "home") {
                        composable("home") {
                            HomeScreen(
                                deviceManager = deviceManager,
                                onRetry = { startAirPodsService() },
                                shizukuReady = ShizukuStatus.isReady(),
                                shizukuStatus = shizukuStateText,
                                onRequestShizuku = {
                                    try {
                                        ShizukuStatus.refresh(this@MainActivity)
                                        if (ShizukuStatus.isReady()) {
                                            // Already authorized - connect directly
                                            val addr = deviceManager.state.value.deviceAddress
                                            if (addr.isNotEmpty()) gattManager.connectViaShizuku(addr)
                                        } else {
                                            rikka.shizuku.Shizuku.requestPermission(0)
                                        }
                                    } catch (_: Exception) { }
                                }
                            )
                        }
                        composable("settings") {
                            SettingsScreen(
                                preferences = preferences,
                                onBack = { navController.popBackStack() }
                            )
                        }
                    }
                }
            }
        }

        setupShizukuAutoConnect()
        checkPermissionsAndStart()
    }

    override fun onResume() {
        super.onResume()
        try {
            startAirPodsService()
        } catch (_: Exception) { }
    }

    private fun setupShizukuAutoConnect() {
        try {
            rikka.shizuku.Shizuku.addRequestPermissionResultListener { code, grant ->
                if (code == 0 && grant == PackageManager.PERMISSION_GRANTED) {
                    ShizukuStatus.refresh(this)
                    val addr = deviceManager.state.value.deviceAddress
                    if (addr.isNotEmpty()) gattManager.connectViaShizuku(addr)
                }
            }
        } catch (_: Exception) { }
    }

    private fun checkPermissionsAndStart() {
        val missingBluetooth = requiredPermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missingBluetooth.isNotEmpty()) {
            permissionLauncher.launch(missingBluetooth.toTypedArray())
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
                !Settings.canDrawOverlays(this)) {
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
                startActivity(intent)
            }
            startAirPodsService()
        }
    }

    private fun startAirPodsService() {
        val intent = Intent(this, AirPodsService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }
}