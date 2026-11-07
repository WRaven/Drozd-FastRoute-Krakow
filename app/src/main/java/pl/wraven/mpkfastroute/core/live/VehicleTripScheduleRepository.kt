package pl.wraven.mpkfastroute.core.live

import java.io.File
import java.io.IOException
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.Duration
import java.time.DateTimeException
import java.time.format.DateTimeFormatter
import java.util.zip.ZipFile
import javax.inject.Inject
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import pl.wraven.mpkfastroute.core.database.ShapePointEntity
import pl.wraven.mpkfastroute.core.database.StopEntity
import pl.wraven.mpkfastroute.core.database.CalendarDateEntity
import pl.wraven.mpkfastroute.core.database.ServiceCalendarEntity
import pl.wraven.mpkfastroute.core.gtfs.ServiceCalendarResolver
import pl.wraven.mpkfastroute.core.gtfs.parseGtfsTime
import pl.wraven.mpkfastroute.core.gtfs.readCsv
import pl.wraven.mpkfastroute.core.gtfs.required

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
    private val archives: ZtpGtfsArchiveRepository
) {
    suspend fun getSchedule(
        vehicle: LiveVehiclePosition,
        now: LocalDateTime
    ): VehicleTripSchedule {
        try {
            return archives.withArchive(vehicle.mode) { parseVehicleTripSchedule(it, vehicle, now) }
        } catch (_: LiveTripMissingException) {
            return archives.withArchive(vehicle.mode, forceRefresh = true) {
                parseVehicleTripSchedule(it, vehicle, now)
            }
        }
    }
}

private class LiveTripMissingException(tripId: String) :
    IOException("Kurs $tripId nie występuje w aktualnym rozkładzie Live ZTP.")

