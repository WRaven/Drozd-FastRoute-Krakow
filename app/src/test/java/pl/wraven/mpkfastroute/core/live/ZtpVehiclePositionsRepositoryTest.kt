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

    @Test
    fun resolvesMissingRouteShortNameFromTripId() {
        val vehicle = LiveVehiclePosition(
            id = "vehicle-1",
            mode = TransitVehicleMode.BUS,
            latitude = 50.061,
            longitude = 19.937,
            routeShortName = null,
            vehicleLabel = "123",
            tripId = "trip-152-1"
        )

        val resolved = resolveMissingVehicleRouteShortNames(
            vehicles = listOf(vehicle),
            routeShortNamesByTripId = mapOf("trip-152-1" to "152")
        ).single()

        assertEquals("152", resolved.routeShortName)
    }

    @Test
    fun keepsRouteShortNameWhenItIsAlreadyResolved() {
        val vehicle = LiveVehiclePosition(
            id = "vehicle-1",
            mode = TransitVehicleMode.TRAM,
            latitude = 50.061,
            longitude = 19.937,
            routeShortName = "52",
            vehicleLabel = "123",
            tripId = "trip-52-1"
        )

        val resolved = resolveMissingVehicleRouteShortNames(
            vehicles = listOf(vehicle),
            routeShortNamesByTripId = mapOf("trip-52-1" to "4")
        ).single()

        assertEquals("52", resolved.routeShortName)
    }

    @Test
    fun matchesBusLineIdToLineNameInTheCombinedPlannerFeed() {
        val position = mapVehiclePositions(
            busFeed(routeId = "126", fleetLabel = "147"),
            TransitVehicleMode.BUS,
            mapOf("160010" to "126")
        ).single()

        assertEquals("126", position.routeShortName)
        assertEquals("147", position.vehicleLabel)
    }

    @Test
    fun doesNotUseFleetNumbersOrUnknownIdsAsLineNumbers() {
        val position = mapVehiclePositions(
            busFeed(routeId = "unknown-route", fleetLabel = "126"),
            TransitVehicleMode.BUS,
            mapOf("160010" to "126")
        ).single()

        assertNull(position.routeShortName)
        assertEquals("126", position.vehicleLabel)
    }

    @Test
    fun doesNotTreatTramRouteIdsAsLineNumbers() {
        val position = mapVehiclePositions(
            busFeed(routeId = "126", fleetLabel = "147"),
            TransitVehicleMode.TRAM,
            mapOf("160010" to "126")
        ).single()

        assertNull(position.routeShortName)
    }

    private fun busFeed(routeId: String, fleetLabel: String): GtfsRealtime.FeedMessage =
        GtfsRealtime.FeedMessage.newBuilder()
            .setHeader(feedHeader())
            .addEntity(
                GtfsRealtime.FeedEntity.newBuilder()
                    .setId("vehicle_PA147")
                    .setVehicle(
                        GtfsRealtime.VehiclePosition.newBuilder()
                            .setTrip(
                                GtfsRealtime.TripDescriptor.newBuilder().setRouteId(routeId)
                            )
                            .setPosition(
                                GtfsRealtime.Position.newBuilder()
                                    .setLatitude(50.07589f)
                                    .setLongitude(19.84899f)
                            )
                            .setVehicle(
                                GtfsRealtime.VehicleDescriptor.newBuilder().setLabel(fleetLabel)
                            )
                    )
            )
            .build()

    private fun feedHeader() = GtfsRealtime.FeedHeader.newBuilder()
        .setGtfsRealtimeVersion("2.0")
        .build()
}
