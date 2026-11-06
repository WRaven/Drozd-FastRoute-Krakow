package pl.wraven.mpkfastroute.feature.search

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
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
            diagnosticEntries = state.diagnosticEntries,
            onRefresh = viewModel::refreshSchedule,
            onClearDiagnostics = viewModel::clearDiagnostics,
            onDiagnosticsCopied = viewModel::diagnosticsCopied,
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
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("Szybkie akcje", style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AssistChip(
                        onClick = onGoHome,
                        enabled = state.hasHomeDestination &&
                            !state.isSearching && !state.isImporting,
                        label = { Text("🏠 Do domu") }
                    )
                    AssistChip(
                        onClick = onGoToWork,
                        enabled = state.hasWorkDestination &&
                            !state.isSearching && !state.isImporting,
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
                        Text(
                            "📻 Uruchom radio · " +
                                RadioStations.all[state.lastSelectedRadioIndex].name
                        )
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
                    enabled = !state.isSearching && !state.isImporting,
                    label = {
                        Text(if (state.isSearching) "Szukam trasy..." else "🎲 Zaszalej")
                    }
                )
            }
        }
        if (state.isImporting && state.progressMessage.isNotBlank()) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Text(
                    state.progressMessage,
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
        if (state.isSearching) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
        }

        Text("Znajdź połączenie", style = MaterialTheme.typography.headlineSmall)
        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("Początek", style = MaterialTheme.typography.titleMedium)
                Button(
                    onClick = onUseCurrentLocation,
                    enabled = !state.isSearching && !state.isImporting,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("📍 Użyj mojej lokalizacji")
                }
                Text(
                    "albo wpisz adres lub nazwę przystanku",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = state.originQuery,
                    onValueChange = onOriginQueryChange,
                    label = { Text("Adres lub nazwa przystanku") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                TextButton(
                    onClick = onSearchOriginAddress,
                    enabled = !state.isSearching && !state.isImporting &&
                        state.originQuery.isNotBlank()
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
                    Text("Adres początku: ${place.label}")
                    if (!state.originStopManuallySelected) {
                        Text(
                            "Przystanek dobiorę podczas wyszukiwania trasy.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        TextButton(
                            onClick = { showOriginStopPicker = !showOriginStopPicker },
                            enabled = state.nearbyOriginGroups.isNotEmpty() &&
                                !state.isSearching && !state.isImporting
                        ) {
                            Text(
                                if (showOriginStopPicker) "Ukryj przystanki"
                                else "Wybierz przystanek"
                            )
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
                    Text(
                        "Wybrany przystanek: " +
                            StopNameGrouper.mainName(stop.stopName),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("Cel", style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(
                    value = state.destinationQuery,
                    onValueChange = onDestinationQueryChange,
                    label = { Text("Adres lub nazwa przystanku") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                TextButton(
                    onClick = onSearchAddress,
                    enabled = !state.isSearching && !state.isImporting &&
                        state.destinationQuery.isNotBlank()
                ) {
                    Text("Szukaj adresu")
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
                        Text(
                            "Przystanek dobiorę podczas wyszukiwania trasy.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        TextButton(
                            onClick = {
                                showDestinationStopPicker = !showDestinationStopPicker
                            },
                            enabled = state.nearbyDestinationGroups.isNotEmpty() &&
                                !state.isSearching && !state.isImporting
                        ) {
                            Text(
                                if (showDestinationStopPicker) "Ukryj przystanki"
                                else "Wybierz przystanek"
                            )
                        }
                        if (showDestinationStopPicker) {
                            Text("Wybierz przystanek docelowy:")
                            state.nearbyDestinationGroups.forEach { group ->
                                val distanceMeters =
                                    distanceToStopGroup(state.destinationPlace, group)
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
                    Text(
                        "Wybrany przystanek: " +
                            StopNameGrouper.mainName(stop.stopName),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onSearch,
                    enabled = !state.isSearching && !state.isImporting &&
                        (state.originPlace != null || state.originStop != null) &&
                        (state.destinationPlace != null || state.destinationStop != null),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (state.isSearching) "Szukam..." else "Szukaj trasy")
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TextButton(
                        onClick = onSaveHome,
                        enabled = state.destinationPlace != null,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Ustaw jako dom")
                    }
                    TextButton(
                        onClick = onSaveWork,
                        enabled = state.destinationPlace != null,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Ustaw jako pracę")
                    }
                }
            }
        }
        state.routeSearchMessage?.let { message ->
            Text(message, color = MaterialTheme.colorScheme.error)
        }

        if (state.journeyOptions.isNotEmpty()) {
            Text("Proponowane trasy", style = MaterialTheme.typography.headlineSmall)
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
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = MaterialTheme.shapes.small
            ) {
                Text(
                    routeLabel ?: "Wynik",
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            Text(
                "${origin?.stopName?.let(StopNameGrouper::mainName).orEmpty()} → " +
                    destination?.stopName?.let(StopNameGrouper::mainName).orEmpty(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                JourneyMetric(
                    label = "PRZYJAZD",
                    value = formatGtfsTime(journey.estimatedArrivalTime),
                    modifier = Modifier.weight(1f)
                )
                JourneyMetric(
                    label = "CZAS",
                    value = formatDuration(journey.totalDurationSeconds),
                    modifier = Modifier.weight(1f)
                )
                JourneyMetric(
                    label = "SPACER",
                    value = formatDistance(journey.totalWalkingDistanceMeters),
                    modifier = Modifier.weight(1f)
                )
            }
            if (journey.transferPenaltySeconds > 0) {
                Text(
                    "Kara za przesiadkę: +${journey.transferPenaltySeconds / 60} min",
                    color = MaterialTheme.colorScheme.tertiary,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
            }
            if (routeLabel != null) {
                Text(
                    "Dystans pieszo",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    JourneyMetric(
                        label = "NA START",
                        value = formatDistance(journey.originAccess.walkingDistanceMeters),
                        modifier = Modifier.weight(1f)
                    )
                    JourneyMetric(
                        label = "PRZESIADKI",
                        value = formatDistance(journey.transferWalkingDistanceMeters),
                        modifier = Modifier.weight(1f)
                    )
                    JourneyMetric(
                        label = "DO CELU",
                        value = formatDistance(journey.destinationAccess.walkingDistanceMeters),
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            HorizontalDivider()
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                collapseTransitSteps(journey.steps).forEach { step ->
                    when (step) {
                        is JourneyStep.Transit -> {
                            val connection = step.connection
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                shape = MaterialTheme.shapes.medium
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Surface(
                                        color = MaterialTheme.colorScheme.primaryContainer,
                                        shape = MaterialTheme.shapes.small
                                    ) {
                                        Text(
                                            "Linia ${connection.routeShortName}",
                                            modifier = Modifier.padding(
                                                horizontal = 10.dp,
                                                vertical = 8.dp
                                            ),
                                            style = MaterialTheme.typography.labelLarge,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    }
                                    Column(
                                        modifier = Modifier.weight(1f),
                                        verticalArrangement = Arrangement.spacedBy(2.dp)
                                    ) {
                                        Text(
                                            "${stopName(stops, connection.departureStopId)} · " +
                                                formatGtfsTime(connection.departureTime),
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = FontWeight.Medium
                                        )
                                        Text(
                                            "${stopName(stops, connection.arrivalStopId)} · " +
                                                formatGtfsTime(connection.arrivalTime),
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }

                        is JourneyStep.Walk -> {
                            val footpath = step.footpath
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                shape = MaterialTheme.shapes.medium
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Surface(
                                        color = MaterialTheme.colorScheme.secondaryContainer,
                                        shape = MaterialTheme.shapes.small
                                    ) {
                                        Text(
                                            "PIESZO",
                                            modifier = Modifier.padding(
                                                horizontal = 10.dp,
                                                vertical = 8.dp
                                            ),
                                            style = MaterialTheme.typography.labelLarge,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer
                                        )
                                    }
                                    Column(
                                        modifier = Modifier.weight(1f),
                                        verticalArrangement = Arrangement.spacedBy(2.dp)
                                    ) {
                                        Text(
                                            "${stopName(stops, footpath.fromStopId)} → " +
                                                stopName(stops, footpath.toStopId),
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Medium
                                        )
                                        Text(
                                            "${formatDistance(footpath.distanceMeters)} · " +
                                                formatDuration(footpath.durationSeconds),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun JourneyMetric(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.medium
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                value,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

private fun collapseTransitSteps(steps: List<JourneyStep>): List<JourneyStep> {
    val collapsed = mutableListOf<JourneyStep>()
    steps.forEach { step ->
        val current = step as? JourneyStep.Transit
        val previous = collapsed.lastOrNull() as? JourneyStep.Transit
        if (
            current != null &&
            previous != null &&
            previous.connection.tripId == current.connection.tripId &&
            previous.connection.arrivalStopId == current.connection.departureStopId
        ) {
            collapsed[collapsed.lastIndex] = JourneyStep.Transit(
                previous.connection.copy(
                    arrivalStopId = current.connection.arrivalStopId,
                    arrivalTime = current.connection.arrivalTime
                )
            )
        } else {
            collapsed += step
        }
    }
    return collapsed
}

@Composable
private fun LiveDeparturesSection(
    stop: StopEntity?,
    departures: LiveDeparturesResult
) {
    if (departures.departures.isEmpty()) {
        return
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                "Odjazdy z ${stop?.stopName?.let(StopNameGrouper::mainName).orEmpty()}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
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
}

@Composable
private fun SettingsDialog(
    progressMessage: String,
    isImporting: Boolean,
    homeStopName: String?,
    workStopName: String?,
    diagnosticEntries: List<String>,
    onRefresh: () -> Unit,
    onClearDiagnostics: () -> Unit,
    onDiagnosticsCopied: () -> Unit,
    onDismiss: () -> Unit
) {
    val clipboardManager = LocalClipboardManager.current
    val diagnosticsScrollState = rememberScrollState()
    LaunchedEffect(diagnosticEntries.lastOrNull()) {
        diagnosticsScrollState.animateScrollTo(diagnosticsScrollState.maxValue)
    }

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
                Text("Terminal diagnostyczny wyszukiwania")
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(8.dp)
                ) {
                    if (diagnosticEntries.isEmpty()) {
                        Text("Brak wpisów.")
                    } else {
                        Text(
                            text = diagnosticEntries.joinToString("\n"),
                            modifier = Modifier.verticalScroll(diagnosticsScrollState),
                            fontFamily = FontFamily.Monospace,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(
                        onClick = {
                            clipboardManager.setText(
                                AnnotatedString(diagnosticEntries.joinToString("\n"))
                            )
                            onDiagnosticsCopied()
                        },
                        enabled = diagnosticEntries.isNotEmpty()
                    ) {
                        Text("Kopiuj")
                    }
                    TextButton(
                        onClick = onClearDiagnostics,
                        enabled = diagnosticEntries.isNotEmpty()
                    ) {
                        Text("Wyczyść")
                    }
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
