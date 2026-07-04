package com.npic.photoandsignscanner.features.save

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Draw
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.npic.photoandsignscanner.core.theme.LocalNpicChrome
import com.npic.photoandsignscanner.core.theme.NpicColors
import com.npic.photoandsignscanner.core.theme.NpicShapes
import com.npic.photoandsignscanner.core.theme.NpicSpacing
import com.npic.photoandsignscanner.core.theme.NpicTheme
import java.io.File

/**
 * The Save-sheet preview row (PRD §4.6, DESIGN §7.4). Renders the captured photo (left)
 * and signature (right) side-by-side at 80dp tall. Missing pieces become dashed
 * BorderStrong placeholders with a semantic label and — when [onAddPhoto] / [onAddSignature]
 * is non-null — an inline Saffron "Add" text button that fires without dismissing the
 * Save draft (PRD §4.6 empty-state affordances).
 */
@Composable
fun PreviewStrip(
    photoPath: String?,
    signaturePath: String?,
    onAddPhoto: (() -> Unit)?,
    onAddSignature: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth().height(80.dp),
        horizontalArrangement = Arrangement.spacedBy(NpicSpacing.sm),
    ) {
        PreviewTile(
            path = photoPath,
            missingLabel = "No photo",
            addLabel = "Add",
            onAdd = onAddPhoto,
            icon = { Icon(Icons.Outlined.Image, contentDescription = null) },
            modifier = Modifier.weight(1f).fillMaxSize(),
        )
        PreviewTile(
            path = signaturePath,
            missingLabel = "No signature",
            addLabel = "Add",
            onAdd = onAddSignature,
            icon = { Icon(Icons.Outlined.Draw, contentDescription = null) },
            modifier = Modifier.weight(1f).fillMaxSize(),
        )
    }
}

@Composable
private fun PreviewTile(
    path: String?,
    missingLabel: String,
    addLabel: String,
    onAdd: (() -> Unit)?,
    icon: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    val chrome = LocalNpicChrome.current
    val shape = NpicShapes.md
    val density = androidx.compose.ui.platform.LocalDensity.current
    val cornerRadius = with(density) { 14.dp.toPx() }
    // Hoist per-frame allocations out of Canvas draw scope (Oracle m-8b): both the
    // dashPathEffect and the Stroke are stable across recompositions.
    val dashEffect = remember { PathEffect.dashPathEffect(floatArrayOf(8f, 6f), 0f) }
    val dashStroke = remember(density, dashEffect) {
        Stroke(width = with(density) { 1.5.dp.toPx() }, pathEffect = dashEffect)
    }

    Box(
        modifier = modifier
            .clip(shape)
            .background(chrome.saffronSoft.copy(alpha = 0.35f), shape)
            .then(if (path == null && onAdd != null) Modifier.clickable(onClick = onAdd) else Modifier)
            .semantics {
                // Two distinct labels — Oracle M-8b-M5-QCC caught the "Captured No photo"
                // string that appeared when a path was present (announces the wrong state
                // to TalkBack).
                contentDescription = if (path == null) missingLabel else "Captured photo ready"
            },
        contentAlignment = Alignment.Center,
    ) {
        if (path == null) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawRoundRect(
                    color = chrome.borderStrong,
                    cornerRadius = CornerRadius(cornerRadius, cornerRadius),
                    style = dashStroke,
                    size = Size(size.width, size.height),
                    topLeft = Offset.Zero,
                )
            }
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(NpicSpacing.xxs),
            ) {
                Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) { icon() }
                Text(
                    text = missingLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = chrome.inkMuted,
                    textAlign = TextAlign.Center,
                )
                if (onAdd != null) {
                    Text(
                        // SaffronDeep + underline: fixes the ~1.8:1 Saffron-on-SaffronSoft@35%
                        // WCAG AA fail (Oracle M-8b-M4-QCC) while keeping the tile's warm
                        // accent, matching Stripe/Linear "premium destination link" pattern.
                        text = addLabel,
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight(600),
                            textDecoration = TextDecoration.Underline,
                        ),
                        color = NpicColors.SaffronDeep,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        } else {
            // Oracle #5 A8 (qc-round-10): Coil AsyncImage replaces the "Ready" stub.
            // File-model bypasses Coil's URI resolver — direct BitmapFactory.decodeFile
            // path is fastest for our on-disk SourceStore assets.
            AsyncImage(
                model = File(path),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().clip(shape),
            )
        }
    }
}

@Preview(name = "Preview strip — both missing", showBackground = true)
@Composable
private fun PreviewStripBothMissingPreview() {
    NpicTheme {
        Box(Modifier.padding(NpicSpacing.md).background(Color.White, RoundedCornerShape(12.dp))) {
            PreviewStrip(
                photoPath = null,
                signaturePath = null,
                onAddPhoto = {},
                onAddSignature = {},
            )
        }
    }
}

@Preview(name = "Preview strip — signature missing", showBackground = true)
@Composable
private fun PreviewStripSignatureMissingPreview() {
    NpicTheme {
        Box(Modifier.padding(NpicSpacing.md).background(Color.White, RoundedCornerShape(12.dp))) {
            PreviewStrip(
                photoPath = "/cache/drafts/photo_placeholder.jpg",
                signaturePath = null,
                onAddPhoto = null,
                onAddSignature = {},
            )
        }
    }
}
