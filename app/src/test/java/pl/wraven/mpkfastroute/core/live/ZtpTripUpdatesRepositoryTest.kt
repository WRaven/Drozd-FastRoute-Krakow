package pl.wraven.mpkfastroute.core.live

import com.google.transit.realtime.GtfsRealtime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ZtpTripUpdatesRepositoryTest {
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
