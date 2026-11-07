package pl.wraven.mpkfastroute.core.live

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ZtpGtfsArchiveRepositoryTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test
    fun cachesEachModeOnDiskAndRefreshesAfterOneHour() = runBlocking {
        val urls = mutableListOf<String>()
        val client = HttpClient(MockEngine { request ->
            urls += request.url.toString()
            respond(archive())
        })
        try {
            val repository = ZtpGtfsArchiveRepository(client, temporary.newFolder())
            repository.withArchive(TransitVehicleMode.TRAM, 1_000L) { assertEquals("T.zip", it.name) }
            repository.withArchive(TransitVehicleMode.TRAM, 2_000L) {}
            repository.withArchive(TransitVehicleMode.BUS, 2_000L) { assertEquals("A.zip", it.name) }
            repository.withArchive(TransitVehicleMode.TRAM, 3_601_000L) {}
            assertEquals(listOf(ZtpGtfsArchiveRepository.gtfsUrl(TransitVehicleMode.TRAM),
                ZtpGtfsArchiveRepository.gtfsUrl(TransitVehicleMode.BUS),
                ZtpGtfsArchiveRepository.gtfsUrl(TransitVehicleMode.TRAM)), urls)
        } finally {
            client.close()
        }
    }

    @Test
    fun rejectsHttpErrorsAndInvalidZipsThenRetriesWithoutAnEmptySuccessCache() = runBlocking {
        var requests = 0
        val client = HttpClient(MockEngine {
            requests++
            when (requests) {
                1 -> respond("", status = HttpStatusCode.ServiceUnavailable)
                2 -> respond("invalid zip")
                else -> respond(archive())
            }
        })
        try {
            val repository = ZtpGtfsArchiveRepository(client, temporary.newFolder())
            repeat(2) {
                assertThrows(IOException::class.java) {
                    runBlocking { repository.withArchive(TransitVehicleMode.TRAM, 1_000L) {} }
                }
            }
            repository.withArchive(TransitVehicleMode.TRAM, 1_000L) {}
            assertEquals(3, requests)
        } finally {
            client.close()
        }
    }

    private fun archive(): ByteArray {
        val bytes = ByteArrayOutputStream()
        ZipOutputStream(bytes).use {
            it.putNextEntry(ZipEntry("trips.txt"))
            it.write("trip_id,route_id\ntrip,route".toByteArray())
            it.closeEntry()
        }
        return bytes.toByteArray()
    }
}
