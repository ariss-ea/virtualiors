package org.arissea.virtualiors.transmission

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.BatteryManager
import android.os.SystemClock
import dev.virtualiors.sstvtx.core.SstvEncoder
import dev.virtualiors.sstvtx.core.SstvEncoderConfig
import dev.virtualiors.sstvtx.core.SstvModes
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.arissea.virtualiors.camera.CameraCaptureController
import org.arissea.virtualiors.model.AppConfig
import org.arissea.virtualiors.model.AprsCoordinateValidator
import org.arissea.virtualiors.model.AprsPositionResolution
import org.arissea.virtualiors.model.MediaSource
import org.arissea.virtualiors.model.SstvModeOption
import org.arissea.virtualiors.model.SstvSourceType
import org.arissea.virtualiors.sstv.AndroidSstvImageProcessor
import java.time.Instant
import kotlin.coroutines.coroutineContext

data class TransmissionUiState(
    val kind: BlockKind,
    val detail: String,
    val nextLabel: String,
    val durationMs: Long,
    val elapsedMs: Long,
    val progress: Float,
    val voxActive: Boolean,
    val aprsPreambleActive: Boolean,
) {
    val remainingMs: Long get() = (durationMs - elapsedMs).coerceAtLeast(0)
}

class TransmissionEngine(
    private val context: Context,
    private val cameraController: CameraCaptureController,
    private val onState: (TransmissionUiState) -> Unit,
    private val onMessage: (String) -> Unit,
) {
    private val scheduler = TransmissionScheduler()
    private val uriAudioPlayer = UriAudioPlayer(context)
    private val pcmAudioPlayer = PcmAudioPlayer()
    private val voiceTransmitter = VoiceTransmitter(context)
    private val imageProcessor = AndroidSstvImageProcessor(context)
    private val aprsBuilder = AprsTransmissionBuilder()
    private val mediaSelector = MediaSelector()
    private val aprsLocationSession = AprsLocationSession(
        source = AndroidAprsLocationUpdateSource(context),
        elapsedRealtimeMs = SystemClock::elapsedRealtime,
    )

    suspend fun run(inputConfig: AppConfig) {
        val config = inputConfig.normalized()
        validate(config)
        var imageNumber = 1
        var sourceIndex = 0
        aprsLocationSession.start(config.aprs)
        try {
            while (coroutineContext.isActive) {
                val blocks = scheduler.planCycle(imageNumber, config)
                for (block in blocks) {
                    coroutineContext.ensureActive()
                    try {
                        when (block.kind) {
                            BlockKind.SSTV_IMAGE -> transmitImage(config, imageNumber, sourceIndex, block)
                            BlockKind.VOICE -> transmitVoice(config, block)
                            BlockKind.APRS_BEACON,
                            BlockKind.APRS_GPS_POSITION,
                            BlockKind.APRS_TELEMETRY,
                            BlockKind.APRS_TEXT,
                            -> transmitAprs(config, imageNumber, block)
                            BlockKind.WAITING -> waitBlock(block)
                        }
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (error: Throwable) {
                        onMessage(error.message ?: "${block.kind.label} was skipped")
                    }
                }
                sourceIndex = nextSourceIndex(config, sourceIndex)
                imageNumber += 1
            }
        } finally {
            aprsLocationSession.stop()
        }
    }

    suspend fun transmitRobot36Test(inputConfig: AppConfig) {
        val config = inputConfig.normalized().copy(sstvMode = SstvModeOption.ROBOT36)
        val source = MediaSource(
            resourceName = "demo_robot36_01",
            displayName = "Robot36 test image",
        )
        val block = TransmissionBlock(
            kind = BlockKind.SSTV_IMAGE,
            durationMs = 0,
            voxToneBefore = config.globalVoxTone,
            nextLabel = "Done",
        )
        val bitmap = preparing(block, "Preparing Robot36 test image") {
            imageProcessor.prepare(
                source = source,
                mode = SstvModeOption.ROBOT36,
                watermark = config.watermark,
                callsign = config.normalizedCallsign,
                imageIndex = 1,
                imageCount = 1,
            )
        }
        transmitPreparedBitmap(config, bitmap, block, source.displayName)
    }

    suspend fun transmitAprsTest(inputConfig: AppConfig) {
        val normalized = inputConfig.normalized()
        val config = normalized.copy(aprs = normalized.aprs.copy(enabled = true, beaconEnabled = true))
        val block = TransmissionBlock(
            kind = BlockKind.APRS_BEACON,
            durationMs = 0,
            aprsPreambleSeconds = if (config.globalVoxTone) 1.0 else TransmissionScheduler.DEFAULT_APRS_PREAMBLE_SECONDS,
            nextLabel = "Done",
        )
        transmitAprs(config, imageNumber = 1, block = block)
    }

    fun stop() {
        aprsLocationSession.stop()
        uriAudioPlayer.stop()
        pcmAudioPlayer.stop()
        voiceTransmitter.stop()
    }

    fun shutdown() {
        stop()
        voiceTransmitter.shutdown()
    }

    private fun validate(config: AppConfig) {
        when (config.sourceType) {
            SstvSourceType.AUDIO_FILES -> require(config.audioSources.size >= 12) { "Add at least 12 SSTV audio files" }
            SstvSourceType.IMAGE_FILES -> require(config.imageSources.isNotEmpty()) { "Add at least one image" }
            SstvSourceType.AUTO_CAMERA -> require(cameraController.isReady()) { "Camera is not ready. Grant camera access and wait for the preview." }
        }
        if (config.voice.enabled && config.voice.useExternalAudio) {
            require(!config.voice.externalAudioUri.isNullOrBlank()) { "Select the external voice audio file" }
        }
        if (config.aprs.enabled && config.aprs.customTextEnabled) {
            require(config.aprs.customText.isNotBlank()) { "Enter APRS custom text or turn it off" }
        }
        if (config.aprs.enabled && config.aprs.gpsPositionEnabled && config.aprs.needsPresetCoordinates) {
            require(
                AprsCoordinateValidator.parse(config.aprs.presetLatitude, config.aprs.presetLongitude) != null,
            ) { "Enter valid preset coordinates in Settings" }
        }
    }

    private suspend fun transmitImage(config: AppConfig, imageNumber: Int, sourceIndex: Int, block: TransmissionBlock) {
        when (config.sourceType) {
            SstvSourceType.AUDIO_FILES -> {
                val selected = mediaSelector.select(config.audioSources, sourceIndex, config.shuffle)
                val source = selected.item
                val uri = Uri.parse(requireNotNull(source.uri))
                val duration = uriAudioPlayer.durationMs(uri, block.voxToneBefore).coerceAtLeast(1_000)
                timed(block, duration, "SSTV image ${selected.counter}") {
                    uriAudioPlayer.play(uri, block.voxToneBefore)
                }
            }
            SstvSourceType.IMAGE_FILES -> {
                val selected = mediaSelector.select(config.imageSources, sourceIndex, config.shuffle)
                val source = selected.item
                val bitmap = preparing(block, "Preparing ${source.displayName}") {
                    imageProcessor.prepare(
                        source = source,
                        mode = config.sstvMode,
                        watermark = config.watermark,
                        callsign = config.normalizedCallsign,
                        imageIndex = selected.position,
                        imageCount = selected.total,
                    )
                }
                transmitPreparedBitmap(
                    config,
                    bitmap,
                    block,
                    "${config.sstvMode.label} image ${selected.counter}",
                )
            }
            SstvSourceType.AUTO_CAMERA -> {
                val captured = preparing(block, "Taking photo") {
                    cameraController.captureBitmap {
                        emitPreparingState(block, "Preparing image")
                    }
                }
                val prepared = preparing(block, "Preparing image") {
                    imageProcessor.prepareBitmap(
                        decoded = captured,
                        mode = config.sstvMode,
                        watermark = config.watermark,
                        callsign = config.normalizedCallsign,
                        imageIndex = imageNumber,
                        imageCount = imageNumber,
                        cameraCapture = true,
                        timestamp = Instant.now(),
                    )
                }
                transmitPreparedBitmap(
                    config = config,
                    bitmap = prepared,
                    block = block,
                    detail = "${config.sstvMode.label} image $imageNumber",
                    preparationDetail = "Preparing image",
                )
            }
        }
    }

    private suspend fun transmitPreparedBitmap(
        config: AppConfig,
        bitmap: android.graphics.Bitmap,
        block: TransmissionBlock,
        detail: String,
        preparationDetail: String = "Encoding ${config.sstvMode.label} on device",
    ) {
        val transmission = preparing(block, preparationDetail) {
            val rgb = imageProcessor.toRgbImage(bitmap)
            val mode = SstvModes.modeById(config.sstvMode.coreId)
            SstvEncoder(SstvEncoderConfig(sampleRate = SSTV_SAMPLE_RATE, amplitude = 0.80)).encode(rgb, mode)
        }
        val pcm = VoxTone.prependIfEnabled(transmission.pcm16, transmission.sampleRate, block.voxToneBefore)
        val duration = pcm.size * 1_000L / transmission.sampleRate
        timed(block, duration, detail) { pcmAudioPlayer.play(pcm, transmission.sampleRate) }
    }

    private suspend fun transmitVoice(config: AppConfig, block: TransmissionBlock) {
        val phraseDuration = if (config.voice.useExternalAudio) {
            config.voice.externalAudioUri?.let { uriAudioPlayer.durationMs(Uri.parse(it), false) } ?: 1_000L
        } else {
            (config.voice.phrase.split(Regex("\\s+")).size / 2.5 * 1_000).toLong().coerceAtLeast(1_000)
        }
        val duration = phraseDuration + if (block.voxToneBefore) VoxTone.DURATION_MS else 0
        timed(block, duration, if (config.voice.useExternalAudio) "External voice audio" else "On-device TTS") {
            voiceTransmitter.transmit(config.voice, block.voxToneBefore)
        }
    }

    private suspend fun transmitAprs(config: AppConfig, imageNumber: Int, block: TransmissionBlock) {
        val position = if (block.kind == BlockKind.APRS_GPS_POSITION) {
            when (val resolved = aprsLocationSession.resolve(config.aprs)) {
                is AprsPositionResolution.Invalid -> {
                    onMessage(resolved.message)
                    return
                }
                else -> resolved
            }
        } else {
            null
        }
        val packet = aprsBuilder.build(
            kind = block.kind,
            config = config,
            imageCounter = imageNumber,
            telemetry = deviceTelemetry(),
            position = position,
            preambleSeconds = block.aprsPreambleSeconds,
        ) ?: return
        val duration = packet.pcm16.size * 1_000L / packet.sampleRate
        timed(block, duration, TransmissionUiText.transmissionLabel(block.kind)) {
            pcmAudioPlayer.play(packet.pcm16, packet.sampleRate)
        }
    }

    private suspend fun waitBlock(block: TransmissionBlock) {
        val detail = when (block.waitReason) {
            WaitReason.APRS_PACKET_SPACING -> "Spacing between APRS packets"
            else -> "Transmitter cooldown"
        }
        timed(block, block.durationMs, detail) { delay(block.durationMs) }
    }

    private suspend fun <T> preparing(block: TransmissionBlock, detail: String, action: suspend () -> T): T {
        emitPreparingState(block, detail)
        return withContext(Dispatchers.Default) { action() }
    }

    private fun emitPreparingState(block: TransmissionBlock, detail: String) {
        onState(
            TransmissionUiState(
                kind = BlockKind.WAITING,
                detail = detail,
                nextLabel = block.kind.label,
                durationMs = 0,
                elapsedMs = 0,
                progress = 0f,
                voxActive = false,
                aprsPreambleActive = false,
            ),
        )
    }

    private suspend fun timed(
        block: TransmissionBlock,
        rawDurationMs: Long,
        detail: String,
        action: suspend () -> Unit,
    ) = coroutineScope {
        val duration = rawDurationMs.coerceAtLeast(100)
        val start = SystemClock.elapsedRealtime()
        val ticker: Job = launch {
            while (isActive) {
                val elapsed = (SystemClock.elapsedRealtime() - start).coerceAtMost(duration)
                onState(
                    TransmissionUiState(
                        kind = block.kind,
                        detail = detail,
                        nextLabel = block.nextLabel,
                        durationMs = duration,
                        elapsedMs = elapsed,
                        progress = (elapsed.toFloat() / duration).coerceIn(0f, 1f),
                        voxActive = block.voxToneBefore && elapsed < VoxTone.DURATION_MS && block.kind.isRealTransmission,
                        aprsPreambleActive = block.kind in TransmissionScheduler.APRS_KINDS &&
                            elapsed < (block.aprsPreambleSeconds * 1_000).toLong(),
                    ),
                )
                delay(50)
            }
        }
        try {
            action()
        } finally {
            ticker.cancelAndJoin()
        }
    }

    private fun nextSourceIndex(config: AppConfig, current: Int): Int = when (config.sourceType) {
        SstvSourceType.AUDIO_FILES -> (current + 1) % config.audioSources.size
        SstvSourceType.IMAGE_FILES -> (current + 1) % config.imageSources.size
        SstvSourceType.AUTO_CAMERA -> current + 1
    }

    private fun deviceTelemetry(): DeviceTelemetry {
        val battery = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = battery?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = battery?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        val status = battery?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        return DeviceTelemetry(
            batteryPercent = if (level >= 0 && scale > 0) level * 100 / scale else 0,
            charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL,
        )
    }

    companion object {
        private const val SSTV_SAMPLE_RATE = 44_100
    }
}
