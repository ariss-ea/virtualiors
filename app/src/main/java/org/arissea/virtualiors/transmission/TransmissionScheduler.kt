package org.arissea.virtualiors.transmission

import org.arissea.virtualiors.model.AppConfig

enum class BlockKind(val label: String, val isRealTransmission: Boolean) {
    SSTV_IMAGE("SSTV image", true),
    VOICE("Voice", true),
    APRS_BEACON("APRS Beacon", true),
    APRS_GPS_POSITION("APRS GPS Position", true),
    APRS_TELEMETRY("APRS Telemetry", true),
    APRS_TEXT("APRS Text", true),
    WAITING("Waiting", false),
}
enum class WaitReason { COOLDOWN, APRS_PACKET_SPACING }

data class TransmissionBlock(
    val kind: BlockKind,
    val durationMs: Long,
    val voxToneBefore: Boolean = false,
    val aprsPreambleSeconds: Double = 0.0,
    val waitReason: WaitReason? = null,
    val nextLabel: String = "",
)

class TransmissionScheduler {
    fun planCycle(imageNumber: Int, config: AppConfig): List<TransmissionBlock> {
        require(imageNumber > 0) { "imageNumber must be positive" }
        val cfg = config.normalized()
        val voiceDue = cfg.voice.enabled && imageNumber % cfg.voice.everyImages == 0
        val aprsDue = cfg.aprs.enabled && imageNumber % cfg.aprs.everyImages == 0
        val blocks = mutableListOf<TransmissionBlock>()

        blocks += real(BlockKind.SSTV_IMAGE, cfg)
        blocks += cooldown(cfg)

        if (voiceDue) {
            blocks += real(BlockKind.VOICE, cfg)
            blocks += cooldown(cfg)
        }

        if (aprsDue) {
            val packets = buildList {
                add(BlockKind.APRS_BEACON)
                if (cfg.aprs.gpsPositionEnabled) add(BlockKind.APRS_GPS_POSITION)
                if (cfg.aprs.telemetryEnabled) add(BlockKind.APRS_TELEMETRY)
                if (cfg.aprs.customTextEnabled && cfg.aprs.customText.isNotBlank()) add(BlockKind.APRS_TEXT)
            }
            packets.forEachIndexed { index, kind ->
                blocks += real(kind, cfg)
                if (index != packets.lastIndex) {
                    blocks += TransmissionBlock(
                        kind = BlockKind.WAITING,
                        durationMs = cfg.aprs.packetSpacingSeconds * 1_000L,
                        waitReason = WaitReason.APRS_PACKET_SPACING,
                    )
                }
            }
            blocks += cooldown(cfg)
        }

        return blocks.mapIndexed { index, block ->
            block.copy(nextLabel = blocks.getOrNull(index + 1)?.kind?.label ?: "Next image")
        }
    }

    private fun real(kind: BlockKind, config: AppConfig): TransmissionBlock {
        val aprs = kind in APRS_KINDS
        return TransmissionBlock(
            kind = kind,
            durationMs = 0,
            voxToneBefore = config.globalVoxTone && !aprs,
            aprsPreambleSeconds = if (aprs) {
                if (config.globalVoxTone) 1.0 else DEFAULT_APRS_PREAMBLE_SECONDS
            } else 0.0,
        )
    }

    private fun cooldown(config: AppConfig) = TransmissionBlock(
        kind = BlockKind.WAITING,
        durationMs = config.cooldownSeconds * 1_000L,
        waitReason = WaitReason.COOLDOWN,
    )

    companion object {
        const val DEFAULT_APRS_PREAMBLE_SECONDS = 0.65
        val APRS_KINDS = setOf(
            BlockKind.APRS_BEACON,
            BlockKind.APRS_GPS_POSITION,
            BlockKind.APRS_TELEMETRY,
            BlockKind.APRS_TEXT,
        )
    }
}

object TimeFormatter {
    fun formatMillis(value: Long): String {
        val safe = value.coerceAtLeast(0)
        val totalSeconds = safe / 1_000
        val hours = totalSeconds / 3_600
        val minutes = (totalSeconds % 3_600) / 60
        val seconds = totalSeconds % 60
        return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds)
        else "%02d:%02d".format(minutes, seconds)
    }
}
