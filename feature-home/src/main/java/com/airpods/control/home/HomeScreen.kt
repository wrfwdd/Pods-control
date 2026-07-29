package com.airpods.control.home

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import com.airpods.control.core.bluetooth.AirPodsDeviceManager
import com.airpods.control.core.bluetooth.ConnectionState
import com.airpods.control.core.data.AirPodsModel
import com.airpods.control.core.data.CAP_AUDIO
import com.airpods.control.core.data.CAP_ANC
import com.airpods.control.core.data.CAP_TRANSPARENCY
import com.airpods.control.core.data.CAP_ADAPTIVE_AUDIO
import com.airpods.control.core.data.CAP_SPATIAL_AUDIO
import com.airpods.control.core.data.CAP_IN_EAR_DETECT
import com.airpods.control.core.data.CAP_FORCE_SENSOR
import com.airpods.control.core.data.CAP_DIGITAL_CROWN
import com.airpods.control.core.data.CAP_FIND_MY
import com.airpods.control.core.data.CAP_EAR_TIP_TEST
import com.airpods.control.core.ui.AirPodsTheme

/**
 * Main control center screen.
 * Displays all AirPods controls organized as cards.
 * Features dynamically show/hide based on the connected model's capability bitmask.
 */
@Composable
fun HomeScreen(
    deviceManager: AirPodsDeviceManager
,
    onRetry: () -> Unit = {},
    shizukuReady: Boolean = false,
    shizukuStatus: String = "",
    onRequestShizuku: () -> Unit = {}
) {
    val state by deviceManager.state.collectAsState()
    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()
    val commander = deviceManager.aacpCommander

    if (state.connectionState == ConnectionState.DISCONNECTED ||
        state.connectionState == ConnectionState.DISCOVERED) {
        // Empty state
        EmptyHomeState(onRetry = onRetry)
        return
    }

    if (state.connectionState == ConnectionState.CONNECTING) {
        ConnectingState(state.deviceName)
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(AirPodsTheme.Dimens.CardPadding)
            .padding(bottom = 32.dp)
    ) {
        // ---- Header: model name + battery bar ----
        HomeHeader(state)

        if (state.connectionState == ConnectionState.FALLBACK_READY) {
            FallbackBanner(shizukuReady = shizukuReady, shizukuStatus = shizukuStatus, onRequestShizuku = onRequestShizuku)
            Spacer(modifier = Modifier.height(8.dp))
        }

        Spacer(modifier = Modifier.height(AirPodsTheme.Dimens.SectionSpacing))




        val caps = state.model.capabilities

        // 1) Listening Mode Card
        if (hasCap(caps, CAP_ANC)) {
            ListeningModeCard(
                currentMode = state.listeningMode,
                hasAdaptive = hasCap(caps, CAP_ADAPTIVE_AUDIO),
                onModeChange = { mode ->
                    scope.launch { commander?.setListeningMode(mode) }
                }
            )
            Spacer(modifier = Modifier.height(AirPodsTheme.Dimens.CardSpacing))
        }

        // 2) Transparency / Adaptive fine-tune
        if (hasCap(caps, CAP_ADAPTIVE_AUDIO) || hasCap(caps, CAP_TRANSPARENCY)) {
            AdaptiveFineTuneCard(
                conversationAwareEnabled = state.conversationAware,
                adaptiveIntensity = state.adaptiveIntensity,
                onConversationToggle = { scope.launch { commander?.setConversationAware(!state.conversationAware) } },
                onIntensityChange = { value -> scope.launch { commander?.setAdaptive(value.toByte()) } }
            )
            Spacer(modifier = Modifier.height(AirPodsTheme.Dimens.CardSpacing))
        }

        // 3) Spatial Audio Card
        if (hasCap(caps, CAP_SPATIAL_AUDIO)) {
            SpatialAudioCard(
                currentMode = state.spatial?.mode ?: 0,
                personalized = state.spatial?.personalized ?: false,
                onModeChange = { mode -> scope.launch { commander?.setSpatial(mode.toByte()) } },
                onPersonalizedToggle = { scope.launch { /* set personalized spatial */ } }
            )
            Spacer(modifier = Modifier.height(AirPodsTheme.Dimens.CardSpacing))
        }

        // 4) In-Ear Detection Card
        if (hasCap(caps, CAP_IN_EAR_DETECT)) {
            InEarDetectionCard(
                inEar = state.inEar,
                onToggle = { enabled -> scope.launch { commander?.setInEar(enabled) } }
            )
            Spacer(modifier = Modifier.height(AirPodsTheme.Dimens.CardSpacing))
        }

        // 5) Gesture Customization Card
        if (hasCap(caps, CAP_FORCE_SENSOR) || hasCap(caps, CAP_DIGITAL_CROWN)) {
            GestureCard()
            Spacer(modifier = Modifier.height(AirPodsTheme.Dimens.CardSpacing))
        }

        // 6) Find My Card
        if (hasCap(caps, CAP_FIND_MY)) {
            FindMyCard(
                onPlayLeft = { scope.launch { commander?.playFindSound(0x00.toByte()) } },
                onPlayRight = { scope.launch { commander?.playFindSound(0x01.toByte()) } },
                onPlayBoth = { scope.launch { commander?.playFindSound(0x02.toByte()) } }
            )
            Spacer(modifier = Modifier.height(AirPodsTheme.Dimens.CardSpacing))
        }

        // 7) Ear Tip Fit Test
        if (hasCap(caps, CAP_EAR_TIP_TEST)) {
            EarTipTestCard(
                result = state.earTipResult,
                onStartTest = { scope.launch { commander?.earTipTest() } }
            )
            Spacer(modifier = Modifier.height(AirPodsTheme.Dimens.CardSpacing))
        }

        // 8) Device Info Card
        DeviceInfoCard(state)

        Spacer(modifier = Modifier.height(AirPodsTheme.Dimens.CardSpacing))

        // 9) Mic Mode Card (if supported)
        MicModeCard(
            currentMode = state.micMode,
            onModeChange = { mode -> scope.launch { commander?.setMicMode(mode) } }
        )
    }
}

