package com.airpods.control.settings

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.airpods.control.core.data.AppPreferences
import com.airpods.control.core.ui.AirPodsTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    preferences: AppPreferences,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var popupEnabled by preferences.popupEnabled.collectAsState(initial = true).let { remember { mutableStateOf(true) } }
    var darkTheme by preferences.darkTheme.collectAsState(initial = 0).let { remember { mutableIntStateOf(0) } }
    var protocolMode by preferences.protocolMode.collectAsState(initial = 0).let { remember { mutableIntStateOf(0) } }

    // Re-collect from flows
    LaunchedEffect(Unit) {
        preferences.popupEnabled.collect { popupEnabled = it }
        preferences.darkTheme.collect { darkTheme = it }
        preferences.protocolMode.collect { protocolMode = it }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings", fontWeight = FontWeight.Medium) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            // ---- Popup Settings ----
            SettingsSection("Popup") {
                SwitchSettingRow(
                    title = "Popup",
                    subtitle = "Auto popup on connection",
                    checked = popupEnabled,
                    onCheckedChange = { popupEnabled = it; /* save pref */ }
                )
            }

            // ---- Theme ----
            SettingsSection("Theme") {
                val themeOptions = listOf("System", "Light", "Dark")
                themeOptions.forEachIndexed { index, label ->
                    SelectableRow(
                        title = label,
                        selected = darkTheme == index,
                        onClick = { darkTheme = index; /* save pref */ }
                    )
                }
            }

            // ---- Protocol ----
            SettingsSection("Protocol") {
                val protoOptions = listOf("Auto (AACP first)", "AACP only", "Standard")
                protoOptions.forEachIndexed { index, label ->
                    SelectableRow(
                        title = label,
                        selected = protocolMode == index,
                        onClick = { protocolMode = index; /* save pref */ }
                    )
                }
            }

            // ---- Background ----
            SettingsSection("Background") {
                // Battery optimization
                ClickableRow(
                    title = "Battery Optimization",
                    subtitle = "Prevent system from killing background service",
                    icon = Icons.Default.BatterySaver
                ) {
                    val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                    context.startActivity(intent)
                }

                // Auto-start guide (per ROM)
                ClickableRow(
                    title = "Auto-Start Management",
                    subtitle = "Guide for current ROM",
                    icon = Icons.Default.PlayArrow
                ) {
                    // Try Huawei
                    try {
                        val intent = Intent().setClassName(
                            "com.huawei.systemmanager",
                            "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"
                        )
                        context.startActivity(intent)
                    } catch (_: Exception) {
                        // Try Xiaomi
                        try {
                            val intent = Intent().setClassName(
                                "com.miui.securitycenter",
                                "com.miui.securitycenter.autostart.AutoStartManagementActivity"
                            )
                            context.startActivity(intent)
                        } catch (_: Exception) {
                            // Fallback to app settings
                            val intent = Intent(
                                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                Uri.parse("package:${context.packageName}")
                            )
                            context.startActivity(intent)
                        }
                    }
                }

                // Permission check shortcut
                ClickableRow(
                    title = "Notification Permission",
                    subtitle = "Manage notification permissions",
                    icon = Icons.Default.OpenInFull
                ) {
                    val intent = Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:${context.packageName}")
                    )
                    context.startActivity(intent)
                }
            }

            // ---- Debug (debug builds only) ----
            // In production, hide this section via BuildConfig.DEBUG check

            // ---- Privacy ----
            SettingsSection("Privacy") {
                Text(
                    text = "This app does not collect any personal information." +
                           "Only requests Bluetooth and location permissions to connect to AirPods.",
                    fontSize = 13.sp,
                    color = AirPodsTheme.Colors.TextSecondary,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }

            // ---- About ----
            SettingsSection("About") {
                Text(
                    text = "AirPods Control v1.0.0\n\n" +
                           "Based on LibrePods open-source project (Kavish Devar) and AACP protocol engineering\n\n" +
                           "Notes:\n" +
                           "\u2022 Device info synced via iCloud and Android local implementation\n" +
                           "\u2022 Some features depend on AACP protocol support\n" +
                           "\u2022 Different ROMs / background policies may affect connection stability\n" +
                           "\u2022 Spatial audio tracking may vary by device\n\n" +
                           "This app does not require root access",
                    fontSize = 13.sp,
                    color = AirPodsTheme.Colors.TextSecondary,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

// ---- Reusable Settings Components ----

@Composable
private fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        Text(
            text = title,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = AirPodsTheme.Colors.TextSecondary,
            modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
        )
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = AirPodsTheme.Shapes.Card,
            colors = CardDefaults.cardColors(containerColor = AirPodsTheme.Colors.CardBackground)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                content()
            }
        }
    }
}

@Composable
private fun SwitchSettingRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontSize = 15.sp, color = AirPodsTheme.Colors.TextPrimary)
            Text(subtitle, fontSize = 13.sp, color = AirPodsTheme.Colors.TextSecondary)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(checkedTrackColor = AirPodsTheme.Colors.BatteryGreen)
        )
    }
}

@Composable
private fun SelectableRow(
    title: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, fontSize = 15.sp, color = AirPodsTheme.Colors.TextPrimary)
        if (selected) {
            Icon(
                Icons.Default.Check,
                contentDescription = null,
                tint = AirPodsTheme.Colors.AccentBlue,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun ClickableRow(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = AirPodsTheme.Colors.AccentBlue,
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontSize = 15.sp, color = AirPodsTheme.Colors.TextPrimary)
            Text(subtitle, fontSize = 13.sp, color = AirPodsTheme.Colors.TextSecondary)
        }
        Icon(
            Icons.Filled.ChevronRight,
            contentDescription = null,
            tint = AirPodsTheme.Colors.TextSecondary,
            modifier = Modifier.size(20.dp)
        )
    }
}



