package pl.wraven.mpkfastroute.core.audio

import android.content.Context
import android.content.Intent
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.first

class RadioManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val radioPreferences: RadioPreferences
) {
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
    }

    fun stop() {
        context.startService(
            Intent(context, RadioService::class.java).setAction(RadioService.ACTION_STOP)
        )
    }
}
