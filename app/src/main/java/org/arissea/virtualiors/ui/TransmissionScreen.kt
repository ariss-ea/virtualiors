package org.arissea.virtualiors.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.arissea.virtualiors.transmission.TransmissionUiText
import org.arissea.virtualiors.transmission.TransmissionUiState

@Composable
fun TransmissionScreen(state: TransmissionUiState?, onStop: () -> Unit) {
    val progress = state?.progress ?: 0f
    val remainingMs = state?.remainingMs ?: 0
    val minutes = remainingMs / 60_000
    val seconds = (remainingMs % 60_000) / 1_000
    val millis = remainingMs % 1_000
    val transmitting = state?.kind?.isRealTransmission == true
    val displayText = TransmissionUiText.forState(state)
    val pulse by rememberInfiniteTransition(label = "Transmitting pulse").animateFloat(
        initialValue = 0.68f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(850), RepeatMode.Reverse),
        label = "Transmitting highlight",
    )

    Column(
        Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = displayText.status,
            style = MaterialTheme.typography.headlineLarge,
            color = if (transmitting) colorScheme.error else colorScheme.primary,
            fontWeight = if (transmitting) FontWeight.Bold else FontWeight.Normal,
            modifier = if (transmitting) {
                Modifier.alpha(pulse)
                    .background(colorScheme.errorContainer.copy(alpha = 0.58f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 14.dp)
            } else Modifier,
        )
        Spacer(Modifier.height(8.dp))
        Box(Modifier.height(32.dp), contentAlignment = Alignment.Center) {
            Text(
                text = displayText.detail,
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Spacer(Modifier.height(24.dp))
        Box(Modifier.size(320.dp), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(
                progress = { progress },
                strokeWidth = 12.dp,
                modifier = Modifier.size(320.dp),
            )
            Text(
                buildAnnotatedString {
                    append("%02d:%02d:".format(minutes, seconds))
                    withStyle(SpanStyle(fontSize = 32.sp)) { append("%03d".format(millis)) }
                },
                style = MaterialTheme.typography.displayLarge,
                textAlign = TextAlign.Center,
            )
        }
        Spacer(Modifier.height(32.dp))
        OutlinedButton(onClick = onStop) {
            Icon(Icons.Default.Close, contentDescription = null)
            Spacer(Modifier.size(6.dp))
            Text("Stop Transmission")
        }
    }
}
