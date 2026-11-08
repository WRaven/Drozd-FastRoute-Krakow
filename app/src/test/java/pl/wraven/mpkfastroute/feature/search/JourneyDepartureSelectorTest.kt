package pl.wraven.mpkfastroute.feature.search

import org.junit.Assert.assertEquals
import org.junit.Test
import pl.wraven.mpkfastroute.core.csa.CsaJourney
import pl.wraven.mpkfastroute.core.csa.Connection
import pl.wraven.mpkfastroute.core.csa.JourneyStep
import pl.wraven.mpkfastroute.core.csa.StopAccess

class JourneyDepartureSelectorTest {
    @Test
    fun returnsFiveDistinctDeparturesInTimeOrderAndKeepsBestRouteForEachTrip() {
        val selected = selectDistinctJourneyDepartures(
            candidates = listOf(
                journey("trip-3", departure = 300, arrival = 800),
                journey("trip-1", departure = 100, arrival = 700),
                journey("trip-1", departure = 100, arrival = 600),
                journey("trip-2", departure = 200, arrival = 700),
                journey("trip-4", departure = 400, arrival = 900),
                journey("trip-5", departure = 500, arrival = 1_000),
                journey("trip-6", departure = 600, arrival = 1_100)
            )
        )

        assertEquals(
            listOf("trip-1", "trip-2", "trip-3", "trip-4", "trip-5"),
            selected.map { it.firstTransitConnection()?.tripId }
        )
        assertEquals(600, selected.first().arrivalTime)
    }

    @Test
    fun ordersArriveByDeparturesFromLatestToEarliest() {
        val selected = selectDistinctJourneyDepartures(
            candidates = listOf(
                journey("trip-1", departure = 100, arrival = 400),
                journey("trip-2", departure = 200, arrival = 500),
                journey("trip-3", departure = 300, arrival = 600)
            ),
            latestFirst = true
        )

        assertEquals(
            listOf("trip-3", "trip-2", "trip-1"),
            selected.map { it.firstTransitConnection()?.tripId }
        )
    }

    private fun journey(tripId: String, departure: Int, arrival: Int) = CsaJourney(
        departureTime = 0,
        arrivalTime = arrival,
        transferPenaltySeconds = 0,
        steps = listOf(
            JourneyStep.Transit(
                Connection(
                    departureStopId = "origin",
                    arrivalStopId = "destination",
                    departureTime = departure,
                    arrivalTime = arrival,
                    tripId = tripId,
                    routeShortName = "5"
                )
            )
        ),
        originAccess = StopAccess("origin", 0.0, 0),
        destinationAccess = StopAccess("destination", 0.0, 0),
        transferWalkingDistanceMeters = 0.0
    )
}
