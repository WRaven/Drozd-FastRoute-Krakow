package pl.wraven.mpkfastroute.core.gtfs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class GtfsZipParserTest {
    @Test
    fun parsesGtfsTimeAsSecondsFromMidnight() {
        assertEquals(30_600, parseGtfsTime("08:30:00"))
    }

    @Test
    fun parsesGtfsHoursGreaterThanTwentyFour() {
        assertEquals(91_800, parseGtfsTime("25:30:00"))
    }

    @Test
    fun rejectsOutOfRangeMinutes() {
        assertThrows(IllegalArgumentException::class.java) {
            parseGtfsTime("08:60:00")
        }
    }

    @Test
    fun rejectsGtfsHoursThatWouldOverflowSeconds() {
        assertThrows(IllegalArgumentException::class.java) {
            parseGtfsTime("999999999999999999999:00:00")
        }
    }

    @Test
    fun parsesQuotedCommasAndEscapedQuotes() {
        assertEquals(
            listOf("route_id", "A, B", "Say \"hello\""),
            parseCsvLine("route_id,\"A, B\",\"Say \"\"hello\"\"\"")
        )
    }

    @Test
    fun rejectsUnclosedCsvQuotes() {
        assertThrows(IllegalArgumentException::class.java) {
            parseCsvLine("\"unfinished")
        }
    }
}
