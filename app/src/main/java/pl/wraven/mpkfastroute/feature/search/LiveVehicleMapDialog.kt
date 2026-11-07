package pl.wraven.mpkfastroute.feature.search

import android.content.Context
import android.graphics.Color as AndroidColor
import android.graphics.drawable.GradientDrawable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import java.io.File
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import pl.wraven.mpkfastroute.core.live.LiveVehicleSnapshot
import pl.wraven.mpkfastroute.core.live.TransitVehicleMode

@Composable
internal fun LiveVehicleMapDialog(
    snapshot: LiveVehicleSnapshot?,
    isLoading: Boolean,
    error: String?,
    onRefresh: () -> Unit,
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
                    TextButton(onClick = onRefresh, enabled = !isLoading) {
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

                error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error)
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
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                )

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
    modifier: Modifier
) {
    val context = LocalContext.current
    val mapView = remember(context) { createMapView(context) }
    var centeredOnVehicles by remember(mapView) { mutableStateOf(false) }

    AndroidView(
        factory = { mapView },
        modifier = modifier.background(
            MaterialTheme.colorScheme.surfaceVariant,
            RoundedCornerShape(16.dp)
        ),
        update = { view ->
            view.overlays.clear()
            val vehicles = snapshot?.vehicles.orEmpty()
            vehicles.forEach { vehicle ->
                val marker = Marker(view).apply {
                    position = GeoPoint(vehicle.latitude, vehicle.longitude)
                    title = vehicle.routeShortName?.let { "${vehicle.mode.label} $it" }
                        ?: vehicle.mode.label
                    snippet = vehicle.vehicleLabel?.let { "Pojazd $it" }
                    icon = createVehicleMarkerIcon(context, vehicle.mode)
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                }
                view.overlays.add(marker)
            }
            if (!centeredOnVehicles && vehicles.isNotEmpty()) {
                view.controller.setCenter(
                    GeoPoint(
                        vehicles.map { it.latitude }.average(),
                        vehicles.map { it.longitude }.average()
                    )
                )
                centeredOnVehicles = true
            }
            view.invalidate()
        }
    )

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(mapView, lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        mapView.onResume()
        onDispose {
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
        controller.setZoom(DEFAULT_MAP_ZOOM)
        controller.setCenter(GeoPoint(KRAKOW_LATITUDE, KRAKOW_LONGITUDE))
    }
}

private fun createVehicleMarkerIcon(
    context: Context,
    mode: TransitVehicleMode
): GradientDrawable {
    val density = context.resources.displayMetrics.density
    val markerColor = when (mode) {
        TransitVehicleMode.BUS -> BUS_MARKER_COLOR
        TransitVehicleMode.TRAM -> TRAM_MARKER_COLOR
    }
    return GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(markerColor)
        setStroke((2 * density).toInt().coerceAtLeast(1), AndroidColor.WHITE)
        setSize((22 * density).toInt(), (22 * density).toInt())
    }
}

private const val VEHICLE_REFRESH_INTERVAL_MILLIS = 30_000L
private const val DEFAULT_MAP_ZOOM = 12.0
private const val KRAKOW_LATITUDE = 50.0647
private const val KRAKOW_LONGITUDE = 19.9450
private const val BUS_MARKER_COLOR = 0xFF1976D2.toInt()
private const val TRAM_MARKER_COLOR = 0xFFD32F2F.toInt()
private val UPDATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss")
