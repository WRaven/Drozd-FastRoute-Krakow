package pl.wraven.mpkfastroute.feature.search

import android.database.sqlite.SQLiteException
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.IOException
import java.time.LocalDateTime
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import pl.wraven.mpkfastroute.core.audio.RadioManager
import pl.wraven.mpkfastroute.core.audio.RadioStations
import pl.wraven.mpkfastroute.core.csa.CsaJourney
import pl.wraven.mpkfastroute.core.csa.RoutePlanner
import pl.wraven.mpkfastroute.core.database.AppDatabase
import pl.wraven.mpkfastroute.core.database.StopEntity
import pl.wraven.mpkfastroute.core.gtfs.GtfsServiceClock
import pl.wraven.mpkfastroute.core.gtfs.GtfsZipParser
import pl.wraven.mpkfastroute.core.live.LiveDeparturesResult
import pl.wraven.mpkfastroute.core.live.TtssDepartureRepository
import pl.wraven.mpkfastroute.core.location.LocationPermissionRequiredException
import pl.wraven.mpkfastroute.core.location.LocationUnavailableException
import pl.wraven.mpkfastroute.core.settings.AppPreferences

data class SearchUiState(
    val stops: List<StopEntity> = emptyList(),
    val originQuery: String = "",
    val destinationQuery: String = "",
    val originStop: StopEntity? = null,
    val destinationStop: StopEntity? = null,
    val homeStopId: String? = null,
    val workStopId: String? = null,
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
}

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val database: AppDatabase,
    private val gtfsZipParser: GtfsZipParser,
    private val routePlanner: RoutePlanner,
    private val ttssDepartureRepository: TtssDepartureRepository,
    private val contextEngine: ContextEngine,
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

    fun setOriginQuery(query: String) {
        mutableUiState.update {
            it.copy(
                originQuery = query,
                originStop = null,
                journey = null,
                liveDepartures = null,
                statusMessage = null
            )
        }
    }

    fun setDestinationQuery(query: String) {
        mutableUiState.update {
            it.copy(
                destinationQuery = query,
                destinationStop = null,
                journey = null,
                statusMessage = null
            )
        }
    }

    fun selectOrigin(stop: StopEntity) {
        mutableUiState.update {
            it.copy(
                originQuery = stop.stopName,
                originStop = stop,
                journey = null,
                liveDepartures = null
            )
        }
    }

    fun selectDestination(stop: StopEntity) {
        mutableUiState.update {
            it.copy(destinationQuery = stop.stopName, destinationStop = stop, journey = null)
        }
    }

    fun searchRoute() {
        val state = mutableUiState.value
        val origin = state.originStop
        val destination = state.destinationStop
        if (origin == null || destination == null) {
            showMessage("Wybierz przystanek początkowy i docelowy.")
            return
        }

        viewModelScope.launch {
            mutableUiState.update { it.copy(isSearching = true, statusMessage = null) }
            try {
                val now = LocalDateTime.now()
                val serviceTime = GtfsServiceClock.from(now)
                val journey = routePlanner.findEarliestJourney(
                    originStopId = origin.stopId,
                    destinationStopId = destination.stopId,
                    serviceDate = serviceTime.serviceDate,
                    startTimeSeconds = serviceTime.timeSeconds
                )
                val liveDepartures = ttssDepartureRepository.getDepartures(
                    stopId = origin.stopId,
                    serviceDate = serviceTime.serviceDate,
                    startTimeSeconds = serviceTime.timeSeconds,
                    nowTimeSeconds = now.toLocalTime().toSecondOfDay()
                )
                mutableUiState.update {
                    it.copy(
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
        saveSelectedStop(isHome = true)
    }

    fun saveSelectedStopAsWork() {
        saveSelectedStop(isHome = false)
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
            appPreferences.workStopId.collect { stopId ->
                mutableUiState.update { it.copy(workStopId = stopId) }
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

    private fun saveSelectedStop(isHome: Boolean) {
        val stop = mutableUiState.value.destinationStop
        if (stop == null) {
            showMessage("Najpierw wybierz przystanek docelowy.")
            return
        }
        viewModelScope.launch {
            try {
                if (isHome) {
                    appPreferences.saveHomeStopId(stop.stopId)
                } else {
                    appPreferences.saveWorkStopId(stop.stopId)
                }
                showMessage(
                    if (isHome) "Zapisano przystanek domowy." else "Zapisano przystanek pracy."
                )
            } catch (exception: IOException) {
                showMessage("Nie można zapisać przystanku: ${exception.message.orEmpty()}")
            }
        }
    }

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
                    originStop = result.origin,
                    destinationStop = result.destination,
                    originQuery = result.origin.stopName,
                    destinationQuery = result.destination.stopName,
                    journey = result.journey,
                    liveDepartures = null,
                    statusMessage = null
                )
            }

            is QuickActionResult.AlreadyAtDestination -> showMessage(
                when (savedDestination) {
                    SavedDestination.HOME -> "Jesteś już w domu!"
                    SavedDestination.WORK -> "Jesteś już w pracy!"
                    null -> "Jesteś już na miejscu: ${result.destination.stopName}."
                }
            )

            QuickActionResult.NoJourneyFound ->
                showMessage("Nie znaleziono połączenia w wybranym przedziale czasu.")

            QuickActionResult.NoRandomDestination ->
                showMessage("Nie znaleziono przystanku oddalonego o co najmniej 3 km.")
        }
    }
}
