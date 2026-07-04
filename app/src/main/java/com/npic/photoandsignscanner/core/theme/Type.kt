package com.npic.photoandsignscanner.core.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.npic.photoandsignscanner.R

/**
 * Typography tokens.
 *
 * Two typefaces bundled locally (SIL Open Font License, redistributed with the app):
 * - **Fraunces** — variable serif. Used for display and title styles where character matters.
 * - **Inter** — variable sans-serif. Used for body and UI.
 *
 * Scale source of truth: DESIGN.md §7 "Type scale". Every text style in the app must
 * come from `NpicTheme.typography` — no ad-hoc TextStyle in feature code.
 *
 * We use a single variable font file per family and let the Android font renderer (API 26+)
 * pick the correct weight from the wght axis based on the FontWeight we declare here.
 * We intentionally avoid FontVariation APIs which are still @ExperimentalTextApi in the
 * current Compose BOM; the wght-axis selection works transparently through FontWeight.
 */

private fun frauncesFont(weight: Int): Font = Font(
    resId    = R.font.fraunces_variable,
    weight   = FontWeight(weight),
    style    = FontStyle.Normal,
)

private fun interFont(weight: Int): Font = Font(
    resId    = R.font.inter_variable,
    weight   = FontWeight(weight),
    style    = FontStyle.Normal,
)

private val FrauncesFamily = FontFamily(
    frauncesFont(weight = 500),
    frauncesFont(weight = 600),
    frauncesFont(weight = 700),
)

private val InterFamily = FontFamily(
    interFont(weight = 400),
    interFont(weight = 500),
    interFont(weight = 600),
    interFont(weight = 700),
)

/**
 * Public font families for cases where a component absolutely needs to reach for a family
 * (e.g. NpicToast picks Inter regardless of `LocalTextStyle`). Prefer typography styles
 * over families when possible.
 */
object NpicFonts {
    val fraunces: FontFamily = FrauncesFamily
    val inter:    FontFamily = InterFamily
}

/**
 * Type scale, hand-tuned for the Warm Editorial voice. Fraunces owns headlines and titles;
 * Inter owns body and UI. Line heights are set explicitly (not left to Material defaults)
 * to keep vertical rhythm on a 4dp grid.
 */
val NpicTypography = Typography(

    // Fraunces — display / headline (weight 500 per DESIGN §3)
    displayLarge = TextStyle(
        fontFamily    = FrauncesFamily,
        fontWeight    = FontWeight(500),
        fontSize      = 40.sp,
        lineHeight    = 44.sp,
        letterSpacing = (-0.5).sp,
    ),
    displayMedium = TextStyle(
        fontFamily    = FrauncesFamily,
        fontWeight    = FontWeight(500),
        fontSize      = 32.sp,
        lineHeight    = 36.sp,
        letterSpacing = (-0.3).sp,
    ),
    headlineLarge = TextStyle(
        fontFamily    = FrauncesFamily,
        fontWeight    = FontWeight(500),
        fontSize      = 26.sp,
        lineHeight    = 32.sp,
    ),
    headlineMedium = TextStyle(
        fontFamily    = FrauncesFamily,
        fontWeight    = FontWeight(500),
        fontSize      = 22.sp,
        lineHeight    = 28.sp,
    ),

    // Fraunces — titleLarge (weight 600 per DESIGN §3)
    titleLarge = TextStyle(
        fontFamily    = FrauncesFamily,
        fontWeight    = FontWeight(600),
        fontSize      = 20.sp,
        lineHeight    = 26.sp,
    ),

    // Inter — titles (weight 600)
    titleMedium = TextStyle(
        fontFamily    = InterFamily,
        fontWeight    = FontWeight(600),
        fontSize      = 16.sp,
        lineHeight    = 22.sp,
    ),
    titleSmall = TextStyle(
        fontFamily    = InterFamily,
        fontWeight    = FontWeight(600),
        fontSize      = 14.sp,
        lineHeight    = 20.sp,
    ),

    // Inter — body (weight 400)
    bodyLarge = TextStyle(
        fontFamily = InterFamily,
        fontWeight = FontWeight(400),
        fontSize   = 16.sp,
        lineHeight = 24.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = InterFamily,
        fontWeight = FontWeight(400),
        fontSize   = 14.sp,
        lineHeight = 20.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = InterFamily,
        fontWeight = FontWeight(400),
        fontSize   = 12.sp,
        lineHeight = 16.sp,
    ),

    // Inter — labels (600 for large, 500 for medium/small per DESIGN §3)
    labelLarge = TextStyle(
        fontFamily    = InterFamily,
        fontWeight    = FontWeight(600),
        fontSize      = 14.sp,
        lineHeight    = 20.sp,
        letterSpacing = 0.1.sp,
    ),
    labelMedium = TextStyle(
        fontFamily    = InterFamily,
        fontWeight    = FontWeight(500),
        fontSize      = 12.sp,
        lineHeight    = 16.sp,
        letterSpacing = 0.3.sp,
    ),
    labelSmall = TextStyle(
        fontFamily    = InterFamily,
        fontWeight    = FontWeight(500),
        fontSize      = 11.sp,
        lineHeight    = 14.sp,
        letterSpacing = 0.4.sp,
    ),
)
