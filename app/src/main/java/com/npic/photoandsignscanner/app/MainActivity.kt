package com.npic.photoandsignscanner.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.npic.photoandsignscanner.core.theme.NpicSpacing
import com.npic.photoandsignscanner.core.theme.NpicTheme

/**
 * Single-activity entry point.
 *
 * Theme layer: hosts the design-system probe. Navigation and the Gallery home screen are
 * wired in the Gallery layer once the core/ui components are in place.
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        splash.setKeepOnScreenCondition { false }

        setContent {
            NpicTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color    = MaterialTheme.colorScheme.background,
                ) {
                    ThemeProbe()
                }
            }
        }
    }
}

/**
 * A minimal probe that renders text at three levels of the type scale on the app background.
 *
 * Its job is to prove — at runtime and in Compose previews — that:
 * 1. `NpicTheme` composes without crashing.
 * 2. Fraunces and Inter variable fonts are resolvable.
 * 3. `MaterialTheme.colorScheme` and `MaterialTheme.typography` resolve to the Warm
 *    Editorial palette and scale.
 *
 * Replaced by Gallery in the next layer.
 */
@Composable
private fun ThemeProbe() {
    Box(
        modifier = Modifier.fillMaxSize().padding(NpicSpacing.xl),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(NpicSpacing.md),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text  = "NPIC Scanner",
                style = MaterialTheme.typography.displayMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text  = "Foundation online",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text  = "Photo and signature scanner for admission forms",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Preview(name = "Theme probe", showBackground = true)
@Composable
private fun ThemeProbePreview() {
    NpicTheme { ThemeProbe() }
}
