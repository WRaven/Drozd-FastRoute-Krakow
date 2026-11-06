package pl.wraven.mpkfastroute.core.settings

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlin.math.roundToInt
import pl.wraven.mpkfastroute.core.location.GeoPlace

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

    val homePlace: Flow<GeoPlace?> = dataStore.data.map { preferences ->
        readPlace(preferences, HOME_PLACE_LABEL, HOME_LATITUDE, HOME_LONGITUDE)
    }

    val workPlace: Flow<GeoPlace?> = dataStore.data.map { preferences ->
        readPlace(preferences, WORK_PLACE_LABEL, WORK_LATITUDE, WORK_LONGITUDE)
    }

    val lastSelectedRadioIndex: Flow<Int> = dataStore.data.map { preferences ->
        preferences[LAST_SELECTED_RADIO_INDEX] ?: DEFAULT_RADIO_INDEX
    }

    val recentStopSearches: Flow<List<String>> = dataStore.data.map { preferences ->
        preferences[RECENT_STOP_SEARCHES]
            .orEmpty()
            .split(RECENT_STOP_DELIMITER)
            .filter(String::isNotBlank)
            .take(MAX_RECENT_STOP_SEARCHES)
    }

    suspend fun saveHomeStopId(stopId: String?) {
        saveStopId(HOME_STOP_ID, stopId)
    }

    suspend fun saveWorkStopId(stopId: String?) {
        saveStopId(WORK_STOP_ID, stopId)
    }

    suspend fun saveHomePlace(place: GeoPlace) {
        savePlace(HOME_STOP_ID, HOME_PLACE_LABEL, HOME_LATITUDE, HOME_LONGITUDE, place)
    }

    suspend fun saveWorkPlace(place: GeoPlace) {
        savePlace(WORK_STOP_ID, WORK_PLACE_LABEL, WORK_LATITUDE, WORK_LONGITUDE, place)
    }

    suspend fun saveLastSelectedRadioIndex(index: Int) {
        require(index in RADIO_INDEX_RANGE) { "Unknown radio station index: $index." }
        dataStore.edit { preferences ->
            preferences[LAST_SELECTED_RADIO_INDEX] = index
        }
    }

    suspend fun recordStopSearch(stopName: String) {
        val name = stopName.trim().replace(RECENT_STOP_DELIMITER, " ")
        require(name.isNotEmpty()) { "A stop name is required." }
        dataStore.edit { preferences ->
            val previousNames = preferences[RECENT_STOP_SEARCHES]
                .orEmpty()
                .split(RECENT_STOP_DELIMITER)
                .filter(String::isNotBlank)
                .filterNot { it.equals(name, ignoreCase = true) }
            preferences[RECENT_STOP_SEARCHES] = (listOf(name) + previousNames)
                .take(MAX_RECENT_STOP_SEARCHES)
                .joinToString(RECENT_STOP_DELIMITER)
        }
    }

    private suspend fun saveStopId(
        key: androidx.datastore.preferences.core.Preferences.Key<String>,
        stopId: String?
    ) {
        dataStore.edit { preferences ->
            removePlace(preferences, placeKeysFor(key))
            if (stopId == null) {
                preferences.remove(key)
            } else {
                preferences[key] = stopId
            }
        }
    }

    private suspend fun savePlace(
        legacyStopIdKey: Preferences.Key<String>,
        labelKey: Preferences.Key<String>,
        latitudeKey: Preferences.Key<Int>,
        longitudeKey: Preferences.Key<Int>,
        place: GeoPlace
    ) {
        dataStore.edit { preferences ->
            preferences.remove(legacyStopIdKey)
            preferences[labelKey] = place.label
            preferences[latitudeKey] = (place.latitude * COORDINATE_SCALE).roundToInt()
            preferences[longitudeKey] = (place.longitude * COORDINATE_SCALE).roundToInt()
        }
    }

    private fun readPlace(
        preferences: Preferences,
        labelKey: Preferences.Key<String>,
        latitudeKey: Preferences.Key<Int>,
        longitudeKey: Preferences.Key<Int>
    ): GeoPlace? {
        val label = preferences[labelKey] ?: return null
        val latitude = preferences[latitudeKey] ?: return null
        val longitude = preferences[longitudeKey] ?: return null
        return GeoPlace(
            label = label,
            latitude = latitude / COORDINATE_SCALE,
            longitude = longitude / COORDINATE_SCALE
        )
    }

    private fun placeKeysFor(
        legacyStopIdKey: Preferences.Key<String>
    ): PlacePreferenceKeys = if (legacyStopIdKey == HOME_STOP_ID) {
        PlacePreferenceKeys(HOME_PLACE_LABEL, HOME_LATITUDE, HOME_LONGITUDE)
    } else {
        PlacePreferenceKeys(WORK_PLACE_LABEL, WORK_LATITUDE, WORK_LONGITUDE)
    }

    private fun removePlace(
        preferences: androidx.datastore.preferences.core.MutablePreferences,
        keys: PlacePreferenceKeys
    ) {
        preferences.remove(keys.label)
        preferences.remove(keys.latitude)
        preferences.remove(keys.longitude)
    }

    private data class PlacePreferenceKeys(
        val label: Preferences.Key<String>,
        val latitude: Preferences.Key<Int>,
        val longitude: Preferences.Key<Int>
    )

    private companion object {
        val HOME_STOP_ID = stringPreferencesKey("homeStopId")
        val WORK_STOP_ID = stringPreferencesKey("workStopId")
        val HOME_PLACE_LABEL = stringPreferencesKey("homePlaceLabel")
        val HOME_LATITUDE = intPreferencesKey("homePlaceLatitudeE6")
        val HOME_LONGITUDE = intPreferencesKey("homePlaceLongitudeE6")
        val WORK_PLACE_LABEL = stringPreferencesKey("workPlaceLabel")
        val WORK_LATITUDE = intPreferencesKey("workPlaceLatitudeE6")
        val WORK_LONGITUDE = intPreferencesKey("workPlaceLongitudeE6")
        val LAST_SELECTED_RADIO_INDEX = intPreferencesKey("lastSelectedRadioIndex")
        val RECENT_STOP_SEARCHES = stringPreferencesKey("recentStopSearches")
        const val RECENT_STOP_DELIMITER = "\u001F"
        const val MAX_RECENT_STOP_SEARCHES = 3
        const val DEFAULT_RADIO_INDEX = 0
        const val COORDINATE_SCALE = 1_000_000.0
        val RADIO_INDEX_RANGE = 0..1
    }
}
