package pl.wraven.mpkfastroute.feature.search

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.GradientDrawable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import java.io.File
import java.time.Instant
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapListener
import org.osmdroid.events.ScrollEvent
import org.osmdroid.events.ZoomEvent
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import pl.wraven.mpkfastroute.core.database.StopEntity
import pl.wraven.mpkfastroute.core.live.LiveTripUpdate
import pl.wraven.mpkfastroute.core.live.LiveVehiclePosition
import pl.wraven.mpkfastroute.core.live.LiveVehicleSnapshot
import pl.wraven.mpkfastroute.core.live.TransitVehicleMode
import pl.wraven.mpkfastroute.core.live.VehicleTripSchedule
import pl.wraven.mpkfastroute.core.live.KRAKOW_ZONE
import pl.wraven.mpkfastroute.core.live.StopTimetable
import pl.wraven.mpkfastroute.core.live.TimetableDayType
import pl.wraven.mpkfastroute.core.live.VehicleStopTiming
import pl.wraven.mpkfastroute.core.live.minutesUntil
import pl.wraven.mpkfastroute.core.live.timetableHours
import pl.wraven.mpkfastroute.core.live.upcomingVehicleStopIndex
import pl.wraven.mpkfastroute.core.live.vehicleStopTimings
import pl.wraven.mpkfastroute.core.live.isLiveTripUpdateFresh
import pl.wraven.mpkfastroute.core.location.GeoPlace

