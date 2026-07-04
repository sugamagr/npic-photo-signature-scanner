package com.npic.photoandsignscanner.domain.model

import androidx.compose.runtime.Immutable

/**
 * Crop aspect constraint the user picks from the Crop tool chip row. `Free` = no lock,
 * quad edges move independently. `Ratio` locks to a `width : height` ratio (Photo 3:4,
 * Signature 3:1). The Camera mode seeds the initial [Ratio]; the user can flip to [Free]
 * to hand-drag any corner without proportional pinning.
 *
 * Modeled as a sealed interface rather than an enum so `Ratio(w, h)` can carry the exact
 * ratio it constrains to, and future presets (16:9 for badges, 1:1 for stamps) add without
 * touching call sites.
 */
@Immutable
sealed interface AspectLock {
    /** No constraint — any corner drag moves that corner alone. */
    data object Free : AspectLock

    /** Locked to `width / height`. Photo default = 3:4; signature default = 3:1. */
    data class Ratio(val width: Int, val height: Int) : AspectLock {
        val aspect: Float get() = width.toFloat() / height.toFloat()

        override fun toString(): String = "$width:$height"
    }

    companion object {
        val PhotoDefault: Ratio     = Ratio(3, 4)
        val SignatureDefault: Ratio = Ratio(3, 1)

        /** Chip-row order per DESIGN §7.3 Crop tool: `Free · 3:4 · 3:1`. */
        val ChipRowOptions: List<AspectLock> = listOf(Free, PhotoDefault, SignatureDefault)
    }
}

/**
 * The mode-appropriate default when Edit opens. Photo mode seeds 3:4; Signature mode seeds
 * 3:1. Lives here (not on [CameraMode]) so the domain model keeps aspect logic in one place.
 */
fun CameraMode.defaultAspectLock(): AspectLock = when (this) {
    CameraMode.Photo     -> AspectLock.PhotoDefault
    CameraMode.Signature -> AspectLock.SignatureDefault
}
