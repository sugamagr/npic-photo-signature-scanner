package com.npic.photoandsignscanner.domain.model

/**
 * Which framing the camera is currently laying over the preview. The user toggles between
 * these via the text pills above the shutter (DESIGN §7.2).
 *
 * The two values differ ONLY in guide-box geometry:
 *   • [Photo] captures a 3:4 portrait rectangle at 70% preview width.
 *   • [Signature] captures a 3:1 landscape rectangle at 85% preview width.
 *
 * Everything else about the Camera screen (shutter behavior, permission gate, top-bar
 * chrome) is identical across modes — this is the "unified Camera with mode pills"
 * shape locked in PRD §4.2. Auto edge / ink detection was removed per m2154; Edit opens
 * at the guide-box quad (when captured) or full-image bounds (when imported).
 */
enum class CameraMode(
    val label: String,
    /** Guide-box aspect ratio as width / height. */
    val guideAspect: Float,
    /** Guide-box short-side fill fraction of the preview area. */
    val guideFillFraction: Float,
) {
    Photo(     label = "Photo",     guideAspect = 3f / 4f, guideFillFraction = 0.70f),
    Signature(label = "Signature", guideAspect = 3f / 1f, guideFillFraction = 0.85f),
}
