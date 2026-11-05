package pl.wraven.mpkfastroute.core.database

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "stops",
    indices = [Index(value = ["stopCode"])]
)
data class StopEntity(
    @PrimaryKey val stopId: String,
    val stopCode: String?,
    val stopName: String,
    val stopLat: Double,
    val stopLon: Double
)

@Entity(tableName = "routes")
data class RouteEntity(
    @PrimaryKey val routeId: String,
    val routeShortName: String,
    val routeLongName: String,
    val routeType: Int
)

@Entity(tableName = "trips")
data class TripEntity(
    @PrimaryKey val tripId: String,
    val routeId: String,
    val serviceId: String,
    val tripHeadsign: String?
)

@Entity(
    tableName = "stop_times",
    indices = [
        Index(value = ["stopId", "departureTime"]),
        Index(value = ["tripId"])
    ]
)
data class StopTimeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val tripId: String,
    val arrivalTime: Int,
    val departureTime: Int,
    val stopId: String,
    val stopSequence: Int
)
