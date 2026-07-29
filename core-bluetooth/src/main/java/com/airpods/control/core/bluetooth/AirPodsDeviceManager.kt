package com.airpods.control.core.bluetooth

import android.bluetooth.BluetoothDevice
import com.airpods.control.core.aacp.AacpCommander
import com.airpods.control.core.aacp.AacpResponseParser
import com.airpods.control.core.data.AirPodsModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Connection state machine. All UI subscribes to these flows.
 * No direct Bluetooth operations from UI.
 */
enum class ConnectionState {
    DISCONNECTED,       // No AirPods in range
    DISCOVERED,         // Found via BLE scan, not connected
    CONNECTING,         // ACL connection in progress
    CONNECTED,          // ACL connected, AACP handshake pending
    AACP_READY,         // AACP channel open, full control available
    FALLBACK_READY      // AACP failed, using standard BT profiles only
}

data class DeviceState(
    val connectionState: ConnectionState = ConnectionState.DISCONNECTED,
    val model: AirPodsModel = AirPodsModel.UNKNOWN,
    val deviceName: String = "",
    val deviceAddress: String = "",
    val battery: AacpResponseParser.BatteryState? = null,
    val listeningMode: Byte = 0,
    val inEar: AacpResponseParser.InEarState? = null,
    val spatial: AacpResponseParser.SpatialState? = null,
    val firmware: String? = null,
    val serialNumber: String? = null,
    val conversationAware: Boolean = false,
    val adaptiveIntensity: Int = 0,
    val micMode: Byte = 0,
    val eqEnabled: Boolean = false,
    val earTipResult: AacpResponseParser.EarTipResult? = null,
    val isCaseOpen: Boolean = false,
    val isCharging: Boolean = false
)

class AirPodsDeviceManager {

    private val _state = MutableStateFlow(DeviceState())
    val state: StateFlow<DeviceState> = _state.asStateFlow()

    var aacpCommander: AacpCommander? = null
        private set

    var bluetoothDevice: BluetoothDevice? = null
        private set

    fun updateConnectionState(newState: ConnectionState) {
        _state.value = _state.value.copy(connectionState = newState)
    }

    fun onDiscovered(
        device: BluetoothDevice,
        adv: AppleAdvertisementParser.AirPodsAdvertisement,
        effectiveName: String? = null,
        effectiveAddress: String? = null
    ) {
        bluetoothDevice = device
        // On MagicUI, device.name and device.address throw SecurityException.
        // Use effectiveName/effectiveAddress when provided.
        val name = effectiveName?.takeIf { it.isNotEmpty() }
            ?: try { device.name } catch (_: SecurityException) { adv.model.displayName }
        val addr = effectiveAddress?.takeIf { it.isNotEmpty() }
            ?: try { device.address } catch (_: SecurityException) { "" }
        _state.value = _state.value.copy(
            connectionState = ConnectionState.DISCOVERED,
            model = adv.model,
            deviceName = name ?: adv.model.displayName,
            deviceAddress = addr,
            battery = if (adv.batteryLeft >= 0) AacpResponseParser.BatteryState(
                leftPercent = adv.batteryLeft,
                rightPercent = adv.batteryRight,
                casePercent = adv.batteryCase,
                leftCharging = false,
                rightCharging = false,
                caseCharging = adv.isCharging
            ) else null,
            isCaseOpen = adv.isCaseOpen,
            isCharging = adv.isCharging
        )
    }

    fun onAacpReady(commander: AacpCommander) {
        aacpCommander = commander
        _state.value = _state.value.copy(connectionState = ConnectionState.AACP_READY)
    }

    fun onFallbackReady() {
        _state.value = _state.value.copy(connectionState = ConnectionState.FALLBACK_READY)
    }

    fun onDisconnected() {
        aacpCommander = null
        bluetoothDevice = null
        _state.value = DeviceState()
    }

    /** Called when GATT connection fails - preserves discovered device info. */
    fun onGattDisconnected() {
        aacpCommander = null
        _state.value = _state.value.copy(
            connectionState = ConnectionState.DISCONNECTED
        )
    }

    fun updateBattery(battery: AacpResponseParser.BatteryState) {
        _state.value = _state.value.copy(battery = battery)
    }

    fun updateListeningMode(mode: Byte) {
        _state.value = _state.value.copy(listeningMode = mode)
    }

    fun updateInEar(inEar: AacpResponseParser.InEarState) {
        _state.value = _state.value.copy(inEar = inEar)
    }

    fun updateSpatial(spatial: AacpResponseParser.SpatialState) {
        _state.value = _state.value.copy(spatial = spatial)
    }

    fun updateFirmware(version: String, serial: String?) {
        _state.value = _state.value.copy(firmware = version, serialNumber = serial)
    }

    fun updateConversationAware(enabled: Boolean) {
        _state.value = _state.value.copy(conversationAware = enabled)
    }

    fun updateAdaptive(intensity: Int) {
        _state.value = _state.value.copy(adaptiveIntensity = intensity)
    }

    fun updateMicMode(mode: Byte) {
        _state.value = _state.value.copy(micMode = mode)
    }

    fun updateEq(enabled: Boolean) {
        _state.value = _state.value.copy(eqEnabled = enabled)
    }

    fun updateEarTipResult(result: AacpResponseParser.EarTipResult) {
        _state.value = _state.value.copy(earTipResult = result)
    }

    fun updateDeviceName(name: String) {
        _state.value = _state.value.copy(deviceName = name)
    }

    fun updateDeviceAddress(address: String) {
        if (address.isNotEmpty()) {
            _state.value = _state.value.copy(deviceAddress = address)
        }
    }

    fun setBluetoothDevice(device: BluetoothDevice) {
        bluetoothDevice = device
    }

    fun isConnected(): Boolean {
        val s = _state.value.connectionState
        return s == ConnectionState.AACP_READY || s == ConnectionState.FALLBACK_READY
    }
}
