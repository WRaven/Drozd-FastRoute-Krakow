package pl.wraven.mpkfastroute.core.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class DestinationShortcutNamesTest {
    @Test
    fun preservesExistingDefaultShortcutNames() {
        assertEquals("Do domu", DestinationShortcutNames().home)
        assertEquals("Do pracy", DestinationShortcutNames().work)
    }

    @Test
    fun trimsNamesWithoutChangingTheirContentOrCase() {
        assertEquals(
            DestinationShortcutNames("Mój cel", "Biuro A"),
            DestinationShortcutNames("  Mój cel  ", " Biuro A ").normalized()
        )
    }

    @Test
    fun rejectsEmptyOrWhitespaceOnlyNames() {
        assertThrows(IllegalArgumentException::class.java) {
            DestinationShortcutNames(home = " ")
        }
        assertThrows(IllegalArgumentException::class.java) {
            DestinationShortcutNames(work = "")
        }
    }
}
