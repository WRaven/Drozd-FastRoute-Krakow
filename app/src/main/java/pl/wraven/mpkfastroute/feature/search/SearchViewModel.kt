package pl.wraven.mpkfastroute.feature.search

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.database.sqlite.SQLiteException
import android.location.Location
import android.os.SystemClock
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import androidx.work.WorkManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import java.time.DateTimeException
import java.time.LocalDateTime
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import io.ktor.client.plugins.HttpRequestTimeoutException
import kotlinx.serialization.SerializationException
import pl.wraven.mpkfastroute.core.audio.RadioManager
import pl.wraven.mpkfastroute.core.audio.RadioPreferences
import pl.wraven.mpkfastroute.core.audio.RadioStation
import pl.wraven.mpkfastroute.core.audio.RadioStations
import pl.wraven.mpkfastroute.core.csa.JourneyTimeMode
import pl.wraven.mpkfastroute.core.csa.JourneyTimePlan
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
import pl.wraven.mpkfastroute.core.gtfs.GtfsScheduleUpdateScheduler
import pl.wraven.mpkfastroute.core.live.LiveDeparturesResult
import pl.wraven.mpkfastroute.core.live.LiveTripUpdate
import pl.wraven.mpkfastroute.core.live.RouteServiceAlert
import pl.wraven.mpkfastroute.core.live.ServiceAlertsClient
import pl.wraven.mpkfastroute.core.live.TtssDepartureRepository
import pl.wraven.mpkfastroute.core.live.LiveVehicleSnapshot
import pl.wraven.mpkfastroute.core.live.LiveVehiclePosition
import pl.wraven.mpkfastroute.core.live.VehicleTripSchedule
import pl.wraven.mpkfastroute.core.live.VehicleTripScheduleRepository
import pl.wraven.mpkfastroute.core.live.KRAKOW_ZONE
import pl.wraven.mpkfastroute.core.live.StopTimetable
import pl.wraven.mpkfastroute.core.live.StopTimetableRepository
import pl.wraven.mpkfastroute.core.live.ZtpTripUpdatesRepository
import pl.wraven.mpkfastroute.core.live.ZtpVehiclePositionsRepository
import pl.wraven.mpkfastroute.core.live.ZtpServiceNewsClient
import pl.wraven.mpkfastroute.core.live.ZtpServiceNewsItem
import pl.wraven.mpkfastroute.core.location.AddressGeocoder
import pl.wraven.mpkfastroute.core.location.CurrentLocationProvider
import pl.wraven.mpkfastroute.core.location.GeoPlace
import pl.wraven.mpkfastroute.core.location.LocationPermissionRequiredException
import pl.wraven.mpkfastroute.core.location.LocationUnavailableException
import pl.wraven.mpkfastroute.core.settings.AppPreferences
import pl.wraven.mpkfastroute.core.settings.DestinationSchedule
import pl.wraven.mpkfastroute.core.settings.DestinationShortcutNames
import pl.wraven.mpkfastroute.core.settings.SavedRoute
import pl.wraven.mpkfastroute.core.update.AppUpdateDownloader
import pl.wraven.mpkfastroute.core.update.GitHubUpdateChecker
import pl.wraven.mpkfastroute.core.update.NoReleaseAvailableException
import pl.wraven.mpkfastroute.core.update.UpdateCheckResult
import pl.wraven.mpkfastroute.core.weather.OpenMeteoClient
import pl.wraven.mpkfastroute.core.weather.OpenMeteoRequestException
import pl.wraven.mpkfastroute.core.weather.WeatherForecast

