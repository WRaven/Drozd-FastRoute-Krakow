package pl.wraven.mpkfastroute.feature.search

import android.location.Location
import java.time.LocalDateTime
import java.time.Duration
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import pl.wraven.mpkfastroute.core.csa.CsaJourney
import pl.wraven.mpkfastroute.core.csa.Haversine
import pl.wraven.mpkfastroute.core.csa.JourneyStep
import pl.wraven.mpkfastroute.core.csa.RoutePlanner
import pl.wraven.mpkfastroute.core.csa.StopAccess
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
        val originStopOptions: List<StopEntity>,
        val destinationStopOptions: List<StopEntity>,
        val journey: CsaJourney,
        val originPlace: GeoPlace,
        val destinationPlace: GeoPlace,
        val arrivalTarget: LocalDateTime? = null,
        val departureTarget: LocalDateTime? = null
    ) : QuickActionResult

    data class AlreadyAtDestination(
        val destinationName: String,
        val distanceMeters: Double
    ) : QuickActionResult

    data object NoJourneyFound : QuickActionResult

}

class ContextEngine @Inject constructor(
    private val database: AppDatabase,
    private val appPreferences: AppPreferences,
    private val locationProvider: CurrentLocationProvider,
    private val routePlanner: RoutePlanner
) {
    data class CurrentOrigin(
        val place: GeoPlace,
        val stop: StopEntity,
        val stopOptions: List<StopEntity>
    )

    suspend fun findCurrentOrigin(): CurrentOrigin {
        val location = locationProvider.getCurrentLocation()
        val stops = database.stopDao().getAll()
        val stopGroup = withContext(Dispatchers.Default) {
            findNearestStopGroup(stops, location)
        }
        return CurrentOrigin(
            place = GeoPlace("Moja lokalizacja", location.latitude, location.longitude),
            stop = nearestStopToLocation(stopGroup, location),
            stopOptions = stopGroup.stops
        )
    }

    suspend fun routeToSavedStop(
        destination: SavedDestination,
        arrivalTargetOverride: LocalDateTime? = null
    ): QuickActionResult {
        val savedPlace = when (destination) {
            SavedDestination.HOME -> appPreferences.homePlace.first()
            SavedDestination.WORK -> appPreferences.workPlace.first()
        }
        val schedule = when (destination) {
            SavedDestination.HOME -> appPreferences.homeSchedule.first()
            SavedDestination.WORK -> appPreferences.workSchedule.first()
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
        val (originGroup, destinationGroup) = withContext(Dispatchers.Default) {
            val originStop = findNearestStop(stops, currentLocation)
            val destinationStop = legacyTarget
                ?: findNearestStop(stops, targetPlace.latitude, targetPlace.longitude)
            findStopGroup(stops, originStop) to findStopGroup(stops, destinationStop)
        }
        val originPlace = GeoPlace(
            "Moja lokalizacja",
            currentLocation.latitude,
            currentLocation.longitude
        )
        return findJourney(
            originGroup = originGroup,
            destinationGroup = destinationGroup,
            originPlace = originPlace,
            destinationPlace = targetPlace,
            allStops = stops,
            arrivalTarget = arrivalTargetOverride ?: schedule?.nextOccurrence(LocalDateTime.now())
        )
    }

    private suspend fun findJourney(
        originGroup: StopGroup,
        destinationGroup: StopGroup,
        originPlace: GeoPlace,
        destinationPlace: GeoPlace,
        allStops: List<StopEntity>,
        arrivalTarget: LocalDateTime?
    ): QuickActionResult {
        val now = LocalDateTime.now()
        val earliestScheduledStart =
            arrivalTarget?.minusSeconds(MAX_SEARCH_WINDOW_SECONDS.toLong())
        val searchStart = earliestScheduledStart?.takeIf { it.isAfter(now) } ?: now
        val searchWindowSeconds = arrivalTarget?.let {
            Duration.between(searchStart, it).seconds.toInt()
        } ?: DEFAULT_SEARCH_WINDOW_SECONDS
        val originStopIds = originGroup.stops.mapTo(mutableSetOf()) { it.stopId }
        val destinationStopIds = destinationGroup.stops.mapTo(mutableSetOf()) { it.stopId }
        val candidateJourney = routePlanner.findJourneys(
            originAccesses = originStopIds.map { StopAccess(it, 0.0, 0) },
            destinationAccesses = destinationStopIds.map { StopAccess(it, 0.0, 0) },
            startDateTime = searchStart,
            searchWindowSeconds = searchWindowSeconds,
            maxResults = 1,
            arrivalDeadline = arrivalTarget
        ).firstOrNull() ?: return QuickActionResult.NoJourneyFound
        val firstTransit = candidateJourney.steps
            .filterIsInstance<JourneyStep.Transit>()
            .firstOrNull()
        val journey = candidateJourney.copy(
            departureTime = firstTransit?.connection?.departureTime
                ?: candidateJourney.departureTime
        )

        val transitSteps = journey.steps.filterIsInstance<JourneyStep.Transit>()
        val origin = transitSteps.firstOrNull()
            ?.connection
            ?.departureStopId
            ?.let { stopId -> allStops.firstOrNull { it.stopId == stopId } }
            ?: originGroup.stops.first()
        val destination = transitSteps.lastOrNull()
            ?.connection
            ?.arrivalStopId
            ?.let { stopId -> allStops.firstOrNull { it.stopId == stopId } }
            ?: destinationGroup.stops.first()

        return QuickActionResult.JourneyFound(
            origin = origin,
            destination = destination,
            originStopOptions = originGroup.stops,
            destinationStopOptions = destinationGroup.stops,
            journey = journey,
            originPlace = originPlace,
            destinationPlace = destinationPlace,
            arrivalTarget = arrivalTarget,
            departureTarget = GtfsServiceClock.from(searchStart).serviceDate
                .atStartOfDay()
                .plusSeconds(journey.departureTime.toLong())
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

    private fun findNearestStopGroup(stops: List<StopEntity>, location: Location): StopGroup =
        findStopGroup(stops, findNearestStop(stops, location))

    private fun findStopGroup(stops: List<StopEntity>, stop: StopEntity): StopGroup =
        StopNameGrouper.group(stops).firstOrNull {
            it.name == StopNameGrouper.mainName(stop.stopName)
        } ?: StopGroup(StopNameGrouper.mainName(stop.stopName), listOf(stop))

    private fun nearestStopToLocation(group: StopGroup, location: Location): StopEntity =
        group.stops.minByOrNull {
            Haversine.distanceMeters(location.latitude, location.longitude, it)
        } ?: throw IllegalStateException("A stop group cannot be empty.")

    private companion object {
        const val ALREADY_THERE_DISTANCE_METERS = 100.0
        const val DEFAULT_SEARCH_WINDOW_SECONDS = 2 * 60 * 60
        const val MAX_SEARCH_WINDOW_SECONDS = 2 * 60 * 60
    }
}
