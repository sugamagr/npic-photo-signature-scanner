package com.npic.photoandsignscanner.app

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.npic.photoandsignscanner.core.theme.NpicTheme
import com.npic.photoandsignscanner.features.camera.CameraScreen
import com.npic.photoandsignscanner.features.gallery.GalleryScreen
import com.npic.photoandsignscanner.features.gallery.GalleryViewModel

/**
 * Single-activity entry point.
 *
 * The whole app is one Activity with a Compose NavHost. Two destinations live today:
 * [Route.Gallery] (which IS the Home per DESIGN §6) and [Route.Camera]. Edit, Signature,
 * Save, Detail, and Export slot in as new [composable] entries during their respective
 * layers — each one owns its own ViewModel via `viewModel()` inside the `composable { }`
 * block so back-stack scoping falls out for free.
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
                    NpicNavHost(rememberNavController())
                }
            }
        }
    }
}

private object Route {
    const val Gallery = "gallery"
    const val Camera  = "camera"
}

@Composable
private fun NpicNavHost(navController: NavHostController) {
    NavHost(navController = navController, startDestination = Route.Gallery) {
        composable(Route.Gallery) {
            GalleryDestination(
                onCaptureClick = { navController.navigate(Route.Camera) },
            )
        }
        composable(Route.Camera) {
            CameraDestination(onBack = { navController.popBackStack() })
        }
    }
}

@Composable
private fun GalleryDestination(onCaptureClick: () -> Unit) {
    val context = LocalContext.current
    val viewModel: GalleryViewModel = viewModel()
    GalleryScreen(
        viewModel = viewModel,
        onCaptureClick = onCaptureClick,
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
        onSearchClick = {
            Toast.makeText(context, "Search (next layer)", Toast.LENGTH_SHORT).show()
        },
    )
}

@Composable
private fun CameraDestination(onBack: () -> Unit) {
    val context = LocalContext.current
    CameraScreen(
        onBack = onBack,
        onCaptureComplete = { capture ->
            Toast.makeText(
                context,
                "Captured ${capture.mode.label.lowercase()} → ${capture.rawPath} (Edit lands next layer)",
                Toast.LENGTH_LONG,
            ).show()
            onBack()
        },
        onDrawInsteadClick = {
            Toast.makeText(context, "Draw signature (next layer)", Toast.LENGTH_SHORT).show()
        },
        onImportFromGalleryClick = {
            Toast.makeText(context, "Import from Photos (next layer)", Toast.LENGTH_SHORT).show()
        },
    )
}
