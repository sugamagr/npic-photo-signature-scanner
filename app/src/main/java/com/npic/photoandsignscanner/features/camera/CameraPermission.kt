package com.npic.photoandsignscanner.features.camera

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

/**
 * Runtime permission state for `android.permission.CAMERA` — the single permission the app
 * ever asks for (storage is scoped, gallery import uses PhotoPicker).
 *
 * Modeled as a sealed hierarchy so the screen can render three visually distinct branches
 * (grant path, rationale, blocked) instead of collapsing everything into a nullable boolean.
 */
sealed interface CameraPermissionState {
    /** Not yet asked. Ask on first frame via [request]. */
    data object NotAsked : CameraPermissionState
    /** Granted — the preview can be bound. */
    data object Granted : CameraPermissionState
    /** User denied but has NOT selected "never ask again"; rationale + retry are valid. */
    data object DeniedTransient : CameraPermissionState
    /** User denied permanently (checked "never ask again", or policy blocked). Only
     *  Settings can recover — the UI should link there. */
    data object DeniedPermanent : CameraPermissionState
}

/**
 * Small handle a screen uses to trigger and observe the CAMERA permission flow. [state]
 * updates synchronously after the system callback resolves.
 */
class CameraPermissionHandle internal constructor(
    private val getState: () -> CameraPermissionState,
    private val requestFn: () -> Unit,
) {
    val state: CameraPermissionState get() = getState()
    fun request() = requestFn()
}

/**
 * Compose hook that wires up an [ActivityResultContracts.RequestPermission] launcher and
 * folds the raw `Boolean` callback into [CameraPermissionState].
 *
 * The rationale bit ("should show rationale") only flips to true AFTER the first denial, so
 * `NotAsked` and `DeniedTransient` are separate branches driven by whether we've heard back
 * yet at least once in this composition scope.
 */
@Composable
fun rememberCameraPermissionState(context: Context): CameraPermissionHandle {
    var state by remember {
        mutableStateOf(
            if (hasCameraPermission(context)) CameraPermissionState.Granted
            else CameraPermissionState.NotAsked
        )
    }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        state = if (granted) {
            CameraPermissionState.Granted
        } else {
            val activity = context as? Activity
            val transient = activity?.let {
                ActivityCompat.shouldShowRequestPermissionRationale(it, Manifest.permission.CAMERA)
            } ?: false
            if (transient) CameraPermissionState.DeniedTransient
            else CameraPermissionState.DeniedPermanent
        }
    }

    return remember(launcher) {
        CameraPermissionHandle(
            getState = { state },
            requestFn = { launcher.launch(Manifest.permission.CAMERA) },
        )
    }
}

private fun hasCameraPermission(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
        PackageManager.PERMISSION_GRANTED
