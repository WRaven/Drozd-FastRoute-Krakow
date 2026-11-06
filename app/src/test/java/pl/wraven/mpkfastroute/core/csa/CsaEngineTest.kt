package pl.wraven.mpkfastroute.core.csa

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class CsaEngineTest {
    private val engine = CsaEngine()

    @Test
    fun findsDirectJourneyWithoutTransferPenalty() {
        val journey = engine.findEarliestJourney(
            connections = listOf(connection("A", "B", 8 * 3600, 8 * 3600 + 600, "trip-1")),
            footpaths = emptyList(),
            originStopId = "A",
            destinationStopId = "B",
            startTime = 8 * 3600
        )

        assertNotNull(journey)
        assertEquals(8 * 3600 + 600, journey?.arrivalTime)
        assertEquals(0, journey?.transferPenaltySeconds)
        assertEquals(1, journey?.steps?.size)
    }

    @Test
    fun choosesBestJourneyAcrossOriginAndDestinationPlatforms() {
        val journey = engine.findEarliestJourney(
            connections = listOf(
                connection("origin-1", "destination-1", 100, 1_300, "trip-1"),
                connection("origin-2", "destination-2", 200, 900, "trip-2")
            ),
            footpaths = emptyList(),
            originStopIds = setOf("origin-1", "origin-2"),
            destinationStopIds = setOf("destination-1", "destination-2"),
            startTime = 100
        )

        assertNotNull(journey)
        assertEquals(900, journey?.arrivalTime)
        val selectedConnection =
            (journey?.steps?.single() as? JourneyStep.Transit)?.connection
        assertNotNull(selectedConnection)
        assertEquals("origin-2", selectedConnection?.departureStopId)
        assertEquals("destination-2", selectedConnection?.arrivalStopId)
    }

    @Test
    fun appliesThreeMinuteMinimumAndPenaltyWhenChangingTrips() {
        val journey = engine.findEarliestJourney(
            connections = listOf(
                connection("A", "B", 8 * 3600, 8 * 3600 + 600, "trip-1"),
                connection("B", "C", 8 * 3600 + 780, 8 * 3600 + 1_200, "trip-2")
            ),
            footpaths = emptyList(),
            originStopId = "A",
            destinationStopId = "C",
            startTime = 8 * 3600
        )

        assertNotNull(journey)
        assertEquals(8 * 3600 + 1_200, journey?.arrivalTime)
        assertEquals(180, journey?.transferPenaltySeconds)
        assertEquals(2, journey?.steps?.size)
    }

    @Test
    fun rejectsTransferWithLessThanThreeMinutes() {
        val journey = engine.findEarliestJourney(
            connections = listOf(
                connection("A", "B", 8 * 3600, 8 * 3600 + 600, "trip-1"),
                connection("B", "C", 8 * 3600 + 779, 8 * 3600 + 1_200, "trip-2")
            ),
            footpaths = emptyList(),
            originStopId = "A",
            destinationStopId = "C",
            startTime = 8 * 3600
        )

        assertNull(journey)
    }

    @Test
    fun continuesOnSameTripWithoutTransferPenalty() {
        val journey = engine.findEarliestJourney(
            connections = listOf(
                connection("A", "B", 8 * 3600, 8 * 3600 + 600, "trip-1"),
                connection("B", "C", 8 * 3600 + 601, 8 * 3600 + 1_200, "trip-1")
            ),
            footpaths = emptyList(),
            originStopId = "A",
            destinationStopId = "C",
            startTime = 8 * 3600
        )

        assertNotNull(journey)
        assertEquals(0, journey?.transferPenaltySeconds)
    }

    @Test
    fun chargesFootpathTransferPenaltyAndIncludesWalkingStep() {
        val footpath = Footpath("B", "C", 30)
        val journey = engine.findEarliestJourney(
            connections = listOf(
                connection("A", "B", 8 * 3600, 8 * 3600 + 600, "trip-1"),
                connection("C", "D", 8 * 3600 + 810, 8 * 3600 + 1_200, "trip-2")
            ),
            footpaths = listOf(footpath),
            originStopId = "A",
            destinationStopId = "D",
            startTime = 8 * 3600
        )

        assertNotNull(journey)
        assertEquals(180, journey?.transferPenaltySeconds)
        assertEquals(3, journey?.steps?.size)
        assertEquals(JourneyStep.Walk(footpath), journey?.steps?.get(1))
    }

    @Test
    fun requiresWalkingTimePlusThreeMinutesForAnInterStopTransfer() {
        val footpath = Footpath("B", "C", 240, 300.0)
        val connections = listOf(
            connection("A", "B", 0, 600, "trip-1"),
            connection("C", "D", 1_020, 1_200, "trip-2")
        )

        val journey = engine.findEarliestJourney(
            connections = connections,
            footpaths = listOf(footpath),
            originStopId = "A",
            destinationStopId = "D",
            startTime = 0
        )

        assertNotNull(journey)
        assertEquals(1_200, journey?.arrivalTime)
        assertEquals(180, journey?.transferPenaltySeconds)
        assertEquals(300.0, journey?.transferWalkingDistanceMeters)

        val impossibleJourney = engine.findEarliestJourney(
            connections = connections.map {
                if (it.departureStopId == "C") it.copy(departureTime = 1_019) else it
            },
            footpaths = listOf(footpath),
            originStopId = "A",
            destinationStopId = "D",
            startTime = 0
        )
        assertNull(impossibleJourney)
    }

    @Test
    fun doesNotChargeTransferPenaltyForWalkingFromTheLastStopToTheDestination() {
        val journey = engine.findEarliestJourney(
            connections = listOf(connection("A", "B", 0, 600, "trip-1")),
            footpaths = listOf(Footpath("B", "C", 240, 300.0)),
            originStopId = "A",
            destinationStopId = "C",
            startTime = 0
        )

        assertNotNull(journey)
        assertEquals(840, journey?.arrivalTime)
        assertEquals(0, journey?.transferPenaltySeconds)
    }

    @Test
    fun ranksDoorToDoorTimeAndRetainsALessWalkingAlternative() {
        val journeys = engine.findJourneys(
            connections = listOf(
                connection("fast-origin", "fast-destination", 80, 200, "fast-trip"),
                connection("walk-origin", "walk-destination", 0, 350, "less-walking-trip")
            ),
            footpaths = emptyList(),
            originAccesses = listOf(
                StopAccess("fast-origin", 100.0, 80),
                StopAccess("walk-origin", 0.0, 0)
            ),
            destinationAccesses = listOf(
                StopAccess("fast-destination", 100.0, 80),
                StopAccess("walk-destination", 0.0, 0)
            ),
            startTime = 0,
            maxResults = 2
        )

        assertEquals(2, journeys.size)
        assertEquals("fast-origin", journeys.first().originAccess.stopId)
        assertEquals(280, journeys.first().estimatedArrivalTime)
        assertEquals(200.0, journeys.first().totalWalkingDistanceMeters, 0.001)
        assertEquals("walk-origin", journeys.last().originAccess.stopId)
        assertEquals(350, journeys.last().estimatedArrivalTime)
        assertEquals(0.0, journeys.last().totalWalkingDistanceMeters, 0.001)
    }

    @Test
    fun returnsEmptyJourneyWhenAlreadyAtDestination() {
        val journey = engine.findEarliestJourney(
            connections = emptyList(),
            footpaths = emptyList(),
            originStopId = "A",
            destinationStopId = "A",
            startTime = 100
        )

        assertEquals(100, journey?.arrivalTime)
        assertEquals(emptyList<JourneyStep>(), journey?.steps)
    }

    private fun connection(
        from: String,
        to: String,
        departure: Int,
        arrival: Int,
        tripId: String
    ) = Connection(from, to, departure, arrival, tripId, "1")
}
