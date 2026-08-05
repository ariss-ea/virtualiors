package dev.pathfinder.aprstx.core.ax25

import java.nio.charset.Charset

private val APRS_CHARSET: Charset = Charsets.ISO_8859_1

/**
 * AX.25 Unnumbered Information frame for APRS.
 *
 * The returned byte array is the AX.25 frame body before FCS/HDLC flags:
 * address fields + control 0x03 + PID 0xF0 + information bytes.
 */
data class Ax25UiFrame(
    val destination: Callsign,
    val source: Callsign,
    val repeaters: List<Callsign> = emptyList(),
    val information: String,
    val commandFrame: Boolean = false,
) {
    init {
        require(repeaters.size <= 8) { "AX.25 supports at most 8 repeater address fields" }
    }

    fun toBytes(): ByteArray {
        val addresses = buildList {
            add(destination)
            add(source)
            addAll(repeaters)
        }
        val out = ArrayList<Byte>(addresses.size * 7 + 2 + information.length)
        addresses.forEachIndexed { index, callsign ->
            val last = index == addresses.lastIndex
            val isDestination = index == 0
            out.addAll(encodeAddress(callsign, last, setCommandBit = commandFrame && isDestination).asList())
        }
        out += 0x03.toByte() // UI frame
        out += 0xF0.toByte() // No layer 3 protocol
        information.toByteArray(APRS_CHARSET).forEach { out += it }
        return out.toByteArray()
    }

    fun monitorString(): String {
        val via = if (repeaters.isEmpty()) {
            ""
        } else {
            repeaters.joinToString(separator = ",", prefix = ",") { it.format() }
        }
        return "${source.format()}>${destination.format()}$via:$information"
    }

    companion object {
        fun encodeAddress(callsign: Callsign, last: Boolean, setCommandBit: Boolean = false): ByteArray {
            val result = ByteArray(7)
            val base = callsign.normalizedBase.padEnd(6, ' ')
            for (i in 0 until 6) {
                result[i] = (base[i].code shl 1).toByte()
            }
            var ssidByte = 0x60 or ((callsign.ssid and 0x0F) shl 1)
            if (setCommandBit) ssidByte = ssidByte or 0x80
            if (last) ssidByte = ssidByte or 0x01
            result[6] = ssidByte.toByte()
            return result
        }
    }
}
