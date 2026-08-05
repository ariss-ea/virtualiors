package org.arissea.virtualiors

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import org.arissea.virtualiors.camera.CameraCaptureController
import org.arissea.virtualiors.ui.VirtualIorsApp

class MainActivity : ComponentActivity() {
    private val cameraController = CameraCaptureController()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            VirtualIorsApp(window = window, cameraController = cameraController)
        }
    }
}
