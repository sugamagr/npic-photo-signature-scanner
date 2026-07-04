package com.npic.photoandsignscanner.features.camera

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.npic.photoandsignscanner.core.theme.LocalNpicChrome
import com.npic.photoandsignscanner.core.theme.NpicColors
import com.npic.photoandsignscanner.core.theme.NpicMotion
import com.npic.photoandsignscanner.core.theme.NpicTheme

/**
 * The Camera shutter. Per DESIGN §7.2:
 *   • 96dp hit target
 *   • 72dp hollow circle with a 4dp CameraInk (white) ring
 *   • Inner fill: transparent by default; during capture it fills with a Saffron progress
 *     arc from 12 o'clock over 200ms
 *   • Press animation: scale to 92% over 100ms
 *
 * The [capturing] flag drives the progress arc; the caller flips it via
 * `CameraViewModel.onCaptureStarted` / `onCaptureFinished`.
 */
@Composable
fun ShutterButton(
    onClick: () -> Unit,
    capturing: Boolean,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val chrome = LocalNpicChrome.current
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.92f else 1f,
        animationSpec = NpicMotion.fast(),
        label = "shutter_scale",
    )
    val arcSweep by animateFloatAsState(
        targetValue = if (capturing) 360f else 0f,
        animationSpec = NpicMotion.standard(),
        label = "shutter_arc",
    )

    Box(
        modifier = modifier
            .size(96.dp)
            .clip(CircleShape)
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(bounded = false, radius = 48.dp, color = chrome.cameraInk),
                enabled = enabled,
                onClick = onClick,
            )
            .semantics {
                role = Role.Button
                contentDescription = if (capturing) "Capturing" else "Shutter"
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(
            modifier = Modifier
                .size(72.dp)
                .graphicsLayer { scaleX = scale; scaleY = scale },
        ) {
            val diameter = size.minDimension
            val ringPx   = 4.dp.toPx()
            // Outer 4dp CameraInk ring — inset by half stroke so the outer edge lands at the
            // canvas boundary.
            drawCircle(
                color  = chrome.cameraInk,
                radius = (diameter - ringPx) / 2f,
                style  = Stroke(width = ringPx),
            )
            // Progress arc — sweeps clockwise from -90° (12 o'clock) to fill the inner
            // area with Saffron. Drawn inside the ring so it doesn't overlap the outline.
            if (arcSweep > 0f) {
                val inset = ringPx + 2.dp.toPx()
                drawArc(
                    color = NpicColors.Saffron,
                    startAngle = -90f,
                    sweepAngle = arcSweep,
                    useCenter  = true,
                    topLeft = Offset(inset, inset),
                    size = Size(diameter - 2 * inset, diameter - 2 * inset),
                )
            }
        }
    }
}

@Preview(name = "ShutterButton — idle")
@Composable
private fun ShutterButtonIdlePreview() {
    NpicTheme {
        val chrome = LocalNpicChrome.current
        Box(Modifier.size(160.dp).background(chrome.cameraBg), contentAlignment = Alignment.Center) {
            ShutterButton(onClick = {}, capturing = false)
        }
    }
}

@Preview(name = "ShutterButton — capturing")
@Composable
private fun ShutterButtonCapturingPreview() {
    NpicTheme {
        val chrome = LocalNpicChrome.current
        Box(Modifier.size(160.dp).background(chrome.cameraBg), contentAlignment = Alignment.Center) {
            ShutterButton(onClick = {}, capturing = true)
        }
    }
}

