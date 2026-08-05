package dev.pathfinder.aprstx.core

import dev.pathfinder.aprstx.core.ax25.Ax25UiFrame
import dev.pathfinder.aprstx.core.ax25.Callsign
import dev.pathfinder.aprstx.core.ax25.HdlcConfig
import dev.pathfinder.aprstx.core.ax25.HdlcEncoder
import dev.pathfinder.aprstx.core.modem.AfskConfig
import dev.pathfinder.aprstx.core.modem.AfskModulator
import dev.pathfinder.aprstx.core.wav.WavWriter

/** Complete TX-only APRS/AX.25/AFSK modem. */
class AprsTxModem(
    private val packetConfig: PacketConfig,
    private val hdlcConfig: HdlcConfig = HdlcConfig(),
    private val afskConfig: AfskConfig = AfskConfig(),
) {
    fun synthesize(payload: String): Transmission {
        val frame = Ax25UiFrame(
            destination = packetConfig.destination,
            source = packetConfig.source,
            repeaters = packetConfig.path,
            information = payload,
            commandFrame = packetConfig.commandFrame,
        )
        val frameBytes = frame.toBytes()
        val hdlc = HdlcEncoder(hdlcConfig)
        val bits = hdlc.encode(frameBytes)
        val pcm = AfskModulator(afskConfig).modulate(bits)
        return Transmission(
            monitor = frame.monitorString(),
            payload = payload,
            ax25FrameWithoutFcs = frameBytes,
            ax25FrameWithFcs = hdlc.frameWithFcs(frameBytes),
            nrziToneBits = bits,
            pcm16 = pcm,
            sampleRate = afskConfig.sampleRate,
        )
    }

    data class PacketConfig(
        val source: Callsign,
        val destination: Callsign = Callsign.parse("APZPTX"),
        val path: List<Callsign> = emptyList(),
        val commandFrame: Boolean = false,
    )
}

data class Transmission(
    val monitor: String,
    val payload: String,
    val ax25FrameWithoutFcs: ByteArray,
    val ax25FrameWithFcs: ByteArray,
    val nrziToneBits: BooleanArray,
    val pcm16: ShortArray,
    val sampleRate: Int,
) {
    fun wavBytes(): ByteArray = WavWriter.pcm16MonoToWav(pcm16, sampleRate)
}
