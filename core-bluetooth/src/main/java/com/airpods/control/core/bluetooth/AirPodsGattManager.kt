package com.airpods.control.core.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.*
import android.content.Context
import com.airpods.control.core.aacp.AacpCommander
import com.airpods.control.core.aacp.AacpProtocol
import com.airpods.control.core.aacp.AacpTransport
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.util.UUID
import com.airpods.control.core.bluetooth.shizuku.ShizukuGattClient
import com.airpods.control.core.bluetooth.shizuku.ShizukuAacpTransport
import com.airpods.control.core.bluetooth.shizuku.ShizukuStatus
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages BLE GATT connection to a single AirPods device.
 * Sets up AACP channel and provides [AacpTransport] to [AacpCommander].
 */
@Singleton
class AirPodsGattManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val deviceManager: AirPodsDeviceManager
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var bluetoothGatt: BluetoothGatt? = null
    private var aacpDataChar: BluetoothGattCharacteristic? = null
    private var aacpNotifyChar: BluetoothGattCharacteristic? = null

    private val _notifications = MutableSharedFlow<ByteArray>(extraBufferCapacity = 16)
    private var gattTransport: GattAacpTransport? = null
    private var aacpSetupPending = false
    private var shizukuClient: ShizukuGattClient? = null
    private var shizukuConnecting = false
    private val _shizukuState = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val shizukuState: SharedFlow<String> = _shizukuState.asSharedFlow()

    sealed class ConnectionEvent {
        data class Connected(val device: BluetoothDevice) : ConnectionEvent()
        data class Disconnected(val device: BluetoothDevice) : ConnectionEvent()
        data class AacpReady(val commander: AacpCommander) : ConnectionEvent()
        data class AacpFailed(val reason: String = "Service not found") : ConnectionEvent()
        data class Error(val message: String) : ConnectionEvent()
    }

    private val _connectionEvents = MutableSharedFlow<ConnectionEvent>(extraBufferCapacity = 8)
    val connectionEvents: SharedFlow<ConnectionEvent> = _connectionEvents.asSharedFlow()

    private val gattCallback = @SuppressLint("MissingPermission")
    object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            try {
            scope.launch {
                when (newState) {
                    BluetoothProfile.STATE_CONNECTED -> {
                        if (status == BluetoothGatt.GATT_SUCCESS) {
                            deviceManager.updateConnectionState(ConnectionState.CONNECTED)
                            _connectionEvents.emit(ConnectionEvent.Connected(gatt.device))
                            try { gatt.discoverServices() } catch (_: SecurityException) {
                                handleGattFailure(gatt.device, "discoverServices blocked by ROM")
                            }
                        } else {
                            handleGattFailure(gatt.device, "GATT connect failed (status=$status)")
                            _connectionEvents.emit(ConnectionEvent.Disconnected(gatt.device))
                        }
                    }
                    BluetoothProfile.STATE_DISCONNECTED -> {
                        handleGattFailure(gatt.device, "GATT disconnected")
                        _connectionEvents.emit(ConnectionEvent.Disconnected(gatt.device))
                        try { gatt.close() } catch (_: Exception) { }
                    }
                }
            }
        } catch (e: Exception) {
                // Prevent Binder-thread exceptions from crashing the app
        }
    }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            try {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                // Service discovery failed - fall back to standard Bluetooth if A2DP is connected
                scope.launch {
                    deviceManager.onFallbackReady()
                    _connectionEvents.emit(ConnectionEvent.AacpFailed("Service discovery failed"))
                }
                return
            }
            val aacpService = gatt.getService(UUID.fromString(AacpProtocol.AACP_SERVICE_UUID))
            if (aacpService != null) {
                aacpDataChar = aacpService.getCharacteristic(
                    UUID.fromString(AacpProtocol.AACP_DATA_CHAR_UUID)
                )
                aacpNotifyChar = aacpService.getCharacteristic(
                    UUID.fromString(AacpProtocol.AACP_NOTIFY_CHAR_UUID)
                )
                if (aacpNotifyChar != null) {
                    gatt.setCharacteristicNotification(aacpNotifyChar, true)
                    // Write CCCD descriptor to enable notifications
                    aacpNotifyChar!!.descriptors.find {
                        it.uuid == UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
                    }?.let { descriptor ->
                        descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                        gatt.writeDescriptor(descriptor)
                    }
                }
                if (aacpDataChar != null && aacpNotifyChar != null) {
                    val transport = GattAacpTransport(gatt, aacpDataChar!!, aacpNotifyChar!!)
                    gattTransport = transport
                    val commander = AacpCommander(transport)
                    deviceManager.onAacpReady(commander)
                    scope.launch {
                        _connectionEvents.emit(ConnectionEvent.AacpReady(commander))
                    }
                } else {
                    deviceManager.onFallbackReady()
                    scope.launch {
                        _connectionEvents.emit(ConnectionEvent.AacpFailed("Characteristics not found"))
                    }
                }
            } else {
                // No AACP service ?? fallback to standard Bluetooth
                deviceManager.onFallbackReady()
                scope.launch {
                    _connectionEvents.emit(ConnectionEvent.AacpFailed("AACP service UUID not present"))
                }
        }

            } catch (e: Exception) {
                deviceManager.onFallbackReady()
                scope.launch {
                    _connectionEvents.emit(ConnectionEvent.AacpFailed("onServicesDiscovered error: ${e.message}"))
                }
            }
        }

        override fun onDescriptorWrite(
            gatt: BluetoothGatt,
            descriptor: BluetoothGattDescriptor,
            status: Int
        ) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                // CCCD notification enabled - AirPods will now push events
            }
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            if (characteristic.uuid == UUID.fromString(AacpProtocol.AACP_NOTIFY_CHAR_UUID)) {
                val data = characteristic.value
                if (data != null) {
                    scope.launch { _notifications.emit(data) }
                }
            }
        }
    }

                @SuppressLint("MissingPermission")
    fun connect(device: BluetoothDevice) {
        try {
        // Safely check existing connection (device.address blocked on MagicUI)
        try {
            if (bluetoothGatt?.device?.address == device.address) return
        } catch (_: SecurityException) { }
        
        deviceManager.updateConnectionState(ConnectionState.CONNECTING)

        // Path 0: try existing system GATT connection
        try { if (tryUseExistingGatt(device)) return } catch (_: SecurityException) { }

        // Path 1: TRANSPORT_LE direct
        try { bluetoothGatt = device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE) }
        catch (_: SecurityException) { }

        // Path 2: TRANSPORT_BREDR (classic Bluetooth transport)
        if (bluetoothGatt == null) {
            try { bluetoothGatt = device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_BREDR) }
            catch (_: SecurityException) { }
        }

        // Path 3: TRANSPORT_AUTO (system decides)
        if (bluetoothGatt == null) {
            try { bluetoothGatt = device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_AUTO) }
            catch (_: SecurityException) { }
        }

        // Path 4: TRANSPORT_LE with autoConnect=true (background)
        if (bluetoothGatt == null) {
            try { bluetoothGatt = device.connectGatt(context, true, gattCallback, BluetoothDevice.TRANSPORT_LE) }
            catch (_: SecurityException) { }
        }

        // Path 5: Reflection bypass
        if (bluetoothGatt == null) {
            try { bluetoothGatt = connectGattViaReflection(device) }
            catch (_: Exception) { }
        }

        if (bluetoothGatt == null) {
            deviceManager.onFallbackReady()
        }
        } catch (_: Exception) {
            // Entire connect flow failed - safe fallback
            deviceManager.onFallbackReady()
        }
    }

    /**
     * Try to connect GATT via reflection, bypassing Huawei permission wrapper.
     * On MagicUI, the standard connectGatt() throws SecurityException even
     * when BLUETOOTH_CONNECT is granted. The hidden method may work.
     */
    @SuppressLint("MissingPermission")
    private fun connectGattViaReflection(device: BluetoothDevice): BluetoothGatt? {
        return try {
            // Try to get the hidden connectGatt method with different signatures
            val methods = BluetoothDevice::class.java.declaredMethods
            for (method in methods) {
                if (method.name == "connectGatt" && method.parameterTypes.size >= 4) {
                    method.isAccessible = true
                    val result = method.invoke(device, context, true, gattCallback, BluetoothDevice.TRANSPORT_LE)
                    if (result is BluetoothGatt) return result
                }
            }
            null
        } catch (_: Exception) {
            null
        }
    }

        /**
     * Check if Android system already holds a GATT connection to this device.
     * On Android 11+ the system may maintain GATT for battery reporting.
     */
        @SuppressLint("MissingPermission")
    fun tryUseExistingGatt(device: BluetoothDevice): Boolean {
        return try {
            val bm = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager ?: return false
            val connectedGattDevices = bm.getConnectedDevices(BluetoothProfile.GATT) ?: emptyList()
            for (d in connectedGattDevices) {
                if (d.address == device.address) {
                    bluetoothGatt = device.connectGatt(context, true, gattCallback, BluetoothDevice.TRANSPORT_LE)
                    return bluetoothGatt != null
                }
            }
            false
        } catch (_: SecurityException) {
            false
        }
    }

    @SuppressLint("MissingPermission")
    fun disconnect() {
        shizukuClient?.destroy()
        shizukuClient = null
        bluetoothGatt?.disconnect()
        bluetoothGatt?.close()
        bluetoothGatt = null
        gattTransport = null
        deviceManager.onDisconnected()
    }

    /**
     * Handle GATT connection failure: check if the device is still connected
     * via A2DP or audio output. On Huawei MagicUI, BluetoothManager may throw
     * SecurityException; AudioManager fallback is used instead.
     * When in doubt, fall back to FALLBACK_READY rather than disconnecting.
     */
    @SuppressLint("MissingPermission")
    private fun handleGattFailure(device: BluetoothDevice, reason: String) {
        try {
            var stillConnected = false
            // Path 1: Check A2DP via BluetoothManager
            try {
                val bm = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
                val connectedDevices = bm?.getConnectedDevices(BluetoothProfile.A2DP) ?: emptyList()
                stillConnected = connectedDevices.any { it.address == device.address }
            } catch (_: SecurityException) { }
            // Path 2: Check audio output via AudioManager
            if (!stillConnected) {
                try {
                    val am = context.getSystemService(Context.AUDIO_SERVICE) as? android.media.AudioManager
                    val devices = am?.getDevices(android.media.AudioManager.GET_DEVICES_OUTPUTS) ?: emptyArray()
                    stillConnected = devices.any {
                        it.type == android.media.AudioDeviceInfo.TYPE_BLUETOOTH_A2DP &&
                        it.address == device.address
                    }
                } catch (_: Exception) { }
            }
            // Path 3: Foolproof - check if ANY BT A2DP device is active
            if (!stillConnected) {
                try {
                    val am = context.getSystemService(Context.AUDIO_SERVICE) as? android.media.AudioManager
                    stillConnected = am?.getDevices(android.media.AudioManager.GET_DEVICES_ALL)
                        ?.any { it.type == android.media.AudioDeviceInfo.TYPE_BLUETOOTH_A2DP } ?: false
                } catch (_: Exception) { }
            }
            if (stillConnected) {
                deviceManager.onFallbackReady()
                scope.launch { _connectionEvents.emit(ConnectionEvent.AacpFailed(
                    "$reason - falling back to standard BT")) }
            } else {
                deviceManager.onGattDisconnected()
            }
        } catch (_: Exception) {
            // When in doubt, preserve connection
            deviceManager.onFallbackReady()
        }
    }

    /**
     * Check whether this device is currently the active Bluetooth audio output.
     * Uses AudioManager.getDevices() which queries the audio framework directly
     * and works reliably across all ROMs including Huawei MagicUI.
     */
    private fun isAudioOutputActive(device: BluetoothDevice): Boolean {
        return try {
            val am = context.getSystemService(Context.AUDIO_SERVICE) as? android.media.AudioManager ?: return false
            val audioDevices = am.getDevices(android.media.AudioManager.GET_DEVICES_OUTPUTS)
            audioDevices.any { info ->
                info.type == android.media.AudioDeviceInfo.TYPE_BLUETOOTH_A2DP &&
                info.address == device.address
            }
        } catch (_: Exception) {
            false
        }
    }

    /** Check whether a device has an active A2DP (classic Bluetooth audio) connection. */
    @SuppressLint("MissingPermission")
    private fun isA2dpConnected(device: BluetoothDevice): Boolean {
        return try {
            val bm = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager ?: return false
            val connectedDevices = bm.getConnectedDevices(BluetoothProfile.A2DP) ?: return false
            connectedDevices.any { it.address == device.address }
        } catch (_: SecurityException) {
            // Some ROMs (e.g., Huawei MagicUI) throw SecurityException on getConnectedDevices()
            false
        } catch (_: Exception) {
            false
        }
    }


    /**
     * Connect GATT via Shizuku (shell UID bypass for MagicUI).
     * Call after Shizuku authorization is granted.
     */
    /**
     * Try Shizuku-assisted GATT connection.
     * Strategy: use Shizuku to run a shell command that tries to
     * temporarily bypass MagicUI Bluetooth restrictions, then
     * attempt connectGatt() from the main process.
     */
    fun connectViaShizuku(address: String) {
        if (!ShizukuStatus.isReady()) {
            _shizukuState.tryEmit("Shizuku not ready")
            return
        }
        if (address.isEmpty()) {
            _shizukuState.tryEmit("No device address")
            return
        }
        _shizukuState.tryEmit("Attempting Shizuku bypass...")
        scope.launch {
            try {
                // Method 1: Try via Shizuku UserService
                val client = ShizukuGattClient(context.applicationContext)
                shizukuClient = client
                client.bind()
                delay(300)
                client.connect(address)
                delay(500)
                client.discover()

                // Wait for result
                var result: String? = null
                val job = launch {
                    client.events.collect { event ->
                        when (event) {
                            is ShizukuGattClient.Event.AacpReady -> {
                                result = "AacpReady"
                                client.setNotify(true)
                                val transport = ShizukuAacpTransport(client)
                                transport.startCollecting()
                                deviceManager.onAacpReady(AacpCommander(transport))
                                _connectionEvents.emit(ConnectionEvent.AacpReady((deviceManager.aacpCommander ?: return@launch)))
                            }
                            is ShizukuGattClient.Event.Error -> {
                                result = "Error: ${event.msg}"
                            }
                            is ShizukuGattClient.Event.Connected -> {
                                _shizukuState.tryEmit("GATT connected via Shizuku")
                            }
                            else -> {}
                        }
                    }
                }
                
                withTimeout(8000) { while (result == null) { delay(100) } }
                job.cancel()
                
                if (result == null || result!!.startsWith("Error")) {
                    _shizukuState.tryEmit(result ?: "Shizuku GATT timeout")
                    // Method 2: Try direct connectGatt from main process
                    // (Shizuku authorization might have changed something)
                    _shizukuState.tryEmit("Trying direct connect...")
                    tryDirectConnect(address)
                }
            } catch (e: Exception) {
                _shizukuState.tryEmit("Shizuku err: ${e.message}")
                tryDirectConnect(address)
            }
        }
    }

    /**
     * Direct GATT connect from main process.
     * Called as fallback when Shizuku UserService fails.
     */
    private fun tryDirectConnect(address: String) {
        try {
            val adapter = BluetoothAdapter.getDefaultAdapter() ?: return
            val device = adapter.getRemoteDevice(address)
            
            // Try TRANSPORT_LE first
            try {
                bluetoothGatt = device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
            } catch (_: SecurityException) { }
            
            if (bluetoothGatt == null) {
                try {
                    bluetoothGatt = device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_AUTO)
                } catch (_: SecurityException) { }
            }
            
            if (bluetoothGatt != null) {
                _shizukuState.tryEmit("Direct GATT connected!")
                bluetoothGatt?.discoverServices()
            } else {
                _shizukuState.tryEmit("All GATT methods blocked")
            }
        } catch (_: Exception) {
            _shizukuState.tryEmit("Direct connect failed")
        }
    }


    fun destroy() {
        disconnect()
        scope.cancel()
    }

    // ---- Internal GATT-based AACP transport ----
    private inner class GattAacpTransport(
        private val gatt: BluetoothGatt,
        private val dataChar: BluetoothGattCharacteristic,
        private val notifyChar: BluetoothGattCharacteristic
    ) : AacpTransport {

        override val isConnected: Boolean
            get() = deviceManager.isConnected()

        @SuppressLint("MissingPermission")
        override suspend fun sendCommand(frame: ByteArray): ByteArray? {
            return withContext(Dispatchers.IO) {
                try {
                    dataChar.value = frame
                    dataChar.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                    if (!gatt.writeCharacteristic(dataChar)) return@withContext null
                    // Wait for notification response (simplified: single-response model)
                    // In production, use a correlation ID + CompletableDeferred map
                    withTimeout(3000L) {
                        suspendCancellableCoroutine<ByteArray?> { cont ->
                            val job = scope.launch {
                                _notifications.collect { data ->
                                    if (cont.isActive) {
                                        cont.resume(data) {}
                                        this@launch.cancel()
                                    }
                                }
                            }
                            cont.invokeOnCancellation { job.cancel() }
                        }
                    }
                } catch (_: Exception) {
                    null
                }
            }
        }

        override fun notificationFlow(): Flow<ByteArray> = _notifications.asSharedFlow()
    }
}

