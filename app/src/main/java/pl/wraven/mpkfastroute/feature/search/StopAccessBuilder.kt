package pl.wraven.mpkfastroute.feature.search

import pl.wraven.mpkfastroute.core.csa.FootpathGenerator
import pl.wraven.mpkfastroute.core.csa.Haversine
import pl.wraven.mpkfastroute.core.csa.StopAccess
import pl.wraven.mpkfastroute.core.database.StopEntity
import pl.wraven.mpkfastroute.core.location.GeoPlace

internal const val MAX_NEARBY_STOPS = 6
internal const val MAX_AUTO_STOP_ACCESS_METERS = 1_500.0

internal fun nearbyStopGroups(stops: List<StopEntity>, place: GeoPlace): List<StopGroup> =
    StopNameGrouper.group(stops)
        .sortedBy { group ->
            group.stops.minOf { stop ->
                Haversine.distanceMeters(
                    place.latitude,
                    place.longitude,
                    stop.stopLat,
                    stop.stopLon
                )
            }
        }
        .take(MAX_NEARBY_STOPS)

internal fun buildStopAccesses(
    place: GeoPlace,
    selectedStops: List<StopEntity>,
    nearbyGroups: List<StopGroup>,
    allStops: List<StopEntity>,
    manuallySelected: Boolean
): List<StopAccess> {
    val candidates = if (manuallySelected) {
        selectedStops
    } else {
        val groups = nearbyGroups.ifEmpty { nearbyStopGroups(allStops, place) }
        groups.flatMap(StopGroup::stops)
    }

    return candidates.distinctBy(StopEntity::stopId).mapNotNull { stop ->
        val access = stop.toStopAccessFrom(place)
        if (!manuallySelected && access.walkingDistanceMeters > MAX_AUTO_STOP_ACCESS_METERS) {
            return@mapNotNull null
        }
        access
    }
}

internal fun StopEntity.toStopAccessFrom(place: GeoPlace): StopAccess {
    val distanceMeters = Haversine.distanceMeters(
        place.latitude,
        place.longitude,
        stopLat,
        stopLon
    )
    return StopAccess(
        stopId = stopId,
        walkingDistanceMeters = distanceMeters,
        walkingDurationSeconds = FootpathGenerator.walkingDurationSeconds(distanceMeters)
    )
}
