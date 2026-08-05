package dev.pathfinder.aprstx.core.wav

import java.io.ByteArrayOutputStream

object WavWriter {
    fun pcm16MonoToWav(samples: ShortArray, sampleRate: Int): ByteArray {
        val byteRate = sampleRate * 2
        val dataSize = samples.size * 2
        val out = ByteArrayOutputStream(44 + dataSize)
        out.writeAscii("RIFF")
        out.writeLe32(36 + dataSize)
        out.writeAscii("WAVE")
        out.writeAscii("fmt ")
        out.writeLe32(16) // PCM chunk
        out.writeLe16(1)  // PCM format
        out.writeLe16(1)  // mono
        out.writeLe32(sampleRate)
        out.writeLe32(byteRate)
        out.writeLe16(2)  // block align
        out.writeLe16(16) // bits/sample
        out.writeAscii("data")
        out.writeLe32(dataSize)
        samples.forEach { out.writeLe16(it.toInt()) }
        return out.toByteArray()
    }

    private fun ByteArrayOutputStream.writeAscii(text: String) = write(text.toByteArray(Charsets.US_ASCII))
    private fun ByteArrayOutputStream.writeLe16(value: Int) {
        write(value and 0xFF)
        write((value ushr 8) and 0xFF)
    }
    private fun ByteArrayOutputStream.writeLe32(value: Int) {
        write(value and 0xFF)
        write((value ushr 8) and 0xFF)
        write((value ushr 16) and 0xFF)
        write((value ushr 24) and 0xFF)
    }
}
