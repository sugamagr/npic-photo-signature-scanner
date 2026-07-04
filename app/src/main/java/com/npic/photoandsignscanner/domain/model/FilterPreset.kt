package com.npic.photoandsignscanner.domain.model

/**
 * The eight filter presets shipped in v1.0. Order matches PRD §5's numbered table so the
 * filter strip renders left-to-right in the same order the PRD reads.
 *
 * The presets carry only a display label here. The actual pixel transform lives in the
 * Edit-layer OpenCV pipeline (PRD §7); this enum is what the UI + non-destructive edit state
 * store — a single [FilterPreset] value per record. Everything else about "what does this
 * preset look like" is deterministic from the preset name.
 *
 * `Auto` is the sole context-aware entry — PRD §5 routes it to `SchoolId` for photo captures
 * and `InkBoost` for signature captures. That routing lives in Edit, not here, because the
 * routing depends on the capture mode which the domain model deliberately does not know.
 */
enum class FilterPreset(val label: String) {
    Auto("Auto"),
    Original("Original"),
    ColorBoost("Color Boost"),
    DocumentBw("Document B&W"),
    Passport("Passport"),
    SchoolId("School ID"),
    FadedRescue("Faded Rescue"),
    InkBoost("Ink Boost"),
}
