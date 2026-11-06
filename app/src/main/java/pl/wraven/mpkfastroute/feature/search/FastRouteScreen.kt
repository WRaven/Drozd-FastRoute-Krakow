package pl.wraven.mpkfastroute.feature.search

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlin.math.roundToInt
import pl.wraven.mpkfastroute.core.audio.RadioStations
import pl.wraven.mpkfastroute.core.csa.CsaJourney
import pl.wraven.mpkfastroute.core.csa.Haversine
import pl.wraven.mpkfastroute.core.csa.JourneyStep
import pl.wraven.mpkfastroute.core.database.StopEntity
import pl.wraven.mpkfastroute.core.live.LiveDeparturesResult
import pl.wraven.mpkfastroute.core.location.GeoPlace

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FastRouteScreen(viewModel: SearchViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    var settingsVisible by remember { mutableStateOf(false) }
    var radioSelectionVisible by remember { mutableStateOf(false) }
    var pendingLocationAction by remember { mutableStateOf<(() -> Unit)?>(null) }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (
            permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        ) {
            pendingLocationAction?.invoke()
        } else {
            viewModel.showMessage("Aby użyć tej akcji, zezwól na dostęp do lokalizacji.")
        }
        pendingLocationAction = null
    }

    val runWithLocationPermission: (() -> Unit) -> Unit = { action ->
        val hasFineLocation = context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        val hasCoarseLocation =
            context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
        if (hasFineLocation || hasCoarseLocation) {
            action()
        } else {
            pendingLocationAction = action
            locationPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    LaunchedEffect(state.statusMessage) {
        val message = state.statusMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        viewModel.clearMessage()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("MPK Kraków FastRoute") },
                actions = {
                    TextButton(onClick = { settingsVisible = true }) {
                        Text("Ustawienia")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { contentPadding ->
        if (state.isScheduleEmpty) {
            OnboardingContent(
                progressMessage = state.progressMessage,
                isImporting = state.isImporting,
                onRefresh = viewModel::refreshSchedule,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(contentPadding)
            )
        } else {
            SearchContent(
                state = state,
                onUseCurrentLocation = {
                    runWithLocationPermission(viewModel::useCurrentLocation)
                },
                onOriginQueryChange = viewModel::setOriginQuery,
                onSelectOrigin = viewModel::selectOrigin,
                onSearchOriginAddress = viewModel::searchOriginAddress,
                onSelectOriginAddress = viewModel::selectOriginAddress,
                onDestinationQueryChange = viewModel::setDestinationQuery,
                onSelectDestination = viewModel::selectDestination,
                onSearchAddress = viewModel::searchDestinationAddress,
                onSelectAddress = viewModel::selectDestinationAddress,
                onSearch = viewModel::searchRoute,
                onGoHome = { runWithLocationPermission(viewModel::routeHome) },
                onGoToWork = { runWithLocationPermission(viewModel::routeToWork) },
                onRandomJourney = { runWithLocationPermission(viewModel::randomJourney) },
                onPlayRadio = viewModel::playLastSelectedRadio,
                onStopRadio = viewModel::stopRadio,
                onChooseRadio = { radioSelectionVisible = true },
                onSaveHome = viewModel::saveSelectedStopAsHome,
                onSaveWork = viewModel::saveSelectedStopAsWork,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(contentPadding)
            )
        }
    }

    if (settingsVisible) {
        SettingsDialog(
            progressMessage = state.progressMessage,
            isImporting = state.isImporting,
            homeStopName = state.homePlace?.label
                ?: state.stops.firstOrNull { it.stopId == state.homeStopId }
                    ?.let { StopNameGrouper.mainName(it.stopName) },
            workStopName = state.workPlace?.label
                ?: state.stops.firstOrNull { it.stopId == state.workStopId }
                    ?.let { StopNameGrouper.mainName(it.stopName) },
            onRefresh = viewModel::refreshSchedule,
            onDismiss = { settingsVisible = false }
        )
    }

    if (radioSelectionVisible) {
        ModalBottomSheet(onDismissRequest = { radioSelectionVisible = false }) {
            RadioStations.all.forEachIndexed { index, station ->
                ListItem(
                    headlineContent = { Text(station.name) },
                    supportingContent = { Text(station.streamUrl) },
                    modifier = Modifier.clickable {
                        radioSelectionVisible = false
                        viewModel.playRadioStation(index)
                    }
                )
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun OnboardingContent(
    progressMessage: String,
    isImporting: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Pobieranie rozkładu ZTP Kraków",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(16.dp))
        if (isImporting) {
            CircularProgressIndicator()
        }
        if (progressMessage.isNotBlank()) {
            Spacer(Modifier.height(12.dp))
            Text(progressMessage)
        }
        Spacer(Modifier.height(20.dp))
        Button(onClick = onRefresh, enabled = !isImporting) {
            Text(if (isImporting) "Pobieranie..." else "Spróbuj ponownie")
        }
    }
}

@Composable
private fun SearchContent(
    state: SearchUiState,
    onUseCurrentLocation: () -> Unit,
    onOriginQueryChange: (String) -> Unit,
    onSelectOrigin: (StopGroup) -> Unit,
    onSearchOriginAddress: () -> Unit,
    onSelectOriginAddress: (GeoPlace) -> Unit,
    onDestinationQueryChange: (String) -> Unit,
    onSelectDestination: (StopGroup) -> Unit,
    onSearchAddress: () -> Unit,
    onSelectAddress: (GeoPlace) -> Unit,
    onSearch: () -> Unit,
    onGoHome: () -> Unit,
    onGoToWork: () -> Unit,
    onRandomJourney: () -> Unit,
    onPlayRadio: () -> Unit,
    onStopRadio: () -> Unit,
    onChooseRadio: () -> Unit,
    onSaveHome: () -> Unit,
    onSaveWork: () -> Unit,
    modifier: Modifier = Modifier
) {
    val stopGroups = remember(state.stops) { StopNameGrouper.group(state.stops) }
    var showOriginStopPicker by remember(state.originPlace) { mutableStateOf(false) }
    var showDestinationStopPicker by remember(state.destinationPlace) {
        mutableStateOf(false)
    }

    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Szybkie akcje", style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AssistChip(
                onClick = onGoHome,
                enabled = state.hasHomeDestination && !state.isSearching,
                label = { Text("🏠 Do domu") }
            )
            AssistChip(
                onClick = onGoToWork,
                enabled = state.hasWorkDestination && !state.isSearching,
                label = { Text("🏢 Do pracy") }
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = onPlayRadio,
                enabled = !state.isSearching,
                modifier = Modifier.weight(1f)
            ) {
                Text("📻 Uruchom radio · ${RadioStations.all[state.lastSelectedRadioIndex].name}")
            }
            Spacer(Modifier.width(8.dp))
            TextButton(onClick = onChooseRadio) {
                Text("Wybierz")
            }
        }
        TextButton(onClick = onStopRadio) {
            Text("⏹ Zatrzymaj radio")
        }
        AssistChip(
            onClick = onRandomJourney,
            enabled = !state.isSearching,
            label = {
                Text(if (state.isSearching) "Szukam trasy..." else "🎲 Zaszalej")
            }
        )
        if (state.isSearching) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
        }

        Text("Znajdź połączenie", style = MaterialTheme.typography.titleMedium)
        Text("Początek", style = MaterialTheme.typography.titleSmall)
        Button(onClick = onUseCurrentLocation, enabled = !state.isSearching) {
            Text("📍 Użyj mojej lokalizacji")
        }
        Text("albo wpisz adres lub nazwę przystanku")
        OutlinedTextField(
            value = state.originQuery,
            onValueChange = onOriginQueryChange,
            label = { Text("Adres lub nazwa przystanku") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        TextButton(
            onClick = onSearchOriginAddress,
            enabled = !state.isSearching && state.originQuery.isNotBlank()
        ) {
            Text("Szukaj adresu początku")
        }
        state.originAddressCandidates.forEach { place ->
            TextButton(
                onClick = { onSelectOriginAddress(place) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(place.label)
            }
        }
        if (state.originPlace == null && state.originAddressCandidates.isEmpty()) {
            StopGroupPicker(
                query = state.originQuery,
                groups = stopGroups,
                recentStopNames = state.recentStopSearches,
                onSelectGroup = onSelectOrigin
            )
        }
        state.originPlace?.let { place ->
            Text("Początek: ${place.label}")
            if (!state.originStopManuallySelected) {
                Text("Przystanek dobiorę podczas wyszukiwania trasy.")
                TextButton(
                    onClick = { showOriginStopPicker = !showOriginStopPicker },
                    enabled = state.nearbyOriginGroups.isNotEmpty() && !state.isSearching
                ) {
                    Text(if (showOriginStopPicker) "Ukryj przystanki" else "Wybierz przystanek")
                }
                if (showOriginStopPicker) {
                    Text("Wybierz przystanek początkowy:")
                    state.nearbyOriginGroups.forEach { group ->
                        val distanceMeters = distanceToStopGroup(place, group)
                        TextButton(
                            onClick = {
                                showOriginStopPicker = false
                                onSelectOrigin(group)
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("${group.name} · ${distanceMeters.toInt()} m")
                        }
                    }
                }
            }
        }
        state.originStop?.let { stop ->
            Text("Przystanek początkowy: ${StopNameGrouper.mainName(stop.stopName)}")
        }

        Text("Cel", style = MaterialTheme.typography.titleSmall)
        OutlinedTextField(
            value = state.destinationQuery,
            onValueChange = onDestinationQueryChange,
            label = { Text("Adres lub nazwa przystanku") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(
                onClick = onSearchAddress,
                enabled = !state.isSearching && state.destinationQuery.isNotBlank()
            ) {
                Text("Szukaj adresu")
            }
        }
        state.addressCandidates.forEach { place ->
            TextButton(
                onClick = { onSelectAddress(place) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(place.label)
            }
        }
        if (state.destinationPlace != null) {
            Text("Adres celu: ${state.destinationPlace.label}")
            if (!state.destinationStopManuallySelected) {
                Text("Przystanek dobiorę podczas wyszukiwania trasy.")
                TextButton(
                    onClick = { showDestinationStopPicker = !showDestinationStopPicker },
                    enabled = state.nearbyDestinationGroups.isNotEmpty() && !state.isSearching
                ) {
                    Text(
                        if (showDestinationStopPicker) {
                            "Ukryj przystanki"
                        } else {
                            "Wybierz przystanek"
                        }
                    )
                }
                if (showDestinationStopPicker) {
                    Text("Wybierz przystanek docelowy:")
                    state.nearbyDestinationGroups.forEach { group ->
                        val distanceMeters = distanceToStopGroup(state.destinationPlace, group)
                        TextButton(
                            onClick = {
                                showDestinationStopPicker = false
                                onSelectDestination(group)
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("${group.name} · ${distanceMeters.toInt()} m")
                        }
                    }
                }
            }
        } else if (state.addressCandidates.isEmpty()) {
            StopGroupPicker(
                query = state.destinationQuery,
                groups = stopGroups,
                recentStopNames = state.recentStopSearches,
                onSelectGroup = onSelectDestination
            )
        }
        state.destinationStop?.let { stop ->
            Text("Przystanek docelowy: ${StopNameGrouper.mainName(stop.stopName)}")
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = onSearch,
                enabled = !state.isSearching &&
                    (state.originPlace != null || state.originStop != null) &&
                    (state.destinationPlace != null || state.destinationStop != null)
            ) {
                Text(if (state.isSearching) "Szukam..." else "Szukaj trasy")
            }
            TextButton(
                onClick = onSaveHome,
                enabled = state.destinationPlace != null
            ) {
                Text("Zapisz jako dom")
            }
            TextButton(
                onClick = onSaveWork,
                enabled = state.destinationPlace != null
            ) {
                Text("Zapisz jako pracę")
            }
        }

        val leastWalkingIndex = state.journeyOptions.indices.minByOrNull { index ->
            state.journeyOptions[index].totalWalkingDistanceMeters
        }
        state.journeyOptions.forEachIndexed { index, journey ->
            JourneyResult(
                journey = journey,
                stops = state.stops,
                origin = state.stops.firstOrNull {
                    it.stopId == journey.originAccess.stopId
                } ?: state.originStop,
                destination = state.stops.firstOrNull {
                    it.stopId == journey.destinationAccess.stopId
                } ?: state.destinationStop,
                routeLabel = when {
                    index == 0 && index == leastWalkingIndex ->
                        "Najszybciej · najmniej chodzenia"
                    index == 0 -> "Najszybciej"
                    index == leastWalkingIndex -> "Najmniej chodzenia"
                    else -> "Alternatywa ${index + 1}"
                }
            )
        }
        if (state.journeyOptions.isEmpty()) {
            state.journey?.let { journey ->
                JourneyResult(
                    journey = journey,
                    stops = state.stops,
                    origin = state.originStop,
                    destination = state.destinationStop
                )
            }
        }
        state.liveDepartures?.let { departures ->
            LiveDeparturesSection(
                stop = state.originStop,
                departures = departures
            )
        }
    }
}

@Composable
private fun StopGroupPicker(
    query: String,
    groups: List<StopGroup>,
    recentStopNames: List<String>,
    onSelectGroup: (StopGroup) -> Unit
) {
    val recentGroups = recentStopNames.mapNotNull { recentName ->
        groups.firstOrNull { it.name.equals(recentName, ignoreCase = true) }
            ?: groups.firstOrNull { StopNameGrouper.sameNameForSearch(it.name, recentName) }
    }.distinctBy { StopNameGrouper.normalizeForSearch(it.name) }
        .filter { query.isBlank() || StopNameGrouper.matches(query, it.name) }
        .take(MAX_RECENT_STOP_SUGGESTIONS)
    val recentGroupNames = recentGroups
        .mapTo(mutableSetOf()) { StopNameGrouper.normalizeForSearch(it.name) }
    val matchingGroups = if (query.length >= MIN_STOP_QUERY_LENGTH) {
        groups.asSequence()
            .filter { group ->
                StopNameGrouper.matches(query, group.name) ||
                    group.stops.any { stop ->
                        stop.stopCode?.let { StopNameGrouper.matches(query, it) } == true
                    }
            }
            .filterNot {
                StopNameGrouper.normalizeForSearch(it.name) in recentGroupNames
            }
            .take(MAX_STOP_SUGGESTIONS)
            .toList()
    } else {
        emptyList()
    }

    if (recentGroups.isNotEmpty()) {
        Text("Ostatnie przystanki", style = MaterialTheme.typography.labelLarge)
        recentGroups.forEach { group ->
            TextButton(
                onClick = { onSelectGroup(group) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(group.name)
            }
        }
    }
    matchingGroups.forEach { group ->
        TextButton(
            onClick = { onSelectGroup(group) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(group.name)
        }
    }
}

@Composable
private fun JourneyResult(
    journey: CsaJourney,
    stops: List<StopEntity>,
    origin: StopEntity?,
    destination: StopEntity?,
    routeLabel: String? = null
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(routeLabel ?: "Wynik", style = MaterialTheme.typography.titleLarge)
        Text(
            "${origin?.stopName?.let(StopNameGrouper::mainName).orEmpty()} → " +
                destination?.stopName?.let(StopNameGrouper::mainName).orEmpty(),
            fontWeight = FontWeight.Medium
        )
        Text(
            (if (routeLabel == null) "Przyjazd " else "Przyjazd do celu około ") +
                formatGtfsTime(
                    if (routeLabel == null) journey.arrivalTime else journey.estimatedArrivalTime
                ) +
                if (journey.transferPenaltySeconds > 0) {
                    " · kara przesiadkowa +${journey.transferPenaltySeconds / 60} min"
                } else {
                    ""
                }
        )
        if (routeLabel != null) {
            Text("Czas od wyjścia: ${formatDuration(journey.totalDurationSeconds)}")
            Text(
                "Spacer łącznie: " +
                    "${formatDistance(journey.totalWalkingDistanceMeters)}"
            )
            Text(
                "Do przystanku ${formatDistance(journey.originAccess.walkingDistanceMeters)} · " +
                    "przesiadki " +
                    "${formatDistance(journey.transferWalkingDistanceMeters)} · " +
                    "od przystanku ${formatDistance(journey.destinationAccess.walkingDistanceMeters)}"
            )
        }
        journey.steps.forEach { step ->
            when (step) {
                is JourneyStep.Transit -> {
                    val connection = step.connection
                    Text(
                        "Linia ${connection.routeShortName}: " +
                            "${stopName(stops, connection.departureStopId)} " +
                            "${formatGtfsTime(connection.departureTime)} → " +
                            "${stopName(stops, connection.arrivalStopId)} " +
                            formatGtfsTime(connection.arrivalTime)
                    )
                }

                is JourneyStep.Walk -> {
                    val footpath = step.footpath
                    Text(
                        "Przejście ${stopName(stops, footpath.fromStopId)} → " +
                            "${stopName(stops, footpath.toStopId)} " +
                            "(${formatDistance(footpath.distanceMeters)}, " +
                            "${formatDuration(footpath.durationSeconds)})"
                    )
                }
            }
        }
    }
}

@Composable
private fun LiveDeparturesSection(
    stop: StopEntity?,
    departures: LiveDeparturesResult
) {
    if (departures.departures.isEmpty()) {
        return
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            "Odjazdy z ${stop?.stopName?.let(StopNameGrouper::mainName).orEmpty()}",
            style = MaterialTheme.typography.titleMedium
        )
        departures.departures.take(MAX_DISPLAYED_DEPARTURES).forEach { departure ->
            val liveIndicator = if (departure.isLive) " · LIVE" else ""
            Text(
                "${departure.routeShortName} → ${departure.direction}: " +
                    "${formatGtfsTime(departure.estimatedDepartureTime)}$liveIndicator",
                color = if (departure.isLive) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurface
                }
            )
        }
    }
}

@Composable
private fun SettingsDialog(
    progressMessage: String,
    isImporting: Boolean,
    homeStopName: String?,
    workStopName: String?,
    onRefresh: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Ustawienia") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Dom: ${homeStopName ?: "nie ustawiono"}")
                Text("Praca: ${workStopName ?: "nie ustawiono"}")
                Button(onClick = onRefresh, enabled = !isImporting) {
                    Text(if (isImporting) "Aktualizuję..." else "Odśwież rozkład GTFS")
                }
                if (progressMessage.isNotBlank()) {
                    Text(progressMessage)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Gotowe")
            }
        }
    )
}

private fun stopName(stops: List<StopEntity>, stopId: String): String =
    stops.firstOrNull { it.stopId == stopId }?.let { stop ->
        StopNameGrouper.mainName(stop.stopName)
    } ?: "Nieznany przystanek"

private fun distanceToStopGroup(place: GeoPlace, group: StopGroup): Double =
    group.stops.minOfOrNull { stop ->
        Haversine.distanceMeters(place.latitude, place.longitude, stop.stopLat, stop.stopLon)
    } ?: throw IllegalStateException("A stop group cannot be empty.")

private fun formatGtfsTime(seconds: Int): String {
    val totalMinutes = seconds / 60
    return "%02d:%02d".format(totalMinutes / 60, totalMinutes % 60)
}

private fun formatDuration(seconds: Int): String {
    val totalMinutes = (seconds + 59) / 60
    return if (totalMinutes < 60) {
        "$totalMinutes min"
    } else {
        "${totalMinutes / 60} godz. ${totalMinutes % 60} min"
    }
}

private fun formatDistance(meters: Double): String = "${meters.roundToInt()} m"

private const val MIN_STOP_QUERY_LENGTH = 2
private const val MAX_STOP_SUGGESTIONS = 6
private const val MAX_RECENT_STOP_SUGGESTIONS = 3
private const val MAX_DISPLAYED_DEPARTURES = 12
