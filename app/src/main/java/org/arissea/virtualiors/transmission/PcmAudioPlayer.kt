package org.arissea.virtualiors.transmission

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.coroutineContext

class PcmAudioPlayer {
    private val activeTrack = AtomicReference<AudioTrack?>(null)

    suspend fun play(pcm16: ShortArray, sampleRate: Int) = withContext(Dispatchers.IO) {
        val minBuffer = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        ).coerceAtLeast(4_096)
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build(),
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .build(),
            )
            .setTransferMode(AudioTrack.MODE_STREAM)
            .setBufferSizeInBytes(minBuffer)
            .build()
        activeTrack.getAndSet(track)?.release()
        try {
            track.play()
            var offset = 0
            while (offset < pcm16.size) {
                coroutineContext.ensureActive()
                val count = minOf(4_096, pcm16.size - offset)
                val written = track.write(pcm16, offset, count, AudioTrack.WRITE_BLOCKING)
                if (written <= 0) error("AudioTrack write failed: $written")
                offset += written
            }
            while (track.playbackHeadPosition.toLong() < pcm16.size.toLong()) {
                coroutineContext.ensureActive()
                Thread.sleep(10)
            }
        } finally {
            if (activeTrack.compareAndSet(track, null)) {
                runCatching { track.stop() }
                track.release()
            }
        }
    }

    fun stop() {
        activeTrack.getAndSet(null)?.let { track ->
            runCatching { track.pause() }
            runCatching { track.flush() }
            runCatching { track.stop() }
            track.release()
        }
    }
}
