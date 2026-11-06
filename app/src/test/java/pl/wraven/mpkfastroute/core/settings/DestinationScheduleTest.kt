package pl.wraven.mpkfastroute.core.settings

import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Test

class DestinationScheduleTest {
    @Test
    fun nextOccurrenceUsesTodayWhenArrivalTimeIsStillUpcoming() {
        val schedule = DestinationSchedule(
            arrivalTime = LocalTime.of(8, 0),
            weekdays = setOf(DayOfWeek.TUESDAY)
        )

        assertEquals(
            LocalDateTime.of(2026, 6, 9, 8, 0),
            schedule.nextOccurrence(LocalDateTime.of(2026, 6, 9, 7, 0))
        )
    }

    @Test
    fun nextOccurrenceMovesToNextWeekWhenTodayTimeHasPassed() {
        val schedule = DestinationSchedule(
            arrivalTime = LocalTime.of(8, 0),
            weekdays = setOf(DayOfWeek.TUESDAY)
        )

        assertEquals(
            LocalDateTime.of(2026, 6, 16, 8, 0),
            schedule.nextOccurrence(LocalDateTime.of(2026, 6, 9, 8, 0))
        )
    }

    @Test
    fun scheduleEncodingRoundTripsTimeAndDays() {
        val schedule = DestinationSchedule(
            arrivalTime = LocalTime.of(7, 45),
            weekdays = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY)
        )

        assertEquals(schedule, DestinationSchedule.decode(schedule.encode()))
    }
}
