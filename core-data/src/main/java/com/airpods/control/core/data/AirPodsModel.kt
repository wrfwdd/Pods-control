package com.airpods.control.core.data

// ---- Capability bitmask constants (top-level so enum entries can reference them) ----
const val CAP_AUDIO            = 1L shl 0   // Basic audio (A2DP)
const val CAP_BASIC_BATTERY    = 1L shl 1   // HID battery service
const val CAP_PRECISE_BATTERY  = 1L shl 2   // Per-ear + case via AACP
const val CAP_IN_EAR_DETECT    = 1L shl 3   // Optical / skin-detect
const val CAP_ANC              = 1L shl 4   // Active Noise Cancellation
const val CAP_TRANSPARENCY     = 1L shl 5   // Transparency mode
const val CAP_ADAPTIVE_AUDIO   = 1L shl 6   // Adaptive Audio (Pro 2+)
const val CAP_SPATIAL_AUDIO    = 1L shl 7   // Spatial Audio + head tracking
const val CAP_ADAPTIVE_EQ      = 1L shl 8   // Adaptive EQ
const val CAP_SKIN_DETECT      = 1L shl 9   // Skin-detect sensor (vs optical)
const val CAP_FORCE_SENSOR     = 1L shl 10  // Force sensor on stem
const val CAP_FIND_MY          = 1L shl 11  // Play sound for Find My
const val CAP_CONVERSATION_AWARE = 1L shl 12 // Conversation Awareness
const val CAP_PERSONALIZED_VOLUME = 1L shl 13 // Personalized Volume
const val CAP_EAR_TIP_TEST     = 1L shl 14  // Ear Tip Fit Test
const val CAP_CASE_SPEAKER     = 1L shl 15  // Case has speaker (Pro 2+)
const val CAP_PRECISION_FINDING = 1L shl 16 // Precision Finding (UWB)
const val CAP_HEY_SIRI         = 1L shl 17  // Hey Siri support
const val CAP_HEAD_GESTURES    = 1L shl 18  // Head gestures (Pro 3)
const val CAP_DIGITAL_CROWN    = 1L shl 19  // Digital Crown (Max)

