/*
 * Derived from SSTV Encoder for Android by Olga Miller, Apache License 2.0.
 */
package dev.virtualiors.sstvtx.core

import dev.virtualiors.sstvtx.core.wav.WavWriter
import dev.virtualiors.sstvtx.core.yuv.Nv21Image
import dev.virtualiors.sstvtx.core.yuv.Yuv440pImage
import dev.virtualiors.sstvtx.core.yuv.Yuy2Image
import dev.virtualiors.sstvtx.core.yuv.YuvConverter
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin

class SstvEncoder(
    private val config: SstvEncoderConfig = SstvEncoderConfig(),
) {
    fun encode(image: RgbImage, mode: SstvMode): SstvTransmission {
        require(image.width == mode.spec.width && image.height == mode.spec.height) {
            "Image must be exactly ${mode.spec.width}x${mode.spec.height} for ${mode.spec.displayName}, got ${image.width}x${image.height}"
        }
        val pcm = encodeToPcm16(image, mode)
        val spec = SstvModes.byId(mode.spec.id)
        return SstvTransmission(
            mode = spec,
            sampleRate = config.sampleRate,
            pcm16 = pcm,
            durationSeconds = pcm.size / config.sampleRate.toDouble(),
        )
    }

    fun encodeToPcm16(image: RgbImage, mode: SstvMode): ShortArray {
        require(image.width == mode.spec.width && image.height == mode.spec.height) {
            "Image must be exactly ${mode.spec.width}x${mode.spec.height} for ${mode.spec.displayName}, got ${image.width}x${image.height}"
        }
        val writer = ToneWriter(config.sampleRate, config.amplitude, SstvSampleMath.totalSamples(mode, config.sampleRate))
        writeCalibrationHeader(mode, writer)
        when (mode.spec.family) {
            SstvFamily.MARTIN -> writeMartin(image, mode, writer)
            SstvFamily.SCOTTIE -> writeScottie(image, mode, writer)
            SstvFamily.PD -> writePd(image, mode, writer)
            SstvFamily.ROBOT -> when (mode) {
                SstvMode.ROBOT_36 -> writeRobot36(image, writer)
                SstvMode.ROBOT_72 -> writeRobot72(image, writer)
                else -> error("Unsupported robot mode $mode")
            }
            SstvFamily.WRAASE -> writeWraase(image, writer)
        }
        return writer.toShortArray()
    }

    fun encodeToWav(image: RgbImage, mode: SstvMode): ByteArray =
        WavWriter.writePcm16Mono(encodeToPcm16(image, mode), config.sampleRate)

    private fun writeCalibrationHeader(mode: SstvMode, writer: ToneWriter) {
        writer.tone(1900.0, 300.0)
        writer.tone(1200.0, 10.0)
        writer.tone(1900.0, 300.0)
        writer.tone(1200.0, 30.0) // VIS start bit
        var parity = 0
        for (pos in 0 until 7) {
            val bit = (mode.spec.visCode shr pos) and 1
            parity = parity xor bit
            writer.tone(if (bit == 0) 1300.0 else 1100.0, 30.0)
        }
        writer.tone(if (parity == 0) 1300.0 else 1100.0, 30.0) // even parity bit
        writer.tone(1200.0, 30.0) // stop bit
    }

    private fun writeMartin(image: RgbImage, mode: SstvMode, writer: ToneWriter) {
        val scanSamples = writer.msToSamples(mode.timing.colorScanMs)
        repeat(image.height) { y ->
            writer.tone(1200.0, 4.862)
            writer.tone(1500.0, 0.572)
            writer.rgbScan(image, y, scanSamples, Channel.GREEN)
            writer.tone(1500.0, 0.572)
            writer.rgbScan(image, y, scanSamples, Channel.BLUE)
            writer.tone(1500.0, 0.572)
            writer.rgbScan(image, y, scanSamples, Channel.RED)
            writer.tone(1500.0, 0.572)
        }
    }

    private fun writeScottie(image: RgbImage, mode: SstvMode, writer: ToneWriter) {
        val scanSamples = writer.msToSamples(mode.timing.colorScanMs)
        repeat(image.height) { y ->
            if (y == 0) writer.tone(1200.0, 9.0)
            writer.tone(1500.0, 1.5)
            writer.rgbScan(image, y, scanSamples, Channel.GREEN)
            writer.tone(1500.0, 1.5)
            writer.rgbScan(image, y, scanSamples, Channel.BLUE)
            writer.tone(1200.0, 9.0)
            writer.tone(1500.0, 1.5)
            writer.rgbScan(image, y, scanSamples, Channel.RED)
        }
    }

    private fun writePd(image: RgbImage, mode: SstvMode, writer: ToneWriter) {
        val yuv = Yuv440pImage(image)
        val scanSamples = writer.msToSamples(mode.timing.colorScanMs)
        var y = 0
        while (y < image.height) {
            writer.tone(1200.0, 20.0)
            writer.tone(1500.0, 2.08)
            writer.yuvScan(yuv, y, scanSamples, YuvChannel.Y)
            writer.yuvScan(yuv, y, scanSamples, YuvChannel.V)
            writer.yuvScan(yuv, y, scanSamples, YuvChannel.U)
            writer.yuvScan(yuv, (y + 1).coerceAtMost(image.height - 1), scanSamples, YuvChannel.Y)
            y += 2
        }
    }

    private fun writeRobot36(image: RgbImage, writer: ToneWriter) {
        val yuv = Nv21Image(image)
        val lumaSamples = writer.msToSamples(88.0)
        val chromaSamples = writer.msToSamples(44.0)
        repeat(image.height) { y ->
            writer.tone(1200.0, 9.0)
            writer.tone(1500.0, 3.0)
            writer.yuvScan(yuv, y, lumaSamples, YuvChannel.Y)
            if (y % 2 == 0) {
                writer.tone(1500.0, 4.5)
                writer.tone(1900.0, 1.5)
                writer.yuvScan(yuv, y, chromaSamples, YuvChannel.V)
            } else {
                writer.tone(2300.0, 4.5)
                writer.tone(1900.0, 1.5)
                writer.yuvScan(yuv, y, chromaSamples, YuvChannel.U)
            }
        }
    }

    private fun writeRobot72(image: RgbImage, writer: ToneWriter) {
        val yuv = Yuy2Image(image)
        val lumaSamples = writer.msToSamples(138.0)
        val chromaSamples = writer.msToSamples(69.0)
        repeat(image.height) { y ->
            writer.tone(1200.0, 9.0)
            writer.tone(1500.0, 3.0)
            writer.yuvScan(yuv, y, lumaSamples, YuvChannel.Y)
            writer.tone(1500.0, 4.5)
            writer.tone(1900.0, 1.5)
            writer.yuvScan(yuv, y, chromaSamples, YuvChannel.V)
            writer.tone(2300.0, 4.5)
            writer.tone(1900.0, 1.5)
            writer.yuvScan(yuv, y, chromaSamples, YuvChannel.U)
        }
    }

    private fun writeWraase(image: RgbImage, writer: ToneWriter) {
        val scanSamples = writer.msToSamples(235.0)
        repeat(image.height) { y ->
            writer.tone(1200.0, 5.5225)
            writer.tone(1500.0, 0.5)
            writer.rgbScan(image, y, scanSamples, Channel.RED)
            writer.rgbScan(image, y, scanSamples, Channel.GREEN)
            writer.rgbScan(image, y, scanSamples, Channel.BLUE)
        }
    }
}

