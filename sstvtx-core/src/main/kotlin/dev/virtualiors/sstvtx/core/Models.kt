/*
 * Copyright 2026 VirtualIORS SSTV TX contributors.
 *
 * Derived in part from SSTV Encoder for Android by Olga Miller, Apache License 2.0.
 */
package dev.virtualiors.sstvtx.core

/** ARGB image container used by the pure Kotlin/JVM encoder. */
data class RgbImage(
    val width: Int,
    val height: Int,
    val argb: IntArray,
) {
    init {
        require(width > 0) { "width must be > 0" }
        require(height > 0) { "height must be > 0" }
        require(argb.size == width * height) { "argb must contain width * height pixels" }
    }

    fun pixel(x: Int, y: Int): Int = argb[y * width + x]

    override fun equals(other: Any?): Boolean =
        other is RgbImage && width == other.width && height == other.height && argb.contentEquals(other.argb)

    override fun hashCode(): Int {
        var result = width
        result = 31 * result + height
        result = 31 * result + argb.contentHashCode()
        return result
    }
}

enum class SstvFamily { MARTIN, PD, SCOTTIE, ROBOT, WRAASE }

data class SstvModeSpec(
    val id: String,
    val displayName: String,
    val width: Int,
    val height: Int,
    val visCode: Int,
    val family: SstvFamily,
    val nominalDurationSeconds: Double,
)

data class SstvTransmission(
    val mode: SstvModeSpec,
    val sampleRate: Int,
    val pcm16: ShortArray,
    val durationSeconds: Double,
) {
    override fun equals(other: Any?): Boolean =
        other is SstvTransmission &&
            mode == other.mode &&
            sampleRate == other.sampleRate &&
            durationSeconds == other.durationSeconds &&
            pcm16.contentEquals(other.pcm16)

    override fun hashCode(): Int {
        var result = mode.hashCode()
        result = 31 * result + sampleRate
        result = 31 * result + pcm16.contentHashCode()
        result = 31 * result + durationSeconds.hashCode()
        return result
    }
}

data class SstvEncoderConfig(
    val sampleRate: Int = 44_100,
    val amplitude: Double = 0.80,
) {
    init {
        require(sampleRate in 8_000..192_000) { "sampleRate must be between 8000 and 192000 Hz" }
        require(amplitude in 0.0..1.0) { "amplitude must be between 0.0 and 1.0" }
    }
}
