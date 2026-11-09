package pl.wraven.mpkfastroute.feature.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.wraven.mpkfastroute.core.csa.FootpathGenerator
import pl.wraven.mpkfastroute.core.database.StopEntity
import pl.wraven.mpkfastroute.core.location.GeoPlace

class StopAccessBuilderTest {
    @Test
    fun usesAddressCoordinatesToCalculateWalkingAccess() {
        val place = GeoPlace("Home", 50.0, 20.0)
        val stop = StopEntity(
            stopId = "stop",
            stopCode = null,
            stopName = "Nearby stop",
            stopLat = 50.001,
            stopLon = 20.0
        )

        val access = stop.toStopAccessFrom(place)

        assertTrue(access.walkingDistanceMeters > 100.0)
        assertEquals(
            FootpathGenerator.walkingDurationSeconds(access.walkingDistanceMeters),
            access.walkingDurationSeconds
        )
    }

    @Test
    fun zeroDistanceIsUsedOnlyWhenStopMatchesPlaceCoordinates() {
        val place = GeoPlace("Stop address", 50.0, 20.0)
        val stop = StopEntity(
            stopId = "stop",
            stopCode = null,
            stopName = "Same place",
            stopLat = 50.0,
            stopLon = 20.0
        )

        val access = stop.toStopAccessFrom(place)

        assertEquals(0.0, access.walkingDistanceMeters, 0.0)
        assertEquals(0, access.walkingDurationSeconds)
    }

    @Test
    fun automaticAccessesKeepTheAddressDistanceForNearbyStopGroups() {
        val place = GeoPlace("Home", 50.0, 20.0)
        val stops = listOf(
            StopEntity("near", null, "Nearby", 50.001, 20.0),
            StopEntity("farther", null, "Farther", 50.002, 20.0)
        )
        val groups = nearbyStopGroups(stops, place)

        val accesses = buildStopAccesses(
            place = place,
            selectedStops = emptyList(),
            nearbyGroups = groups,
            allStops = stops,
            manuallySelected = false
        )

        assertEquals(setOf("near", "farther"), accesses.map { it.stopId }.toSet())
        assertTrue(accesses.all { it.walkingDistanceMeters > 0.0 })
    }
}
