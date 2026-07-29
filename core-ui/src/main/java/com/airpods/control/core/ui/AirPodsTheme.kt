package com.airpods.control.core.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Typography
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Design system tokens - single source of truth for AirPods Control app.
 * All UI elements reference these tokens; no hardcoded values.
 */
object AirPodsTheme {

    // ---- Color Palette ----
    object Colors {
        // Dark card background (matches iOS AirPods popup)
        val CardBackground = Color(0xFF1C1C1E)
        val CardBackgroundLight = Color(0xFFF2F2F7)

        // Text
        val TextPrimary = Color(0xFFFFFFFF)
        val TextSecondary = Color(0xFF8E8E93)
        val TextPrimaryLight = Color(0xFF1C1C1E)
        val TextSecondaryLight = Color(0xFF6E6E73)

        // Accent / interactive
        val AccentBlue = Color(0xFF0A84FF)
        val BatteryGreen = Color(0xFF34C759)
        val DestructiveRed = Color(0xFFFF453A)

        // Surface / backgrounds
        val SurfaceDark = Color(0xFF000000)
        val SurfaceLight = Color(0xFFFFFFFF)
        val Divider = Color(0x33FFFFFF)
        val DividerLight = Color(0x33000000)
    }

    // ---- Shapes ----
    object Shapes {
        /** Popup top corners: 28dp */
        val PopupTop = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
        /** Full popup card (bottom sheet): all 28dp */
        val PopupCard = RoundedCornerShape(28.dp)
        /** Feature card: 20dp */
        val Card = RoundedCornerShape(20.dp)
        /** Pill / capsule button: fully rounded */
        val Pill = RoundedCornerShape(percent = 50)
        /** Small rounded button */
        val Small = RoundedCornerShape(12.dp)
    }

    // ---- Typography ----
    val Typography = Typography(
        headlineLarge = TextStyle(
            fontSize = 24.sp,
            fontWeight = FontWeight.Medium,
            lineHeight = 30.sp
        ),
        headlineMedium = TextStyle(
            fontSize = 20.sp,
            fontWeight = FontWeight.Medium,
            lineHeight = 26.sp
        ),
        titleLarge = TextStyle(
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            lineHeight = 24.sp
        ),
        titleMedium = TextStyle(
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            lineHeight = 22.sp
        ),
        bodyLarge = TextStyle(
            fontSize = 16.sp,
            fontWeight = FontWeight.Normal,
            lineHeight = 22.sp
        ),
        bodyMedium = TextStyle(
            fontSize = 14.sp,
            fontWeight = FontWeight.Normal,
            lineHeight = 20.sp
        ),
        labelLarge = TextStyle(
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            lineHeight = 20.sp
        ),
        labelSmall = TextStyle(
            fontSize = 12.sp,
            fontWeight = FontWeight.Normal,
            lineHeight = 16.sp
        )
    )

    // ---- Animation specs ----
    object Animation {
        /** Spring used for popup slide-in/out */
        val PopupSpring = spring<Float>(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        )
        /** Spring for card/control interactions */
        val CardSpring = spring<Float>(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        )
        /** Subtle spring for icon morphing */
        val MorphSpring = spring<Float>(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMedium
        )
    }

    // ---- Dimensions ----
    object Dimens {
        val PopupHorizontalMargin = 16.dp
        val PopupBottomOffset = 8.dp
        val CardPadding = 16.dp
        val CardSpacing = 12.dp
        val SectionSpacing = 24.dp
        val IconSize = 24.dp
        val LargeIconSize = 48.dp
    }
}

// ---- Composition locals for dynamic theme access ----
val LocalAirPodsColors = staticCompositionLocalOf { AirPodsTheme.Colors }
