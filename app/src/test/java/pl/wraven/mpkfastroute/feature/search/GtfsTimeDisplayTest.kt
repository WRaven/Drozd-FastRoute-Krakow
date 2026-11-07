package pl.wraven.mpkfastroute.feature.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.wraven.mpkfastroute.core.gtfs.parseGtfsTime

class GtfsTimeDisplayTest {
    @Test
    fun displaysTheScreenshotTimesAsNormalClockHours() {
        mapOf(
            "24:42:00" to "00:42",
            "24:45:00" to "00:45",
            "24:54:00" to "00:54",
            "25:00:00" to "01:00",
            "25:01:00" to "01:01",
            "25:06:00" to "01:06",
            "25:30:00" to "01:30",
            "25:55:00" to "01:55",
            "26:02:00" to "02:02",
            "26:11:00" to "02:11"
        ).forEach { (gtfs, expected) ->
            assertEquals(expected, formatGtfsTime(parseGtfsTime(gtfs)))
        }
    }

    @Test
    fun preservesDaytimeTimesAndWrapsMidnightWithoutRounding() {
        assertEquals("00:00", formatGtfsTime(0))
        assertEquals("12:34", formatGtfsTime(parseGtfsTime("12:34:59")))
        assertEquals("23:59", formatGtfsTime(parseGtfsTime("23:59:59")))
        assertEquals("00:00", formatGtfsTime(parseGtfsTime("24:00:00")))
        assertEquals("01:15", formatGtfsTime(parseGtfsTime("49:15:00")))
        assertEquals("23:59", formatGtfsTime(-1))
    }

    @Test
    fun onlyMarksANewCalendarDayRelativeToTheJourneyStart() {
        assertEquals("01:06", formatJourneyTime(parseGtfsTime("25:06:00"), parseGtfsTime("24:34:00")))
        assertEquals("01:06 (+1)", formatJourneyTime(parseGtfsTime("25:06:00"), parseGtfsTime("23:34:00")))
        assertEquals("00:00 (+1)", formatJourneyTime(parseGtfsTime("24:00:00"), parseGtfsTime("23:59:00")))
        assertEquals("01:15 (+2)", formatJourneyTime(parseGtfsTime("49:15:00"), parseGtfsTime("23:59:00")))
    }

    @Test
    fun everyDisplayedMinuteIsWithinThe24HourClock() {
        for (minute in 0 until 3 * 24 * 60) {
            val text = formatGtfsTime(minute * 60)
            assertTrue(text.matches(Regex("""(?:[01]\d|2[0-3]):[0-5]\d""")))
            assertEquals(formatGtfsTime(minute * 60 % (24 * 60 * 60)), text)
        }
    }

    @Test
    fun formattingDoesNotChangeGtfsTimesOrTheOvernightJourneyDuration() {
        val departure = parseGtfsTime("24:34:00")
        val arrival = parseGtfsTime("25:06:00")
        assertEquals("01:06", formatJourneyTime(arrival, departure))
        assertEquals(25 * 3_600 + 6 * 60, arrival)
        assertEquals(32 * 60, arrival - departure)
    }
}
