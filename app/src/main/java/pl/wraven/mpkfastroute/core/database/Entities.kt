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

@Entity(tableName = "calendar")
data class ServiceCalendarEntity(
    @PrimaryKey val serviceId: String,
    val monday: Int,
    val tuesday: Int,
    val wednesday: Int,
    val thursday: Int,
    val friday: Int,
    val saturday: Int,
    val sunday: Int,
    val startDate: String,
    val endDate: String
)

@Entity(
    tableName = "calendar_dates",
    primaryKeys = ["serviceId", "date"],
    indices = [Index(value = ["date"])]
)
data class CalendarDateEntity(
    val serviceId: String,
    val date: String,
    val exceptionType: Int
)

@Entity(
    tableName = "stop_times",
    indices = [
        Index(value = ["stopId", "departureTime"]),
        Index(value = ["tripId", "stopSequence"])
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
