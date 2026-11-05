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
