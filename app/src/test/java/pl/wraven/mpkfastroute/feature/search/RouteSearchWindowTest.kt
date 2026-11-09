package pl.wraven.mpkfastroute.feature.search

import org.junit.Assert.assertEquals
import org.junit.Test
import pl.wraven.mpkfastroute.core.csa.JourneyTimeMode
import java.time.LocalDateTime

class RouteSearchWindowTest {
    private val anchor = LocalDateTime.of(2026, 11, 9, 7, 0)

    @Test
    fun departureAfterWindowStartsAtAnchorAndSpansTwoHours() {
        val window = routeSearchWindow(anchor, JourneyTimeMode.DEPART_AFTER)

        assertEquals(anchor, window.startDateTime)
        assertEquals(anchor.plusHours(2), window.endDateTime)
    }

    @Test
    fun arriveByWindowEndsAtAnchorAndSpansTwoHours() {
        val window = routeSearchWindow(anchor, JourneyTimeMode.ARRIVE_BY)

        assertEquals(anchor.minusHours(2), window.startDateTime)
        assertEquals(anchor, window.endDateTime)
    }

    @Test
    fun earlierWindowJoinsAtCurrentWindowStart() {
        val current = routeSearchWindow(anchor, JourneyTimeMode.DEPART_AFTER)

        val earlier = adjacentRouteSearchWindow(current, RouteSearchDirection.EARLIER)

        assertEquals(current.startDateTime.minusHours(2), earlier.startDateTime)
        assertEquals(current.startDateTime, earlier.endDateTime)
    }

    @Test
    fun laterWindowJoinsAtCurrentWindowEnd() {
        val current = routeSearchWindow(anchor, JourneyTimeMode.DEPART_AFTER)

        val later = adjacentRouteSearchWindow(current, RouteSearchDirection.LATER)

        assertEquals(current.endDateTime, later.startDateTime)
        assertEquals(current.endDateTime.plusHours(2), later.endDateTime)
    }
}
