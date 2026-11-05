package pl.wraven.mpkfastroute.core.settings

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.appPreferencesDataStore by preferencesDataStore(
    name = "mpk_fastroute_preferences"
)

class AppPreferences @Inject constructor(
    @ApplicationContext context: Context
) {
    private val dataStore = context.appPreferencesDataStore

    val homeStopId: Flow<String?> = dataStore.data.map { preferences ->
        preferences[HOME_STOP_ID]
    }

    val workStopId: Flow<String?> = dataStore.data.map { preferences ->
        preferences[WORK_STOP_ID]
    }

    val lastSelectedRadioIndex: Flow<Int> = dataStore.data.map { preferences ->
        preferences[LAST_SELECTED_RADIO_INDEX] ?: DEFAULT_RADIO_INDEX
    }

    suspend fun saveHomeStopId(stopId: String?) {
        saveStopId(HOME_STOP_ID, stopId)
    }

    suspend fun saveWorkStopId(stopId: String?) {
        saveStopId(WORK_STOP_ID, stopId)
    }

    suspend fun saveLastSelectedRadioIndex(index: Int) {
        require(index in RADIO_INDEX_RANGE) { "Unknown radio station index: $index." }
        dataStore.edit { preferences ->
            preferences[LAST_SELECTED_RADIO_INDEX] = index
        }
    }

    private suspend fun saveStopId(
        key: androidx.datastore.preferences.core.Preferences.Key<String>,
        stopId: String?
    ) {
        dataStore.edit { preferences ->
            if (stopId == null) {
                preferences.remove(key)
            } else {
                preferences[key] = stopId
            }
        }
    }

    private companion object {
        val HOME_STOP_ID = stringPreferencesKey("homeStopId")
        val WORK_STOP_ID = stringPreferencesKey("workStopId")
        val LAST_SELECTED_RADIO_INDEX = intPreferencesKey("lastSelectedRadioIndex")
        const val DEFAULT_RADIO_INDEX = 0
        val RADIO_INDEX_RANGE = 0..1
    }
}
