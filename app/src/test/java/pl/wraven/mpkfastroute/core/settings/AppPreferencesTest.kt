package pl.wraven.mpkfastroute.core.settings

import androidx.datastore.core.okio.OkioStorage
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.PreferencesSerializer
import java.nio.file.Files
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okio.FileSystem
import okio.Path.Companion.toOkioPath
import org.junit.Assert.assertEquals
import org.junit.Test
import pl.wraven.mpkfastroute.core.location.GeoPlace

class AppPreferencesTest {
    @Test
    fun shortcutNamesSurviveReopeningWithoutChangingDestinationsOrSchedules() = runBlocking {
        val directory = Files.createTempDirectory("mpk-shortcuts-")
        val file = directory.resolve("shortcuts.preferences_pb").toFile()
        val firstScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val secondScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val preferences = AppPreferences(
                PreferenceDataStoreFactory.create(
                    storage = OkioStorage(FileSystem.SYSTEM, PreferencesSerializer) {
                        file.toOkioPath()
                    },
                    scope = firstScope
                ),
                Json
            )
            assertEquals(DestinationShortcutNames(), preferences.destinationShortcutNames.first())
            val place = GeoPlace("Punkt testowy", 50.0, 20.0)
            preferences.saveHomePlace(place, DestinationSchedule.DEFAULT)
            preferences.saveWorkStopId("work-test-stop")
            preferences.saveDestinationShortcutNames(
                DestinationShortcutNames("  Mój cel  ", " Biuro A ")
            )
            firstScope.coroutineContext.job.cancelAndJoin()

            val reopened = AppPreferences(
                PreferenceDataStoreFactory.create(
                    storage = OkioStorage(FileSystem.SYSTEM, PreferencesSerializer) {
                        file.toOkioPath()
                    },
                    scope = secondScope
                ),
                Json
            )
            assertEquals(
                DestinationShortcutNames("Mój cel", "Biuro A"),
                reopened.destinationShortcutNames.first()
            )
            assertEquals(place, reopened.homePlace.first())
            assertEquals(DestinationSchedule.DEFAULT, reopened.homeSchedule.first())
            assertEquals("work-test-stop", reopened.workStopId.first())
        } finally {
            firstScope.coroutineContext.job.cancelAndJoin()
            secondScope.coroutineContext.job.cancelAndJoin()
            Files.deleteIfExists(file.toPath())
            Files.deleteIfExists(directory)
        }
    }
}
