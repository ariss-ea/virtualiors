package dev.virtualiors.sstvtx.core

import dev.virtualiors.sstvtx.core.image.AspectCropper
import dev.virtualiors.sstvtx.core.image.ImageResampler
import dev.virtualiors.sstvtx.core.wav.WavWriter
import dev.virtualiors.sstvtx.core.yuv.YuvConverter
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class SstvCoreTest {
    @Test
    fun modesContainExactlyExpectedModes() {
        val expected = listOf(
            "martin1", "martin2", "pd50", "pd90", "pd120", "pd160", "pd180", "pd240", "pd290",
            "scottie1", "scottie2", "scottiedx", "robot36", "robot72", "wraase-sc2-180",
        )
        assertEquals(expected, SstvModes.all.map { it.id })
        assertEquals("robot36", SstvModes.default.id)
    }

    @Test
    fun eachModeHasResolutionAndVisFromOriginalEncoder() {
        val table = mapOf(
            "martin1" to Triple(320 to 256, 44, SstvFamily.MARTIN),
            "martin2" to Triple(320 to 256, 40, SstvFamily.MARTIN),
            "pd50" to Triple(320 to 256, 93, SstvFamily.PD),
            "pd90" to Triple(320 to 256, 99, SstvFamily.PD),
            "pd120" to Triple(640 to 496, 95, SstvFamily.PD),
            "pd160" to Triple(512 to 400, 98, SstvFamily.PD),
            "pd180" to Triple(640 to 496, 96, SstvFamily.PD),
            "pd240" to Triple(640 to 496, 97, SstvFamily.PD),
            "pd290" to Triple(800 to 616, 94, SstvFamily.PD),
            "scottie1" to Triple(320 to 256, 60, SstvFamily.SCOTTIE),
            "scottie2" to Triple(320 to 256, 56, SstvFamily.SCOTTIE),
            "scottiedx" to Triple(320 to 256, 76, SstvFamily.SCOTTIE),
            "robot36" to Triple(320 to 240, 8, SstvFamily.ROBOT),
            "robot72" to Triple(320 to 240, 12, SstvFamily.ROBOT),
            "wraase-sc2-180" to Triple(320 to 256, 55, SstvFamily.WRAASE),
        )
        for ((id, expected) in table) {
            val spec = SstvModes.byId(id)
            assertEquals(expected.first.first, spec.width)
            assertEquals(expected.first.second, spec.height)
            assertEquals(expected.second, spec.visCode)
            assertEquals(expected.third, spec.family)
        }
    }

    @Test
    fun headerSamplesFollowVisTiming() {
        assertEquals(910, SstvSampleMath.headerSamples(1_000))
        assertEquals(40_131, SstvSampleMath.headerSamples(44_100))
    }

    @Test
    fun durationSamplesAreStableForEveryMode() {
        val expectedAt8000 = mapOf(
            "martin1" to 921_712,
            "martin2" to 472_432,
            "pd50" to 404_720,
            "pd90" to 727_280,
            "pd120" to 1_016_392,
            "pd160" to 1_294_680,
            "pd180" to 1_503_464,
            "pd240" to 1_991_528,
            "pd290" to 2_316_356,
            "scottie1" to 884_408,
            "scottie2" to 576_440,
            "scottiedx" to 2_158_520,
            "robot36" to 295_280,
            "robot72" to 583_280,
            "wraase-sc2-180" to 1_463_408,
        )
        for (mode in SstvMode.entries) {
            assertEquals(mode.spec.id, expectedAt8000.getValue(mode.spec.id), SstvSampleMath.totalSamples(mode, 8000))
            assertTrue(SstvModes.byId(mode.spec.id).nominalDurationSeconds > 30.0)
        }
    }

    @Test
    fun encoderGeneratesPcmWithoutFullScaleClipping() {
        val mode = SstvMode.ROBOT_36
        val image = gradient(mode.spec.width, mode.spec.height)
        val tx = SstvEncoder(SstvEncoderConfig(sampleRate = 8_000, amplitude = 0.75)).encode(image, mode)
        assertEquals(SstvSampleMath.totalSamples(mode, 8_000), tx.pcm16.size)
        assertTrue(tx.pcm16.any { it != 0.toShort() })
        assertTrue(tx.pcm16.none { it == Short.MAX_VALUE || it == Short.MIN_VALUE })
    }

    @Test
    fun wavWriterProducesValidRiffWaveHeader() {
        val pcm = shortArrayOf(-10, 0, 10)
        val wav = WavWriter.writePcm16Mono(pcm, 44_100)
        assertEquals(50, wav.size)
        assertEquals("RIFF", wav.decodeToString(0, 4))
        assertEquals("WAVE", wav.decodeToString(8, 12))
        assertEquals("fmt ", wav.decodeToString(12, 16))
        assertEquals("data", wav.decodeToString(36, 40))
        assertEquals(6, le32(wav, 40))
    }

    @Test
    fun centerCropCoversRequiredAspectRatios() {
        assertEquals(AspectCropper.computeCenterCrop(1920, 1080, 320, 240), AspectCropper.computeCenterCrop(1920, 1080, 4, 3))
        assertEquals(864, AspectCropper.computeCenterCrop(1080, 1920, 320, 256).height)
        assertEquals(AspectCropper.computeCenterCrop(1000, 1000, 640, 496).width, 1000)
        assertEquals(AspectCropper.computeCenterCrop(320, 256, 320, 256).left, 0)
        assertTrue(AspectCropper.computeCenterCrop(4000, 500, 320, 256).width < 4000)
    }

    @Test
    fun imageResamplerReturnsExactTargetSize() {
        val source = gradient(80, 45)
        val out = ImageResampler.resizeCrop(source, 320, 240)
        assertEquals(320, out.width)
        assertEquals(240, out.height)
        assertNotEquals(0, out.pixel(160, 120))
    }

    @Test
    fun yuvConversionHasExpectedRanges() {
        val black = 0xff000000.toInt()
        val white = 0xffffffff.toInt()
        val gray = 0xff808080.toInt()
        val red = 0xffff0000.toInt()
        assertTrue(YuvConverter.y(black) in 15..17)
        assertTrue(YuvConverter.y(white) in 234..236)
        assertTrue(abs(YuvConverter.u(gray) - 128) <= 1)
        assertTrue(YuvConverter.v(red) > 200)
    }

    private fun gradient(width: Int, height: Int): RgbImage {
        val argb = IntArray(width * height)
        for (y in 0 until height) {
            for (x in 0 until width) {
                val r = x * 255 / (width - 1).coerceAtLeast(1)
                val g = y * 255 / (height - 1).coerceAtLeast(1)
                val b = (x + y) * 255 / (width + height - 2).coerceAtLeast(1)
                argb[y * width + x] = (0xff shl 24) or (r shl 16) or (g shl 8) or b
            }
        }
        return RgbImage(width, height, argb)
    }

    private fun le32(bytes: ByteArray, offset: Int): Int =
        (bytes[offset].toInt() and 0xff) or
            ((bytes[offset + 1].toInt() and 0xff) shl 8) or
            ((bytes[offset + 2].toInt() and 0xff) shl 16) or
            ((bytes[offset + 3].toInt() and 0xff) shl 24)
}
