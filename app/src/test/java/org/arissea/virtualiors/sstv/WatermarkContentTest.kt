package org.arissea.virtualiors.sstv

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WatermarkContentTest {
    @Test
    fun titleAndImageCounterMatchTheTransmittedWatermarkCopy() {
        assertEquals("ARISS VirtualIORS", WatermarkContent.TITLE)

        val details = WatermarkContent.imageDetails(
            callsign = "viors",
            showCallsign = true,
            showImageNumber = true,
            imageIndex = 1,
            imageCount = 12,
        )

        assertTrue(details.startsWith("VIORS"))
        assertTrue(details.endsWith("1/12"))
        assertFalse(details.contains("Image", ignoreCase = true))
        assertEquals("1/12", WatermarkContent.imageDetails("VIORS", false, true, 1, 12))
    }

    @Test
    fun cameraPreviewAndRendererShareTheSameUtcCopy() {
        val details = WatermarkContent.cameraDetails(
            callsign = "viors",
            showCallsign = true,
            showTimestamp = true,
            timestamp = Instant.parse("2026-07-11T09:08:07Z"),
        )

        assertEquals("VIORS  •  2026-07-11 09:08 UTC", details)
    }
}
