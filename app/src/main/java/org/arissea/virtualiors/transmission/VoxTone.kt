package org.arissea.virtualiors.transmission

import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin

object VoxTone {
    const val FREQUENCY_HZ = 1_900.0
    const val DURATION_MS = 1_000L

    fun pcm16(sampleRate: Int, amplitude: Double = 0.45): ShortArray {
        require(sampleRate > 0)
        require(amplitude in 0.0..1.0)
        val sampleCount = sampleRate
        val scale = Short.MAX_VALUE * amplitude
        return ShortArray(sampleCount) { index ->
            (sin(2.0 * PI * FREQUENCY_HZ * index / sampleRate) * scale)
                .roundToInt()
                .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                .toShort()
        }
    }

    fun prependIfEnabled(pcm: ShortArray, sampleRate: Int, enabled: Boolean): ShortArray {
        if (!enabled) return pcm
        val tone = pcm16(sampleRate)
        return ShortArray(tone.size + pcm.size).also { output ->
            tone.copyInto(output)
            pcm.copyInto(output, tone.size)
        }
    }
}
