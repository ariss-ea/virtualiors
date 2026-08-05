package dev.pathfinder.aprstx.core

import dev.pathfinder.aprstx.core.aprs.AprsPayloads
import dev.pathfinder.aprstx.core.ax25.Ax25UiFrame
import dev.pathfinder.aprstx.core.ax25.Callsign
import dev.pathfinder.aprstx.core.ax25.Fcs
import dev.pathfinder.aprstx.core.ax25.HdlcConfig
import dev.pathfinder.aprstx.core.modem.AfskConfig
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CoreUnitTest {
    @Test
    fun callsignAddressEncodingMatchesAx25Layout() {
        val encoded = Ax25UiFrame.encodeAddress(Callsign.parse("RS0ISS"), last = false)
        assertArrayEquals(
            byteArrayOf(0xA4.toByte(), 0xA6.toByte(), 0x60, 0x92.toByte(), 0xA6.toByte(), 0xA6.toByte(), 0x60),
            encoded,
        )
        val source = Ax25UiFrame.encodeAddress(Callsign.parse("EA5KAB-10"), last = true)
        assertEquals(0x75, source[6].toInt() and 0xFF)
    }

    @Test
    fun positionPayloadCanMatchIssStyle() {
        val payload = AprsPayloads.issLikePosition(38.6666667, -2.4833333)
        assertTrue(payload.startsWith("!3840.00NR00229.00W&ARISS"))
    }

    @Test
    fun fcsAppendedFrameVerifies() {
        val frame = Ax25UiFrame(
            destination = Callsign.parse("APDW18"),
            source = Callsign.parse("EA5KAB-10"),
            repeaters = listOf(Callsign.parse("ARISS")),
            information = AprsPayloads.issLikePosition(38.6666667, -2.4833333),
        ).toBytes()
        val withFcs = Fcs.append(frame)
        assertTrue(Fcs.verify(withFcs))
    }

    @Test
    fun monitorHeaderUsesTnc2PathWithoutDisplaySpaces() {
        val frame = Ax25UiFrame(
            destination = Callsign.parse("CQ"),
            source = Callsign.parse("VIORS"),
            repeaters = listOf(Callsign.parse("WIDE1-1"), Callsign.parse("WIDE2-1")),
            information = ">VirtualIORS",
        )
        assertEquals("VIORS>CQ,WIDE1-1,WIDE2-1:>VirtualIORS", frame.monitorString())
    }

    @Test
    fun modemProducesPcmAndWav() {
        val modem = AprsTxModem(
            packetConfig = AprsTxModem.PacketConfig(
                source = Callsign.parse("EA5KAB-10"),
                destination = Callsign.parse("APDW18"),
                path = listOf(Callsign.parse("ARISS")),
            ),
            hdlcConfig = HdlcConfig(preambleSeconds = 0.20, tailSeconds = 0.10),
            afskConfig = AfskConfig(sampleRate = 48_000, amplitude = 0.25),
        )
        val tx = modem.synthesize(AprsPayloads.issLikePosition(38.6666667, -2.4833333))
        assertTrue(tx.nrziToneBits.isNotEmpty())
        assertTrue(tx.pcm16.isNotEmpty())
        assertEquals('R', tx.wavBytes()[0].toInt().toChar())
        assertEquals('I', tx.wavBytes()[1].toInt().toChar())
        assertTrue(Fcs.verify(tx.ax25FrameWithFcs))
    }
}
