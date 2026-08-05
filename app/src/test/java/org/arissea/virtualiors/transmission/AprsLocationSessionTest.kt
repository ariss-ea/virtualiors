package org.arissea.virtualiors.transmission

import org.arissea.virtualiors.model.AppConfig
import org.arissea.virtualiors.model.AprsConfig
import org.arissea.virtualiors.model.AprsCoordinates
import org.arissea.virtualiors.model.AprsNoGpsLockBehavior
import org.arissea.virtualiors.model.AprsPositionResolution
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AprsLocationSessionTest {
    @Test
    fun recentCachedLocationIsSelectedImmediately() {
        val fixture = Fixture(nowMs = 10_000)
        fixture.session.start(fixture.config)
        fixture.source.emit(AprsCoordinates(40.4168, -3.7038), capturedAtMs = 9_000)

        assertEquals(
            AprsPositionResolution.Coordinates(AprsCoordinates(40.4168, -3.7038)),
            fixture.session.resolve(fixture.config),
        )
    }

    @Test
    fun missingOrStaleLocationImmediatelyUsesNoGpsLock() {
        val fixture = Fixture(nowMs = 10_000)
        fixture.session.start(fixture.config)
        assertEquals(AprsPositionResolution.NoGpsLock, fixture.session.resolve(fixture.config))

        fixture.source.emit(AprsCoordinates(40.4168, -3.7038), capturedAtMs = 10_000)
        fixture.clock.nowMs += APRS_LOCATION_MAX_AGE_MS + 1
        assertEquals(AprsPositionResolution.NoGpsLock, fixture.session.resolve(fixture.config))
    }

    @Test
    fun missingLocationImmediatelyUsesValidPresetFallback() {
        val fixture = Fixture(nowMs = 10_000)
        val config = fixture.config.copy(
            noGpsLockBehavior = AprsNoGpsLockBehavior.USE_PRESET_COORDINATES,
            presetLatitude = "51.5074",
            presetLongitude = "-0.1278",
        )
        fixture.session.start(config)

        assertEquals(
            AprsPositionResolution.Coordinates(AprsCoordinates(51.5074, -0.1278)),
            fixture.session.resolve(config),
        )
    }

    @Test
    fun lateLocationOnlyChangesTheNextPacketDecision() {
        val fixture = Fixture(nowMs = 10_000)
        fixture.session.start(fixture.config)
        val currentDecision = fixture.session.resolve(fixture.config)
        val currentPacket = AprsTransmissionBuilder().build(
            kind = BlockKind.APRS_GPS_POSITION,
            config = AppConfig(aprs = fixture.config),
            imageCounter = 1,
            telemetry = DeviceTelemetry(80, false),
            position = currentDecision,
        )!!

        fixture.source.emit(AprsCoordinates(48.8566, 2.3522), capturedAtMs = 10_000)
        val nextDecision = fixture.session.resolve(fixture.config)
        val nextPacket = AprsTransmissionBuilder().build(
            kind = BlockKind.APRS_GPS_POSITION,
            config = AppConfig(aprs = fixture.config),
            imageCounter = 2,
            telemetry = DeviceTelemetry(80, false),
            position = nextDecision,
        )!!

        assertEquals(">No GPS Lock", currentPacket.payload)
        assertEquals(AprsPositionResolution.NoGpsLock, currentDecision)
        assertEquals(
            AprsPositionResolution.Coordinates(AprsCoordinates(48.8566, 2.3522)),
            nextDecision,
        )
        assertFalse(nextPacket.payload.contains("No GPS Lock"))
    }

    @Test
    fun pendingProviderDoesNotGatePacketSelectionOrSchedulerCycles() {
        val fixture = Fixture(nowMs = 10_000)
        fixture.session.start(fixture.config)
        val scheduler = TransmissionScheduler()
        val appConfig = AppConfig(aprs = fixture.config.copy(everyImages = 1))

        val cycles = (1..3).map { imageNumber ->
            val decision = fixture.session.resolve(fixture.config)
            val plan = scheduler.planCycle(imageNumber, appConfig)
            decision to plan.map(TransmissionBlock::kind)
        }

        assertTrue(cycles.all { it.first == AprsPositionResolution.NoGpsLock })
        assertTrue(cycles.all { (_, kinds) -> BlockKind.SSTV_IMAGE in kinds && BlockKind.APRS_GPS_POSITION in kinds })
        assertEquals(1, fixture.source.startCount)
    }

    @Test
    fun startAndStopAreIdempotentAndRemoveTheListener() {
        val fixture = Fixture(nowMs = 10_000)
        fixture.session.start(fixture.config)
        fixture.session.start(fixture.config)
        assertTrue(fixture.source.active)
        assertEquals(1, fixture.source.startCount)

        fixture.session.stop()
        fixture.session.stop()
        assertFalse(fixture.source.active)
        assertEquals(1, fixture.source.stopCount)
    }

    @Test
    fun olderCallbackCannotReplaceANewerCachedFix() {
        val fixture = Fixture(nowMs = 10_000)
        fixture.session.start(fixture.config)
        fixture.source.emit(AprsCoordinates(52.52, 13.405), capturedAtMs = 9_500)
        fixture.source.emit(AprsCoordinates(40.4168, -3.7038), capturedAtMs = 9_000)

        assertEquals(
            AprsPositionResolution.Coordinates(AprsCoordinates(52.52, 13.405)),
            fixture.session.resolve(fixture.config),
        )
    }

    private class Fixture(nowMs: Long) {
        val source = FakeLocationSource()
        val clock = FakeClock(nowMs)
        val session = AprsLocationSession(source, clock::elapsedRealtime)
        val config = AprsConfig(
            enabled = true,
            gpsPositionEnabled = true,
        )
    }

    private class FakeClock(var nowMs: Long) {
        fun elapsedRealtime(): Long = nowMs
    }

    private class FakeLocationSource : AprsLocationUpdateSource {
        private var listener: ((AprsLocationFix) -> Unit)? = null
        var active = false
            private set
        var startCount = 0
            private set
        var stopCount = 0
            private set

        override fun start(onLocation: (AprsLocationFix) -> Unit): Boolean {
            startCount += 1
            listener = onLocation
            active = true
            return true
        }

        override fun stop() {
            stopCount += 1
            listener = null
            active = false
        }

        fun emit(coordinates: AprsCoordinates, capturedAtMs: Long) {
            listener?.invoke(AprsLocationFix(coordinates, capturedAtMs))
        }
    }
}