/** AirPods model identifiers parsed from Apple Manufacturer Specific Data (0x004C). */
enum class AirPodsModel(
    val modelId: Int,
    val displayName: String,
    val iconRes: String,
    val capabilities: Long
) {
    AIRPODS_1(1, "AirPods (1st gen)", "airpods1",
        CAP_AUDIO or CAP_BASIC_BATTERY or CAP_IN_EAR_DETECT),
    AIRPODS_2(2, "AirPods (2nd gen)", "airpods2",
        CAP_AUDIO or CAP_BASIC_BATTERY or CAP_IN_EAR_DETECT or CAP_HEY_SIRI),
    AIRPODS_3(3, "AirPods (3rd gen)", "airpods3",
        CAP_AUDIO or CAP_BASIC_BATTERY or CAP_IN_EAR_DETECT or CAP_SPATIAL_AUDIO
                or CAP_ADAPTIVE_EQ or CAP_SKIN_DETECT or CAP_FORCE_SENSOR
                or CAP_FIND_MY or CAP_HEY_SIRI),
    AIRPODS_4(4, "AirPods 4", "airpods4",
        CAP_AUDIO or CAP_BASIC_BATTERY or CAP_IN_EAR_DETECT or CAP_SPATIAL_AUDIO
                or CAP_ADAPTIVE_EQ or CAP_SKIN_DETECT or CAP_FORCE_SENSOR
                or CAP_FIND_MY or CAP_HEY_SIRI or CAP_CONVERSATION_AWARE
                or CAP_PERSONALIZED_VOLUME),
    AIRPODS_4_ANC(5, "AirPods 4 (ANC)", "airpods4anc",
        CAP_AUDIO or CAP_BASIC_BATTERY or CAP_IN_EAR_DETECT or CAP_SPATIAL_AUDIO
                or CAP_ADAPTIVE_EQ or CAP_SKIN_DETECT or CAP_FORCE_SENSOR
                or CAP_FIND_MY or CAP_HEY_SIRI or CAP_CONVERSATION_AWARE
                or CAP_PERSONALIZED_VOLUME or CAP_ANC or CAP_TRANSPARENCY
                or CAP_ADAPTIVE_AUDIO),
    AIRPODS_PRO_1(10, "AirPods Pro (1st gen)", "airpodspro1",
        CAP_AUDIO or CAP_PRECISE_BATTERY or CAP_IN_EAR_DETECT or CAP_SPATIAL_AUDIO
                or CAP_ADAPTIVE_EQ or CAP_SKIN_DETECT or CAP_FORCE_SENSOR
                or CAP_FIND_MY or CAP_ANC or CAP_TRANSPARENCY or CAP_EAR_TIP_TEST
                or CAP_CONVERSATION_AWARE or CAP_HEY_SIRI),
    AIRPODS_PRO_2(11, "AirPods Pro (2nd gen)", "airpodspro2",
        CAP_AUDIO or CAP_PRECISE_BATTERY or CAP_IN_EAR_DETECT or CAP_SPATIAL_AUDIO
                or CAP_ADAPTIVE_EQ or CAP_SKIN_DETECT or CAP_FORCE_SENSOR
                or CAP_FIND_MY or CAP_ANC or CAP_TRANSPARENCY or CAP_ADAPTIVE_AUDIO
                or CAP_EAR_TIP_TEST or CAP_CONVERSATION_AWARE or CAP_PERSONALIZED_VOLUME
                or CAP_CASE_SPEAKER or CAP_PRECISION_FINDING or CAP_HEY_SIRI
                or CAP_HEAD_GESTURES),
    AIRPODS_PRO_3(12, "AirPods Pro (3rd gen)", "airpodspro3",
        CAP_AUDIO or CAP_PRECISE_BATTERY or CAP_IN_EAR_DETECT or CAP_SPATIAL_AUDIO
                or CAP_ADAPTIVE_EQ or CAP_SKIN_DETECT or CAP_FORCE_SENSOR
                or CAP_FIND_MY or CAP_ANC or CAP_TRANSPARENCY or CAP_ADAPTIVE_AUDIO
                or CAP_EAR_TIP_TEST or CAP_CONVERSATION_AWARE or CAP_PERSONALIZED_VOLUME
                or CAP_CASE_SPEAKER or CAP_PRECISION_FINDING or CAP_HEY_SIRI
                or CAP_HEAD_GESTURES),
    AIRPODS_MAX_1(20, "AirPods Max (1st gen)", "airpodsmax1",
        CAP_AUDIO or CAP_PRECISE_BATTERY or CAP_SPATIAL_AUDIO or CAP_ADAPTIVE_EQ
                or CAP_FIND_MY or CAP_ANC or CAP_TRANSPARENCY or CAP_HEY_SIRI
                or CAP_DIGITAL_CROWN),
    AIRPODS_MAX_2(21, "AirPods Max (2nd gen)", "airpodsmax2",
        CAP_AUDIO or CAP_PRECISE_BATTERY or CAP_SPATIAL_AUDIO or CAP_ADAPTIVE_EQ
                or CAP_FIND_MY or CAP_ANC or CAP_TRANSPARENCY or CAP_ADAPTIVE_AUDIO
                or CAP_CONVERSATION_AWARE or CAP_PERSONALIZED_VOLUME or CAP_HEY_SIRI
                or CAP_DIGITAL_CROWN),
    UNKNOWN(-1, "AirPods", "airpods_generic", CAP_AUDIO or CAP_BASIC_BATTERY);

    companion object {
        /** Map Apple model byte (from Manufacturer Data) �� AirPodsModel. */
        fun fromModelId(id: Int): AirPodsModel =
            entries.find { it.modelId == id } ?: UNKNOWN

        /** Infer the most likely AirPods model from the Bluetooth device name. */
        fun guessFromName(name: String): AirPodsModel {
            val n = name.lowercase()
            return when {
                n.contains("pro") && (n.contains("2nd") || n.contains(" gen 2")) -> AIRPODS_PRO_2
                n.contains("pro") -> AIRPODS_PRO_1
                n.contains("max") -> AIRPODS_MAX_1
                n.contains("4") && n.contains("anc") -> AIRPODS_4_ANC
                n.contains("4") -> AIRPODS_4
                n.contains("3") -> AIRPODS_3
                n.contains("2") -> AIRPODS_2
                else -> UNKNOWN
            }
        }
    }
}
