package com.npic.photoandsignscanner.features.edit

import androidx.lifecycle.ViewModel
import com.npic.photoandsignscanner.domain.model.CameraCapture
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Activity-scoped hand-off for the [CameraCapture] object between the Camera and Edit
 * destinations. Both destinations obtain the SAME instance via `viewModel(activityOwner)`
 * so navigation can stay stringly-typed without shoving the whole capture into a route
 * argument.
 *
 * Not a persistent store — process death wipes it. That's fine because the actual raw JPEG
 * lives on disk at `capture.rawPath`, so if the app returns from a cold start we can either
 * scan the drafts folder (deferred; PRD §8.3 draft persistence) or drop back to Gallery.
 *
 * TODO(repo): swap for a `DraftRepository`-backed source when the Save+Room layer lands.
 */
class SharedCaptureHolder : ViewModel() {

    private val _current = MutableStateFlow<CameraCapture?>(null)
    val current: StateFlow<CameraCapture?> = _current.asStateFlow()

    fun push(capture: CameraCapture) {
        _current.value = capture
    }

    fun clear() {
        _current.value = null
    }
}
