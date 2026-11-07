package pl.wraven.mpkfastroute.core.live

import com.google.transit.realtime.GtfsRealtime
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.request.get
import io.ktor.http.isSuccess
import java.io.IOException
import java.time.LocalDateTime
import javax.inject.Inject
import pl.wraven.mpkfastroute.core.database.AppDatabase

enum class TransitVehicleMode(
    val label: String,
    val pluralLabel: String,
    val feedSuffix: String
) {
    BUS("Autobus", "autobusów", "A"),
    TRAM("Tramwaj", "tramwajów", "T")
}

data class LiveVehiclePosition(
    val id: String,
    val mode: TransitVehicleMode,
    val latitude: Double,
    val longitude: Double,
    val bearing: Float? = null,
    val routeShortName: String?,
    val vehicleLabel: String?,
    val routeId: String? = null,
    val tripId: String? = null,
    val currentStopSequence: Int? = null,
    val currentStopId: String? = null
)

data class LiveVehicleSnapshot(
    val vehicles: List<LiveVehiclePosition>,
    val fetchedAt: LocalDateTime,
    val unavailableModes: Set<TransitVehicleMode>,
    val routeLabelsWarning: String? = null
)

class ZtpVehiclePositionsRepository @Inject constructor(
    private val httpClient: HttpClient,
    private val database: AppDatabase,
    private val tramRoutesRepository: ZtpTramRoutesRepository
) {
    suspend fun getSnapshot(): LiveVehicleSnapshot {
        val routeShortNames = database.routeDao().getAll()
            .associate { it.routeId to it.routeShortName }
        val vehicles = mutableListOf<LiveVehiclePosition>()
        val unavailableModes = linkedSetOf<TransitVehicleMode>()
        val failures = mutableListOf<IOException>()

        TransitVehicleMode.entries.forEach { mode ->
            try {
                vehicles += fetchPositions(mode, routeShortNames)
            } catch (exception: HttpRequestTimeoutException) {
                unavailableModes += mode
                failures += IOException(
                    "Przekroczono czas oczekiwania na pozycje ${mode.pluralLabel} z ZTP.",
                    exception
                )
            } catch (exception: IOException) {
                unavailableModes += mode
                failures += IOException(
                    "Nie udało się pobrać pozycji ${mode.pluralLabel} z ZTP: " +
                        exception.message.orEmpty(),
                    exception
                )
            }
        }

        if (unavailableModes.size == TransitVehicleMode.entries.size) {
            val exception = IOException("Nie udało się pobrać pozycji autobusów ani tramwajów.")
            failures.forEach(exception::addSuppressed)
            throw exception
        }

        val tripIdsWithoutRouteName = vehicles
            .filter { it.routeShortName.isNullOrBlank() }
            .mapNotNull { it.tripId?.takeIf(String::isNotBlank) }
            .distinct()
        val routeShortNamesByTripId = if (tripIdsWithoutRouteName.isEmpty()) {
            emptyMap()
        } else {
            database.tripDao()
                .getRouteShortNamesByTripIds(tripIdsWithoutRouteName)
                .associate { it.tripId to it.routeShortName }
        }

        var resolvedVehicles = resolveMissingVehicleRouteShortNames(vehicles, routeShortNamesByTripId)
        var routeLabelsWarning: String? = null
        if (resolvedVehicles.any {
                it.mode == TransitVehicleMode.TRAM && it.routeShortName.isNullOrBlank()
            }
        ) {
            try {
                val tramRoutes = tramRoutesRepository.getRouteShortNamesByTripId()
                resolvedVehicles = resolvedVehicles.map { vehicle ->
                    if (vehicle.mode == TransitVehicleMode.TRAM) {
                        resolveVehicleRouteShortName(vehicle, tramRoutes)
                    } else {
                        vehicle
                    }
                }
            } catch (exception: IOException) {
                routeLabelsWarning = "Nie udało się pobrać numerów linii tramwajowych: " +
                    exception.message.orEmpty()
            }
        }
        val unresolvedCount = resolvedVehicles.count { it.routeShortName.isNullOrBlank() }
        if (unresolvedCount > 0 && routeLabelsWarning == null) {
            routeLabelsWarning = "Brak numeru linii dla $unresolvedCount pojazdów w danych ZTP."
        }

        return LiveVehicleSnapshot(
            vehicles = resolvedVehicles,
            fetchedAt = LocalDateTime.now(),
            unavailableModes = unavailableModes,
            routeLabelsWarning = routeLabelsWarning
        )
    }

    private suspend fun fetchPositions(
        mode: TransitVehicleMode,
        routeShortNames: Map<String, String>
    ): List<LiveVehiclePosition> {
        val response = httpClient.get("$VEHICLE_POSITIONS_URL${mode.feedSuffix}.pb")
        if (!response.status.isSuccess()) {
            throw IOException(
                "ZTP zwrócił HTTP ${response.status.value} dla pozycji ${mode.pluralLabel}."
            )
        }
        val feed = GtfsRealtime.FeedMessage.parseFrom(response.body<ByteArray>())
        return mapVehiclePositions(feed, mode, routeShortNames)
    }

    companion object {
        const val VEHICLE_POSITIONS_URL = "https://gtfs.ztp.krakow.pl/VehiclePositions_"
    }
}

