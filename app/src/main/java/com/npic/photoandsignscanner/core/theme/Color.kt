package com.npic.photoandsignscanner.core.theme

import androidx.compose.ui.graphics.Color

/**
 * Semantic color tokens. Every color used anywhere in the app must resolve to one of these.
 *
 * Enforcement rule DESIGN.md §11.1: no hex literals in feature code. If a shade you need
 * isn't here, add it here first with a name — never inline it.
 *
 * Palette source of truth: DESIGN.md §5 "Warm Editorial".
 */
object NpicColors {

    // ────────────────────────────────────────────────────────────────────────────
    // Brand — saffron family
    // ────────────────────────────────────────────────────────────────────────────

    /** Primary brand accent. Buttons, active chips, crop handles, chart accents. */
    val Saffron       = Color(0xFFF4A300)

    /** Pressed / hover-analog / secondary saffron for depth. */
    val SaffronDeep   = Color(0xFFD98A00)

    /** Soft saffron tint. Selection pills, gentle backgrounds behind active tabs. */
    val SaffronSoft   = Color(0xFFFCE9C2)

    // ────────────────────────────────────────────────────────────────────────────
    // Ink — text and iconography on light surfaces
    // ────────────────────────────────────────────────────────────────────────────

    /** Primary text on light surfaces. WCAG AAA on Saffron (8.6:1) and Ivory (16.8:1). */
    val Ink           = Color(0xFF1A1613)

    /** Secondary text: captions, metadata, disabled-looking labels that are still legible. */
    val InkMuted      = Color(0xFF5B534C)

    /** Tertiary text: placeholder hints, timestamps, low-hierarchy metadata. */
    val InkFaint      = Color(0xFF8A8079)

    // ────────────────────────────────────────────────────────────────────────────
    // Surfaces — the warm editorial canvas
    // ────────────────────────────────────────────────────────────────────────────

    /** Screen background. Everything sits on Ivory unless a card or sheet lifts it. */
    val Ivory         = Color(0xFFFAF7F2)

    /** Cards and content islands on top of Ivory. Slightly cooler for separation. */
    val Surface       = Color(0xFFFFFFFF)

    /** Raised surface for sheets, dialogs, and elevated overlays. Slightly warmer. */
    val SurfaceRaised = Color(0xFFFFFDF8)

    /** 60% black overlay for scrims (camera guide-box outside, dialog backdrop). */
    val Overlay       = Color(0x99000000)

    // ────────────────────────────────────────────────────────────────────────────
    // Borders — hairlines that never fight the type
    // ────────────────────────────────────────────────────────────────────────────

    /** 1dp hairlines between rows, list dividers, chip outlines. */
    val BorderSoft    = Color(0xFFEDE6DA)

    /** Assertive borders: unselected chips, empty-state placeholders, focus rings. */
    val BorderStrong  = Color(0xFFD8CDB8)

    // ────────────────────────────────────────────────────────────────────────────
    // Status — semantic accents (used sparingly)
    // ────────────────────────────────────────────────────────────────────────────

    /** Destructive / error. Delete, duplicate warning, size overflow. */
    val Terracotta     = Color(0xFFC1440E)

    /** Soft terracotta tint for error banner backgrounds. */
    val TerracottaSoft = Color(0xFFF9E4DA)

    /** Success. Save confirmed, export succeeded. */
    val Sage           = Color(0xFF3B7A57)

    /** Soft sage tint for success banner backgrounds. */
    val SageSoft       = Color(0xFFE2EFE6)

    /** Informational accent. Rare — reserved for tips and info banners. */
    val Indigo         = Color(0xFF2B2D6B)

    // ────────────────────────────────────────────────────────────────────────────
    // Camera-mode dark chrome (near-pure black for maximum preview contrast)
    // Used by Camera, Edit, Signature Camera, and Signature Draw chrome (§7 DESIGN).
    // NEVER used on Gallery / Detail / Save / Duplicate / Export — those stay Ivory.
    // ────────────────────────────────────────────────────────────────────────────

    /** Full-bleed dark background for the capture flow. */
    val CameraBg       = Color(0xFF050506)

    /** Tool tabs row, signature prompt sheet, dialogs within capture flow.
     *  NOTE: Tool CONTENT (filter strip, adjust sliders, rotate buttons) sits directly
     *  on CameraBg — never on CameraSurface. See DESIGN §11.9. */
    val CameraSurface  = Color(0xFF141416)

    /** Primary ink on dark chrome. */
    val CameraInk      = Color(0xFFF5F5F7)

    /** Secondary ink on dark chrome: unselected mode pill, muted labels. */
    val CameraInkMuted = Color(0xFFA0A0A5)
}
