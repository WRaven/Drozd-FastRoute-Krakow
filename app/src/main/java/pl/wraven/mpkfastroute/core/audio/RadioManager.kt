package pl.wraven.mpkfastroute.core.audio

import android.content.Context
import android.content.ComponentName
import android.content.Intent
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.ExecutionException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

@Singleton
class RadioManager @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val radioPreferences: RadioPreferences
) {
    private val controllerLock = Any()

    @Volatile
    private var mediaControllerFuture: ListenableFuture<MediaController>? = null

    suspend fun playLastSelectedStation() {
        playStation(radioPreferences.lastSelectedRadioIndex.first())
    }

    suspend fun playStation(index: Int) {
        require(index in RadioStations.all.indices) { "Unknown radio station index: $index." }
        radioPreferences.saveLastSelectedRadioIndex(index)
        context.startForegroundService(
            Intent(context, RadioService::class.java)
                .setAction(RadioService.ACTION_PLAY)
                .putExtra(RadioService.EXTRA_STATION_INDEX, index)
        )
        val future = getOrCreateControllerFuture()
        try {
            withContext(Dispatchers.IO) { future.get() }
        } catch (exception: ExecutionException) {
            releaseControllerFuture(future)
            throw IllegalStateException(
                "Nie udało się połączyć z usługą radia.",
                exception.cause ?: exception
            )
        } catch (exception: InterruptedException) {
            Thread.currentThread().interrupt()
            releaseControllerFuture(future)
            throw IllegalStateException("Przerwano połączenie z usługą radia.", exception)
        }
    }

    fun stop() {
        context.startService(
            Intent(context, RadioService::class.java).setAction(RadioService.ACTION_STOP)
        )
        val future = synchronized(controllerLock) {
            mediaControllerFuture.also { mediaControllerFuture = null }
        }
        if (future != null) {
            MediaController.releaseFuture(future)
        }
    }

    private fun getOrCreateControllerFuture(): ListenableFuture<MediaController> =
        synchronized(controllerLock) {
            mediaControllerFuture?.takeUnless { it.isCancelled }
                ?: MediaController.Builder(
                    context,
                    SessionToken(context, ComponentName(context, RadioService::class.java))
                ).buildAsync().also { mediaControllerFuture = it }
        }

    private fun releaseControllerFuture(future: ListenableFuture<MediaController>) {
        synchronized(controllerLock) {
            if (mediaControllerFuture === future) {
                mediaControllerFuture = null
            }
        }
        MediaController.releaseFuture(future)
    }
}
