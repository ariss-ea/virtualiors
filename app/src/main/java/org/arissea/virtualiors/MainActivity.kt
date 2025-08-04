package org.arissea.virtualiors

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.view.Window
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RawRes
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.json.JSONArray
import org.json.JSONObject
import java.util.*
import kotlin.math.roundToInt
import androidx.compose.ui.layout.ContentScale
import android.media.MediaMetadataRetriever
import android.view.WindowManager
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import kotlinx.coroutines.Job
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.AudioAttributes as M3AudioAttributes
import androidx.media3.common.C
import androidx.compose.material.icons.*
import android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
import androidx.core.view.WindowCompat
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalUriHandler


private val BUILT_IN_PRESETS = setOf("Demo Robot36", "Demo PD120")

/*────────────────────────────  DATA CLASSES  ────────────────────────────────*/

private data class Config(
    val audioUris: List<Uri>,
    val useExternalTtsAudio: Boolean,
    val ttsAudioUri: Uri?, // nullable if generating TTS
    val speakEvery: Int?,  // null = no TTS
    val cooldownSeconds: Int,
    val shuffle: Boolean,
    val ttsPhrase: String?,
    val prependVoxTone: Boolean
)

private data class Preset(
    val name: String,
    val config: Config
)

/*────────────────────────────  MAIN ACTIVITY  ───────────────────────────────*/

class MainActivity : ComponentActivity(), TextToSpeech.OnInitListener {

