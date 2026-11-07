package pl.wraven.mpkfastroute.core.live

import java.time.LocalDate
import javax.inject.Inject
import pl.wraven.mpkfastroute.core.database.AppDatabase
import pl.wraven.mpkfastroute.core.database.ShapePointEntity
import pl.wraven.mpkfastroute.core.database.StopEntity

data class ScheduledVehicleStop(
    val stop: StopEntity,
    val stopSequence: Int,
    val arrivalTime: Int,
    val departureTime: Int
)

data class VehicleTripSchedule(
    val tripId: String,
    val routeShortName: String,
    val direction: String,
    val serviceDate: LocalDate,
    val stops: List<ScheduledVehicleStop>,
    val shapePoints: List<ShapePointEntity>
)

class VehicleTripScheduleRepository @Inject constructor(
    private val database: AppDatabase
) {
    suspend fun getSchedule(
        tripId: String,
        stopsById: Map<String, StopEntity>,
        serviceDate: LocalDate
    ): VehicleTripSchedule {
        val trip = database.tripDao().getById(tripId)
            ?: throw IllegalStateException("The selected live trip is not in the GTFS schedule.")
        val route = database.routeDao().getById(trip.routeId)
            ?: throw IllegalStateException("The selected live trip has no matching GTFS route.")
        val stopTimes = database.stopTimeDao().getForTrip(tripId)
        if (stopTimes.isEmpty()) {
            throw IllegalStateException("The selected GTFS trip has no stop times.")
        }
        val scheduledStops = stopTimes.map { stopTime ->
            val stop = stopsById[stopTime.stopId]
                ?: throw IllegalStateException(
                    "GTFS stop ${stopTime.stopId} is missing from the loaded stop list."
                )
            ScheduledVehicleStop(
                stop = stop,
                stopSequence = stopTime.stopSequence,
                arrivalTime = stopTime.arrivalTime,
                departureTime = stopTime.departureTime
            )
        }
        val shapeId = trip.shapeId
        val shapePoints = if (shapeId == null) {
            emptyList()
        } else {
            database.shapePointDao().getForShape(shapeId)
        }

        return VehicleTripSchedule(
            tripId = trip.tripId,
            routeShortName = route.routeShortName,
            direction = trip.tripHeadsign
                ?.takeIf(String::isNotBlank)
                ?: route.routeLongName.takeIf(String::isNotBlank).orEmpty(),
            serviceDate = serviceDate,
            stops = scheduledStops,
            shapePoints = shapePoints
        )
    }
}
