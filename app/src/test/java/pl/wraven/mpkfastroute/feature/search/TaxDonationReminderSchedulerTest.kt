package pl.wraven.mpkfastroute.feature.search

import java.time.LocalDate
import java.time.LocalTime
import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TaxDonationReminderSchedulerTest {
    @Test
    fun schedulesReminderForTheChosenDateInTheLocalTimezone() {
        val zoneId = ZoneId.of("Europe/Warsaw")
        val target = taxDonationReminderAt(zoneId)

        assertEquals(LocalDate.of(2027, 2, 15), target.toLocalDate())
        assertEquals(LocalTime.of(10, 0), target.toLocalTime())
        assertEquals(zoneId, target.zone)
    }

    @Test
    fun reminderBecomesDueAtTheChosenLocalTime() {
        val zoneId = ZoneId.of("Europe/Warsaw")
        val target = taxDonationReminderAt(zoneId).toInstant()

        assertFalse(isTaxDonationReminderDue(target.minusMillis(1), zoneId))
        assertTrue(isTaxDonationReminderDue(target, zoneId))
        assertEquals(1_000L, taxDonationReminderDelayMillis(target.minusSeconds(1), zoneId))
        assertEquals(0L, taxDonationReminderDelayMillis(target, zoneId))
    }
}
