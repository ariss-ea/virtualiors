package org.arissea.virtualiors.transmission

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class VoxToneTest {
    @Test
    fun generatedPcmToneIsOneSecondAt1900Hz() {
        val sampleRate = 38_000
        val tone = VoxTone.pcm16(sampleRate)

        assertEquals(sampleRate, tone.size)
        assertEquals(1_900.0, VoxTone.FREQUENCY_HZ, 0.0)
        assertTrue(tone[5] > 14_000)
        assertTrue(kotlin.math.abs(tone[10].toInt()) <= 1)
        assertTrue(tone[15] < -14_000)
    }

    @Test
    fun enabledPrependAddsGeneratedToneAndKeepsImagePcmUnchanged() {
        val imagePcm = shortArrayOf(120, -340, 560, -780)
        val sampleRate = 8_000

        val output = VoxTone.prependIfEnabled(imagePcm, sampleRate, enabled = true)

        assertEquals(sampleRate + imagePcm.size, output.size)
        assertArrayEquals(VoxTone.pcm16(sampleRate), output.copyOfRange(0, sampleRate))
        assertArrayEquals(imagePcm, output.copyOfRange(sampleRate, output.size))
    }

    @Test
    fun disabledPrependReturnsOriginalPcm() {
        val pcm = shortArrayOf(1, 2, 3)
        assertSame(pcm, VoxTone.prependIfEnabled(pcm, 8_000, enabled = false))
    }
}
