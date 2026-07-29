package com.airpods.control.core.model3d

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import coil.compose.AsyncImage
import coil.request.ImageRequest
import kotlinx.coroutines.delay

/**
 * 3D model rendering abstraction for AirPods popup.
 *
 * Strategy:
 * - HIGH tier: glTF/GLB via SceneView (Filament) - full PBR + interactive rotation.
 * - MID tier: Pre-rendered frame sequence with rotation interpolation.
 * - LOW tier: Static PNG with CSS-like rotateY transform.
 *
 * This file implements the LOW/MID tier using Compose Canvas + image sequences.
 * The HIGH tier (SceneView) requires filament/sceneform dependency and is deferred.
 */
object ModelRenderer {

    enum class QualityTier {
        HIGH,   // OpenGL / Filament glTF
        MID,    // Pre-rendered frame sequence
        LOW     // Static image + rotation
    }

    /**
     * Detect appropriate quality tier based on device capabilities.
     * Simple heuristic: API level + RAM class.
     */
    fun detectQualityTier(apiLevel: Int, isLowRamDevice: Boolean): QualityTier {
        return when {
            apiLevel >= 31 && !isLowRamDevice -> QualityTier.HIGH
            apiLevel >= 28 && !isLowRamDevice -> QualityTier.MID
            else -> QualityTier.LOW
        }
    }
}

/**
 * Renders a 3D-rotating model using image-based approach (MID/LOW tier).
 */
@Composable
fun RotatingModelView(
    imageRes: String,
    autoRotate: Boolean = true,
    rotationPeriodMs: Int = 7000,
    modifier: Modifier = Modifier,
    onManualRotation: ((Float) -> Unit)? = null
) {
    // Auto-rotation animation
    val infiniteRotation = rememberInfiniteTransition(label = "model_rotation")
    val autoAngle by infiniteRotation.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(rotationPeriodMs, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "angle"
    )

    // Manual drag rotation offset
    var manualOffset by remember { mutableFloatStateOf(0f) }
    val currentAngle = if (autoRotate) autoAngle + manualOffset else manualOffset

    Box(
        modifier = modifier.pointerInput(Unit) {
            detectHorizontalDragGestures(
                onDragEnd = {
                    onManualRotation?.invoke(manualOffset)
                }
            ) { _, dragAmount ->
                manualOffset += dragAmount * 0.5f
            }
        },
        contentAlignment = Alignment.Center
    ) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(imageRes)
                .crossfade(true)
                .build(),
            contentDescription = "AirPods 3D Model",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Fit
        )
    }
}

/**
 * Canvas-based 3D rotation renderer for programmatic model drawing.
 */
@Composable
fun CanvasRotatingModel(
    frameDrawables: List<ImageBitmap>,
    currentFrame: Int,
    rotationY: Float = 0f,
    modifier: Modifier = Modifier
) {
    if (frameDrawables.isEmpty()) return

    val frame = frameDrawables[currentFrame % frameDrawables.size]

    Canvas(modifier = modifier) {
        rotate(rotationY, pivot = Offset(size.width / 2, size.height / 2)) {
            drawImage(
                image = frame,
                dstOffset = IntOffset.Zero,
                dstSize = IntSize(size.width.toInt(), size.height.toInt())
            )
        }
    }
}

/**
 * Spring-based rotation value that provides inertia/rebound for manual drag.
 */
@Composable
fun rememberSpringRotation(): Animatable<Float, AnimationVector1D> {
    return remember {
        Animatable(0f, Float.VectorConverter)
    }
}
