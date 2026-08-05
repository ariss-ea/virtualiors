package org.arissea.virtualiors.transmission

import java.util.concurrent.atomic.AtomicReference
import org.arissea.virtualiors.model.AprsConfig
import org.arissea.virtualiors.model.AprsCoordinates
import org.arissea.virtualiors.model.AprsPositionResolution
import org.arissea.virtualiors.model.AprsPositionResolver
import org.arissea.virtualiors.model.AprsPositionSource

const val APRS_LOCATION_MAX_AGE_MS = 3 * 60 * 1_000L

data class AprsLocationFix(
    val coordinates: AprsCoordinates,
    val capturedAtElapsedRealtimeMs: Long,
) {
    init {
        require(capturedAtElapsedRealtimeMs >= 0) { "Location timestamp must be monotonic" }
    }
}

interface AprsLocationUpdateSource {
    /**
     * Starts asynchronous updates and returns whether location access is available.
     * Implementations must never wait for a fix before returning.
     */
    fun start(onLocation: (AprsLocationFix) -> Unit): Boolean

    fun stop()
}

class AprsLocationCache {
    private val latest = AtomicReference<AprsLocationFix?>(null)

    fun update(fix: AprsLocationFix) {
        while (true) {
            val previous = latest.get()
            if (previous != null && previous.capturedAtElapsedRealtimeMs > fix.capturedAtElapsedRealtimeMs) return
            if (latest.compareAndSet(previous, fix)) return
        }
    }

    fun recentOrNull(
        nowElapsedRealtimeMs: Long,
        maxAgeMs: Long = APRS_LOCATION_MAX_AGE_MS,
    ): AprsCoordinates? {
        val fix = latest.get() ?: return null
        val ageMs = nowElapsedRealtimeMs - fix.capturedAtElapsedRealtimeMs
        return fix.coordinates.takeIf { ageMs in 0..maxAgeMs }
    }

    fun clear() {
        latest.set(null)
    }
}

class AprsLocationSession(
    private val source: AprsLocationUpdateSource,
    private val elapsedRealtimeMs: () -> Long,
    private val cache: AprsLocationCache = AprsLocationCache(),
) {
    private var tracking = false

    @Synchronized
    fun start(config: AprsConfig) {
        val needsPhoneGps = config.enabled &&
            config.gpsPositionEnabled &&
            config.positionSource == AprsPositionSource.PHONE_GPS
        if (!needsPhoneGps) {
            stop()
            return
        }
        if (tracking) return

        tracking = source.start(cache::update)
        if (!tracking) cache.clear()
    }

    @Synchronized
    fun stop() {
        if (!tracking) return
        source.stop()
        tracking = false
    }

    fun resolve(config: AprsConfig): AprsPositionResolution {
        val phoneLocation = if (config.positionSource == AprsPositionSource.PHONE_GPS) {
            cache.recentOrNull(elapsedRealtimeMs())?.let { it.latitude to it.longitude }
        } else {
            null
        }
        return AprsPositionResolver.resolve(config, phoneLocation)
    }
}
