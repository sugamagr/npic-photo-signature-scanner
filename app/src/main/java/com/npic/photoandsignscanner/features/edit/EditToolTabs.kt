package com.npic.photoandsignscanner.features.edit

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.npic.photoandsignscanner.core.theme.LocalNpicChrome
import com.npic.photoandsignscanner.core.theme.LocalReduceMotion
import com.npic.photoandsignscanner.core.theme.NpicColors
import com.npic.photoandsignscanner.core.theme.NpicMotion
import com.npic.photoandsignscanner.core.theme.NpicShapes
import com.npic.photoandsignscanner.core.theme.NpicSpacing

/**
 * 56dp row of four equal-width tool tabs on [CameraSurface]. Active tab renders a pill
 * capsule filled with SaffronSoft @ 22% over CameraSurface, [NpicShapes.sm] corners, and
 * icon+label swap to Saffron with label weight 700. Per DESIGN §7.3.
 */
@Composable
fun EditToolTabs(
    active: EditTool,
    onSelect: (EditTool) -> Unit,
    modifier: Modifier = Modifier,
) {
    val chrome = LocalNpicChrome.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(chrome.cameraSurface)
            .windowInsetsPadding(WindowInsets.navigationBars)
            .height(56.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        EditTool.entries.forEach { tool ->
            EditToolTab(
                tool = tool,
                isActive = tool == active,
                onClick = { onSelect(tool) },
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
        }
    }
}

@Composable
private fun EditToolTab(
    tool: EditTool,
    isActive: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val chrome = LocalNpicChrome.current
    val reduceMotion = LocalReduceMotion.current
    val ink by animateColorAsState(
        targetValue = if (isActive) NpicColors.Saffron else chrome.cameraInkMuted,
        animationSpec = NpicMotion.fastOrSnap(reduceMotion),
        label = "tab_ink",
    )
    val pillFill by animateColorAsState(
        targetValue = if (isActive) NpicColors.SaffronSoft.copy(alpha = 0.22f) else Color.Transparent,
        animationSpec = NpicMotion.fastOrSnap(reduceMotion),
        label = "tab_pill",
    )
    Box(
        modifier = modifier
            .semantics(mergeDescendants = true) {
                role = Role.Tab
                selected = isActive
            }
            .clickable(onClick = onClick)
            .padding(horizontal = NpicSpacing.xs, vertical = NpicSpacing.xxs),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .clip(NpicShapes.sm)
                .background(pillFill, NpicShapes.sm)
                .padding(horizontal = NpicSpacing.xs, vertical = NpicSpacing.xxs),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(NpicSpacing.xxs),
        ) {
            Icon(
                imageVector = tool.icon,
                contentDescription = null,
                tint = ink,
                modifier = Modifier.height(24.dp),
            )
            Text(
                text = tool.label,
                color = ink,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = if (isActive) FontWeight(700) else FontWeight(500),
                ),
            )
        }
    }
}
