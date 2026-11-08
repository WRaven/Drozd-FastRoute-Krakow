package pl.wraven.mpkfastroute.feature.search

import pl.wraven.mpkfastroute.core.csa.CsaJourney
import pl.wraven.mpkfastroute.core.csa.Connection
import pl.wraven.mpkfastroute.core.csa.Footpath
import pl.wraven.mpkfastroute.core.csa.JourneyStep
import pl.wraven.mpkfastroute.core.csa.StopAccess

internal const val MAX_DISPLAYED_ROUTE_DEPARTURES = 5

internal data class JourneyWalkingBreakdown(
    val startMeters: Double,
    val transferMeters: Double,
    val destinationMeters: Double
)

internal fun CsaJourney.firstTransitConnection(): Connection? =
    steps.filterIsInstance<JourneyStep.Transit>().firstOrNull()?.connection

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
    latestFirst: Boolean = false
): List<CsaJourney> {
    require(maxResults > 0) { "At least one journey result is required." }

    val routePreference = if (latestFirst) {
        compareByDescending<CsaJourney> { it.departureTime }
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

    val departureOrder = compareBy<CsaJourney> { it.departureTime }
        .thenBy { it.estimatedArrivalTime }
    val ordered = if (latestFirst) departureOrder.reversed() else departureOrder
    return bestByTrip.sortedWith(ordered).take(maxResults)
}
