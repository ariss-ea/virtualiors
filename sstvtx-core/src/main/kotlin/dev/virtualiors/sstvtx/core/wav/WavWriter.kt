package dev.virtualiors.sstvtx.core.wav

object WavWriter {
    fun writePcm16Mono(pcm16: ShortArray, sampleRate: Int): ByteArray {
        require(sampleRate > 0) { "sampleRate must be positive" }
        val dataBytes = pcm16.size * 2
        val out = ByteArray(44 + dataBytes)
        ascii(out, 0, "RIFF")
        le32(out, 4, 36 + dataBytes)
        ascii(out, 8, "WAVE")
        ascii(out, 12, "fmt ")
        le32(out, 16, 16)
        le16(out, 20, 1)
        le16(out, 22, 1)
        le32(out, 24, sampleRate)
        le32(out, 28, sampleRate * 2)
        le16(out, 32, 2)
        le16(out, 34, 16)
        ascii(out, 36, "data")
        le32(out, 40, dataBytes)
        var p = 44
        for (sample in pcm16) {
            le16(out, p, sample.toInt())
            p += 2
        }
        return out
    }

    private fun ascii(out: ByteArray, offset: Int, value: String) {
        for (i in value.indices) out[offset + i] = value[i].code.toByte()
    }

    private fun le16(out: ByteArray, offset: Int, value: Int) {
        out[offset] = (value and 0xff).toByte()
        out[offset + 1] = ((value ushr 8) and 0xff).toByte()
    }

    private fun le32(out: ByteArray, offset: Int, value: Int) {
        out[offset] = (value and 0xff).toByte()
        out[offset + 1] = ((value ushr 8) and 0xff).toByte()
        out[offset + 2] = ((value ushr 16) and 0xff).toByte()
        out[offset + 3] = ((value ushr 24) and 0xff).toByte()
    }
}
