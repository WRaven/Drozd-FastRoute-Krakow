package pl.wraven.mpkfastroute.feature.search

import android.database.sqlite.SQLiteException
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.IOException
import java.time.LocalDateTime
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import pl.wraven.mpkfastroute.core.audio.RadioManager
import pl.wraven.mpkfastroute.core.audio.RadioStations
import pl.wraven.mpkfastroute.core.csa.CsaJourney
import pl.wraven.mpkfastroute.core.csa.Haversine
import pl.wraven.mpkfastroute.core.csa.JourneyStep
import pl.wraven.mpkfastroute.core.csa.RoutePlanner
import pl.wraven.mpkfastroute.core.database.AppDatabase
import pl.wraven.mpkfastroute.core.database.StopEntity
import pl.wraven.mpkfastroute.core.gtfs.GtfsServiceClock
import pl.wraven.mpkfastroute.core.gtfs.GtfsZipParser
import pl.wraven.mpkfastroute.core.live.LiveDeparturesResult
import pl.wraven.mpkfastroute.core.live.TtssDepartureRepository
import pl.wraven.mpkfastroute.core.location.AddressGeocoder
import pl.wraven.mpkfastroute.core.location.GeoPlace
import pl.wraven.mpkfastroute.core.location.LocationPermissionRequiredException
import pl.wraven.mpkfastroute.core.location.LocationUnavailableException
import pl.wraven.mpkfastroute.core.settings.AppPreferences

