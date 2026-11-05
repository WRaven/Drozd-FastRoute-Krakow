package pl.wraven.mpkfastroute.core.gtfs

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import pl.wraven.mpkfastroute.core.database.CalendarDateEntity
import pl.wraven.mpkfastroute.core.database.ServiceCalendarEntity

object ServiceCalendarResolver {
    fun activeServiceIds(
        date: LocalDate,
        calendars: List<ServiceCalendarEntity>,
        exceptions: List<CalendarDateEntity>
    ): Set<String> {
        val gtfsDate = date.format(DateTimeFormatter.BASIC_ISO_DATE)
        val activeServices = calendars.asSequence()
            .filter { it.startDate <= gtfsDate && it.endDate >= gtfsDate }
            .filter { it.runsOn(date) }
            .mapTo(mutableSetOf(), ServiceCalendarEntity::serviceId)

        for (exception in exceptions.filter { it.date == gtfsDate }) {
            when (exception.exceptionType) {
                1 -> activeServices += exception.serviceId
                2 -> activeServices -= exception.serviceId
            }
        }
        return activeServices
    }

    private fun ServiceCalendarEntity.runsOn(date: LocalDate): Boolean =
        when (date.dayOfWeek.value) {
            1 -> monday
            2 -> tuesday
            3 -> wednesday
            4 -> thursday
            5 -> friday
            6 -> saturday
            7 -> sunday
            else -> 0
        } == 1
}
