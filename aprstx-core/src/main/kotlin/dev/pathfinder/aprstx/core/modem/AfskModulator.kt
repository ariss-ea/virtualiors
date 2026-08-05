package dev.pathfinder.aprstx.core.modem

import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.roundToInt
import kotlin.math.sin

/** Bell 202 AFSK configuration used by 1200 baud packet/APRS. */
data class AfskConfig(
    val sampleRate: Int = 48_000,
    val baud: Int = 1_200,
    val markHz: Double = 1_200.0,
    val spaceHz: Double = 2_200.0,
    /** 0.0..1.0 of full-scale PCM. Keep below 0.5 for phone speaker/headset outputs. */
    val amplitude: Double = 0.35,
) {
    init {
        require(sampleRate > 0) { "sampleRate must be > 0" }
        require(baud > 0) { "baud must be > 0" }
        require(markHz > 0.0 && spaceHz > 0.0) { "tone frequencies must be positive" }
        require(amplitude in 0.0..1.0) { "amplitude must be in 0.0..1.0" }
    }
}

class AfskModulator(private val config: AfskConfig = AfskConfig()) {
    fun modulate(nrziToneBits: BooleanArray): ShortArray {
        if (nrziToneBits.isEmpty()) return ShortArray(0)
        val totalSamples = ceil(nrziToneBits.size * config.sampleRate.toDouble() / config.baud).toInt()
        val out = ShortArray(totalSamples)
        var phase = 0.0
        val fullScale = Short.MAX_VALUE * config.amplitude

        for (sampleIndex in out.indices) {
            val bitIndex = ((sampleIndex.toDouble() * config.baud) / config.sampleRate)
                .toInt()
                .coerceIn(0, nrziToneBits.lastIndex)
            val freq = if (nrziToneBits[bitIndex]) config.markHz else config.spaceHz
            phase += 2.0 * PI * freq / config.sampleRate
            if (phase > 2.0 * PI) phase -= 2.0 * PI
            out[sampleIndex] = (sin(phase) * fullScale).roundToInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
        return out
    }
}
