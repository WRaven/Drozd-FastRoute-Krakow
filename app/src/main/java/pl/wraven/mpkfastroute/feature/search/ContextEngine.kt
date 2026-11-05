package pl.wraven.mpkfastroute.feature.search

import android.location.Location
import java.time.LocalDateTime
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import pl.wraven.mpkfastroute.core.csa.CsaJourney
import pl.wraven.mpkfastroute.core.csa.Haversine
import pl.wraven.mpkfastroute.core.csa.RoutePlanner
import pl.wraven.mpkfastroute.core.database.AppDatabase
import pl.wraven.mpkfastroute.core.database.StopEntity
import pl.wraven.mpkfastroute.core.gtfs.GtfsServiceClock
import pl.wraven.mpkfastroute.core.location.CurrentLocationProvider
import pl.wraven.mpkfastroute.core.location.GeoPlace
import pl.wraven.mpkfastroute.core.settings.AppPreferences

enum class SavedDestination {
    HOME,
    WORK
}

sealed interface QuickActionResult {
    data class JourneyFound(
        val origin: StopEntity,
        val destination: StopEntity,
        val journey: CsaJourney,
        val originPlace: GeoPlace,
        val destinationPlace: GeoPlace
    ) : QuickActionResult

    data class AlreadyAtDestination(
        val destinationName: String,
        val distanceMeters: Double
    ) : QuickActionResult

    data object NoJourneyFound : QuickActionResult

    data object NoRandomDestination : QuickActionResult
}

class ContextEngine @Inject constructor(
    private val database: AppDatabase,
    private val appPreferences: AppPreferences,
    private val locationProvider: CurrentLocationProvider,
    private val routePlanner: RoutePlanner
) {
    data class CurrentOrigin(
        val place: GeoPlace,
        val stop: StopEntity
    )

    suspend fun findCurrentOrigin(): CurrentOrigin {
        val location = locationProvider.getCurrentLocation()
        val stops = database.stopDao().getAll()
        val stop = withContext(Dispatchers.Default) { findNearestStop(stops, location) }
        return CurrentOrigin(
            place = GeoPlace("Moja lokalizacja", location.latitude, location.longitude),
            stop = stop
        )
    }

    suspend fun routeToSavedStop(destination: SavedDestination): QuickActionResult {
        val savedPlace = when (destination) {
            SavedDestination.HOME -> appPreferences.homePlace.first()
            SavedDestination.WORK -> appPreferences.workPlace.first()
        }
        val legacyStopId = when (destination) {
            SavedDestination.HOME -> appPreferences.homeStopId.first()
            SavedDestination.WORK -> appPreferences.workStopId.first()
        }
        val legacyTarget = if (savedPlace == null) {
            val stopId = legacyStopId
                ?: throw IllegalStateException(
                    "The ${destination.name.lowercase()} destination is not configured."
                )
            database.stopDao().getById(stopId)
                ?: throw IllegalStateException("The configured destination stop no longer exists.")
        } else {
            null
        }
        val targetPlace = savedPlace ?: legacyTarget?.let { stop ->
            GeoPlace(
                label = StopNameGrouper.mainName(stop.stopName),
                latitude = stop.stopLat,
                longitude = stop.stopLon
            )
        } ?: throw IllegalStateException("The saved destination could not be resolved.")

        val currentLocation = locationProvider.getCurrentLocation()
        val distanceToTarget = Haversine.distanceMeters(
            currentLocation.latitude,
            currentLocation.longitude,
            targetPlace.latitude,
            targetPlace.longitude
        )
        if (distanceToTarget < ALREADY_THERE_DISTANCE_METERS) {
            return QuickActionResult.AlreadyAtDestination(targetPlace.label, distanceToTarget)
        }

        val stops = database.stopDao().getAll()
        val (origin, destinationStop) = withContext(Dispatchers.Default) {
            findNearestStop(stops, currentLocation) to (
                legacyTarget ?: findNearestStop(stops, targetPlace.latitude, targetPlace.longitude)
                )
        }
        val originPlace = GeoPlace(
            "Moja lokalizacja",
            currentLocation.latitude,
            currentLocation.longitude
        )
        return findJourney(
            origin = origin,
            destination = destinationStop,
            originPlace = originPlace,
            destinationPlace = targetPlace,
            searchWindowSeconds = DEFAULT_SEARCH_WINDOW_SECONDS
        )
    }

    suspend fun findRandomJourney(): QuickActionResult {
        val currentLocation = locationProvider.getCurrentLocation()
        val stops = database.stopDao().getAll()
        val (origin, destination) = withContext(Dispatchers.Default) {
            val nearest = findNearestStop(stops, currentLocation)
            val candidateGroups = StopNameGrouper.group(stops)
                .mapNotNull { group ->
                    val eligibleStops = group.stops.filter {
                        Haversine.distanceMeters(nearest, it) >=
                            RANDOM_DESTINATION_MIN_DISTANCE_METERS
                    }
                    eligibleStops.randomOrNull()
                }
            nearest to candidateGroups.randomOrNull()
        }
        if (destination == null) {
            return QuickActionResult.NoRandomDestination
        }

        return findJourney(
            origin = origin,
            destination = destination,
            originPlace = GeoPlace(
                "Moja lokalizacja",
                currentLocation.latitude,
                currentLocation.longitude
            ),
            destinationPlace = GeoPlace(
                StopNameGrouper.mainName(destination.stopName),
                destination.stopLat,
                destination.stopLon
            ),
            searchWindowSeconds = RANDOM_SEARCH_WINDOW_SECONDS
        )
    }

    private suspend fun findJourney(
        origin: StopEntity,
        destination: StopEntity,
        originPlace: GeoPlace,
        destinationPlace: GeoPlace,
        searchWindowSeconds: Int
    ): QuickActionResult {
        val serviceTime = GtfsServiceClock.from(LocalDateTime.now())
        val journey = routePlanner.findEarliestJourney(
            originStopId = origin.stopId,
            destinationStopId = destination.stopId,
            serviceDate = serviceTime.serviceDate,
            startTimeSeconds = serviceTime.timeSeconds,
            searchWindowSeconds = searchWindowSeconds
        ) ?: return QuickActionResult.NoJourneyFound

        return QuickActionResult.JourneyFound(
            origin = origin,
            destination = destination,
            journey = journey,
            originPlace = originPlace,
            destinationPlace = destinationPlace
        )
    }

    private fun findNearestStop(stops: List<StopEntity>, location: Location): StopEntity =
        findNearestStop(stops, location.latitude, location.longitude)

    private fun findNearestStop(
        stops: List<StopEntity>,
        latitude: Double,
        longitude: Double
    ): StopEntity =
        stops.minByOrNull { stop -> Haversine.distanceMeters(latitude, longitude, stop) }
            ?: throw IllegalStateException("No GTFS stops are available.")

    private companion object {
        const val ALREADY_THERE_DISTANCE_METERS = 100.0
        const val RANDOM_DESTINATION_MIN_DISTANCE_METERS = 3_000.0
        const val RANDOM_SEARCH_WINDOW_SECONDS = 60 * 60
        const val DEFAULT_SEARCH_WINDOW_SECONDS = 2 * 60 * 60
    }
}