// ---- Helper ----
private fun hasCap(caps: Long, cap: Long): Boolean = (caps and cap) != 0L

// ---- Header ----
@Composable
private fun HomeHeader(state: com.airpods.control.core.bluetooth.DeviceState) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = AirPodsTheme.Shapes.Card,
        colors = CardDefaults.cardColors(containerColor = AirPodsTheme.Colors.CardBackground)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = state.model.displayName,
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.Medium,
                        color = AirPodsTheme.Colors.TextPrimary
                    )
                )
                // Connection status dot
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(RoundedCornerShape(percent = 50))
                        .background(
                            when (state.connectionState) {
                                ConnectionState.AACP_READY -> AirPodsTheme.Colors.BatteryGreen
                                ConnectionState.FALLBACK_READY -> Color(0xFFFF9F0A)
                                else -> AirPodsTheme.Colors.TextSecondary
                            }
                        )
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            // Battery bar
            state.battery?.let { battery ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    BatterySegment("L", battery.leftPercent)
                    BatterySegment("R", battery.rightPercent)
                    BatterySegment("C", battery.casePercent)
                }
            }
        }
    }
}

@Composable
private fun BatterySegment(label: String, percent: Int) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = "$percent%",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = AirPodsTheme.Colors.TextPrimary
        )
        Text(
            text = label,
            fontSize = 12.sp,
            color = AirPodsTheme.Colors.TextSecondary
        )
    }
}

