package com.airpods.control.core.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.ParcelUuid
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * BLE scanner that watches for AirPods advertisements.
 * Uses Apple Company ID filter (0x004C) and known AirPods GATT service UUIDs.
 * Respects battery: uses interval scanning with duty cycling when not connected.
 */
@Singleton
class AirPodsScanner @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter
    private var leScanner: BluetoothLeScanner? = null
    private var isScanning = false

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _discoveries = MutableSharedFlow<AirPodsDiscovery>(replay = 0, extraBufferCapacity = 8)
    val discoveries: SharedFlow<AirPodsDiscovery> = _discoveries.asSharedFlow()

    data class AirPodsDiscovery(
        val device: BluetoothDevice,
        val advertisement: AppleAdvertisementParser.AirPodsAdvertisement,
        val rssi: Int
    )

    private val scanCallback = @SuppressLint("MissingPermission")
    object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device
            val adv = AppleAdvertisementParser.parse(device, result.scanRecord?.bytes)
            if (adv != null) {
                scope.launch {
                    _discoveries.emit(AirPodsDiscovery(device, adv, result.rssi))
                }
            }
        }

        override fun onScanFailed(errorCode: Int) {
            // Log silently; consumer handles retry via state machine
        }
    }

    /**
     * Start continuous scanning with duty cycling to save power.
     * @param scanPeriodMs scan duration per cycle
     * @param idlePeriodMs idle duration between cycles
     */
    @SuppressLint("MissingPermission")
    fun startScanning(scanPeriodMs: Long = 8000, idlePeriodMs: Long = 2000) {
        if (isScanning) return
        isScanning = true
        leScanner = bluetoothAdapter?.bluetoothLeScanner

        // No hardware filter - software filtering via AppleAdvertisementParser
        // is more reliable across different AirPods firmware versions and connection states.
        val filters: List<ScanFilter> = emptyList()

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_BALANCED)
            .setReportDelay(0)
            .build()

        scope.launch {
            while (isActive && isScanning) {
                try {
                    leScanner?.startScan(filters, settings, scanCallback)
                    delay(scanPeriodMs)
                    leScanner?.stopScan(scanCallback)
                    delay(idlePeriodMs)
                } catch (_: SecurityException) {
                    // Permission missing; stop
                    isScanning = false
                    break
                }
            }
        }
    }

    fun stopScanning() {
        isScanning = false
        try {
            leScanner?.stopScan(scanCallback)
        } catch (_: SecurityException) { }
    }

    fun destroy() {
        stopScanning()
        scope.cancel()
    }
}
