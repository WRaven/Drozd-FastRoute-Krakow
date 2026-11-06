package pl.wraven.mpkfastroute.core.settings

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlin.math.roundToInt
import pl.wraven.mpkfastroute.core.audio.RadioStation
import pl.wraven.mpkfastroute.core.location.GeoPlace

private val Context.appPreferencesDataStore by preferencesDataStore(
    name = "mpk_fastroute_preferences"
)

class AppPreferences @Inject constructor(
    @ApplicationContext context: Context,
    private val json: Json
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

    val homeSchedule: Flow<DestinationSchedule?> = dataStore.data.map { preferences ->
        DestinationSchedule.decode(preferences[HOME_SCHEDULE])
    }

    val workSchedule: Flow<DestinationSchedule?> = dataStore.data.map { preferences ->
        DestinationSchedule.decode(preferences[WORK_SCHEDULE])
    }

    val homeCommuteNotificationOccurrence: Flow<String?> = dataStore.data.map { preferences ->
        preferences[HOME_COMMUTE_NOTIFICATION_OCCURRENCE]
    }

    val workCommuteNotificationOccurrence: Flow<String?> = dataStore.data.map { preferences ->
        preferences[WORK_COMMUTE_NOTIFICATION_OCCURRENCE]
    }

    val skippedUpdateVersion: Flow<String?> = dataStore.data.map { preferences ->
        preferences[SKIPPED_UPDATE_VERSION]
    }

    val customRoute: Flow<SavedRoute?> = dataStore.data.map { preferences ->
        preferences[CUSTOM_ROUTE]?.let { json.decodeFromString<SavedRoute>(it) }
    }

    val recentAddressPlaces: Flow<List<GeoPlace>> = dataStore.data.map { preferences ->
        preferences[RECENT_ADDRESS_PLACES]
            ?.let { json.decodeFromString<List<GeoPlace>>(it) }
            .orEmpty()
            .take(MAX_RECENT_ADDRESS_PLACES)
    }

    val radioStationsJson: Flow<String?> = dataStore.data.map { preferences ->
        preferences[RADIO_STATIONS]
    }

    val lastSelectedRadioId: Flow<String?> = dataStore.data.map { preferences ->
        preferences[LAST_SELECTED_RADIO_ID]
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

    suspend fun saveHomePlace(place: GeoPlace, schedule: DestinationSchedule) {
        savePlace(
            HOME_STOP_ID,
            HOME_PLACE_LABEL,
            HOME_LATITUDE,
            HOME_LONGITUDE,
            HOME_SCHEDULE,
            place,
            schedule
        )
    }

    suspend fun saveWorkPlace(place: GeoPlace, schedule: DestinationSchedule) {
        savePlace(
            WORK_STOP_ID,
            WORK_PLACE_LABEL,
            WORK_LATITUDE,
            WORK_LONGITUDE,
            WORK_SCHEDULE,
            place,
            schedule
        )
    }

    suspend fun saveHomeSchedule(schedule: DestinationSchedule) {
        saveSchedule(HOME_SCHEDULE, schedule)
    }

    suspend fun saveWorkSchedule(schedule: DestinationSchedule) {
        saveSchedule(WORK_SCHEDULE, schedule)
    }

    suspend fun markHomeCommuteNotificationOccurrence(occurrence: String) {
        dataStore.edit { preferences ->
            preferences[HOME_COMMUTE_NOTIFICATION_OCCURRENCE] = occurrence
        }
    }

    suspend fun markWorkCommuteNotificationOccurrence(occurrence: String) {
        dataStore.edit { preferences ->
            preferences[WORK_COMMUTE_NOTIFICATION_OCCURRENCE] = occurrence
        }
    }

    suspend fun saveSkippedUpdateVersion(version: String) {
        dataStore.edit { preferences ->
            preferences[SKIPPED_UPDATE_VERSION] = version
        }
    }

    suspend fun saveCustomRoute(route: SavedRoute) {
        dataStore.edit { preferences ->
            preferences[CUSTOM_ROUTE] = json.encodeToString(route)
        }
    }

    suspend fun recordAddressPlace(place: GeoPlace) {
        dataStore.edit { preferences ->
            val previousPlaces = preferences[RECENT_ADDRESS_PLACES]
                ?.let { json.decodeFromString<List<GeoPlace>>(it) }
                .orEmpty()
                .filterNot {
                    it.label.equals(place.label, ignoreCase = true) &&
                        it.latitude == place.latitude &&
                        it.longitude == place.longitude
                }
            preferences[RECENT_ADDRESS_PLACES] = json.encodeToString(
                (listOf(place) + previousPlaces).take(MAX_RECENT_ADDRESS_PLACES)
            )
        }
    }

    suspend fun saveRadioStations(stations: List<RadioStation>) {
        dataStore.edit { preferences ->
            preferences[RADIO_STATIONS] = json.encodeToString(stations)
        }
    }

    suspend fun saveLastSelectedRadioId(id: String) {
        dataStore.edit { preferences ->
            preferences[LAST_SELECTED_RADIO_ID] = id
        }
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
        scheduleKey: Preferences.Key<String>,
        place: GeoPlace,
        schedule: DestinationSchedule
    ) {
        dataStore.edit { preferences ->
            preferences.remove(legacyStopIdKey)
            preferences[labelKey] = place.label
            preferences[latitudeKey] = (place.latitude * COORDINATE_SCALE).roundToInt()
            preferences[longitudeKey] = (place.longitude * COORDINATE_SCALE).roundToInt()
            preferences[scheduleKey] = schedule.encode()
        }
    }

    private suspend fun saveSchedule(
        key: Preferences.Key<String>,
        schedule: DestinationSchedule
    ) {
        dataStore.edit { preferences ->
            preferences[key] = schedule.encode()
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
        val HOME_SCHEDULE = stringPreferencesKey("homeSchedule")
        val WORK_SCHEDULE = stringPreferencesKey("workSchedule")
        val HOME_COMMUTE_NOTIFICATION_OCCURRENCE =
            stringPreferencesKey("homeCommuteNotificationOccurrence")
        val WORK_COMMUTE_NOTIFICATION_OCCURRENCE =
            stringPreferencesKey("workCommuteNotificationOccurrence")
        val SKIPPED_UPDATE_VERSION = stringPreferencesKey("skippedUpdateVersion")
        val CUSTOM_ROUTE = stringPreferencesKey("customRoute")
        val RECENT_ADDRESS_PLACES = stringPreferencesKey("recentAddressPlaces")
        val RADIO_STATIONS = stringPreferencesKey("radioStations")
        val LAST_SELECTED_RADIO_ID = stringPreferencesKey("lastSelectedRadioId")
        val LAST_SELECTED_RADIO_INDEX = intPreferencesKey("lastSelectedRadioIndex")
        val RECENT_STOP_SEARCHES = stringPreferencesKey("recentStopSearches")
        const val RECENT_STOP_DELIMITER = "\u001F"
        const val MAX_RECENT_STOP_SEARCHES = 3
        const val MAX_RECENT_ADDRESS_PLACES = 5
        const val DEFAULT_RADIO_INDEX = 0
        const val COORDINATE_SCALE = 1_000_000.0
        val RADIO_INDEX_RANGE = 0..1
    }
}
