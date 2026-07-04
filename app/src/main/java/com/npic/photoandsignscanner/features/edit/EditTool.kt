package com.npic.photoandsignscanner.features.edit

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.RotateRight
import androidx.compose.material.icons.outlined.Crop
import androidx.compose.material.icons.outlined.Style
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The four Edit tabs, in DESIGN §7.3 order. Each carries its own label + line icon + the
 * open-panel height per §7.3.0.a; the screen animates viewport height to accommodate.
 */
enum class EditTool(
    val label: String,
    val icon: ImageVector,
    val panelHeight: Dp,
) {
    Crop(   label = "Crop",   icon = Icons.Outlined.Crop,        panelHeight = 64.dp),
    Filter( label = "Filter", icon = Icons.Outlined.Style,       panelHeight = 120.dp),
    Adjust( label = "Adjust", icon = Icons.Outlined.Tune,        panelHeight = 216.dp),
    Rotate( label = "Rotate", icon = Icons.AutoMirrored.Outlined.RotateRight, panelHeight = 140.dp),
}
