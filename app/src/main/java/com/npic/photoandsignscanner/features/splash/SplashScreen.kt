package com.npic.photoandsignscanner.features.splash

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseInCubic
import androidx.compose.animation.core.EaseOutCubic
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.npic.photoandsignscanner.R
import com.npic.photoandsignscanner.core.theme.LocalNpicChrome
import com.npic.photoandsignscanner.core.theme.LocalReduceMotion
import com.npic.photoandsignscanner.core.theme.NpicColors
import com.npic.photoandsignscanner.core.theme.NpicShapes
import com.npic.photoandsignscanner.core.theme.NpicSpacing
import kotlinx.coroutines.delay

/**
 * m2513: branded launch splash (DESIGN §7.11).
 *
 * Trigger: MainActivity's NavHost has [Route.Splash] as the start destination.
 * The system SplashScreen (themes.xml windowSplashScreenAnimationDuration=200)
 * dismisses as soon as Compose mounts; this composable is the FIRST thing the
 * user sees under Compose ownership. After [HOLD_MS] the caller navigates to
 * Gallery and pops Splash off the back stack (see MainActivity Route.Splash
 * composable wiring).
 *
 * Composition:
 *   - Full-bleed ivory background (matches the system splash so the handoff has
 *     zero color flash)
 *   - Centered gold PS-in-photo-frame logo card (240dp rounded square, drawable-
 *     nodpi/splash_logo.png) with fade-in + subtle scale-in
 *   - Saffron pulse ring behind the logo card, infinite while splash is up
 *   - Wordmark 'NPIC' + tagline 'Photo and signature scanner' below
 *   - Thin saffron progress indicator at the bottom
 *
 * Motion respects LocalReduceMotion — pulse ring and scale-in collapse to
 * static under system reduce-motion setting.
 */
@Composable
fun SplashScreen(onDone: () -> Unit) {
    val reduceMotion = LocalReduceMotion.current
    val chrome = LocalNpicChrome.current

    // Entrance: fade-in over 220ms, scale-in from 0.92 → 1.0 over 400ms.
    val alpha = remember { Animatable(if (reduceMotion) 1f else 0f) }
    val scale = remember { Animatable(if (reduceMotion) 1f else 0.92f) }
    LaunchedEffect(Unit) {
        if (!reduceMotion) {
            alpha.animateTo(1f, tween(durationMillis = 220, easing = EaseOutCubic))
        }
    }
    LaunchedEffect(Unit) {
        if (!reduceMotion) {
            scale.animateTo(1f, tween(durationMillis = 400, easing = EaseOutCubic))
        }
    }

    // Infinite saffron pulse ring: expands 1.0 → 1.35 while fading 0.35 → 0 over
    // 1400ms, restarts. Two visible pulses across the 1800ms hold. Skipped when
    // reduce-motion is on.
    val pulseAlpha: Float = if (reduceMotion) 0f else {
        val transition = rememberInfiniteTransition(label = "splash-pulse-alpha")
        val v by transition.animateFloat(
            initialValue = 0.35f,
            targetValue = 0f,
            animationSpec = infiniteRepeatable(
                animation = tween(PULSE_MS, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
            label = "pulse-alpha",
        )
        v
    }
    val pulseScale: Float = if (reduceMotion) 1f else {
        val transition = rememberInfiniteTransition(label = "splash-pulse-scale")
        val v by transition.animateFloat(
            initialValue = 1f,
            targetValue = 1.35f,
            animationSpec = infiniteRepeatable(
                animation = tween(PULSE_MS, easing = EaseOutCubic),
                repeatMode = RepeatMode.Restart,
            ),
            label = "pulse-scale",
        )
        v
    }

    // Hand-off: hold for HOLD_MS, fade out over 200ms, then call onDone.
    LaunchedEffect(Unit) {
        delay(HOLD_MS)
        if (!reduceMotion) {
            alpha.animateTo(0f, tween(durationMillis = 200, easing = EaseInCubic))
        }
        onDone()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(NpicColors.Ivory),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.graphicsLayer { this.alpha = alpha.value },
        ) {
            // Logo card + pulse ring stacked in a Box so the ring sits behind.
            Box(contentAlignment = Alignment.Center) {
                // Pulse ring: drawn behind, larger than the card, saffron with fading alpha.
                Box(
                    modifier = Modifier
                        .size(RING_BASE_DP.dp)
                        .graphicsLayer {
                            scaleX = pulseScale
                            scaleY = pulseScale
                        }
                        .drawBehind {
                            if (pulseAlpha > 0f) {
                                drawCircleRing(
                                    color = NpicColors.Saffron.copy(alpha = pulseAlpha),
                                    strokePx = RING_STROKE_DP * density,
                                )
                            }
                        }
                )
                // Logo card
                Image(
                    painter = painterResource(R.drawable.splash_logo),
                    contentDescription = "NPIC Photo and Signature Scanner",
                    modifier = Modifier
                        .size(LOGO_DP.dp)
                        .graphicsLayer {
                            scaleX = scale.value
                            scaleY = scale.value
                        }
                        .clip(RoundedCornerShape(LOGO_CORNER_DP.dp)),
                )
            }

            Spacer(Modifier.height(NpicSpacing.xxl))

            // Wordmark
            Text(
                text = "NPIC",
                style = MaterialTheme.typography.displayMedium.copy(
                    fontWeight = FontWeight(600),
                    letterSpacing = 2.sp,
                ),
                color = NpicColors.Ink,
            )
            Spacer(Modifier.height(NpicSpacing.xxs))
            Text(
                text = "Photo and signature scanner",
                style = MaterialTheme.typography.bodyMedium,
                color = chrome.inkMuted,
            )
        }

        // Thin saffron progress ring bottom-of-screen for the "loading" affordance.
        Column(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { this.alpha = alpha.value },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Bottom,
        ) {
            CircularProgressIndicator(
                color = NpicColors.Saffron,
                strokeWidth = 2.dp,
                modifier = Modifier.size(22.dp),
            )
            Spacer(Modifier.height(NpicSpacing.xxxl))
        }
    }
}

/**
 * Draw a stroked circle inscribed in the composable's bounds. Used for the
 * pulse ring behind the splash logo card so the ring is a true circle rather
 * than the rounded-square shape a `border(...)` on the card would produce.
 */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawCircleRing(
    color: Color,
    strokePx: Float,
) {
    val diameter = size.minDimension - strokePx
    val topLeft = Offset(
        x = (size.width - diameter) / 2f,
        y = (size.height - diameter) / 2f,
    )
    drawArc(
        color = color,
        startAngle = 0f,
        sweepAngle = 360f,
        useCenter = false,
        topLeft = topLeft,
        size = Size(diameter, diameter),
        style = androidx.compose.ui.graphics.drawscope.Stroke(width = strokePx),
    )
}

private const val HOLD_MS = 1800L
private const val PULSE_MS = 1400
private const val LOGO_DP = 168
private const val LOGO_CORNER_DP = 32
private const val RING_BASE_DP = 200
private const val RING_STROKE_DP = 3f
