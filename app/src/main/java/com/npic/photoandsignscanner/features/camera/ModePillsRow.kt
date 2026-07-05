package com.npic.photoandsignscanner.features.camera

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.npic.photoandsignscanner.core.theme.LocalNpicChrome
import com.npic.photoandsignscanner.core.theme.LocalReduceMotion
import com.npic.photoandsignscanner.core.theme.NpicColors
import com.npic.photoandsignscanner.core.theme.NpicMotion
import com.npic.photoandsignscanner.core.theme.NpicSpacing
import com.npic.photoandsignscanner.core.theme.NpicTheme
import com.npic.photoandsignscanner.domain.model.CameraMode

/**
 * Text-only mode selector for the Camera screen. Per DESIGN §7.2: no pill background, no
 * capsule, no border — just two `titleMedium` labels, 32dp apart, with saffron/muted color
 * crossfade on selection. When [mode] is [CameraMode.Signature], a "Draw instead" text link
 * appears 12dp below (Saffron `labelMedium`, weight 600).
 */
@Composable
fun ModePillsRow(
    mode: CameraMode,
    onModeChange: (CameraMode) -> Unit,
    onDrawInsteadClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            modifier = Modifier.selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(NpicSpacing.xxl),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CameraMode.entries.forEach { candidate ->
                ModePill(
                    label    = candidate.label,
                    selected = candidate == mode,
                    onClick  = { onModeChange(candidate) },
                )
            }
        }
        if (mode == CameraMode.Signature) {
            Text(
                text  = "Draw instead",
                color = NpicColors.Saffron,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight(600)),
                modifier = Modifier
                    .padding(top = NpicSpacing.sm)
                    .defaultMinSize(minHeight = 44.dp)
                    .semantics { role = Role.Button }
                    .clickable(onClick = onDrawInsteadClick)
                    .padding(horizontal = NpicSpacing.md, vertical = NpicSpacing.xs),
            )
        }
    }
}

@Composable
private fun ModePill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val chrome = LocalNpicChrome.current
    val reduceMotion = LocalReduceMotion.current
    val ink by animateColorAsState(
        targetValue = if (selected) NpicColors.Saffron else chrome.cameraInkMuted,
        animationSpec = NpicMotion.standardOrSnap(reduceMotion),
        label = "mode_pill_ink",
    )
    val scale by animateFloatAsState(
        targetValue = if (selected) 1.06f else 1f,
        animationSpec = NpicMotion.springSnappyOrSnap(reduceMotion),
        label = "mode_pill_scale",
    )
    Text(
        text  = label,
        color = ink,
        style = MaterialTheme.typography.titleMedium.copy(
            fontWeight = if (selected) FontWeight(700) else FontWeight(500),
        ),
        modifier = Modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .defaultMinSize(minHeight = 44.dp)
            .clickable(onClick = onClick)
            .semantics {
                role = Role.RadioButton
                this.selected = selected
            }
            .padding(horizontal = NpicSpacing.xl, vertical = NpicSpacing.sm),
    )
}

@Preview(name = "ModePillsRow — Photo selected")
@Composable
private fun ModePillsRowPhotoPreview() {
    NpicTheme {
        val chrome = LocalNpicChrome.current
        androidx.compose.foundation.layout.Box(
            Modifier
                .padding(NpicSpacing.xl)
                .defaultMinSize(minHeight = 100.dp),
        ) {
            ModePillsRow(
                mode = CameraMode.Photo,
                onModeChange = {},
                onDrawInsteadClick = {},
                modifier = Modifier.padding(NpicSpacing.xl),
            )
        }
    }
}

@Preview(name = "ModePillsRow — Signature selected")
@Composable
private fun ModePillsRowSignaturePreview() {
    NpicTheme {
        ModePillsRow(
            mode = CameraMode.Signature,
            onModeChange = {},
            onDrawInsteadClick = {},
            modifier = Modifier.padding(NpicSpacing.xl),
        )
    }
}
