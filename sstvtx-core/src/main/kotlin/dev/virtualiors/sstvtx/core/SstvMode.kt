package dev.virtualiors.sstvtx.core

internal data class ModeTiming(
    val colorScanMs: Double = 0.0,
)

enum class SstvMode(
    val spec: SstvModeSpec,
    internal val timing: ModeTiming,
) {
    MARTIN_1(
        SstvModeSpec("martin1", "Martin 1", 320, 256, 44, SstvFamily.MARTIN, 0.0),
        ModeTiming(colorScanMs = 146.432),
    ),
    MARTIN_2(
        SstvModeSpec("martin2", "Martin 2", 320, 256, 40, SstvFamily.MARTIN, 0.0),
        ModeTiming(colorScanMs = 73.216),
    ),
    PD_50(
        SstvModeSpec("pd50", "PD 50", 320, 256, 93, SstvFamily.PD, 0.0),
        ModeTiming(colorScanMs = 91.52),
    ),
    PD_90(
        SstvModeSpec("pd90", "PD 90", 320, 256, 99, SstvFamily.PD, 0.0),
        ModeTiming(colorScanMs = 170.24),
    ),
    PD_120(
        SstvModeSpec("pd120", "PD 120", 640, 496, 95, SstvFamily.PD, 0.0),
        ModeTiming(colorScanMs = 121.6),
    ),
    PD_160(
        SstvModeSpec("pd160", "PD 160", 512, 400, 98, SstvFamily.PD, 0.0),
        ModeTiming(colorScanMs = 195.584),
    ),
    PD_180(
        SstvModeSpec("pd180", "PD 180", 640, 496, 96, SstvFamily.PD, 0.0),
        ModeTiming(colorScanMs = 183.04),
    ),
    PD_240(
        SstvModeSpec("pd240", "PD 240", 640, 496, 97, SstvFamily.PD, 0.0),
        ModeTiming(colorScanMs = 244.48),
    ),
    PD_290(
        SstvModeSpec("pd290", "PD 290", 800, 616, 94, SstvFamily.PD, 0.0),
        ModeTiming(colorScanMs = 228.8),
    ),
    SCOTTIE_1(
        SstvModeSpec("scottie1", "Scottie 1", 320, 256, 60, SstvFamily.SCOTTIE, 0.0),
        ModeTiming(colorScanMs = 138.24),
    ),
    SCOTTIE_2(
        SstvModeSpec("scottie2", "Scottie 2", 320, 256, 56, SstvFamily.SCOTTIE, 0.0),
        ModeTiming(colorScanMs = 88.064),
    ),
    SCOTTIE_DX(
        SstvModeSpec("scottiedx", "Scottie DX", 320, 256, 76, SstvFamily.SCOTTIE, 0.0),
        ModeTiming(colorScanMs = 345.6),
    ),
    ROBOT_36(
        SstvModeSpec("robot36", "Robot 36", 320, 240, 8, SstvFamily.ROBOT, 0.0),
        ModeTiming(),
    ),
    ROBOT_72(
        SstvModeSpec("robot72", "Robot 72", 320, 240, 12, SstvFamily.ROBOT, 0.0),
        ModeTiming(),
    ),
    WRAASE_SC2_180(
        SstvModeSpec("wraase-sc2-180", "Wraase SC2 180", 320, 256, 55, SstvFamily.WRAASE, 0.0),
        ModeTiming(colorScanMs = 235.0),
    );
}

object SstvModes {
    val all: List<SstvModeSpec> = SstvMode.entries.map { it.spec.withNominalDuration() }
    val default: SstvModeSpec = byId("robot36")

    fun byId(id: String): SstvModeSpec =
        all.firstOrNull { it.id == id } ?: throw IllegalArgumentException("Unknown SSTV mode id: $id")

    fun modeById(id: String): SstvMode =
        SstvMode.entries.firstOrNull { it.spec.id == id } ?: throw IllegalArgumentException("Unknown SSTV mode id: $id")
}

private fun SstvModeSpec.withNominalDuration(): SstvModeSpec {
    val mode = SstvMode.entries.first { it.spec.id == id }
    val samples = SstvSampleMath.totalSamples(mode, 44_100)
    return copy(nominalDurationSeconds = samples / 44_100.0)
}
