package dev.virtualiors.sstvtx.core.image

import dev.virtualiors.sstvtx.core.RgbImage
import kotlin.math.floor
import kotlin.math.roundToInt

object ImageResampler {
    fun resizeCrop(source: RgbImage, targetWidth: Int, targetHeight: Int): RgbImage {
        val crop = AspectCropper.computeCenterCrop(source.width, source.height, targetWidth, targetHeight)
        val output = IntArray(targetWidth * targetHeight)
        val xScale = crop.width.toDouble() / targetWidth.toDouble()
        val yScale = crop.height.toDouble() / targetHeight.toDouble()
        for (y in 0 until targetHeight) {
            val srcY = crop.top + (y + 0.5) * yScale - 0.5
            for (x in 0 until targetWidth) {
                val srcX = crop.left + (x + 0.5) * xScale - 0.5
                output[y * targetWidth + x] = bilinear(source, srcX, srcY)
            }
        }
        return RgbImage(targetWidth, targetHeight, output)
    }

    private fun bilinear(image: RgbImage, x: Double, y: Double): Int {
        val x0 = floor(x).toInt().coerceIn(0, image.width - 1)
        val y0 = floor(y).toInt().coerceIn(0, image.height - 1)
        val x1 = (x0 + 1).coerceAtMost(image.width - 1)
        val y1 = (y0 + 1).coerceAtMost(image.height - 1)
        val fx = (x - x0).coerceIn(0.0, 1.0)
        val fy = (y - y0).coerceIn(0.0, 1.0)
        val c00 = image.pixel(x0, y0)
        val c10 = image.pixel(x1, y0)
        val c01 = image.pixel(x0, y1)
        val c11 = image.pixel(x1, y1)
        val a = channel(c00, c10, c01, c11, 24, fx, fy)
        val r = channel(c00, c10, c01, c11, 16, fx, fy)
        val g = channel(c00, c10, c01, c11, 8, fx, fy)
        val b = channel(c00, c10, c01, c11, 0, fx, fy)
        return (a shl 24) or (r shl 16) or (g shl 8) or b
    }

    private fun channel(c00: Int, c10: Int, c01: Int, c11: Int, shift: Int, fx: Double, fy: Double): Int {
        val v00 = ((c00 ushr shift) and 0xff).toDouble()
        val v10 = ((c10 ushr shift) and 0xff).toDouble()
        val v01 = ((c01 ushr shift) and 0xff).toDouble()
        val v11 = ((c11 ushr shift) and 0xff).toDouble()
        val top = v00 * (1.0 - fx) + v10 * fx
        val bottom = v01 * (1.0 - fx) + v11 * fx
        return (top * (1.0 - fy) + bottom * fy).roundToInt().coerceIn(0, 255)
    }
}
