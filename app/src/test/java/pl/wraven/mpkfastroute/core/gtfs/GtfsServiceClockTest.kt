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

    @Test
    fun splitsWindowAcrossServiceDayBoundary() {
        val windows = GtfsServiceClock.window(
            startDateTime = LocalDateTime.of(2024, 1, 2, 2, 0),
            durationSeconds = 2 * 60 * 60
        )

        assertEquals(2, windows.size)
        assertEquals(LocalDate.of(2024, 1, 1), windows[0].serviceDate)
        assertEquals(26 * 60 * 60, windows[0].startTimeSeconds)
        assertEquals(60 * 60, windows[0].durationSeconds)
        assertEquals(0, windows[0].serviceDayOffsetSeconds)
        assertEquals(LocalDate.of(2024, 1, 2), windows[1].serviceDate)
        assertEquals(3 * 60 * 60, windows[1].startTimeSeconds)
        assertEquals(60 * 60, windows[1].durationSeconds)
        assertEquals(24 * 60 * 60, windows[1].serviceDayOffsetSeconds)
    }

    @Test
    fun keepsArrivalDeadlineOnBothServiceDateTimelines() {
        val windows = GtfsServiceClock.window(
            startDateTime = LocalDateTime.of(2024, 1, 2, 1, 30),
            durationSeconds = 2 * 60 * 60,
            arrivalDeadline = LocalDateTime.of(2024, 1, 2, 3, 30)
        )

        assertEquals(2, windows.size)
        assertEquals(27 * 60 * 60 + 30 * 60, windows[0].arrivalDeadlineSeconds)
        assertEquals(3 * 60 * 60 + 30 * 60, windows[1].arrivalDeadlineSeconds)
    }
}
