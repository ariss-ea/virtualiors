package dev.pathfinder.aprstx.core.ax25

/** AX.25/HDLC FCS: CRC-16/X-25, reflected, init FFFF, xorout FFFF. */
object Fcs {
    private const val POLY_REVERSED = 0x8408
    private const val GOOD_RAW_FCS = 0xF0B8

    fun compute(data: ByteArray): Int {
        var crc = 0xFFFF
        for (b in data) {
            var value = b.toInt() and 0xFF
            repeat(8) {
                val mix = (crc xor value) and 0x01
                crc = crc ushr 1
                if (mix != 0) crc = crc xor POLY_REVERSED
                value = value ushr 1
            }
        }
        return (crc xor 0xFFFF) and 0xFFFF
    }

    fun append(data: ByteArray): ByteArray {
        val fcs = compute(data)
        return data + byteArrayOf((fcs and 0xFF).toByte(), ((fcs ushr 8) and 0xFF).toByte())
    }

    /** Verifies a frame that already includes the two FCS bytes, little-endian. */
    fun verify(dataWithFcs: ByteArray): Boolean {
        var crc = 0xFFFF
        for (b in dataWithFcs) {
            var value = b.toInt() and 0xFF
            repeat(8) {
                val mix = (crc xor value) and 0x01
                crc = crc ushr 1
                if (mix != 0) crc = crc xor POLY_REVERSED
                value = value ushr 1
            }
        }
        return (crc and 0xFFFF) == GOOD_RAW_FCS
    }
}
