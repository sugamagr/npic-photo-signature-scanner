package com.npic.photoandsignscanner.domain.model

/**
 * Camera flash / torch state as exposed on the Camera top bar. Cycles Auto → On → Off → Auto
 * on tap (PRD §4.2).
 *
 * The mapping to CameraX's `ImageCapture.FLASH_MODE_*` and `LifecycleCameraController.
 * enableTorch()` lives in the Camera feature; the domain layer only carries the intent.
 */
enum class FlashMode(val label: String) {
    Auto("Auto"),
    On("On"),
    Off("Off");

    fun cycle(): FlashMode = when (this) {
        Auto -> On
        On   -> Off
        Off  -> Auto
    }
}