// ---- Listening Mode Card ----
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ListeningModeCard(
    currentMode: Byte,
    hasAdaptive: Boolean,
    onModeChange: (Byte) -> Unit
) {
    FeatureCard(
        title = "Listening Mode",
        icon = Icons.Default.Headphones
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val modes = buildList {
                if (hasAdaptive) add(Triple("Adaptive", 0x03.toByte(), AirPodsTheme.Colors.AccentBlue))
                add(Triple("", 0x01.toByte(), AirPodsTheme.Colors.AccentBlue))
                add(Triple("Transparency", 0x02.toByte(), AirPodsTheme.Colors.AccentBlue))
                add(Triple("Off", 0x00.toByte(), AirPodsTheme.Colors.TextSecondary))
            }
            modes.forEach { (label, mode, color) ->
                val isSelected = currentMode == mode
                FilterChip(
                    selected = isSelected,
                    onClick = { onModeChange(mode) },
                    label = {
                        Text(
                            text = label,
                            fontSize = 13.sp,
                            color = if (isSelected) Color.White else AirPodsTheme.Colors.TextSecondary
                        )
                    },
                    modifier = Modifier.weight(1f),
                    shape = AirPodsTheme.Shapes.Pill,
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = color
                    )
                )
            }
        }
    }
}

// ---- Adaptive Fine-Tune Card ----
@Composable
private fun AdaptiveFineTuneCard(
    conversationAwareEnabled: Boolean,
    adaptiveIntensity: Int,
    onConversationToggle: (Boolean) -> Unit,
    onIntensityChange: (Int) -> Unit
) {
    FeatureCard(
        title = "Adaptive Fine-Tune",
        icon = Icons.Default.Tune
    ) {
        // Conversation Awareness switch
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "曰知",
                fontSize = 14.sp,
                color = AirPodsTheme.Colors.TextPrimary
            )
            Switch(
                checked = conversationAwareEnabled,
                onCheckedChange = onConversationToggle,
                colors = SwitchDefaults.colors(
                    checkedTrackColor = AirPodsTheme.Colors.BatteryGreen
                )
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        // Adaptive intensity slider
        Text(
            text = "应强",
            fontSize = 14.sp,
            color = AirPodsTheme.Colors.TextPrimary
        )
        Slider(
            value = adaptiveIntensity.toFloat(),
            onValueChange = { onIntensityChange(it.toInt()) },
            valueRange = 0f..100f,
            colors = SliderDefaults.colors(
                thumbColor = AirPodsTheme.Colors.AccentBlue,
                activeTrackColor = AirPodsTheme.Colors.AccentBlue
            )
        )
    }
}

// ---- Spatial Audio Card ----
@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun SpatialAudioCard(
    currentMode: Byte,
    personalized: Boolean,
    onModeChange: (Byte) -> Unit,
    onPersonalizedToggle: (Boolean) -> Unit
) {
    FeatureCard(
        title = "Spatial Audio",
        icon = Icons.Default.SurroundSound
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf(
                Triple("Off", 0x00.toByte(), false),
                Triple("Fixed", 0x01.toByte(), false),
                Triple("Head Tracked", 0x02.toByte(), false)
            ).forEach { (label, mode, _) ->
                FilterChip(
                    selected = currentMode == mode,
                    onClick = { onModeChange(mode) },
                    label = { Text(label, fontSize = 13.sp) },
                    shape = AirPodsTheme.Shapes.Pill
                )
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "曰占频",
                fontSize = 14.sp,
                color = AirPodsTheme.Colors.TextPrimary
            )
            Switch(
                checked = personalized,
                onCheckedChange = onPersonalizedToggle,
                colors = SwitchDefaults.colors(checkedTrackColor = AirPodsTheme.Colors.BatteryGreen)
            )
        }
    }
}

// ---- In-Ear Detection Card ----
@Composable
private fun InEarDetectionCard(
    inEar: com.airpods.control.core.aacp.AacpResponseParser.InEarState?,
    onToggle: (Boolean) -> Unit
) {
    FeatureCard(
        title = "In-Ear Detection",
        icon = Icons.Default.Sensors
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "远/停",
                    fontSize = 14.sp,
                    color = AirPodsTheme.Colors.TextPrimary
                )
                Text(
                    text = "摘停",
                    fontSize = 12.sp,
                    color = AirPodsTheme.Colors.TextSecondary
                )
            }
            // In-ear status indicators
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                EarStatusDot("L", inEar?.leftInEar ?: false)
                EarStatusDot("R", inEar?.rightInEar ?: false)
            }
        }
    }
}

