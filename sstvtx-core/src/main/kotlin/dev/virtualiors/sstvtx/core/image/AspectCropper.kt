package dev.virtualiors.sstvtx.core.image

import kotlin.math.roundToInt

/** Source rectangle selected for center-crop before scaling to target dimensions. */
data class CropRect(val left: Int, val top: Int, val width: Int, val height: Int) {
    val right: Int get() = left + width
    val bottom: Int get() = top + height
}

object AspectCropper {
    fun computeCenterCrop(sourceWidth: Int, sourceHeight: Int, targetWidth: Int, targetHeight: Int): CropRect {
        require(sourceWidth > 0 && sourceHeight > 0) { "source dimensions must be positive" }
        require(targetWidth > 0 && targetHeight > 0) { "target dimensions must be positive" }
        val sourceAspect = sourceWidth.toDouble() / sourceHeight.toDouble()
        val targetAspect = targetWidth.toDouble() / targetHeight.toDouble()
        return if (sourceAspect > targetAspect) {
            val cropWidth = (sourceHeight * targetAspect).roundToInt().coerceIn(1, sourceWidth)
            val left = ((sourceWidth - cropWidth) / 2.0).roundToInt().coerceIn(0, sourceWidth - cropWidth)
            CropRect(left = left, top = 0, width = cropWidth, height = sourceHeight)
        } else if (sourceAspect < targetAspect) {
            val cropHeight = (sourceWidth / targetAspect).roundToInt().coerceIn(1, sourceHeight)
            val top = ((sourceHeight - cropHeight) / 2.0).roundToInt().coerceIn(0, sourceHeight - cropHeight)
            CropRect(left = 0, top = top, width = sourceWidth, height = cropHeight)
        } else {
            CropRect(0, 0, sourceWidth, sourceHeight)
        }
    }
}
