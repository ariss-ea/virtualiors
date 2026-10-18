package org.arissea.virtualiors.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.view.ContextThemeWrapper
import android.view.View
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Policy
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.SettingsInputAntenna
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.core.locationbutton.LocationButton
import org.arissea.virtualiors.R

const val PRIVACY_POLICY_URL = "https://ariss-ea.org/virtualiors-privacy/"

/** Android 17 (API 37) renders the system location button; older versions use the runtime prompt. */
private val systemLocationButtonSupported: Boolean
    get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.CINNAMON_BUN

fun hasPhoneLocationPermission(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

/**
 * Prominent disclosure shown before any location request. Explains that APRS coordinates may be
 * broadcast over radio, then asks for location through the Android 17+ location button or, on
 * older versions, through the regular runtime permission prompt.
 *
 * Keep this composable in the tree even when [visible] is false so the permission launcher survives.
 */
@Composable
fun LocationDisclosureDialog(
    visible: Boolean,
    dismissLabel: String,
    onGranted: () -> Unit,
    onDenied: () -> Unit,
    onUsePresetCoordinates: () -> Unit,
    onDismiss: () -> Unit,
) {
    val currentOnGranted by rememberUpdatedState(onGranted)
    val currentOnDenied by rememberUpdatedState(onDenied)
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        if (result.values.any { it }) currentOnGranted() else currentOnDenied()
    }
    if (!visible) return

    var systemButtonFailed by rememberSaveable { mutableStateOf(false) }
    val requestWithRuntimePrompt = {
        permissionLauncher.launch(
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
        )
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier.padding(horizontal = 24.dp).fillMaxWidth().heightIn(max = 640.dp),
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 6.dp,
        ) {
            Column(
                Modifier.verticalScroll(rememberScrollState()).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = CircleShape) {
                    Icon(
                        Icons.Default.LocationOn,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(14.dp).size(28.dp),
                    )
                }
                Spacer(Modifier.height(16.dp))
                Text(
                    "Use phone location for APRS?",
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    "VirtualIORS uses your location only when APRS GPS Position is enabled. " +
                        "During an active transmission, your current location is used to generate the coordinates " +
                        "included in APRS packets. VirtualIORS does not send this location to its own servers.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                Surface(
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                    shape = MaterialTheme.shapes.large,
                ) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
                        Icon(Icons.Default.SettingsInputAntenna, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(12.dp))
                        Text(
                            "If your device is connected to a radio transmitter, these coordinates may be broadcast " +
                                "over radio and received by third parties.",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    "You can use preset coordinates instead.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(24.dp))
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (systemLocationButtonSupported && !systemButtonFailed) {
                        SystemLocationButton(
                            onPermissionResult = { granted -> if (granted) currentOnGranted() else currentOnDenied() },
                            onError = { systemButtonFailed = true },
                        )
                    } else {
                        Button(
                            onClick = requestWithRuntimePrompt,
                            modifier = Modifier.fillMaxWidth().height(56.dp),
                        ) {
                            Icon(Icons.Default.LocationOn, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Allow location")
                        }
                    }
                    OutlinedButton(
                        onClick = onUsePresetCoordinates,
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                    ) {
                        Text("Use preset coordinates")
                    }
                    TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                        Text(dismissLabel)
                    }
                }
            }
        }
    }
}

/** System-rendered Android 17 location button that grants precise location for the current session. */
@Composable
private fun SystemLocationButton(
    onPermissionResult: (Boolean) -> Unit,
    onError: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val density = LocalDensity.current
    val currentOnPermissionResult by rememberUpdatedState(onPermissionResult)
    val currentOnError by rememberUpdatedState(onError)
    AndroidView(
        modifier = Modifier.fillMaxWidth().height(56.dp),
        factory = { context ->
            LocationButton(ContextThemeWrapper(context, R.style.ThemeOverlay_VirtualIORS_LocationButton)).apply {
                id = View.generateViewId()
                setTextType(LocationButton.TEXT_TYPE_USE_PRECISE_LOCATION)
                setBackgroundColor(colors.primary.toArgb())
                setTextColor(colors.onPrimary.toArgb())
                setIconTint(colors.onPrimary.toArgb())
                // Material 3 Expressive shape morph: fully rounded at rest, squarer while pressed.
                setCornerRadius(with(density) { 28.dp.toPx() })
                setPressedCornerRadius(with(density) { 12.dp.toPx() })
                setOnPermissionResultListener { granted -> currentOnPermissionResult(granted) }
                setOnErrorListener { currentOnError() }
            }
        },
    )
}

/** Compact "Privacy & data" summary, shown as a dialog so Settings stays uncluttered. */
@Composable
fun PrivacyAndDataDialog(onDismiss: () -> Unit) {
    val uriHandler = LocalUriHandler.current
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.PrivacyTip, contentDescription = null) },
        title = { Text("Privacy & data") },
        text = {
            Text(
                "Media, camera images and APRS configuration are processed locally on this device. " +
                    "Location is only accessed when phone GPS is selected for APRS.",
            )
        },
        confirmButton = {
            TextButton(onClick = { uriHandler.openUri(PRIVACY_POLICY_URL) }) {
                Text("Privacy Policy")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        },
    )
}

/** About links shown as three uniform tonal buttons: privacy summary, privacy policy and source code. */
@Composable
fun AboutLinkButtons(repositoryUrl: String) {
    val uriHandler = LocalUriHandler.current
    var showPrivacyDialog by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        AboutLinkButton(Icons.Default.PrivacyTip, "Privacy & data") { showPrivacyDialog = true }
        AboutLinkButton(Icons.Default.Policy, "Privacy Policy") { uriHandler.openUri(PRIVACY_POLICY_URL) }
        AboutLinkButton(Icons.Default.Code, "View source on GitHub") { uriHandler.openUri(repositoryUrl) }
    }
    if (showPrivacyDialog) {
        PrivacyAndDataDialog(onDismiss = { showPrivacyDialog = false })
    }
}

@Composable
private fun AboutLinkButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    FilledTonalButton(onClick = onClick, modifier = Modifier.fillMaxWidth().height(48.dp)) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
        Spacer(Modifier.width(ButtonDefaults.IconSpacing))
        Text(label)
    }
}
