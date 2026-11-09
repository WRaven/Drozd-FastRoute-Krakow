package pl.wraven.mpkfastroute.feature.search

import pl.wraven.mpkfastroute.core.csa.JourneyTimeMode
import java.time.LocalDateTime

internal const val ROUTE_SEARCH_WINDOW_SECONDS = 2 * 60 * 60
internal const val ROUTE_SEARCH_PAGE_SIZE = 4
internal const val MAX_ROUTE_SEARCH_WINDOWS_PER_PAGE = 12

internal data class RouteSearchWindow(
    val startDateTime: LocalDateTime,
    val endDateTime: LocalDateTime
)

internal enum class RouteSearchDirection {
    EARLIER,
    LATER
}

internal fun isDepartureWithinPage(
    departureDateTime: LocalDateTime,
    boundary: LocalDateTime?,
    direction: RouteSearchDirection?
): Boolean = when (direction) {
    RouteSearchDirection.EARLIER ->
        boundary == null || departureDateTime.isBefore(boundary)
    RouteSearchDirection.LATER ->
        boundary == null || departureDateTime.isAfter(boundary)
    null -> true
}

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

internal fun adjacentRouteSearchWindow(
    window: RouteSearchWindow,
    direction: RouteSearchDirection
): RouteSearchWindow = when (direction) {
    RouteSearchDirection.EARLIER -> RouteSearchWindow(
        startDateTime = window.startDateTime.minusSeconds(ROUTE_SEARCH_WINDOW_SECONDS.toLong()),
        endDateTime = window.startDateTime
    )
    RouteSearchDirection.LATER -> RouteSearchWindow(
        startDateTime = window.endDateTime,
        endDateTime = window.endDateTime.plusSeconds(ROUTE_SEARCH_WINDOW_SECONDS.toLong())
    )
}