data class SearchUiState(
    val stops: List<StopEntity> = emptyList(),
    val originQuery: String = "",
    val destinationQuery: String = "",
    val originPlace: GeoPlace? = null,
    val destinationPlace: GeoPlace? = null,
    val originAddressCandidates: List<GeoPlace> = emptyList(),
    val addressCandidates: List<GeoPlace> = emptyList(),
    val nearbyOriginGroups: List<StopGroup> = emptyList(),
    val nearbyDestinationGroups: List<StopGroup> = emptyList(),
    val originStop: StopEntity? = null,
    val destinationStop: StopEntity? = null,
    val originStopOptions: List<StopEntity> = emptyList(),
    val destinationStopOptions: List<StopEntity> = emptyList(),
    val homeStopId: String? = null,
    val workStopId: String? = null,
    val homePlace: GeoPlace? = null,
    val workPlace: GeoPlace? = null,
    val journey: CsaJourney? = null,
    val liveDepartures: LiveDeparturesResult? = null,
    val isImporting: Boolean = false,
    val isSearching: Boolean = false,
    val progressMessage: String = "",
    val statusMessage: String? = null,
    val lastSelectedRadioIndex: Int = 0
) {
    val isScheduleEmpty: Boolean
        get() = stops.isEmpty()

    val hasHomeDestination: Boolean
        get() = homePlace != null || homeStopId != null

    val hasWorkDestination: Boolean
        get() = workPlace != null || workStopId != null
}

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val database: AppDatabase,
    private val gtfsZipParser: GtfsZipParser,
    private val routePlanner: RoutePlanner,
    private val ttssDepartureRepository: TtssDepartureRepository,
    private val contextEngine: ContextEngine,
    private val addressGeocoder: AddressGeocoder,
    private val appPreferences: AppPreferences,
    private val radioManager: RadioManager
) : ViewModel() {
    private val mutableUiState = MutableStateFlow(SearchUiState())
    val uiState = mutableUiState.asStateFlow()

    init {
        observeStops()
        observePreferences()
        ensureScheduleImported()
    }

    fun setDestinationQuery(query: String) {
        mutableUiState.update {
            it.copy(
                destinationQuery = query,
                destinationStop = null,
                destinationStopOptions = emptyList(),
                destinationPlace = null,
                addressCandidates = emptyList(),
                nearbyDestinationGroups = emptyList(),
                journey = null,
                liveDepartures = null,
                statusMessage = null
            )
        }
    }

    fun setOriginQuery(query: String) {
        mutableUiState.update {
            it.copy(
                originQuery = query,
                originPlace = null,
                originStop = null,
                originStopOptions = emptyList(),
                originAddressCandidates = emptyList(),
                nearbyOriginGroups = emptyList(),
                journey = null,
                liveDepartures = null,
                statusMessage = null
            )
        }
    }

    fun selectOrigin(group: StopGroup) {
        val state = mutableUiState.value
        val selectedFromAddress = state.nearbyOriginGroups.any { it.name == group.name }
        val place = state.originPlace.takeIf { selectedFromAddress }
            ?: GeoPlace(group.name, group.stops.first().stopLat, group.stops.first().stopLon)
        val representative = nearestStopToPlace(group, place)
        mutableUiState.update {
            it.copy(
                originQuery = if (selectedFromAddress) state.originQuery else group.name,
                originPlace = place,
                originStop = representative,
                originStopOptions = group.stops,
                originAddressCandidates = emptyList(),
                nearbyOriginGroups = emptyList(),
                journey = null,
                liveDepartures = null
            )
        }
    }

    fun useCurrentLocation() {
        viewModelScope.launch {
            mutableUiState.update { it.copy(isSearching = true, statusMessage = null) }
            try {
                val origin = contextEngine.findCurrentOrigin()
                mutableUiState.update {
                    it.copy(
                        originQuery = "",
                        originPlace = origin.place,
                        originStop = origin.stop,
                        originStopOptions = origin.stopOptions,
                        originAddressCandidates = emptyList(),
                        nearbyOriginGroups = emptyList(),
                        journey = null,
                        liveDepartures = null
                    )
                }
            } catch (exception: LocationPermissionRequiredException) {
                showMessage("Aby użyć lokalizacji, zezwól na dostęp do lokalizacji.")
            } catch (exception: LocationUnavailableException) {
                showMessage(exception.message.orEmpty())
            } catch (exception: IllegalStateException) {
                showMessage(exception.message.orEmpty())
            } catch (exception: SecurityException) {
                showMessage("Brak dostępu do lokalizacji.")
            } catch (exception: SQLiteException) {
                showMessage("Nie można odczytać przystanków: ${exception.message.orEmpty()}")
            } finally {
                mutableUiState.update { it.copy(isSearching = false) }
            }
        }
    }

    fun searchOriginAddress() {
        val query = mutableUiState.value.originQuery
        if (query.isBlank()) {
            showMessage("Wpisz adres, aby go wyszukać.")
            return
        }
        viewModelScope.launch {
            mutableUiState.update { it.copy(isSearching = true, statusMessage = null) }
            try {
                val addresses = addressGeocoder.search(query)
                if (addresses.isEmpty()) {
                    showMessage("Nie znaleziono adresu. Sprawdź wpisaną nazwę.")
                } else {
                    mutableUiState.update {
                        it.copy(
                            originAddressCandidates = addresses,
                            originPlace = null,
                            originStop = null,
                            originStopOptions = emptyList(),
                            nearbyOriginGroups = emptyList(),
                            journey = null,
                            liveDepartures = null
                        )
                    }
                }
            } catch (exception: IOException) {
                showMessage("Nie udało się wyszukać adresu: ${exception.message.orEmpty()}")
            } catch (exception: IllegalArgumentException) {
                showMessage("Nieprawidłowy adres: ${exception.message.orEmpty()}")
            } finally {
                mutableUiState.update { it.copy(isSearching = false) }
            }
        }
    }

    fun selectOriginAddress(place: GeoPlace) {
        viewModelScope.launch {
            val nearbyGroups = withContext(Dispatchers.Default) {
                nearbyStopGroups(mutableUiState.value.stops, place)
            }
            mutableUiState.update {
                it.copy(
                    originQuery = place.label,
                    originPlace = place,
                    originAddressCandidates = emptyList(),
                    nearbyOriginGroups = nearbyGroups,
                    originStop = null,
                    originStopOptions = emptyList(),
                    journey = null,
                    liveDepartures = null,
                    statusMessage = if (nearbyGroups.isEmpty()) {
                        "Nie znaleziono przystanków w rozkładzie."
                    } else {
                        null
                    }
                )
            }
        }
    }

    fun selectDestination(group: StopGroup) {
        val state = mutableUiState.value
        val selectedFromAddress = state.nearbyDestinationGroups.any { it.name == group.name }
        val place = state.destinationPlace.takeIf { selectedFromAddress }
            ?: GeoPlace(group.name, group.stops.first().stopLat, group.stops.first().stopLon)
        val representative = nearestStopToPlace(group, place)
        mutableUiState.update {
            it.copy(
                destinationQuery = if (selectedFromAddress) state.destinationQuery else group.name,
                destinationPlace = place,
                destinationStop = representative,
                destinationStopOptions = group.stops,
                addressCandidates = emptyList(),
                nearbyDestinationGroups = emptyList(),
                journey = null,
                liveDepartures = null
            )
        }
    }

    fun searchDestinationAddress() {
        val query = mutableUiState.value.destinationQuery
        if (query.isBlank()) {
            showMessage("Wpisz adres, aby go wyszukać.")
            return
        }
        viewModelScope.launch {
            mutableUiState.update { it.copy(isSearching = true, statusMessage = null) }
            try {
                val addresses = addressGeocoder.search(query)
                if (addresses.isEmpty()) {
                    showMessage("Nie znaleziono adresu. Sprawdź wpisaną nazwę.")
                } else {
                    mutableUiState.update {
                        it.copy(
                            addressCandidates = addresses,
                            destinationPlace = null,
                            destinationStop = null,
                            destinationStopOptions = emptyList(),
                            nearbyDestinationGroups = emptyList(),
                            journey = null,
                            liveDepartures = null
                        )
                    }
                }
            } catch (exception: IOException) {
                showMessage("Nie udało się wyszukać adresu: ${exception.message.orEmpty()}")
            } catch (exception: IllegalArgumentException) {
                showMessage("Nieprawidłowy adres: ${exception.message.orEmpty()}")
            } finally {
                mutableUiState.update { it.copy(isSearching = false) }
            }
        }
    }

    fun selectDestinationAddress(place: GeoPlace) {
        viewModelScope.launch {
            val nearbyGroups = withContext(Dispatchers.Default) {
                nearbyStopGroups(mutableUiState.value.stops, place)
            }
            mutableUiState.update {
                it.copy(
                    destinationQuery = place.label,
                    destinationPlace = place,
                    addressCandidates = emptyList(),
                    nearbyDestinationGroups = nearbyGroups,
                    destinationStop = null,
                    destinationStopOptions = emptyList(),
                    journey = null,
                    liveDepartures = null,
                    statusMessage = if (nearbyGroups.isEmpty()) {
                        "Nie znaleziono przystanków w rozkładzie."
                    } else {
                        null
                    }
                )
            }
        }
    }

    fun searchRoute() {
        val state = mutableUiState.value
        val origin = state.originStop
        val destination = state.destinationStop
        val originOptions = state.originStopOptions.ifEmpty { listOfNotNull(origin) }
        val destinationOptions = state.destinationStopOptions.ifEmpty { listOfNotNull(destination) }
        if (originOptions.isEmpty() || destinationOptions.isEmpty()) {
            showMessage("Ustaw przystanek początkowy i docelowy.")
            return
        }

        viewModelScope.launch {
            mutableUiState.update { it.copy(isSearching = true, statusMessage = null) }
            try {
                val now = LocalDateTime.now()
                val serviceTime = GtfsServiceClock.from(now)
                val journey = routePlanner.findEarliestJourney(
                    originStopIds = originOptions.mapTo(mutableSetOf()) { it.stopId },
                    destinationStopIds = destinationOptions.mapTo(mutableSetOf()) { it.stopId },
                    serviceDate = serviceTime.serviceDate,
                    startTimeSeconds = serviceTime.timeSeconds
                )
                val transitSteps = journey?.steps?.filterIsInstance<JourneyStep.Transit>().orEmpty()
                val selectedOrigin = transitSteps.firstOrNull()
                    ?.connection
                    ?.departureStopId
                    ?.let { database.stopDao().getById(it) }
                    ?: originOptions.first()
                val selectedDestination = transitSteps.lastOrNull()
                    ?.connection
                    ?.arrivalStopId
                    ?.let { database.stopDao().getById(it) }
                    ?: destinationOptions.first()
                val liveDepartures = ttssDepartureRepository.getDepartures(
                    stopId = selectedOrigin.stopId,
                    serviceDate = serviceTime.serviceDate,
                    startTimeSeconds = serviceTime.timeSeconds,
                    nowTimeSeconds = now.toLocalTime().toSecondOfDay()
                )
                mutableUiState.update {
                    it.copy(
                        originStop = selectedOrigin,
                        destinationStop = selectedDestination,
                        journey = journey,
                        liveDepartures = liveDepartures,
                        statusMessage = if (journey == null) {
                            "Nie znaleziono połączenia w ciągu najbliższych dwóch godzin."
                        } else {
                            null
                        }
                    )
                }
            } catch (exception: IllegalArgumentException) {
                showMessage("Nie można wyszukać trasy: ${exception.message.orEmpty()}")
            } catch (exception: SQLiteException) {
                showMessage("Nie można odczytać rozkładu: ${exception.message.orEmpty()}")
            } finally {
                mutableUiState.update { it.copy(isSearching = false) }
            }
        }
    }

    fun refreshSchedule() {
        viewModelScope.launch { importSchedule() }
    }

    fun saveSelectedStopAsHome() {
        saveSelectedPlace(isHome = true)
    }

    fun saveSelectedStopAsWork() {
        saveSelectedPlace(isHome = false)
    }

    fun routeHome() {
        runContextAction(SavedDestination.HOME)
    }

    fun routeToWork() {
        runContextAction(SavedDestination.WORK)
    }

    fun randomJourney() {
        viewModelScope.launch {
            mutableUiState.update { it.copy(isSearching = true, statusMessage = null) }
            try {
                showQuickActionResult(contextEngine.findRandomJourney())
            } catch (exception: LocationPermissionRequiredException) {
                showMessage(exception.message.orEmpty())
            } catch (exception: LocationUnavailableException) {
                showMessage(exception.message.orEmpty())
            } catch (exception: IllegalStateException) {
                showMessage(exception.message.orEmpty())
            } catch (exception: SecurityException) {
                showMessage("Brak dostępu do lokalizacji.")
            } catch (exception: SQLiteException) {
                showMessage("Nie można odczytać przystanków: ${exception.message.orEmpty()}")
            } catch (exception: IOException) {
                showMessage("Nie można odczytać zapisanych ustawień: ${exception.message.orEmpty()}")
            } finally {
                mutableUiState.update { it.copy(isSearching = false) }
            }
        }
    }

    fun playLastSelectedRadio() {
        viewModelScope.launch {
            try {
                radioManager.playLastSelectedStation()
            } catch (exception: IOException) {
                showMessage("Nie można odczytać ustawień radia: ${exception.message.orEmpty()}")
            } catch (exception: IllegalStateException) {
                showMessage("Nie można uruchomić radia: ${exception.message.orEmpty()}")
            }
        }
    }

    fun playRadioStation(index: Int) {
        if (index !in RadioStations.all.indices) {
            showMessage("Wybrano nieprawidłową stację radiową.")
            return
        }
        viewModelScope.launch {
            try {
                radioManager.playStation(index)
            } catch (exception: IOException) {
                showMessage("Nie można zapisać ustawień radia: ${exception.message.orEmpty()}")
            } catch (exception: IllegalStateException) {
                showMessage("Nie można uruchomić radia: ${exception.message.orEmpty()}")
            }
        }
    }

    fun stopRadio() {
        try {
            radioManager.stop()
        } catch (exception: IllegalStateException) {
            showMessage("Nie można zatrzymać radia: ${exception.message.orEmpty()}")
        }
    }

    fun showMessage(message: String) {
        mutableUiState.update { it.copy(statusMessage = message) }
    }

    fun clearMessage() {
        mutableUiState.update { it.copy(statusMessage = null) }
    }

    private fun observeStops() {
        viewModelScope.launch {
            database.stopDao().observeAll().collect { stops ->
                mutableUiState.update { it.copy(stops = stops) }
            }
        }
    }

    private fun observePreferences() {
        viewModelScope.launch {
            appPreferences.homeStopId.collect { stopId ->
                mutableUiState.update { it.copy(homeStopId = stopId) }
            }
        }
        viewModelScope.launch {
            appPreferences.homePlace.collect { place ->
                mutableUiState.update { it.copy(homePlace = place) }
            }
        }
        viewModelScope.launch {
            appPreferences.workStopId.collect { stopId ->
                mutableUiState.update { it.copy(workStopId = stopId) }
            }
        }
        viewModelScope.launch {
            appPreferences.workPlace.collect { place ->
                mutableUiState.update { it.copy(workPlace = place) }
            }
        }
        viewModelScope.launch {
            appPreferences.lastSelectedRadioIndex.collect { index ->
                mutableUiState.update { it.copy(lastSelectedRadioIndex = index) }
            }
        }
    }

    private fun ensureScheduleImported() {
        viewModelScope.launch {
            try {
                if (database.stopDao().count() == 0) {
                    importSchedule()
                }
            } catch (exception: SQLiteException) {
                showMessage("Nie można sprawdzić lokalnego rozkładu: ${exception.message.orEmpty()}")
            }
        }
    }

    private suspend fun importSchedule() {
        if (mutableUiState.value.isImporting) {
            return
        }
        mutableUiState.update {
            it.copy(isImporting = true, progressMessage = "Przygotowanie importu...", statusMessage = null)
        }
        try {
            gtfsZipParser.importSchedule { progress ->
                mutableUiState.update { it.copy(progressMessage = progress) }
            }
            showMessage("Rozkład został zaktualizowany.")
        } catch (exception: IOException) {
            showMessage("Nie udało się pobrać rozkładu: ${exception.message.orEmpty()}")
        } catch (exception: IllegalArgumentException) {
            showMessage("Rozkład ma nieprawidłowe dane: ${exception.message.orEmpty()}")
        } catch (exception: IllegalStateException) {
            showMessage("Nie udało się zaimportować rozkładu: ${exception.message.orEmpty()}")
        } catch (exception: SQLiteException) {
            showMessage("Nie udało się zapisać rozkładu: ${exception.message.orEmpty()}")
        } finally {
            mutableUiState.update {
                it.copy(isImporting = false, progressMessage = "")
            }
        }
    }

    private fun saveSelectedPlace(isHome: Boolean) {
        val place = mutableUiState.value.destinationPlace
        if (place == null) {
            showMessage("Najpierw wybierz adres lub przystanek docelowy.")
            return
        }
        viewModelScope.launch {
            try {
                if (isHome) {
                    appPreferences.saveHomePlace(place)
                } else {
                    appPreferences.saveWorkPlace(place)
                }
                showMessage(
                    if (isHome) "Zapisano adres domu." else "Zapisano adres pracy."
                )
            } catch (exception: IOException) {
                showMessage("Nie można zapisać lokalizacji: ${exception.message.orEmpty()}")
            }
        }
    }

    private fun nearbyStopGroups(stops: List<StopEntity>, place: GeoPlace): List<StopGroup> =
        StopNameGrouper.group(stops)
            .sortedBy { group ->
                group.stops.minOf { stop ->
                    Haversine.distanceMeters(
                        place.latitude,
                        place.longitude,
                        stop.stopLat,
                        stop.stopLon
                    )
                }
            }
            .take(MAX_NEARBY_STOPS)

    private fun nearestStopToPlace(group: StopGroup, place: GeoPlace): StopEntity =
        group.stops.minByOrNull { stop ->
            Haversine.distanceMeters(
                place.latitude,
                place.longitude,
                stop.stopLat,
                stop.stopLon
            )
        } ?: throw IllegalStateException("A stop group cannot be empty.")

    private fun runContextAction(destination: SavedDestination) {
        viewModelScope.launch {
            mutableUiState.update { it.copy(isSearching = true, statusMessage = null) }
            try {
                showQuickActionResult(
                    result = contextEngine.routeToSavedStop(destination),
                    savedDestination = destination
                )
            } catch (exception: LocationPermissionRequiredException) {
                showMessage(exception.message.orEmpty())
            } catch (exception: LocationUnavailableException) {
                showMessage(exception.message.orEmpty())
            } catch (exception: SecurityException) {
                showMessage("Brak dostępu do lokalizacji.")
            } catch (exception: IllegalStateException) {
                showMessage(exception.message.orEmpty())
            } catch (exception: SQLiteException) {
                showMessage("Nie można odczytać przystanków: ${exception.message.orEmpty()}")
            } catch (exception: IOException) {
                showMessage("Nie można odczytać zapisanych ustawień: ${exception.message.orEmpty()}")
            } finally {
                mutableUiState.update { it.copy(isSearching = false) }
            }
        }
    }

    private fun showQuickActionResult(
        result: QuickActionResult,
        savedDestination: SavedDestination? = null
    ) {
        when (result) {
            is QuickActionResult.JourneyFound -> mutableUiState.update {
                it.copy(
                    originQuery = "",
                    originStop = result.origin,
                    destinationStop = result.destination,
                    originStopOptions = result.originStopOptions,
                    destinationStopOptions = result.destinationStopOptions,
                    originPlace = result.originPlace,
                    destinationPlace = result.destinationPlace,
                    destinationQuery = result.destinationPlace.label,
                    originAddressCandidates = emptyList(),
                    addressCandidates = emptyList(),
                    nearbyOriginGroups = emptyList(),
                    nearbyDestinationGroups = emptyList(),
                    journey = result.journey,
                    liveDepartures = null,
                    statusMessage = null
                )
            }

            is QuickActionResult.AlreadyAtDestination -> showMessage(
                when (savedDestination) {
                    SavedDestination.HOME -> "Jesteś już w domu!"
                    SavedDestination.WORK -> "Jesteś już w pracy!"
                    null -> "Jesteś już na miejscu: ${result.destinationName}."
                }
            )

            QuickActionResult.NoJourneyFound ->
                showMessage("Nie znaleziono połączenia w wybranym przedziale czasu.")

            QuickActionResult.NoRandomDestination ->
                showMessage("Nie znaleziono przystanku oddalonego o co najmniej 3 km.")
        }
    }

    private companion object {
        const val MAX_NEARBY_STOPS = 6
    }
}
