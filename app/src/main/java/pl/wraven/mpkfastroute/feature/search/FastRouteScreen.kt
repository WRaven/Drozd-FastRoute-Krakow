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
import pl.wraven.mpkfastroute.core.audio.RadioStations
import pl.wraven.mpkfastroute.core.csa.JourneyStep
import pl.wraven.mpkfastroute.core.database.StopEntity

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
                onOriginQueryChange = viewModel::setOriginQuery,
                onDestinationQueryChange = viewModel::setDestinationQuery,
                onSelectOrigin = viewModel::selectOrigin,
                onSelectDestination = viewModel::selectDestination,
                onSearch = viewModel::searchRoute,
                onGoHome = { runWithLocationPermission(viewModel::routeHome) },
                onGoToWork = { runWithLocationPermission(viewModel::routeToWork) },
                onRandomJourney = { runWithLocationPermission(viewModel::randomJourney) },
                onPlayRadio = viewModel::playLastSelectedRadio,
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
            homeStopName = state.stops.firstOrNull { it.stopId == state.homeStopId }?.stopName,
            workStopName = state.stops.firstOrNull { it.stopId == state.workStopId }?.stopName,
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
    onOriginQueryChange: (String) -> Unit,
    onDestinationQueryChange: (String) -> Unit,
    onSelectOrigin: (StopEntity) -> Unit,
    onSelectDestination: (StopEntity) -> Unit,
    onSearch: () -> Unit,
    onGoHome: () -> Unit,
    onGoToWork: () -> Unit,
    onRandomJourney: () -> Unit,
    onPlayRadio: () -> Unit,
    onChooseRadio: () -> Unit,
    onSaveHome: () -> Unit,
    onSaveWork: () -> Unit,
    modifier: Modifier = Modifier
) {
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
                enabled = state.homeStopId != null && !state.isSearching,
                label = { Text("🏠 Do domu") }
            )
            AssistChip(
                onClick = onGoToWork,
                enabled = state.workStopId != null && !state.isSearching,
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
        AssistChip(
            onClick = onRandomJourney,
            enabled = !state.isSearching,
            label = { Text("🎲 Zaszalej") }
        )

        Text("Znajdź połączenie", style = MaterialTheme.typography.titleMedium)
        StopSelector(
            label = "Początek",
            query = state.originQuery,
            selectedStop = state.originStop,
            stops = state.stops,
            onQueryChange = onOriginQueryChange,
            onSelectStop = onSelectOrigin
        )
        StopSelector(
            label = "Cel",
            query = state.destinationQuery,
            selectedStop = state.destinationStop,
            stops = state.stops,
            onQueryChange = onDestinationQueryChange,
            onSelectStop = onSelectDestination
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = onSearch,
                enabled = !state.isSearching && state.originStop != null &&
                    state.destinationStop != null
            ) {
                Text(if (state.isSearching) "Szukam..." else "Szukaj trasy")
            }
            TextButton(
                onClick = onSaveHome,
                enabled = state.destinationStop != null
            ) {
                Text("Zapisz jako dom")
            }
            TextButton(
                onClick = onSaveWork,
                enabled = state.destinationStop != null
            ) {
                Text("Zapisz jako pracę")
            }
        }

        state.journey?.let { journey ->
            JourneyResult(
                journey = journey,
                stops = state.stops,
                origin = state.originStop,
                destination = state.destinationStop
            )
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
private fun StopSelector(
    label: String,
    query: String,
    selectedStop: StopEntity?,
    stops: List<StopEntity>,
    onQueryChange: (String) -> Unit,
    onSelectStop: (StopEntity) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            label = { Text(label) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        if (selectedStop == null && query.length >= 2) {
            stops.asSequence()
                .filter { stop ->
                    stop.stopName.contains(query, ignoreCase = true) ||
                        stop.stopCode?.contains(query, ignoreCase = true) == true
                }
                .take(MAX_STOP_SUGGESTIONS)
                .forEach { stop ->
                    Text(
                        text = "${stop.stopName}${stop.stopCode?.let { " · $it" }.orEmpty()}",
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelectStop(stop) }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
        }
    }
}

@Composable
private fun JourneyResult(
    journey: pl.wraven.mpkfastroute.core.csa.CsaJourney,
    stops: List<StopEntity>,
    origin: StopEntity?,
    destination: StopEntity?
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text("Wynik", style = MaterialTheme.typography.titleLarge)
        Text(
            "${origin?.stopName.orEmpty()} → ${destination?.stopName.orEmpty()}",
            fontWeight = FontWeight.Medium
        )
        Text(
            "Przyjazd ${formatGtfsTime(journey.arrivalTime)}" +
                if (journey.transferPenaltySeconds > 0) {
                    " · kara przesiadkowa +${journey.transferPenaltySeconds / 60} min"
                } else {
                    ""
                }
        )
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

                @Composable
                private fun LiveDeparturesSection(
                    stop: StopEntity?,
                    departures: pl.wraven.mpkfastroute.core.live.LiveDeparturesResult
                ) {
                    if (departures.departures.isEmpty()) {
                        return
                    }

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            "Odjazdy z ${stop?.stopName.orEmpty()}",
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

                is JourneyStep.Walk -> {
                    val footpath = step.footpath
                    Text(
                        "Przejście ${stopName(stops, footpath.fromStopId)} → " +
                            "${stopName(stops, footpath.toStopId)} " +
                            "(${footpath.durationSeconds / 60} min)"
                    )
                }
            }
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
    stops.firstOrNull { it.stopId == stopId }?.stopName ?: stopId

private fun formatGtfsTime(seconds: Int): String {
    val totalMinutes = seconds / 60
    return "%02d:%02d".format(totalMinutes / 60, totalMinutes % 60)
}

private const val MAX_STOP_SUGGESTIONS = 6
private const val MAX_DISPLAYED_DEPARTURES = 12
