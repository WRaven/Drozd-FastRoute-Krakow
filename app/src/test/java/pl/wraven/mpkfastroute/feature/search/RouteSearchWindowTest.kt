package pl.wraven.mpkfastroute.feature.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.wraven.mpkfastroute.core.csa.JourneyTimeMode
import java.time.LocalDateTime

class RouteSearchWindowTest {
    private val anchor = LocalDateTime.of(2026, 11, 9, 7, 0)

    @Test
    fun earlierAndLaterPagesExcludeTheBoundaryAndOppositeSideDepartures() {
        val boundary = LocalDateTime.of(2026, 11, 9, 16, 18)

        assertTrue(
            isDepartureWithinPage(
                LocalDateTime.of(2026, 11, 9, 16, 17),
                boundary,
                RouteSearchDirection.EARLIER
            )
        )
        assertFalse(
            isDepartureWithinPage(
                boundary,
                boundary,
                RouteSearchDirection.EARLIER
            )
        )
        assertFalse(
            isDepartureWithinPage(
                LocalDateTime.of(2026, 11, 9, 16, 17),
                boundary,
                RouteSearchDirection.LATER
            )
        )
        assertTrue(
            isDepartureWithinPage(
                LocalDateTime.of(2026, 11, 9, 16, 19),
                boundary,
                RouteSearchDirection.LATER
            )
        )
    }

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

    @Test
    fun earlierPageStartsImmediatelyBeforeTheDisplayedBoundary() {
        val boundary = LocalDateTime.of(2026, 11, 9, 17, 10)

        val earlier = routeSearchPageWindow(boundary, RouteSearchDirection.EARLIER)

        assertEquals(boundary.minusMinutes(15), earlier.startDateTime)
        assertEquals(boundary, earlier.endDateTime)
    }

    @Test
    fun laterPageStartsAtTheDisplayedBoundaryWithoutSkippingTwoHours() {
        val boundary = LocalDateTime.of(2026, 11, 9, 17, 16)

        val later = routeSearchPageWindow(boundary, RouteSearchDirection.LATER)

        assertEquals(boundary, later.startDateTime)
        assertEquals(boundary.plusMinutes(15), later.endDateTime)
    }

    @Test
    fun pageWindowsAdvanceWithoutGapsInEitherDirection() {
        val boundary = LocalDateTime.of(2026, 11, 9, 17, 0)
        val firstEarlier = routeSearchPageWindow(boundary, RouteSearchDirection.EARLIER)
        val firstLater = routeSearchPageWindow(boundary, RouteSearchDirection.LATER)

        val nextEarlier = adjacentRouteSearchWindow(
            firstEarlier,
            RouteSearchDirection.EARLIER,
            ROUTE_SEARCH_PAGE_WINDOW_SECONDS
        )
        val nextLater = adjacentRouteSearchWindow(
            firstLater,
            RouteSearchDirection.LATER,
            ROUTE_SEARCH_PAGE_WINDOW_SECONDS
        )

        assertEquals(firstEarlier.startDateTime, nextEarlier.endDateTime)
        assertEquals(firstLater.endDateTime, nextLater.startDateTime)
    }
}
