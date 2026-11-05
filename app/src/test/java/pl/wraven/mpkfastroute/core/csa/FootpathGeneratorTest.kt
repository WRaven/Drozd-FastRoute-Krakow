package pl.wraven.mpkfastroute.core.csa

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.wraven.mpkfastroute.core.database.StopEntity

class FootpathGeneratorTest {
    private val generator = FootpathGenerator()

    @Test
    fun generatesBidirectionalFootpathsForStopsWithin150Meters() {
        val stops = listOf(
            stop("A", latitude = 50.0, longitude = 19.0),
            stop("B", latitude = 50.0005, longitude = 19.0),
            stop("C", latitude = 50.002, longitude = 19.0)
        )

        val paths = generator.generate(stops)

        assertTrue(paths.any { it.fromStopId == "A" && it.toStopId == "B" })
        assertTrue(paths.any { it.fromStopId == "B" && it.toStopId == "A" })
        assertTrue(paths.none { it.fromStopId == "A" && it.toStopId == "C" })
    }

    @Test
    fun usesWalkingSpeedOfFourPointFiveKilometersPerHour() {
        val path = generator.generate(
            listOf(
                stop("A", latitude = 50.0, longitude = 19.0),
                stop("B", latitude = 50.0005, longitude = 19.0)
            )
        ).first()

        assertEquals(45, path.durationSeconds)
    }

    private fun stop(id: String, latitude: Double, longitude: Double) = StopEntity(
        stopId = id,
        stopCode = null,
        stopName = id,
        stopLat = latitude,
        stopLon = longitude
    )
}
