package dev.virtualiors.sstvtx.core

import kotlin.math.roundToInt

data class PlaybackProgress(
    val playedSamples: Int,
    val totalSamples: Int,
    val elapsedSeconds: Double,
    val remainingSeconds: Double,
    val fraction: Double,
)

object ProgressEstimator {
    fun fromSamples(playedSamples: Int, totalSamples: Int, sampleRate: Int): PlaybackProgress {
        require(totalSamples >= 0) { "totalSamples cannot be negative" }
        require(sampleRate > 0) { "sampleRate must be positive" }
        val played = playedSamples.coerceIn(0, totalSamples)
        val elapsed = played / sampleRate.toDouble()
        val remaining = (totalSamples - played) / sampleRate.toDouble()
        val fraction = if (totalSamples == 0) 1.0 else played / totalSamples.toDouble()
        return PlaybackProgress(played, totalSamples, elapsed, remaining, fraction)
    }

    fun formatClock(seconds: Double): String {
        val total = seconds.roundToInt().coerceAtLeast(0)
        val minutes = total / 60
        val secs = total % 60
        return "%02d:%02d".format(minutes, secs)
    }
}
