package pl.wraven.mpkfastroute.core.gtfs

import java.time.LocalDate
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Test

class GtfsServiceClockTest {
    @Test
    fun mapsAfterMidnightTimesToPreviousServiceDateAndGtfsHours() {
        val serviceTime = GtfsServiceClock.from(
            LocalDateTime.of(2026, 11, 6, 2, 15)
        )

        assertEquals(LocalDate.of(2026, 11, 5), serviceTime.serviceDate)
        assertEquals(26 * 60 * 60 + 15 * 60, serviceTime.timeSeconds)
    }

    @Test
    fun keepsTimesAtServiceDayCutoffOnCurrentDate() {
        val serviceTime = GtfsServiceClock.from(
            LocalDateTime.of(2026, 11, 6, 3, 0)
        )

        assertEquals(LocalDate.of(2026, 11, 6), serviceTime.serviceDate)
        assertEquals(3 * 60 * 60, serviceTime.timeSeconds)
    }
}
