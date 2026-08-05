package org.arissea.virtualiors.sstv

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.net.Uri
import dev.virtualiors.sstvtx.core.RgbImage
import dev.virtualiors.sstvtx.core.image.AspectCropper
import org.arissea.virtualiors.R
import org.arissea.virtualiors.model.MediaSource
import org.arissea.virtualiors.model.SstvModeOption
import org.arissea.virtualiors.model.WatermarkConfig
import java.time.Instant
import kotlin.math.max

class AndroidSstvImageProcessor(private val context: Context) {
    fun prepare(
        source: MediaSource,
        mode: SstvModeOption,
        watermark: WatermarkConfig,
        callsign: String,
        imageIndex: Int,
        imageCount: Int,
        cameraCapture: Boolean = false,
        timestamp: Instant = Instant.now(),
    ): Bitmap {
        val decoded = decode(source)
        return prepareBitmap(decoded, mode, watermark, callsign, imageIndex, imageCount, cameraCapture, timestamp)
    }

    fun prepareBitmap(
        decoded: Bitmap,
        mode: SstvModeOption,
        watermark: WatermarkConfig,
        callsign: String,
        imageIndex: Int,
        imageCount: Int,
        cameraCapture: Boolean,
        timestamp: Instant = Instant.now(),
    ): Bitmap {
        val cropped = centerCrop(decoded, mode.width, mode.height)
        return renderWatermark(cropped, watermark, callsign, imageIndex, imageCount, cameraCapture, timestamp)
    }

    fun toRgbImage(bitmap: Bitmap): RgbImage {
        val argb = bitmap.ensureArgb8888()
        val pixels = IntArray(argb.width * argb.height)
        argb.getPixels(pixels, 0, argb.width, 0, 0, argb.width, argb.height)
        return RgbImage(argb.width, argb.height, pixels)
    }

    private fun decode(source: MediaSource): Bitmap = when {
        !source.uri.isNullOrBlank() -> {
            val imageSource = ImageDecoder.createSource(context.contentResolver, Uri.parse(source.uri))
            ImageDecoder.decodeBitmap(imageSource) { decoder, _, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                decoder.isMutableRequired = true
            }
        }
        !source.resourceName.isNullOrBlank() -> {
            val id = context.resources.getIdentifier(source.resourceName, "drawable", context.packageName)
            require(id != 0) { "Missing image resource ${source.resourceName}" }
            BitmapFactory.decodeResource(context.resources, id)
                ?: error("Could not decode ${source.resourceName}")
        }
        else -> error("Media source has no data")
    }.ensureArgb8888()

    private fun centerCrop(source: Bitmap, targetWidth: Int, targetHeight: Int): Bitmap {
        val input = source.ensureArgb8888()
        val crop = AspectCropper.computeCenterCrop(input.width, input.height, targetWidth, targetHeight)
        val output = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
        Canvas(output).drawBitmap(
            input,
            Rect(crop.left, crop.top, crop.right, crop.bottom),
            Rect(0, 0, targetWidth, targetHeight),
            Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG),
        )
        return output
    }

    private fun renderWatermark(
        input: Bitmap,
        options: WatermarkConfig,
        callsign: String,
        imageIndex: Int,
        imageCount: Int,
        cameraCapture: Boolean,
        timestamp: Instant,
    ): Bitmap {
        val output = input.copy(Bitmap.Config.ARGB_8888, true)
        if (!options.enabled) return output
        val canvas = Canvas(output)
        val stripHeight = (output.height * 0.18f).toInt().coerceAtLeast(42)
        val stripTop = output.height - stripHeight
        val padding = max(6, (stripHeight * 0.12f).toInt())
        canvas.drawRect(
            0f,
            stripTop.toFloat(),
            output.width.toFloat(),
            output.height.toFloat(),
            Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(224, 14, 15, 18) },
        )

        val logo = BitmapFactory.decodeResource(context.resources, R.drawable.logo_viors)
        val logoHeight = stripHeight - padding * 2
        val logoWidth = (logoHeight * logo.width.toFloat() / logo.height).toInt().coerceAtLeast(1)
        canvas.drawBitmap(
            logo,
            null,
            Rect(padding, stripTop + padding, padding + logoWidth, stripTop + padding + logoHeight),
            Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG),
        )
        val textLeft = (padding * 2 + logoWidth).toFloat()
        val primary = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            textSize = max(18f, stripHeight * 0.36f)
        }
        val secondary = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(245, 255, 255, 255)
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
            textSize = max(15f, stripHeight * 0.26f)
        }
        canvas.drawText(WatermarkContent.TITLE, textLeft, stripTop + stripHeight * 0.42f, primary)
        val details = if (cameraCapture) {
            WatermarkContent.cameraDetails(callsign, options.showCallsign, options.showTimestamp, timestamp)
        } else {
            WatermarkContent.imageDetails(
                callsign,
                options.showCallsign,
                options.showImageNumber,
                imageIndex,
                imageCount,
            )
        }
        if (details.isNotBlank()) canvas.drawText(details, textLeft, stripTop + stripHeight * 0.80f, secondary)
        return output
    }

    private fun Bitmap.ensureArgb8888(): Bitmap =
        if (config == Bitmap.Config.ARGB_8888 && isMutable) this else copy(Bitmap.Config.ARGB_8888, true)

    companion object {
        fun rotate(bitmap: Bitmap, degrees: Int): Bitmap {
            if (degrees % 360 == 0) return bitmap
            val matrix = Matrix().apply { postRotate(degrees.toFloat()) }
            return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        }
    }
}
