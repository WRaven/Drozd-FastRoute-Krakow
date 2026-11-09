package pl.wraven.mpkfastroute.feature.search

import pl.wraven.mpkfastroute.core.csa.JourneyTimeMode
import java.time.LocalDateTime

internal const val ROUTE_SEARCH_WINDOW_SECONDS = 2 * 60 * 60

internal data class RouteSearchWindow(
    val startDateTime: LocalDateTime,
    val endDateTime: LocalDateTime
)

internal fun routeSearchWindow(
    anchor: LocalDateTime,
    mode: JourneyTimeMode?
): RouteSearchWindow = if (mode == JourneyTimeMode.ARRIVE_BY) {
    RouteSearchWindow(
        startDateTime = anchor.minusSeconds(ROUTE_SEARCH_WINDOW_SECONDS.toLong()),
        endDateTime = anchor
    )
} else {
    RouteSearchWindow(
        startDateTime = anchor,
        endDateTime = anchor.plusSeconds(ROUTE_SEARCH_WINDOW_SECONDS.toLong())
    )
}
