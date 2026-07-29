package com.airpods.control.core.aacp

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.mapNotNull

/**
 * Abstract interface for AACP communication.
 * Implementations handle the transport layer (BLE GATT or RFCOMM).
 */
interface AacpTransport {
    val isConnected: Boolean

    suspend fun sendCommand(frame: ByteArray): ByteArray?

    fun notificationFlow(): Flow<ByteArray>
}

/**
 * High-level AACP command interface.
 * Abstracts command building + sending + response parsing into simple suspend functions.
 */
class AacpCommander(private val transport: AacpTransport) {

    val isConnected: Boolean get() = transport.isConnected

    suspend fun getBattery(): AacpResponseParser.BatteryState? {
        return sendAndParse(AacpFrameBuilder.buildGetBattery()) as? AacpResponseParser.BatteryState
    }

    suspend fun getStatus(): Any? {
        return sendAndParse(AacpFrameBuilder.buildGetStatus())
    }

    suspend fun getListeningMode(): AacpResponseParser.ListeningModeState? {
        return sendAndParse(AacpFrameBuilder.buildGetListeningMode()) as? AacpResponseParser.ListeningModeState
    }

    suspend fun setListeningMode(mode: Byte): AacpResponseParser.ListeningModeState? {
        return sendAndParse(AacpFrameBuilder.buildSetListeningMode(mode)) as? AacpResponseParser.ListeningModeState
    }

    suspend fun getInEar(): AacpResponseParser.InEarState? {
        return sendAndParse(AacpFrameBuilder.buildGetInEar()) as? AacpResponseParser.InEarState
    }

    suspend fun setInEar(autoDetect: Boolean): AacpResponseParser.InEarState? {
        return sendAndParse(AacpFrameBuilder.buildSetInEar(autoDetect)) as? AacpResponseParser.InEarState
    }

    suspend fun getSpatial(): AacpResponseParser.SpatialState? {
        return sendAndParse(AacpFrameBuilder.buildGetSpatial()) as? AacpResponseParser.SpatialState
    }

    suspend fun setSpatial(mode: Byte): AacpResponseParser.SpatialState? {
        return sendAndParse(AacpFrameBuilder.buildSetSpatial(mode)) as? AacpResponseParser.SpatialState
    }

    suspend fun getName(): String? {
        val r = sendAndParse(AacpFrameBuilder.buildGetName())
        return (r as? AacpResponseParser.DeviceName)?.name
    }

    suspend fun setName(name: String): String? {
        val r = sendAndParse(AacpFrameBuilder.buildSetName(name))
        return (r as? AacpResponseParser.DeviceName)?.name
    }

    suspend fun getFirmware(): AacpResponseParser.FirmwareInfo? {
        return sendAndParse(AacpFrameBuilder.buildGetFirmware()) as? AacpResponseParser.FirmwareInfo
    }

    suspend fun playFindSound(ear: Byte = 0): Boolean {
        val r = transport.sendCommand(AacpFrameBuilder.buildPlayFindSound(ear))
        return r != null
    }

    suspend fun earTipTest(): AacpResponseParser.EarTipResult? {
        return sendAndParse(AacpFrameBuilder.buildEarTipTest()) as? AacpResponseParser.EarTipResult
    }

    suspend fun getConversationAware(): Boolean? {
        val r = sendAndParse(AacpFrameBuilder.buildGetConversationAware())
        return (r as? AacpResponseParser.ConversationState)?.enabled
    }

    suspend fun setConversationAware(enabled: Boolean): Boolean? {
        val r = sendAndParse(AacpFrameBuilder.buildSetConversationAware(enabled))
        return (r as? AacpResponseParser.ConversationState)?.enabled
    }

    suspend fun getGesture(side: Byte): AacpResponseParser.GestureConfig? {
        return sendAndParse(AacpFrameBuilder.buildGetGesture(side)) as? AacpResponseParser.GestureConfig
    }

    suspend fun setGesture(side: Byte, gestureType: Byte, action: Byte): AacpResponseParser.GestureConfig? {
        return sendAndParse(AacpFrameBuilder.buildSetGesture(side, gestureType, action)) as? AacpResponseParser.GestureConfig
    }

    suspend fun getAdaptive(): AacpResponseParser.AdaptiveState? {
        return sendAndParse(AacpFrameBuilder.buildGetAdaptive()) as? AacpResponseParser.AdaptiveState
    }

    suspend fun setAdaptive(intensity: Byte): AacpResponseParser.AdaptiveState? {
        return sendAndParse(AacpFrameBuilder.buildSetAdaptive(intensity)) as? AacpResponseParser.AdaptiveState
    }

    suspend fun getMicMode(): AacpResponseParser.MicModeState? {
        return sendAndParse(AacpFrameBuilder.buildGetMicMode()) as? AacpResponseParser.MicModeState
    }

    suspend fun setMicMode(mode: Byte): AacpResponseParser.MicModeState? {
        return sendAndParse(AacpFrameBuilder.buildSetMicMode(mode)) as? AacpResponseParser.MicModeState
    }

    suspend fun getEq(): Boolean? {
        val r = sendAndParse(AacpFrameBuilder.buildGetEq())
        return (r as? AacpResponseParser.EqState)?.enabled
    }

    suspend fun setEq(enabled: Boolean): Boolean? {
        val r = sendAndParse(AacpFrameBuilder.buildSetEq(enabled))
        return (r as? AacpResponseParser.EqState)?.enabled
    }

    // ---- Notification event flow ----
    fun events(): Flow<AacpEvent> {
        return transport.notificationFlow().mapNotNull { raw ->
            when (val parsed = AacpResponseParser.parse(raw)) {
                is AacpResponseParser.BatteryState -> AacpEvent.Battery(parsed)
                is AacpResponseParser.InEarState -> AacpEvent.InEar(parsed)
                is AacpResponseParser.ListeningModeState -> AacpEvent.ListeningMode(parsed)
                is AacpResponseParser.GestureConfig -> AacpEvent.Gesture(parsed)
                is AacpResponseParser.EarTipResult -> AacpEvent.EarTipResult(parsed)
                else -> null
            }
        }
    }

    private suspend fun sendAndParse(frame: ByteArray): Any? {
        val raw = transport.sendCommand(frame) ?: return null
        return AacpResponseParser.parse(raw)
    }
}

/** Sealed class for async device events. */
sealed class AacpEvent {
    data class Battery(val state: AacpResponseParser.BatteryState) : AacpEvent()
    data class InEar(val state: AacpResponseParser.InEarState) : AacpEvent()
    data class ListeningMode(val state: AacpResponseParser.ListeningModeState) : AacpEvent()
    data class Gesture(val config: AacpResponseParser.GestureConfig) : AacpEvent()
    data class EarTipResult(val result: AacpResponseParser.EarTipResult) : AacpEvent()
}
