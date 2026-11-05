package pl.wraven.mpkfastroute.core.audio

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.radioDataStore by preferencesDataStore(name = "mpk_fastroute_preferences")

class RadioPreferences @Inject constructor(
    @ApplicationContext context: Context
) {
    private val dataStore = context.radioDataStore

    val lastSelectedRadioIndex: Flow<Int> = dataStore.data.map { preferences ->
        preferences[LAST_SELECTED_RADIO_INDEX] ?: DEFAULT_RADIO_INDEX
    }

    suspend fun saveLastSelectedRadioIndex(index: Int) {
        require(index in RadioStations.all.indices) { "Unknown radio station index: $index." }
        dataStore.edit { preferences ->
            preferences[LAST_SELECTED_RADIO_INDEX] = index
        }
    }

    private companion object {
        val LAST_SELECTED_RADIO_INDEX = intPreferencesKey("lastSelectedRadioIndex")
        const val DEFAULT_RADIO_INDEX = 0
    }
}
