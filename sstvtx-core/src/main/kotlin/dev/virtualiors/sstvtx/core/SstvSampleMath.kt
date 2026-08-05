package dev.virtualiors.sstvtx.core

import kotlin.math.roundToInt

object SstvSampleMath {
    fun msToSamples(durationMs: Double, sampleRate: Int): Int =
        (durationMs * sampleRate / 1000.0).roundToInt()

    fun headerSamples(sampleRate: Int): Int =
        2 * msToSamples(300.0, sampleRate) +
            msToSamples(10.0, sampleRate) +
            10 * msToSamples(30.0, sampleRate)

    fun transmissionSamples(mode: SstvMode, sampleRate: Int): Int = when (mode.spec.family) {
        SstvFamily.MARTIN -> {
            val sync = msToSamples(4.862, sampleRate)
            val porch = msToSamples(0.572, sampleRate)
            val sep = msToSamples(0.572, sampleRate)
            val scan = msToSamples(mode.timing.colorScanMs, sampleRate)
            mode.spec.height * (sync + porch + 3 * (sep + scan))
        }
        SstvFamily.SCOTTIE -> {
            val sync = msToSamples(9.0, sampleRate)
            val porch = msToSamples(1.5, sampleRate)
            val sep = msToSamples(1.5, sampleRate)
            val scan = msToSamples(mode.timing.colorScanMs, sampleRate)
            sync + mode.spec.height * (2 * sep + 3 * scan + sync + porch)
        }
        SstvFamily.PD -> {
            val sync = msToSamples(20.0, sampleRate)
            val porch = msToSamples(2.08, sampleRate)
            val scan = msToSamples(mode.timing.colorScanMs, sampleRate)
            (mode.spec.height / 2) * (sync + porch + 4 * scan)
        }
        SstvFamily.ROBOT -> when (mode) {
            SstvMode.ROBOT_36 -> {
                val sync = msToSamples(9.0, sampleRate)
                val syncPorch = msToSamples(3.0, sampleRate)
                val y = msToSamples(88.0, sampleRate)
                val sep = msToSamples(4.5, sampleRate)
                val porch = msToSamples(1.5, sampleRate)
                val chroma = msToSamples(44.0, sampleRate)
                mode.spec.height * (sync + syncPorch + y + sep + porch + chroma)
            }
            SstvMode.ROBOT_72 -> {
                val sync = msToSamples(9.0, sampleRate)
                val syncPorch = msToSamples(3.0, sampleRate)
                val y = msToSamples(138.0, sampleRate)
                val sep = msToSamples(4.5, sampleRate)
                val porch = msToSamples(1.5, sampleRate)
                val chroma = msToSamples(69.0, sampleRate)
                mode.spec.height * (sync + syncPorch + y + 2 * (sep + porch + chroma))
            }
            else -> error("Unsupported robot mode $mode")
        }
        SstvFamily.WRAASE -> {
            val sync = msToSamples(5.5225, sampleRate)
            val porch = msToSamples(0.5, sampleRate)
            val scan = msToSamples(mode.timing.colorScanMs, sampleRate)
            mode.spec.height * (sync + porch + 3 * scan)
        }
    }

    fun totalSamples(mode: SstvMode, sampleRate: Int): Int =
        headerSamples(sampleRate) + transmissionSamples(mode, sampleRate)
}
