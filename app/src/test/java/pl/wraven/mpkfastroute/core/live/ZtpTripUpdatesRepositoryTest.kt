package pl.wraven.mpkfastroute.core.live

import com.google.transit.realtime.GtfsRealtime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ZtpTripUpdatesRepositoryTest {
    @Test
    fun treatsTheBusFeedsZeroArrivalEpochAsMissingRatherThanJanuary1970() {
        val feed = GtfsRealtime.FeedMessage.newBuilder()
            .setHeader(GtfsRealtime.FeedHeader.newBuilder().setGtfsRealtimeVersion("2.0"))
            .addEntity(GtfsRealtime.FeedEntity.newBuilder().setId("bus").setTripUpdate(
                GtfsRealtime.TripUpdate.newBuilder()
                    .setTrip(GtfsRealtime.TripDescriptor.newBuilder().setTripId("trip"))
                    .addStopTimeUpdate(GtfsRealtime.TripUpdate.StopTimeUpdate.newBuilder()
                        .setStopSequence(1)
                        .setArrival(GtfsRealtime.TripUpdate.StopTimeEvent.newBuilder().setTime(0).setDelay(0))
                        .setDeparture(GtfsRealtime.TripUpdate.StopTimeEvent.newBuilder().setTime(1_750_000_000L)))
            )).build()
        val stop = mapTripUpdate(feed, "trip")?.stopTimeUpdates?.single()
        assertNull(stop?.arrivalTimeEpochSeconds)
        assertNull(stop?.arrivalDelaySeconds)
        assertEquals(1_750_000_000L, stop?.departureTimeEpochSeconds)
    }

    @Test
    fun mapsTheServiceDateAndTheSourcesTimestamp() {
        val feed = GtfsRealtime.FeedMessage.newBuilder()
            .setHeader(GtfsRealtime.FeedHeader.newBuilder().setGtfsRealtimeVersion("2.0").setTimestamp(1_750_000_000L))
            .addEntity(GtfsRealtime.FeedEntity.newBuilder().setId("trip").setTripUpdate(
                GtfsRealtime.TripUpdate.newBuilder().setTrip(
                    GtfsRealtime.TripDescriptor.newBuilder().setTripId("trip").setStartDate("20261107")
                )
            )).build()
        val update = mapTripUpdate(feed, "trip")
        assertEquals(java.time.LocalDate.of(2026, 11, 7), update?.serviceDate)
        assertEquals(java.time.Instant.ofEpochSecond(1_750_000_000L), update?.sourceTimestamp)
    }

    @Test
    fun mapsArrivalEpochSkippedStopsAndNoData() {
        val feed = GtfsRealtime.FeedMessage.newBuilder()
            .setHeader(GtfsRealtime.FeedHeader.newBuilder().setGtfsRealtimeVersion("2.0"))
            .addEntity(GtfsRealtime.FeedEntity.newBuilder().setId("update").setTripUpdate(
                GtfsRealtime.TripUpdate.newBuilder()
                    .setTrip(GtfsRealtime.TripDescriptor.newBuilder().setTripId("trip"))
                    .addStopTimeUpdate(GtfsRealtime.TripUpdate.StopTimeUpdate.newBuilder()
                        .setStopSequence(1)
                        .setArrival(GtfsRealtime.TripUpdate.StopTimeEvent.newBuilder().setTime(1_750_000_000L)))
                    .addStopTimeUpdate(GtfsRealtime.TripUpdate.StopTimeUpdate.newBuilder()
                        .setStopSequence(2)
                        .setScheduleRelationship(GtfsRealtime.TripUpdate.StopTimeUpdate.ScheduleRelationship.SKIPPED))
                    .addStopTimeUpdate(GtfsRealtime.TripUpdate.StopTimeUpdate.newBuilder()
                        .setStopSequence(3)
                        .setScheduleRelationship(GtfsRealtime.TripUpdate.StopTimeUpdate.ScheduleRelationship.NO_DATA))
            )).build()
        val update = mapTripUpdate(feed, "trip") ?: error("Missing update")
        assertEquals(1_750_000_000L, update.stopTimeUpdates[0].arrivalTimeEpochSeconds)
        assertEquals(true, update.stopTimeUpdates[1].isSkipped)
        assertEquals(true, update.stopTimeUpdates[2].hasNoData)
    }

    @Test
    fun mapsStopUpdatesForTheRequestedTrip() {
        val feed = GtfsRealtime.FeedMessage.newBuilder()
            .setHeader(
                GtfsRealtime.FeedHeader.newBuilder()
                    .setGtfsRealtimeVersion("2.0")
            )
            .addEntity(
                GtfsRealtime.FeedEntity.newBuilder()
                    .setId("trip-update")
                    .setTripUpdate(
                        GtfsRealtime.TripUpdate.newBuilder()
                            .setTrip(
                                GtfsRealtime.TripDescriptor.newBuilder()
                                    .setTripId("trip-52-1")
                            )
                            .addStopTimeUpdate(
                                GtfsRealtime.TripUpdate.StopTimeUpdate.newBuilder()
                                    .setStopSequence(4)
                                    .setStopId("stop-4")
                                    .setDeparture(
                                        GtfsRealtime.TripUpdate.StopTimeEvent.newBuilder()
                                            .setDelay(180)
                                            .setTime(1_750_000_000L)
                                    )
                            )
                    )
            )
            .build()

        val update = mapTripUpdate(feed, "trip-52-1")

        assertEquals("trip-52-1", update?.tripId)
        assertEquals(4, update?.stopTimeUpdates?.single()?.stopSequence)
        assertEquals("stop-4", update?.stopTimeUpdates?.single()?.stopId)
        assertEquals(180, update?.stopTimeUpdates?.single()?.departureDelaySeconds)
        assertEquals(
            1_750_000_000L,
            update?.stopTimeUpdates?.single()?.departureTimeEpochSeconds
        )
    }

    @Test
    fun returnsNullWhenTheTripHasNoRealtimeUpdate() {
        val feed = GtfsRealtime.FeedMessage.newBuilder()
            .setHeader(
                GtfsRealtime.FeedHeader.newBuilder()
                    .setGtfsRealtimeVersion("2.0")
            )
            .build()

        assertNull(mapTripUpdate(feed, "unknown-trip"))
    }
}
