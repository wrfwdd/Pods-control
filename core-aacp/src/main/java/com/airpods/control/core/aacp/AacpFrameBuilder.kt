package com.airpods.control.core.aacp

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Builds AACP command frames to send to the accessory.
 * Frame layout (little-endian):
 *   [0..1] Header magic 0x00 0x04
 *   [2..3] Payload length (uint16 LE) = total after header
 *   [4]    Command byte
 *   [5]    Sub-command / parameter byte
 *   [6..N] Optional payload
 */
object AacpFrameBuilder {

    fun build(command: Byte, subCommand: Byte = 0, payload: ByteArray = ByteArray(0)): ByteArray {
        val totalLen = 1 + 1 + payload.size // cmd + sub + payload
        val buf = ByteBuffer.allocate(AacpProtocol.HEADER_TOTAL + totalLen)
            .order(ByteOrder.LITTLE_ENDIAN)
        buf.putShort(AacpProtocol.HEADER_MAGIC)
        buf.putShort(totalLen.toShort())
        buf.put(command)
        buf.put(subCommand)
        if (payload.isNotEmpty()) buf.put(payload)
        return buf.array()
    }

    fun buildGetBattery(): ByteArray = build(AacpProtocol.Cmd.GET_BATTERY)
    fun buildGetStatus(): ByteArray = build(AacpProtocol.Cmd.GET_STATUS)
    fun buildGetListeningMode(): ByteArray = build(AacpProtocol.Cmd.GET_ANC_MODE)

    fun buildSetListeningMode(mode: Byte): ByteArray =
        build(AacpProtocol.Cmd.SET_LISTENING_MODE, mode)

    fun buildGetInEar(): ByteArray = build(AacpProtocol.Cmd.GET_IN_EAR)

    fun buildSetInEar(autoDetect: Boolean): ByteArray =
        build(AacpProtocol.Cmd.SET_IN_EAR, if (autoDetect) 1 else 0)

    fun buildGetSpatial(): ByteArray = build(AacpProtocol.Cmd.GET_SPATIAL)

    fun buildSetSpatial(mode: Byte): ByteArray =
        build(AacpProtocol.Cmd.SET_SPATIAL, mode)

    fun buildGetName(): ByteArray = build(AacpProtocol.Cmd.GET_NAME)

    fun buildSetName(name: String): ByteArray {
        val nameBytes = name.toByteArray(Charsets.UTF_8).copyOf(32) // max 32 bytes
        return build(AacpProtocol.Cmd.SET_NAME, nameBytes.size.toByte(), nameBytes)
    }

    fun buildGetFirmware(): ByteArray = build(AacpProtocol.Cmd.GET_FIRMWARE)

    fun buildPlayFindSound(ear: Byte = 0): ByteArray =
        build(AacpProtocol.Cmd.PLAY_FIND_SOUND, ear) // 0=both, 1=left, 2=right

    fun buildEarTipTest(): ByteArray = build(AacpProtocol.Cmd.EAR_TIP_TEST)

    fun buildGetConversationAware(): ByteArray = build(AacpProtocol.Cmd.GET_CONVERSATION)

    fun buildSetConversationAware(enabled: Boolean): ByteArray =
        build(AacpProtocol.Cmd.SET_CONVERSATION, if (enabled) 1 else 0)

    fun buildGetGesture(side: Byte): ByteArray =
        build(AacpProtocol.Cmd.GET_GESTURE, side) // 0=left, 1=right

    fun buildSetGesture(side: Byte, gestureType: Byte, action: Byte): ByteArray =
        build(AacpProtocol.Cmd.SET_GESTURE, side, byteArrayOf(gestureType, action))

    fun buildGetAdaptive(): ByteArray = build(AacpProtocol.Cmd.GET_ADAPTIVE)

    fun buildSetAdaptive(intensity: Byte): ByteArray =
        build(AacpProtocol.Cmd.SET_ADAPTIVE, intensity)

    fun buildGetMicMode(): ByteArray = build(AacpProtocol.Cmd.GET_MIC_MODE)

    fun buildSetMicMode(mode: Byte): ByteArray =
        build(AacpProtocol.Cmd.SET_MIC_MODE, mode)

    fun buildGetVolume(): ByteArray = build(AacpProtocol.Cmd.GET_VOLUME)

    fun buildGetEq(): ByteArray = build(AacpProtocol.Cmd.GET_EQ)

    fun buildSetEq(enabled: Boolean): ByteArray =
        build(AacpProtocol.Cmd.SET_EQ, if (enabled) 1 else 0)
}
