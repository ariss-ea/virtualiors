package org.arissea.virtualiors.model

const val CURRENT_SCHEMA_VERSION = 2
const val DEFAULT_CALLSIGN = "VIORS"
const val DEFAULT_APRS_DESTINATION = "CQ"

enum class SstvSourceType(val label: String) {
    AUDIO_FILES("Audio files"),
    IMAGE_FILES("Image files"),
    AUTO_CAMERA("Automatic camera images"),
}

enum class SstvModeOption(
    val label: String,
    val coreId: String,
    val width: Int,
    val height: Int,
) {
    ROBOT36("Robot36", "robot36", 320, 240),
    PD120("PD120", "pd120", 640, 496),
}

enum class CameraFacing { FRONT, BACK }

data class MediaSource(
    val uri: String? = null,
    val resourceName: String? = null,
    val displayName: String,
) {
    init {
        require(!uri.isNullOrBlank() || !resourceName.isNullOrBlank()) {
            "A media source needs a URI or resource name"
        }
    }
}

data class WatermarkConfig(
    val enabled: Boolean = false,
    val showCallsign: Boolean = true,
    val showImageNumber: Boolean = true,
    val showTimestamp: Boolean = true,
)

data class VoiceConfig(
    val enabled: Boolean = false,
    val everyImages: Int = 3,
    val useExternalAudio: Boolean = false,
    val externalAudioUri: String? = null,
    val phrase: String = "Hello from Virtual I O R S",
) {
    fun normalized(): VoiceConfig = copy(everyImages = everyImages.coerceAtLeast(1))
}

data class AprsConfig(
    val enabled: Boolean = false,
    val everyImages: Int = 3,
    val packetSpacingSeconds: Int = 5,
    val beaconEnabled: Boolean = true,
    val gpsPositionEnabled: Boolean = false,
    val positionSource: AprsPositionSource = AprsPositionSource.PHONE_GPS,
    val noGpsLockBehavior: AprsNoGpsLockBehavior = AprsNoGpsLockBehavior.SEND_NO_GPS_LOCK,
    val presetLatitude: String = "",
    val presetLongitude: String = "",
    val telemetryEnabled: Boolean = false,
    val customTextEnabled: Boolean = false,
    val customText: String = "",
) {
    val needsPresetCoordinates: Boolean
        get() = positionSource == AprsPositionSource.PRESET_COORDINATES ||
            noGpsLockBehavior == AprsNoGpsLockBehavior.USE_PRESET_COORDINATES

    fun normalized(): AprsConfig = copy(
        everyImages = everyImages.coerceAtLeast(1),
        packetSpacingSeconds = packetSpacingSeconds.coerceAtLeast(1),
        beaconEnabled = enabled || beaconEnabled,
        presetLatitude = presetLatitude.trim(),
        presetLongitude = presetLongitude.trim(),
        customText = customText.take(67),
    )
}

data class AppConfig(
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
    val sourceType: SstvSourceType = SstvSourceType.AUDIO_FILES,
    val audioSources: List<MediaSource> = emptyList(),
    val imageSources: List<MediaSource> = emptyList(),
    val sstvMode: SstvModeOption = SstvModeOption.ROBOT36,
    val cameraFacing: CameraFacing = CameraFacing.FRONT,
    val watermark: WatermarkConfig = WatermarkConfig(),
    val voice: VoiceConfig = VoiceConfig(),
    val aprs: AprsConfig = AprsConfig(),
    val cooldownSeconds: Int = 120,
    val shuffle: Boolean = false,
    val globalVoxTone: Boolean = false,
    val callsign: String = DEFAULT_CALLSIGN,
    val aprsDestination: String = DEFAULT_APRS_DESTINATION,
    val aprsPath: String = "",
) {
    val normalizedCallsign: String get() = callsign.trim().uppercase().ifBlank { DEFAULT_CALLSIGN }
    val normalizedDestination: String get() = aprsDestination.trim().uppercase().ifBlank { DEFAULT_APRS_DESTINATION }

    fun normalized(): AppConfig = copy(
        schemaVersion = CURRENT_SCHEMA_VERSION,
        cooldownSeconds = cooldownSeconds.coerceAtLeast(1),
        voice = voice.normalized(),
        aprs = aprs.normalized(),
        callsign = normalizedCallsign,
        aprsDestination = normalizedDestination,
        aprsPath = aprsPath.trim().uppercase(),
    )
}

data class Preset(
    val name: String,
    val config: AppConfig,
    val builtIn: Boolean = false,
)

object CallsignGuidance {
    private val loosePattern = Regex("^[A-Z0-9]{1,6}(-(?:[0-9]|1[0-5]))?$")
    fun looksValid(value: String): Boolean = value.trim().uppercase().matches(loosePattern)
}
