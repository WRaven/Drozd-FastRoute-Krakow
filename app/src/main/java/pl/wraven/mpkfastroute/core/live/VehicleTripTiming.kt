package pl.wraven.mpkfastroute.core.live

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import pl.wraven.mpkfastroute.core.csa.Haversine

val KRAKOW_ZONE: ZoneId = ZoneId.of("Europe/Warsaw")

data class VehicleStopTiming(
    val scheduledStop: ScheduledVehicleStop,
    val scheduledArrival: Instant,
    val predictedArrival: Instant?,
    val isPassed: Boolean,
    val isCurrent: Boolean,
    val isSkipped: Boolean
)

fun gtfsInstant(date: LocalDate, timeSeconds: Int): Instant =
    date.atTime(12, 0).atZone(KRAKOW_ZONE).minusHours(12).toInstant().plusSeconds(timeSeconds.toLong())

fun upcomingVehicleStopIndex(vehicle: LiveVehiclePosition, schedule: VehicleTripSchedule): Int {
    vehicle.currentStopSequence?.let { sequence ->
        schedule.stops.indexOfFirst { it.stopSequence >= sequence }.takeIf { it >= 0 }?.let { return it }
    }
    vehicle.currentStopId?.let { id ->
        schedule.stops.indexOfFirst { it.stop.stopId == id }.takeIf { it >= 0 }?.let { return it }
    }
    return schedule.stops.indices.minByOrNull { index ->
        val stop = schedule.stops[index].stop
        Haversine.distanceMeters(vehicle.latitude, vehicle.longitude, stop.stopLat, stop.stopLon)
    } ?: 0
}

fun vehicleStopTimings(
    vehicle: LiveVehiclePosition,
    schedule: VehicleTripSchedule,
    update: LiveTripUpdate?
): List<VehicleStopTiming> {
    val nextIndex = upcomingVehicleStopIndex(vehicle, schedule)
    val tripUpdate = update?.takeIf {
        it.tripId == schedule.tripId && (it.serviceDate == null || it.serviceDate == schedule.serviceDate)
    }
    var propagatedDelay = tripUpdate?.tripDelaySeconds?.toLong()
    return schedule.stops.mapIndexed { index, stop ->
        val arrival = gtfsInstant(schedule.serviceDate, stop.arrivalTime)
        val departure = gtfsInstant(schedule.serviceDate, stop.departureTime)
        val stopUpdate = tripUpdate?.stopTimeUpdates?.firstOrNull {
            if (it.stopSequence != null) it.stopSequence == stop.stopSequence
            else it.stopId == stop.stop.stopId
        }
        val arrivalDelay = stopUpdate?.arrivalTimeEpochSeconds?.let {
            Duration.between(arrival, Instant.ofEpochSecond(it)).seconds
        } ?: stopUpdate?.arrivalDelaySeconds?.toLong()
        val departureDelay = stopUpdate?.departureTimeEpochSeconds?.let {
            Duration.between(departure, Instant.ofEpochSecond(it)).seconds
        } ?: stopUpdate?.departureDelaySeconds?.toLong()
        val noPrediction = stopUpdate?.hasNoData == true || tripUpdate?.isCanceled == true
        val delay = if (noPrediction) null else arrivalDelay ?: departureDelay ?: propagatedDelay
        propagatedDelay = when {
            noPrediction -> null
            stopUpdate?.isSkipped == true -> propagatedDelay
            else -> departureDelay ?: arrivalDelay ?: propagatedDelay
        }
        VehicleStopTiming(stop, arrival,
            delay?.takeIf { stopUpdate?.isSkipped != true }?.let(arrival::plusSeconds),
            index < nextIndex, index == nextIndex, stopUpdate?.isSkipped == true)
    }
}

fun minutesUntil(target: Instant, now: Instant): Long =
    Math.floorDiv(Duration.between(now, target).seconds + 59, 60)

fun isLiveTripUpdateFresh(update: LiveTripUpdate, now: Instant): Boolean {
    val timestamp = update.sourceTimestamp
        ?: update.fetchedAt.atZone(ZoneId.systemDefault()).toInstant()
    return Duration.between(timestamp, now).seconds in 0..90
}
