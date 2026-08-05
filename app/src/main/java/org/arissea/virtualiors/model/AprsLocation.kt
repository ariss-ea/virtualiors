package org.arissea.virtualiors.model

enum class AprsPositionSource {
    PHONE_GPS,
    PRESET_COORDINATES,
}

enum class AprsNoGpsLockBehavior {
    SEND_NO_GPS_LOCK,
    USE_PRESET_COORDINATES,
}

data class AprsCoordinates(
    val latitude: Double,
    val longitude: Double,
) {
    init {
        require(latitude.isFinite() && latitude in -90.0..90.0) { "Latitude must be between -90 and 90" }
        require(longitude.isFinite() && longitude in -180.0..180.0) { "Longitude must be between -180 and 180" }
    }
}

object AprsCoordinateValidator {
    private val signedDecimal = Regex("^[+-]?(?:\\d+(?:\\.\\d*)?|\\.\\d+)$")

    fun latitudeError(value: String): String? = coordinateError(
        value = value,
        range = -90.0..90.0,
        emptyMessage = "Enter a latitude.",
        rangeMessage = "Latitude must be between -90 and 90.",
    )

    fun longitudeError(value: String): String? = coordinateError(
        value = value,
        range = -180.0..180.0,
        emptyMessage = "Enter a longitude.",
        rangeMessage = "Longitude must be between -180 and 180.",
    )

    fun parse(latitude: String, longitude: String): AprsCoordinates? {
        if (latitudeError(latitude) != null || longitudeError(longitude) != null) return null
        return AprsCoordinates(
            latitude = latitude.trim().toDouble(),
            longitude = longitude.trim().toDouble(),
        )
    }

    private fun coordinateError(
        value: String,
        range: ClosedFloatingPointRange<Double>,
        emptyMessage: String,
        rangeMessage: String,
    ): String? {
        val trimmed = value.trim()
        if (trimmed.isEmpty()) return emptyMessage
        if (!signedDecimal.matches(trimmed)) return "Enter a valid decimal number."
        val number = trimmed.toDoubleOrNull()
        if (number == null || !number.isFinite()) return "Enter a valid decimal number."
        return if (number !in range) rangeMessage else null
    }
}

sealed interface AprsPositionResolution {
    data class Coordinates(val value: AprsCoordinates) : AprsPositionResolution
    data object NoGpsLock : AprsPositionResolution
    data class Invalid(val message: String) : AprsPositionResolution
}

object AprsPositionResolver {
    fun resolve(config: AprsConfig, phoneLocation: Pair<Double, Double>?): AprsPositionResolution {
        val preset = AprsCoordinateValidator.parse(config.presetLatitude, config.presetLongitude)
        if (config.positionSource == AprsPositionSource.PRESET_COORDINATES) {
            return preset?.let { AprsPositionResolution.Coordinates(it) }
                ?: AprsPositionResolution.Invalid("Enter valid preset coordinates in Settings.")
        }

        validPhoneCoordinates(phoneLocation)?.let {
            return AprsPositionResolution.Coordinates(it)
        }
        return when (config.noGpsLockBehavior) {
            AprsNoGpsLockBehavior.SEND_NO_GPS_LOCK -> AprsPositionResolution.NoGpsLock
            AprsNoGpsLockBehavior.USE_PRESET_COORDINATES -> {
                preset?.let { AprsPositionResolution.Coordinates(it) }
                    ?: AprsPositionResolution.Invalid("Enter valid preset coordinates in Settings.")
            }
        }
    }

    private fun validPhoneCoordinates(location: Pair<Double, Double>?): AprsCoordinates? {
        val (latitude, longitude) = location ?: return null
        return runCatching { AprsCoordinates(latitude, longitude) }.getOrNull()
    }
}
