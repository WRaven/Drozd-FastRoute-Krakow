package pl.wraven.mpkfastroute.core.live

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.wraven.mpkfastroute.core.database.StopEntity

class TtssLiveMapperTest {
    private val mapper = TtssLiveMapper()

    @Test
    fun usesStopCodeBeforeNameMapping() {
        val stop = stop(stopCode = "1234", stopName = "Dworzec Główny")

        assertEquals(
            "1234",
            mapper.resolveStopId(stop, mapOf("Dworzec Główny" to "5678"))
        )
    }

    @Test
    fun fallsBackToNormalizedStopNameMapping() {
        val stop = stop(stopCode = null, stopName = "Dworzec Główny")

        assertEquals(
            "5678",
            mapper.resolveStopId(stop, mapOf("dworzec główny" to "5678"))
        )
    }

    @Test
    fun addsLiveDelayToStaticDepartureTime() {
        val result = mapper.mapDepartures(
            staticDepartures = listOf(
                StaticDeparture(
                    routeShortName = "4",
                    direction = "Krowodrza Górka",
                    departureTime = 36_000
                )
            ),
            passages = listOf(
                TtssDeparture(
                    routeShortName = "4",
                    direction = "Krowodrza Górka",
                    status = "PREDICTED",
                    actualRelativeTime = 7_500,
                    plannedRelativeTime = 7_200
                )
            ),
            nowTimeSeconds = 28_800
        )

        assertEquals(36_300, result.departures.single().estimatedDepartureTime)
        assertEquals(300, result.departures.single().delaySeconds)
        assertTrue(result.departures.single().isLive)
        assertNull(result.fallbackReason)
    }

    @Test
    fun keepsStaticEstimateWhenNoLivePassageMatches() {
        val result = mapper.mapDepartures(
            staticDepartures = listOf(
                StaticDeparture("4", "Krowodrza Górka", 36_000)
            ),
            passages = emptyList(),
            nowTimeSeconds = 28_800
        )

        assertEquals(36_000, result.departures.single().estimatedDepartureTime)
        assertNull(result.departures.single().delaySeconds)
        assertFalse(result.departures.single().isLive)
        assertTrue(result.fallbackReason != null)
    }

    private fun stop(stopCode: String?, stopName: String) = StopEntity(
        stopId = "gtfs-id",
        stopCode = stopCode,
        stopName = stopName,
        stopLat = 50.0,
        stopLon = 19.0
    )
}