@Composable
private fun EarStatusDot(label: String, inEar: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(RoundedCornerShape(percent = 50))
                .background(if (inEar) AirPodsTheme.Colors.BatteryGreen else AirPodsTheme.Colors.TextSecondary)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = label,
            fontSize = 12.sp,
            color = AirPodsTheme.Colors.TextSecondary
        )
    }
}

// ---- Gesture Card ----
@Composable
private fun GestureCard() {
    FeatureCard(
        title = "Gesture Customization",
        icon = Icons.Default.TouchApp
    ) {
        Text(
            text = "/双// 映",
            fontSize = 14.sp,
            color = AirPodsTheme.Colors.TextSecondary
        )
        Spacer(modifier = Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            GestureStem("", "L")
            GestureStem("叶", "R")
        }
    }
}

@Composable
private fun GestureStem(label: String, side: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        // Simplified stem visualization
        Box(
            modifier = Modifier
                .width(12.dp)
                .height(40.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(AirPodsTheme.Colors.TextSecondary.copy(alpha = 0.3f))
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = label, fontSize = 13.sp, color = AirPodsTheme.Colors.TextPrimary)
        Text(text = "Tap to Configure", fontSize = 11.sp, color = AirPodsTheme.Colors.AccentBlue)
    }
}

// ---- Find My Card ----
@Composable
private fun FindMyCard(
    onPlayLeft: () -> Unit,
    onPlayRight: () -> Unit,
    onPlayBoth: () -> Unit
) {
    FeatureCard(
        title = "Find My AirPods",
        icon = Icons.Default.MyLocation
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            FindButton("") { onPlayLeft() }
            FindButton("双") { onPlayBoth() }
            FindButton("叶") { onPlayRight() }
        }
    }
}

@Composable
private fun FindButton(label: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        shape = AirPodsTheme.Shapes.Pill,
        colors = ButtonDefaults.buttonColors(
            containerColor = AirPodsTheme.Colors.AccentBlue
        )
    ) {
        Icon(Icons.Default.VolumeUp, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(6.dp))
        Text(text = label, fontSize = 13.sp)
    }
}

// ---- Ear Tip Test Card ----
@Composable
private fun EarTipTestCard(
    result: com.airpods.control.core.aacp.AacpResponseParser.EarTipResult?,
    onStartTest: () -> Unit
) {
    FeatureCard(
        title = "Ear Tip Fit Test",
        icon = Icons.Default.CheckCircle
    ) {
        if (result != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                TipResult("", result.leftGood)
                TipResult("叶", result.rightGood)
            }
        } else {
            Button(
                onClick = onStartTest,
                shape = AirPodsTheme.Shapes.Pill,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("始")
            }
        }
    }
}

@Composable
private fun TipResult(label: String, good: Boolean) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(
            imageVector = if (good) Icons.Default.CheckCircle else Icons.Default.Warning,
            contentDescription = null,
            tint = if (good) AirPodsTheme.Colors.BatteryGreen else AirPodsTheme.Colors.DestructiveRed,
            modifier = Modifier.size(32.dp)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "$label: ${if (good) "" else ""}",
            fontSize = 13.sp,
            color = AirPodsTheme.Colors.TextPrimary
        )
    }
}

// ---- Device Info Card ----
@Composable
private fun DeviceInfoCard(state: com.airpods.control.core.bluetooth.DeviceState) {
    FeatureCard(
        title = "Device Info",
        icon = Icons.Default.Info
    ) {
        InfoRow("Device Name", state.deviceName)
        InfoRow("Model", state.model.displayName)
        state.firmware?.let { InfoRow("Firmware", it) }
        state.serialNumber?.let { InfoRow("Serial Number", it) }
        InfoRow("Address", state.deviceAddress)
    }
}


