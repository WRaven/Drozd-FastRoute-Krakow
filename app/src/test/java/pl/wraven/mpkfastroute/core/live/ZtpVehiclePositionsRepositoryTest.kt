package pl.wraven.mpkfastroute.core.live

import com.google.transit.realtime.GtfsRealtime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ZtpVehiclePositionsRepositoryTest {
    @Test
    fun mapsLiveVehiclePositionAndResolvesRouteShortName() {
        val feed = GtfsRealtime.FeedMessage.newBuilder()
            .setHeader(feedHeader())
            .addEntity(
                GtfsRealtime.FeedEntity.newBuilder()
                    .setId("vehicle-entity")
                    .setVehicle(
                        GtfsRealtime.VehiclePosition.newBuilder()
                            .setPosition(
                                GtfsRealtime.Position.newBuilder()
                                    .setLatitude(50.061f)
                                    .setLongitude(19.937f)
                                    .setBearing(90f)
                            )
                            .setTrip(
                                GtfsRealtime.TripDescriptor.newBuilder()
                                    .setRouteId("route-52")
                                    .setTripId("trip-52-1")
                            )
                            .setCurrentStopSequence(3)
                            .setStopId("stop-3")
                            .setVehicle(
                                GtfsRealtime.VehicleDescriptor.newBuilder()
                                    .setId("fleet-123")
                                    .setLabel("123")
                            )
                    )
            )
            .build()

        val position = mapVehiclePositions(
            feed = feed,
            mode = TransitVehicleMode.TRAM,
            routeShortNames = mapOf("route-52" to "52")
        ).single()

        assertEquals(TransitVehicleMode.TRAM, position.mode)
        assertEquals(90f, position.bearing ?: -1f, 0.001f)
        assertEquals("52", position.routeShortName)
        assertEquals("123", position.vehicleLabel)
        assertEquals("route-52", position.routeId)
        assertEquals("trip-52-1", position.tripId)
        assertEquals(3, position.currentStopSequence)
        assertEquals("stop-3", position.currentStopId)
        assertEquals(50.061, position.latitude, 0.001)
        assertEquals(19.937, position.longitude, 0.001)
    }

    @Test
    fun skipsVehiclesWithoutPositions() {
        val feed = GtfsRealtime.FeedMessage.newBuilder()
            .setHeader(feedHeader())
            .addEntity(
                GtfsRealtime.FeedEntity.newBuilder()
                    .setId("missing-longitude")
                    .setVehicle(GtfsRealtime.VehiclePosition.newBuilder())
            )
            .build()

        assertNull(
            mapVehiclePositions(
                feed = feed,
                mode = TransitVehicleMode.BUS,
                routeShortNames = emptyMap()
            ).firstOrNull()
        )
    }

    private fun feedHeader() = GtfsRealtime.FeedHeader.newBuilder()
        .setGtfsRealtimeVersion("2.0")
        .build()
}
