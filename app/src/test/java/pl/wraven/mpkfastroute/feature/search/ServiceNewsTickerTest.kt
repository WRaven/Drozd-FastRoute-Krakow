package pl.wraven.mpkfastroute.feature.search

import androidx.compose.foundation.MarqueeDefaults
import org.junit.Assert.assertEquals
import org.junit.Test

class ServiceNewsTickerTest {
    @Test
    fun scrollsTwentyPercentFasterThanThePreviousDefault() {
        assertEquals(1.2f, serviceNewsMarqueeVelocity.value / MarqueeDefaults.Velocity.value, 0.001f)
        assertEquals(36f, serviceNewsMarqueeVelocity.value, 0.001f)
    }
}
