package pl.wraven.mpkfastroute.core.audio

import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import pl.wraven.mpkfastroute.core.settings.AppPreferences

class RadioPreferences @Inject constructor(
    private val appPreferences: AppPreferences
) {
    val lastSelectedRadioIndex: Flow<Int> = appPreferences.lastSelectedRadioIndex

    suspend fun saveLastSelectedRadioIndex(index: Int) {
        appPreferences.saveLastSelectedRadioIndex(index)
    }
}
