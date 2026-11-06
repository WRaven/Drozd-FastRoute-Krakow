package pl.wraven.mpkfastroute.feature.search

import android.database.sqlite.SQLiteException
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.IOException
import java.time.LocalDateTime
import kotlinx.coroutines.Job
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import pl.wraven.mpkfastroute.core.audio.RadioManager
import pl.wraven.mpkfastroute.core.audio.RadioPreferences
import pl.wraven.mpkfastroute.core.audio.RadioStation
import pl.wraven.mpkfastroute.core.audio.RadioStations
import pl.wraven.mpkfastroute.core.csa.CsaJourney
import pl.wraven.mpkfastroute.core.csa.FootpathGenerator
import pl.wraven.mpkfastroute.core.csa.Haversine
import pl.wraven.mpkfastroute.core.csa.JourneyStep
import pl.wraven.mpkfastroute.core.csa.RoutePlanner
import pl.wraven.mpkfastroute.core.csa.StopAccess
import pl.wraven.mpkfastroute.core.database.AppDatabase
import pl.wraven.mpkfastroute.core.database.StopEntity
import pl.wraven.mpkfastroute.core.diagnostics.SearchDiagnostics
import pl.wraven.mpkfastroute.core.gtfs.GtfsServiceClock
import pl.wraven.mpkfastroute.core.gtfs.GtfsZipParser
import pl.wraven.mpkfastroute.core.live.LiveDeparturesResult
import pl.wraven.mpkfastroute.core.live.TtssDepartureRepository
import pl.wraven.mpkfastroute.core.location.AddressGeocoder
import pl.wraven.mpkfastroute.core.location.GeoPlace
import pl.wraven.mpkfastroute.core.location.LocationPermissionRequiredException
import pl.wraven.mpkfastroute.core.location.LocationUnavailableException
import pl.wraven.mpkfastroute.core.settings.AppPreferences
import pl.wraven.mpkfastroute.core.settings.DestinationSchedule
import pl.wraven.mpkfastroute.core.settings.SavedRoute
import pl.wraven.mpkfastroute.core.update.GitHubUpdateChecker
import pl.wraven.mpkfastroute.core.update.NoStableReleaseAvailableException
import pl.wraven.mpkfastroute.core.update.UpdateCheckResult

