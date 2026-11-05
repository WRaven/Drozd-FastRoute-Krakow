package pl.wraven.mpkfastroute.feature.search

import org.junit.Assert.assertEquals
import org.junit.Test
import pl.wraven.mpkfastroute.core.database.StopEntity

class StopNameGrouperTest {
    @Test
    fun removesOnlyNumericPlatformSuffix() {
        assertEquals("Plac Inwalidów", StopNameGrouper.mainName("Plac Inwalidów 02"))
        assertEquals("Dworzec Główny Zachód", StopNameGrouper.mainName("Dworzec Główny Zachód"))
    }

    @Test
    fun groupsPlatformsByMainStopNameAndKeepsConcreteStops() {
        val firstPlatform = stop("stop-1", "Plac Inwalidów 01")
        val secondPlatform = stop("stop-2", "Plac Inwalidów 02")
        val otherStop = stop("stop-3", "Teatr Bagatela 01")

        val groups = StopNameGrouper.group(listOf(firstPlatform, secondPlatform, otherStop))

        assertEquals(listOf("Plac Inwalidów", "Teatr Bagatela"), groups.map(StopGroup::name))
        assertEquals(
            listOf(firstPlatform, secondPlatform),
            groups.first().stops
        )
        assertEquals("01", StopNameGrouper.platformLabel(firstPlatform))
    }

    private fun stop(id: String, name: String) = StopEntity(
        stopId = id,
        stopCode = null,
        stopName = name,
        stopLat = 50.0,
        stopLon = 19.0
    )
}
