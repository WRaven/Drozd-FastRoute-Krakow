package pl.wraven.mpkfastroute.feature.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveStopMarkerSizeTest {
    @Test
    fun usesSmallDotsForTheWholeCityView() {
        listOf(0.0, 10.0, 12.0, 13.0).forEach { zoom ->
            assertEquals(5f, stopMarkerSizeDp(zoom, selected = false), 0f)
        }
    }

    @Test
    fun growsSmoothlyAndRestoresTheExistingSizeAtCloseZoom() {
        assertEquals(7.25f, stopMarkerSizeDp(13.5, selected = false), 0f)
        assertEquals(9.5f, stopMarkerSizeDp(14.0, selected = false), 0f)
        assertEquals(11.75f, stopMarkerSizeDp(14.5, selected = false), 0f)
        listOf(15.0, 16.0, 20.0).forEach { zoom ->
            assertEquals(14f, stopMarkerSizeDp(zoom, selected = false), 0f)
        }
    }

    @Test
    fun zoomingBackOutShrinksTheDotsWithoutExceedingTheSizeBounds() {
        val sizes = (200 downTo 0).map { stopMarkerSizeDp(it / 10.0, selected = false) }
        assertTrue(sizes.all { it in 5f..14f })
        assertTrue(sizes.zipWithNext().all { (previous, next) -> previous >= next })
    }

    @Test
    fun preservesTheSelectedStopSizeAtEveryZoom() {
        listOf(0.0, 12.0, 13.5, 15.0, 20.0).forEach { zoom ->
            assertEquals(18f, stopMarkerSizeDp(zoom, selected = true), 0f)
        }
    }

    @Test
    fun rejectsInvalidZoomValues() {
        listOf(Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY).forEach { zoom ->
            assertThrows(IllegalArgumentException::class.java) {
                stopMarkerSizeDp(zoom, selected = false)
            }
        }
    }
}