internal fun mapVehiclePositions(
    feed: GtfsRealtime.FeedMessage,
    mode: TransitVehicleMode,
    routeShortNames: Map<String, String>
): List<LiveVehiclePosition> {
    val knownRouteShortNames = routeShortNames.values.toSet()
    return feed.entityList.mapNotNull { entity ->
        if (!entity.hasVehicle()) return@mapNotNull null
        val vehicle = entity.vehicle
        if (!vehicle.hasPosition()) return@mapNotNull null

        val position = vehicle.position
        if (!position.hasLatitude() || !position.hasLongitude()) return@mapNotNull null
        val latitude = position.latitude.toDouble()
        val longitude = position.longitude.toDouble()
        if (
            !latitude.isFinite() || latitude !in -90.0..90.0 ||
            !longitude.isFinite() || longitude !in -180.0..180.0
        ) {
            return@mapNotNull null
        }

        val descriptor = vehicle.vehicle.takeIf { vehicle.hasVehicle() }
        val vehicleId = descriptor?.id?.takeIf(String::isNotBlank)
        val entityId = entity.id.ifBlank { vehicleId ?: "$latitude,$longitude" }
        val trip = vehicle.trip.takeIf { vehicle.hasTrip() }
        val routeId = trip?.routeId?.takeIf(String::isNotBlank)

        LiveVehiclePosition(
            id = "${mode.name}:$entityId",
            mode = mode,
            latitude = latitude,
            longitude = longitude,
            bearing = position.bearing.takeIf {
                position.hasBearing() && it.isFinite() && it in 0f..360f
            },
            routeShortName = routeId?.let(routeShortNames::get)?.takeIf(String::isNotBlank)
                ?: routeId?.takeIf { mode == TransitVehicleMode.BUS && it in knownRouteShortNames },
            vehicleLabel = descriptor?.label?.takeIf(String::isNotBlank),
            routeId = routeId,
            tripId = trip?.tripId?.takeIf(String::isNotBlank),
            currentStopSequence = vehicle.currentStopSequence.takeIf {
                vehicle.hasCurrentStopSequence()
            },
            currentStopId = vehicle.stopId.takeIf { vehicle.hasStopId() && it.isNotBlank() }
        )
    }
}

internal fun resolveMissingVehicleRouteShortNames(
    vehicles: List<LiveVehiclePosition>,
    routeShortNamesByTripId: Map<String, String>
): List<LiveVehiclePosition> = vehicles.map { vehicle ->
    resolveVehicleRouteShortName(vehicle, routeShortNamesByTripId)
}

private fun resolveVehicleRouteShortName(
    vehicle: LiveVehiclePosition,
    routeShortNamesByTripId: Map<String, String>
): LiveVehiclePosition =
    if (!vehicle.routeShortName.isNullOrBlank()) {
        vehicle
    } else {
        val tripRouteShortName = vehicle.tripId
            ?.let(routeShortNamesByTripId::get)
            ?.takeIf(String::isNotBlank)
        vehicle.copy(routeShortName = tripRouteShortName)
    }
