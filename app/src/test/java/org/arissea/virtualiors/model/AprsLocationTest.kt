package org.arissea.virtualiors.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.arissea.virtualiors.transmission.AprsAddressingResolver
import org.arissea.virtualiors.transmission.AprsHeaderPresentation

class AprsLocationTest {
    @Test
    fun signedPresetCoordinatesAreValidatedWithoutInventingDefaults() {
        assertEquals(
            AprsCoordinates(-33.8688, 151.2093),
            AprsCoordinateValidator.parse("-33.8688", "+151.2093"),
        )
        assertNull(AprsCoordinateValidator.parse("", "151.2093"))
        assertNull(AprsCoordinateValidator.parse("-90.1", "151.2093"))
        assertNull(AprsCoordinateValidator.parse("-33.8688", "180.1"))
        assertNull(AprsCoordinateValidator.parse("NaN", "0"))
    }

    @Test
    fun coordinateErrorsDistinguishEmptyFormatAndRange() {
        assertEquals("Enter a latitude.", AprsCoordinateValidator.latitudeError(""))
        assertEquals("Enter a longitude.", AprsCoordinateValidator.longitudeError("  "))
        assertEquals("Enter a valid decimal number.", AprsCoordinateValidator.latitudeError("north"))
        assertEquals("Enter a valid decimal number.", AprsCoordinateValidator.longitudeError("1,25"))
        assertEquals("Latitude must be between -90 and 90.", AprsCoordinateValidator.latitudeError("90.1"))
        assertEquals("Longitude must be between -180 and 180.", AprsCoordinateValidator.longitudeError("-180.1"))
        assertNull(AprsCoordinateValidator.latitudeError("38.9943"))
        assertNull(AprsCoordinateValidator.longitudeError("-1.8585"))
    }

    @Test
    fun phoneGpsDefaultsToNoGpsLockAndCanUseAValidPresetFallback() {
        assertEquals(
            AprsPositionResolution.NoGpsLock,
            AprsPositionResolver.resolve(AprsConfig(), phoneLocation = null),
        )

        val fallback = AprsPositionResolver.resolve(
            AprsConfig(
                noGpsLockBehavior = AprsNoGpsLockBehavior.USE_PRESET_COORDINATES,
                presetLatitude = "51.5074",
                presetLongitude = "-0.1278",
            ),
            phoneLocation = null,
        )
        assertEquals(
            AprsCoordinates(51.5074, -0.1278),
            (fallback as AprsPositionResolution.Coordinates).value,
        )
    }

    @Test
    fun invalidRequiredPresetIsRejected() {
        val result = AprsPositionResolver.resolve(
            AprsConfig(positionSource = AprsPositionSource.PRESET_COORDINATES),
            phoneLocation = null,
        )
        assertTrue(result is AprsPositionResolution.Invalid)

        assertEquals(
            AprsPositionResolution.NoGpsLock,
            AprsPositionResolver.resolve(
                AprsConfig(),
                phoneLocation = 91.0 to 181.0,
            ),
        )
    }

    @Test
    fun disabledAprsHasNoHeaderPresentation() {
        assertNull(AprsHeaderPresentation.summaryOrNull(AppConfig()))
    }

    @Test
    fun aprsHeaderUsesConfiguredPathOrDirectWithoutUnicodeArrow() {
        val direct = AprsHeaderPresentation.summaryOrNull(
            AppConfig(aprs = AprsConfig(enabled = true)),
        )
        assertEquals("VIORS > CQ · DIRECT", direct)
        assertTrue(direct?.contains("→") == false)
        assertTrue(direct?.contains("->") == false)

        assertEquals(
            "EA4ABC > TEST · PATH: WIDE1-1,WIDE2-1",
            AprsHeaderPresentation.summaryOrNull(
                AppConfig(
                    callsign = "ea4abc",
                    aprsDestination = "test",
                    aprsPath = "wide1-1,wide2-1",
                    aprs = AprsConfig(enabled = true),
                ),
            ),
        )
        assertEquals(
            "VIORS > CQ · PATH: WIDE1-15",
            AprsAddressingResolver.resolve(
                AppConfig(callsign = "@@", aprsPath = "!,,,WIDE1-16"),
            ).headerSummary,
        )
    }
}
