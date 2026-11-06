package pl.wraven.mpkfastroute.core.csa

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.wraven.mpkfastroute.core.database.StopEntity

class FootpathGeneratorTest {
    private val generator = FootpathGenerator()

    @Test
    fun generatesBidirectionalFootpathsForStopsWithin500Meters() {
        val stops = listOf(
            stop("A", latitude = 50.0, longitude = 19.0),
            stop("B", latitude = 50.0005, longitude = 19.0),
            stop("C", latitude = 50.006, longitude = 19.0)
        )

        val paths = generator.generate(stops)

        assertTrue(paths.any { it.fromStopId == "A" && it.toStopId == "B" })
        assertTrue(paths.any { it.fromStopId == "B" && it.toStopId == "A" })
        assertTrue(paths.none { it.fromStopId == "A" && it.toStopId == "C" })
    }

    @Test
    fun generatesTimedFootpathAcrossAQuarterKilometerInterchange() {
        val paths = generator.generate(
            listOf(
                stop("A", latitude = 50.0, longitude = 19.0),
                stop("B", latitude = 50.0027, longitude = 19.0)
            )
        )

        val path = paths.first { it.fromStopId == "A" && it.toStopId == "B" }
        assertEquals(241, path.durationSeconds)
        assertTrue(path.distanceMeters in 290.0..310.0)
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
