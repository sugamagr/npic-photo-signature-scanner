package com.npic.photoandsignscanner.core.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.npic.photoandsignscanner.core.theme.LocalNpicChrome
import com.npic.photoandsignscanner.core.theme.NpicColors
import com.npic.photoandsignscanner.core.theme.NpicMotion
import com.npic.photoandsignscanner.core.theme.NpicShapes
import com.npic.photoandsignscanner.core.theme.NpicSpacing
import com.npic.photoandsignscanner.core.theme.NpicTheme

/**
 * Selectable pill. 36dp tall, `NpicShapes.full`. Used for:
 * - Gallery class filters (All / 9 / 10 / 11 / 12)
 * - Sort mode (Newest / Oldest / Class / Name)
 * - Filter preset picker in Edit
 *
 * Selection is a container swap (Ivory → SaffronSoft) + border swap (BorderStrong → Saffron)
 * animated over `NpicMotion.StandardMs` (DESIGN §5.2). Label weight bumps 500 → 600 on select.
 */
@Composable
fun NpicChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null,
    enabled: Boolean = true,
) {
    val chrome = LocalNpicChrome.current
    val container by animateColorAsState(
        targetValue = when {
            !enabled -> NpicColors.Ivory
            selected -> chrome.saffronSoft
            else     -> NpicColors.Surface
        },
        animationSpec = NpicMotion.standard(),
        label = "chip_container",
    )
    val border by animateColorAsState(
        targetValue = when {
            !enabled -> chrome.borderSoft
            selected -> NpicColors.Saffron
            else     -> chrome.borderStrong
        },
        animationSpec = NpicMotion.standard(),
        label = "chip_border",
    )
    val label_ = if (enabled) NpicColors.Ink else chrome.inkFaint
    val interactionSource = remember { MutableInteractionSource() }

    Box(
        modifier = modifier
            .semantics { role = Role.Button }
            .defaultMinSize(minHeight = 34.dp)
            .clip(NpicShapes.xs)
            .background(container, NpicShapes.xs)
            .border(width = 1.dp, color = border, shape = NpicShapes.xs)
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(bounded = true, color = label_),
                enabled = enabled,
                onClick = onClick,
            )
            .padding(PaddingValues(horizontal = 14.dp, vertical = NpicSpacing.xxs)),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(NpicSpacing.xs),
        ) {
            if (leadingIcon != null) {
                Icon(
                    imageVector = leadingIcon,
                    contentDescription = null,
                    tint = label_,
                    modifier = Modifier.size(16.dp),
                )
            }
            Text(
                text  = label,
                color = label_,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = if (selected) FontWeight(600) else FontWeight(500),
                ),
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Previews
// ─────────────────────────────────────────────────────────────────────────────

@Preview(name = "Chips — filter row")
@Composable
private fun ChipPreview() {
    NpicTheme {
        Box(Modifier.background(NpicColors.Ivory).padding(NpicSpacing.md)) {
            Row(horizontalArrangement = Arrangement.spacedBy(NpicSpacing.xs)) {
                NpicChip("All",     selected = true,  onClick = {})
                NpicChip("Class 9", selected = false, onClick = {})
                NpicChip("Class 10", selected = false, onClick = {})
                NpicChip("Class 11", selected = false, onClick = {})
                NpicChip("Class 12", selected = false, onClick = {})
            }
        }
    }
}
