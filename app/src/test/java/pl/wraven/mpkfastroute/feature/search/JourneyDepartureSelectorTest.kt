package pl.wraven.mpkfastroute.feature.search

import org.junit.Assert.assertEquals
import org.junit.Test
import pl.wraven.mpkfastroute.core.csa.CsaJourney
import pl.wraven.mpkfastroute.core.csa.Connection
import pl.wraven.mpkfastroute.core.csa.Footpath
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

    @Test
    fun limitsAJourneyPageToFourDistinctDepartures() {
        val selected = selectDistinctJourneyDepartures(
            candidates = (1..6).map { index ->
                journey("trip-$index", departure = index * 100, arrival = index * 100 + 500)
            },
            maxResults = ROUTE_SEARCH_PAGE_SIZE
        )

        assertEquals(4, selected.size)
    }

    @Test
    fun terminalWalkIsShownAsDestinationAccessNotAsATransfer() {
        val journey = CsaJourney(
            departureTime = 0,
            arrivalTime = 100,
            transferPenaltySeconds = 0,
            steps = listOf(
                JourneyStep.Transit(testConnection("A", "B", 0, 100, "trip-1")),
                JourneyStep.Walk(Footpath("B", "C", 60, 63.0))
            ),
            originAccess = StopAccess("A", 302.0, 242),
            destinationAccess = StopAccess("C", 369.0, 296),
            transferWalkingDistanceMeters = 63.0
        )

        val walking = journey.walkingBreakdown()

        assertEquals(302.0, walking.startMeters, 0.0)
        assertEquals(0.0, walking.transferMeters, 0.0)
        assertEquals(432.0, walking.destinationMeters, 0.0)
        assertEquals(journey.totalWalkingDistanceMeters, walking.startMeters +
            walking.transferMeters + walking.destinationMeters, 0.0)
    }

    @Test
    fun walkingBetweenTransitLegsIsCountedAsTransferWalking() {
        val journey = CsaJourney(
            departureTime = 0,
            arrivalTime = 200,
            transferPenaltySeconds = 180,
            steps = listOf(
                JourneyStep.Transit(testConnection("A", "B", 0, 100, "trip-1")),
                JourneyStep.Walk(Footpath("B", "C", 60, 75.0)),
                JourneyStep.Transit(testConnection("C", "D", 160, 200, "trip-2"))
            ),
            originAccess = StopAccess("A", 10.0, 8),
            destinationAccess = StopAccess("D", 20.0, 16),
            transferWalkingDistanceMeters = 75.0
        )

        val walking = journey.walkingBreakdown()

        assertEquals(10.0, walking.startMeters, 0.0)
        assertEquals(75.0, walking.transferMeters, 0.0)
        assertEquals(20.0, walking.destinationMeters, 0.0)
    }

    private fun journey(tripId: String, departure: Int, arrival: Int) = CsaJourney(
        departureTime = departure,
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

    private fun testConnection(
        departureStopId: String,
        arrivalStopId: String,
        departureTime: Int,
        arrivalTime: Int,
        tripId: String
    ) = Connection(
        departureStopId = departureStopId,
        arrivalStopId = arrivalStopId,
        departureTime = departureTime,
        arrivalTime = arrivalTime,
        tripId = tripId,
        routeShortName = "1"
    )
}
