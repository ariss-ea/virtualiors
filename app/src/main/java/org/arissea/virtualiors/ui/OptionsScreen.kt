package org.arissea.virtualiors.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.OpenableColumns
import android.speech.tts.TextToSpeech
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Image as ImageIcon
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.arissea.virtualiors.camera.CameraCaptureController
import org.arissea.virtualiors.model.AppConfig
import org.arissea.virtualiors.model.AprsCoordinateValidator
import org.arissea.virtualiors.model.AprsPositionSource
import org.arissea.virtualiors.model.BuiltInPresets
import org.arissea.virtualiors.model.MediaSource
import org.arissea.virtualiors.model.Preset
import org.arissea.virtualiors.model.SstvModeOption
import org.arissea.virtualiors.model.SstvSourceType
import org.arissea.virtualiors.sstv.AndroidSstvImageProcessor
import org.arissea.virtualiors.transmission.AprsHeaderPresentation
import java.util.Locale
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OptionsScreen(
    config: AppConfig,
    presets: List<Preset>,
    cameraReady: Boolean,
    cameraController: CameraCaptureController,
    onConfigChange: (AppConfig) -> Unit,
    onOpenSettings: () -> Unit,
    onLoadPreset: (Preset) -> Unit,
    onSavePreset: (String) -> Unit,
    onDeletePreset: (Preset) -> Unit,
    onMessage: (String) -> Unit,
    onStart: () -> Unit,
) {
    val context = LocalContext.current
    val audioPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        uris.forEach { persistReadPermission(context, it) }
        val additions = uris.map { MediaSource(uri = it.toString(), displayName = displayName(context, it)) }
        onConfigChange(config.copy(audioSources = (config.audioSources + additions).distinctBy { it.uri }))
    }
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        uris.forEach { persistReadPermission(context, it) }
        val additions = uris.map { MediaSource(uri = it.toString(), displayName = displayName(context, it)) }
        onConfigChange(config.copy(imageSources = (config.imageSources + additions).distinctBy { it.uri }))
    }
    val voicePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        persistReadPermission(context, uri)
        onConfigChange(config.copy(voice = config.voice.copy(externalAudioUri = uri.toString(), useExternalAudio = true)))
    }
    var startAfterLocationPermission by remember { mutableStateOf(false) }
    val locationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        val shouldStart = startAfterLocationPermission
        startAfterLocationPermission = false
        if (result.values.none { it }) {
            onMessage("Location permission was not granted. The configured no-lock fallback will be used.")
        }
        if (shouldStart) onStart()
    }
    var textToSpeech by remember { mutableStateOf<TextToSpeech?>(null) }
    var ttsReady by remember { mutableStateOf(false) }
    DisposableEffect(context) {
        val engine = TextToSpeech(context) { status ->
            ttsReady = status == TextToSpeech.SUCCESS
            if (ttsReady) {
                textToSpeech?.language = Locale.US
                textToSpeech?.setSpeechRate(1.0f)
            }
        }
        textToSpeech = engine
        onDispose {
            engine.stop()
            engine.shutdown()
            textToSpeech = null
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        floatingActionButton = {
            FloatingActionButton(onClick = onOpenSettings) {
                Icon(Icons.Default.Settings, contentDescription = "Settings")
            }
        },
    ) { _ ->
        Column(
            Modifier.fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 8.dp, vertical = 4.dp),
        ) {
            PresetsCard(
                config = config,
                presets = presets,
                saveEnabled = startDisabledReason(config, cameraReady) == null,
                onLoadPreset = onLoadPreset,
                onSavePreset = onSavePreset,
                onDeletePreset = onDeletePreset,
            )
            Spacer(Modifier.height(8.dp))
            SstvSourceCard(
                config = config,
                cameraController = cameraController,
                audioPicker = { audioPicker.launch(arrayOf("audio/*")) },
                imagePicker = { imagePicker.launch(arrayOf("image/*")) },
                onConfigChange = onConfigChange,
                onMessage = onMessage,
            )
            Spacer(Modifier.height(8.dp))
            AprsCard(config, onConfigChange, onMessage) {
                if (!hasLocation(context)) {
                    locationPermission.launch(
                        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            VoiceCard(
                config = config,
                ttsReady = ttsReady,
                onSelectAudio = { voicePicker.launch(arrayOf("audio/*")) },
                onTestVoice = {
                    if (ttsReady && config.voice.phrase.isNotBlank()) {
                        textToSpeech?.speak(config.voice.phrase, TextToSpeech.QUEUE_FLUSH, null, "virtualiors-test")
                    }
                },
                onConfigChange = onConfigChange,
            )
            Spacer(Modifier.height(8.dp))
            SecondsCard(config, onConfigChange)
            Spacer(Modifier.height(8.dp))
            VoxCard(config, onConfigChange)
            Spacer(Modifier.height(8.dp))
            TransmissionOrderCard(config, onConfigChange)
            Spacer(Modifier.height(16.dp))

            val disabledReason = startDisabledReason(config, cameraReady)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                Button(
                    enabled = disabledReason == null,
                    onClick = {
                        val needsPhoneLocation = config.aprs.enabled &&
                            config.aprs.gpsPositionEnabled &&
                            config.aprs.positionSource == AprsPositionSource.PHONE_GPS
                        if (needsPhoneLocation && !hasLocation(context)) {
                            startAfterLocationPermission = true
                            locationPermission.launch(
                                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
                            )
                        } else {
                            onStart()
                        }
                    },
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text("Start Transmission")
                }
            }
            AnimatedVisibility(
                visible = disabledReason != null,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Default.Info, contentDescription = null, tint = colorScheme.secondary)
                    Spacer(Modifier.width(8.dp))
                    Text(disabledReason.orEmpty(), style = MaterialTheme.typography.bodyMedium, color = colorScheme.secondary)
                }
            }
            Spacer(Modifier.height(24.dp))
            ResetCurrentConfig(config, onConfigChange)
            Spacer(Modifier.height(88.dp))
        }
    }
}

