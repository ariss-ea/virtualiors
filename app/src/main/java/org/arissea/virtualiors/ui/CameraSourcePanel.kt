package org.arissea.virtualiors.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.delay
import org.arissea.virtualiors.R
import org.arissea.virtualiors.camera.CameraBindingStatus
import org.arissea.virtualiors.camera.CameraCaptureController
import org.arissea.virtualiors.model.CameraFacing
import org.arissea.virtualiors.model.SstvModeOption
import org.arissea.virtualiors.model.WatermarkConfig
import org.arissea.virtualiors.sstv.WatermarkContent
import java.time.Instant

@Composable
fun CameraSourcePanel(
    facing: CameraFacing,
    mode: SstvModeOption,
    watermark: WatermarkConfig,
    callsign: String,
    controller: CameraCaptureController,
    onFacingChange: (CameraFacing) -> Unit,
    onMessage: (String) -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var granted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED,
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { allowed ->
        granted = allowed
        if (!allowed) onMessage("Camera access is needed for automatic SSTV pictures.")
    }
    val previewView = remember {
        PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER }
    }
    val binding by controller.state.collectAsState()
    var timestamp by remember { mutableStateOf(Instant.now()) }

    LaunchedEffect(watermark.enabled, watermark.showTimestamp) {
        while (watermark.enabled && watermark.showTimestamp) {
            timestamp = Instant.now()
            delay(1_000)
        }
    }
    DisposableEffect(granted, facing, lifecycleOwner, previewView) {
        if (granted) controller.bind(context, lifecycleOwner, facing, previewView)
        onDispose {
            // CameraX remains bound while Camera is the selected source, including transmission.
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Camera", style = MaterialTheme.typography.titleSmall)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CameraFacing.entries.forEach { option ->
                FilterChip(
                    selected = facing == option,
                    onClick = { onFacingChange(option) },
                    label = { Text(if (option == CameraFacing.FRONT) "Front camera" else "Back camera") },
                    modifier = Modifier.weight(1f),
                )
            }
        }

        if (!granted) {
            Text("Camera is used to take an SSTV picture automatically.")
            Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.CameraAlt, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("Allow camera")
            }
        } else {
            Box(
                Modifier.fillMaxWidth()
                    .aspectRatio(mode.width.toFloat() / mode.height)
                    .clip(RoundedCornerShape(12.dp)),
            ) {
                AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
                if (watermark.enabled) {
                    CameraWatermarkOverlay(
                        callsign = callsign,
                        showCallsign = watermark.showCallsign,
                        showTimestamp = watermark.showTimestamp,
                        timestamp = timestamp,
                        modifier = Modifier.align(Alignment.BottomCenter),
                    )
                }
                if (binding.status == CameraBindingStatus.BINDING) {
                    CircularProgressIndicator(Modifier.align(Alignment.Center))
                }
                Text(
                    "${mode.label} crop",
                    modifier = Modifier.align(Alignment.TopStart)
                        .background(Color.Black.copy(alpha = 0.65f))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    color = Color.White,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
            when (binding.status) {
                CameraBindingStatus.READY -> Text(
                    "Ready. Each transmission captures a fresh picture.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                CameraBindingStatus.ERROR -> {
                    Text(binding.message ?: "Camera is unavailable.", color = MaterialTheme.colorScheme.error)
                    Button(
                        onClick = { controller.bind(context, lifecycleOwner, facing, previewView) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Try camera again")
                    }
                }
                else -> Unit
            }
        }
    }
}

@Composable
private fun CameraWatermarkOverlay(
    callsign: String,
    showCallsign: Boolean,
    showTimestamp: Boolean,
    timestamp: Instant,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier.fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.82f))
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            painter = painterResource(R.drawable.logo_viors),
            contentDescription = null,
            modifier = Modifier.size(34.dp),
            contentScale = ContentScale.Fit,
        )
        Spacer(Modifier.width(8.dp))
        Column {
            Text(WatermarkContent.TITLE, color = Color.White, style = MaterialTheme.typography.labelLarge)
            val details = WatermarkContent.cameraDetails(callsign, showCallsign, showTimestamp, timestamp)
            if (details.isNotBlank()) Text(details, color = Color.White, style = MaterialTheme.typography.labelMedium)
        }
    }
}
