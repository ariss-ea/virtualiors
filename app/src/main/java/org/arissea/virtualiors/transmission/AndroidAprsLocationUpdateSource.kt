package org.arissea.virtualiors.transmission

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.SystemClock
import androidx.core.content.ContextCompat
import androidx.core.location.LocationListenerCompat
import androidx.core.location.LocationManagerCompat
import androidx.core.location.LocationRequestCompat
import org.arissea.virtualiors.model.AprsCoordinates

internal class AndroidAprsLocationUpdateSource(context: Context) : AprsLocationUpdateSource {
    private val appContext = context.applicationContext
    private val locationManager = appContext.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    private val request = LocationRequestCompat.Builder(LOCATION_UPDATE_INTERVAL_MS)
        .setMinUpdateDistanceMeters(0f)
        .build()

    @Volatile
    private var onLocation: ((AprsLocationFix) -> Unit)? = null
    private var registered = false

    private val listener = object : LocationListenerCompat {
        override fun onLocationChanged(location: Location) {
            val coordinates = runCatching {
                AprsCoordinates(location.latitude, location.longitude)
            }.getOrNull() ?: return
            val timestampMs = location.elapsedRealtimeNanos
                .takeIf { it > 0L }
                ?.div(1_000_000L)
                ?: SystemClock.elapsedRealtime()
            onLocation?.invoke(AprsLocationFix(coordinates, timestampMs))
        }
    }

    @SuppressLint("MissingPermission")
    @Synchronized
    override fun start(onLocation: (AprsLocationFix) -> Unit): Boolean {
        this.onLocation = onLocation
        if (registered) return true

        val fine = ContextCompat.checkSelfPermission(
            appContext,
            Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(
            appContext,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
        if (!fine && !coarse) {
            this.onLocation = null
            return false
        }

        val providers = buildList {
            if (fine && isEnabled(LocationManager.GPS_PROVIDER)) add(LocationManager.GPS_PROVIDER)
            if ((fine || coarse) && isEnabled(LocationManager.NETWORK_PROVIDER)) add(LocationManager.NETWORK_PROVIDER)
        }
        if (providers.isEmpty()) return true

        val executor = ContextCompat.getMainExecutor(appContext)
        var registeredAny = false
        providers.forEach { provider ->
            val registeredProvider = runCatching {
                LocationManagerCompat.requestLocationUpdates(
                    locationManager,
                    provider,
                    request,
                    executor,
                    listener,
                )
            }.isSuccess
            registeredAny = registeredAny || registeredProvider
        }
        registered = registeredAny
        if (!registered) this.onLocation = null
        return registered
    }

    @Synchronized
    override fun stop() {
        onLocation = null
        if (registered) {
            runCatching { LocationManagerCompat.removeUpdates(locationManager, listener) }
        }
        registered = false
    }

    private fun isEnabled(provider: String): Boolean =
        runCatching { locationManager.isProviderEnabled(provider) }.getOrDefault(false)

    private companion object {
        const val LOCATION_UPDATE_INTERVAL_MS = 30_000L
    }
}
