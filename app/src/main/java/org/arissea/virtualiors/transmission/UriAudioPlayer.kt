package org.arissea.virtualiors.transmission

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class UriAudioPlayer(private val context: Context) {
    private val activePlayer = AtomicReference<ExoPlayer?>(null)

    suspend fun play(uri: Uri, prependVox: Boolean) = withContext(Dispatchers.Main.immediate) {
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
            player.addMediaItem(MediaItem.fromUri(uri))
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

    fun durationMs(uri: Uri, prependVox: Boolean): Long {
        val mediaDuration = MediaMetadataRetriever().let { retriever ->
            try {
                retriever.setDataSource(context, uri)
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            } finally {
                retriever.release()
            }
        }
        return mediaDuration + if (prependVox) VoxTone.DURATION_MS else 0L
    }

    fun stop() {
        activePlayer.getAndSet(null)?.let { player ->
            player.stop()
            player.release()
        }
    }

    private fun voxToneUri(): Uri {
        val id = context.resources.getIdentifier("tone_1900hz", "raw", context.packageName)
        check(id != 0)
        return Uri.parse("android.resource://${context.packageName}/$id")
    }
}
