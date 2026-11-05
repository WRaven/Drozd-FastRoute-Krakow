package pl.wraven.mpkfastroute.feature.search

import android.location.Location
import java.time.LocalDateTime
import javax.inject.Inject
import kotlinx.coroutines.flow.first
import pl.wraven.mpkfastroute.core.csa.CsaJourney
import pl.wraven.mpkfastroute.core.csa.Haversine
import pl.wraven.mpkfastroute.core.csa.RoutePlanner
import pl.wraven.mpkfastroute.core.database.AppDatabase
import pl.wraven.mpkfastroute.core.database.StopEntity
import pl.wraven.mpkfastroute.core.gtfs.GtfsServiceClock
import pl.wraven.mpkfastroute.core.location.CurrentLocationProvider
import pl.wraven.mpkfastroute.core.settings.AppPreferences

enum class SavedDestination {
    HOME,
    WORK
}

sealed interface QuickActionResult {
    data class JourneyFound(
        val origin: StopEntity,
        val destination: StopEntity,
        val journey: CsaJourney
    ) : QuickActionResult

    data class AlreadyAtDestination(
        val destination: StopEntity,
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
    suspend fun routeToSavedStop(destination: SavedDestination): QuickActionResult {
        val stopId = when (destination) {
            SavedDestination.HOME -> appPreferences.homeStopId.first()
            SavedDestination.WORK -> appPreferences.workStopId.first()
        } ?: throw IllegalStateException("The ${destination.name.lowercase()} stop is not configured.")
        val target = database.stopDao().getById(stopId)
            ?: throw IllegalStateException("The configured destination stop no longer exists.")
        val currentLocation = locationProvider.getCurrentLocation()
        val distanceToTarget = distanceTo(currentLocation, target)
        if (distanceToTarget < ALREADY_THERE_DISTANCE_METERS) {
            return QuickActionResult.AlreadyAtDestination(target, distanceToTarget)
        }

        val stops = database.stopDao().getAll()
        val origin = findNearestStop(stops, currentLocation)
        return findJourney(origin, target, DEFAULT_SEARCH_WINDOW_SECONDS)
    }

    suspend fun findRandomJourney(): QuickActionResult {
        val currentLocation = locationProvider.getCurrentLocation()
        val stops = database.stopDao().getAll()
        val origin = findNearestStop(stops, currentLocation)
        val destination = stops
            .asSequence()
            .filter { it.stopId != origin.stopId }
            .filter { Haversine.distanceMeters(origin, it) >= RANDOM_DESTINATION_MIN_DISTANCE_METERS }
            .toList()
            .randomOrNull()
            ?: return QuickActionResult.NoRandomDestination

        return findJourney(origin, destination, RANDOM_SEARCH_WINDOW_SECONDS)
    }

    private suspend fun findJourney(
        origin: StopEntity,
        destination: StopEntity,
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

        return QuickActionResult.JourneyFound(origin, destination, journey)
    }

    private fun findNearestStop(stops: List<StopEntity>, location: Location): StopEntity =
        stops.minByOrNull { stop -> distanceTo(location, stop) }
            ?: throw IllegalStateException("No GTFS stops are available.")

    private fun distanceTo(location: Location, stop: StopEntity): Double =
        Haversine.distanceMeters(location.latitude, location.longitude, stop)

    private companion object {
        const val ALREADY_THERE_DISTANCE_METERS = 100.0
        const val RANDOM_DESTINATION_MIN_DISTANCE_METERS = 3_000.0
        const val RANDOM_SEARCH_WINDOW_SECONDS = 60 * 60
        const val DEFAULT_SEARCH_WINDOW_SECONDS = 2 * 60 * 60
    }
}
