package org.arissea.virtualiors.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Policy
import androidx.compose.material.icons.filled.SatelliteAlt
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@Composable
fun OnboardingScreen(onContinue: () -> Unit) {
    val uriHandler = LocalUriHandler.current

    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier.weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top,
        ) {
            Icon(
                Icons.Default.SatelliteAlt,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(72.dp),
            )
            Spacer(Modifier.height(12.dp))
            Text(
                "Bring ISS-style radio to the classroom.",
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                "VirtualIORS helps you recreate the ARISS IORS payload experience with a phone and a radio setup. " +
                    "Prepare SSTV images, add optional voice or APRS packets, and let students learn about space communications. " +
                    "Use it for classroom demonstrations, to create your own SSTV events, " +
                    "or to practice for real ARISS SSTV and APRS activities.",
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                "Your files stay on this device. Camera and location are only requested if you choose features that need them. " +
                    "Source code is available on GitHub.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(12.dp))
            TextButton(onClick = { uriHandler.openUri(PRIVACY_POLICY_URL) }) {
                Icon(Icons.Default.Policy, contentDescription = null)
                Text(" Privacy Policy")
            }
            TextButton(onClick = { uriHandler.openUri(REPOSITORY_URL) }) {
                Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null)
                Text(" View source on GitHub")
            }
        }
        Button(
            onClick = onContinue,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp).height(56.dp),
        ) {
            Text("Get started")
        }
    }
}

private const val REPOSITORY_URL = "https://github.com/ARISS-EA/VirtualIORS"
