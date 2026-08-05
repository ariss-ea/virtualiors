package org.arissea.virtualiors.transmission

data class TransmissionDisplayText(
    val status: String,
    val detail: String,
)

object TransmissionUiText {
    fun forState(state: TransmissionUiState?): TransmissionDisplayText = when {
        state == null -> TransmissionDisplayText("Preparing", "Preparing transmission")
        state.durationMs == 0L -> TransmissionDisplayText(
            "Preparing",
            if (state.detail in CAMERA_PREPARATION_DETAILS) {
                state.detail
            } else {
                "Preparing ${nextCategory(state.nextLabel).lowercaseUnlessAcronym()}"
            },
        )
        state.kind.isRealTransmission -> TransmissionDisplayText(
            "Transmitting",
            transmissionLabel(state.kind, state.detail),
        )
        else -> TransmissionDisplayText("Waiting", "Next: ${nextCategory(state.nextLabel)}")
    }

    fun transmissionLabel(kind: BlockKind, sstvLabel: String = ""): String = when (kind) {
        BlockKind.SSTV_IMAGE -> sstvLabel.ifBlank { "SSTV image" }
        BlockKind.VOICE -> "Voice announcement"
        BlockKind.APRS_BEACON -> "APRS beacon"
        BlockKind.APRS_GPS_POSITION -> "APRS position"
        BlockKind.APRS_TELEMETRY -> "APRS telemetry"
        BlockKind.APRS_TEXT -> "APRS text"
        BlockKind.WAITING -> "Waiting"
    }

    private fun nextCategory(raw: String): String = when {
        raw.contains("APRS", ignoreCase = true) -> "APRS"
        raw.contains("voice", ignoreCase = true) -> "Voice"
        raw.contains("image", ignoreCase = true) || raw.contains("SSTV", ignoreCase = true) -> "Image"
        raw.isBlank() -> "Image"
        else -> raw
    }

    private fun String.lowercaseUnlessAcronym(): String = if (this == "APRS") this else lowercase()

    private val CAMERA_PREPARATION_DETAILS = setOf("Taking photo", "Preparing image")
}
