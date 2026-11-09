package pl.wraven.mpkfastroute.core.audio

import java.net.URI
import java.net.URISyntaxException
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import pl.wraven.mpkfastroute.core.settings.AppPreferences

class RadioPreferences @Inject constructor(
    private val appPreferences: AppPreferences,
    private val json: Json
) {
    val stations: Flow<List<RadioStation>> = appPreferences.radioStationsJson.map { encoded ->
        val configuredStations =
            encoded?.let { json.decodeFromString<List<RadioStation>>(it) } ?: RadioStations.all
        filterUnavailableRadioStations(configuredStations).ifEmpty { RadioStations.all }
    }

    val lastSelectedRadioId: Flow<String?> = combine(
        appPreferences.lastSelectedRadioId,
        appPreferences.lastSelectedRadioIndex,
        stations
    ) { savedId, legacyIndex, currentStations ->
        savedId?.takeIf { id -> currentStations.any { it.id == id } }
            ?: currentStations.getOrNull(legacyIndex)?.id
            ?: currentStations.firstOrNull()?.id
    }

    suspend fun saveStations(stations: List<RadioStation>) {
        val availableStations = filterUnavailableRadioStations(stations)
        validateStations(availableStations)
        val selectedId = lastSelectedRadioId.first()
        appPreferences.saveRadioStations(availableStations)
        if (selectedId !in availableStations.map(RadioStation::id)) {
            appPreferences.saveLastSelectedRadioId(availableStations.first().id)
        }
    }

    suspend fun saveLastSelectedStation(id: String) {
        require(stations.first().any { it.id == id }) {
            "Unknown radio station id: $id."
        }
        appPreferences.saveLastSelectedRadioId(id)
    }

    private fun validateStations(stations: List<RadioStation>) {
        require(stations.isNotEmpty()) { "At least one radio station is required." }
        require(stations.map(RadioStation::id).distinct().size == stations.size) {
            "Radio station ids must be unique."
        }
        stations.forEach { station ->
            require(station.id.isNotBlank()) { "A radio station id is required." }
            require(station.name.isNotBlank()) { "A radio station name is required." }
            require(isHttpUrl(station.streamUrl)) {
                "Radio station URLs must be valid HTTP or HTTPS addresses."
            }
        }
    }

    private fun isHttpUrl(value: String): Boolean {
        val uri = try {
            URI(value)
        } catch (_: URISyntaxException) {
            return false
        }
        return (
            uri.scheme.equals("http", ignoreCase = true) ||
                uri.scheme.equals("https", ignoreCase = true)
            ) && !uri.host.isNullOrBlank()
    }
}

internal fun filterUnavailableRadioStations(stations: List<RadioStation>): List<RadioStation> =
    stations.filterNot { it.id == UNAVAILABLE_RADIO_STATION_ID }

private const val UNAVAILABLE_RADIO_STATION_ID = "radio-krakow"
