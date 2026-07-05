package com.npic.photoandsignscanner.core.theme

import android.content.Context
import android.provider.Settings
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.npic.photoandsignscanner.domain.model.AppSettings
import com.npic.photoandsignscanner.domain.model.MotionPreference
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

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
 * Live [AppSettings] from the Settings drawer (user m1551 S3). Callers that need to read
 * `hapticsEnabled` or `exportMimePreference` at usage sites (e.g. the shutter button, the
 * share intent builder) pull from here so a single toggle in the drawer takes effect
 * everywhere without threading callbacks through every composable.
 *
 * MUST be [compositionLocalOf], NOT [staticCompositionLocalOf]. The static variant tells
 * Compose to skip tracking readers for recomposition — used for values that never change
 * during the app's lifetime (like layout direction). AppSettings DOES change at runtime
 * (Settings drawer toggles) so consumers must recompose when it flips. Making it static
 * broke the haptics toggle silently for months — every call site cached the initial
 * value and never picked up the flip.
 *
 * Default is [AppSettings.Default] so previews and any composable rendered outside
 * [NpicTheme] still resolve; the reduce-motion override is applied inside NpicTheme
 * itself so [LocalReduceMotion] carries the resolved boolean directly.
 */
val LocalAppSettings = compositionLocalOf { AppSettings.Default }

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
    settingsFlow: Flow<AppSettings> = flowOf(AppSettings.Default),
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val systemReduce = remember(context) { context.resolveReduceMotion() }
    val settings by settingsFlow.collectAsStateWithLifecycle(initialValue = AppSettings.Default)
    // Motion override layers on the system reading (user m1551 S3): explicit `On`/`Off`
    // wins over Settings.Global; `System` defers to the platform. This means a user who
    // wants full motion on a device with system reduce-motion enabled can force it, and
    // vice versa — animations answer to the last-touched preference.
    val effectiveReduceMotion = when (settings.reduceMotionOverride) {
        MotionPreference.System -> systemReduce
        MotionPreference.On     -> true
        MotionPreference.Off    -> false
    }
    MaterialTheme(
        colorScheme = WarmEditorialColorScheme,
        typography  = NpicTypography,
        shapes      = NpicShapes.material,
    ) {
        CompositionLocalProvider(
            LocalNpicChrome    provides WarmEditorialChrome,
            LocalTextStyle     provides NpicTypography.bodyMedium,
            LocalReduceMotion  provides effectiveReduceMotion,
            LocalAppSettings   provides settings,
            content = content,
        )
    }
}

/**
 * True when animations should be suppressed. Two signals per Android platform docs:
 * 1. Pre-API 33 proxy: `Settings.Global.TRANSITION_ANIMATION_SCALE == 0f` (developer-options
 *    or a11y-tool driven).
 * 2. API 33+: `AccessibilityManager` exposes reduce-motion via developer-facing intent that
 *    the same global setting drives. Reading the global setting covers both paths and needs
 *    no permission.
 *
 * `AccessibilityManager.isEnabled` alone is NOT reduce-motion — it's true whenever any a11y
 * service (TalkBack, Switch Access, magnification, etc.) is on. We deliberately DO NOT read
 * that here.
 */
private fun Context.resolveReduceMotion(): Boolean = try {
    val transition = Settings.Global.getFloat(
        contentResolver, Settings.Global.TRANSITION_ANIMATION_SCALE, 1f,
    )
    val animator = Settings.Global.getFloat(
        contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f,
    )
    transition == 0f || animator == 0f
} catch (_: Throwable) {
    false
}
