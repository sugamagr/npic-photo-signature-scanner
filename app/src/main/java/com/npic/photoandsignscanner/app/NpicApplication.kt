package com.npic.photoandsignscanner.app

import android.app.Application

/**
 * Application entry point.
 *
 * Kept minimal by design. Anything that would normally live here (DI graph, logging config,
 * OpenCV native init, WorkManager configuration) is added in the layer that actually needs it
 * — not preemptively in bootstrap.
 */
class NpicApplication : Application()