private enum class Channel { RED, GREEN, BLUE }
private enum class YuvChannel { Y, U, V }

private class ToneWriter(
    private val sampleRate: Int,
    private val amplitude: Double,
    expectedSamples: Int,
) {
    private val pcm = ShortArray(expectedSamples)
    private var phase = 0.0
    private var position = 0
    private val scale = Short.MAX_VALUE * amplitude

    fun msToSamples(durationMs: Double): Int = SstvSampleMath.msToSamples(durationMs, sampleRate)

    fun tone(frequency: Double, durationMs: Double) {
        repeat(msToSamples(durationMs)) { sampleTone(frequency) }
    }

    fun sampleTone(frequency: Double) {
        phase += 2.0 * frequency * PI / sampleRate
        phase %= 2.0 * PI
        val value = (sin(phase) * scale).roundToInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
        if (position < pcm.size) pcm[position++] = value.toShort() else error("PCM writer overflow")
    }

    fun colorTone(color: Int) {
        val frequency = color * (2300.0 - 1500.0) / 255.0 + 1500.0
        sampleTone(frequency)
    }

    fun rgbScan(image: RgbImage, y: Int, scanSamples: Int, channel: Channel) {
        repeat(scanSamples) { i ->
            val x = (i * image.width) / scanSamples
            val pixel = image.pixel(x, y)
            val color = when (channel) {
                Channel.RED -> (pixel ushr 16) and 0xff
                Channel.GREEN -> (pixel ushr 8) and 0xff
                Channel.BLUE -> pixel and 0xff
            }
            colorTone(color)
        }
    }

    fun yuvScan(yuv: dev.virtualiors.sstvtx.core.yuv.YuvImage, y: Int, scanSamples: Int, channel: YuvChannel) {
        repeat(scanSamples) { i ->
            val x = (i * yuv.width) / scanSamples
            val color = when (channel) {
                YuvChannel.Y -> yuv.getY(x, y)
                YuvChannel.U -> yuv.getU(x, y)
                YuvChannel.V -> yuv.getV(x, y)
            }
            colorTone(color)
        }
    }

    fun toShortArray(): ShortArray {
        check(position == pcm.size) { "PCM writer produced $position samples but expected ${pcm.size}" }
        return pcm
    }
}
