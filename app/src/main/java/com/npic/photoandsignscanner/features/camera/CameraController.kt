package com.npic.photoandsignscanner.features.camera

import android.content.Context
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.npic.photoandsignscanner.domain.model.FlashMode
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Thin wrapper around [LifecycleCameraController]. Owns exactly one active controller for the
 * screen's lifetime and exposes the three actions the UI needs: bind, apply flash, and take a
 * still.
 *
 * Kept as a plain class (not a Composable) so the ViewModel can inject / hold it and drive
 * capture without dragging the Compose runtime into unit-testable logic.
 */
class NpicCameraController(private val context: Context) {

    private val impl = LifecycleCameraController(context).apply {
        cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
        setEnabledUseCases(CameraController.IMAGE_CAPTURE)
        imageCaptureMode = ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY
    }

    /** Exposed for the PreviewView binding — do not mutate directly outside this class. */
    val controller: LifecycleCameraController get() = impl

    /** Bind the controller to the screen's lifecycle owner. Idempotent — CameraX handles reuse. */
    fun bindTo(owner: LifecycleOwner) {
        impl.bindToLifecycle(owner)
    }

    /** Apply the user-selected flash mode to the ImageCapture use case. */
    fun applyFlash(mode: FlashMode) {
        impl.imageCaptureFlashMode = when (mode) {
            FlashMode.Auto -> ImageCapture.FLASH_MODE_AUTO
            FlashMode.On   -> ImageCapture.FLASH_MODE_ON
            FlashMode.Off  -> ImageCapture.FLASH_MODE_OFF
        }
    }

    /**
     * Capture a still JPEG to [target] and suspend until CameraX reports success or failure.
     * The returned file is guaranteed to exist on success.
     */
    suspend fun takePicture(target: File): File = suspendCancellableCoroutine { cont ->
        val options = ImageCapture.OutputFileOptions.Builder(target).build()
        impl.takePicture(
            options,
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    if (cont.isActive) cont.resume(target)
                }

                override fun onError(exc: ImageCaptureException) {
                    Log.e(TAG, "takePicture failed", exc)
                    if (cont.isActive) cont.resumeWithException(exc)
                }
            }
        )
    }

    private companion object {
        const val TAG = "NpicCameraController"
    }
}
