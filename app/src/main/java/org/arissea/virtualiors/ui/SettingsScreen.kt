package org.arissea.virtualiors.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Image as ImageIcon
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import org.arissea.virtualiors.BuildConfig
import org.arissea.virtualiors.model.AppConfig
import org.arissea.virtualiors.model.AprsCoordinateValidator
import org.arissea.virtualiors.model.AprsNoGpsLockBehavior
import org.arissea.virtualiors.model.AprsPositionSource
import org.arissea.virtualiors.model.CallsignGuidance

@Composable
fun SettingsScreen(
    config: AppConfig,
    onConfigChange: (AppConfig) -> Unit,
    onTestRobot36: () -> Unit,
    onTestAprs: () -> Unit,
    onMessage: (String) -> Unit,
    onBack: () -> Unit,
) {
    val uriHandler = LocalUriHandler.current
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 8.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Default.ArrowBack, contentDescription = "Back")
            }
            Text("Settings", style = MaterialTheme.typography.titleLarge)
        }
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Station identity", style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(
                    value = config.callsign,
                    onValueChange = { onConfigChange(config.copy(callsign = it.uppercase())) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Callsign") },
                    supportingText = {
                        if (!CallsignGuidance.looksValid(config.callsign)) {
                            Text("This looks unusual. APRS will use a safe AX.25 version.")
                        }
                    },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = config.aprsDestination,
                    onValueChange = { onConfigChange(config.copy(aprsDestination = it.uppercase())) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("APRS destination") },
                    supportingText = { Text("CQ sends a general packet to everyone.") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = config.aprsPath,
                    onValueChange = { onConfigChange(config.copy(aprsPath = it.uppercase())) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("APRS path (optional)") },
                    supportingText = { Text("Leave empty for normal use.") },
                    singleLine = true,
                )
            }
        }
        AprsLocationSettingsCard(config, onConfigChange, onMessage)
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Test signals", style = MaterialTheme.typography.titleMedium)
                Text("Play a test signal before preparing a full transmission.", style = MaterialTheme.typography.bodySmall)
                Button(onClick = onTestRobot36, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.ImageIcon, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Transmit Robot36 test image")
                }
                OutlinedButton(onClick = onTestAprs, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Radio, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Transmit APRS test beacon")
                }
            }
        }
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("About", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Column {
                    Text("VirtualIORS", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "Version ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Authors", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                    AuthorCredit("Alejandro Romero", "University of Castilla-La Mancha")
                    AuthorCredit("Rodrigo Catalán", "University of Valencia")
                }
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Acknowledgements", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                    Text(
                        "SSTV Encoder for Android by Olga Miller (Apache License 2.0), used as a reference for the SSTV encoder.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text(
                        "Dire Wolf by WB2OSZ John Langner, used as the APRS and packet-radio reference for the APRS encoder.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text(
                        "ARISS Team, teachers, students, and radio amateurs using VirtualIORS.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    TextButton(
                        onClick = { uriHandler.openUri(SETTINGS_REPOSITORY_URL) }
                    ) {
                        Icon(
                            imageVector = Icons.Default.OpenInNew,
                            contentDescription = null
                        )
                        Text("View source on GitHub")
                    }
                }
            }
        }
    }
}

private const val SETTINGS_REPOSITORY_URL = "https://github.com/ARISS-EA/VirtualIORS"