data class SearchUiState(
    val destinationShortcutNames: DestinationShortcutNames = DestinationShortcutNames(),
    val isSavingShortcutNames: Boolean = false,
    val shortcutNamesSaveError: String? = null,
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
    val routeTimePlan: JourneyTimePlan? = null,
    val liveDepartures: LiveDeparturesResult? = null,
    val liveVehicleSnapshot: LiveVehicleSnapshot? = null,
    val isLoadingLiveVehicles: Boolean = false,
    val liveVehicleError: String? = null,
    val liveMapUserLocation: GeoPlace? = null,
    val liveMapLocationError: String? = null,
    val selectedLiveMapStop: StopEntity? = null,
    val liveMapStopTimetable: StopTimetable? = null,
    val isLoadingLiveMapStopTimetable: Boolean = false,
    val liveMapStopTimetableError: String? = null,
    val selectedLiveVehicle: LiveVehiclePosition? = null,
    val selectedLiveVehicleTrip: VehicleTripSchedule? = null,
    val selectedLiveVehicleTripUpdate: LiveTripUpdate? = null,
    val isLoadingSelectedLiveVehicleTrip: Boolean = false,
    val isLoadingSelectedLiveVehicleTripUpdate: Boolean = false,
    val selectedLiveVehicleTripError: String? = null,
    val selectedLiveVehicleTripUpdateError: String? = null,
    val isImporting: Boolean = false,
    val databaseUpdateProgressPercent: Int? = null,
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
    val updateCheckResult: UpdateCheckResult? = null,
    val updatePrompt: UpdateCheckResult? = null,
    val isDownloadingAppUpdate: Boolean = false,
    val appUpdateDownloadProgressPercent: Int? = null,
    val downloadedAppUpdatePath: String? = null,
    val downloadedAppUpdateVersion: String? = null,
    val appUpdateDownloadError: String? = null,
    val weatherForecast: WeatherForecast? = null,
    val isLoadingWeather: Boolean = false,
    val weatherError: String? = null,
    val serviceNews: List<ZtpServiceNewsItem> = emptyList(),
    val isLoadingServiceNews: Boolean = false,
    val serviceNewsError: String? = null,
    val activeServiceAlerts: List<RouteServiceAlert> = emptyList(),
    val isLoadingLiveServiceAlerts: Boolean = false,
    val liveServiceAlertsError: String? = null
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
    @param:ApplicationContext private val appContext: Context,
    private val routePlanner: RoutePlanner,
    private val ttssDepartureRepository: TtssDepartureRepository,
    private val liveVehiclePositionsRepository: ZtpVehiclePositionsRepository,
    private val vehicleTripScheduleRepository: VehicleTripScheduleRepository,
    private val stopTimetableRepository: StopTimetableRepository,
    private val ztpTripUpdatesRepository: ZtpTripUpdatesRepository,
    private val contextEngine: ContextEngine,
    private val addressGeocoder: AddressGeocoder,
    private val appPreferences: AppPreferences,
    private val radioManager: RadioManager,
    private val radioPreferences: RadioPreferences,
    private val updateChecker: GitHubUpdateChecker,
    private val appUpdateDownloader: AppUpdateDownloader,
    private val diagnostics: SearchDiagnostics,
    private val currentLocationProvider: CurrentLocationProvider,
    private val openMeteoClient: OpenMeteoClient,
    private val ztpServiceNewsClient: ZtpServiceNewsClient,
    private val serviceAlertsClient: ServiceAlertsClient
) : ViewModel() {
    private val mutableUiState = MutableStateFlow(SearchUiState())
    val uiState = mutableUiState.asStateFlow()
    private var originAddressSearchJob: Job? = null
    private var destinationAddressSearchJob: Job? = null
    private var weatherJob: Job? = null
    private var liveVehicleJob: Job? = null
    private var liveMapStopTimetableJob: Job? = null
    private var liveMapStopRequestId = 0L
    private var selectedVehicleTripJob: Job? = null
    private var selectedVehicleTripUpdateJob: Job? = null
    private var selectedVehicleTripRequestId = 0L
    private var serviceNewsJob: Job? = null
    private var liveServiceAlertsJob: Job? = null
    private var currentLocationPrefetchJob: Job? = null
    private var currentLocationResolutionJob: Job? = null
    private var prefetchedCurrentLocation: PrefetchedCurrentLocation? = null
    private var lastServiceNewsAttemptAtMillis = 0L
    private var lastLiveServiceAlertsAttemptAtMillis = 0L

    private data class PrefetchedCurrentLocation(
        val location: Location,
        val acquiredAtElapsedRealtime: Long
    )

    init {
        observeStops()
        observePreferences()
        observeDiagnostics()
        observeScheduleUpdates()
        checkForUpdates(showStatus = false)
        refreshServiceNews()
        refreshLiveServiceAlerts()
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
        cancelCurrentLocationResolution()
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

    fun prefetchCurrentLocation() {
        if (!hasCurrentLocationPermission()) return
        if (currentLocationPrefetchJob?.isActive == true) return
        if (hasFreshPrefetchedCurrentLocation()) return

        currentLocationPrefetchJob = viewModelScope.launch {
            try {
                prefetchedCurrentLocation = PrefetchedCurrentLocation(
                    location = currentLocationProvider.getCurrentLocation(),
                    acquiredAtElapsedRealtime = SystemClock.elapsedRealtime()
                )
            } catch (exception: LocationUnavailableException) {
                Log.i(TAG, "Startup location prefetch is unavailable.", exception)
            } catch (exception: SecurityException) {
                Log.w(TAG, "Startup location prefetch was denied.", exception)
            }
        }
    }

    fun selectOrigin(group: StopGroup) {
        cancelCurrentLocationResolution()
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
        if (currentLocationResolutionJob?.isActive == true) return
        mutableUiState.update { it.copy(statusMessage = null) }
        currentLocationResolutionJob = viewModelScope.launch {
            try {
                val location = takeFreshPrefetchedCurrentLocation() ?: run {
                    currentLocationPrefetchJob?.join()
                    takeFreshPrefetchedCurrentLocation()
                        ?: currentLocationProvider.getCurrentLocation()
                }
                val origin = contextEngine.findCurrentOrigin(location)
                val originPlace = try {
                    origin.place.copy(
                        label = addressGeocoder.reverseGeocode(origin.place) ?: origin.place.label
                    )
                } catch (exception: IOException) {
                    Log.w(TAG, "Could not resolve the current location to an address.", exception)
                    origin.place
                }
                val allStops = mutableUiState.value.stops.ifEmpty {
                    database.stopDao().getAll()
                }
                val nearbyGroups = withContext(Dispatchers.Default) {
                    nearbyStopGroups(allStops, originPlace)
                }
                mutableUiState.update {
                    it.copy(
                        originQuery = originPlace.label,
                        originPlace = originPlace,
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
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: LocationPermissionRequiredException) {
                showMessage("Aby użyć lokalizacji, zezwól na dostęp do lokalizacji.")
            } catch (exception: LocationUnavailableException) {
                showMessage(exception.message.orEmpty())
            } catch (exception: IllegalStateException) {
                showMessage(exception.message.orEmpty())
            } catch (_: SecurityException) {
                showMessage("Brak dostępu do lokalizacji.")
            } catch (exception: SQLiteException) {
                showMessage("Nie można odczytać przystanków: ${exception.message.orEmpty()}")
            }
        }
    }

    fun refreshLiveVehiclePositions() {
        refreshSelectedLiveVehicleTripUpdate()
        if (liveVehicleJob?.isActive == true) return
        liveVehicleJob = viewModelScope.launch {
            mutableUiState.update {
                it.copy(isLoadingLiveVehicles = true, liveVehicleError = null)
            }
            try {
                val snapshot = liveVehiclePositionsRepository.getSnapshot()
                val selectedVehicle = mutableUiState.value.selectedLiveVehicle
                val refreshedSelectedVehicle = selectedVehicle?.let { selected ->
                    snapshot.vehicles.firstOrNull { it.id == selected.id }
                }
                mutableUiState.update {
                    it.copy(
                        liveVehicleSnapshot = snapshot,
                        liveVehicleError = null,
                        selectedLiveVehicle = refreshedSelectedVehicle ?: selectedVehicle
                    )
                }
                if (
                    refreshedSelectedVehicle != null &&
                    (refreshedSelectedVehicle.tripId != selectedVehicle?.tripId ||
                        refreshedSelectedVehicle.serviceDate != selectedVehicle?.serviceDate)
                ) {
                    selectLiveVehicle(refreshedSelectedVehicle)
                }
            } catch (exception: IOException) {
                mutableUiState.update {
                    it.copy(
                        liveVehicleError = exception.message
                            ?: "Nie udało się pobrać pozycji pojazdów ZTP."
                    )
                }
            } catch (exception: SQLiteException) {
                mutableUiState.update {
                    it.copy(
                        liveVehicleError = "Nie można odczytać linii z bazy: " +
                            exception.message.orEmpty()
                    )
                }
            } finally {
                mutableUiState.update { it.copy(isLoadingLiveVehicles = false) }
            }
        }
    }

    fun clearLiveMapUserLocation() {
        mutableUiState.update {
            it.copy(liveMapUserLocation = null, liveMapLocationError = null)
        }
    }

    fun locateUserOnLiveMap() {
        viewModelScope.launch {
            mutableUiState.update {
                it.copy(liveMapUserLocation = null, liveMapLocationError = null)
            }
            try {
                val location = currentLocationProvider.getCurrentLocation()
                if (
                    !location.latitude.isFinite() || location.latitude !in -90.0..90.0 ||
                    !location.longitude.isFinite() || location.longitude !in -180.0..180.0
                ) {
                    throw LocationUnavailableException()
                }
                mutableUiState.update {
                    it.copy(
                        liveMapUserLocation = GeoPlace(
                            label = "Moja lokalizacja",
                            latitude = location.latitude,
                            longitude = location.longitude
                        ),
                        liveMapLocationError = null
                    )
                }
            } catch (exception: LocationPermissionRequiredException) {
                mutableUiState.update {
                    it.copy(liveMapLocationError = "Zezwól na dostęp do lokalizacji.")
                }
            } catch (exception: LocationUnavailableException) {
                mutableUiState.update {
                    it.copy(
                        liveMapLocationError =
                            "Nie udało się ustalić bieżącej lokalizacji: ${exception.message.orEmpty()}"
                    )
                }
            } catch (exception: SecurityException) {
                mutableUiState.update {
                    it.copy(liveMapLocationError = "Brak dostępu do lokalizacji.")
                }
            } catch (exception: IllegalStateException) {
                mutableUiState.update {
                    it.copy(
                        liveMapLocationError =
                            "Nie udało się ustalić bieżącej lokalizacji: ${exception.message.orEmpty()}"
                    )
                }
            }
        }
    }

    fun selectLiveVehicle(vehicle: LiveVehiclePosition) {
        val requestId = ++selectedVehicleTripRequestId
        selectedVehicleTripJob?.cancel()
        selectedVehicleTripUpdateJob?.cancel()
        clearLiveMapStop()
        val tripId = vehicle.tripId
        mutableUiState.update {
            it.copy(
                selectedLiveVehicle = vehicle,
                selectedLiveVehicleTrip = null,
                selectedLiveVehicleTripUpdate = null,
                isLoadingSelectedLiveVehicleTrip = !tripId.isNullOrBlank(),
                isLoadingSelectedLiveVehicleTripUpdate = !tripId.isNullOrBlank(),
                selectedLiveVehicleTripError = if (tripId.isNullOrBlank()) {
                    "ZTP nie przekazał identyfikatora kursu dla tego pojazdu."
                } else {
                    null
                },
                selectedLiveVehicleTripUpdateError = null
            )
        }
        if (tripId.isNullOrBlank()) return

        selectedVehicleTripJob = viewModelScope.launch {
            try {
                val schedule = vehicleTripScheduleRepository.getSchedule(
                    vehicle = vehicle,
                    now = LocalDateTime.now(KRAKOW_ZONE)
                )
                if (requestId == selectedVehicleTripRequestId) {
                    mutableUiState.update {
                        it.copy(
                            selectedLiveVehicleTrip = schedule,
                            selectedLiveVehicleTripError = null
                        )
                    }
                }
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: IOException) {
                if (requestId == selectedVehicleTripRequestId) {
                    mutableUiState.update {
                        it.copy(selectedLiveVehicleTripError =
                            "Nie udało się pobrać rozkładu kursu: ${exception.message.orEmpty()}")
                    }
                }
            } catch (exception: SQLiteException) {
                if (requestId == selectedVehicleTripRequestId) {
                    mutableUiState.update {
                        it.copy(
                            selectedLiveVehicleTripError =
                                "Nie można odczytać rozkładu kursu: ${exception.message.orEmpty()}"
                        )
                    }
                }
            } catch (exception: IllegalStateException) {
                if (requestId == selectedVehicleTripRequestId) {
                    mutableUiState.update {
                        it.copy(selectedLiveVehicleTripError = exception.message)
                    }
                }
            } finally {
                if (requestId == selectedVehicleTripRequestId) {
                    mutableUiState.update {
                        it.copy(isLoadingSelectedLiveVehicleTrip = false)
                    }
                }
            }
        }
        refreshSelectedLiveVehicleTripUpdate()
    }

    fun clearSelectedLiveVehicle() {
        selectedVehicleTripRequestId += 1
        selectedVehicleTripJob?.cancel()
        selectedVehicleTripUpdateJob?.cancel()
        selectedVehicleTripJob = null
        selectedVehicleTripUpdateJob = null
        mutableUiState.update {
            it.copy(
                selectedLiveVehicle = null,
                selectedLiveVehicleTrip = null,
                selectedLiveVehicleTripUpdate = null,
                isLoadingSelectedLiveVehicleTrip = false,
                isLoadingSelectedLiveVehicleTripUpdate = false,
                selectedLiveVehicleTripError = null,
                selectedLiveVehicleTripUpdateError = null
            )
        }
    }

    private fun refreshSelectedLiveVehicleTripUpdate() {
        val state = mutableUiState.value
        val vehicle = state.selectedLiveVehicle ?: return
        val tripId = vehicle.tripId?.takeIf(String::isNotBlank) ?: return
        if (selectedVehicleTripUpdateJob?.isActive == true) return
        val requestId = selectedVehicleTripRequestId
        mutableUiState.update {
            if (it.selectedLiveVehicle?.id != vehicle.id) {
                it
            } else {
                it.copy(
                    isLoadingSelectedLiveVehicleTripUpdate = true,
                    selectedLiveVehicleTripUpdateError = null
                )
            }
        }
        selectedVehicleTripUpdateJob = viewModelScope.launch {
            try {
                val update = ztpTripUpdatesRepository.getTripUpdate(
                    vehicle.mode, tripId, vehicle.serviceDate
                )
                if (
                    requestId == selectedVehicleTripRequestId &&
                    mutableUiState.value.selectedLiveVehicle?.tripId == tripId
                ) {
                    mutableUiState.update {
                        it.copy(
                            selectedLiveVehicleTripUpdate = update,
                            selectedLiveVehicleTripUpdateError = if (update == null) {
                                "Brak bieżących prognoz dla kursu; pokazuję rozkład."
                            } else {
                                null
                            }
                        )
                    }
                }
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: IOException) {
                if (requestId == selectedVehicleTripRequestId) {
                    mutableUiState.update {
                        it.copy(
                            selectedLiveVehicleTripUpdateError =
                                "Nie udało się pobrać opóźnień: ${exception.message.orEmpty()}"
                        )
                    }
                }
            } finally {
                if (requestId == selectedVehicleTripRequestId) {
                    mutableUiState.update {
                        it.copy(isLoadingSelectedLiveVehicleTripUpdate = false)
                    }
                }
            }
        }
    }

    fun loadLiveMapStop(stop: StopEntity) {
        clearSelectedLiveVehicle()
        val requestId = ++liveMapStopRequestId
        liveMapStopTimetableJob?.cancel()
        mutableUiState.update {
            it.copy(
                selectedLiveMapStop = stop,
                liveMapStopTimetable = null,
                isLoadingLiveMapStopTimetable = true,
                liveMapStopTimetableError = null
            )
        }
        liveMapStopTimetableJob = viewModelScope.launch {
            try {
                val timetable = stopTimetableRepository.getTimetable(
                    stopId = stop.stopId,
                    today = LocalDateTime.now(KRAKOW_ZONE).toLocalDate()
                )
                if (requestId == liveMapStopRequestId) {
                    mutableUiState.update {
                        it.copy(
                            liveMapStopTimetable = timetable,
                            liveMapStopTimetableError = null
                        )
                    }
                }
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: SQLiteException) {
                if (requestId == liveMapStopRequestId) {
                    mutableUiState.update {
                        it.copy(
                            liveMapStopTimetableError =
                                "Nie można odczytać rozkładu przystanku: ${exception.message.orEmpty()}"
                        )
                    }
                }
            } catch (exception: IllegalStateException) {
                if (requestId == liveMapStopRequestId) {
                    mutableUiState.update {
                        it.copy(
                            liveMapStopTimetableError =
                                "Nie można odczytać rozkładu przystanku: ${exception.message.orEmpty()}"
                        )
                    }
                }
            } catch (exception: IOException) {
                if (requestId == liveMapStopRequestId) {
                    mutableUiState.update {
                        it.copy(
                            liveMapStopTimetableError =
                                "Nie udało się pobrać rozkładu przystanku: ${exception.message.orEmpty()}"
                        )
                    }
                }
            } finally {
                if (requestId == liveMapStopRequestId) {
                    mutableUiState.update {
                        it.copy(isLoadingLiveMapStopTimetable = false)
                    }
                }
            }
        }
    }

    fun clearLiveMapStop() {
        liveMapStopRequestId += 1
        liveMapStopTimetableJob?.cancel()
        liveMapStopTimetableJob = null
        mutableUiState.update {
            it.copy(
                selectedLiveMapStop = null,
                liveMapStopTimetable = null,
                isLoadingLiveMapStopTimetable = false,
                liveMapStopTimetableError = null
            )
        }
    }

    fun loadWeather() {
        if (mutableUiState.value.isLoadingWeather) return
        weatherJob?.cancel()
        weatherJob = viewModelScope.launch {
            mutableUiState.update {
                it.copy(isLoadingWeather = true, weatherError = null)
            }
            try {
                val location = currentLocationProvider.getCurrentLocation()
                val forecast = openMeteoClient.getForecast(
                    latitude = location.latitude,
                    longitude = location.longitude
                )
                mutableUiState.update {
                    it.copy(
                        weatherForecast = forecast,
                        weatherError = null,
                        isLoadingWeather = false
                    )
                }
            } catch (exception: LocationPermissionRequiredException) {
                showWeatherError("Zezwól na dostęp do lokalizacji, aby pobrać pogodę.")
            } catch (exception: LocationUnavailableException) {
                showWeatherError("Nie udało się ustalić bieżącej lokalizacji.")
            } catch (exception: SecurityException) {
                showWeatherError("Brak dostępu do lokalizacji.")
            } catch (exception: HttpRequestTimeoutException) {
                showWeatherError("Serwis pogody nie odpowiedział na czas. Spróbuj ponownie.")
            } catch (exception: OpenMeteoRequestException) {
                showWeatherError("Nie udało się pobrać prognozy. Spróbuj ponownie później.")
            } catch (exception: IOException) {
                showWeatherError("Nie udało się pobrać pogody. Sprawdź połączenie z internetem.")
            } catch (exception: SerializationException) {
                showWeatherError("Serwis pogody zwrócił nieprawidłowe dane.")
            } catch (exception: DateTimeException) {
                showWeatherError("Serwis pogody zwrócił nieprawidłowe godziny prognozy.")
            } catch (exception: IllegalArgumentException) {
                showWeatherError("Serwis pogody zwrócił nieprawidłowe dane.")
            } finally {
                mutableUiState.update { it.copy(isLoadingWeather = false) }
            }
        }
    }

    private fun showWeatherError(message: String) {
        mutableUiState.update {
            it.copy(weatherError = message)
        }
    }

    fun refreshServiceNews(force: Boolean = false) {
        val now = System.currentTimeMillis()
        if (mutableUiState.value.isLoadingServiceNews) return
        if (!force && now - lastServiceNewsAttemptAtMillis < SERVICE_NEWS_REFRESH_INTERVAL_MS) {
            return
        }
        lastServiceNewsAttemptAtMillis = now
        serviceNewsJob?.cancel()
        serviceNewsJob = viewModelScope.launch {
            mutableUiState.update {
                it.copy(isLoadingServiceNews = true, serviceNewsError = null)
            }
            try {
                val items = ztpServiceNewsClient.getLatestItems()
                mutableUiState.update {
                    it.copy(serviceNews = items, serviceNewsError = null)
                }
            } catch (exception: HttpRequestTimeoutException) {
                showServiceNewsError("Komunikaty ZTP nie odpowiadają. Spróbuj ponownie.")
            } catch (exception: IOException) {
                showServiceNewsError("Nie można pobrać komunikatów ZTP. Sprawdź internet.")
            } finally {
                mutableUiState.update { it.copy(isLoadingServiceNews = false) }
            }
        }
    }

    private fun showServiceNewsError(message: String) {
        mutableUiState.update { it.copy(serviceNewsError = message) }
    }

    fun refreshLiveServiceAlerts(force: Boolean = false) {
        val now = System.currentTimeMillis()
        if (mutableUiState.value.isLoadingLiveServiceAlerts) return
        if (
            !force &&
            now - lastLiveServiceAlertsAttemptAtMillis < SERVICE_NEWS_REFRESH_INTERVAL_MS
        ) {
            return
        }
        lastLiveServiceAlertsAttemptAtMillis = now
        liveServiceAlertsJob?.cancel()
        liveServiceAlertsJob = viewModelScope.launch {
            mutableUiState.update {
                it.copy(isLoadingLiveServiceAlerts = true, liveServiceAlertsError = null)
            }
            try {
                val result = serviceAlertsClient.getAllAlerts()
                mutableUiState.update {
                    it.copy(
                        activeServiceAlerts = result.alerts,
                        liveServiceAlertsError = result.unavailableFeeds
                            .takeIf { it.isNotEmpty() }
                            ?.joinToString()
                            ?.let { feeds ->
                                "Bieżące alerty ZTP są częściowo niedostępne ($feeds)."
                            }
                    )
                }
            } catch (exception: HttpRequestTimeoutException) {
                showLiveServiceAlertsError("Bieżące alerty ZTP nie odpowiadają. Spróbuj ponownie.")
            } catch (exception: IOException) {
                showLiveServiceAlertsError("Nie można pobrać bieżących alertów ZTP.")
            } finally {
                mutableUiState.update { it.copy(isLoadingLiveServiceAlerts = false) }
            }
        }
    }

    private fun showLiveServiceAlertsError(message: String) {
        mutableUiState.update { it.copy(liveServiceAlertsError = message) }
    }

    private fun hasCurrentLocationPermission(): Boolean =
        appContext.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
            appContext.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    private fun hasFreshPrefetchedCurrentLocation(): Boolean {
        val cached = prefetchedCurrentLocation ?: return false
        val ageMillis = SystemClock.elapsedRealtime() - cached.acquiredAtElapsedRealtime
        return ageMillis in 0L..CURRENT_LOCATION_CACHE_TTL_MILLIS
    }

    private fun takeFreshPrefetchedCurrentLocation(): Location? {
        val cached = prefetchedCurrentLocation ?: return null
        prefetchedCurrentLocation = null
        val ageMillis = SystemClock.elapsedRealtime() - cached.acquiredAtElapsedRealtime
        return cached.location.takeIf { ageMillis in 0L..CURRENT_LOCATION_CACHE_TTL_MILLIS }
    }

    private fun cancelCurrentLocationResolution() {
        currentLocationResolutionJob?.cancel()
    }

    fun selectOriginAddress(place: GeoPlace) {
        cancelCurrentLocationResolution()
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
        setDestinationAddress(place, searchAfterSelection = false)
    }

    fun selectDestinationAddressAndSearch(place: GeoPlace) {
        setDestinationAddress(place, searchAfterSelection = true)
    }

    private fun setDestinationAddress(
        place: GeoPlace,
        searchAfterSelection: Boolean
    ) {
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
            if (searchAfterSelection) {
                searchRoute()
            }
        }
    }

    fun searchRoute() {
        cancelCurrentLocationResolution()
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
        val routeTimePlan = state.routeTimePlan
        if (routeTimePlan != null && !routeTimePlan.dateTime.isAfter(LocalDateTime.now())) {
            showMessage("Wybrana data i godzina już minęła.")
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
                val plannedDateTime = routeTimePlan?.dateTime ?: now
                val searchStart = if (routeTimePlan?.mode == JourneyTimeMode.ARRIVE_BY) {
                    plannedDateTime.minusSeconds(ROUTE_SEARCH_WINDOW_SECONDS.toLong())
                } else {
                    plannedDateTime
                }
                val candidates = routePlanner.findJourneys(
                    originAccesses = originAccesses,
                    destinationAccesses = destinationAccesses,
                    startDateTime = searchStart,
                    searchWindowSeconds = ROUTE_SEARCH_WINDOW_SECONDS,
                    maxResults = MAX_CSA_JOURNEY_CANDIDATES,
                    arrivalDeadline = routeTimePlan
                        ?.takeIf { it.mode == JourneyTimeMode.ARRIVE_BY }
                        ?.dateTime
                )
                val currentServiceTime = GtfsServiceClock.from(now)
                val preferLatestDeparture = routeTimePlan?.mode == JourneyTimeMode.ARRIVE_BY
                val journeyOptions = selectDistinctJourneyDepartures(
                    candidates,
                    latestFirst = preferLatestDeparture
                )
                diagnostics.record(
                    "Wyniki: ${candidates.size} kandydatów, " +
                        "${journeyOptions.size} tras po wyborze alternatyw."
                )
                val firstJourney = journeyOptions.firstOrNull()
                val transitSteps = firstJourney?.steps
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
                    ?: firstJourney?.originAccess?.stopId?.let(stopsById::get)
                    ?: state.originStop
                val selectedDestination = lastTransitStopId?.let(stopsById::get)
                    ?: firstJourney?.destinationAccess?.stopId?.let(stopsById::get)
                    ?: state.destinationStop
                val noJourneyMessage = when (routeTimePlan?.mode) {
                    JourneyTimeMode.DEPART_AFTER ->
                        "Nie znaleziono połączenia w ciągu dwóch godzin od wybranej godziny."
                    JourneyTimeMode.ARRIVE_BY ->
                        "Nie znaleziono połączenia przed wybraną godziną przyjazdu."
                    null ->
                        "Nie znaleziono połączenia w ciągu najbliższych dwóch godzin."
                }
                mutableUiState.update {
                    it.copy(
                        originStop = selectedOrigin,
                        destinationStop = selectedDestination,
                        journey = firstJourney,
                        journeyOptions = journeyOptions,
                        liveDepartures = null,
                        routeSearchMessage = if (firstJourney == null) {
                            noJourneyMessage
                        } else {
                            null
                        },
                        statusMessage = if (firstJourney == null) {
                            noJourneyMessage
                        } else {
                            null
                        }
                    )
                }
                if (routeTimePlan == null && firstJourney != null && firstTransitStopId != null) {
                    diagnostics.record("Trasa gotowa; odjazdy TTSS pobierane w tle.")
                    viewModelScope.launch {
                        try {
                            val liveDepartures = ttssDepartureRepository.getDepartures(
                                stopId = firstTransitStopId,
                                serviceDate = currentServiceTime.serviceDate,
                                startTimeSeconds = currentServiceTime.timeSeconds,
                                nowTimeSeconds = now.toLocalTime().toSecondOfDay()
                            )
                            diagnostics.record("Odpowiedź TTSS odebrana.")
                            mutableUiState.update { current ->
                                if (current.journey !== firstJourney) {
                                    current
                                } else {
                                    current.copy(liveDepartures = liveDepartures)
                                }
                            }
                        } catch (exception: SQLiteException) {
                            diagnostics.record("Błąd bazy podczas pobierania odjazdów TTSS.")
                            mutableUiState.update { current ->
                                if (current.journey !== firstJourney) {
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
                                if (current.journey !== firstJourney) {
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
        GtfsScheduleUpdateScheduler.requestRefresh(appContext)
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

    fun setRouteTimePlan(plan: JourneyTimePlan?) {
        mutableUiState.update {
            it.copy(
                routeTimePlan = plan,
                journey = null,
                journeyOptions = emptyList(),
                arrivalTarget = null,
                liveDepartures = null,
                routeSearchMessage = null,
                statusMessage = null
            )
        }
    }

    fun routeToWork() {
        runContextAction(SavedDestination.WORK)
    }

    fun routeCustom() {
        cancelCurrentLocationResolution()
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
        checkForUpdates(showStatus = true)
    }

    fun dismissUpdatePrompt() {
        mutableUiState.update { it.copy(updatePrompt = null) }
    }

    fun downloadAppUpdate(update: UpdateCheckResult) {
        if (mutableUiState.value.isDownloadingAppUpdate) return
        mutableUiState.update {
            it.copy(
                isDownloadingAppUpdate = true,
                appUpdateDownloadProgressPercent = 0,
                downloadedAppUpdatePath = null,
                downloadedAppUpdateVersion = null,
                appUpdateDownloadError = null
            )
        }
        viewModelScope.launch {
            try {
                val downloadedUpdate = appUpdateDownloader.download(update) { progressPercent ->
                    mutableUiState.update { state ->
                        if (state.isDownloadingAppUpdate) {
                            state.copy(appUpdateDownloadProgressPercent = progressPercent)
                        } else {
                            state
                        }
                    }
                }
                mutableUiState.update {
                    it.copy(
                        downloadedAppUpdatePath = downloadedUpdate.file.absolutePath,
                        downloadedAppUpdateVersion = downloadedUpdate.version,
                        appUpdateDownloadError = null
                    )
                }
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: IOException) {
                mutableUiState.update {
                    it.copy(
                        appUpdateDownloadError =
                            "Nie udało się pobrać aktualizacji: ${exception.message.orEmpty()}"
                    )
                }
            } catch (exception: SecurityException) {
                mutableUiState.update {
                    it.copy(
                        appUpdateDownloadError = "Android zablokował dostęp do pliku aktualizacji."
                    )
                }
            } finally {
                mutableUiState.update {
                    it.copy(
                        isDownloadingAppUpdate = false,
                        appUpdateDownloadProgressPercent = null
                    )
                }
            }
        }
    }

    fun clearDownloadedAppUpdate() {
        mutableUiState.update {
            it.copy(
                downloadedAppUpdatePath = null,
                downloadedAppUpdateVersion = null
            )
        }
    }

    fun skipUpdatePrompt() {
        val version = mutableUiState.value.updatePrompt?.latestVersion ?: return
        viewModelScope.launch {
            try {
                appPreferences.saveSkippedUpdateVersion(version)
                mutableUiState.update {
                    if (it.updatePrompt?.latestVersion == version) {
                        it.copy(updatePrompt = null)
                    } else {
                        it
                    }
                }
            } catch (exception: IOException) {
                showMessage(
                    "Nie udało się zapamiętać pominiętej wersji: ${exception.message.orEmpty()}"
                )
            }
        }
    }

    private fun checkForUpdates(showStatus: Boolean) {
        viewModelScope.launch {
            mutableUiState.update {
                it.copy(
                    isCheckingUpdates = true,
                    updateCheckMessage = if (showStatus) null else it.updateCheckMessage,
                    updateCheckResult = if (showStatus) null else it.updateCheckResult
                )
            }
            try {
                val result = updateChecker.checkForUpdates()
                val skippedVersion = appPreferences.skippedUpdateVersion.first()
                mutableUiState.update { state ->
                    val message = if (showStatus) {
                        if (result.updateAvailable) {
                            "Dostępna jest wersja ${result.latestVersion}."
                        } else {
                            "Masz aktualną wersję (${result.currentVersion})."
                        }
                    } else {
                        state.updateCheckMessage
                    }
                    state.copy(
                        updateCheckMessage = message,
                        updateCheckResult = result,
                        updatePrompt = result.takeIf {
                            it.updateAvailable && it.latestVersion != skippedVersion
                        }
                    )
                }
            } catch (_: NoReleaseAvailableException) {
                mutableUiState.update { state ->
                    state.copy(
                        updateCheckMessage = if (showStatus) {
                            "Nie opublikowano jeszcze wydania dla tej wersji aplikacji na GitHub. " +
                                "Sprawdź ponownie później."
                        } else {
                            state.updateCheckMessage
                        },
                        updateCheckResult = null,
                        updatePrompt = null
                    )
                }
            } catch (exception: IOException) {
                mutableUiState.update { state ->
                    state.copy(
                        updateCheckMessage = if (showStatus) {
                            "Nie udało się sprawdzić aktualizacji: ${exception.message.orEmpty()}"
                        } else {
                            state.updateCheckMessage
                        },
                        updateCheckResult = null,
                        updatePrompt = null
                    )
                }
            } catch (exception: IllegalArgumentException) {
                mutableUiState.update { state ->
                    state.copy(
                        updateCheckMessage = if (showStatus) {
                            "Nie udało się porównać wersji: ${exception.message.orEmpty()}"
                        } else {
                            state.updateCheckMessage
                        },
                        updateCheckResult = null,
                        updatePrompt = null
                    )
                }
            } catch (exception: IllegalStateException) {
                mutableUiState.update { state ->
                    state.copy(
                        updateCheckMessage = if (showStatus) {
                            "Nie udało się sprawdzić aktualizacji: ${exception.message.orEmpty()}"
                        } else {
                            state.updateCheckMessage
                        },
                        updateCheckResult = null,
                        updatePrompt = null
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
                mutableUiState.update {
                    it.copy(
                        stops = stops,
                        isImporting = stops.isEmpty() &&
                            it.databaseUpdateProgressPercent != null
                    )
                }
            }
        }
    }

    fun saveDestinationShortcutNames(names: DestinationShortcutNames) {
        if (mutableUiState.value.isSavingShortcutNames) return
        mutableUiState.update {
            it.copy(isSavingShortcutNames = true, shortcutNamesSaveError = null)
        }
        viewModelScope.launch {
            try {
                appPreferences.saveDestinationShortcutNames(names)
            } catch (exception: IOException) {
                mutableUiState.update {
                    it.copy(
                        shortcutNamesSaveError =
                            "Nie można zapisać nazw skrótów: ${exception.message.orEmpty()}"
                    )
                }
            } finally {
                mutableUiState.update { it.copy(isSavingShortcutNames = false) }
            }
        }
    }

    private fun observePreferences() {
        viewModelScope.launch {
            appPreferences.destinationShortcutNames.collect { names ->
                mutableUiState.update { it.copy(destinationShortcutNames = names) }
            }
        }
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

    private fun observeScheduleUpdates() {
        val workManager = WorkManager.getInstance(appContext)
        viewModelScope.launch {
            combine(
                workManager.getWorkInfosForUniqueWorkFlow(
                    GtfsScheduleUpdateScheduler.STARTUP_WORK_NAME
                ),
                workManager.getWorkInfosForUniqueWorkFlow(
                    GtfsScheduleUpdateScheduler.PERIODIC_WORK_NAME
                )
            ) { startupWorkInfos, periodicWorkInfos ->
                startupWorkInfos + periodicWorkInfos
            }.collect { workInfos ->
                val runningWork = workInfos
                    .filter { it.state == WorkInfo.State.RUNNING }
                    .maxByOrNull {
                        it.progress.getInt(
                            GtfsScheduleUpdateScheduler.PROGRESS_PERCENT_KEY,
                            0
                        )
                    }
                val startupWork = workInfos.firstOrNull {
                    GtfsScheduleUpdateScheduler.STARTUP_WORK_NAME in it.tags &&
                        !it.state.isFinished
                } ?: workInfos.lastOrNull {
                    GtfsScheduleUpdateScheduler.STARTUP_WORK_NAME in it.tags
                }
                val startupIsPending = startupWork?.state == WorkInfo.State.ENQUEUED ||
                    startupWork?.state == WorkInfo.State.BLOCKED
                val startupFailed = startupWork?.takeIf {
                    it.state == WorkInfo.State.FAILED
                }
                val isScheduleEmpty = mutableUiState.value.stops.isEmpty()
                val progress = runningWork?.progress?.getInt(
                    GtfsScheduleUpdateScheduler.PROGRESS_PERCENT_KEY,
                    0
                )?.coerceIn(0, 100)
                val progressMessage = when {
                    runningWork != null -> runningWork.progress.getString(
                        GtfsScheduleUpdateScheduler.PROGRESS_MESSAGE_KEY
                    ).orEmpty()
                    isScheduleEmpty && startupFailed != null ->
                        startupFailed.outputData.getString(
                            GtfsScheduleUpdateScheduler.ERROR_MESSAGE_KEY
                        ) ?: "Nie udało się pobrać rozkładu ZTP. Spróbuj ponownie."
                    isScheduleEmpty && startupIsPending ->
                        "Oczekiwanie na połączenie z internetem..."
                    else -> ""
                }

                mutableUiState.update { state ->
                    state.copy(
                        databaseUpdateProgressPercent = progress,
                        isImporting = state.stops.isEmpty() && progress != null,
                        progressMessage = progressMessage
                    )
                }
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
        cancelCurrentLocationResolution()
        viewModelScope.launch {
            mutableUiState.update {
                it.copy(
                    isSearching = true,
                    statusMessage = null,
                    journey = null,
                    journeyOptions = emptyList(),
                    arrivalTarget = null,
                    routeTimePlan = null,
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
        const val TAG = "SearchViewModel"
        const val CURRENT_LOCATION_CACHE_TTL_MILLIS = 60_000L
        const val SERVICE_NEWS_REFRESH_INTERVAL_MS = 5 * 60 * 1_000L
        const val ADDRESS_LOOKUP_DEBOUNCE_MILLIS = 450L
        const val MIN_ADDRESS_QUERY_LENGTH = 3
        const val MAX_NEARBY_STOPS = 6
        const val MAX_AUTO_STOP_ACCESS_METERS = 1_500.0
        const val ROUTE_SEARCH_WINDOW_SECONDS = 2 * 60 * 60
        const val MAX_CSA_JOURNEY_CANDIDATES = 100
    }
}
