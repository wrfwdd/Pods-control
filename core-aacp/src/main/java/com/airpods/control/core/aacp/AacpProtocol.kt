package com.airpods.control.core.aacp

/**
 * AACP (Apple Accessory Communication Protocol) frame definitions.
 * Based on reverse-engineering from LibrePods (Kavish Devar) and community research.
 *
 * Frame structure:
 * | Header (2B) | Length (2B) | Command (1B) | SubCommand (1B) | Payload (N bytes) |
 *
 * Header always: 0x04 0x00 (little-endian magic)
 */
object AacpProtocol {

    // ---- Frame constants ----
    const val HEADER_MAGIC: Short = 0x0004
    const val HEADER_SIZE = 2
    const val LENGTH_SIZE = 2
    const val HEADER_TOTAL = HEADER_SIZE + LENGTH_SIZE // 4

    // ---- Service UUIDs ----
    val AACP_SERVICE_UUID: String = "74EC2170-0043-4702-A69D-40F144434D42"
    val AACP_DATA_CHAR_UUID: String = "74EC2171-0043-4702-A69D-40F144434D42"
    val AACP_NOTIFY_CHAR_UUID: String = "74EC2172-0043-4702-A69D-40F144434D42"

    // ---- Commands (1-byte) ----
    object Cmd {
        const val GET_STATUS        = 0x00.toByte()
        const val SET_LISTENING_MODE = 0x01.toByte()
        const val GET_BATTERY       = 0x02.toByte()
        const val GET_ANC_MODE      = 0x03.toByte()
        const val SET_ANC_MODE      = 0x04.toByte()
        const val GET_IN_EAR        = 0x05.toByte()
        const val SET_IN_EAR        = 0x06.toByte()
        const val GET_SPATIAL       = 0x07.toByte()
        const val SET_SPATIAL       = 0x08.toByte()
        const val GET_NAME          = 0x09.toByte()
        const val SET_NAME          = 0x0A.toByte()
        const val GET_FIRMWARE      = 0x0B.toByte()
        const val PLAY_FIND_SOUND   = 0x0C.toByte()
        const val EAR_TIP_TEST      = 0x0D.toByte()
        const val GET_CONVERSATION  = 0x0E.toByte()
        const val SET_CONVERSATION  = 0x0F.toByte()
        const val GET_GESTURE       = 0x10.toByte()
        const val SET_GESTURE       = 0x11.toByte()
        const val GET_ADAPTIVE      = 0x12.toByte()
        const val SET_ADAPTIVE      = 0x13.toByte()
        const val GET_MIC_MODE      = 0x14.toByte()
        const val SET_MIC_MODE      = 0x15.toByte()
        const val GET_VOLUME        = 0x16.toByte()
        const val SET_VOLUME        = 0x17.toByte()
        const val GET_EQ            = 0x18.toByte()
        const val SET_EQ            = 0x19.toByte()
        const val NOTIFY_EVENT      = 0x20.toByte() // Unsolicited notification from device
    }

    // ---- Listening Mode values ----
    object ListeningMode {
        const val OFF         = 0x00.toByte()
        const val ANC         = 0x01.toByte()
        const val TRANSPARENCY = 0x02.toByte()
        const val ADAPTIVE    = 0x03.toByte()
    }

    // ---- Spatial Audio values ----
    object SpatialMode {
        const val OFF          = 0x00.toByte()
        const val FIXED        = 0x01.toByte()
        const val HEAD_TRACKED = 0x02.toByte()
    }

    // ---- In-Ear Detection values ----
    object InEarState {
        const val BOTH_OUT = 0x00.toByte()
        const val LEFT_IN  = 0x01.toByte()
        const val RIGHT_IN = 0x02.toByte()
        const val BOTH_IN  = 0x03.toByte()
    }

    // ---- Gesture types ----
    object GestureType {
        const val SINGLE_TAP   = 0x00.toByte()
        const val DOUBLE_TAP   = 0x01.toByte()
        const val TRIPLE_TAP   = 0x02.toByte()
        const val LONG_PRESS   = 0x03.toByte()
        const val SWIPE_VOLUME = 0x04.toByte()
    }

    // ---- Gesture actions ----
    object GestureAction {
        const val PLAY_PAUSE      = 0x00.toByte()
        const val NEXT_TRACK      = 0x01.toByte()
        const val PREV_TRACK      = 0x02.toByte()
        const val ANC_TOGGLE      = 0x03.toByte()
        const val VOICE_ASSISTANT = 0x04.toByte()
        const val VOLUME_UP       = 0x05.toByte()
        const val VOLUME_DOWN     = 0x06.toByte()
        const val NONE            = 0xFF.toByte()
    }

    // ---- Mic mode ----
    object MicMode {
        const val AUTO   = 0x00.toByte()
        const val LEFT   = 0x01.toByte()
        const val RIGHT  = 0x02.toByte()
    }

    // ---- Notification event types (from device) ----
    object EventType {
        const val BATTERY_UPDATE       = 0x01.toByte()
        const val IN_EAR_CHANGE        = 0x02.toByte()
        const val LISTENING_MODE_CHANGE = 0x03.toByte()
        const val CASE_OPEN            = 0x04.toByte()
        const val CASE_CLOSE           = 0x05.toByte()
        const val EAR_TIP_RESULT       = 0x06.toByte()
        const val FIRMWARE_UPDATE      = 0x07.toByte()
        const val GESTURE_TRIGGERED    = 0x08.toByte()
    }
}
