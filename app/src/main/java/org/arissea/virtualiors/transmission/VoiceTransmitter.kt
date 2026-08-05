package org.arissea.virtualiors.transmission

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.arissea.virtualiors.model.VoiceConfig
import java.io.File
import java.util.Locale
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class VoiceTransmitter(private val context: Context) {
    private val ready = CompletableDeferred<Boolean>()
    private val activePlayer = AtomicReference<ExoPlayer?>(null)
    private lateinit var textToSpeech: TextToSpeech

    init {
        textToSpeech = TextToSpeech(context) { status ->
            val initialized = status == TextToSpeech.SUCCESS
            if (initialized) {
                textToSpeech.language = Locale.US
                textToSpeech.setSpeechRate(1.0f)
            }
            if (!ready.isCompleted) ready.complete(initialized)
        }
    }

    suspend fun transmit(config: VoiceConfig, prependVox: Boolean) {
        val voiceUri = if (config.useExternalAudio) {
            config.externalAudioUri?.let(Uri::parse) ?: error("External voice audio is not selected")
        } else {
            synthesize(config.phrase)
        }
        playMedia(voiceUri, prependVox)
        if (!config.useExternalAudio && voiceUri.scheme == "file") {
            runCatching { File(requireNotNull(voiceUri.path)).delete() }
        }
    }

    private suspend fun synthesize(text: String): Uri {
        check(ready.await()) { "On-device text-to-speech is unavailable" }
        val output = File(context.cacheDir, "virtualiors-voice-${UUID.randomUUID()}.wav")
        val utteranceId = UUID.randomUUID().toString()
        return suspendCancellableCoroutine { continuation ->
            textToSpeech.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(id: String?) = Unit

                override fun onDone(id: String?) {
                    if (id == utteranceId && continuation.isActive) continuation.resume(Uri.fromFile(output))
                }

                @Deprecated("Deprecated by Android")
                override fun onError(id: String?) {
                    if (id == utteranceId && continuation.isActive) {
                        continuation.resumeWithException(IllegalStateException("Text-to-speech synthesis failed"))
                    }
                }

                override fun onError(id: String?, errorCode: Int) = onError(id)
            })
            val result = textToSpeech.synthesizeToFile(
                text,
                Bundle(),
                output,
                utteranceId,
            )
            if (result == TextToSpeech.ERROR && continuation.isActive) {
                continuation.resumeWithException(IllegalStateException("Text-to-speech could not start"))
            }
            continuation.invokeOnCancellation {
                textToSpeech.stop()
                output.delete()
            }
        }
    }

    private suspend fun playMedia(voiceUri: Uri, prependVox: Boolean) = withContext(Dispatchers.Main.immediate) {
        suspendCancellableCoroutine { continuation ->
            val player = ExoPlayer.Builder(context).build()
            activePlayer.getAndSet(player)?.release()
            player.setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .setUsage(C.USAGE_MEDIA)
                    .build(),
                true,
            )
            if (prependVox) player.addMediaItem(MediaItem.fromUri(voxToneUri()))
            player.addMediaItem(MediaItem.fromUri(voiceUri))
            player.addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState == Player.STATE_ENDED && continuation.isActive) {
                        activePlayer.compareAndSet(player, null)
                        player.release()
                        continuation.resume(Unit)
                    }
                }

                override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                    if (continuation.isActive) {
                        activePlayer.compareAndSet(player, null)
                        player.release()
                        continuation.resumeWithException(error)
                    }
                }
            })
            player.prepare()
            player.playWhenReady = true
            continuation.invokeOnCancellation {
                if (activePlayer.compareAndSet(player, null)) {
                    player.stop()
                    player.release()
                }
            }
        }
    }

    private fun voxToneUri(): Uri {
        val id = context.resources.getIdentifier("tone_1900hz", "raw", context.packageName)
        check(id != 0) { "VOX tone resource is missing" }
        return Uri.parse("android.resource://${context.packageName}/$id")
    }

    fun stop() {
        textToSpeech.stop()
        activePlayer.getAndSet(null)?.let { player ->
            player.stop()
            player.release()
        }
    }

    fun shutdown() {
        stop()
        textToSpeech.shutdown()
    }
}