@Composable
private fun AprsLocationSettingsCard(
    config: AppConfig,
    onConfigChange: (AppConfig) -> Unit,
    onMessage: (String) -> Unit,
) {
    val context = LocalContext.current
    val locationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        if (result.values.none { it }) {
            onMessage("Location permission was not granted. The configured no-lock fallback will be used.")
        }
    }
    var latitudeDraft by remember(config.aprs.presetLatitude) { mutableStateOf(config.aprs.presetLatitude) }
    var longitudeDraft by remember(config.aprs.presetLongitude) { mutableStateOf(config.aprs.presetLongitude) }
    var pendingSource by remember(config.aprs.positionSource) { mutableStateOf<AprsPositionSource?>(null) }
    var pendingNoLockBehavior by remember(config.aprs.noGpsLockBehavior) {
        mutableStateOf<AprsNoGpsLockBehavior?>(null)
    }
    var latitudeInteracted by remember { mutableStateOf(false) }
    var longitudeInteracted by remember { mutableStateOf(false) }
    var latitudeHasFocused by remember { mutableStateOf(false) }
    var longitudeHasFocused by remember { mutableStateOf(false) }

    val displayedSource = pendingSource ?: config.aprs.positionSource
    val displayedNoLockBehavior = pendingNoLockBehavior ?: config.aprs.noGpsLockBehavior
    val coordinatesVisible = displayedSource == AprsPositionSource.PRESET_COORDINATES ||
        displayedNoLockBehavior == AprsNoGpsLockBehavior.USE_PRESET_COORDINATES
    val latitudeError = AprsCoordinateValidator.latitudeError(latitudeDraft)
    val longitudeError = AprsCoordinateValidator.longitudeError(longitudeDraft)
    val coordinatesValid = latitudeError == null && longitudeError == null
    val visibleLatitudeError = latitudeError.takeIf { latitudeInteracted || latitudeDraft.isNotBlank() }
    val visibleLongitudeError = longitudeError.takeIf { longitudeInteracted || longitudeDraft.isNotBlank() }

    fun resetCoordinateFeedback() {
        latitudeInteracted = false
        longitudeInteracted = false
        latitudeHasFocused = false
        longitudeHasFocused = false
    }

    fun applyCoordinateDrafts(latitude: String, longitude: String) {
        latitudeDraft = latitude
        longitudeDraft = longitude
        if (AprsCoordinateValidator.parse(latitude, longitude) == null) return
        val updated = config.aprs.copy(
            presetLatitude = latitude.trim(),
            presetLongitude = longitude.trim(),
            positionSource = pendingSource ?: config.aprs.positionSource,
            noGpsLockBehavior = pendingNoLockBehavior ?: config.aprs.noGpsLockBehavior,
        )
        pendingSource = null
        pendingNoLockBehavior = null
        onConfigChange(config.copy(aprs = updated))
    }

    fun selectSource(source: AprsPositionSource) {
        if (source == AprsPositionSource.PRESET_COORDINATES && !coordinatesValid) {
            resetCoordinateFeedback()
            pendingSource = source
            return
        }
        pendingSource = null
        onConfigChange(config.copy(aprs = config.aprs.copy(positionSource = source)))
        if (
            source == AprsPositionSource.PHONE_GPS &&
            config.aprs.enabled &&
            config.aprs.gpsPositionEnabled &&
            !hasLocationPermission(context)
        ) {
            locationPermission.launch(
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
            )
        }
    }

    fun selectNoLockBehavior(behavior: AprsNoGpsLockBehavior) {
        if (behavior == AprsNoGpsLockBehavior.USE_PRESET_COORDINATES && !coordinatesValid) {
            resetCoordinateFeedback()
            pendingNoLockBehavior = behavior
            return
        }
        pendingNoLockBehavior = null
        onConfigChange(config.copy(aprs = config.aprs.copy(noGpsLockBehavior = behavior)))
    }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("APRS location", style = MaterialTheme.typography.titleMedium)
            Text("Position source", style = MaterialTheme.typography.labelLarge)
            LocationChoiceRow(
                label = "Use phone GPS",
                selected = displayedSource == AprsPositionSource.PHONE_GPS,
                onClick = { selectSource(AprsPositionSource.PHONE_GPS) },
            )
            LocationChoiceRow(
                label = "Use preset coordinates",
                selected = displayedSource == AprsPositionSource.PRESET_COORDINATES,
                onClick = { selectSource(AprsPositionSource.PRESET_COORDINATES) },
            )

            if (displayedSource == AprsPositionSource.PHONE_GPS) {
                Text("Without a GPS lock", style = MaterialTheme.typography.labelLarge)
                LocationChoiceRow(
                    label = "Send \"No GPS Lock\"",
                    selected = displayedNoLockBehavior == AprsNoGpsLockBehavior.SEND_NO_GPS_LOCK,
                    onClick = { selectNoLockBehavior(AprsNoGpsLockBehavior.SEND_NO_GPS_LOCK) },
                )
                LocationChoiceRow(
                    label = "Use preset coordinates",
                    selected = displayedNoLockBehavior == AprsNoGpsLockBehavior.USE_PRESET_COORDINATES,
                    onClick = { selectNoLockBehavior(AprsNoGpsLockBehavior.USE_PRESET_COORDINATES) },
                )
            }

            if (coordinatesVisible) {
                Text("Preset coordinates", style = MaterialTheme.typography.labelLarge)
                Text(
                    "Use decimal degrees. Latitude: -90 to 90. Longitude: -180 to 180.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = latitudeDraft,
                    onValueChange = {
                        latitudeInteracted = true
                        applyCoordinateDrafts(it, longitudeDraft)
                    },
                    modifier = Modifier.fillMaxWidth().onFocusChanged { focusState ->
                        if (focusState.isFocused) {
                            latitudeHasFocused = true
                        } else if (latitudeHasFocused) {
                            latitudeInteracted = true
                        }
                    },
                    label = { Text("Latitude") },
                    placeholder = { Text("38.9943") },
                    isError = visibleLatitudeError != null,
                    supportingText = visibleLatitudeError?.let { error -> { Text(error) } },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = longitudeDraft,
                    onValueChange = {
                        longitudeInteracted = true
                        applyCoordinateDrafts(latitudeDraft, it)
                    },
                    modifier = Modifier.fillMaxWidth().onFocusChanged { focusState ->
                        if (focusState.isFocused) {
                            longitudeHasFocused = true
                        } else if (longitudeHasFocused) {
                            longitudeInteracted = true
                        }
                    },
                    label = { Text("Longitude") },
                    placeholder = { Text("-1.8585") },
                    isError = visibleLongitudeError != null,
                    supportingText = visibleLongitudeError?.let { error -> { Text(error) } },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                    singleLine = true,
                )
            }
        }
    }
}

@Composable
private fun LocationChoiceRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected = selected, onClick = onClick)
        Text(label)
    }
}

private fun hasLocationPermission(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

@Composable
private fun AuthorCredit(name: String, university: String) {
    Column {
        Text(name, style = MaterialTheme.typography.bodyLarge)
        Text(
            university,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