data class SearchUiState(
    val stops: List<StopEntity> = emptyList(),
    val recentStopSearches: List<String> = emptyList(),
    val recentAddressPlaces: List<GeoPlace> = emptyList(),
    val originQuery: String = "",
    val destinationQuery: String = "",
    val originPlace: GeoPlace? = null,
    val destinationPlace: GeoPlace? = null,
    val originAddressCandidates: List<GeoPlace> = emptyList(),
    val addressCandidates: List<GeoPlace> = emptyList(),
    val originAddressSearchError: String? = null,
    val destinationAddressSearchError: String? = null,
    val isSearchingOriginAddresses: Boolean = false,
    val isSearchingDestinationAddresses: Boolean = false,
    val nearbyOriginGroups: List<StopGroup> = emptyList(),
    val nearbyDestinationGroups: List<StopGroup> = emptyList(),
    val originStop: StopEntity? = null,
    val destinationStop: StopEntity? = null,
    val originStopOptions: List<StopEntity> = emptyList(),
    val destinationStopOptions: List<StopEntity> = emptyList(),
    val originStopManuallySelected: Boolean = false,
    val destinationStopManuallySelected: Boolean = false,
    val homeStopId: String? = null,
    val workStopId: String? = null,
    val homePlace: GeoPlace? = null,
    val workPlace: GeoPlace? = null,
    val homeSchedule: DestinationSchedule? = null,
    val workSchedule: DestinationSchedule? = null,
    val customRoute: SavedRoute? = null,
    val journey: CsaJourney? = null,
    val journeyOptions: List<CsaJourney> = emptyList(),
    val arrivalTarget: LocalDateTime? = null,
    val liveDepartures: LiveDeparturesResult? = null,
    val isImporting: Boolean = false,
    val isSearching: Boolean = false,
    val progressMessage: String = "",
    val diagnosticEntries: List<String> = emptyList(),
    val routeSearchMessage: String? = null,
    val statusMessage: String? = null,
    val radioStations: List<RadioStation> = RadioStations.all,
    val lastSelectedRadioId: String? = RadioStations.all.firstOrNull()?.id,
    val isRadioPlaying: Boolean = false,
    val isCheckingUpdates: Boolean = false,
    val updateCheckMessage: String? = null,
    val updateCheckResult: UpdateCheckResult? = null
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
    private val radioManager: RadioManager,
    private val radioPreferences: RadioPreferences,
    private val updateChecker: GitHubUpdateChecker,
    private val diagnostics: SearchDiagnostics
) : ViewModel() {
    private val mutableUiState = MutableStateFlow(SearchUiState())
    val uiState = mutableUiState.asStateFlow()
    private var originAddressSearchJob: Job? = null
    private var destinationAddressSearchJob: Job? = null

    init {
        observeStops()
        observePreferences()
        observeDiagnostics()
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
                destinationAddressSearchError = null,
                isSearchingDestinationAddresses = false,
                nearbyDestinationGroups = emptyList(),
                journey = null,
                journeyOptions = emptyList(),
                arrivalTarget = null,
                liveDepartures = null,
                routeSearchMessage = null,
                statusMessage = null,
                destinationStopManuallySelected = false
            )
        }
        scheduleAddressSuggestions(query, isOrigin = false)
    }

    fun setOriginQuery(query: String) {
        mutableUiState.update {
            it.copy(
                originQuery = query,
                originPlace = null,
                originStop = null,
                originStopOptions = emptyList(),
                originAddressCandidates = emptyList(),
                originAddressSearchError = null,
                isSearchingOriginAddresses = false,
                nearbyOriginGroups = emptyList(),
                journey = null,
                journeyOptions = emptyList(),
                arrivalTarget = null,
                liveDepartures = null,
                routeSearchMessage = null,
                statusMessage = null,
                originStopManuallySelected = false
            )
        }
        scheduleAddressSuggestions(query, isOrigin = true)
    }

    fun selectOrigin(group: StopGroup) {
        originAddressSearchJob?.cancel()
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
                originAddressSearchError = null,
                isSearchingOriginAddresses = false,
                nearbyOriginGroups = emptyList(),
                journey = null,
                journeyOptions = emptyList(),
                arrivalTarget = null,
                routeSearchMessage = null,
                originStopManuallySelected = true,
                liveDepartures = null
            )
        }
        recordRecentStopSearch(group.name)
    }

    fun useCurrentLocation() {
        originAddressSearchJob?.cancel()
        viewModelScope.launch {
            mutableUiState.update { it.copy(isSearching = true, statusMessage = null) }
            try {
                val origin = contextEngine.findCurrentOrigin()
                val allStops = mutableUiState.value.stops.ifEmpty {
                    database.stopDao().getAll()
                }
                val nearbyGroups = withContext(Dispatchers.Default) {
                    nearbyStopGroups(allStops, origin.place)
                }
                mutableUiState.update {
                    it.copy(
                        originQuery = "",
                        originPlace = origin.place,
                        originStop = origin.stop,
                        originStopOptions = origin.stopOptions,
                        originAddressCandidates = emptyList(),
                        originAddressSearchError = null,
                        isSearchingOriginAddresses = false,
                        nearbyOriginGroups = nearbyGroups,
                        journey = null,
                        journeyOptions = emptyList(),
                        routeSearchMessage = null,
                        originStopManuallySelected = false,
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

    fun selectOriginAddress(place: GeoPlace) {
        originAddressSearchJob?.cancel()
        viewModelScope.launch {
            val nearbyGroups = withContext(Dispatchers.Default) {
                nearbyStopGroups(mutableUiState.value.stops, place)
            }
            mutableUiState.update {
                it.copy(
                    originQuery = place.label,
                    originPlace = place,
                    originAddressCandidates = emptyList(),
                    originAddressSearchError = null,
                    isSearchingOriginAddresses = false,
                    nearbyOriginGroups = nearbyGroups,
                    originStop = null,
                    originStopOptions = emptyList(),
                    journey = null,
                    journeyOptions = emptyList(),
                    routeSearchMessage = null,
                    originStopManuallySelected = false,
                    arrivalTarget = null,
                    liveDepartures = null,
                    statusMessage = if (nearbyGroups.isEmpty()) {
                        "Nie znaleziono przystanków w rozkładzie."
                    } else {
                        null
                    }
                )
            }
            recordRecentAddress(place)
        }
    }

    fun selectDestination(group: StopGroup) {
        destinationAddressSearchJob?.cancel()
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
                destinationAddressSearchError = null,
                isSearchingDestinationAddresses = false,
                nearbyDestinationGroups = emptyList(),
                journey = null,
                journeyOptions = emptyList(),
                arrivalTarget = null,
                routeSearchMessage = null,
                destinationStopManuallySelected = true,
                liveDepartures = null
            )
        }
        recordRecentStopSearch(group.name)
    }

    fun selectDestinationAddress(place: GeoPlace) {
        destinationAddressSearchJob?.cancel()
        viewModelScope.launch {
            val nearbyGroups = withContext(Dispatchers.Default) {
                nearbyStopGroups(mutableUiState.value.stops, place)
            }
            mutableUiState.update {
                it.copy(
                    destinationQuery = place.label,
                    destinationPlace = place,
                    addressCandidates = emptyList(),
                    destinationAddressSearchError = null,
                    isSearchingDestinationAddresses = false,
                    nearbyDestinationGroups = nearbyGroups,
                    destinationStop = null,
                    destinationStopOptions = emptyList(),
                    journey = null,
                    journeyOptions = emptyList(),
                    routeSearchMessage = null,
                    destinationStopManuallySelected = false,
                    arrivalTarget = null,
                    liveDepartures = null,
                    statusMessage = if (nearbyGroups.isEmpty()) {
                        "Nie znaleziono przystanków w rozkładzie."
                    } else {
                        null
                    }
                )
            }
            recordRecentAddress(place)
        }
    }

    fun searchRoute() {
        val state = mutableUiState.value
        if (state.isImporting) {
            diagnostics.record("Wyszukiwanie wstrzymane: trwa przygotowanie indeksu.")
            showMessage("Trwa przygotowanie lokalnego indeksu połączeń. Spróbuj ponownie za chwilę.")
            return
        }
        val originPlace = state.originPlace ?: state.originStop?.let(::placeForStop)
        val destinationPlace = state.destinationPlace ?: state.destinationStop?.let(::placeForStop)
        if (originPlace == null || destinationPlace == null) {
            showMessage("Ustaw lokalizację początkową i adres lub przystanek docelowy.")
            return
        }

        val originAccesses = buildStopAccesses(
            place = originPlace,
            selectedStops = state.originStopOptions.ifEmpty {
                listOfNotNull(state.originStop)
            },
            nearbyGroups = state.nearbyOriginGroups,
            allStops = state.stops,
            manuallySelected = state.originStopManuallySelected
        )
        val destinationAccesses = buildStopAccesses(
            place = destinationPlace,
            selectedStops = state.destinationStopOptions.ifEmpty {
                listOfNotNull(state.destinationStop)
            },
            nearbyGroups = state.nearbyDestinationGroups,
            allStops = state.stops,
            manuallySelected = state.destinationStopManuallySelected
        )
        if (originAccesses.isEmpty()) {
            showMessage("Nie znaleziono przystanków w promieniu 1,5 km od początku trasy.")
            return
        }
        if (destinationAccesses.isEmpty()) {
            showMessage("Nie znaleziono przystanków w promieniu 1,5 km od celu.")
            return
        }

        viewModelScope.launch {
            diagnostics.record(
                "Przygotowano wyszukiwanie dla ${originAccesses.size} początkowych i " +
                    "${destinationAccesses.size} końcowych przystanków."
            )
            mutableUiState.update {
                it.copy(
                    isSearching = true,
                    routeSearchMessage = null,
                    statusMessage = null,
                    journey = null,
                    journeyOptions = emptyList(),
                    arrivalTarget = null,
                    liveDepartures = null
                )
            }
            try {
                val now = LocalDateTime.now()
                val serviceTime = GtfsServiceClock.from(now)
                val candidates = routePlanner.findJourneys(
                    originAccesses = originAccesses,
                    destinationAccesses = destinationAccesses,
                    serviceDate = serviceTime.serviceDate,
                    startTimeSeconds = serviceTime.timeSeconds,
                    maxResults = MAX_CSA_JOURNEY_CANDIDATES
                )
                val journeyOptions = selectRouteAlternatives(candidates)
                diagnostics.record(
                    "Wyniki: ${candidates.size} kandydatów, " +
                        "${journeyOptions.size} tras po wyborze alternatyw."
                )
                val fastestJourney = journeyOptions.firstOrNull()
                val transitSteps = fastestJourney?.steps
                    ?.filterIsInstance<JourneyStep.Transit>()
                    .orEmpty()
                val stopsById = state.stops.associateBy(StopEntity::stopId)
                val firstTransitStopId = transitSteps.firstOrNull()
                    ?.connection
                    ?.departureStopId
                val lastTransitStopId = transitSteps.lastOrNull()
                    ?.connection
                    ?.arrivalStopId
                val selectedOrigin = firstTransitStopId?.let(stopsById::get)
                    ?: fastestJourney?.originAccess?.stopId?.let(stopsById::get)
                    ?: state.originStop
                val selectedDestination = lastTransitStopId?.let(stopsById::get)
                    ?: fastestJourney?.destinationAccess?.stopId?.let(stopsById::get)
                    ?: state.destinationStop
                mutableUiState.update {
                    it.copy(
                        originStop = selectedOrigin,
                        destinationStop = selectedDestination,
                        journey = fastestJourney,
                        journeyOptions = journeyOptions,
                        liveDepartures = null,
                        routeSearchMessage = if (fastestJourney == null) {
                            "Nie znaleziono połączenia w ciągu najbliższych dwóch godzin."
                        } else {
                            null
                        },
                        statusMessage = if (fastestJourney == null) {
                            "Nie znaleziono połączenia w ciągu najbliższych dwóch godzin."
                        } else {
                            null
                        }
                    )
                }
                if (fastestJourney != null && firstTransitStopId != null) {
                    diagnostics.record("Trasa gotowa; odjazdy TTSS pobierane w tle.")
                    viewModelScope.launch {
                        try {
                            val liveDepartures = ttssDepartureRepository.getDepartures(
                                stopId = firstTransitStopId,
                                serviceDate = serviceTime.serviceDate,
                                startTimeSeconds = serviceTime.timeSeconds,
                                nowTimeSeconds = now.toLocalTime().toSecondOfDay()
                            )
                            diagnostics.record("Odpowiedź TTSS odebrana.")
                            mutableUiState.update { current ->
                                if (current.journey !== fastestJourney) {
                                    current
                                } else {
                                    current.copy(liveDepartures = liveDepartures)
                                }
                            }
                        } catch (exception: SQLiteException) {
                            diagnostics.record("Błąd bazy podczas pobierania odjazdów TTSS.")
                            mutableUiState.update { current ->
                                if (current.journey !== fastestJourney) {
                                    current
                                } else {
                                    current.copy(
                                        statusMessage =
                                            "Trasa jest gotowa, ale nie udało się odczytać odjazdów: " +
                                                exception.message.orEmpty()
                                    )
                                }
                            }
                        } catch (exception: IllegalStateException) {
                            diagnostics.record(
                                "Błąd danych podczas pobierania odjazdów TTSS: " +
                                    exception.message.orEmpty()
                            )
                            mutableUiState.update { current ->
                                if (current.journey !== fastestJourney) {
                                    current
                                } else {
                                    current.copy(
                                        statusMessage =
                                            "Trasa jest gotowa, ale nie udało się odczytać odjazdów: " +
                                                exception.message.orEmpty()
                                    )
                                }
                            }
                        }
                    }
                }
            } catch (exception: IllegalArgumentException) {
                diagnostics.record("Nieprawidłowe dane trasy: ${exception.message.orEmpty()}")
                val message = "Nie można wyszukać trasy: ${exception.message.orEmpty()}"
                mutableUiState.update {
                    it.copy(routeSearchMessage = message, statusMessage = message)
                }
            } catch (exception: SQLiteException) {
                diagnostics.record("Błąd bazy danych podczas wyszukiwania.")
                val message = "Nie można odczytać rozkładu: ${exception.message.orEmpty()}"
                mutableUiState.update {
                    it.copy(routeSearchMessage = message, statusMessage = message)
                }
            } catch (exception: IllegalStateException) {
                diagnostics.record("Wyszukiwanie przerwane: ${exception.message.orEmpty()}")
                val message = "Nie udało się wyszukać trasy: ${exception.message.orEmpty()}"
                mutableUiState.update {
                    it.copy(routeSearchMessage = message, statusMessage = message)
                }
            } finally {
                mutableUiState.update { it.copy(isSearching = false) }
            }
        }
    }

    fun refreshSchedule() {
        viewModelScope.launch { importSchedule() }
    }

    fun saveSelectedStopAsHome(schedule: DestinationSchedule) {
        saveSelectedPlace(isHome = true, schedule = schedule)
    }

    fun saveSelectedStopAsWork(schedule: DestinationSchedule) {
        saveSelectedPlace(isHome = false, schedule = schedule)
    }

    fun saveHomeSchedule(schedule: DestinationSchedule) {
        saveDestinationSchedule(isHome = true, schedule = schedule)
    }

    fun saveWorkSchedule(schedule: DestinationSchedule) {
        saveDestinationSchedule(isHome = false, schedule = schedule)
    }

    fun routeHome() {
        runContextAction(SavedDestination.HOME)
    }

    fun routeToWork() {
        runContextAction(SavedDestination.WORK)
    }

    fun routeCustom() {
        viewModelScope.launch {
            try {
                val route = appPreferences.customRoute.first()
                if (route == null) {
                    showMessage("Przytrzymaj kartę trasy, aby zapisać ją jako „Własny”.")
                    return@launch
                }
                val allStops = mutableUiState.value.stops.ifEmpty {
                    database.stopDao().getAll()
                }
                val (originGroups, destinationGroups) = withContext(Dispatchers.Default) {
                    nearbyStopGroups(allStops, route.origin) to
                        nearbyStopGroups(allStops, route.destination)
                }
                mutableUiState.update {
                    it.copy(
                        originQuery = route.origin.label,
                        destinationQuery = route.destination.label,
                        originPlace = route.origin,
                        destinationPlace = route.destination,
                        originStop = null,
                        destinationStop = null,
                        originStopOptions = emptyList(),
                        destinationStopOptions = emptyList(),
                        originAddressCandidates = emptyList(),
                        addressCandidates = emptyList(),
                        originAddressSearchError = null,
                        destinationAddressSearchError = null,
                        isSearchingOriginAddresses = false,
                        isSearchingDestinationAddresses = false,
                        nearbyOriginGroups = originGroups,
                        nearbyDestinationGroups = destinationGroups,
                        originStopManuallySelected = false,
                        destinationStopManuallySelected = false,
                        journey = null,
                        journeyOptions = emptyList(),
                        arrivalTarget = null,
                        liveDepartures = null,
                        routeSearchMessage = null,
                        statusMessage = null
                    )
                }
                searchRoute()
            } catch (exception: IllegalStateException) {
                showMessage(exception.message.orEmpty())
            } catch (exception: SQLiteException) {
                showMessage("Nie można odczytać przystanków: ${exception.message.orEmpty()}")
            } catch (exception: IOException) {
                showMessage("Nie można odczytać zapisanej trasy: ${exception.message.orEmpty()}")
            }
        }
    }

    fun saveCurrentRouteAsCustom() {
        val state = mutableUiState.value
        val origin = state.originPlace ?: state.originStop?.let(::placeForStop)
        val destination = state.destinationPlace ?: state.destinationStop?.let(::placeForStop)
        if (state.journey == null || origin == null || destination == null) {
            showMessage("Najpierw wyszukaj trasę, którą chcesz zapisać.")
            return
        }
        viewModelScope.launch {
            try {
                appPreferences.saveCustomRoute(SavedRoute(origin, destination))
                showMessage("Zapisano trasę jako „Własny”.")
            } catch (exception: IOException) {
                showMessage("Nie można zapisać trasy: ${exception.message.orEmpty()}")
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

    fun playRadioStation(stationId: String) {
        if (mutableUiState.value.radioStations.none { it.id == stationId }) {
            showMessage("Wybrano nieprawidłową stację radiową.")
            return
        }
        viewModelScope.launch {
            try {
                radioManager.playStation(stationId)
            } catch (exception: IOException) {
                showMessage("Nie można zapisać ustawień radia: ${exception.message.orEmpty()}")
            } catch (exception: IllegalStateException) {
                showMessage("Nie można uruchomić radia: ${exception.message.orEmpty()}")
            }
        }
    }

    fun saveRadioStations(stations: List<RadioStation>) {
        viewModelScope.launch {
            try {
                radioPreferences.saveStations(stations)
                showMessage("Zapisano listę stacji radiowych.")
            } catch (exception: IOException) {
                showMessage("Nie można zapisać stacji: ${exception.message.orEmpty()}")
            } catch (exception: IllegalArgumentException) {
                showMessage("Nieprawidłowa stacja: ${exception.message.orEmpty()}")
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

    fun clearDiagnostics() {
        diagnostics.clear()
    }

    fun diagnosticsCopied() {
        showMessage("Skopiowano dziennik diagnostyczny.")
    }

    fun checkForUpdates() {
        viewModelScope.launch {
            mutableUiState.update {
                it.copy(isCheckingUpdates = true, updateCheckMessage = null, updateCheckResult = null)
            }
            try {
                val result = updateChecker.checkForUpdates()
                mutableUiState.update {
                    it.copy(
                        updateCheckMessage = if (result.updateAvailable) {
                            "Dostępna jest wersja ${result.latestVersion}."
                        } else {
                            "Masz aktualną wersję (${result.currentVersion})."
                        },
                        updateCheckResult = result
                    )
                }
            } catch (_: NoStableReleaseAvailableException) {
                mutableUiState.update {
                    it.copy(
                        updateCheckMessage =
                            "Nie opublikowano jeszcze stabilnego wydania na GitHub. " +
                                "Sprawdź ponownie później."
                    )
                }
            } catch (exception: IOException) {
                mutableUiState.update {
                    it.copy(
                        updateCheckMessage =
                            "Nie udało się sprawdzić aktualizacji: ${exception.message.orEmpty()}"
                    )
                }
            } catch (exception: IllegalArgumentException) {
                mutableUiState.update {
                    it.copy(
                        updateCheckMessage =
                            "Nie udało się porównać wersji: ${exception.message.orEmpty()}"
                    )
                }
            } catch (exception: IllegalStateException) {
                mutableUiState.update {
                    it.copy(
                        updateCheckMessage =
                            "Nie udało się sprawdzić aktualizacji: ${exception.message.orEmpty()}"
                    )
                }
            } finally {
                mutableUiState.update { it.copy(isCheckingUpdates = false) }
            }
        }
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
            appPreferences.homeSchedule.collect { schedule ->
                mutableUiState.update { it.copy(homeSchedule = schedule) }
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
            appPreferences.workSchedule.collect { schedule ->
                mutableUiState.update { it.copy(workSchedule = schedule) }
            }
        }
        viewModelScope.launch {
            appPreferences.customRoute.collect { route ->
                mutableUiState.update { it.copy(customRoute = route) }
            }
        }
        viewModelScope.launch {
            appPreferences.recentAddressPlaces.collect { places ->
                mutableUiState.update { it.copy(recentAddressPlaces = places) }
            }
        }
        viewModelScope.launch {
            radioPreferences.stations.collect { stations ->
                mutableUiState.update { it.copy(radioStations = stations) }
            }
        }
        viewModelScope.launch {
            radioPreferences.lastSelectedRadioId.collect { id ->
                mutableUiState.update { it.copy(lastSelectedRadioId = id) }
            }
        }
        viewModelScope.launch {
            radioManager.isPlaying.collect { isPlaying ->
                mutableUiState.update { it.copy(isRadioPlaying = isPlaying) }
            }
        }
        viewModelScope.launch {
            appPreferences.recentStopSearches.collect { searches ->
                mutableUiState.update { it.copy(recentStopSearches = searches) }
            }
        }
    }

    private fun observeDiagnostics() {
        viewModelScope.launch {
            diagnostics.entries.collect { entries ->
                mutableUiState.update { it.copy(diagnosticEntries = entries) }
            }
        }
    }

    private fun ensureScheduleImported() {
        viewModelScope.launch {
            try {
                if (database.stopDao().count() == 0) {
                    importSchedule()
                } else if (database.connectionDao().count() == 0) {
                    prepareConnectionIndex()
                }
            } catch (exception: SQLiteException) {
                showMessage("Nie można sprawdzić lokalnego rozkładu: ${exception.message.orEmpty()}")
            }
        }
    }

    private suspend fun prepareConnectionIndex() {
        if (mutableUiState.value.isImporting) {
            return
        }
        diagnostics.record("Brak lokalnego indeksu połączeń; rozpoczęto jego budowę.")
        mutableUiState.update {
            it.copy(
                isImporting = true,
                progressMessage = "Przygotowanie szybkiego indeksu połączeń...",
                statusMessage = null
            )
        }
        try {
            gtfsZipParser.rebuildConnectionIndex { progress ->
                diagnostics.record(progress)
                mutableUiState.update { it.copy(progressMessage = progress) }
            }
            showMessage("Lokalny indeks połączeń jest gotowy.")
        } catch (exception: IllegalStateException) {
            diagnostics.record("Błąd budowy indeksu: ${exception.message.orEmpty()}")
            showMessage("Nie udało się przygotować indeksu: ${exception.message.orEmpty()}")
        } catch (exception: IllegalArgumentException) {
            diagnostics.record("Nieprawidłowe dane podczas budowy indeksu: ${exception.message.orEmpty()}")
            showMessage("Nie udało się przygotować indeksu: ${exception.message.orEmpty()}")
        } catch (exception: SQLiteException) {
            diagnostics.record("Błąd bazy podczas budowy indeksu.")
            showMessage("Nie udało się przygotować indeksu: ${exception.message.orEmpty()}")
        } finally {
            mutableUiState.update {
                it.copy(isImporting = false, progressMessage = "")
            }
        }
    }

    private suspend fun importSchedule() {
        if (mutableUiState.value.isImporting) {
            return
        }
        diagnostics.record("Rozpoczęto pobieranie i import GTFS.")
        mutableUiState.update {
            it.copy(isImporting = true, progressMessage = "Przygotowanie importu...", statusMessage = null)
        }
        try {
            gtfsZipParser.importSchedule { progress ->
                diagnostics.record(progress)
                mutableUiState.update { it.copy(progressMessage = progress) }
            }
            diagnostics.record("Import GTFS zakończony pomyślnie.")
            showMessage("Rozkład został zaktualizowany.")
        } catch (exception: IOException) {
            diagnostics.record("Błąd pobierania GTFS: ${exception.message.orEmpty()}")
            showMessage("Nie udało się pobrać rozkładu: ${exception.message.orEmpty()}")
        } catch (exception: IllegalArgumentException) {
            diagnostics.record("Nieprawidłowe dane GTFS: ${exception.message.orEmpty()}")
            showMessage("Rozkład ma nieprawidłowe dane: ${exception.message.orEmpty()}")
        } catch (exception: IllegalStateException) {
            diagnostics.record("Błąd importu GTFS: ${exception.message.orEmpty()}")
            showMessage("Nie udało się zaimportować rozkładu: ${exception.message.orEmpty()}")
        } catch (exception: SQLiteException) {
            diagnostics.record("Błąd bazy danych podczas importu GTFS.")
            showMessage("Nie udało się zapisać rozkładu: ${exception.message.orEmpty()}")
        } finally {
            mutableUiState.update {
                it.copy(isImporting = false, progressMessage = "")
            }
        }
    }

    private fun saveSelectedPlace(isHome: Boolean, schedule: DestinationSchedule) {
        val state = mutableUiState.value
        val place = state.destinationPlace ?: state.destinationStop?.let(::placeForStop)
        if (place == null) {
            showMessage("Najpierw wybierz adres lub przystanek docelowy.")
            return
        }
        viewModelScope.launch {
            try {
                if (isHome) {
                    appPreferences.saveHomePlace(place, schedule)
                } else {
                    appPreferences.saveWorkPlace(place, schedule)
                }
                showMessage(
                    if (isHome) "Zapisano dom i harmonogram." else "Zapisano pracę i harmonogram."
                )
            } catch (exception: IOException) {
                showMessage("Nie można zapisać lokalizacji i harmonogramu: ${exception.message.orEmpty()}")
            }
        }
    }

    private fun saveDestinationSchedule(isHome: Boolean, schedule: DestinationSchedule) {
        viewModelScope.launch {
            try {
                if (isHome) {
                    appPreferences.saveHomeSchedule(schedule)
                } else {
                    appPreferences.saveWorkSchedule(schedule)
                }
                showMessage("Zapisano harmonogram.")
            } catch (exception: IOException) {
                showMessage("Nie można zapisać harmonogramu: ${exception.message.orEmpty()}")
            }
        }
    }

    private fun scheduleAddressSuggestions(query: String, isOrigin: Boolean) {
        val trimmedQuery = query.trim()
        if (isOrigin) {
            originAddressSearchJob?.cancel()
        } else {
            destinationAddressSearchJob?.cancel()
        }
        if (trimmedQuery.length < MIN_ADDRESS_QUERY_LENGTH) {
            mutableUiState.update {
                if (isOrigin) {
                    it.copy(
                        originAddressCandidates = emptyList(),
                        originAddressSearchError = null,
                        isSearchingOriginAddresses = false
                    )
                } else {
                    it.copy(
                        addressCandidates = emptyList(),
                        destinationAddressSearchError = null,
                        isSearchingDestinationAddresses = false
                    )
                }
            }
            return
        }

        val job = viewModelScope.launch {
            delay(ADDRESS_LOOKUP_DEBOUNCE_MILLIS)
            val currentState = mutableUiState.value
            val currentQuery = if (isOrigin) {
                currentState.originQuery
            } else {
                currentState.destinationQuery
            }
            val selectedPlace = if (isOrigin) {
                currentState.originPlace
            } else {
                currentState.destinationPlace
            }
            if (currentQuery != query || selectedPlace != null) {
                return@launch
            }
            mutableUiState.update {
                if (isOrigin) {
                    it.copy(
                        isSearchingOriginAddresses = true,
                        originAddressSearchError = null
                    )
                } else {
                    it.copy(
                        isSearchingDestinationAddresses = true,
                        destinationAddressSearchError = null
                    )
                }
            }
            try {
                val candidates = addressGeocoder.search(trimmedQuery)
                mutableUiState.update {
                    val latestQuery = if (isOrigin) it.originQuery else it.destinationQuery
                    val latestPlace = if (isOrigin) it.originPlace else it.destinationPlace
                    if (latestQuery != query || latestPlace != null) {
                        it
                    } else if (isOrigin) {
                        it.copy(
                            originAddressCandidates = candidates,
                            isSearchingOriginAddresses = false
                        )
                    } else {
                        it.copy(
                            addressCandidates = candidates,
                            isSearchingDestinationAddresses = false
                        )
                    }
                }
            } catch (exception: IOException) {
                mutableUiState.update {
                    if (isOrigin) {
                        it.copy(
                            originAddressSearchError =
                                "Nie udało się podpowiedzieć adresu: ${exception.message.orEmpty()}",
                            isSearchingOriginAddresses = false
                        )
                    } else {
                        it.copy(
                            destinationAddressSearchError =
                                "Nie udało się podpowiedzieć adresu: ${exception.message.orEmpty()}",
                            isSearchingDestinationAddresses = false
                        )
                    }
                }
            } catch (exception: IllegalArgumentException) {
                mutableUiState.update {
                    if (isOrigin) {
                        it.copy(
                            originAddressSearchError =
                                "Nieprawidłowy adres: ${exception.message.orEmpty()}",
                            isSearchingOriginAddresses = false
                        )
                    } else {
                        it.copy(
                            destinationAddressSearchError =
                                "Nieprawidłowy adres: ${exception.message.orEmpty()}",
                            isSearchingDestinationAddresses = false
                        )
                    }
                }
            }
        }
        if (isOrigin) {
            originAddressSearchJob = job
        } else {
            destinationAddressSearchJob = job
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

    private fun buildStopAccesses(
        place: GeoPlace,
        selectedStops: List<StopEntity>,
        nearbyGroups: List<StopGroup>,
        allStops: List<StopEntity>,
        manuallySelected: Boolean
    ): List<StopAccess> {
        val candidates = if (manuallySelected) {
            selectedStops
        } else {
            val groups = nearbyGroups.ifEmpty { nearbyStopGroups(allStops, place) }
            groups.flatMap(StopGroup::stops)
        }

        return candidates.distinctBy(StopEntity::stopId).mapNotNull { stop ->
            val distanceMeters = Haversine.distanceMeters(
                place.latitude,
                place.longitude,
                stop.stopLat,
                stop.stopLon
            )
            if (!manuallySelected && distanceMeters > MAX_AUTO_STOP_ACCESS_METERS) {
                return@mapNotNull null
            }
            StopAccess(
                stopId = stop.stopId,
                walkingDistanceMeters = distanceMeters,
                walkingDurationSeconds =
                    FootpathGenerator.walkingDurationSeconds(distanceMeters)
            )
        }
    }

    private fun placeForStop(stop: StopEntity): GeoPlace = GeoPlace(
        label = StopNameGrouper.mainName(stop.stopName),
        latitude = stop.stopLat,
        longitude = stop.stopLon
    )

    private fun selectRouteAlternatives(candidates: List<CsaJourney>): List<CsaJourney> {
        val ordered = candidates
            .distinctBy(::routeSignature)
            .sortedWith(
                compareBy<CsaJourney> { it.totalDurationSeconds }
                    .thenBy { it.totalWalkingDistanceMeters }
                    .thenBy { it.transferPenaltySeconds }
            )
        val fastest = ordered.firstOrNull() ?: return emptyList()
        val leastWalking = ordered.minWithOrNull(
            compareBy<CsaJourney> { it.totalWalkingDistanceMeters }
                .thenBy { it.totalDurationSeconds }
                .thenBy { it.transferPenaltySeconds }
        ) ?: return listOf(fastest)

        return buildList {
            add(fastest)
            if (routeSignature(leastWalking) != routeSignature(fastest)) {
                add(leastWalking)
            }
            ordered.forEach { journey ->
                if (none { routeSignature(it) == routeSignature(journey) }) {
                    add(journey)
                }
            }
        }.take(MAX_DISPLAYED_ROUTE_OPTIONS)
    }

    private fun routeSignature(journey: CsaJourney): String {
        val tripIds = journey.steps
            .filterIsInstance<JourneyStep.Transit>()
            .joinToString(separator = ",") { it.connection.tripId }
        return "${journey.originAccess.stopId}|$tripIds|${journey.destinationAccess.stopId}"
    }

    private fun recordRecentStopSearch(stopName: String) {
        viewModelScope.launch {
            try {
                appPreferences.recordStopSearch(stopName)
            } catch (exception: IOException) {
                showMessage("Nie udało się zapisać historii przystanków: ${exception.message.orEmpty()}")
            }
        }
    }

    private fun recordRecentAddress(place: GeoPlace) {
        viewModelScope.launch {
            try {
                appPreferences.recordAddressPlace(place)
            } catch (exception: IOException) {
                showMessage("Nie udało się zapisać historii adresów: ${exception.message.orEmpty()}")
            }
        }
    }

    private fun runContextAction(destination: SavedDestination) {
        viewModelScope.launch {
            mutableUiState.update {
                it.copy(
                    isSearching = true,
                    statusMessage = null,
                    journey = null,
                    journeyOptions = emptyList(),
                    arrivalTarget = null,
                    liveDepartures = null
                )
            }
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
                    journeyOptions = emptyList(),
                    arrivalTarget = result.arrivalTarget,
                    originStopManuallySelected = false,
                    destinationStopManuallySelected = false,
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
        }
    }

    private companion object {
        const val ADDRESS_LOOKUP_DEBOUNCE_MILLIS = 450L
        const val MIN_ADDRESS_QUERY_LENGTH = 3
        const val MAX_NEARBY_STOPS = 6
        const val MAX_AUTO_STOP_ACCESS_METERS = 1_500.0
        const val MAX_CSA_JOURNEY_CANDIDATES = 20
        const val MAX_DISPLAYED_ROUTE_OPTIONS = 3
    }
}
