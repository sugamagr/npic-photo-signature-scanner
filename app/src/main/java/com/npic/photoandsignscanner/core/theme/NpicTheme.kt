package com.npic.photoandsignscanner.core.theme

import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Root theme composable. Every screen must be wrapped in `NpicTheme { … }`.
 *
 * What lives inside Material's own APIs (colorScheme / typography / shapes):
 * - The Warm Editorial palette mapped onto Material 3 slots so that any Material component
 *   (Button, TextField, Card) pulled in "for free" still speaks the app's language.
 * - `NpicTypography` — the full type scale.
 * - `NpicShapes.material` — the six-radius scale mapped onto Material's five slots.
 *
 * What lives outside Material's APIs (via CompositionLocal):
 * - Camera-chrome colors (dark canvas + surface + inks) — Material's colorScheme is a light
 *   palette; we don't want to swap the whole scheme mid-screen inside the capture flow.
 * - `NpicSpacing` — Material has no spacing scale.
 * - `NpicMotion` and `NpicElevation` — provided as objects because they're static, but
 *   referenced explicitly by call sites so they show up in code review.
 */

/** Extra chrome tokens Material can't carry. Access via `LocalNpicChrome.current`. */
@Immutable
data class NpicChrome(
    val cameraBg:       Color,
    val cameraSurface:  Color,
    val cameraInk:      Color,
    val cameraInkMuted: Color,
    val overlay:        Color,
    val borderSoft:     Color,
    val borderStrong:   Color,
    val saffronSoft:    Color,
    val saffronDeep:    Color,
    val inkMuted:       Color,
    val inkFaint:       Color,
    val terracotta:     Color,
    val terracottaSoft: Color,
    val sage:           Color,
    val sageSoft:       Color,
    val indigo:         Color,
)

private val WarmEditorialChrome = NpicChrome(
    cameraBg       = NpicColors.CameraBg,
    cameraSurface  = NpicColors.CameraSurface,
    cameraInk      = NpicColors.CameraInk,
    cameraInkMuted = NpicColors.CameraInkMuted,
    overlay        = NpicColors.Overlay,
    borderSoft     = NpicColors.BorderSoft,
    borderStrong   = NpicColors.BorderStrong,
    saffronSoft    = NpicColors.SaffronSoft,
    saffronDeep    = NpicColors.SaffronDeep,
    inkMuted       = NpicColors.InkMuted,
    inkFaint       = NpicColors.InkFaint,
    terracotta     = NpicColors.Terracotta,
    terracottaSoft = NpicColors.TerracottaSoft,
    sage           = NpicColors.Sage,
    sageSoft       = NpicColors.SageSoft,
    indigo         = NpicColors.Indigo,
)

val LocalNpicChrome = staticCompositionLocalOf { WarmEditorialChrome }

/**
 * Material 3 slot mapping. Kept deliberate — every slot documented so a reader can trace
 * which token drives which visual role. If a Material component reads wrong, fix here,
 * not in the component.
 */
private val WarmEditorialColorScheme = lightColorScheme(
    // Primary = brand accent (Saffron)
    primary            = NpicColors.Saffron,
    onPrimary          = NpicColors.Ink,
    primaryContainer   = NpicColors.SaffronSoft,
    onPrimaryContainer = NpicColors.Ink,

    // Secondary = deep saffron for pressed / secondary emphasis
    secondary            = NpicColors.SaffronDeep,
    onSecondary          = NpicColors.Ivory,
    secondaryContainer   = NpicColors.SaffronSoft,
    onSecondaryContainer = NpicColors.Ink,

    // Tertiary = Indigo, used sparingly for information
    tertiary             = NpicColors.Indigo,
    onTertiary           = NpicColors.Ivory,
    tertiaryContainer    = NpicColors.Ivory,
    onTertiaryContainer  = NpicColors.Ink,

    // Errors — Terracotta family
    error                = NpicColors.Terracotta,
    onError              = NpicColors.Ivory,
    errorContainer       = NpicColors.TerracottaSoft,
    onErrorContainer     = NpicColors.Ink,

    // Backgrounds and surfaces
    background            = NpicColors.Ivory,
    onBackground          = NpicColors.Ink,
    surface               = NpicColors.Surface,
    onSurface             = NpicColors.Ink,
    surfaceVariant        = NpicColors.SurfaceRaised,
    onSurfaceVariant      = NpicColors.InkMuted,
    surfaceContainerLowest  = NpicColors.Ivory,
    surfaceContainerLow     = NpicColors.Ivory,
    surfaceContainer        = NpicColors.Surface,
    surfaceContainerHigh    = NpicColors.SurfaceRaised,
    surfaceContainerHighest = NpicColors.SurfaceRaised,

    // Outlines
    outline        = NpicColors.BorderStrong,
    outlineVariant = NpicColors.BorderSoft,

    // Inverse (used by snackbars / dark banners on light chrome)
    inverseSurface       = NpicColors.Ink,
    inverseOnSurface     = NpicColors.Ivory,
    inversePrimary       = NpicColors.SaffronSoft,

    // Scrim behind dialogs
    scrim = NpicColors.Overlay,
)

@Composable
fun NpicTheme(
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = WarmEditorialColorScheme,
        typography  = NpicTypography,
        shapes      = NpicShapes.material,
    ) {
        CompositionLocalProvider(
            LocalNpicChrome provides WarmEditorialChrome,
            LocalTextStyle  provides NpicTypography.bodyMedium,
            content = content,
        )
    }
}
