package pl.wraven.mpkfastroute.core.live

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.wraven.mpkfastroute.core.database.CalendarDateEntity
import pl.wraven.mpkfastroute.core.database.GtfsDepartureRow

class StopTimetableRepositoryTest {
    @Test
    fun usesActualCalendarDatesAndShowsTheWholeDayForEachLineAndDirection() {
        val today = LocalDate.of(2026, 11, 7)
        val timetable = buildStopTimetable(
            listOf(
                GtfsDepartureRow("22", "A", 5 * 3_600 + 5 * 60, "work"),
                GtfsDepartureRow("22", "A", 23 * 3_600 + 55 * 60, "work"),
                GtfsDepartureRow("22", "A", 25 * 3_600 + 10 * 60, "work"),
                GtfsDepartureRow("22", "B", 8 * 3_600, "work"),
                GtfsDepartureRow("1", "A", 6 * 3_600, "sat"),
                GtfsDepartureRow("22", "A", 7 * 3_600, "sun")
            ), emptyList(), listOf(
                CalendarDateEntity("sat", "20261107", 1),
                CalendarDateEntity("sun", "20261108", 1),
                CalendarDateEntity("work", "20261109", 1)
            ), today
        )
        assertEquals(listOf("1", "22", "22"), timetable.lines.map { it.routeShortName })
        assertEquals(LocalDate.of(2026, 11, 9), timetable.days[TimetableDayType.WORKDAY]?.date)
        assertEquals(LocalDate.of(2026, 11, 7), timetable.days[TimetableDayType.SATURDAY]?.date)
        val hours = timetableHours(timetable.days[TimetableDayType.WORKDAY], TimetableLine("22", "A"))
        assertEquals(listOf(5, 23, 25), hours.map { it.hour })
        assertEquals(listOf(listOf(5), listOf(55), listOf(10)), hours.map { it.minutes })
        assertTrue(timetableHours(timetable.days[TimetableDayType.SATURDAY], TimetableLine("22", "A")).isEmpty())
    }

    @Test
    fun appliesRemovedServicesAndDoesNotInventUnavailableDayTypes() {
        val result = buildStopTimetable(listOf(GtfsDepartureRow("1", "End", 100, "s")),
            emptyList(), listOf(CalendarDateEntity("s", "20261107", 1),
                CalendarDateEntity("s", "20261107", 2)), LocalDate.of(2026, 11, 7))
        assertTrue(result.lines.isEmpty())
        assertTrue(result.days.values.all { it.date == null && it.departures.isEmpty() })
    }

    @Test
    fun groupsMinutesInOrderWithoutMergingDirectionsOrIncludingDuplicateTimes() {
        val day = TimetableDay(LocalDate.of(2026, 11, 9), listOf(
            StaticDeparture("22", "A", 6 * 3_600 + 50 * 60),
            StaticDeparture("22", "A", 6 * 3_600 + 5 * 60),
            StaticDeparture("22", "A", 6 * 3_600 + 5 * 60),
            StaticDeparture("22", "B", 6 * 3_600 + 15 * 60)
        ))
        assertEquals(listOf(5, 50), timetableHours(day, TimetableLine("22", "A")).single().minutes)
    }

    @Test
    fun classifiesPolishHolidaysIncludingMovableDatesAndChristmasEveFrom2025() {
        listOf("2026-11-11", "2026-04-05", "2026-04-06", "2026-06-04", "2026-12-24").forEach {
            assertEquals(TimetableDayType.SUNDAY_HOLIDAY, timetableDayType(LocalDate.parse(it)))
        }
        assertFalse(isPolishPublicHoliday(LocalDate.of(2024, 12, 24)))
        assertEquals(TimetableDayType.SATURDAY, timetableDayType(LocalDate.of(2026, 11, 7)))
        assertEquals(TimetableDayType.WORKDAY, timetableDayType(LocalDate.of(2026, 11, 9)))
    }
}
