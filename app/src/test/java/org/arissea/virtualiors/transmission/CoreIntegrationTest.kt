package org.arissea.virtualiors.transmission

import dev.virtualiors.sstvtx.core.image.AspectCropper
import org.arissea.virtualiors.model.AppConfig
import org.arissea.virtualiors.model.AprsConfig
import org.arissea.virtualiors.model.AprsCoordinates
import org.arissea.virtualiors.model.AprsPositionResolution
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CoreIntegrationTest {
    @Test
    fun aprsDefaultsProduceViorsToCqBeacon() {
        val packet = AprsTransmissionBuilder().build(
            kind = BlockKind.APRS_BEACON,
            config = AppConfig(aprs = AprsConfig(enabled = true)),
            imageCounter = 1,
            telemetry = DeviceTelemetry(80, false),
        )!!
        assertTrue(packet.monitor.startsWith("VIORS>CQ:>VIORS"))
        assertTrue(packet.pcm16.isNotEmpty())
        assertEquals(TransmissionScheduler.DEFAULT_APRS_PREAMBLE_SECONDS, packet.preambleSeconds, 0.0)
    }

    @Test
    fun globalVoxHelpUsesOneSecondAprsPreambleWithoutExtraTone() {
        val builder = AprsTransmissionBuilder()
        val normal = builder.build(
            BlockKind.APRS_BEACON,
            AppConfig(aprs = AprsConfig(enabled = true)),
            1,
            DeviceTelemetry(80, false),
        )!!
        val assisted = builder.build(
            BlockKind.APRS_BEACON,
            AppConfig(globalVoxTone = true, aprs = AprsConfig(enabled = true)),
            1,
            DeviceTelemetry(80, false),
        )!!
        assertEquals(1.0, assisted.preambleSeconds, 0.0)
        assertTrue(assisted.pcm16.size > normal.pcm16.size)
    }

    @Test
    fun aprsPositionUsesShortCommentAndNoLockUsesExactStatusText() {
        val builder = AprsTransmissionBuilder()
        val coordinates = builder.build(
            kind = BlockKind.APRS_GPS_POSITION,
            config = AppConfig(aprs = AprsConfig(enabled = true, gpsPositionEnabled = true)),
            imageCounter = 1,
            telemetry = DeviceTelemetry(80, false),
            position = AprsPositionResolution.Coordinates(AprsCoordinates(40.4168, -3.7038)),
        )!!
        assertTrue(coordinates.payload.endsWith("VirtualIORS"))
        assertTrue(!coordinates.payload.contains("classroom position"))

        val noLock = builder.build(
            kind = BlockKind.APRS_GPS_POSITION,
            config = AppConfig(aprs = AprsConfig(enabled = true, gpsPositionEnabled = true)),
            imageCounter = 1,
            telemetry = DeviceTelemetry(80, false),
            position = AprsPositionResolution.NoGpsLock,
        )!!
        assertEquals(">No GPS Lock", noLock.payload)
        assertTrue(noLock.monitor.endsWith(":>No GPS Lock"))
    }

    @Test
    fun blankCustomMessageIsNotBuiltAndConfiguredPathUsesAx25Header() {
        val builder = AprsTransmissionBuilder()
        val blank = builder.build(
            kind = BlockKind.APRS_TEXT,
            config = AppConfig(aprs = AprsConfig(enabled = true, customTextEnabled = true)),
            imageCounter = 1,
            telemetry = DeviceTelemetry(80, false),
        )
        assertEquals(null, blank)

        val routed = builder.build(
            kind = BlockKind.APRS_BEACON,
            config = AppConfig(
                aprs = AprsConfig(enabled = true),
                aprsPath = "WIDE1-1,WIDE2-1",
            ),
            imageCounter = 1,
            telemetry = DeviceTelemetry(80, false),
        )!!
        assertTrue(routed.monitor, routed.monitor.startsWith("VIORS>CQ,WIDE1-1,WIDE2-1:"))

        val sanitizedConfig = AppConfig(callsign = "@@", aprsPath = "!,,,WIDE1-16")
        val sanitized = builder.build(
            kind = BlockKind.APRS_BEACON,
            config = sanitizedConfig,
            imageCounter = 1,
            telemetry = DeviceTelemetry(80, false),
        )!!
        assertEquals(
            "VIORS > CQ · PATH: WIDE1-15",
            AprsAddressingResolver.resolve(sanitizedConfig).headerSummary,
        )
        assertTrue(sanitized.monitor.startsWith("VIORS>CQ,WIDE1-15:"))
    }

    @Test
    fun centerCropCoversRequiredRobotAndPd120Shapes() {
        val wideRobot = AspectCropper.computeCenterCrop(1920, 1080, 320, 240)
        assertEquals(1440, wideRobot.width)
        assertEquals(1080, wideRobot.height)

        val verticalPd = AspectCropper.computeCenterCrop(1080, 1920, 640, 496)
        assertEquals(1080, verticalPd.width)
        assertTrue(verticalPd.height < 1920)

        val squarePd = AspectCropper.computeCenterCrop(1000, 1000, 640, 496)
        assertEquals(1000, squarePd.width)
        assertTrue(squarePd.height < 1000)

        assertEquals(0, AspectCropper.computeCenterCrop(640, 496, 640, 496).left)
    }

    @Test
    fun timeFormattingCoversElapsedRemainingAndDurationValues() {
        assertEquals("00:00", TimeFormatter.formatMillis(0))
        assertEquals("01:05", TimeFormatter.formatMillis(65_000))
        assertEquals("1:01:01", TimeFormatter.formatMillis(3_661_000))
    }
}
