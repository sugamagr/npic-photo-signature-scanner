package com.npic.photoandsignscanner.features.camera

import androidx.compose.runtime.Immutable
import com.npic.photoandsignscanner.domain.model.CameraMode
import com.npic.photoandsignscanner.domain.model.FlashMode

/**
 * View state for [CameraScreen]. Immutable — all mutations go through [CameraViewModel].
 *
 * The three visual regions of the Camera screen (top bar chrome, mode pills / hint, bottom
 * control row) read directly from this object; there is no derived per-region state.
 */
@Immutable
data class CameraUiState(
    val mode: CameraMode = CameraMode.Photo,
    val flash: FlashMode = FlashMode.Auto,
    /** Number of successful captures in the current Camera session (resets on back-out). */
    val sessionCount: Int = 0,
    /** True while the shutter has been tapped but the still hasn't been persisted yet. */
    val capturing: Boolean = false,
    /** First-run hint text under the guide box; user can dismiss with a tap. */
    val hintVisible: Boolean = true,
    /** Terminal error from a failed capture — cleared on next successful shutter or mode swap. */
    val lastError: String? = null,
)
