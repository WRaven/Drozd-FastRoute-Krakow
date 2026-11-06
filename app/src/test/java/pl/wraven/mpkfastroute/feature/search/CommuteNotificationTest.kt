package pl.wraven.mpkfastroute.feature.search

import org.junit.Assert.assertEquals
import org.junit.Test

class CommuteNotificationTest {
    @Test
    fun collapsedNotificationStartsWithUpcomingDepartures() {
        val body = formatCommuteNotificationBody(
            departureLines = "Odjazdy: 07:30 live · 07:40 rozkład",
            minutesUntilDeparture = 15,
            disruptions = "Brak zgłoszonych utrudnień na trasie."
        )

        assertEquals("Odjazdy: 07:30 live · 07:40 rozkład", body.lineSequence().first())
    }
}