@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            fontSize = 14.sp,
            color = AirPodsTheme.Colors.TextSecondary
        )
        Text(
            text = value,
            fontSize = 14.sp,
            color = AirPodsTheme.Colors.TextPrimary
        )
    }
}

// ---- Mic Mode Card ----
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MicModeCard(
    currentMode: Byte,
    onModeChange: (Byte) -> Unit
) {
    FeatureCard(
        title = "Mic Mode",
        icon = Icons.Default.Mic
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf(
                "Standard" to 0x00.toByte(),
                "Voice Isolation" to 0x01.toByte(),
                "Wide Spectrum" to 0x02.toByte()
            ).forEach { (label, mode) ->
                FilterChip(
                    selected = currentMode == mode,
                    onClick = { onModeChange(mode) },
                    label = { Text(label, fontSize = 13.sp) },
                    shape = AirPodsTheme.Shapes.Pill
                )
            }
        }
    }
}

// ---- Connecting State ----
@Composable
private fun ConnectingState(deviceName: String) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(48.dp),
            color = AirPodsTheme.Colors.AccentBlue,
            strokeWidth = 3.dp
        )
        Spacer(modifier = Modifier.height(20.dp))
        Text(
            text = if (deviceName.isNotEmpty()) "Connecting to $deviceName..." else "Connecting...",
            fontSize = 16.sp,
            color = AirPodsTheme.Colors.TextPrimary
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Establishing link to AirPods",
            fontSize = 13.sp,
            color = AirPodsTheme.Colors.TextSecondary
        )
    }
}


// ---- Fallback Banner ----
@Composable
private fun FallbackBanner(
    shizukuReady: Boolean = false,
    shizukuStatus: String = "",
    onRequestShizuku: () -> Unit = {}
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = AirPodsTheme.Shapes.Card,
        colors = CardDefaults.cardColors(
            containerColor = AirPodsTheme.Colors.AccentBlue.copy(alpha = 0.12f)
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = AirPodsTheme.Colors.AccentBlue
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = if (shizukuReady) "Shizuku connected" else "Compatibility Mode",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = AirPodsTheme.Colors.AccentBlue
                    )
                    if (shizukuStatus.isNotEmpty()) {
                        Text(
                            text = shizukuStatus,
                            fontSize = 12.sp,
                            color = AirPodsTheme.Colors.TextSecondary
                        )
                    }
                }
            }
            if (shizukuReady && shizukuStatus.isEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = onRequestShizuku,
                    shape = AirPodsTheme.Shapes.Pill,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AirPodsTheme.Colors.AccentBlue
                    )
                ) {
                    Text("Authorize Shizuku", color = Color.White, fontSize = 13.sp)
                }
            }
        }
    }
}
// ---- Empty State ----
@Composable
private fun EmptyHomeState(onRetry: () -> Unit = {}) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.BluetoothDisabled,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = AirPodsTheme.Colors.TextSecondary
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Not Connected",
            fontSize = 18.sp,
            color = AirPodsTheme.Colors.TextPrimary
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Make sure your AirPods are powered on and in range.\n\nMagicUI restricts Bluetooth GATT - install Shizuku to unlock ANC and other advanced controls.",
            fontSize = 14.sp,
            color = AirPodsTheme.Colors.TextSecondary
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(
            onClick = onRetry,
            shape = AirPodsTheme.Shapes.Pill,
            colors = ButtonDefaults.buttonColors(
                containerColor = AirPodsTheme.Colors.AccentBlue
            )
        ) {
            Text("Retry Connection", color = Color.White)
        }
    }
}

// ---- Reusable Feature Card wrapper ----
@Composable
private fun FeatureCard(
    title: String,
    icon: ImageVector,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = AirPodsTheme.Shapes.Card,
        colors = CardDefaults.cardColors(containerColor = AirPodsTheme.Colors.CardBackground)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 12.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = AirPodsTheme.Colors.AccentBlue
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = AirPodsTheme.Colors.TextPrimary
                )
            }
            content()
        }
    }
}






