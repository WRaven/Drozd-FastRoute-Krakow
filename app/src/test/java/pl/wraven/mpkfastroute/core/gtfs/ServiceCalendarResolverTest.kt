package pl.wraven.mpkfastroute.core.gtfs

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test
import pl.wraven.mpkfastroute.core.database.CalendarDateEntity
import pl.wraven.mpkfastroute.core.database.ServiceCalendarEntity

class ServiceCalendarResolverTest {
    @Test
    fun usesWeekdayFlagsWithinCalendarDateRange() {
        val active = ServiceCalendarResolver.activeServiceIds(
            date = LocalDate.of(2026, 11, 2),
            calendars = listOf(calendar("weekday")),
            exceptions = emptyList()
        )

        assertEquals(setOf("weekday"), active)
    }

    @Test
    fun appliesAddedAndRemovedDateExceptions() {
        val active = ServiceCalendarResolver.activeServiceIds(
            date = LocalDate.of(2026, 11, 7),
            calendars = listOf(calendar("weekday")),
            exceptions = listOf(
                CalendarDateEntity("holiday", "20261107", 1),
                CalendarDateEntity("weekday", "20261107", 2)
            )
        )

        assertEquals(setOf("holiday"), active)
    }

    @Test
    fun ignoresCalendarOutsideItsDateRange() {
        val active = ServiceCalendarResolver.activeServiceIds(
            date = LocalDate.of(2026, 11, 2),
            calendars = listOf(calendar("weekday", start = "20261103")),
            exceptions = emptyList()
        )

        assertEquals(emptySet<String>(), active)
    }

    private fun calendar(
        serviceId: String,
        start: String = "20260101",
        end: String = "20261231"
    ) = ServiceCalendarEntity(
        serviceId = serviceId,
        monday = 1,
        tuesday = 1,
        wednesday = 1,
        thursday = 1,
        friday = 1,
        saturday = 0,
        sunday = 0,
        startDate = start,
        endDate = end
    )
}
