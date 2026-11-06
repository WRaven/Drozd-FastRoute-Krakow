package pl.wraven.mpkfastroute.core.csa

import javax.inject.Inject
import pl.wraven.mpkfastroute.core.database.StopEntity
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.atan2

class FootpathGenerator @Inject constructor() {
    fun generate(stops: List<StopEntity>): List<Footpath> {
        if (stops.size < 2) {
            return emptyList()
        }

        val averageLatitude = stops.sumOf(StopEntity::stopLat) / stops.size
        val latitudeCellSize = MAX_DISTANCE_METERS / METERS_PER_LATITUDE_DEGREE
        val longitudeCellSize = latitudeCellSize /
            abs(cos(Math.toRadians(averageLatitude))).coerceAtLeast(MIN_LONGITUDE_SCALE)
        val buckets = mutableMapOf<GridCell, MutableList<StopEntity>>()
        val result = mutableListOf<Footpath>()

        for (stop in stops) {
            val cell = cellFor(stop, latitudeCellSize, longitudeCellSize)
            for (latitudeOffset in -1..1) {
                for (longitudeOffset in -1..1) {
                    val neighborCell = GridCell(
                        latitude = cell.latitude + latitudeOffset,
                        longitude = cell.longitude + longitudeOffset
                    )
                    for (neighbor in buckets[neighborCell].orEmpty()) {
                        val distance = Haversine.distanceMeters(stop, neighbor)
                        if (distance <= MAX_DISTANCE_METERS) {
                            val duration = walkingDurationSeconds(distance)
                            result += Footpath(stop.stopId, neighbor.stopId, duration, distance)
                            result += Footpath(neighbor.stopId, stop.stopId, duration, distance)
                        }
                    }
                }
            }
            buckets.getOrPut(cell) { mutableListOf() } += stop
        }

        return result
    }

    private fun cellFor(
        stop: StopEntity,
        latitudeCellSize: Double,
        longitudeCellSize: Double
    ): GridCell = GridCell(
        latitude = floor(stop.stopLat / latitudeCellSize).toInt(),
        longitude = floor(stop.stopLon / longitudeCellSize).toInt()
    )

    private data class GridCell(val latitude: Int, val longitude: Int)

    companion object {
        const val MAX_DISTANCE_METERS = 500.0
        const val METERS_PER_LATITUDE_DEGREE = 110_000.0
        const val WALK_SPEED_METERS_PER_SECOND = 1.25
        const val MIN_LONGITUDE_SCALE = 0.01

        fun walkingDurationSeconds(distanceMeters: Double): Int {
            require(distanceMeters.isFinite() && distanceMeters >= 0.0) {
                "Walking distance must be finite and non-negative."
            }
            return ceil(distanceMeters / WALK_SPEED_METERS_PER_SECOND).toInt()
        }
    }
}

object Haversine {
    fun distanceMeters(first: StopEntity, second: StopEntity): Double =
        distanceMeters(first.stopLat, first.stopLon, second.stopLat, second.stopLon)

    fun distanceMeters(latitude: Double, longitude: Double, stop: StopEntity): Double =
        distanceMeters(latitude, longitude, stop.stopLat, stop.stopLon)

    fun distanceMeters(
        firstLatitudeDegrees: Double,
        firstLongitudeDegrees: Double,
        secondLatitudeDegrees: Double,
        secondLongitudeDegrees: Double
    ): Double {
        val latitudeDifference = Math.toRadians(secondLatitudeDegrees - firstLatitudeDegrees)
        val longitudeDifference = Math.toRadians(secondLongitudeDegrees - firstLongitudeDegrees)
        val firstLatitude = Math.toRadians(firstLatitudeDegrees)
        val secondLatitude = Math.toRadians(secondLatitudeDegrees)
        val haversine = sin(latitudeDifference / 2) * sin(latitudeDifference / 2) +
            cos(firstLatitude) * cos(secondLatitude) *
            sin(longitudeDifference / 2) * sin(longitudeDifference / 2)
        val centralAngle = 2 * atan2(sqrt(haversine.coerceIn(0.0, 1.0)), sqrt((1 - haversine).coerceIn(0.0, 1.0)))
        return EARTH_RADIUS_METERS * centralAngle
    }

    private const val EARTH_RADIUS_METERS = 6_371_000.0
}
