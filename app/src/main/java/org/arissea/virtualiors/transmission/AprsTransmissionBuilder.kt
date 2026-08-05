package org.arissea.virtualiors.transmission

import dev.pathfinder.aprstx.core.AprsTxModem
import dev.pathfinder.aprstx.core.aprs.AprsPayloads
import dev.pathfinder.aprstx.core.ax25.HdlcConfig
import org.arissea.virtualiors.model.AppConfig
import org.arissea.virtualiors.model.AprsPositionResolution
import org.arissea.virtualiors.model.SstvSourceType

data class DeviceTelemetry(
    val batteryPercent: Int,
    val charging: Boolean,
)

data class AprsAudioPacket(
    val kind: BlockKind,
    val payload: String,
    val monitor: String,
    val pcm16: ShortArray,
    val sampleRate: Int,
    val preambleSeconds: Double,
)

class AprsTransmissionBuilder {
    fun build(
        kind: BlockKind,
        config: AppConfig,
        imageCounter: Int,
        telemetry: DeviceTelemetry,
        position: AprsPositionResolution? = null,
        preambleSeconds: Double = if (config.globalVoxTone) 1.0 else TransmissionScheduler.DEFAULT_APRS_PREAMBLE_SECONDS,
    ): AprsAudioPacket? {
        val cfg = config.normalized()
        val addressing = AprsAddressingResolver.resolve(cfg)
        val payload = when (kind) {
            BlockKind.APRS_BEACON -> AprsPayloads.status("${cfg.normalizedCallsign} - Amateur Radio on the ISS Emulator")
            BlockKind.APRS_GPS_POSITION -> when (position) {
                is AprsPositionResolution.Coordinates -> AprsPayloads.position(
                    position.value.latitude,
                    position.value.longitude,
                    "VirtualIORS",
                )
                AprsPositionResolution.NoGpsLock -> AprsPayloads.status("No GPS Lock")
                else -> return null
            }
            BlockKind.APRS_TELEMETRY -> AprsPayloads.telemetry(
                sequence = imageCounter % 1_000,
                analog1 = telemetry.batteryPercent.coerceIn(0, 100),
                analog2 = imageCounter.coerceIn(0, 255),
                analog3 = if (cfg.sstvMode.coreId == "robot36") 36 else 120,
                analog4 = when (cfg.sourceType) {
                    SstvSourceType.AUDIO_FILES -> 1
                    SstvSourceType.IMAGE_FILES -> 2
                    SstvSourceType.AUTO_CAMERA -> 3
                },
                analog5 = 0,
                bits = if (telemetry.charging) "10000000" else "00000000",
            )
            BlockKind.APRS_TEXT -> cfg.aprs.customText
                .takeIf { it.isNotBlank() }
                ?.let { AprsPayloads.message(addressing.destination, it) }
                ?: return null
            else -> error("$kind is not an APRS packet")
        }
        val transmission = AprsTxModem(
            AprsTxModem.PacketConfig(
                source = addressing.source,
                destination = addressing.destination,
                path = addressing.path,
            ),
            hdlcConfig = HdlcConfig(preambleSeconds = preambleSeconds),
        ).synthesize(payload)
        return AprsAudioPacket(
            kind,
            payload,
            transmission.monitor,
            transmission.pcm16,
            transmission.sampleRate,
            preambleSeconds,
        )
    }

}
