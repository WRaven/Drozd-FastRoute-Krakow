package pl.wraven.mpkfastroute.core.live

import java.time.DayOfWeek
import java.time.LocalDate
import javax.inject.Inject
import androidx.room.withTransaction
import pl.wraven.mpkfastroute.core.database.AppDatabase
import pl.wraven.mpkfastroute.core.database.CalendarDateEntity
import pl.wraven.mpkfastroute.core.database.GtfsDepartureRow
import pl.wraven.mpkfastroute.core.database.ServiceCalendarEntity
import pl.wraven.mpkfastroute.core.gtfs.ServiceCalendarResolver

enum class TimetableDayType(val label: String) {
    WORKDAY("Dni robocze"), SATURDAY("Sobota"), SUNDAY_HOLIDAY("Niedziela i święta")
}

data class TimetableLine(val routeShortName: String, val direction: String)
data class TimetableDay(val date: LocalDate?, val departures: List<StaticDeparture>)
data class StopTimetable(val lines: List<TimetableLine>, val days: Map<TimetableDayType, TimetableDay>)
data class TimetableHour(val hour: Int, val minutes: List<Int>)

class StopTimetableRepository @Inject constructor(private val database: AppDatabase) {
    suspend fun getTimetable(stopId: String, today: LocalDate): StopTimetable {
        return database.withTransaction {
            check(database.stopDao().getById(stopId) != null) {
                "Wybrany przystanek nie występuje już w bazie rozkładu."
            }
            val rows = database.stopTimeDao().getTimetableAtStop(stopId)
            val calendar = database.serviceCalendarDao()
            buildStopTimetable(rows, calendar.getAll(), calendar.getAllExceptions(), today)
        }
    }
}

internal fun buildStopTimetable(
    rows: List<GtfsDepartureRow>,
    calendars: List<ServiceCalendarEntity>,
    exceptions: List<CalendarDateEntity>,
    today: LocalDate
): StopTimetable {
    val servicesAtStop = rows.mapTo(mutableSetOf(), GtfsDepartureRow::serviceId)
    val days = TimetableDayType.entries.associateWith { type ->
        val date = (0L..35L).asSequence().map(today::plusDays)
            .filter { timetableDayType(it) == type }
            .firstOrNull { date ->
                ServiceCalendarResolver.activeServiceIds(date, calendars, exceptions)
                    .any { it in servicesAtStop }
            }
        val activeServices = date?.let {
            ServiceCalendarResolver.activeServiceIds(it, calendars, exceptions)
        }.orEmpty()
        TimetableDay(date, rows.filter { it.serviceId in activeServices }.map {
            StaticDeparture(it.routeShortName, it.direction.orEmpty(), it.departureTime)
        })
    }
    val lines = days.values.flatMap { it.departures }
        .map { TimetableLine(it.routeShortName, it.direction) }.distinct()
        .sortedWith(compareBy<TimetableLine> { it.routeShortName.toIntOrNull() ?: Int.MAX_VALUE }
            .thenBy { it.routeShortName }.thenBy { it.direction })
    return StopTimetable(lines, days)
}

fun timetableHours(day: TimetableDay?, line: TimetableLine?): List<TimetableHour> {
    if (line == null) return emptyList()
    return day?.departures.orEmpty()
        .filter { it.routeShortName == line.routeShortName && it.direction == line.direction }
        .map { it.departureTime }.distinct().sorted()
        .groupBy { it / 3_600 }
        .map { (hour, times) -> TimetableHour(hour, times.map { it / 60 % 60 }.distinct()) }
}

internal fun timetableDayType(date: LocalDate): TimetableDayType = when {
    isPolishPublicHoliday(date) || date.dayOfWeek == DayOfWeek.SUNDAY -> TimetableDayType.SUNDAY_HOLIDAY
    date.dayOfWeek == DayOfWeek.SATURDAY -> TimetableDayType.SATURDAY
    else -> TimetableDayType.WORKDAY
}

internal fun isPolishPublicHoliday(date: LocalDate): Boolean {
    val fixed = setOf(1 to 1, 1 to 6, 5 to 1, 5 to 3, 8 to 15, 11 to 1, 11 to 11, 12 to 25, 12 to 26)
    if ((date.monthValue to date.dayOfMonth) in fixed ||
        date.year >= 2025 && date.monthValue == 12 && date.dayOfMonth == 24
    ) return true
    // Gregorian computus; movable public holidays are offsets from Easter.
    val year = date.year
    val a = year % 19
    val b = year / 100
    val c = year % 100
    val d = b / 4
    val e = b % 4
    val f = (b + 8) / 25
    val g = (b - f + 1) / 3
    val h = (19 * a + b - d - g + 15) % 30
    val i = c / 4
    val k = c % 4
    val l = (32 + 2 * e + 2 * i - h - k) % 7
    val m = (a + 11 * h + 22 * l) / 451
    val easter = LocalDate.of(year, (h + l - 7 * m + 114) / 31, (h + l - 7 * m + 114) % 31 + 1)
    return date == easter || date == easter.plusDays(1) || date == easter.plusDays(49) ||
        date == easter.plusDays(60)
}
