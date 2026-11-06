package pl.wraven.mpkfastroute.core.live

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ServiceAlertsClientTest {
    private val alert = RouteServiceAlert(
        id = "A:alert-1",
        title = "Objazd",
        description = null,
        routeIds = setOf("route-1"),
        stopIds = setOf("stop-1")
    )

    @Test
    fun matchesAffectedRouteIdOrShortName() {
        assertTrue(alert.affectsRoute(setOf("route-1"), emptySet(), emptySet()))
        assertTrue(alert.affectsRoute(emptySet(), setOf("route-1"), emptySet()))
        assertFalse(alert.affectsRoute(setOf("other-route"), emptySet(), emptySet()))
    }

    @Test
    fun matchesAffectedStopOnTheJourney() {
        assertTrue(alert.affectsRoute(emptySet(), emptySet(), setOf("stop-1")))
        assertFalse(alert.affectsRoute(emptySet(), emptySet(), setOf("other-stop")))
    }
}
