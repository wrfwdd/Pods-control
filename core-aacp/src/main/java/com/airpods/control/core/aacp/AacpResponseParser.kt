package com.airpods.control.core.aacp

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Parses AACP response frames received from the accessory.
 * Returns typed result objects for UI consumption.
 */
object AacpResponseParser {

    data class BatteryState(
        val leftPercent: Int,
        val rightPercent: Int,
        val casePercent: Int,
        val leftCharging: Boolean = false,
        val rightCharging: Boolean = false,
        val caseCharging: Boolean = false
    )

    data class InEarState(
        val leftInEar: Boolean,
        val rightInEar: Boolean
    )

    data class ListeningModeState(
        val mode: Byte // AacpProtocol.ListeningMode.*
    )

    data class SpatialState(
        val mode: Byte,  // AacpProtocol.SpatialMode.*
        val personalized: Boolean = false
    )

    data class EarTipResult(
        val leftGood: Boolean,
        val rightGood: Boolean
    )

    data class FirmwareInfo(
        val version: String,
        val serialNumber: String? = null
    )

    data class DeviceName(val name: String)

    data class GestureConfig(
        val side: Byte,
        val gestureType: Byte,
        val action: Byte
    )

    data class ConversationState(val enabled: Boolean)
    data class AdaptiveState(val intensity: Byte, val enabled: Boolean)
    data class MicModeState(val mode: Byte)
    data class VolumeState(val level: Byte)
    data class EqState(val enabled: Boolean)

    // ---- Main parse entry point ----
    fun parse(raw: ByteArray): Any? {
        if (raw.size < AacpProtocol.HEADER_TOTAL + 2) return null
        val buf = ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN)
        val magic = buf.short
        if (magic != AacpProtocol.HEADER_MAGIC) return null
        val length = buf.short.toInt() and 0xFFFF
        if (buf.remaining() < length) return null
        val command = buf.get()
        val subCommand = buf.get()
        val payload = ByteArray(maxOf(0, length - 2))
        if (payload.isNotEmpty()) buf.get(payload)

        return parsePayload(command, subCommand, payload)
    }

    private fun parsePayload(command: Byte, subCommand: Byte, payload: ByteArray): Any? {
        when (command) {
            AacpProtocol.Cmd.GET_BATTERY,
            AacpProtocol.Cmd.NOTIFY_EVENT -> {
                if (payload.size >= 3) {
                    if (subCommand == AacpProtocol.EventType.BATTERY_UPDATE) {
                        return BatteryState(
                            leftPercent = payload[0].toInt() and 0xFF,
                            rightPercent = payload[1].toInt() and 0xFF,
                            casePercent = payload[2].toInt() and 0xFF,
                            leftCharging = (payload[0].toInt() and 0x80) != 0,
                            rightCharging = (payload[1].toInt() and 0x80) != 0,
                            caseCharging = (payload[2].toInt() and 0x80) != 0
                        )
                    }
                }
            }
            AacpProtocol.Cmd.GET_ANC_MODE,
            AacpProtocol.Cmd.SET_ANC_MODE -> {
                if (payload.isNotEmpty()) return ListeningModeState(payload[0])
            }
            AacpProtocol.Cmd.GET_IN_EAR,
            AacpProtocol.Cmd.SET_IN_EAR -> {
                if (payload.isNotEmpty()) {
                    val state = payload[0].toInt() and 0xFF
                    return InEarState(
                        leftInEar = (state and 0x01) != 0,
                        rightInEar = (state and 0x02) != 0
                    )
                }
            }
            AacpProtocol.Cmd.GET_SPATIAL,
            AacpProtocol.Cmd.SET_SPATIAL -> {
                if (payload.isNotEmpty()) {
                    return SpatialState(
                        mode = payload[0],
                        personalized = payload.size > 1 && payload[1].toInt() != 0
                    )
                }
            }
            AacpProtocol.Cmd.GET_NAME,
            AacpProtocol.Cmd.SET_NAME -> {
                val name = payload.takeWhile { it != 0.toByte() }.toByteArray()
                    .toString(Charsets.UTF_8)
                return DeviceName(name)
            }
            AacpProtocol.Cmd.GET_FIRMWARE -> {
                if (payload.size >= 2) {
                    val major = payload[0].toInt() and 0xFF
                    val minor = payload[1].toInt() and 0xFF
                    return FirmwareInfo(
                        version = "$major.$minor",
                        serialNumber = if (payload.size > 4) {
                            payload.copyOfRange(2, payload.size)
                                .toString(Charsets.UTF_8).trim()
                        } else null
                    )
                }
            }
            AacpProtocol.Cmd.EAR_TIP_TEST -> {
                if (payload.size >= 2) {
                    return EarTipResult(
                        leftGood = payload[0].toInt() != 0,
                        rightGood = payload[1].toInt() != 0
                    )
                }
            }
            AacpProtocol.Cmd.GET_CONVERSATION -> {
                if (payload.isNotEmpty()) {
                    return ConversationState(payload[0].toInt() != 0)
                }
            }
            AacpProtocol.Cmd.GET_ADAPTIVE,
            AacpProtocol.Cmd.SET_ADAPTIVE -> {
                if (payload.isNotEmpty()) {
                    return AdaptiveState(
                        intensity = payload[0],
                        enabled = payload[0].toInt() != 0
                    )
                }
            }
            AacpProtocol.Cmd.GET_MIC_MODE,
            AacpProtocol.Cmd.SET_MIC_MODE -> {
                if (payload.isNotEmpty()) return MicModeState(payload[0])
            }
            AacpProtocol.Cmd.GET_VOLUME,
            AacpProtocol.Cmd.SET_VOLUME -> {
                if (payload.isNotEmpty()) return VolumeState(payload[0])
            }
            AacpProtocol.Cmd.GET_EQ,
            AacpProtocol.Cmd.SET_EQ -> {
                if (payload.isNotEmpty()) return EqState(payload[0].toInt() != 0)
            }
            AacpProtocol.Cmd.GET_GESTURE,
            AacpProtocol.Cmd.SET_GESTURE -> {
                if (payload.size >= 2) {
                    return GestureConfig(
                        side = subCommand,
                        gestureType = payload[0],
                        action = payload[1]
                    )
                }
            }
        }
        return null
    }
}