@Composable
internal fun LiveVehicleMapDialog(
    snapshot: LiveVehicleSnapshot?,
    isLoading: Boolean,
    error: String?,
    userLocation: GeoPlace?,
    userLocationError: String?,
    stops: List<StopEntity>,
    selectedStop: StopEntity?,
    stopTimetable: StopTimetable?,
    isLoadingStopTimetable: Boolean,
    stopTimetableError: String?,
    selectedVehicle: LiveVehiclePosition?,
    selectedVehicleTrip: VehicleTripSchedule?,
    selectedVehicleTripUpdate: LiveTripUpdate?,
    isLoadingVehicleTrip: Boolean,
    isLoadingVehicleTripUpdate: Boolean,
    vehicleTripError: String?,
    vehicleTripUpdateError: String?,
    onRefresh: () -> Unit,
    onLocateUser: () -> Unit,
    onSelectStop: (StopEntity) -> Unit,
    onSelectVehicle: (LiveVehiclePosition) -> Unit,
    onClearSelectedStop: () -> Unit,
    onClearSelectedVehicle: () -> Unit,
    onDismiss: () -> Unit
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val latestOnRefresh by rememberUpdatedState(onRefresh)
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (isActive) {
                latestOnRefresh()
                delay(VEHICLE_REFRESH_INTERVAL_MILLIS)
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.94f)
                .padding(horizontal = 12.dp, vertical = 16.dp),
            shape = RoundedCornerShape(24.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Autobusy i tramwaje na żywo",
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (isLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp))
                    }
                    TextButton(onClick = {
                        onRefresh()
                        selectedStop?.let(onSelectStop)
                    }, enabled = !isLoading) {
                        Text("Odśwież")
                    }
                    TextButton(onClick = onDismiss) {
                        Text("Zamknij")
                    }
                }

                Text(
                    snapshot?.let {
                        val buses = it.vehicles.count { vehicle ->
                            vehicle.mode == TransitVehicleMode.BUS
                        }
                        val trams = it.vehicles.count { vehicle ->
                            vehicle.mode == TransitVehicleMode.TRAM
                        }
                        "Autobusy: $buses · Tramwaje: $trams · " +
                            "aktualizacja ${it.fetchedAt.format(UPDATE_TIME_FORMATTER)}"
                    } ?: if (isLoading) {
                        "Pobieranie pozycji z ZTP…"
                    } else {
                        "Oczekiwanie na pozycje pojazdów…"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                val mappedStops = remember(stops) { stops.filter(::hasValidCoordinates) }
                Text(
                    "${mappedStops.size} przystanków · Dotknij zielonego znacznika po odjazdy " +
                        "lub pojazdu po trasę i rozkład.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onLocateUser) {
                        Text("📍 Moja lokalizacja")
                    }
                }

                error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error)
                }
                snapshot?.routeLabelsWarning?.let {
                    Text(it, color = MaterialTheme.colorScheme.error)
                }
                userLocationError?.let {
                    Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                snapshot?.unavailableModes?.takeIf { it.isNotEmpty() }?.let {
                    Text(
                        "Częściowe dane — brak pozycji ${it.joinToString(" i ") { mode ->
                            mode.pluralLabel
                        }}.",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                if (snapshot?.vehicles?.isEmpty() == true && error == null) {
                    Text(
                        "W ostatnim odczycie nie znaleziono aktywnych pojazdów.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                LiveVehicleMap(
                    snapshot = snapshot,
                    userLocation = userLocation,
                    stops = mappedStops,
                    selectedStop = selectedStop,
                    selectedVehicle = selectedVehicle,
                    selectedVehicleTrip = selectedVehicleTrip,
                    onSelectStop = onSelectStop,
                    onSelectVehicle = onSelectVehicle,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                )

                selectedVehicle?.let {
                    SelectedVehicleTripPanel(
                        vehicle = it,
                        schedule = selectedVehicleTrip,
                        liveUpdate = selectedVehicleTripUpdate,
                        isLoadingSchedule = isLoadingVehicleTrip,
                        isLoadingLiveUpdate = isLoadingVehicleTripUpdate,
                        scheduleError = vehicleTripError,
                        liveUpdateError = vehicleTripUpdateError,
                        onClear = onClearSelectedVehicle
                    )
                } ?: selectedStop?.let {
                    SelectedStopTimetable(
                        stop = it,
                        timetable = stopTimetable,
                        isLoading = isLoadingStopTimetable,
                        error = stopTimetableError,
                        onClear = onClearSelectedStop
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    VehicleLegend(
                        color = Color(BUS_MARKER_COLOR),
                        label = "Autobus"
                    )
                    VehicleLegend(
                        color = Color(TRAM_MARKER_COLOR),
                        label = "Tramwaj"
                    )
                    VehicleLegend(
                        color = Color(STOP_MARKER_COLOR),
                        label = "Przystanek"
                    )
                }
                Text(
                    "Pozycje: ZTP Kraków · mapa: © OpenStreetMap contributors",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun LiveVehicleMap(
    snapshot: LiveVehicleSnapshot?,
    userLocation: GeoPlace?,
    stops: List<StopEntity>,
    selectedStop: StopEntity?,
    selectedVehicle: LiveVehiclePosition?,
    selectedVehicleTrip: VehicleTripSchedule?,
    onSelectStop: (StopEntity) -> Unit,
    onSelectVehicle: (LiveVehiclePosition) -> Unit,
    modifier: Modifier
) {
    val context = LocalContext.current
    val mapView = remember(context) { createMapView(context) }
    val stopMarkers = remember(mapView) { mutableListOf<Pair<Marker, Boolean>>() }
    var centeredOnUserLocation by remember(mapView) { mutableStateOf<GeoPlace?>(null) }
    var fittedTripId by remember(mapView) { mutableStateOf<String?>(null) }
    var previousSelectedVehicleId by remember(mapView) { mutableStateOf<String?>(null) }
    val mapShape = RoundedCornerShape(16.dp)

    AndroidView(
        factory = { mapView },
        modifier = modifier
            .clip(mapShape)
            .background(MaterialTheme.colorScheme.surfaceVariant, mapShape),
        update = { view ->
            view.overlays.clear()
            stopMarkers.clear()
            val vehicles = snapshot?.vehicles.orEmpty()
            val routePoints = selectedVehicleTrip?.let(::tripRoutePoints).orEmpty()
            if (selectedVehicle?.id != previousSelectedVehicleId) {
                previousSelectedVehicleId = selectedVehicle?.id
                fittedTripId = null
                if (selectedVehicle != null && selectedVehicleTrip == null) {
                    view.controller.setCenter(
                        GeoPoint(selectedVehicle.latitude, selectedVehicle.longitude)
                    )
                    view.controller.setZoom(SELECTED_VEHICLE_MAP_ZOOM)
                }
            }
            if (selectedVehicleTrip == null) {
                fittedTripId = null
            } else if (fittedTripId != selectedVehicleTrip.tripId) {
                if (routePoints.size >= 2) {
                    val padding = (ROUTE_MAP_PADDING_DP * context.resources.displayMetrics.density)
                        .toInt()
                    view.zoomToBoundingBox(
                        BoundingBox.fromGeoPoints(routePoints),
                        true,
                        padding
                    )
                } else {
                    selectedVehicle?.let {
                        view.controller.setCenter(GeoPoint(it.latitude, it.longitude))
                        view.controller.setZoom(SELECTED_VEHICLE_MAP_ZOOM)
                    }
                }
                fittedTripId = selectedVehicleTrip.tripId
            }
            if (selectedVehicle != null && routePoints.size >= 2) {
                view.overlays.add(
                    Polyline(view).apply {
                        setPoints(routePoints)
                        setColor(markerColor(selectedVehicle.mode))
                        setWidth(ROUTE_LINE_WIDTH_DP * context.resources.displayMetrics.density)
                    }
                )
            }
            stops.forEach { stop ->
                val isSelected = stop.stopId == selectedStop?.stopId
                val marker = Marker(view).apply {
                    position = GeoPoint(stop.stopLat, stop.stopLon)
                    title = stop.stopName
                    snippet = "Dotknij, aby wybrać linię i rozkład godzinowy"
                    icon = createStopMarkerIcon(context, isSelected, view.zoomLevelDouble)
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                    setOnMarkerClickListener { clickedMarker, _ ->
                        clickedMarker.showInfoWindow()
                        onSelectStop(stop)
                        true
                    }
                }
                stopMarkers.add(marker to isSelected)
                view.overlays.add(marker)
            }
            vehicles.forEach { vehicle ->
                val marker = Marker(view).apply {
                    position = GeoPoint(vehicle.latitude, vehicle.longitude)
                    title = vehicle.routeShortName?.let { "${vehicle.mode.label} $it" }
                        ?: vehicle.mode.label
                    snippet = listOfNotNull(
                        vehicle.vehicleLabel?.let { "Pojazd $it" },
                        "Dotknij, aby zobaczyć trasę i rozkład"
                    ).joinToString(" · ")
                    val isSelected = vehicle.id == selectedVehicle?.id
                    icon = createVehicleMarkerIcon(context, vehicle, isSelected)
                    setAnchor(vehicleMarkerAnchorX(isSelected), Marker.ANCHOR_CENTER)
                    setOnMarkerClickListener { clickedMarker, _ ->
                        clickedMarker.showInfoWindow()
                        onSelectVehicle(vehicle)
                        true
                    }
                }
                view.overlays.add(marker)
            }
            userLocation?.let { location ->
                view.overlays.add(
                    Marker(view).apply {
                        position = GeoPoint(location.latitude, location.longitude)
                        title = location.label
                        icon = createUserLocationMarkerIcon(context)
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                    }
                )
            }
            if (userLocation == null) {
                centeredOnUserLocation = null
            } else if (userLocation != centeredOnUserLocation) {
                view.controller.setCenter(GeoPoint(userLocation.latitude, userLocation.longitude))
                view.controller.setZoom(USER_LOCATION_MAP_ZOOM)
                centeredOnUserLocation = userLocation
            }
            updateStopMarkerIcons(context, stopMarkers, view.zoomLevelDouble)
            view.invalidate()
        }
    )

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(mapView, lifecycleOwner) {
        val mapListener = object : MapListener {
            override fun onScroll(event: ScrollEvent): Boolean = false

            override fun onZoom(event: ZoomEvent): Boolean {
                updateStopMarkerIcons(context, stopMarkers, event.zoomLevel)
                mapView.invalidate()
                return false
            }
        }
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                else -> Unit
            }
        }
        mapView.addMapListener(mapListener)
        lifecycleOwner.lifecycle.addObserver(observer)
        mapView.onResume()
        onDispose {
            mapView.removeMapListener(mapListener)
            stopMarkers.clear()
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapView.onPause()
            mapView.onDetach()
        }
    }
}

@Composable
private fun VehicleLegend(color: Color, label: String) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(12.dp)
                .background(color, CircleShape)
        )
        Text(label, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun SelectedStopTimetable(
    stop: StopEntity,
    timetable: StopTimetable?,
    isLoading: Boolean,
    error: String?,
    onClear: () -> Unit
) {
    var selectedLine by remember(stop.stopId) { mutableStateOf(timetable?.lines?.firstOrNull()) }
    var dayType by remember(stop.stopId) { mutableStateOf(TimetableDayType.WORKDAY) }
    val line = selectedLine?.takeIf { it in timetable?.lines.orEmpty() }
        ?: timetable?.lines?.firstOrNull()
    val day = timetable?.days?.get(dayType)
    val hours = remember(day, line) { timetableHours(day, line) }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Rozkład przystanku",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        stop.stopName,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1
                    )
                }
                TextButton(onClick = onClear) {
                    Text("Wyczyść")
                }
            }
            when {
                isLoading -> Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp))
                    Text("Odczytywanie rozkładu…", style = MaterialTheme.typography.bodySmall)
                }
                error != null -> Text(
                    error,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
                timetable == null -> Text(
                    "Wybierz przystanek na mapie, aby sprawdzić rozkład.",
                    style = MaterialTheme.typography.bodySmall
                )
                timetable.lines.isEmpty() -> Text(
                    "Brak kursów z tego stanowiska w dostępnym rozkładzie GTFS.",
                    style = MaterialTheme.typography.bodySmall
                )
                else -> {
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        timetable.lines.forEach { option ->
                            FilterChip(
                                selected = line == option,
                                onClick = { selectedLine = option },
                                label = { Text("${option.routeShortName} → ${option.direction}") }
                            )
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        TimetableDayType.entries.forEach { type ->
                            FilterChip(selected = dayType == type, onClick = { dayType = type },
                                label = { Text(type.label) })
                        }
                    }
                    line?.let {
                        Text("Linia ${it.routeShortName} → ${it.direction}",
                            style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                    }
                    day?.date?.let {
                        Text(
                            "Wzorzec dla ${it.format(DateTimeFormatter.ofPattern("dd.MM.yyyy"))} · ROZKŁAD",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (hours.isEmpty()) {
                        Text("Brak odjazdów tej linii w wybranym typie dnia.",
                            style = MaterialTheme.typography.bodySmall)
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxWidth().heightIn(max = 200.dp)
                                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(4.dp))
                        ) {
                            item {
                                Row(modifier = Modifier.fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.surfaceVariant).padding(6.dp)) {
                                    Text("Godzina", modifier = Modifier.weight(0.25f),
                                        style = MaterialTheme.typography.labelMedium)
                                    Text("Minuty", modifier = Modifier.weight(0.75f),
                                        style = MaterialTheme.typography.labelMedium)
                                }
                            }
                            items(hours, key = { it.hour }) { hour ->
                                Row(modifier = Modifier.fillMaxWidth().padding(6.dp)) {
                                    Text("%02d".format(hour.hour % 24) +
                                        if (hour.hour >= 24) " (+${hour.hour / 24})" else "",
                                        modifier = Modifier.weight(0.25f), fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.bodySmall)
                                    Text(hour.minutes.joinToString("  ") { "%02d".format(it) },
                                        modifier = Modifier.weight(0.75f),
                                        style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                    Text("Pełna doba; (+1) = następny dzień. Warianty i święta mogą różnić się od wzorca.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun SelectedVehicleTripPanel(
    vehicle: LiveVehiclePosition,
    schedule: VehicleTripSchedule?,
    liveUpdate: LiveTripUpdate?,
    isLoadingSchedule: Boolean,
    isLoadingLiveUpdate: Boolean,
    scheduleError: String?,
    liveUpdateError: String?,
    onClear: () -> Unit
) {
    val routeName = vehicle.routeShortName
        ?: schedule?.routeShortName
        ?: "?"
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "${vehicle.mode.label} $routeName · rozkład kursu",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    schedule?.direction?.takeIf(String::isNotBlank)?.let {
                        Text(
                            "Kierunek: $it",
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                TextButton(onClick = onClear) {
                    Text("Wyczyść")
                }
            }

            vehicle.vehicleLabel?.let {
                Text("Pojazd $it", style = MaterialTheme.typography.bodySmall)
            }
            if (isLoadingSchedule) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp))
                    Text("Pobieranie trasy i rozkładu…", style = MaterialTheme.typography.bodySmall)
                }
            }
            scheduleError?.let {
                Text(
                    it,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            schedule?.let { trip ->
                var now by remember(trip.tripId) { mutableStateOf(Instant.now()) }
                LaunchedEffect(trip.tripId) {
                    while (isActive) {
                        now = Instant.now()
                        delay(1_000L)
                    }
                }
                val currentUpdate = liveUpdate?.takeIf {
                    isLiveTripUpdateFresh(it, now) &&
                        (it.serviceDate == null || it.serviceDate == trip.serviceDate)
                }
                val timings = remember(vehicle, trip, currentUpdate) {
                    vehicleStopTimings(vehicle, trip, currentUpdate)
                }
                val listState = rememberLazyListState()
                LaunchedEffect(trip.tripId) {
                    listState.scrollToItem(upcomingVehicleStopIndex(vehicle, trip))
                }
                Text("${trip.stops.size} przystanków · cały kurs · " +
                    trip.serviceDate.format(DateTimeFormatter.ofPattern("dd.MM.yyyy")),
                    style = MaterialTheme.typography.labelSmall)
                if (trip.shapePoints.size < 2) {
                    Text(
                        "Brak geometrii GTFS — pokazano przybliżony przebieg po przystankach.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                when {
                    isLoadingLiveUpdate -> Text(
                        "Pobieranie opóźnień na żywo…",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    currentUpdate?.isCanceled == true -> Text(
                        "ZTP oznaczył ten kurs jako odwołany.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                    currentUpdate != null && timings.any { it.predictedArrival != null } -> Text(
                        "Prognoza LIVE · aktualizacja " +
                            (currentUpdate.sourceTimestamp?.atZone(KRAKOW_ZONE)?.format(UPDATE_TIME_FORMATTER)
                                ?: currentUpdate.fetchedAt.format(UPDATE_TIME_FORMATTER)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    liveUpdateError != null -> Text(
                        liveUpdateError,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    else -> Text(
                        "Brak prognozy LIVE; wyświetlam rozkład.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (liveUpdate != null && liveUpdateError != null) {
                    Text(
                        liveUpdateError,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = MAX_TRIP_STOPS_PANEL_HEIGHT_DP.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(timings, key = { it.scheduledStop.stopSequence }) { timing ->
                        VehicleTripStopRow(timing, now, currentUpdate?.isCanceled == true)
                    }
                }
            }
        }
    }
}

@Composable
private fun VehicleTripStopRow(
    timing: VehicleStopTiming,
    now: Instant,
    isCanceled: Boolean
) {
    val target = timing.predictedArrival ?: timing.scheduledArrival
    val remaining = minutesUntil(target, now)
    val timeLabel = if (timing.predictedArrival == null) {
        formatGtfsTime(timing.scheduledStop.arrivalTime)
    } else {
        target.atZone(KRAKOW_ZONE).format(DateTimeFormatter.ofPattern("HH:mm"))
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                timing.scheduledStop.stop.stopName + if (timing.isCurrent) " · pojazd" else "",
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = if (timing.isCurrent) FontWeight.Bold else FontWeight.Normal,
                color = if (timing.isPassed) MaterialTheme.colorScheme.onSurfaceVariant
                    else MaterialTheme.colorScheme.onSurface
            )
            Text(
                "$timeLabel · " + if (timing.predictedArrival != null) "LIVE" else "ROZKŁAD",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            when {
                isCanceled -> "odwołany"
                timing.isSkipped -> "pomija"
                timing.isPassed -> "minął"
                remaining > 0 -> "za $remaining min"
                remaining == 0L -> "teraz"
                else -> "${-remaining} min temu"
            },
            color = if (timing.isCurrent) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.bodySmall
        )
    }
}

private fun tripRoutePoints(schedule: VehicleTripSchedule): List<GeoPoint> =
    if (schedule.shapePoints.size >= 2) {
        schedule.shapePoints.map { GeoPoint(it.shapeLat, it.shapeLon) }
    } else {
        schedule.stops.map { GeoPoint(it.stop.stopLat, it.stop.stopLon) }
    }

private fun createMapView(context: Context): MapView {
    val basePath = File(context.cacheDir, "osmdroid")
    val tileCache = File(basePath, "tiles")
    basePath.mkdirs()
    tileCache.mkdirs()
    Configuration.getInstance().apply {
        userAgentValue = context.packageName
        osmdroidBasePath = basePath
        osmdroidTileCache = tileCache
    }
    return MapView(context).apply {
        setTileSource(TileSourceFactory.MAPNIK)
        setMultiTouchControls(true)
        setBuiltInZoomControls(true)
        controller.setZoom(DEFAULT_MAP_ZOOM)
        controller.setCenter(GeoPoint(KRAKOW_LATITUDE, KRAKOW_LONGITUDE))
    }
}

private fun createVehicleMarkerIcon(
    context: Context,
    vehicle: LiveVehiclePosition,
    selected: Boolean
): BitmapDrawable {
    val density = context.resources.displayMetrics.density
    val markerSize = vehicleMarkerCircleSizeDp(selected) * density
    val arrowSize = VEHICLE_MARKER_ARROW_SIZE_DP * density
    val gap = VEHICLE_MARKER_ARROW_GAP_DP * density
    val width = (markerSize + arrowSize + gap).roundToInt().coerceAtLeast(1)
    val height = markerSize.roundToInt().coerceAtLeast(1)
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val markerCenterX = markerSize / 2
    val centerY = height / 2f
    val markerRadius = markerSize / 2
    val markerStrokeWidth = (if (selected) 3f else 2f) * density

    canvas.drawCircle(
        markerCenterX,
        centerY,
        markerRadius - markerStrokeWidth / 2,
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = markerColor(vehicle.mode)
            style = Paint.Style.FILL
        }
    )
    canvas.drawCircle(
        markerCenterX,
        centerY,
        markerRadius - markerStrokeWidth / 2,
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (selected) SELECTED_MARKER_COLOR else AndroidColor.WHITE
            style = Paint.Style.STROKE
            strokeWidth = markerStrokeWidth
        }
    )

    val label = vehicle.routeShortName?.takeIf(String::isNotBlank) ?: "?"
    val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = AndroidColor.WHITE
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textSize = VEHICLE_MARKER_LABEL_TEXT_SIZE_DP * density
    }
    val availableLabelWidth = markerSize - 2 * VEHICLE_MARKER_LABEL_PADDING_DP * density
    val measuredLabelWidth = labelPaint.measureText(label)
    if (measuredLabelWidth > availableLabelWidth) {
        labelPaint.textSize *= availableLabelWidth / measuredLabelWidth
    }
    val labelBaseline = centerY - (labelPaint.ascent() + labelPaint.descent()) / 2
    canvas.drawText(label, markerCenterX, labelBaseline, labelPaint)

    val arrowCenterX = markerSize + gap + arrowSize / 2
    val arrowRadius = arrowSize / 2
    canvas.drawCircle(
        arrowCenterX,
        centerY,
        arrowRadius,
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = AndroidColor.WHITE
            style = Paint.Style.FILL
        }
    )
    canvas.drawCircle(
        arrowCenterX,
        centerY,
        arrowRadius,
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = DIRECTION_MARKER_STROKE_COLOR
            style = Paint.Style.STROKE
            strokeWidth = density
        }
    )

    val bearing = vehicle.bearing
    if (bearing == null) {
        val unknownDirectionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = DIRECTION_MARKER_STROKE_COLOR
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textSize = VEHICLE_MARKER_UNKNOWN_TEXT_SIZE_DP * density
        }
        val unknownDirectionBaseline =
            centerY - (unknownDirectionPaint.ascent() + unknownDirectionPaint.descent()) / 2
        canvas.drawText("?", arrowCenterX, unknownDirectionBaseline, unknownDirectionPaint)
    } else {
        val arrowPath = Path().apply {
            moveTo(arrowCenterX, centerY - arrowRadius * 0.68f)
            lineTo(arrowCenterX + arrowRadius * 0.5f, centerY - arrowRadius * 0.05f)
            lineTo(arrowCenterX + arrowRadius * 0.17f, centerY - arrowRadius * 0.05f)
            lineTo(arrowCenterX + arrowRadius * 0.17f, centerY + arrowRadius * 0.5f)
            lineTo(arrowCenterX - arrowRadius * 0.17f, centerY + arrowRadius * 0.5f)
            lineTo(arrowCenterX - arrowRadius * 0.17f, centerY - arrowRadius * 0.05f)
            lineTo(arrowCenterX - arrowRadius * 0.5f, centerY - arrowRadius * 0.05f)
            close()
        }
        canvas.save()
        canvas.rotate(bearing, arrowCenterX, centerY)
        canvas.drawPath(
            arrowPath,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = DIRECTION_MARKER_STROKE_COLOR
                style = Paint.Style.FILL
            }
        )
        canvas.restore()
    }

    return BitmapDrawable(context.resources, bitmap)
}

private fun vehicleMarkerAnchorX(selected: Boolean): Float {
    val markerSize = vehicleMarkerCircleSizeDp(selected)
    val totalWidth = markerSize + VEHICLE_MARKER_ARROW_GAP_DP + VEHICLE_MARKER_ARROW_SIZE_DP
    return markerSize / 2 / totalWidth
}

private fun vehicleMarkerCircleSizeDp(selected: Boolean): Float =
    if (selected) VEHICLE_MARKER_SELECTED_SIZE_DP else VEHICLE_MARKER_SIZE_DP

private fun markerColor(mode: TransitVehicleMode): Int = when (mode) {
    TransitVehicleMode.BUS -> BUS_MARKER_COLOR
    TransitVehicleMode.TRAM -> TRAM_MARKER_COLOR
}

internal fun stopMarkerSizeDp(zoomLevel: Double, selected: Boolean): Float {
    require(zoomLevel.isFinite()) { "Map zoom must be finite." }
    if (selected) return STOP_MARKER_SELECTED_SIZE_DP
    val progress = ((zoomLevel - STOP_MARKER_SMALL_ZOOM) /
        (STOP_MARKER_FULL_ZOOM - STOP_MARKER_SMALL_ZOOM)).coerceIn(0.0, 1.0).toFloat()
    return STOP_MARKER_SMALL_SIZE_DP +
        (STOP_MARKER_FULL_SIZE_DP - STOP_MARKER_SMALL_SIZE_DP) * progress
}

private fun updateStopMarkerIcons(
    context: Context,
    markers: List<Pair<Marker, Boolean>>,
    zoomLevel: Double
) {
    val density = context.resources.displayMetrics.density
    markers.forEach { (marker, selected) ->
        val sizePixels = (stopMarkerSizeDp(zoomLevel, selected) * density)
            .roundToInt().coerceAtLeast(1)
        if (marker.icon.intrinsicWidth != sizePixels) {
            marker.icon = createStopMarkerIcon(context, selected, zoomLevel)
        }
    }
}

private fun createStopMarkerIcon(
    context: Context,
    selected: Boolean,
    zoomLevel: Double
): GradientDrawable {
    val density = context.resources.displayMetrics.density
    val sizeDp = stopMarkerSizeDp(zoomLevel, selected)
    val sizePixels = (sizeDp * density).roundToInt().coerceAtLeast(1)
    val strokeScale = (sizeDp / STOP_MARKER_FULL_SIZE_DP).coerceAtMost(1f)
    return GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(if (selected) SELECTED_MARKER_COLOR else STOP_MARKER_COLOR)
        setStroke((2 * density * strokeScale).roundToInt().coerceAtLeast(1), AndroidColor.WHITE)
        setSize(sizePixels, sizePixels)
    }
}

private fun createUserLocationMarkerIcon(context: Context): GradientDrawable {
    val density = context.resources.displayMetrics.density
    return GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(USER_LOCATION_MARKER_COLOR)
        setStroke((3 * density).toInt().coerceAtLeast(1), AndroidColor.WHITE)
        val sizeDp = 20
        setSize((sizeDp * density).toInt(), (sizeDp * density).toInt())
    }
}

private fun hasValidCoordinates(stop: StopEntity): Boolean =
    stop.stopLat.isFinite() &&
        stop.stopLon.isFinite() &&
        stop.stopLat in -90.0..90.0 &&
        stop.stopLon in -180.0..180.0 &&
        (stop.stopLat != 0.0 || stop.stopLon != 0.0)

private const val VEHICLE_REFRESH_INTERVAL_MILLIS = 30_000L
private const val MAX_TRIP_STOPS_PANEL_HEIGHT_DP = 240
private const val DEFAULT_MAP_ZOOM = 12.0
private const val USER_LOCATION_MAP_ZOOM = 14.5
private const val SELECTED_VEHICLE_MAP_ZOOM = 14.5
private const val STOP_MARKER_SMALL_ZOOM = 13.0
private const val STOP_MARKER_FULL_ZOOM = 15.0
private const val STOP_MARKER_SMALL_SIZE_DP = 5f
private const val STOP_MARKER_FULL_SIZE_DP = 14f
private const val STOP_MARKER_SELECTED_SIZE_DP = 18f
private const val ROUTE_MAP_PADDING_DP = 48
private const val ROUTE_LINE_WIDTH_DP = 5f
private const val VEHICLE_MARKER_SIZE_DP = 30f
private const val VEHICLE_MARKER_SELECTED_SIZE_DP = 34f
private const val VEHICLE_MARKER_ARROW_SIZE_DP = 18f
private const val VEHICLE_MARKER_ARROW_GAP_DP = 1.5f
private const val VEHICLE_MARKER_LABEL_TEXT_SIZE_DP = 13f
private const val VEHICLE_MARKER_LABEL_PADDING_DP = 3.5f
private const val VEHICLE_MARKER_UNKNOWN_TEXT_SIZE_DP = 11f
private const val KRAKOW_LATITUDE = 50.0647
private const val KRAKOW_LONGITUDE = 19.9450
private const val BUS_MARKER_COLOR = 0xFF1976D2.toInt()
private const val TRAM_MARKER_COLOR = 0xFFD32F2F.toInt()
private const val STOP_MARKER_COLOR = 0xFF388E3C.toInt()
private const val SELECTED_MARKER_COLOR = 0xFFFFA000.toInt()
private const val USER_LOCATION_MARKER_COLOR = 0xFF1565C0.toInt()
private const val DIRECTION_MARKER_STROKE_COLOR = 0xFF263238.toInt()
private val UPDATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss")
