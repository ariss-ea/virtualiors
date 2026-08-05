package org.arissea.virtualiors.camera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.arissea.virtualiors.model.CameraFacing
import org.arissea.virtualiors.sstv.AndroidSstvImageProcessor
import java.io.ByteArrayOutputStream
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

enum class CameraBindingStatus { IDLE, BINDING, READY, ERROR }

data class CameraBindingState(
    val status: CameraBindingStatus = CameraBindingStatus.IDLE,
    val message: String? = null,
) {
    val ready: Boolean get() = status == CameraBindingStatus.READY
}

class CameraCaptureController {
    private val capture = AtomicReference<ImageCapture?>(null)
    private val executor = AtomicReference<Executor?>(null)
    private val captureInProgress = AtomicBoolean(false)
    private val generation = AtomicInteger(0)
    private val mutableState = MutableStateFlow(CameraBindingState())
    val state: StateFlow<CameraBindingState> = mutableState.asStateFlow()

    private var provider: ProcessCameraProvider? = null
    private var previewUseCase: Preview? = null
    private var captureUseCase: ImageCapture? = null
    private var boundFacing: CameraFacing? = null

    fun bind(
        context: Context,
        lifecycleOwner: LifecycleOwner,
        facing: CameraFacing,
        previewView: PreviewView,
    ) {
        val request = generation.incrementAndGet()
        mutableState.value = CameraBindingState(CameraBindingStatus.BINDING)
        val mainExecutor = ContextCompat.getMainExecutor(context)
        ProcessCameraProvider.getInstance(context).addListener(
            {
                if (request != generation.get()) return@addListener
                runCatching {
                    val cameraProvider = ProcessCameraProvider.getInstance(context).get()
                    provider = cameraProvider
                    if (captureUseCase == null || previewUseCase == null || boundFacing != facing) {
                        unbindOwnedUseCases(cameraProvider)
                        val preview = Preview.Builder().build()
                        val imageCapture = ImageCapture.Builder()
                            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                            .build()
                        val selector = if (facing == CameraFacing.FRONT) {
                            CameraSelector.DEFAULT_FRONT_CAMERA
                        } else {
                            CameraSelector.DEFAULT_BACK_CAMERA
                        }
                        preview.setSurfaceProvider(previewView.surfaceProvider)
                        cameraProvider.bindToLifecycle(lifecycleOwner, selector, preview, imageCapture)
                        previewUseCase = preview
                        captureUseCase = imageCapture
                        boundFacing = facing
                        capture.set(imageCapture)
                        executor.set(mainExecutor)
                    } else {
                        previewUseCase?.setSurfaceProvider(previewView.surfaceProvider)
                    }
                    mutableState.value = CameraBindingState(CameraBindingStatus.READY)
                }.onFailure { error ->
                    capture.set(null)
                    mutableState.value = CameraBindingState(
                        CameraBindingStatus.ERROR,
                        error.message ?: "The selected camera is unavailable.",
                    )
                }
            },
            mainExecutor,
        )
    }

    fun unbind() {
        generation.incrementAndGet()
        provider?.let(::unbindOwnedUseCases)
        capture.set(null)
        executor.set(null)
        boundFacing = null
        mutableState.value = CameraBindingState()
    }

    fun release() = unbind()

    fun isReady(): Boolean = state.value.ready && capture.get() != null

    suspend fun captureBitmap(onPhotoTaken: () -> Unit = {}): Bitmap {
        check(captureInProgress.compareAndSet(false, true)) { "A photo is already being taken" }
        var image: ImageProxy? = null
        try {
            val captured = awaitCapturedImage(onPhotoTaken)
            image = captured
            return withContext(Dispatchers.Default) { imageProxyToBitmap(captured) }
        } finally {
            image?.close()
            if (image != null) captureInProgress.set(false)
        }
    }

    private suspend fun awaitCapturedImage(onPhotoTaken: () -> Unit): ImageProxy =
        suspendCancellableCoroutine { continuation ->
            val imageCapture = capture.get()
            val callbackExecutor = executor.get()
            if (imageCapture == null || callbackExecutor == null || !state.value.ready) {
                captureInProgress.set(false)
                continuation.resumeWithException(IllegalStateException("Camera is not ready yet"))
                return@suspendCancellableCoroutine
            }
            if (!continuation.isActive) {
                captureInProgress.set(false)
                return@suspendCancellableCoroutine
            }
            val callback = object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    try {
                        if (!continuation.isActive) {
                            image.close()
                            captureInProgress.set(false)
                            return
                        }
                        onPhotoTaken()
                        continuation.invokeOnCancellation {
                            image.close()
                            captureInProgress.set(false)
                        }
                        continuation.resume(image)
                    } catch (error: Throwable) {
                        image.close()
                        captureInProgress.set(false)
                        if (continuation.isActive) continuation.resumeWithException(error)
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    captureInProgress.set(false)
                    if (continuation.isActive) continuation.resumeWithException(exception)
                }
            }
            try {
                imageCapture.takePicture(callbackExecutor, callback)
            } catch (error: Throwable) {
                captureInProgress.set(false)
                if (continuation.isActive) continuation.resumeWithException(error)
            }
        }

    private fun unbindOwnedUseCases(cameraProvider: ProcessCameraProvider) {
        val owned = listOfNotNull(previewUseCase, captureUseCase)
        if (owned.isNotEmpty()) cameraProvider.unbind(*owned.toTypedArray())
        previewUseCase = null
        captureUseCase = null
    }

    private fun imageProxyToBitmap(image: ImageProxy): Bitmap {
        val raw = when (image.format) {
            ImageFormat.JPEG -> image.planes[0].buffer.let { buffer ->
                ByteArray(buffer.remaining()).also { buffer.get(it) }
            }
            ImageFormat.YUV_420_888 -> yuv420ToJpegBytes(image)
            else -> error("Unsupported camera format ${image.format}")
        }
        val decoded = BitmapFactory.decodeByteArray(raw, 0, raw.size) ?: error("Camera image could not be decoded")
        return AndroidSstvImageProcessor.rotate(decoded, image.imageInfo.rotationDegrees)
    }

    private fun yuv420ToJpegBytes(image: ImageProxy): ByteArray {
        val width = image.width
        val height = image.height
        val output = ByteArray(width * height + width * height / 2)
        var position = 0
        val y = image.planes[0]
        for (row in 0 until height) {
            for (column in 0 until width) {
                output[position++] = y.buffer.get(row * y.rowStride + column * y.pixelStride)
            }
        }
        val u = image.planes[1]
        val v = image.planes[2]
        for (row in 0 until height / 2) {
            for (column in 0 until width / 2) {
                output[position++] = v.buffer.get(row * v.rowStride + column * v.pixelStride)
                output[position++] = u.buffer.get(row * u.rowStride + column * u.pixelStride)
            }
        }
        return ByteArrayOutputStream().use { stream ->
            YuvImage(output, ImageFormat.NV21, width, height, null)
                .compressToJpeg(Rect(0, 0, width, height), 95, stream)
            stream.toByteArray()
        }
    }
}
