package com.airpods.control.popup

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.animation.*
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.airpods.control.core.bluetooth.AirPodsDeviceManager
import com.airpods.control.core.bluetooth.ConnectionState
import com.airpods.control.core.model3d.RotatingModelView
import com.airpods.control.core.ui.AirPodsTheme
import kotlinx.coroutines.delay

class AirPodsPopupService(
    private val context: Context,
    private val deviceManager: AirPodsDeviceManager
) {
    private var overlayView: ComposeView? = null
    private val windowManager: WindowManager =
        context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var isShowing = false

    private val popupComposable: @androidx.compose.runtime.Composable () -> Unit = {
        val state by deviceManager.state.collectAsState()
        PopupContent(
            state = state,
            onDismiss = { hide() },
            onSettingsClick = { }
        )
    }

    fun show() {
        if (isShowing) return
        isShowing = true
        val params = WindowManager.LayoutParams().apply {
            type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            }
            flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
            format = PixelFormat.TRANSLUCENT
            width = WindowManager.LayoutParams.MATCH_PARENT
            height = WindowManager.LayoutParams.WRAP_CONTENT
            gravity = Gravity.BOTTOM
            y = 0
        }
        overlayView = ComposeView(context).apply { setContent { popupComposable() } }
        try { windowManager.addView(overlayView, params) } catch (_: Exception) { isShowing = false }
    }

    fun hide() {
        if (!isShowing) return
        isShowing = false
        try { overlayView?.let { windowManager.removeView(it) } } catch (_: Exception) { }
        overlayView = null
    }

    fun destroy() { hide() }
}

@Composable
fun PopupContent(
    state: com.airpods.control.core.bluetooth.DeviceState,
    onDismiss: () -> Unit,
    onSettingsClick: () -> Unit
) {
    LaunchedEffect(state.connectionState) {
        if (state.connectionState == ConnectionState.AACP_READY ||
            state.connectionState == ConnectionState.FALLBACK_READY) {
            delay(5000)
            onDismiss()
        }
    }

    AnimatedVisibility(
        visible = true,
        enter = slideInVertically(
            initialOffsetY = { it },
            animationSpec = spring(dampingRatio = 0.7f, stiffness = 300f)
        ) + fadeIn(),
        exit = slideOutVertically(
            targetOffsetY = { it },
            animationSpec = spring(dampingRatio = 0.7f, stiffness = 300f)
        ) + fadeOut()
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AirPodsTheme.Dimens.PopupHorizontalMargin)
                .padding(bottom = AirPodsTheme.Dimens.PopupBottomOffset)
                .clip(AirPodsTheme.Shapes.PopupTop)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(AirPodsTheme.Colors.CardBackground, Color(0xFF141416))
                    )
                )
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }
                ) { }
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "\u2699",
                        fontSize = 20.sp,
                        color = AirPodsTheme.Colors.TextSecondary,
                        modifier = Modifier.align(Alignment.TopEnd).clip(CircleShape)
                            .clickable { onSettingsClick() }.padding(4.dp)
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = when {
                        state.connectionState == ConnectionState.AACP_READY ||
                        state.connectionState == ConnectionState.FALLBACK_READY ->
                            state.model.displayName
                        state.connectionState == ConnectionState.CONNECTING -> "Connecting..."
                        else -> "AirPods Not Connected"
                    },
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.Medium,
                        color = AirPodsTheme.Colors.TextPrimary
                    ),
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(24.dp))
                when {
                    state.connectionState == ConnectionState.AACP_READY ||
                    state.connectionState == ConnectionState.FALLBACK_READY ->
                        ConnectedContentView(state)
                    state.connectionState == ConnectionState.CONNECTING ->
                        ConnectingContentView(state)
                    else -> DisconnectedContentView(state)
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Tap to dismiss",
                    fontSize = 12.sp,
                    color = AirPodsTheme.Colors.TextSecondary,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun ConnectedContentView(state: com.airpods.control.core.bluetooth.DeviceState) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.Bottom
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
            Box(modifier = Modifier.size(120.dp)) {
                RotatingModelView(imageRes = "model_buds", autoRotate = true, rotationPeriodMs = 7000)
            }
            Spacer(modifier = Modifier.height(12.dp))
            BatteryInfo("Earbuds", state.battery?.leftPercent, state.battery?.rightPercent, null, state.isCharging)
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
            Box(modifier = Modifier.size(120.dp)) {
                RotatingModelView(imageRes = "model_case", autoRotate = true, rotationPeriodMs = 8000)
            }
            Spacer(modifier = Modifier.height(12.dp))
            BatteryInfo("Case", null, null, state.battery?.casePercent, state.battery?.caseCharging ?: false)
        }
    }
}

@Composable
private fun ConnectingContentView(state: com.airpods.control.core.bluetooth.DeviceState) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Box(modifier = Modifier.size(180.dp)) {
            RotatingModelView(imageRes = "model_case_open", autoRotate = true, rotationPeriodMs = 8000)
        }
        Spacer(modifier = Modifier.height(16.dp))
        val dots = remember { mutableStateOf("") }
        LaunchedEffect(Unit) {
            while (true) {
                dots.value = "."; delay(400)
                dots.value = ".."; delay(400)
                dots.value = "..."; delay(400)
            }
        }
        Text(
            text = "Waiting for battery${dots.value}",
            fontSize = 16.sp,
            color = AirPodsTheme.Colors.TextSecondary
        )
    }
}

@Composable
private fun DisconnectedContentView(state: com.airpods.control.core.bluetooth.DeviceState) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            Box(modifier = Modifier.size(80.dp)) { RotatingModelView(imageRes = "model_left", autoRotate = false) }
            Box(modifier = Modifier.size(80.dp)) { RotatingModelView(imageRes = "model_right", autoRotate = false) }
            Box(modifier = Modifier.size(80.dp)) { RotatingModelView(imageRes = "model_case_closed", autoRotate = false) }
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Not connected. Open the case!",
            fontSize = 16.sp,
            color = AirPodsTheme.Colors.TextSecondary,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun BatteryInfo(
    label: String,
    leftPct: Int?,
    rightPct: Int?,
    casePct: Int?,
    isCharging: Boolean
) {
    val percent = casePct ?: (((leftPct ?: 0) + (rightPct ?: 0)) / 2)
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            val batteryColor = AirPodsTheme.Colors.BatteryGreen
            Box(
                modifier = Modifier
                    .width(28.dp).height(14.dp)
                    .background(Color.White.copy(alpha = 0.15f), RoundedCornerShape(3.dp))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(fraction = (percent / 100f).coerceIn(0f, 1f))
                        .background(batteryColor, RoundedCornerShape(3.dp))
                )
            }
            Spacer(modifier = Modifier.width(6.dp))
            val text = if (casePct != null) "$casePct%" else "~$percent%"
            Text(text = text, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = AirPodsTheme.Colors.TextPrimary)
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = if (isCharging) "Charging" else label,
            fontSize = 12.sp,
            color = AirPodsTheme.Colors.TextSecondary
        )
    }
}
