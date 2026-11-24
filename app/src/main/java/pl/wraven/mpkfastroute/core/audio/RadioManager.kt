package pl.wraven.mpkfastroute.core.audio

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.CancellationException
import java.util.concurrent.ExecutionException
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

@Singleton
class RadioManager @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val radioPreferences: RadioPreferences
) {
    private val mutableIsPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = mutableIsPlaying.asStateFlow()

    private var observedController: MediaController? = null
    private var mediaControllerFuture: ListenableFuture<MediaController>? = null

    private val playbackListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            updatePlayingState(observedController)
        }

        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            updatePlayingState(observedController)
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            updatePlayingState(observedController)
        }

        override fun onPlayerError(error: PlaybackException) {
            mutableIsPlaying.value = false
        }
    }

    private val controllerListener = object : MediaController.Listener {
        override fun onDisconnected(controller: MediaController) {
            if (observedController === controller) {
                controller.removeListener(playbackListener)
                observedController = null
                mediaControllerFuture = null
                mutableIsPlaying.value = false
            }
        }
    }

    suspend fun playLastSelectedStation() {
        val stationId = radioPreferences.lastSelectedRadioId.first()
            ?: throw IllegalStateException("No radio stations are configured.")
        playStation(stationId)
    }

    suspend fun playStation(stationId: String) {
        val stations = radioPreferences.stations.first()
        val index = stations.indexOfFirst { it.id == stationId }
        require(index >= 0) { "Unknown radio station id: $stationId." }
        radioPreferences.saveLastSelectedStation(stationId)

        withContext(Dispatchers.Main.immediate) {
            val future = getOrCreateControllerFuture()
            val controller = try {
                awaitController(future)
            } catch (exception: ExecutionException) {
                releaseControllerFuture(future)
                throw IllegalStateException(
                    "Nie udało się połączyć z usługą radia.",
                    exception.cause ?: exception
                )
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: IllegalStateException) {
                releaseControllerFuture(future)
                throw exception
            }

            if (!controller.isConnected) {
                releaseControllerFuture(future)
                throw IllegalStateException("Połączenie z usługą radia zostało przerwane.")
            }

            observePlayback(future, controller)
            val mediaItems = stations.map { station ->
                MediaItem.Builder()
                    .setMediaId(station.id)
                    .setUri(station.streamUrl)
                    .setMediaMetadata(
                        MediaMetadata.Builder()
                            .setTitle(station.name)
                            .setArtist(RadioService.RADIO_ARTIST)
                            .build()
                    )
                    .build()
            }
            controller.setMediaItems(mediaItems, index, 0L)
            controller.prepare()
            controller.play()
            updatePlayingState(controller)
        }
    }

    fun stop() {
        val controller = observedController
        observedController = null
        if (controller != null) {
            controller.removeListener(playbackListener)
            if (controller.isConnected) {
                controller.stop()
                controller.clearMediaItems()
            }
        }
        mutableIsPlaying.value = false
        val future = mediaControllerFuture.also { mediaControllerFuture = null }
        if (future != null) {
            MediaController.releaseFuture(future)
        }
        context.stopService(Intent(context, RadioService::class.java))
    }

    private fun updatePlayingState(controller: MediaController?) {
        mutableIsPlaying.value = controller != null &&
            controller.isConnected &&
            (controller.isPlaying ||
                (controller.playWhenReady &&
                    controller.playbackState == Player.STATE_BUFFERING))
    }

    private fun observePlayback(
        future: ListenableFuture<MediaController>,
        controller: MediaController
    ) {
        if (mediaControllerFuture !== future) {
            return
        }
        if (observedController !== controller) {
            observedController?.removeListener(playbackListener)
            observedController = controller
            controller.addListener(playbackListener)
        }
        updatePlayingState(controller)
    }

    private fun getOrCreateControllerFuture(): ListenableFuture<MediaController> {
        val existingController = observedController
        if (existingController != null && !existingController.isConnected) {
            existingController.removeListener(playbackListener)
            observedController = null
            mediaControllerFuture?.let(MediaController::releaseFuture)
            mediaControllerFuture = null
        }
        return mediaControllerFuture?.takeUnless { it.isCancelled }
            ?: MediaController.Builder(
                context,
                SessionToken(context, ComponentName(context, RadioService::class.java))
            )
                .setListener(controllerListener)
                .buildAsync()
                .also { mediaControllerFuture = it }
    }

    private suspend fun awaitController(
        future: ListenableFuture<MediaController>
    ): MediaController {
        if (future.isDone) {
            return future.get()
        }
        return suspendCancellableCoroutine { continuation ->
            future.addListener(
                {
                    if (!continuation.isActive) return@addListener
                    try {
                        continuation.resume(future.get())
                    } catch (exception: ExecutionException) {
                        continuation.resumeWithException(exception)
                    } catch (exception: CancellationException) {
                        continuation.resumeWithException(exception)
                    } catch (exception: InterruptedException) {
                        Thread.currentThread().interrupt()
                        continuation.resumeWithException(
                            IllegalStateException("Przerwano połączenie z usługą radia.", exception)
                        )
                    }
                },
                ContextCompat.getMainExecutor(context)
            )
        }
    }

    private fun releaseControllerFuture(future: ListenableFuture<MediaController>) {
        if (mediaControllerFuture === future) {
            observedController?.removeListener(playbackListener)
            observedController = null
            mediaControllerFuture = null
            mutableIsPlaying.value = false
        }
        MediaController.releaseFuture(future)
    }
}
