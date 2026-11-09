package pl.wraven.mpkfastroute.feature.search

import pl.wraven.mpkfastroute.core.csa.CsaJourney
import pl.wraven.mpkfastroute.core.csa.Connection
import pl.wraven.mpkfastroute.core.csa.Footpath
import pl.wraven.mpkfastroute.core.csa.JourneyStep
import pl.wraven.mpkfastroute.core.csa.StopAccess
import pl.wraven.mpkfastroute.core.gtfs.GtfsServiceClock
import pl.wraven.mpkfastroute.core.live.KRAKOW_ZONE
import pl.wraven.mpkfastroute.core.live.LiveTripUpdate
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset

internal const val MAX_DISPLAYED_ROUTE_DEPARTURES = 5

internal data class JourneyWalkingBreakdown(
    val startMeters: Double,
    val transferMeters: Double,
    val destinationMeters: Double
)

internal fun CsaJourney.firstTransitConnection(): Connection? =
    steps.filterIsInstance<JourneyStep.Transit>().firstOrNull()?.connection

internal fun CsaJourney.firstTransitDepartureTime(): Int =
    firstTransitConnection()?.departureTime ?: departureTime

internal fun CsaJourney.firstTransitDepartureDateTime(serviceDate: LocalDate): LocalDateTime =
    serviceDate.atStartOfDay().plusSeconds(firstTransitDepartureTime().toLong())

internal fun CsaJourney.estimatedFirstTransitDepartureDateTime(
    scheduledDeparture: LocalDateTime,
    tripUpdates: Map<String, LiveTripUpdate>
): LocalDateTime {
    val connection = firstTransitConnection() ?: return scheduledDeparture
    val update = tripUpdates[connection.tripId] ?: return scheduledDeparture
    val serviceDate = GtfsServiceClock.from(scheduledDeparture).serviceDate
    if (update.isCanceled || update.serviceDate?.let { it != serviceDate } == true) {
        return scheduledDeparture
    }

    val stopUpdate = update.stopTimeUpdates.firstOrNull {
        it.stopId == connection.departureStopId
    }
    if (stopUpdate?.isSkipped == true || stopUpdate?.hasNoData == true) {
        return scheduledDeparture
    }
    stopUpdate?.departureTimeEpochSeconds?.let { epochSeconds ->
        return java.time.Instant.ofEpochSecond(epochSeconds)
            .atZone(KRAKOW_ZONE)
            .toLocalDateTime()
    }
    val delaySeconds = stopUpdate?.departureDelaySeconds ?: update.tripDelaySeconds
    return delaySeconds?.let { scheduledDeparture.plusSeconds(it.toLong()) } ?: scheduledDeparture
}

internal fun CsaJourney.walkingBreakdown(): JourneyWalkingBreakdown {
    val firstTransitIndex = steps.indexOfFirst { it is JourneyStep.Transit }
    val lastTransitIndex = steps.indexOfLast { it is JourneyStep.Transit }
    var startMeters = originAccess.walkingDistanceMeters
    var transferMeters = 0.0
    var destinationMeters = destinationAccess.walkingDistanceMeters

    steps.forEachIndexed { index, step ->
        val walk = step as? JourneyStep.Walk ?: return@forEachIndexed
        when {
            firstTransitIndex < 0 || index < firstTransitIndex ->
                startMeters += walk.footpath.distanceMeters
            index > lastTransitIndex ->
                destinationMeters += walk.footpath.distanceMeters
            else -> transferMeters += walk.footpath.distanceMeters
        }
    }

    return JourneyWalkingBreakdown(
        startMeters = startMeters,
        transferMeters = transferMeters,
        destinationMeters = destinationMeters
    )
}

internal fun selectDistinctJourneyDepartures(
    candidates: List<CsaJourney>,
    maxResults: Int = MAX_DISPLAYED_ROUTE_DEPARTURES,
    latestFirst: Boolean = false,
    orderByTransitDeparture: Boolean = false,
    departureDateTimeOverrides: Map<CsaJourney, LocalDateTime>? = null
): List<CsaJourney> {
    require(maxResults > 0) { "At least one journey result is required." }

    val departureOrderTime: (CsaJourney) -> Long = { journey ->
        departureDateTimeOverrides?.get(journey)?.toEpochSecond(ZoneOffset.UTC)
            ?: if (orderByTransitDeparture) {
                journey.firstTransitDepartureTime().toLong()
            } else {
                journey.departureTime.toLong()
            }
    }
    val routePreference = if (latestFirst) {
        compareByDescending(departureOrderTime)
            .thenBy { it.totalDurationSeconds }
            .thenBy { it.totalWalkingDistanceMeters }
            .thenBy { it.transferPenaltySeconds }
    } else {
        compareBy<CsaJourney> { it.totalDurationSeconds }
            .thenBy { it.totalWalkingDistanceMeters }
            .thenBy { it.transferPenaltySeconds }
    }

    val bestByTrip = candidates
        .mapNotNull { journey ->
            journey.firstTransitConnection()?.let { it.tripId to journey }
        }
        .groupBy({ it.first }, { it.second })
        .values
        .mapNotNull { journeys -> journeys.minWithOrNull(routePreference) }

    val departureOrder = compareBy(departureOrderTime)
        .thenBy { it.estimatedArrivalTime }
    val ordered = if (latestFirst) departureOrder.reversed() else departureOrder
    return bestByTrip.sortedWith(ordered).take(maxResults)
}
