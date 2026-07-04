package com.npic.photoandsignscanner.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen

/**
 * Single-activity entry point.
 *
 * Bootstrap layer: renders a placeholder. Navigation and the Gallery home screen are wired in
 * the Gallery layer once the theme + core/ui components are in place.
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        splash.setKeepOnScreenCondition { false }

        setContent {
            BootstrapPlaceholder()
        }
    }
}

@Composable
private fun BootstrapPlaceholder() {
    MaterialTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color(0xFFFAF7F2) // NpicColors.Ivory — theme lands in the next layer
        ) {
            Box(
                modifier = Modifier.fillMaxSize().background(Color(0xFFFAF7F2)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "NPIC Photo & Signature Scanner\nBootstrap OK",
                    color = Color(0xFF1A1613)
                )
            }
        }
    }
}
