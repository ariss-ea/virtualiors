package dev.pathfinder.aprstx.core.ax25

import kotlin.math.ceil
import kotlin.math.max

/** HDLC framing configuration for AX.25 AFSK transmission. */
data class HdlcConfig(
    /** Seconds of 0x7E flags before data. Useful for VOX and manual PTT. */
    val preambleSeconds: Double = 0.65,
    /** Seconds of 0x7E flags after data. */
    val tailSeconds: Double = 0.20,
    val baud: Int = 1200,
) {
    init {
        require(preambleSeconds >= 0.0) { "preambleSeconds must be >= 0" }
        require(tailSeconds >= 0.0) { "tailSeconds must be >= 0" }
        require(baud > 0) { "baud must be > 0" }
    }
}

/**
 * Produces NRZI encoded tone-select bits for AX.25 HDLC.
 * false/true are later mapped to space/mark tones by the AFSK modulator.
 */
class HdlcEncoder(private val config: HdlcConfig = HdlcConfig()) {
    fun encode(frameWithoutFcs: ByteArray): BooleanArray {
        val frame = Fcs.append(frameWithoutFcs)
        val sink = NrziBitSink()
        val preambleFlags = max(1, ceil(config.preambleSeconds * config.baud / 8.0).toInt())
        val tailFlags = max(1, ceil(config.tailSeconds * config.baud / 8.0).toInt())

        repeat(preambleFlags) { sink.sendFlag() }
        frame.forEach { sink.sendDataByte(it.toInt() and 0xFF) }
        repeat(tailFlags) { sink.sendFlag() }

        return sink.toBooleanArray()
    }

    /** Primarily for tests and diagnostics: HDLC data bytes with FCS, before bit stuffing and NRZI. */
    fun frameWithFcs(frameWithoutFcs: ByteArray): ByteArray = Fcs.append(frameWithoutFcs)

    private class NrziBitSink {
        private val bits = ArrayList<Boolean>(2048)
        private var ones = 0
        private var nrziState = false

        fun sendFlag() {
            sendByteNoStuff(0x7E)
            ones = 0
        }

        private fun sendByteNoStuff(value: Int) {
            for (i in 0 until 8) {
                sendNrziBit(((value ushr i) and 1) == 1)
            }
        }

        fun sendDataByte(value: Int) {
            for (i in 0 until 8) {
                val bit = ((value ushr i) and 1) == 1
                sendNrziBit(bit)
                if (bit) {
                    ones++
                    if (ones == 5) {
                        sendNrziBit(false) // bit stuffing: insert a zero after five consecutive ones
                        ones = 0
                    }
                } else {
                    ones = 0
                }
            }
        }

        private fun sendNrziBit(dataBit: Boolean) {
            if (!dataBit) nrziState = !nrziState
            bits += nrziState
        }

        fun toBooleanArray(): BooleanArray = BooleanArray(bits.size) { bits[it] }
    }
}
