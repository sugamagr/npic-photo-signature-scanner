package com.npic.photoandsignscanner.features.camera

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import kotlin.math.atan2
import kotlin.math.sqrt

/**
 * Streams the device roll angle (degrees, portrait-relative) as Compose state, subscribing
 * to [Sensor.TYPE_ACCELEROMETER] on the enclosing lifecycle. Returns `null` when the device
 * has no accelerometer OR the sensor hasn't emitted a stable reading yet.
 *
 * Roll math per PRD §4.2 / DESIGN §6.16: roll = atan2(x, sqrt(y²+z²)) in radians. The
 * accelerometer's x-axis lies horizontal in portrait; when the device is level, x ≈ 0. A
 * low-pass filter (α=0.15) smooths out shutter tremor while staying responsive to a
 * deliberate tilt — anything faster produces jitter, anything slower feels laggy under a
 * real-world hand hold.
 */
@Composable
fun rememberDeviceTiltDegrees(): State<Float?> {
    val context = LocalContext.current
    val tilt = remember { mutableStateOf<Float?>(null) }

    DisposableEffect(context) {
        val manager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val sensor  = manager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        if (manager == null || sensor == null) {
            return@DisposableEffect onDispose { /* no-op */ }
        }
        var smoothed = 0f
        var primed = false
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent?) {
                if (event == null || event.values.size < 3) return
                val x = event.values[0]
                val y = event.values[1]
                val z = event.values[2]
                val raw = Math.toDegrees(
                    atan2(x.toDouble(), sqrt((y * y + z * z).toDouble()))
                ).toFloat()
                // Portrait convention: right-tilt should produce POSITIVE degrees so the
                // NpicCameraOverlay level indicator rotates the same direction as the
                // physical device. atan2(x, √(y²+z²)) gives us that sign directly.
                smoothed = if (!primed) { primed = true; raw }
                           else 0.15f * raw + 0.85f * smoothed
                tilt.value = smoothed
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        manager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI)
        onDispose { manager.unregisterListener(listener) }
    }
    return tilt
}
