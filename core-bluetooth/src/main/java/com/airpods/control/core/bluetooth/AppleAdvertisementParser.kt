package com.airpods.control.core.bluetooth

import android.bluetooth.BluetoothDevice
import com.airpods.control.core.data.AirPodsModel

/**
 * Parses Apple Manufacturer Specific Data (Company ID 0x004C) from BLE advertisements.
 * Extracts: model identifier, case open/close status, ear position, battery hints.
 */
object AppleAdvertisementParser {

    private const val APPLE_COMPANY_ID = 0x004C

    data class AirPodsAdvertisement(
        val model: AirPodsModel,
        val isCaseOpen: Boolean = false,
        val leftInCase: Boolean = true,
        val rightInCase: Boolean = true,
        val batteryLeft: Int = -1,
        val batteryRight: Int = -1,
        val batteryCase: Int = -1,
        val isCharging: Boolean = false,
        val rawModelId: Int = -1
    )

    fun parse(device: BluetoothDevice, scanRecord: ByteArray?): AirPodsAdvertisement? {
        if (scanRecord == null) return null
        var i = 0
        while (i < scanRecord.size - 1) {
            val length = scanRecord[i].toInt() and 0xFF
            if (length == 0 || i + length >= scanRecord.size) break
            val type = scanRecord[i + 1].toInt() and 0xFF
            if (type == 0xFF) {
                val data = scanRecord.copyOfRange(i + 2, i + length + 1)
                if (data.size >= 2) {
                    val companyId = ((data[1].toInt() and 0xFF) shl 8) or (data[0].toInt() and 0xFF)
                    if (companyId == APPLE_COMPANY_ID && data.size >= 4) {
                        return parseAppleData(data)
                    }
                }
            }
            i += length + 1
        }
        return null
    }

    private fun parseAppleData(data: ByteArray): AirPodsAdvertisement? {
        val subType = data[2].toInt() and 0xFF
        val subDataLen = data[3].toInt() and 0xFF
        if (data.size < 4 + subDataLen) return null
        val subData = data.copyOfRange(4, 4 + subDataLen)
        return when (subType) {
            0x07 -> parseAirPodsPro(subData)
            0x09 -> parseAirPodsClassic(subData)
            0x0F -> parseAirPods3(subData)
            else -> null
        }
    }

    private fun parseAirPodsPro(data: ByteArray): AirPodsAdvertisement {
        val status = if (data.size > 0) data[0].toInt() and 0xFF else 0
        val modelId = if (data.size > 1) data[1].toInt() and 0xFF else -1
        val batteryL = if (data.size > 2) (data[2].toInt() and 0x7F) else -1
        val batteryR = if (data.size > 3) (data[3].toInt() and 0x7F) else -1
        val batteryC = if (data.size > 4) (data[4].toInt() and 0x7F) else -1
        return AirPodsAdvertisement(
            model = AirPodsModel.fromModelId(modelId),
            isCaseOpen = (status and 0x02) != 0,
            leftInCase = (status and 0x04) != 0,
            rightInCase = (status and 0x08) != 0,
            batteryLeft = batteryL,
            batteryRight = batteryR,
            batteryCase = batteryC,
            isCharging = (status and 0x01) != 0,
            rawModelId = modelId
        )
    }

    private fun parseAirPodsClassic(data: ByteArray): AirPodsAdvertisement {
        val status = if (data.size > 0) data[0].toInt() and 0xFF else 0
        val modelId = if (data.size > 1) data[1].toInt() and 0xFF else 1
        return AirPodsAdvertisement(
            model = AirPodsModel.fromModelId(modelId),
            isCaseOpen = (status and 0x20) != 0,
            batteryLeft = -1, batteryRight = -1, batteryCase = -1,
            rawModelId = modelId
        )
    }

    private fun parseAirPods3(data: ByteArray): AirPodsAdvertisement {
        return parseAirPodsPro(data)
    }
}