@Composable
private fun PresetsCard(
    config: AppConfig,
    presets: List<Preset>,
    saveEnabled: Boolean,
    onLoadPreset: (Preset) -> Unit,
    onSavePreset: (String) -> Unit,
    onDeletePreset: (Preset) -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    var showSaveDialog by remember { mutableStateOf(false) }
    var newPresetName by remember { mutableStateOf("") }
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = colorScheme.secondaryContainer)) {
        Column(
            Modifier.fillMaxWidth().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("Presets", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
                modifier = Modifier.fillMaxWidth(),
            ) {
                OutlinedButton(onClick = { menuExpanded = true }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Archive, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Load preset")
                }
                Button(enabled = saveEnabled, onClick = { showSaveDialog = true }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Save, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Save preset")
                }
            }
            DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                presets.forEach { preset ->
                    DropdownMenuItem(
                        text = { Text(preset.name) },
                        onClick = { menuExpanded = false; onLoadPreset(preset) },
                        trailingIcon = {
                            if (!preset.builtIn) {
                                IconButton(onClick = { onDeletePreset(preset) }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete")
                                }
                            }
                        },
                    )
                }
            }
        }
    }
    if (showSaveDialog) {
        val nameClashes = presets.any { it.name.equals(newPresetName, ignoreCase = true) }
        val illegalName = newPresetName.isBlank() || newPresetName in BuiltInPresets.names || nameClashes
        AlertDialog(
            onDismissRequest = { showSaveDialog = false },
            title = { Text("Save Preset") },
            text = {
                OutlinedTextField(
                    value = newPresetName,
                    onValueChange = { newPresetName = it },
                    label = { Text("Preset name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    supportingText = {
                        when {
                            newPresetName in BuiltInPresets.names -> Text("That name is reserved.")
                            nameClashes -> Text("A preset with that name already exists.")
                        }
                    },
                )
            },
            confirmButton = {
                Button(
                    enabled = !illegalName,
                    onClick = { onSavePreset(newPresetName); newPresetName = ""; showSaveDialog = false },
                ) { Text("Save") }
            },
            dismissButton = {
                OutlinedButton(onClick = { showSaveDialog = false; newPresetName = "" }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun SstvSourceCard(
    config: AppConfig,
    cameraController: CameraCaptureController,
    audioPicker: () -> Unit,
    imagePicker: () -> Unit,
    onConfigChange: (AppConfig) -> Unit,
    onMessage: (String) -> Unit,
) {
    var showClearAll by remember { mutableStateOf(false) }
    val activeItems = if (config.sourceType == SstvSourceType.AUDIO_FILES) config.audioSources else config.imageSources
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp).animateContentSize()) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.width(8.dp))
                Text("SSTV Source", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                TextButton(
                    enabled = config.sourceType != SstvSourceType.AUTO_CAMERA && activeItems.isNotEmpty(),
                    onClick = { showClearAll = true },
                ) {
                    Icon(Icons.Default.DeleteSweep, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Remove all")
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                SstvSourceType.entries.forEach { type ->
                    FilterChip(
                        selected = config.sourceType == type,
                        onClick = { onConfigChange(config.copy(sourceType = type)) },
                        label = {
                            Text(
                                when (type) {
                                    SstvSourceType.AUDIO_FILES -> "Audio"
                                    SstvSourceType.IMAGE_FILES -> "Images"
                                    SstvSourceType.AUTO_CAMERA -> "Camera"
                                },
                            )
                        },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            when (config.sourceType) {
                SstvSourceType.AUDIO_FILES -> {
                    Text("SSTV Audio Files (min 12)", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = audioPicker, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.UploadFile, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Add audio files")
                    }
                    Spacer(Modifier.height(8.dp))
                    ReorderableMediaList(config.audioSources, audio = true) {
                        onConfigChange(config.copy(audioSources = it))
                    }
                }
                SstvSourceType.IMAGE_FILES -> {
                    Text("SSTV Images", style = MaterialTheme.typography.titleSmall)
                    ModeSelector(config.sstvMode) { onConfigChange(config.copy(sstvMode = it)) }
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = imagePicker, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.ImageIcon, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Add images")
                    }
                    Spacer(Modifier.height(8.dp))
                    ReorderableMediaList(config.imageSources, audio = false) {
                        onConfigChange(config.copy(imageSources = it))
                    }
                    Spacer(Modifier.height(8.dp))
                    WatermarkControls(config, camera = false, onConfigChange = onConfigChange)
                    ImagePreview(config)
                }
                SstvSourceType.AUTO_CAMERA -> {
                    Text("Automatic Camera Images", style = MaterialTheme.typography.titleSmall)
                    ModeSelector(config.sstvMode) { onConfigChange(config.copy(sstvMode = it)) }
                    Spacer(Modifier.height(8.dp))
                    WatermarkControls(config, camera = true, onConfigChange = onConfigChange)
                    CameraSourcePanel(
                        facing = config.cameraFacing,
                        mode = config.sstvMode,
                        watermark = config.watermark,
                        callsign = config.normalizedCallsign,
                        controller = cameraController,
                        onFacingChange = { onConfigChange(config.copy(cameraFacing = it)) },
                        onMessage = onMessage,
                    )
                }
            }
        }
    }
    if (showClearAll) {
        AlertDialog(
            onDismissRequest = { showClearAll = false },
            title = { Text("Remove all files?") },
            text = { Text("This clears the files selected for this SSTV source.") },
            confirmButton = {
                Button(onClick = {
                    onConfigChange(
                        if (config.sourceType == SstvSourceType.AUDIO_FILES) config.copy(audioSources = emptyList())
                        else config.copy(imageSources = emptyList()),
                    )
                    showClearAll = false
                }) { Text("Remove") }
            },
            dismissButton = { OutlinedButton(onClick = { showClearAll = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun ModeSelector(selected: SstvModeOption, onSelected: (SstvModeOption) -> Unit) {
    Text("SSTV mode", style = MaterialTheme.typography.labelLarge)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SstvModeOption.entries.forEach { mode ->
            FilterChip(
                selected = selected == mode,
                onClick = { onSelected(mode) },
                label = { Text(mode.label) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun WatermarkControls(config: AppConfig, camera: Boolean, onConfigChange: (AppConfig) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Checkbox(
            checked = config.watermark.enabled,
            onCheckedChange = { onConfigChange(config.copy(watermark = config.watermark.copy(enabled = it))) },
        )
        Text("Watermark")
    }
    AnimatedVisibility(
        visible = config.watermark.enabled,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically(),
    ) {
        Column(Modifier.fillMaxWidth().padding(start = 24.dp)) {
            CheckRow("Callsign", config.watermark.showCallsign) {
                onConfigChange(config.copy(watermark = config.watermark.copy(showCallsign = it)))
            }
            if (camera) {
                CheckRow("UTC timestamp", config.watermark.showTimestamp) {
                    onConfigChange(config.copy(watermark = config.watermark.copy(showTimestamp = it)))
                }
            } else {
                CheckRow("Image number", config.watermark.showImageNumber) {
                    onConfigChange(config.copy(watermark = config.watermark.copy(showImageNumber = it)))
                }
            }
        }
    }
}

@Composable
private fun ImagePreview(config: AppConfig) {
    val source = config.imageSources.firstOrNull() ?: return
    val context = LocalContext.current
    val previewState by produceState<ImagePreviewState>(
        initialValue = ImagePreviewState.Loading,
        source,
        config.sstvMode,
        config.watermark,
        config.callsign,
        config.imageSources.size,
    ) {
        value = ImagePreviewState.Loading
        value = runCatching {
            withContext(Dispatchers.Default) {
                AndroidSstvImageProcessor(context).prepare(
                    source = source,
                    mode = config.sstvMode,
                    watermark = config.watermark,
                    callsign = config.normalizedCallsign,
                    imageIndex = 1,
                    imageCount = config.imageSources.size,
                )
            }
        }.fold(
            onSuccess = { ImagePreviewState.Ready(it) },
            onFailure = { ImagePreviewState.Error },
        )
    }
    Spacer(Modifier.height(8.dp))
    Text("First image preview", style = MaterialTheme.typography.labelLarge)
    Spacer(Modifier.height(6.dp))
    when (val state = previewState) {
        ImagePreviewState.Loading -> Text("Preparing preview…", style = MaterialTheme.typography.bodySmall)
        ImagePreviewState.Error -> Text("Preview unavailable", style = MaterialTheme.typography.bodySmall)
        is ImagePreviewState.Ready -> Image(
            bitmap = state.bitmap.asImageBitmap(),
            contentDescription = "Final SSTV preview",
            modifier = Modifier.fillMaxWidth()
                .aspectRatio(config.sstvMode.width.toFloat() / config.sstvMode.height)
                .clip(RoundedCornerShape(12.dp)),
            contentScale = ContentScale.Fit,
        )
    }
}

private sealed interface ImagePreviewState {
    data object Loading : ImagePreviewState
    data object Error : ImagePreviewState
    data class Ready(val bitmap: android.graphics.Bitmap) : ImagePreviewState
}

@Composable
private fun AprsCard(
    config: AppConfig,
    onConfigChange: (AppConfig) -> Unit,
    onMessage: (String) -> Unit,
    requestLocation: () -> Unit,
) {
    Card(Modifier.fillMaxWidth().animateContentSize()) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = config.aprs.enabled,
                    onCheckedChange = { onConfigChange(config.copy(aprs = config.aprs.copy(enabled = it, beaconEnabled = true))) },
                )
                Spacer(Modifier.width(8.dp))
                Text("Enable APRS", style = MaterialTheme.typography.titleMedium)
            }
            val headerSummary = AprsHeaderPresentation.summaryOrNull(config)
            AnimatedVisibility(
                visible = headerSummary != null,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
            ) {
                Column {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        color = colorScheme.secondaryContainer.copy(alpha = 0.72f),
                    ) {
                        Text(
                            headerSummary.orEmpty(),
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Change the callsign, destination and path in Settings.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Spacer(Modifier.height(8.dp))
                    IntField("Transmit APRS every N images", config.aprs.everyImages) {
                        onConfigChange(config.copy(aprs = config.aprs.copy(everyImages = it)))
                    }
                    Spacer(Modifier.height(8.dp))
                    IntField("Packet spacing (seconds)", config.aprs.packetSpacingSeconds) {
                        onConfigChange(config.copy(aprs = config.aprs.copy(packetSpacingSeconds = it)))
                    }
                    CheckRow("Beacon (required)", true, enabled = false) { }
                    CheckRow("GPS position", config.aprs.gpsPositionEnabled) { checked ->
                        onConfigChange(config.copy(aprs = config.aprs.copy(gpsPositionEnabled = checked)))
                        if (checked && config.aprs.positionSource == AprsPositionSource.PHONE_GPS) requestLocation()
                    }
                    CheckRow("Mobile telemetry", config.aprs.telemetryEnabled) {
                        onConfigChange(config.copy(aprs = config.aprs.copy(telemetryEnabled = it)))
                    }
                    if (config.aprs.telemetryEnabled) {
                        Text(
                            "Shows battery, charging, image count, SSTV mode and source as an APRS telemetry lesson.",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(start = 48.dp),
                        )
                    }
                    CheckRow("Custom text message", config.aprs.customTextEnabled) {
                        onConfigChange(config.copy(aprs = config.aprs.copy(customTextEnabled = it)))
                        if (it && config.aprs.customText.isBlank()) {
                            onMessage("Enter a custom text message before transmitting.")
                        }
                    }
                    if (config.aprs.customTextEnabled) {
                        OutlinedTextField(
                            value = config.aprs.customText,
                            onValueChange = { onConfigChange(config.copy(aprs = config.aprs.copy(customText = it.take(67)))) },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Short APRS text") },
                            placeholder = { Text("Hello from VirtualIORS!") },
                            supportingText = { Text("${config.aprs.customText.length}/67") },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun VoiceCard(
    config: AppConfig,
    ttsReady: Boolean,
    onSelectAudio: () -> Unit,
    onTestVoice: () -> Unit,
    onConfigChange: (AppConfig) -> Unit,
) {
    Card(Modifier.fillMaxWidth().animateContentSize()) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = config.voice.enabled,
                    onCheckedChange = { onConfigChange(config.copy(voice = config.voice.copy(enabled = it))) },
                )
                Spacer(Modifier.width(8.dp))
                Text("Enable voice announcements (TTS)")
            }
            AnimatedVisibility(
                visible = config.voice.enabled,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IntField(
                            "Announce every N images",
                            config.voice.everyImages,
                            modifier = Modifier.width(160.dp),
                        ) { onConfigChange(config.copy(voice = config.voice.copy(everyImages = it))) }
                        Spacer(Modifier.width(8.dp))
                        Checkbox(
                            checked = config.voice.useExternalAudio,
                            onCheckedChange = { onConfigChange(config.copy(voice = config.voice.copy(useExternalAudio = it))) },
                        )
                        Spacer(Modifier.width(4.dp))
                        Text("Use custom audio instead")
                    }
                    if (config.voice.useExternalAudio) {
                        Spacer(Modifier.height(8.dp))
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                config.voice.externalAudioUri?.let { Uri.parse(it).lastPathSegment?.substringAfterLast(':') } ?: "No file chosen",
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                            )
                            OutlinedButton(onClick = onSelectAudio) {
                                Icon(Icons.Default.UploadFile, contentDescription = null)
                                Spacer(Modifier.width(6.dp))
                                Text("Select audio file")
                            }
                        }
                    } else {
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = config.voice.phrase,
                            onValueChange = { onConfigChange(config.copy(voice = config.voice.copy(phrase = it))) },
                            label = { Text("Phrase to speak") },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(8.dp))
                        FilledTonalButton(
                            enabled = ttsReady && config.voice.phrase.isNotBlank(),
                            onClick = onTestVoice,
                            modifier = Modifier.align(Alignment.CenterHorizontally),
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("Test voice")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SecondsCard(config: AppConfig, onConfigChange: (AppConfig) -> Unit) {
    var text by remember(config.cooldownSeconds) { mutableStateOf(config.cooldownSeconds.toString()) }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            OutlinedTextField(
                value = text,
                onValueChange = { entered ->
                    text = entered.filter(Char::isDigit)
                    text.toIntOrNull()?.takeIf { it > 0 }?.let { onConfigChange(config.copy(cooldownSeconds = it)) }
                },
                label = { Text("Seconds between images") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            AnimatedVisibility(
                visible = text.toIntOrNull()?.let { it < 30 } == true,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                ) {
                    Icon(Icons.Default.Warning, contentDescription = null, tint = colorScheme.error)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Warning: intervals below 30 seconds can overheat and permanently damage some transmitters.",
                        color = colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }
}

@Composable
private fun VoxCard(config: AppConfig, onConfigChange: (AppConfig) -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = config.globalVoxTone,
                    onCheckedChange = { onConfigChange(config.copy(globalVoxTone = it)) },
                )
                Spacer(Modifier.width(8.dp))
                Text("VOX Preamble")
            }
            Spacer(Modifier.height(4.dp))
            Text(
                "Helps VOX transmitters open before audio starts.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun TransmissionOrderCard(config: AppConfig, onConfigChange: (AppConfig) -> Unit) {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = colorScheme.secondaryContainer)) {
        Column(
            Modifier.padding(16.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("Transmission Order", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(12.dp))
            if (config.sourceType == SstvSourceType.AUTO_CAMERA) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CameraAlt, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Each transmission captures a fresh camera image.", textAlign = TextAlign.Center)
                }
            } else {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    ElevatedButton(
                        onClick = { onConfigChange(config.copy(shuffle = false)) },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.elevatedButtonColors(
                            containerColor = if (!config.shuffle) colorScheme.primaryContainer else colorScheme.surfaceVariant,
                        ),
                    ) {
                        Icon(Icons.Default.ArrowForward, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "Sequential\n(1 → 12 in order)",
                            textAlign = TextAlign.Center,
                        )
                    }
                    ElevatedButton(
                        onClick = { onConfigChange(config.copy(shuffle = true)) },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.elevatedButtonColors(
                            containerColor = if (config.shuffle) colorScheme.primaryContainer else colorScheme.surfaceVariant,
                        ),
                    ) {
                        Icon(Icons.Default.Shuffle, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Shuffle\n(random order)", textAlign = TextAlign.Center)
                    }
                }
            }
        }
    }
}

@Composable
private fun ResetCurrentConfig(config: AppConfig, onConfigChange: (AppConfig) -> Unit) {
    var showResetDialog by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().padding(top = 4.dp),
        horizontalArrangement = Arrangement.Center,
    ) {
        OutlinedButton(
            onClick = { showResetDialog = true },
            colors = ButtonDefaults.outlinedButtonColors(contentColor = colorScheme.error),
        ) {
            Icon(Icons.Default.Restore, contentDescription = null)
            Spacer(Modifier.width(6.dp))
            Text("Reset current config")
        }
    }
    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text("Reset configuration?") },
            text = { Text("This clears the current selections and fields, but does not delete presets.") },
            confirmButton = {
                Button(onClick = {
                    onConfigChange(
                        AppConfig(
                            callsign = config.callsign,
                            aprsDestination = config.aprsDestination,
                            aprsPath = config.aprsPath,
                            aprs = AppConfig().aprs.copy(
                                positionSource = config.aprs.positionSource,
                                noGpsLockBehavior = config.aprs.noGpsLockBehavior,
                                presetLatitude = config.aprs.presetLatitude,
                                presetLongitude = config.aprs.presetLongitude,
                            ),
                        ),
                    )
                    showResetDialog = false
                }) { Text("Reset") }
            },
            dismissButton = { OutlinedButton(onClick = { showResetDialog = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun ReorderableMediaList(
    sources: List<MediaSource>,
    audio: Boolean,
    onChange: (List<MediaSource>) -> Unit,
) {
    if (sources.isEmpty()) {
        Text("No files selected", style = MaterialTheme.typography.bodyMedium)
        return
    }
    var draggedIndex by remember(sources) { mutableStateOf<Int?>(null) }
    var targetIndex by remember(sources) { mutableStateOf<Int?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    val rowStepPx = with(LocalDensity.current) { 64.dp.toPx() }
    val start = draggedIndex
    val target = targetIndex
    val insertion = if (start != null && target != null) target + if (target > start) 1 else 0 else -1

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        sources.forEachIndexed { index, source ->
            if (insertion == index) HorizontalDivider(thickness = 3.dp, color = colorScheme.primary)
            val isDragged = index == draggedIndex
            ElevatedCard(
                modifier = Modifier.fillMaxWidth()
                    .zIndex(if (isDragged) 2f else 0f)
                    .graphicsLayer { translationY = if (isDragged) dragOffset else 0f }
                    .scale(if (isDragged) 1.02f else 1f)
                    .alpha(if (isDragged) 0.92f else 1f)
                    .pointerInput(source.uri, source.resourceName, sources.size) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = {
                                draggedIndex = index
                                targetIndex = index
                                dragOffset = 0f
                            },
                            onDrag = { change, amount ->
                                change.consume()
                                dragOffset += amount.y
                                val original = draggedIndex ?: index
                                targetIndex = (original + (dragOffset / rowStepPx).roundToInt()).coerceIn(sources.indices)
                            },
                            onDragEnd = {
                                val from = draggedIndex
                                val to = targetIndex
                                if (from != null && to != null && from != to) onChange(sources.moved(from, to))
                                draggedIndex = null
                                targetIndex = null
                                dragOffset = 0f
                            },
                            onDragCancel = {
                                draggedIndex = null
                                targetIndex = null
                                dragOffset = 0f
                            },
                        )
                    },
                colors = CardDefaults.elevatedCardColors(containerColor = colorScheme.surfaceVariant),
                elevation = CardDefaults.elevatedCardElevation(defaultElevation = if (isDragged) 10.dp else 1.dp),
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Default.DragHandle, contentDescription = "Drag to reorder", tint = colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(4.dp))
                    Icon(
                        if (audio) Icons.Default.AudioFile else Icons.Default.ImageIcon,
                        contentDescription = null,
                        tint = colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        source.displayName,
                        modifier = Modifier.weight(1f).padding(vertical = 2.dp),
                        maxLines = 2,
                        softWrap = true,
                    )
                    IconButton(enabled = index > 0, onClick = { onChange(sources.moved(index, index - 1)) }) {
                        Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Move up")
                    }
                    IconButton(enabled = index < sources.lastIndex, onClick = { onChange(sources.moved(index, index + 1)) }) {
                        Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Move down")
                    }
                    IconButton(onClick = { onChange(sources.toMutableList().also { it.removeAt(index) }) }) {
                        Icon(Icons.Default.Delete, contentDescription = "Remove", tint = colorScheme.error)
                    }
                }
            }
        }
        if (insertion == sources.size) HorizontalDivider(thickness = 3.dp, color = colorScheme.primary)
    }
}

@Composable
private fun CheckRow(
    label: String,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = checked, enabled = enabled, onCheckedChange = onCheckedChange)
        Text(label, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun IntField(
    label: String,
    value: Int,
    modifier: Modifier = Modifier.fillMaxWidth(),
    onChange: (Int) -> Unit,
) {
    var text by remember(value) { mutableStateOf(value.toString()) }
    OutlinedTextField(
        value = text,
        onValueChange = { entered ->
            text = entered.filter(Char::isDigit)
            text.toIntOrNull()?.takeIf { it > 0 }?.let(onChange)
        },
        label = { Text(label) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        singleLine = true,
        modifier = modifier,
    )
}

private fun startDisabledReason(config: AppConfig, cameraReady: Boolean): String? = when {
    config.sourceType == SstvSourceType.AUDIO_FILES && config.audioSources.size < 12 -> "Add at least 12 SSTV audio files."
    config.sourceType == SstvSourceType.IMAGE_FILES && config.imageSources.isEmpty() -> "Add at least one image."
    config.sourceType == SstvSourceType.AUTO_CAMERA && !cameraReady -> "Wait for the camera preview to be ready."
    config.voice.enabled && config.voice.useExternalAudio && config.voice.externalAudioUri.isNullOrBlank() -> "Select the custom voice audio file."
    config.aprs.enabled && config.aprs.gpsPositionEnabled && config.aprs.needsPresetCoordinates &&
        AprsCoordinateValidator.parse(config.aprs.presetLatitude, config.aprs.presetLongitude) == null ->
        "Enter valid preset coordinates in Settings."
    config.aprs.enabled && config.aprs.customTextEnabled && config.aprs.customText.isBlank() -> "Enter APRS custom text or turn it off."
    else -> null
}

private fun <T> List<T>.moved(from: Int, to: Int): List<T> = toMutableList().apply { add(to, removeAt(from)) }

private fun persistReadPermission(context: Context, uri: Uri) {
    runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
}

private fun displayName(context: Context, uri: Uri): String = runCatching {
    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) cursor.getString(0) else null
    }
}.getOrNull() ?: uri.lastPathSegment?.substringAfterLast(':') ?: "Selected file"

private fun hasLocation(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
