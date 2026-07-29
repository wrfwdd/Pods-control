package com.airpods.control.core.service
import android.annotation.SuppressLint
import android.app.*
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.BroadcastReceiver
import android.media.AudioDeviceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import com.airpods.control.core.bluetooth.*
import com.airpods.control.core.aacp.AacpCommander
import com.airpods.control.core.bluetooth.shizuku.ShizukuStatus
import com.airpods.control.core.data.AirPodsModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest
import javax.inject.Inject


@AndroidEntryPoint
class AirPodsService : Service() {

    @Inject lateinit var scanner: AirPodsScanner
    @Inject lateinit var gattManager: AirPodsGattManager
    @Inject lateinit var deviceManager: AirPodsDeviceManager

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Handler-based AudioManager poller (main thread, bypasses Huawei coroutine throttling)
    private val audioPollerHandler = Handler(Looper.getMainLooper())
    private var audioPollerRunning = false

    private val bluetoothAdapter: BluetoothAdapter? by lazy {
        val bm = getSystemService(Context.BLUETOOTH_SERVICE) as? android.bluetooth.BluetoothManager
        bm?.adapter
    }

    /**
     * Dynamically-registered receiver for ACL connect/disconnect events.
     * Implicit broadcasts like ACTION_ACL_CONNECTED cannot be received via
     * manifest-declared receivers on Android 8+ (Oreo).
     */
    private val aclReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                BluetoothDevice.ACTION_ACL_CONNECTED -> {
                    val device: BluetoothDevice? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                    }
                    if (device != null) {
                        checkA2dpAirPods()
                        checkBondedAirPods()
                    }
                }
                BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                    // Let state machine + verifyOrResetConnection handle cleanup
                }
            }
        }
    }

    companion object {
        const val CHANNEL_ID = "airpods_service"
        const val NOTIFICATION_ID = 1001
        const val CHANNEL_NAME = "AirPods Connection Service"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification("Starting service..."))
        // IMMEDIATE foolproof check: if ANY BT audio device is connected,
        // enter FALLBACK_READY before starting observers. This ensures
        // the user sees the main UI within 1 second on MagicUI/Huawei.
        foolproofCheckNow()
        registerAclReceiver()
        observeDeviceState()
        observeDiscoveries()
        startPeriodicRetry()
        checkBondedAirPods()
        checkA2dpAirPods()
        startAudioPoller()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        scanner.startScanning()
        verifyOrResetConnection()
        checkBondedAirPods()
        checkA2dpAirPods()
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        scanner.stopScanning()
        gattManager.disconnect()
        unregisterAclReceiver()
        stopAudioPoller()
        serviceScope.cancel()
        super.onDestroy()
    }

    /** Register ACL broadcast receiver dynamically (required on Android 8+). */
    /**
     * Immediate foolproof check: uses AudioManager only (no Bluetooth permissions)
     * to detect if ANY Bluetooth audio device is connected. If yes, enters
     * FALLBACK_READY immediately. Runs before all observers to prevent race
     * conditions between DISCOVERED -> CONNECTING -> FALLBACK_READY transitions.
     */
    private fun foolproofCheckNow() {
        try {
            val state = deviceManager.state.value
            if (state.connectionState != ConnectionState.DISCONNECTED) return
            val am = getSystemService(Context.AUDIO_SERVICE) as? android.media.AudioManager ?: return
            val found = am.getDevices(android.media.AudioManager.GET_DEVICES_ALL)
                .any { it.type == android.media.AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                       it.type == android.media.AudioDeviceInfo.TYPE_BLUETOOTH_SCO }
            if (found) {
                enterFallbackWithDeviceInfo("AirPods")
            }
        } catch (_: Exception) { }
    }

    /**
     * Enter FALLBACK_READY while preserving as much device info as possible.
     * Critical for MagicUI: without a stored MAC address, Shizuku cannot
     * establish the AACP GATT channel even after permission is granted.
     */
    @SuppressLint("MissingPermission")
    private fun enterFallbackWithDeviceInfo(defaultName: String = "AirPods") {
        var name = defaultName
        var address = ""
        var device: BluetoothDevice? = null

        // Path 1: bonded devices (most reliable for MAC on Huawei)
        try {
            val adapter = bluetoothAdapter
            if (adapter != null) {
                for (d in adapter.bondedDevices) {
                    val n = try { d.name ?: "" } catch (_: SecurityException) { "" }
                    val a = try { d.address ?: "" } catch (_: SecurityException) { "" }
                    if (n.contains("AirPods", ignoreCase = true) ||
                        n.contains("AirPod", ignoreCase = true) ||
                        isAppleMacAddress(a)) {
                        name = n.ifEmpty { defaultName }
                        address = a
                        device = d
                        break
                    }
                }
            }
        } catch (_: Exception) { }

        // Path 2: AudioManager device list
        if (address.isEmpty()) {
            try {
                val am = getSystemService(Context.AUDIO_SERVICE) as? android.media.AudioManager
                val audioDevices = am?.getDevices(android.media.AudioManager.GET_DEVICES_ALL) ?: emptyArray()
                for (info in audioDevices) {
                    val isBt = info.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                            info.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                            info.type == AudioDeviceInfo.TYPE_BLE_HEADSET
                    if (!isBt) continue
                    val addr = info.address ?: ""
                    if (addr.isEmpty() || isMaskedAddress(addr)) continue
                    val productName = info.productName?.toString() ?: ""
                    if (productName.contains("AirPods", ignoreCase = true) ||
                        isAppleMacAddress(addr)) {
                        address = addr
                        if (productName.isNotEmpty()) name = productName
                        try {
                            device = bluetoothAdapter?.getRemoteDevice(addr)
                        } catch (_: Exception) { }
                        break
                    }
                }
            } catch (_: Exception) { }
        }

        finalizeFallback(name, address, device)
    }

    private fun finalizeFallback(name: String, address: String, device: BluetoothDevice?) {
        deviceManager.updateDeviceName(name)
        if (address.isNotEmpty()) deviceManager.updateDeviceAddress(address)
        if (device != null) deviceManager.setBluetoothDevice(device)
        deviceManager.onFallbackReady()

        // Attempt Shizuku GATT if permission already granted
        if (address.isNotEmpty()) {
            try { gattManager.connectViaShizuku(address) } catch (_: Exception) { }
        }
    }

    private fun registerAclReceiver() {
        try {
            val filter = IntentFilter().apply {
                addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
                addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(aclReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                registerReceiver(aclReceiver, filter)
            }
        } catch (_: Exception) { }
    }

    private fun unregisterAclReceiver() {
        try { unregisterReceiver(aclReceiver) } catch (_: Exception) { }
    }

    /**
     * Main-thread AudioManager poller.
     * On Huawei MagicUI, coroutines on Dispatchers.IO may be throttled.
     * This Handler-based poller runs on the main thread every 2 seconds
     * and directly checks AudioManager for any BT audio device.
     * When found, immediately enters FALLBACK_READY.
     */
    private val audioPollerRunnable = object : Runnable {
        override fun run() {
            if (!audioPollerRunning) return
            try {
                val state = deviceManager.state.value
                if (state.connectionState == ConnectionState.DISCONNECTED ||
                    state.connectionState == ConnectionState.DISCOVERED) {
                    val am = getSystemService(Context.AUDIO_SERVICE) as? android.media.AudioManager
                    val found = am?.getDevices(android.media.AudioManager.GET_DEVICES_ALL)
                        ?.any { it.type == android.media.AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                                it.type == android.media.AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                                it.type == android.media.AudioDeviceInfo.TYPE_BLE_HEADSET } ?: false
                    if (found) {
                        deviceManager.updateDeviceName("AirPods")
                        enterFallbackWithDeviceInfo("AirPods")
                    }
                }
                // Stop polling once connected
                if (deviceManager.state.value.connectionState == ConnectionState.FALLBACK_READY ||
                    deviceManager.state.value.connectionState == ConnectionState.AACP_READY) {
                    audioPollerRunning = false
                }
            } catch (_: Exception) { }
            if (audioPollerRunning) {
                audioPollerHandler.postDelayed(this, 2000L)
            }
        }
    }

    private fun startAudioPoller() {
        if (audioPollerRunning) return
        audioPollerRunning = true
        audioPollerHandler.post(audioPollerRunnable)
    }

    private fun stopAudioPoller() {
        audioPollerRunning = false
        audioPollerHandler.removeCallbacks(audioPollerRunnable)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows AirPods connection status"
                setShowBadge(false)
            }
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(status: String): Notification {
        val state = deviceManager.state.value
        val title = if (state.model != AirPodsModel.UNKNOWN) state.model.displayName else "AirPods"
        val batteryText = state.battery?.let {
            "L:${it.leftPercent}% R:${it.rightPercent}% C:${it.casePercent}%"
        } ?: status

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(batteryText)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setShowWhen(false)
            .build()
    }

    private fun observeDeviceState() {
        serviceScope.launch {
            deviceManager.state.collectLatest { state ->
                try {
                    // Stop scanning once connected - reduces BLE activity
                    // Critical for Huawei MagicUI which kills apps with excessive Bluetooth usage
                    when (state.connectionState) {
                        ConnectionState.AACP_READY -> {
                            scanner.stopScanning()
                        }
                        ConnectionState.FALLBACK_READY -> {
                            // Keep scanning for BLE advertisements (battery data)
                            scanner.startScanning(scanPeriodMs = 12000, idlePeriodMs = 8000)
                        }
                        ConnectionState.DISCONNECTED -> {
                            scanner.startScanning()
                        }
                        else -> { /* keep current scan state */ }
                    }

                    val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                    nm.notify(NOTIFICATION_ID, buildNotification(
                        when (state.connectionState) {
                            ConnectionState.AACP_READY -> "Connected (AACP)"
                            ConnectionState.FALLBACK_READY -> "Connected (Standard)"
                            ConnectionState.CONNECTING -> "Connecting..."
                            else -> "Waiting for connection"
                        }
                    ))
                } catch (_: Exception) {
                    // Prevent notification update from crashing on custom ROMs
                }
            }
        }
    }


    /** Periodic retry: re-scan for connected AirPods every 8 seconds when idle. */
    private fun startPeriodicRetry() {
        serviceScope.launch {
            while (isActive) {
                try {
                    val state = deviceManager.state.value
                    if (state.connectionState == ConnectionState.DISCONNECTED ||
                        state.connectionState == ConnectionState.DISCOVERED) {
                        checkA2dpAirPods()
                        checkBondedAirPods()
                    }
                } catch (_: Exception) { }
                kotlinx.coroutines.delay(4000L)
            }
        }
    }

    private fun observeDiscoveries() {
        serviceScope.launch {
            scanner.discoveries.collectLatest { discovery ->
                val state = deviceManager.state.value
                // Only process BLE ads when not yet connected.
                // In FALLBACK_READY/AACP_READY, ignore ads to avoid
                // resetting connection state back to DISCOVERED.
                if (state.connectionState == ConnectionState.DISCONNECTED ||
                    state.connectionState == ConnectionState.DISCOVERED) {
                    deviceManager.onDiscovered(discovery.device, discovery.advertisement)
                    gattManager.connect(discovery.device)
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun checkBondedAirPods() {
        try {
            val adapter = bluetoothAdapter ?: return
            if (!adapter.isEnabled) return

            val state = deviceManager.state.value
            if (state.connectionState != ConnectionState.DISCONNECTED &&
                state.connectionState != ConnectionState.DISCOVERED) return

            val bondedDevices: Set<BluetoothDevice> = adapter.bondedDevices ?: emptySet()
            for (device in bondedDevices) {
                val name = device.name ?: ""
                val address = device.address ?: ""

                val isAirPods = name.contains("AirPods", ignoreCase = true) ||
                        name.contains("AirPod", ignoreCase = true) ||
                        isAppleMacAddress(address)

                if (isAirPods) {
                    deviceManager.onDiscovered(
                        device,
                        AppleAdvertisementParser.AirPodsAdvertisement(
                            model = AirPodsModel.guessFromName(name),
                            isCaseOpen = true
                        )
                    )
                    gattManager.connect(device)
                    break
                }
            }
        } catch (_: SecurityException) {
            // bondedDevices may throw on some customized ROMs
        } catch (_: Exception) {
            // Silently ignore
        }
    }

    /**
     * When in FALLBACK_READY or CONNECTED state, verify the device is still actively
     * connected (not just paired). Uses A2DP / AudioManager checks.
     * Resets to DISCONNECTED if the AirPods have truly disconnected.
     */
        /**
     * When in FALLBACK_READY or CONNECTED state, verify the device is still actively
     * connected. Uses A2DP / AudioManager checks.
     * Resets to DISCONNECTED only when all detection paths confirm disconnection.
     * On Huawei MagicUI, BluetoothManager may throw SecurityException and
     * AudioManager may return masked addresses; this method handles both gracefully.
     */

    /**
     * When in FALLBACK_READY or CONNECTED state, verify the device is still actively
     * connected. Uses A2DP / AudioManager checks.
     * Resets to DISCONNECTED only when all detection paths confirm disconnection.
     * On Huawei MagicUI, BluetoothManager may throw SecurityException and
     * AudioManager may return masked addresses; this method handles both gracefully.
     */
    @SuppressLint("MissingPermission")
    private fun verifyOrResetConnection() {
        try {
            val state = deviceManager.state.value
            if (state.connectionState != ConnectionState.FALLBACK_READY &&
                state.connectionState != ConnectionState.CONNECTED &&
                state.connectionState != ConnectionState.CONNECTING &&
                state.connectionState != ConnectionState.AACP_READY) return

            val deviceAddr = state.deviceAddress
            val deviceName = state.deviceName

            var stillAvailable = false

            // Path 1: Check A2DP via BluetoothManager
            if (deviceAddr.isNotEmpty()) {
                try {
                    val bm = getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
                    val a2dpDevices = bm?.getConnectedDevices(BluetoothProfile.A2DP) ?: emptyList()
                    stillAvailable = a2dpDevices.any { it.address == deviceAddr }
                } catch (_: SecurityException) { } catch (_: Exception) { }
            }

            // Path 2: Check active audio output via AudioManager (bypasses BT permission stack)
            if (!stillAvailable) {
                try {
                    val am = getSystemService(Context.AUDIO_SERVICE) as? android.media.AudioManager
                    val audioDevices = am?.getDevices(android.media.AudioManager.GET_DEVICES_OUTPUTS) ?: emptyArray()
                    stillAvailable = audioDevices.any {
                        it.type == android.media.AudioDeviceInfo.TYPE_BLUETOOTH_A2DP &&
                        (it.address == deviceAddr ||
                         (deviceName.isNotEmpty() &&
                          it.productName?.toString()?.contains(deviceName, ignoreCase = true) == true))
                    }
                } catch (_: Exception) { }
            }

            //             // Path 3: If still no match, check if ANY Bluetooth A2DP audio is active.
            // On MagicUI, address comparison may fail due to masking; presence of any
            // BT A2DP device strongly suggests the AirPods are still connected.
            if (!stillAvailable) {
                try {
                    val am = getSystemService(Context.AUDIO_SERVICE) as? android.media.AudioManager
                    // Check both ALL devices and OUTPUTS for robustness
                    var activeBt = am?.getDevices(android.media.AudioManager.GET_DEVICES_ALL)
                        ?.any { it.type == android.media.AudioDeviceInfo.TYPE_BLUETOOTH_A2DP } ?: false
                    if (!activeBt) {
                        activeBt = am?.getDevices(android.media.AudioManager.GET_DEVICES_OUTPUTS)
                            ?.any { it.type == android.media.AudioDeviceInfo.TYPE_BLUETOOTH_A2DP } ?: false
                    }
                    if (activeBt) stillAvailable = true
                } catch (_: Exception) { }
            }
            if (!stillAvailable) {
                try {
                    val am = getSystemService(Context.AUDIO_SERVICE) as? android.media.AudioManager
                    val activeBt = am?.getDevices(android.media.AudioManager.GET_DEVICES_OUTPUTS)
                        ?.any { it.type == android.media.AudioDeviceInfo.TYPE_BLUETOOTH_A2DP } ?: false
                    if (activeBt) stillAvailable = true
                } catch (_: Exception) { }
            }

            if (!stillAvailable) {
                deviceManager.onDisconnected()
            }
        } catch (_: Exception) {
            // If verification itself fails, preserve current state
        }
    }


    /**
     * Detect AirPods that are already connected via A2DP (classic Bluetooth audio).
     * When AirPods are connected for audio, the GATT connection may fail:
     * this ensures we still enter FALLBACK_READY and show the UI.
     *
     * On Huawei MagicUI, BluetoothManager.getConnectedDevices() and
     * BluetoothAdapter.bondedDevices may throw SecurityException.
     * AudioManager.getDevices() is used as the primary fallback since it
     * bypasses the Bluetooth permission stack entirely.
     */
    @SuppressLint("MissingPermission")
    private fun checkA2dpAirPods() {
        try {
            val adapter = bluetoothAdapter ?: return
            val state = deviceManager.state.value
            if (state.connectionState != ConnectionState.DISCONNECTED &&
                state.connectionState != ConnectionState.DISCOVERED) return

            var found = false

            // Path 1: BluetoothManager.getConnectedDevices(A2DP)
            try {
                val bm = getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
                val a2dpDevices = bm?.getConnectedDevices(BluetoothProfile.A2DP)
                if (a2dpDevices != null) {
                    for (device in a2dpDevices) {
                        if (tryConnectAirPodsDevice(device)) { found = true; break }
                    }
                }
            } catch (_: SecurityException) { } catch (_: Exception) { }

            // Path 2: AudioManager.getDevices(ALL) -- bypasses Huawei permission stack
            if (!found) found = discoverAirPodsViaAudio(adapter)

            // Path 3: Bonded devices fallback
            if (!found) found = discoverAirPodsViaBonded(adapter)

            // Path 4 (Foolproof): When all else fails on MagicUI,
            // check if ANY Bluetooth A2DP device is connected via AudioManager.
            // If yes, enter FALLBACK_READY so the user sees the main UI
            // instead of a permanent "Not Connected" screen.
            if (!found && state.connectionState == ConnectionState.DISCONNECTED) {
                try {
                    val am = getSystemService(Context.AUDIO_SERVICE) as? android.media.AudioManager
                    // Try GET_DEVICES_ALL first (broader detection than OUTPUTS)
                    var activeBt = am?.getDevices(android.media.AudioManager.GET_DEVICES_ALL)
                        ?.any { it.type == android.media.AudioDeviceInfo.TYPE_BLUETOOTH_A2DP } ?: false
                    // Fallback: also check GET_DEVICES_OUTPUTS
                    if (!activeBt) {
                        activeBt = am?.getDevices(android.media.AudioManager.GET_DEVICES_OUTPUTS)
                            ?.any { it.type == android.media.AudioDeviceInfo.TYPE_BLUETOOTH_A2DP } ?: false
                    }
                    if (activeBt) {
                        enterFallbackWithDeviceInfo("AirPods")
                    }
                } catch (_: Exception) { }
            }
        } catch (_: Exception) { }
    }

    /**
     * Discover AirPods via AudioManager (works when BluetoothManager throws).
     * On MagicUI/Huawei, AudioDeviceInfo.productName often returns generic names
     * like "generic translation" instead of "AirPods Pro".
     * This method bypasses productName by calling BluetoothDevice.getName()
     * for every BT audio device found, which returns the real device name.
     */
    @SuppressLint("MissingPermission")
    private fun discoverAirPodsViaAudio(adapter: BluetoothAdapter): Boolean {
        try {
            val am = getSystemService(Context.AUDIO_SERVICE) as? android.media.AudioManager ?: return false
            val devices = am.getDevices(android.media.AudioManager.GET_DEVICES_ALL)
            for (info in devices) {
                val isBt = info.type == android.media.AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                        info.type == android.media.AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                        info.type == android.media.AudioDeviceInfo.TYPE_BLE_HEADSET
                if (!isBt) continue

                val addr = info.address ?: ""
                if (addr.isEmpty()) continue
                val productName = info.productName?.toString() ?: ""

                // Try direct approach: getRemoteDevice + tryConnectAirPodsDevice
                // Pass addr and productName directly since BluetoothDevice.name/address
                // throw SecurityException on MagicUI/Huawei.
                try {
                    val device = adapter.getRemoteDevice(addr)
                    if (tryConnectAirPodsDevice(device, knownAddress = addr, knownName = productName)) return true
                } catch (_: Exception) { }

                // If address is masked and getRemoteDevice fails,
                // try to find a matching bonded device by product name
                if (productName.isNotEmpty()) {
                    try {
                        val bonded = findBondedByName(adapter, productName)
                        if (bonded != null && tryConnectAirPodsDevice(bonded, knownAddress = addr, knownName = productName)) return true
                    } catch (_: SecurityException) { }
                }
            }

            // Last resort: iterate all bonded devices looking for AirPods
            try {
                for (device in adapter.bondedDevices) {
                    if (tryConnectAirPodsDevice(device)) return true
                }
            } catch (_: SecurityException) { }
        } catch (_: SecurityException) { } catch (_: Exception) { }
        return false
    }

    /** Fallback: iterate bonded devices looking for AirPods by name or MAC. */
    @SuppressLint("MissingPermission")
    private fun discoverAirPodsViaBonded(adapter: BluetoothAdapter): Boolean {
        try {
            for (device in adapter.bondedDevices) {
                if (tryConnectAirPodsDevice(device)) return true
            }
        } catch (_: SecurityException) { } catch (_: Exception) { }
        return false
    }

    /** Find a bonded device whose name matches (or contains) the given name. */
    @SuppressLint("MissingPermission")
    private fun findBondedByName(adapter: BluetoothAdapter, target: String): BluetoothDevice? {
        try {
            for (d in adapter.bondedDevices) {
                val n = d.name ?: ""
                if (n.equals(target, ignoreCase = true) ||
                    (n.isNotEmpty() && target.contains(n, ignoreCase = true))) return d
            }
        } catch (_: SecurityException) { } catch (_: Exception) { }
        return null
    }

    /** Check if address is a privacy-masked dummy (no location permission). */
    private fun isMaskedAddress(address: String): Boolean {
        val n = address.replace(Regex("[:-]"), "").uppercase()
        return n.startsWith("020000") || n.length < 12
    }
        /**
     * Try to identify and connect to an AirPods device.
     * On MagicUI/Huawei, device.name and device.address may throw
     * SecurityException even when BLUETOOTH_CONNECT is granted.
     * All Bluetooth API calls are wrapped in try-catch for resilience.
     */
    @SuppressLint("MissingPermission")
    private fun tryConnectAirPodsDevice(
        device: BluetoothDevice,
        knownAddress: String? = null,
        knownName: String? = null
    ): Boolean {
        return try {
            // On MagicUI, device.name and device.address throw SecurityException.
            // Use knownAddress/knownName (from AudioManager) as fallback.
            val name = knownName?.takeIf { it.isNotEmpty() }
                ?: try { device.name ?: "" } catch (_: SecurityException) { "" }
            val address = knownAddress?.takeIf { it.isNotEmpty() }
                ?: try { device.address ?: "" } catch (_: SecurityException) { "" }
            val isAirPods = name.contains("AirPods", ignoreCase = true) ||
                    name.contains("AirPod", ignoreCase = true) ||
                    isAppleMacAddress(address)
            if (isAirPods) {
                val effectiveAddress = address.ifEmpty { knownAddress ?: "" }
                val effectiveName = name.ifEmpty { knownName ?: "" }
                deviceManager.onDiscovered(
                    device,
                    AppleAdvertisementParser.AirPodsAdvertisement(
                        model = AirPodsModel.guessFromName(effectiveName.ifEmpty { name }),
                        isCaseOpen = true
                    ),
                    effectiveName = effectiveName,
                    effectiveAddress = effectiveAddress
                )
                gattManager.connect(device)
                // Also attempt Shizuku GATT bypass for Huawei MagicUI
                if (effectiveAddress.isNotEmpty()) {
                    gattManager.connectViaShizuku(effectiveAddress)
                }
                true
            } else {
                false
            }
        } catch (_: SecurityException) {
            false
        }
    }

    /** Check if a Bluetooth MAC address belongs to Apple's OUI range. */
    private fun isAppleMacAddress(address: String): Boolean {
        // Normalize: strip colons/dashes/spaces, uppercase
        val normalized = address.replace(Regex("[:-]"), "").uppercase()
        if (normalized.length < 6) return false
        val prefix = normalized.substring(0, 6)
        return prefix in setOf(
            "ACBC32", "ACFDEC", "AC7F3E", "F0CBA1",
            "D89695", "A4D1D2", "38C986", "CC25EF",
            "D461DA", "C869CD", "B817C2", "80E650",
            "00DB70", "F41BA1", "F81EDF", "CC088D",
            "C8BCC8", "8C2937", "A8968A", "F01898",
            "E0B52D", "1093E9", "A0EDCD", "28E7CF",
            "58404E", "D03311", "BCEC5D", "CC785F",
            "442A60", "60FEC5", "F07959", "380F4A",
            "D4DCCD", "4C3275", "B89096", "E428EB",
            "949426", "E0C767", "503237", "B418D1",
            "B8E856", "186590"
        )
    }

    fun getRomWhitelistIntents(context: Context): List<Pair<String, Intent>> {
        val intents = mutableListOf<Pair<String, Intent>>()
        val powerIntent = Intent(android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
        intents.add("Battery Optimization" to powerIntent)

        val romIntents = mapOf(
            "com.huawei.systemmanager" to listOf(
                "Huawei Auto-Start" to "com.huawei.systemmanager/.startupmgr.ui.StartupNormalAppListActivity",
                "Huawei Lock Screen Cleanup" to "com.huawei.systemmanager/.optimize.process.ProtectActivity"
            ),
            "com.miui.securitycenter" to listOf(
                "Xiaomi Auto-Start" to "com.miui.securitycenter/.autostart.AutoStartManagementActivity"
            ),
            "com.coloros.safecenter" to listOf(
                "OPPO Auto-Start" to "com.coloros.safecenter/.permission.startup.StartupAppListActivity"
            ),
            "com.oppo.safe" to listOf(
                "OPPO Auto-Start" to "com.oppo.safe/.permission.startup.StartupAppListActivity"
            ),
            "com.vivo.permissionmanager" to listOf(
                "vivo Auto-Start" to "com.vivo.permissionmanager/.activity.SoftPermissionDetailActivity"
            ),
            "com.samsung.android.lool" to listOf(
                "Samsung Battery" to "com.samsung.android.lool/.appui.BatteryOptimizationActivity"
            )
        )

        romIntents.forEach { (pkg, intents_) ->
            try {
                context.packageManager.getPackageInfo(pkg, 0)
                intents_.forEach { (label, className) ->
                    val i = Intent().setClassName(pkg, className)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    intents.add(label to i)
                }
            } catch (_: Exception) { }
        }

        return intents
    }
}