    /*------------------  constants & helpers  ------------------*/
    private val PERMISSIONS = if (Build.VERSION.SDK_INT >= 33)
        arrayOf(Manifest.permission.READ_MEDIA_AUDIO, Manifest.permission.RECORD_AUDIO)
    else arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.RECORD_AUDIO)

    private val prefs by lazy<SharedPreferences> {
        getSharedPreferences("virtual_iors_prefs", Context.MODE_PRIVATE)
    }


    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var tts: TextToSpeech
    private var ttsReady = false

    /*------------------  lifecycle  ------------------*/
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tts = TextToSpeech(this, this)

        // Ensure default presets exist one time
        if (!prefs.getBoolean("defaults_seeded", false)) {
            seedDefaultPresets()
        }

        setContent { VirtualIORSApp(window = window, permissions = PERMISSIONS) }
    }

    override fun onDestroy() {
        super.onDestroy()
        coroutineScope.cancel()
        if (::tts.isInitialized) tts.shutdown()
    }

    override fun onInit(status: Int) {
        ttsReady = status == TextToSpeech.SUCCESS
        if (ttsReady) {
            tts.language = Locale.US
            tts.setSpeechRate(1.0f)
        }
    }

    /*────────────────────────────  COMPOSABLE ROOT  ─────────────────────────*/
    @Composable
    private fun VirtualIORSApp(window: Window, permissions: Array<String>) {
        val ctx = LocalContext.current
        val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current
        var permissionsGranted by remember {
            mutableStateOf(
                permissions.all { p ->
                    ContextCompat.checkSelfPermission(ctx, p) == PackageManager.PERMISSION_GRANTED
                }
            )
        }
        val permissionLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { result ->
            permissionsGranted = result.values.all { it }
        }
        LaunchedEffect(Unit) {
            permissionsGranted = permissions.all { p ->
                ContextCompat.checkSelfPermission(ctx, p) == PackageManager.PERMISSION_GRANTED
            }
        }
        var transmissionState by remember { mutableStateOf<TransmissionState>(TransmissionState.Idle) }
        var currentConfig by remember { mutableStateOf<Config?>(null) }
        var transmitJob by remember { mutableStateOf<Job?>(null) }
        var presetsState by remember { mutableStateOf(loadPresets()) }
        var configVersion by remember { mutableStateOf(0L) }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MaterialTheme(
                colorScheme = dynamicDarkColorScheme(ctx).run {
                    if (isSystemInDarkTheme()) this else dynamicLightColorScheme(ctx)
                },
                typography = Typography()
            ) {
                val barColor = MaterialTheme.colorScheme.background.toArgb()
                val darkIcons = !isSystemInDarkTheme()

                SideEffect {
                    WindowCompat.getInsetsController(window, window.decorView).apply {
                        isAppearanceLightStatusBars = darkIcons
                        isAppearanceLightNavigationBars = darkIcons
                    }
                    // use non-deprecated setters
                    window.setStatusBarColor(barColor)
                    window.setNavigationBarColor(barColor)
                }

                val isActive = transmissionState is TransmissionState.Active
                DisposableEffect(isActive) {
                    if (isActive) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    onDispose { window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
                }

                Surface(Modifier.fillMaxSize(), color = colorScheme.background) {
                    if (!permissionsGranted) {
                        PermissionGate(onGrant = { permissionLauncher.launch(permissions) })
                    } else {
                        Column(
                            Modifier
                                .fillMaxSize()
                                .systemBarsPadding()
                        ) {
                            TopLogoCard()

                            AnimatedContent(
                                targetState = transmissionState,
                                transitionSpec = {
                                    fadeIn(tween(500)) togetherWith fadeOut(tween(200))
                                },
                                modifier = Modifier.weight(1f)
                            ) { state ->
                                when (state) {
                                    TransmissionState.Idle -> OptionsScreen(
                                        initialConfig = currentConfig,
                                        configVersion = configVersion,
                                        onStart = { cfg ->
                                            currentConfig = cfg
                                            transmitJob?.cancel()
                                            transmitJob = coroutineScope.launch {
                                                startTransmission(cfg) { s -> transmissionState = s }
                                                transmissionState = TransmissionState.Idle
                                            }
                                        },
                                        onPresetSave = { name, cfg ->
                                            savePreset(name, cfg)
                                            presetsState = loadPresets()
                                        },
                                        onPresetLoad = { preset ->
                                            currentConfig = preset.config
                                            configVersion++
                                        },
                                        onPresetDelete = { preset ->
                                            deletePreset(preset)
                                            presetsState = loadPresets()
                                        },
                                        presets = presetsState
                                    )

                                    is TransmissionState.Active -> TransmissionDisplay(
                                        state,
                                        onStop = {
                                            transmitJob?.cancel()
                                            transmitJob = null
                                            transmissionState = TransmissionState.Idle
                                        }
                                    )

                                }
                            }
                        }
                    }
                }
            }
        }
    }


    /*────────────────────────────  UI COMPONENTS  ───────────────────────────*/

    @Composable
    private fun TopLogoCard() {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            colors = CardDefaults.cardColors(
                containerColor = colorScheme.primaryContainer
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Image(
                    painter = painterResource(id = R.drawable.ic_virtual_iors_logo),
                    contentDescription = "ARISS-EA Logo",
                    modifier = Modifier
                        .width(250.dp)
                        .wrapContentHeight(align = Alignment.CenterVertically),
                    contentScale = ContentScale.Fit
                )
                Spacer(Modifier.height(16.dp))
                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Virtual ",
                        fontStyle = FontStyle.Italic,
                        fontWeight = FontWeight.Normal,
                        style = MaterialTheme.typography.headlineLarge,
                        color = colorScheme.onPrimaryContainer
                    )
                    Text(
                        text = "IORS",
                        fontStyle = FontStyle.Normal,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.headlineLarge,
                        color = colorScheme.onPrimaryContainer
                    )
                }
            }
        }
    }

    /*────────────────────────────  PERMISSION GATE  ─────────────────────────*/

    @Composable
    private fun PermissionGate(onGrant: () -> Unit) {
        val isTiramisuPlus = Build.VERSION.SDK_INT >= 33
        val storageTitle = if (isTiramisuPlus) "Read media (audio)" else "Read external storage"
        val uriHandler = LocalUriHandler.current

        Column(
            Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = null,
                modifier = Modifier.size(72.dp),
                tint = colorScheme.primary
            )
            Spacer(Modifier.height(12.dp))
            Text(
                "Permissions needed to transmit audio",
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "VirtualIORS runs entirely on your device. We never upload your files. " +
                        "It’s open-source — you can inspect the code on the ARISS-EA GitHub.",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(16.dp))

            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.LibraryMusic, contentDescription = null, tint = colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "$storageTitle – used to pick and play your SSTV audio files (and optional TTS audio).",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Mic, contentDescription = null, tint = colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Record audio – for future APRS / Voice Repeater mode. "+
                                "We do not capture or send microphone audio.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            TextButton(onClick = {
                uriHandler.openUri("https://github.com/ARISS-EA")
            }) {
                Icon(Icons.Default.OpenInNew, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("View source on GitHub")
            }

            Spacer(Modifier.height(16.dp))
            Button(onClick = onGrant) { Text("Grant permission") }
            Spacer(Modifier.height(8.dp))
            Text(
                "You can change permissions later in Android Settings.",
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center
            )
        }
    }


    /*────────────────────────────  OPTIONS SCREEN  ──────────────────────────*/

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun OptionsScreen(
        initialConfig: Config?,
        configVersion: Long,
        onStart: (Config) -> Unit,
        onPresetSave: (String, Config) -> Unit,
        onPresetLoad: (Preset) -> Unit,
        onPresetDelete: (Preset) -> Unit,
        presets: List<Preset>
    ) {
        val ctx = LocalContext.current

        /* form state */
        var audioUris by remember { mutableStateOf(initialConfig?.audioUris ?: emptyList()) }
        var cooldownText by remember { mutableStateOf(initialConfig?.cooldownSeconds?.toString() ?: "120") }
        var shuffle by remember { mutableStateOf(initialConfig?.shuffle ?: false) }

        /* TTS */
        var useTts by remember { mutableStateOf(initialConfig?.speakEvery != null) }
        var speakEveryText by remember {
            mutableStateOf(initialConfig?.speakEvery?.toString() ?: "3")
        }
        var useExternalTtsAudio by remember {
            mutableStateOf(initialConfig?.useExternalTtsAudio ?: false)
        }
        var ttsAudioUri by remember { mutableStateOf(initialConfig?.ttsAudioUri) }

        var phraseText by remember {
            mutableStateOf(
                initialConfig?.ttsPhrase
                    ?: "Virtual I O R S demo"
            )
        }

        var prependVoxTone by remember { mutableStateOf(initialConfig?.prependVoxTone ?: false) }

        fun resetForm() {
            audioUris = emptyList()
            cooldownText = "120"
            shuffle = false

            useTts = false
            speakEveryText = "3"
            useExternalTtsAudio = false
            ttsAudioUri = null
            phraseText = "Virtual I O R S demo"

            prependVoxTone = false
        }

        LaunchedEffect(configVersion, initialConfig) {
            initialConfig?.let { cfg ->
                audioUris = cfg.audioUris
                cooldownText = cfg.cooldownSeconds.toString()
                shuffle = cfg.shuffle

                useTts = (cfg.speakEvery != null)
                speakEveryText = cfg.speakEvery?.toString() ?: speakEveryText
                useExternalTtsAudio = cfg.useExternalTtsAudio
                ttsAudioUri = cfg.ttsAudioUri
                phraseText = cfg.ttsPhrase ?: phraseText

                prependVoxTone = cfg.prependVoxTone
            }
        }

        val audioPickerLauncher =
            rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
                // persist URI permissions
                uris.forEach { ctx.contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
                audioUris = (audioUris + uris).distinct()
            }

        val singleAudioPickerLauncher =
            rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
                uri?.let {
                    ctx.contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    ttsAudioUri = it
                    useExternalTtsAudio = true
                }
            }



        /* UI */

        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            /*------------  Preset chooser / manager card  --------------*/
            Card(
                Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = colorScheme.secondaryContainer)
            ) {
                var presetMenuExpanded by remember { mutableStateOf(false) }
                var showSaveDialog by remember { mutableStateOf(false) }
                var newPresetName by remember { mutableStateOf("") }

                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {

                    Text("Presets", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedButton(                       // outlined “Load”
                            onClick = { presetMenuExpanded = true },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Archive, null)
                            Spacer(Modifier.width(6.dp))
                            Text("Load preset")
                        }
                        Button(                               // filled “Save”
                            enabled = audioUris.size >= 12,
                            onClick = { showSaveDialog = true },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Save, null)
                            Spacer(Modifier.width(6.dp))
                            Text("Save preset")
                        }
                    }
                    DropdownMenu(
                        expanded = presetMenuExpanded,
                        onDismissRequest = { presetMenuExpanded = false }
                    ) {
                        presets.forEach { preset ->
                            DropdownMenuItem(
                                text = { Text(preset.name) },
                                onClick = {
                                    presetMenuExpanded = false
                                    onPresetLoad(preset)   // this changes currentConfig at the parent
                                },
                                trailingIcon = {
                                    if (preset.name !in BUILT_IN_PRESETS) {
                                        IconButton(onClick = { onPresetDelete(preset) }) {
                                            Icon(Icons.Default.Delete, contentDescription = "Delete")
                                        }
                                    }
                                }

                            )
                        }
                    }

                    val nameClashes = presets.any { it.name.equals(newPresetName, ignoreCase = true) }
                    val illegalName = newPresetName.isBlank() || (newPresetName in BUILT_IN_PRESETS) || nameClashes
                    if (showSaveDialog) {
                        val nameClashes = presets.any { it.name.equals(newPresetName, ignoreCase = true) }
                        val illegalName = newPresetName.isBlank() ||
                                (newPresetName in BUILT_IN_PRESETS) || nameClashes

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
                                            newPresetName in BUILT_IN_PRESETS -> Text("That name is reserved.")
                                            nameClashes -> Text("A preset with that name already exists.")
                                        }
                                    }
                                )
                            },
                            confirmButton = {
                                Button(
                                    enabled = !illegalName && audioUris.size >= 12,
                                    onClick = {
                                        val cfg = buildConfig(
                                            audioUris, cooldownText, shuffle, useTts, speakEveryText,
                                            useExternalTtsAudio, ttsAudioUri, phraseText, prependVoxTone
                                        )
                                        cfg?.let { onPresetSave(newPresetName, it) }
                                        newPresetName = ""
                                        showSaveDialog = false
                                    }
                                ) { Text("Save") }
                            },
                            dismissButton = {
                                OutlinedButton(
                                    onClick = {
                                        showSaveDialog = false
                                        newPresetName = ""
                                    }
                                ) { Text("Cancel") }
                            }
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            /*------------  Audio picker card ----------------------------*/
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    // Centered title + Remove all on the right (with confirmation)
                    var showClearAll by remember { mutableStateOf(false) }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "SSTV Audio Files (min 12)",
                            style = MaterialTheme.typography.titleMedium,
                            textAlign = TextAlign.Left,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(
                            enabled = audioUris.isNotEmpty(),
                            onClick = { showClearAll = true }
                        ) {
                            Icon(Icons.Default.DeleteSweep, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("Remove all")
                        }
                    }

                    if (showClearAll) {
                        AlertDialog(
                            onDismissRequest = { showClearAll = false },
                            title = { Text("Remove all files?") },
                            text = { Text("This will clear the list of selected audio files.") },
                            confirmButton = {
                                Button(onClick = {
                                    audioUris = emptyList()
                                    showClearAll = false
                                }) { Text("Remove") }
                            },
                            dismissButton = {
                                OutlinedButton(onClick = { showClearAll = false }) { Text("Cancel") }
                            }
                        )
                    }

                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = {
                            audioPickerLauncher.launch(
                                arrayOf("audio/wav", "audio/x-wav", "audio/mpeg")
                            )
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.UploadFile, null)
                        Spacer(Modifier.width(6.dp))
                        Text("Add audio file")
                    }

                    Spacer(Modifier.height(8.dp))
                    AudioList(
                        uris = audioUris,
                        onMove = { from, to ->
                            audioUris = audioUris.toMutableList().also { list ->
                                val moved = list.removeAt(from)
                                list.add(to, moved)
                            }
                        },
                        onDelete = { i -> audioUris = audioUris.toMutableList().also { it.removeAt(i) } }
                    )
                }
            }


            Spacer(Modifier.height(8.dp))

            /*------------  TTS card ------------------------------------*/
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = useTts, onCheckedChange = { useTts = it })
                        Spacer(Modifier.width(8.dp))
                        Text("Enable voice announcements (TTS)")
                    }
                    AnimatedVisibility(useTts) {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                OutlinedTextField(
                                    value = speakEveryText,
                                    onValueChange = { speakEveryText = it },
                                    label = { Text("Announce every N images") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    singleLine = true,
                                    modifier = Modifier.width(160.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Checkbox(
                                    checked = useExternalTtsAudio,
                                    onCheckedChange = {
                                        useExternalTtsAudio = it
                                        if (!it) ttsAudioUri = null
                                    }
                                )
                                Spacer(Modifier.width(4.dp))
                                Text("Use custom audio instead")
                            }

                            if (useExternalTtsAudio) {
                                Spacer(Modifier.height(8.dp))
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        ttsAudioUri?.prettyName() ?: "No file chosen",
                                        Modifier.weight(1f),
                                        maxLines = 1
                                    )
                                    OutlinedButton(
                                        onClick = {
                                            singleAudioPickerLauncher.launch(
                                                arrayOf("audio/wav", "audio/mpeg", "audio/x-wav")
                                            )
                                        }
                                    ) {
                                        Icon(Icons.Default.UploadFile, null)
                                        Spacer(Modifier.width(6.dp))
                                        Text("Select audio file")
                                    }
                                }
                            }

                            if (useTts && !useExternalTtsAudio) {
                                Spacer(Modifier.height(8.dp))
                                OutlinedTextField(
                                    value = phraseText,
                                    onValueChange = { phraseText = it },
                                    label = { Text("Phrase to speak") },
                                    singleLine = false,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Spacer(Modifier.height(8.dp))
                                FilledTonalButton(
                                    onClick = {
                                        if (ttsReady && phraseText.isNotBlank()) {
                                            tts.speak(phraseText, TextToSpeech.QUEUE_FLUSH, null, "testId")
                                        }
                                    },
                                    modifier = Modifier.align(Alignment.CenterHorizontally)
                                ) {
                                    Icon(Icons.Default.PlayArrow, null)
                                    Spacer(Modifier.width(6.dp))
                                    Text("Test voice")
                                }
                            }

                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            /*------------  Cooldown & mode card -------------------------*/
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    OutlinedTextField(
                        value = cooldownText,
                        onValueChange = { cooldownText = it },
                        label = { Text("Seconds between images") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    val cdVal = cooldownText.toIntOrNull()
                    val showWarning = cdVal != null && cdVal < 30

                    AnimatedVisibility(
                        visible = showWarning,
                        enter = fadeIn() + expandVertically(),
                        exit  = fadeOut() + shrinkVertically()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "Warning: intervals below 30 seconds can overheat and permanently damage some transmitters.",
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = prependVoxTone, onCheckedChange = { prependVoxTone = it })
                        Spacer(Modifier.width(8.dp))
                        Text("1-second 1900 Hz tone before each image")
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Helps VOX transmitters open before the SSTV image starts.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            Card(
                Modifier
                    .fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = colorScheme.secondaryContainer)
            ) {
                Column(
                    Modifier
                        .padding(16.dp)
                        .fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("Transmission Order", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(12.dp))

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        ElevatedButton(
                            onClick = { shuffle = false },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.elevatedButtonColors(
                                containerColor = if (!shuffle) colorScheme.primaryContainer else colorScheme.surfaceVariant
                            )
                        ) {
                            Icon(Icons.Default.ArrowForward, null)
                            Spacer(Modifier.width(6.dp))
                            Text("Sequential\n(1 → 12 in order)", textAlign = TextAlign.Center)
                        }

                        ElevatedButton(
                            onClick = { shuffle = true },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.elevatedButtonColors(
                                containerColor = if (shuffle) colorScheme.primaryContainer else colorScheme.surfaceVariant
                            )
                        ) {
                            Icon(Icons.Default.Shuffle, null)
                            Spacer(Modifier.width(6.dp))
                            Text("Shuffle\n(random order)", textAlign = TextAlign.Center)
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // Compute why the Start button is disabled (updates in real time)
            val startDisabledReason: String? = remember(
                audioUris, cooldownText, useTts, speakEveryText, useExternalTtsAudio, ttsAudioUri
            ) {
                when {
                    audioUris.size < 12 -> "Add at least 12 SSTV audio files."
                    cooldownText.toIntOrNull() == null -> "Enter a valid number of seconds between images."
                    useTts && speakEveryText.toIntOrNull() == null -> "Enter a valid “every N images” value for TTS."
                    useExternalTtsAudio && ttsAudioUri == null -> "Select the custom TTS audio file (or uncheck “Use custom audio” or disable TTS)."
                    else -> null
                }
            }

            /*------------  Start button --------------------------------*/
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                val cfg = buildConfig(
                    audioUris,
                    cooldownText,
                    shuffle,
                    useTts,
                    speakEveryText,
                    useExternalTtsAudio,
                    ttsAudioUri,
                    phraseText,
                    prependVoxTone
                )
                Button(
                    enabled = cfg != null,
                    onClick = { cfg?.let(onStart) }
                ) {
                    Icon(Icons.Default.PlayArrow, null)
                    Spacer(Modifier.width(4.dp))
                    Text("Start Transmission")
                }
            }
            AnimatedVisibility(visible = startDisabledReason != null) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        startDisabledReason ?: "",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }


            Spacer(Modifier.height(24.dp))

            /*------------  Reset button --------------------------------*/
            var showResetDialog by remember { mutableStateOf(false) }

            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                // Place Reset below and visually safer
                OutlinedButton(
                    onClick = { showResetDialog = true },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = colorScheme.error)
                ) {
                    Icon(Icons.Default.Restore, null)
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
                        Button(
                            onClick = {
                                resetForm()
                                showResetDialog = false
                            }
                        ) { Text("Reset") }
                    },
                    dismissButton = {
                        OutlinedButton(onClick = { showResetDialog = false }) { Text("Cancel") }
                    }
                )
            }

        }


    }

    @Composable
    private fun AudioList(
        uris: List<Uri>,
        onMove: (from: Int, to: Int) -> Unit,
        onDelete: (index: Int) -> Unit
    ) {
        if (uris.isEmpty()) {
            Text("No files selected", style = MaterialTheme.typography.bodyMedium)
            return
        }

        // simple up/down move – keeps code self-contained
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            uris.forEachIndexed { index, uri ->
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.AudioFile, null, tint = colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            uri.prettyName(),
                            modifier = Modifier
                                .weight(1f)
                                .padding(vertical = 2.dp),
                            maxLines = 2,
                            softWrap = true
                        )
                        IconButton(
                            enabled = index > 0,
                            onClick = { onMove(index, index - 1) }
                        ) { Icon(Icons.Default.KeyboardArrowUp, null) }
                        IconButton(
                            enabled = index < uris.lastIndex,
                            onClick = { onMove(index, index + 1) }
                        ) { Icon(Icons.Default.KeyboardArrowDown, null) }
                        IconButton(onClick = { onDelete(index) }) {
                            Icon(Icons.Default.Delete, null, tint = colorScheme.error)
                        }
                    }
                }
            }
        }
    }

    /*─────────────────────────  TRANSMISSION UI  ─────────────────────────────*/

    @Composable
    private fun TransmissionDisplay(
        state: TransmissionState.Active,
        onStop: () -> Unit
    ) {
        val progress by remember(state) { state.progress }.collectAsState()
        val remainingMs = remember(progress) {
            ((1f - progress) * state.totalMillis).roundToInt()
        }.coerceAtLeast(0)

        val minutes  = remainingMs / 60_000
        val seconds  = (remainingMs % 60_000) / 1_000
        val millis   = remainingMs % 1_000
        val timeText = "%02d:%02d:%03d".format(minutes, seconds, millis)

        val status = when (state.kind) {
            TransmissionKind.SSTV -> "Transmitting"
            TransmissionKind.Wait -> "Waiting"
            TransmissionKind.TTS  -> "Voice Transmission"
        }

        Column(
            Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                status,
                style = MaterialTheme.typography.headlineLarge,
                color = colorScheme.primary
            )
            Spacer(Modifier.height(8.dp))

            Text(                             // nice file name
                state.fileName,
                style = MaterialTheme.typography.titleLarge
            )

            Spacer(Modifier.height(24.dp))

            Box(Modifier.size(320.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(     // smoother ring – same fps as loop
                    progress = progress,
                    strokeWidth = 12.dp,
                    modifier = Modifier.size(320.dp)
                )
                Text(
                    buildAnnotatedString {
                        append("%02d:%02d:".format(minutes, seconds))
                        withStyle(SpanStyle(fontSize = 32.sp)) {
                            append("%03d".format(millis))
                        }
                    },
                    style = MaterialTheme.typography.displayLarge,
                    textAlign = TextAlign.Center
                )
            }
            Spacer(Modifier.height(32.dp))
            OutlinedButton(onClick = onStop) {
                Icon(Icons.Default.Close, null)
                Spacer(Modifier.width(6.dp))
                Text("Stop Transmission")
            }
        }
    }



    /*───────────────────────────  STATE TYPES  ──────────────────────────────*/

    sealed interface TransmissionState {
        object Idle : TransmissionState
        class Active(
            val kind: TransmissionKind,
            val totalMillis: Int,                 // rename
            internal val progress: MutableStateFlow<Float>,
            val fileName: String
        ) : TransmissionState

    }

    enum class TransmissionKind { SSTV, Wait, TTS }

    /*───────────────────────────  TRANSMISSION  ─────────────────────────────*/

    private suspend fun startTransmission(
        cfg: Config,
        updateUi: (TransmissionState) -> Unit
    ) = coroutineScope {
        require(cfg.audioUris.size >= 12) // safety – UI already blocks
        val order = if (cfg.shuffle) cfg.audioUris.shuffled() else cfg.audioUris
        val speakEvery = cfg.speakEvery
        var index = 0
        var nextSpeakCounter = 0

        while (isActive) {

            /* ---------- 1.   IMAGE (with optional VOX tone, gapless) ------------ */
            val imgUri   = order[index]
            val imgDurMs = audioDurationMs(imgUri)

            if (cfg.prependVoxTone) {
                val toneUri = tone1900Uri()
                val toneDur = audioDurationMs(toneUri).coerceAtLeast(900)
                val totalDur = toneDur + imgDurMs
                launch { runCountdown(totalDur, TransmissionKind.SSTV, imgUri.prettyName(), updateUi) }
                playGaplessPairBlocking(toneUri, imgUri)
            } else {
                launch { runCountdown(imgDurMs, TransmissionKind.SSTV, imgUri.prettyName(), updateUi) }
                playAudioBlocking(imgUri)
            }

            nextSpeakCounter++

            /* ---------- 2.   first WAIT (always) ------------ */
            val ttsDue = speakEvery != null &&             // user turned TTS on
                nextSpeakCounter == speakEvery //nextSpeakCounter es el número de imágenes desde el último TTS

            launch {
                val label = if (ttsDue) "Next: TTS" else "Next: Image"
                runCountdown(cfg.cooldownSeconds * 1_000,
                    TransmissionKind.Wait, label, updateUi)
            }
            delay(cfg.cooldownSeconds * 1_000L)            // real waiting time

            /* ---------- 3.   optional  TTS  ----------------- */
            if (ttsDue) {
                nextSpeakCounter = 0                       // reset counter

                if (cfg.useExternalTtsAudio && cfg.ttsAudioUri != null) {
                    val dur = audioDurationMs(cfg.ttsAudioUri)
                    launch { runCountdown(dur, TransmissionKind.TTS, "TTS", updateUi) }
                    playAudioBlocking(cfg.ttsAudioUri)

                } else if (ttsReady) {
                    val text   = cfg.ttsPhrase ?: "TTS was not configured"
                    val estDur = estimateTtsDurationMs(text)
                    launch { runCountdown(estDur, TransmissionKind.TTS, "TTS", updateUi) }
                    speakTtsBlocking(text)
                }

                /* ---------- 4.   second WAIT (after TTS) ----- */
                launch {
                    runCountdown(cfg.cooldownSeconds * 1_000,
                        TransmissionKind.Wait, "Next: Image", updateUi)
                }
                delay(cfg.cooldownSeconds * 1_000L)
            }
            /* ---------- 5.   advance to next image ---------- */
            index = (index + 1) % order.size

        }

    }

    /*--- MediaPlayer helper – blocks until completion & returns duration(ms) ---*/
    private suspend fun playAudioBlocking(uri: Uri): Int = suspendCancellableCoroutine { cont ->
        val player = MediaPlayer().apply {
            setDataSource(this@MainActivity, uri)
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .setUsage(AudioAttributes.USAGE_MEDIA).build()
            )
            setOnPreparedListener { start() }
            setOnCompletionListener {
                val dur = duration
                release()
                if (cont.isActive) cont.resume(dur, null)
            }
            setOnErrorListener { _, _, _ ->
                release()
                if (cont.isActive) cont.resume(0, null)
                true
            }
            prepareAsync()
        }
        cont.invokeOnCancellation { player.release() }
    }

    /*--- TTS helper, blocks until spoken, returns duration(ms) heuristic ---*/
    private suspend fun speakTtsBlocking(text: String): Int = suspendCancellableCoroutine { cont ->
        val utteranceId = UUID.randomUUID().toString()
        val listener = TextToSpeech.OnUtteranceCompletedListener { id ->
            if (id == utteranceId && cont.isActive) cont.resume(0, null)
        }
        tts.setOnUtteranceCompletedListener(listener)
        val params = Bundle().apply { putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId) }
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, params, utteranceId)
        /* simple heuristic: 150 wpm */
        val dur = (text.split("\\s+".toRegex()).size / 2.5 * 1000).roundToInt()
        coroutineScope.launch {
            delay(dur.toLong() + 500)
            val safeDur = dur.coerceAtLeast(500)
            delay(safeDur.toLong() + 500)
            if (cont.isActive) cont.resume(safeDur, null)

        }
    }

    private suspend fun runCountdown(
        rawDurationMs: Int,
        kind: TransmissionKind,
        fileName: String,
        updateUi: (TransmissionState) -> Unit
    ) {
        val durationMs = rawDurationMs.coerceAtLeast(100)
        val flow = MutableStateFlow(0f)
        updateUi(TransmissionState.Active(kind, durationMs, flow, fileName))

        val start = System.currentTimeMillis()
        while (true) {
            val p = ((System.currentTimeMillis() - start).toFloat() / durationMs)
                .coerceIn(0f, 1f)
            flow.value = p
            if (p >= 1f) break
            delay(33)
        }
    }

    private suspend fun playGaplessPairBlocking(first: Uri, second: Uri): Int =
        suspendCancellableCoroutine { cont ->
            val player = ExoPlayer.Builder(this).build().apply {
                val attrs = M3AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .setUsage(C.USAGE_MEDIA)
                    .build()
                setAudioAttributes(attrs, /* handleAudioFocus= */ true)

                addMediaItem(MediaItem.fromUri(first))
                addMediaItem(MediaItem.fromUri(second))

                prepare()
                playWhenReady = true

                addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(state: Int) {
                        if (state == Player.STATE_ENDED && cont.isActive) {
                            val total = audioDurationMs(first) + audioDurationMs(second)
                            release()
                            cont.resume(total, null)
                        }
                    }
                })
            }

            cont.invokeOnCancellation { player.release() }
        }

    /*───────────────────────────  BUILD CONFIG  ─────────────────────────────*/

    private fun buildConfig(
        audioUris: List<Uri>,
        cooldownText: String,
        shuffle: Boolean,
        useTts: Boolean,
        speakEveryText: String,
        useExternalTts: Boolean,
        ttsUri: Uri?,
        phraseText: String,
        prependVoxTone: Boolean
    ): Config? {
        val cooldown = cooldownText.toIntOrNull() ?: return null
        val speakEvery = if (useTts) speakEveryText.toIntOrNull() else null
        if (useTts && speakEvery == null) return null
        if (useExternalTts && ttsUri == null) return null
        if (audioUris.size < 12) return null
        return Config(
            audioUris = audioUris,
            useExternalTtsAudio = useExternalTts,
            ttsAudioUri = ttsUri,
            speakEvery = speakEvery,
            cooldownSeconds = cooldown,
            shuffle = shuffle,
            ttsPhrase = phraseText.takeIf{useTts && !useExternalTts},
            prependVoxTone = prependVoxTone
        )
    }

    /*────────────────────────  PRESETS – PERSISTENCE  ───────────────────────*/

    private fun savePreset(name: String, cfg: Config) {
        val list = loadPresets().toMutableList()
        list.removeAll { it.name == name } // overwrite
        list.add(Preset(name, cfg))
        prefs.edit {
            putString("presets_json", JSONArray().apply {
                list.forEach { put(it.toJson()) }
            }.toString())
        }
    }

    private fun deletePreset(preset: Preset) {
        val list = loadPresets().filter { it.name != preset.name }
        prefs.edit {
            putString("presets_json", JSONArray().apply {
                list.forEach { put(it.toJson()) }
            }.toString())
        }
    }

    private fun loadPresets(): List<Preset> {
        val json = prefs.getString("presets_json", "[]") ?: "[]"
        return JSONArray(json).let { arr ->
            (0 until arr.length()).mapNotNull { i ->
                runCatching { presetFromJson(arr.getJSONObject(i)) }.getOrNull()
            }
        }
    }

    // Helper to parse a Preset from JSONObject
    private fun presetFromJson(obj: JSONObject): Preset {
        val name = obj.getString("name")
        val configObj = obj.getJSONObject("config")
        val audioUris = configObj.getJSONArray("audio").let { audioArr ->
            List(audioArr.length()) { i -> Uri.parse(audioArr.getString(i)) }
        }
        val useExternalTtsAudio = configObj.optBoolean("ttsExt", false)
        val ttsAudioUri = configObj.optString("ttsUri", null)?.let { if (it.isNotEmpty()) Uri.parse(it) else null }
        val speakEvery = if (configObj.has("speakEvery") && !configObj.isNull("speakEvery")) configObj.getInt("speakEvery") else null
        val cooldownSeconds = configObj.getInt("cooldown")
        val shuffle = configObj.optBoolean("shuffle", false)
        val phrase = configObj.optString("ttsPhrase", null).takeIf { it?.isNotBlank() == true }
        val voxTone = configObj.optBoolean("voxTone", false)
        return Preset(
            name = name,
            config = Config(
                audioUris = audioUris,
                useExternalTtsAudio = useExternalTtsAudio,
                ttsAudioUri = ttsAudioUri,
                speakEvery = speakEvery,
                cooldownSeconds = cooldownSeconds,
                shuffle = shuffle,
                ttsPhrase = phrase,
                prependVoxTone = voxTone
            )
        )
    }

    /*──────────────────────────  DEFAULT PRESETS  ───────────────────────────*/

    private fun seedDefaultPresets() {
        val robotUris = (1..12).map { rawUri(resIdByName("demo_robot36_%02d".format(it))) }
        val pdUris = (1..12).map { rawUri(resIdByName("demo_pd120_%02d".format(it))) }

        val robotCfg = Config(
            audioUris = robotUris,
            useExternalTtsAudio = false,
            ttsAudioUri = null,
            speakEvery = null,
            cooldownSeconds = 120,
            shuffle = false,
            ttsPhrase = "This is a Virtual I O R S Demo.",
            prependVoxTone = true
        )
        val pdCfg = robotCfg.copy(audioUris = pdUris)

        prefs.edit {
            putBoolean("defaults_seeded", true)
            putString(
                "presets_json", JSONArray().apply {
                    put(Preset("Demo Robot36", robotCfg).toJson())
                    put(Preset("Demo PD120", pdCfg).toJson())
                }.toString()
            )
        }
    }

    /*──────────────────────────  UTIL HELPERS  ─────────────────────────────*/

    private fun Preset.toJson(): JSONObject = JSONObject().apply {
        put("name", name)
        put("config", JSONObject().apply {
            put("audio", JSONArray(config.audioUris.map { it.toString() }))
            put("ttsExt", config.useExternalTtsAudio)
            put("ttsUri", config.ttsAudioUri?.toString())
            put("speakEvery", config.speakEvery)
            put("cooldown", config.cooldownSeconds)
            put("shuffle", config.shuffle)
            put("ttsPhrase", config.ttsPhrase)
            put("voxTone", config.prependVoxTone)
        })
    }

    private fun resIdByName(resName: String): Int =
        resources.getIdentifier(resName, "raw", packageName)

    private fun rawUri(@RawRes id: Int): Uri =
        Uri.parse("android.resource://$packageName/$id")

    private fun audioDurationMs(uri: Uri): Int {
        val mmr = MediaMetadataRetriever()
        return try {
            mmr.setDataSource(this, uri)
            mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toInt() ?: 0
        } finally {
            mmr.release()
        }
    }

    private fun Uri.prettyName(): String {
        return when (scheme) {
            "android.resource" -> {
                val id = lastPathSegment?.toIntOrNull()
                if (id != null) {
                    try { resources.getResourceEntryName(id) } catch (_: Exception) { id.toString() }
                } else toString()
            }
            else -> {
                lastPathSegment
                    ?.substringAfterLast('/')    // remove “document/.../”
                    ?.substringAfterLast(':')    // remove “primary:”
                    ?: "audio"
            }
        }
    }

    private fun estimateTtsDurationMs(text: String): Int =
        (text.split("\\s+".toRegex()).size / 2.5 * 1000).roundToInt().coerceAtLeast(500)

    private fun tone1900Uri(): Uri = rawUri(resIdByName("tone_1900hz"))

}
