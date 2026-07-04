package com.npic.photoandsignscanner.core.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
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

    // ── Display / Headline (Fraunces) ────────────────────────────────────────
    displayLarge = TextStyle(
        fontFamily    = FrauncesFamily,
        fontWeight    = FontWeight(600),
        fontSize      = 40.sp,
        lineHeight    = 48.sp,
        letterSpacing = (-0.01).em,
    ),
    displayMedium = TextStyle(
        fontFamily    = FrauncesFamily,
        fontWeight    = FontWeight(600),
        fontSize      = 32.sp,
        lineHeight    = 40.sp,
        letterSpacing = (-0.01).em,
    ),
    headlineLarge = TextStyle(
        fontFamily    = FrauncesFamily,
        fontWeight    = FontWeight(600),
        fontSize      = 26.sp,
        lineHeight    = 32.sp,
        letterSpacing = 0.em,
    ),
    headlineMedium = TextStyle(
        fontFamily    = FrauncesFamily,
        fontWeight    = FontWeight(600),
        fontSize      = 22.sp,
        lineHeight    = 28.sp,
        letterSpacing = 0.em,
    ),
    headlineSmall = TextStyle(
        fontFamily    = FrauncesFamily,
        fontWeight    = FontWeight(600),
        fontSize      = 20.sp,
        lineHeight    = 28.sp,
        letterSpacing = 0.em,
    ),

    // ── Title (Fraunces for large, Inter for medium/small) ───────────────────
    titleLarge = TextStyle(
        fontFamily    = FrauncesFamily,
        fontWeight    = FontWeight(700),
        fontSize      = 20.sp,
        lineHeight    = 24.sp,
        letterSpacing = 0.em,
    ),
    titleMedium = TextStyle(
        fontFamily    = InterFamily,
        fontWeight    = FontWeight(600),
        fontSize      = 16.sp,
        lineHeight    = 24.sp,
        letterSpacing = 0.005.em,
    ),
    titleSmall = TextStyle(
        fontFamily    = InterFamily,
        fontWeight    = FontWeight(600),
        fontSize      = 14.sp,
        lineHeight    = 20.sp,
        letterSpacing = 0.01.em,
    ),

    // ── Body (Inter) ─────────────────────────────────────────────────────────
    bodyLarge = TextStyle(
        fontFamily    = InterFamily,
        fontWeight    = FontWeight(400),
        fontSize      = 16.sp,
        lineHeight    = 24.sp,
        letterSpacing = 0.02.em,
    ),
    bodyMedium = TextStyle(
        fontFamily    = InterFamily,
        fontWeight    = FontWeight(400),
        fontSize      = 14.sp,
        lineHeight    = 20.sp,
        letterSpacing = 0.02.em,
    ),
    bodySmall = TextStyle(
        fontFamily    = InterFamily,
        fontWeight    = FontWeight(400),
        fontSize      = 12.sp,
        lineHeight    = 16.sp,
        letterSpacing = 0.03.em,
    ),

    // ── Label (Inter, tighter and heavier for UI) ────────────────────────────
    labelLarge = TextStyle(
        fontFamily    = InterFamily,
        fontWeight    = FontWeight(600),
        fontSize      = 14.sp,
        lineHeight    = 20.sp,
        letterSpacing = 0.02.em,
    ),
    labelMedium = TextStyle(
        fontFamily    = InterFamily,
        fontWeight    = FontWeight(600),
        fontSize      = 12.sp,
        lineHeight    = 16.sp,
        letterSpacing = 0.04.em,
    ),
    labelSmall = TextStyle(
        fontFamily    = InterFamily,
        fontWeight    = FontWeight(600),
        fontSize      = 11.sp,
        lineHeight    = 14.sp,
        letterSpacing = 0.05.em,
    ),
)
