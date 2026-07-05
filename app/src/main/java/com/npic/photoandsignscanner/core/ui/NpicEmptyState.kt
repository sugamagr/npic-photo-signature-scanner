package com.npic.photoandsignscanner.core.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.npic.photoandsignscanner.core.theme.LocalNpicChrome
import com.npic.photoandsignscanner.core.theme.LocalReduceMotion
import com.npic.photoandsignscanner.core.theme.NpicColors
import com.npic.photoandsignscanner.core.theme.NpicMotion
import com.npic.photoandsignscanner.core.theme.NpicShapes
import com.npic.photoandsignscanner.core.theme.NpicSpacing
import com.npic.photoandsignscanner.core.theme.NpicTheme

/**
 * Empty state for Gallery ("no records yet") and filtered lists ("no records for Class 12").
 * Centered column: 64dp icon in a SaffronSoft circle, Fraunces title, InkMuted body, optional
 * primary action.
 *
 * NOT for error states — those use [NpicToast] with `Error` tone.
 */
@Composable
fun NpicEmptyState(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    icon: ImageVector = Icons.Outlined.PhotoCamera,
    action: (@Composable () -> Unit)? = null,
) {
    val chrome = LocalNpicChrome.current
    val reduceMotion = LocalReduceMotion.current
    val bobDp: State<Float> = if (reduceMotion) {
        remember { mutableFloatStateOf(0f) }
    } else {
        rememberInfiniteTransition(label = "emptystate_bob").animateFloat(
            initialValue = -4f,
            targetValue = 4f,
            animationSpec = infiniteRepeatable(
                animation = tween(2000, easing = NpicMotion.EaseInOutCubic),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "emptystate_bob_offset",
        )
    }
    Box(
        modifier = modifier.fillMaxSize().padding(NpicSpacing.xl),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(NpicSpacing.md),
        ) {
            Box(
                modifier = Modifier
                    .graphicsLayer { translationY = bobDp.value * density }
                    .size(96.dp)
                    .background(chrome.saffronSoft, shape = NpicShapes.full),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = NpicColors.Saffron,
                    modifier = Modifier.size(40.dp),
                )
            }
            Text(
                text  = title,
                color = NpicColors.Ink,
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center,
            )
            Text(
                text  = body,
                color = chrome.inkMuted,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = NpicSpacing.md),
            )
            if (action != null) {
                Box(Modifier.padding(top = NpicSpacing.sm)) { action() }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Previews
// ─────────────────────────────────────────────────────────────────────────────

@Preview(name = "EmptyState — no records")
@Composable
private fun EmptyStatePreview() {
    NpicTheme {
        Box(Modifier.background(NpicColors.Ivory)) {
            NpicEmptyState(
                title = "No records yet",
                body  = "Tap Capture to scan the first passport photo. Signatures come next.",
                action = { NpicButton("Capture", onClick = {}, size = NpicButtonSize.Small) },
            )
        }
    }
}
