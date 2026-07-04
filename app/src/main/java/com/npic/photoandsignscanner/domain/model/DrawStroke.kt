package com.npic.photoandsignscanner.domain.model

import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Offset

/**
 * A single continuous pen stroke: the ordered list of touch samples in canvas coordinates
 * plus the pen thickness (in canvas pixels, not dp) applied to the whole stroke.
 *
 * Stroke width is fixed per-stroke rather than per-point because PRD §4.4 v1.0 explicitly
 * declines pressure sensitivity — the user picks a thickness with the slider, draws a
 * stroke at that thickness, and can change thickness only between strokes.
 *
 * Points are stored as raw canvas [Offset]s so undo/redo can replay them without geometry
 * loss. Rasterization to the 1500×500 export bitmap projects through the canvas→export
 * transform (see [com.npic.photoandsignscanner.features.signaturedraw.SignatureRasterizer]).
 */
@Immutable
data class DrawStroke(
    val points: List<Offset>,
    val widthPx: Float,
)