internal suspend fun parseVehicleTripSchedule(
    archive: File,
    vehicle: LiveVehiclePosition,
    now: LocalDateTime
): VehicleTripSchedule = ZipFile(archive).use { zip ->
    val tripId = vehicle.tripId?.takeIf(String::isNotBlank)
        ?: throw IOException("ZTP nie przekazał identyfikatora kursu.")
    val context = currentCoroutineContext()
    suspend fun table(name: String, optional: Boolean = false, record: suspend (Map<String, Int>, List<String>, Int) -> Unit) {
        val entry = zip.entries().asSequence().firstOrNull { it.name.substringAfterLast('/') == name }
        if (entry == null) {
            if (optional) return
            throw IOException("Rozkład Live nie zawiera $name.")
        }
        zip.getInputStream(entry).use { input ->
            readCsv(input, name) { columns, values, row ->
                context.ensureActive()
                record(columns, values, row)
            }
        }
    }
    var routeId: String? = null
    var serviceId: String? = null
    var headsign: String? = null
    var shapeId: String? = null
    table("trips.txt") { columns, values, row ->
        if (values.required(columns, "trip_id", "trips.txt", row) == tripId) {
            routeId = values.required(columns, "route_id", "trips.txt", row)
            serviceId = values.required(columns, "service_id", "trips.txt", row)
            headsign = columns["trip_headsign"]?.let(values::get)?.takeIf(String::isNotBlank)
            shapeId = columns["shape_id"]?.let(values::get)?.takeIf(String::isNotBlank)
        }
    }
    if (routeId == null) throw LiveTripMissingException(tripId)
    var routeName: String? = null
    table("routes.txt") { columns, values, row ->
        if (values.required(columns, "route_id", "routes.txt", row) == routeId) {
            routeName = values.required(columns, "route_short_name", "routes.txt", row)
        }
    }
    val stopMap = mutableMapOf<String, StopEntity>()
    table("stops.txt") { columns, values, row ->
        fun field(name: String) = values.required(columns, name, "stops.txt", row)
        val latitude = field("stop_lat").toDoubleOrNull()
        val longitude = field("stop_lon").toDoubleOrNull()
        if (latitude == null || longitude == null || !latitude.isFinite() || !longitude.isFinite() ||
            latitude !in -90.0..90.0 || longitude !in -180.0..180.0
        ) throw IOException("Nieprawidłowe współrzędne przystanku Live w wierszu $row.")
        val id = field("stop_id")
        stopMap[id] = StopEntity(id, columns["stop_code"]?.let(values::get), field("stop_name"), latitude, longitude)
    }
    val stops = mutableListOf<ScheduledVehicleStop>()
    table("stop_times.txt") { columns, values, row ->
        fun field(name: String) = values.required(columns, name, "stop_times.txt", row)
        if (field("trip_id") == tripId) {
            val stop = stopMap[field("stop_id")] ?: throw IOException("Brak przystanku kursu Live w wierszu $row.")
            val arrival = columns["arrival_time"]?.let(values::get)?.takeIf(String::isNotBlank)
            val departure = columns["departure_time"]?.let(values::get)?.takeIf(String::isNotBlank)
            try {
                stops += ScheduledVehicleStop(
                    stop,
                    field("stop_sequence").toInt(),
                    parseGtfsTime(arrival ?: departure ?: throw IOException("Brak czasu kursu Live.")),
                    parseGtfsTime(departure ?: arrival ?: throw IOException("Brak czasu kursu Live."))
                )
            } catch (exception: IllegalArgumentException) {
                throw IOException("Nieprawidłowy czas lub kolejność kursu Live w wierszu $row.", exception)
            }
        }
    }
    stops.sortBy(ScheduledVehicleStop::stopSequence)
    if (stops.isEmpty() || routeName == null) throw IOException("Niekompletny rozkład kursu Live $tripId.")
    if (stops.map { it.stopSequence }.distinct().size != stops.size ||
        stops.any { it.stopSequence <= 0 || it.arrivalTime > it.departureTime }
    ) throw IOException("Nieprawidłowa kolejność lub czasy przystanków kursu Live $tripId.")
    val shapes = mutableListOf<ShapePointEntity>()
    if (shapeId != null) {
        table("shapes.txt", optional = true) { columns, values, row ->
            fun field(name: String) = values.required(columns, name, "shapes.txt", row)
            if (field("shape_id") == shapeId) {
                val latitude = field("shape_pt_lat").toDoubleOrNull()
                val longitude = field("shape_pt_lon").toDoubleOrNull()
                val sequence = field("shape_pt_sequence").toIntOrNull()
                if (latitude == null || longitude == null || sequence == null ||
                    !latitude.isFinite() || !longitude.isFinite() ||
                    latitude !in -90.0..90.0 || longitude !in -180.0..180.0
                ) throw IOException("Nieprawidłowa geometria kursu Live w wierszu $row.")
                shapes += ShapePointEntity(field("shape_id"), sequence, latitude, longitude)
            }
        }
    }
    val calendars = mutableListOf<ServiceCalendarEntity>()
    table("calendar.txt", optional = true) { columns, values, row ->
        fun field(name: String) = values.required(columns, name, "calendar.txt", row)
        if (field("service_id") == serviceId) {
            val flags = listOf("monday", "tuesday", "wednesday", "thursday", "friday", "saturday", "sunday")
                .map { field(it).toIntOrNull()?.takeIf { flag -> flag in 0..1 }
                    ?: throw IOException("Nieprawidłowy kalendarz kursu Live.") }
            calendars += ServiceCalendarEntity(field("service_id"), flags[0], flags[1], flags[2],
                flags[3], flags[4], flags[5], flags[6], field("start_date"), field("end_date"))
        }
    }
    val exceptions = mutableListOf<CalendarDateEntity>()
    table("calendar_dates.txt", optional = true) { columns, values, row ->
        fun field(name: String) = values.required(columns, name, "calendar_dates.txt", row)
        if (field("service_id") == serviceId) {
            val type = field("exception_type").toIntOrNull()?.takeIf { it in 1..2 }
                ?: throw IOException("Nieprawidłowy wyjątek kalendarza Live.")
            val date = field("date")
            try {
                LocalDate.parse(date, DateTimeFormatter.BASIC_ISO_DATE)
            } catch (exception: DateTimeException) {
                throw IOException("Nieprawidłowa data kursu Live.", exception)
            }
            exceptions += CalendarDateEntity(field("service_id"), date, type)
        }
    }
    val candidates = vehicle.serviceDate?.let(::listOf)
        ?: listOf(now.toLocalDate().minusDays(1), now.toLocalDate())
    val serviceDate = candidates
        .filter { serviceId in ServiceCalendarResolver.activeServiceIds(it, calendars, exceptions) }
        .minByOrNull { date ->
            val start = date.atStartOfDay().plusSeconds(stops.first().departureTime.toLong())
            val end = date.atStartOfDay().plusSeconds(stops.last().arrivalTime.toLong())
            when {
                now.isBefore(start) -> Duration.between(now, start).seconds
                now.isAfter(end) -> Duration.between(end, now).seconds
                else -> 0L
            }
        } ?: throw LiveTripMissingException(tripId)
    VehicleTripSchedule(
        tripId, routeName ?: throw IOException("Brak numeru linii Live."),
        headsign ?: stops.last().stop.stopName, serviceDate, stops,
        shapes.sortedBy(ShapePointEntity::shapePtSequence)
    )
}
