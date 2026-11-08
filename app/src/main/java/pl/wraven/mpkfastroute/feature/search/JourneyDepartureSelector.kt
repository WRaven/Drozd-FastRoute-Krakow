package pl.wraven.mpkfastroute.feature.search

import pl.wraven.mpkfastroute.core.csa.CsaJourney
import pl.wraven.mpkfastroute.core.csa.Connection
import pl.wraven.mpkfastroute.core.csa.JourneyStep

internal const val MAX_DISPLAYED_ROUTE_DEPARTURES = 5

internal fun CsaJourney.firstTransitConnection(): Connection? =
    steps.filterIsInstance<JourneyStep.Transit>().firstOrNull()?.connection

internal fun selectDistinctJourneyDepartures(
    candidates: List<CsaJourney>,
    maxResults: Int = MAX_DISPLAYED_ROUTE_DEPARTURES,
    latestFirst: Boolean = false
): List<CsaJourney> {
    require(maxResults > 0) { "At least one journey result is required." }

    val routePreference = if (latestFirst) {
        compareByDescending<CsaJourney> { it.estimatedArrivalTime }
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

    val departureOrder = compareBy<CsaJourney> {
        it.firstTransitConnection()?.departureTime ?: Int.MAX_VALUE
    }.thenBy { it.estimatedArrivalTime }
    val ordered = if (latestFirst) departureOrder.reversed() else departureOrder
    return bestByTrip.sortedWith(ordered).take(maxResults)
}
