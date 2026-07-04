package com.npic.photoandsignscanner.app

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.npic.photoandsignscanner.core.theme.NpicTheme
import com.npic.photoandsignscanner.features.gallery.GalleryScreen
import com.npic.photoandsignscanner.features.gallery.GalleryViewModel

/**
 * Single-activity entry point.
 *
 * Renders the Gallery screen (which IS the Home per DESIGN §6). Navigation to Camera /
 * Edit / Signature / Save / Detail / Export slots in during their respective layers via
 * NavHost — for the shell we log intents to a Toast so the interaction model is testable
 * end-to-end before the destinations exist.
 */
class MainActivity : ComponentActivity() {

    private val galleryViewModel: GalleryViewModel by viewModels()

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
                    val context = LocalContext.current
                    GalleryScreen(
                        viewModel = galleryViewModel,
                        onCaptureClick = {
                            Toast.makeText(context, "Capture → Camera (next layer)", Toast.LENGTH_SHORT).show()
                        },
                        onRecordClick = { id ->
                            Toast.makeText(context, "Open record #$id → Detail (next layer)", Toast.LENGTH_SHORT).show()
                        },
                        onExportSelection = { ids ->
                            Toast.makeText(context, "Export ${ids.size} record(s) → Share sheet (next layer)", Toast.LENGTH_SHORT).show()
                        },
                        onDeleteSelection = { ids ->
                            Toast.makeText(context, "Delete ${ids.size} record(s) → Confirm dialog (next layer)", Toast.LENGTH_SHORT).show()
                        },
                        onOverflowClick = {
                            Toast.makeText(context, "Overflow menu (next layer)", Toast.LENGTH_SHORT).show()
                        },
                    )
                }
            }
        }
    }
}
