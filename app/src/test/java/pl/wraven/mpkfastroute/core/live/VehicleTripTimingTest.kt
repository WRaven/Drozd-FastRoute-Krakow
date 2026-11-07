package pl.wraven.mpkfastroute.core.live

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import pl.wraven.mpkfastroute.core.database.StopEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VehicleTripTimingTest {
    private val date = LocalDate.of(2026, 11, 7)
    private val stops = (1..12).map {
        ScheduledVehicleStop(StopEntity("s$it", null, "Stop $it", 50.0, 20.0),
            it, 24 * 3_600 + it * 60, 24 * 3_600 + it * 60 + 20)
    }
    private val trip = VehicleTripSchedule("trip", "22", "End", date, stops, emptyList())
    private val vehicle = LiveVehiclePosition("v", TransitVehicleMode.TRAM, 50.0, 20.0,
        routeShortName = "22", vehicleLabel = "721", currentStopSequence = 5)

    @Test
    fun retainsTheWholeTripAndSeparatesPassedAndUpcomingStops() {
        val times = vehicleStopTimings(vehicle, trip, null)
        assertEquals(12, times.size)
        assertTrue(times.take(4).all { it.isPassed })
        assertTrue(times[4].isCurrent)
        assertTrue(times.drop(4).none { it.isPassed })
        assertTrue(times.all { it.predictedArrival == null })
    }

    @Test
    fun usesAbsoluteArrivalBeforeDelayAndPropagatesDepartureDelayForward() {
        val absolute = gtfsInstant(date, stops[4].arrivalTime).epochSecond + 120
        val update = update(listOf(LiveTripStopUpdate(5, "s5", 999, 180, null, absolute)))
        val timings = vehicleStopTimings(vehicle, trip, update)
        assertEquals(Instant.ofEpochSecond(absolute), timings[4].predictedArrival)
        assertEquals(gtfsInstant(date, stops[5].arrivalTime).plusSeconds(180), timings[5].predictedArrival)
        assertNull(timings[3].predictedArrival)
    }

    @Test
    fun noDataResetsPropagationAndSkippedAndCanceledStopsDoNotClaimArrival() {
        val data = update(listOf(
            LiveTripStopUpdate(5, null, 60, null, null),
            LiveTripStopUpdate(6, null, null, null, null, isSkipped = true),
            LiveTripStopUpdate(7, null, null, null, null, hasNoData = true)
        ))
        val timings = vehicleStopTimings(vehicle, trip, data)
        assertTrue(timings[5].isSkipped)
        assertNull(timings[5].predictedArrival)
        assertNull(timings[6].predictedArrival)
        assertNull(timings[7].predictedArrival)
        assertTrue(vehicleStopTimings(vehicle, trip, data.copy(isCanceled = true))
            .all { it.predictedArrival == null })
    }

    @Test
    fun ignoresAnUpdateForAnotherTrip() {
        val data = update(emptyList()).copy(tripId = "other", tripDelaySeconds = 900)
        assertTrue(vehicleStopTimings(vehicle, trip, data).all { it.predictedArrival == null })
    }

    @Test
    fun ignoresAnotherServiceDaysUpdateAndDoesNotRefreshAnOldSourceTimestampByFetchingIt() {
        val now = Instant.parse("2026-11-08T00:00:00Z")
        val old = update(emptyList()).copy(tripDelaySeconds = 60, serviceDate = date.plusDays(1),
            sourceTimestamp = now.minusSeconds(91), fetchedAt = LocalDateTime.now())
        assertTrue(vehicleStopTimings(vehicle, trip, old).all { it.predictedArrival == null })
        assertEquals(false, isLiveTripUpdateFresh(old, now))
        assertEquals(true, isLiveTripUpdateFresh(old.copy(sourceTimestamp = now.minusSeconds(90)), now))
    }

    @Test
    fun matchesSequenceBeforeStopIdOnALoopingRoute() {
        val looping = trip.copy(stops = stops.map { it.copy(stop = stops.first().stop) })
        val timings = vehicleStopTimings(vehicle, looping,
            update(listOf(LiveTripStopUpdate(5, "s1", 60, null, null))))
        assertNull(timings.first().predictedArrival)
        assertEquals(gtfsInstant(date, stops[4].arrivalTime).plusSeconds(60), timings[4].predictedArrival)
    }

    @Test
    fun handlesMidnightMinutesAndTheGtfsDaylightSavingTimeOrigin() {
        val target = gtfsInstant(date, 25 * 3_600)
        assertEquals(Instant.parse("2026-11-08T00:00:00Z"), target)
        assertEquals(2, minutesUntil(target, target.minusSeconds(61)))
        assertEquals(0, minutesUntil(target, target.plusSeconds(1)))
        assertEquals(-1, minutesUntil(target, target.plusSeconds(60)))
        assertEquals(Instant.parse("2026-03-28T22:00:00Z"), gtfsInstant(LocalDate.of(2026, 3, 29), 0))
    }

    private fun update(stops: List<LiveTripStopUpdate>) =
        LiveTripUpdate("trip", stops, null, LocalDateTime.of(2026, 11, 8, 0, 0))
}
