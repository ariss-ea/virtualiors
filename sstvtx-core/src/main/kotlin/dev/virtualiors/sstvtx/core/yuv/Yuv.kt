/* Derived from SSTV Encoder for Android by Olga Miller, Apache License 2.0. */
package dev.virtualiors.sstvtx.core.yuv

import dev.virtualiors.sstvtx.core.RgbImage

object YuvConverter {
    fun y(argb: Int): Int {
        val r = red(argb).toDouble()
        val g = green(argb).toDouble()
        val b = blue(argb).toDouble()
        return clamp(16.0 + (0.003906 * ((65.738 * r) + (129.057 * g) + (25.064 * b))))
    }

    fun u(argb: Int): Int {
        val r = red(argb).toDouble()
        val g = green(argb).toDouble()
        val b = blue(argb).toDouble()
        return clamp(128.0 + (0.003906 * ((-37.945 * r) + (-74.494 * g) + (112.439 * b))))
    }

    fun v(argb: Int): Int {
        val r = red(argb).toDouble()
        val g = green(argb).toDouble()
        val b = blue(argb).toDouble()
        return clamp(128.0 + (0.003906 * ((112.439 * r) + (-94.154 * g) + (-18.285 * b))))
    }

    internal fun red(argb: Int): Int = (argb ushr 16) and 0xff
    internal fun green(argb: Int): Int = (argb ushr 8) and 0xff
    internal fun blue(argb: Int): Int = argb and 0xff

    private fun clamp(value: Double): Int = when {
        value < 0.0 -> 0
        value > 255.0 -> 255
        else -> value.toInt()
    }
}

internal interface YuvImage {
    val width: Int
    val height: Int
    fun getY(x: Int, y: Int): Int
    fun getU(x: Int, y: Int): Int
    fun getV(x: Int, y: Int): Int
}

internal class Nv21Image(image: RgbImage) : YuvImage {
    override val width: Int = image.width
    override val height: Int = image.height
    private val yuv = ByteArray((3 * width * height) / 2)

    init {
        var pos = 0
        for (row in 0 until height) {
            for (col in 0 until width) {
                yuv[pos++] = YuvConverter.y(image.pixel(col, row)).toByte()
            }
        }
        for (row in 0 until height step 2) {
            for (col in 0 until width step 2) {
                val row2 = (row + 1).coerceAtMost(height - 1)
                val col2 = (col + 1).coerceAtMost(width - 1)
                val p00 = image.pixel(col, row)
                val p10 = image.pixel(col2, row)
                val p01 = image.pixel(col, row2)
                val p11 = image.pixel(col2, row2)
                yuv[pos++] = ((YuvConverter.v(p00) + YuvConverter.v(p10) + YuvConverter.v(p01) + YuvConverter.v(p11)) / 4).toByte()
                yuv[pos++] = ((YuvConverter.u(p00) + YuvConverter.u(p10) + YuvConverter.u(p01) + YuvConverter.u(p11)) / 4).toByte()
            }
        }
    }

    override fun getY(x: Int, y: Int): Int = yuv[width * y + x].toInt() and 0xff
    override fun getU(x: Int, y: Int): Int = yuv[width * height + width * (y shr 1) + (x or 1)].toInt() and 0xff
    override fun getV(x: Int, y: Int): Int = yuv[width * height + width * (y shr 1) + (x and -2)].toInt() and 0xff
}

internal class Yuv440pImage(image: RgbImage) : YuvImage {
    override val width: Int = image.width
    override val height: Int = image.height
    private val yuv = ByteArray(2 * width * height)

    init {
        var pos = 0
        for (row in 0 until height) {
            for (col in 0 until width) {
                yuv[pos++] = YuvConverter.y(image.pixel(col, row)).toByte()
            }
        }
        for (row in 0 until height step 2) {
            val row2 = (row + 1).coerceAtMost(height - 1)
            for (col in 0 until width) {
                yuv[pos++] = ((YuvConverter.u(image.pixel(col, row)) + YuvConverter.u(image.pixel(col, row2))) / 2).toByte()
            }
        }
        for (row in 0 until height step 2) {
            val row2 = (row + 1).coerceAtMost(height - 1)
            for (col in 0 until width) {
                yuv[pos++] = ((YuvConverter.v(image.pixel(col, row)) + YuvConverter.v(image.pixel(col, row2))) / 2).toByte()
            }
        }
    }

    override fun getY(x: Int, y: Int): Int = yuv[width * y + x].toInt() and 0xff
    override fun getU(x: Int, y: Int): Int = yuv[width * height + width * (y shr 1) + x].toInt() and 0xff
    override fun getV(x: Int, y: Int): Int = yuv[((3 * width * height) shr 1) + width * (y shr 1) + x].toInt() and 0xff
}

internal class Yuy2Image(image: RgbImage) : YuvImage {
    override val width: Int = image.width
    override val height: Int = image.height
    private val yuv = ByteArray(2 * width * height)

    init {
        var pos = 0
        for (row in 0 until height) {
            for (col in 0 until width step 2) {
                val col2 = (col + 1).coerceAtMost(width - 1)
                val p0 = image.pixel(col, row)
                val p1 = image.pixel(col2, row)
                yuv[pos++] = YuvConverter.y(p0).toByte()
                yuv[pos++] = ((YuvConverter.u(p0) + YuvConverter.u(p1)) / 2).toByte()
                yuv[pos++] = YuvConverter.y(p1).toByte()
                yuv[pos++] = ((YuvConverter.v(p0) + YuvConverter.v(p1)) / 2).toByte()
            }
        }
    }

    override fun getY(x: Int, y: Int): Int = yuv[2 * width * y + 2 * x].toInt() and 0xff
    override fun getU(x: Int, y: Int): Int = yuv[2 * width * y + (((x and -2) shl 1) or 1)].toInt() and 0xff
    override fun getV(x: Int, y: Int): Int = yuv[2 * width * y + (((x and -2) shl 1) or 3)].toInt() and 0xff
}
