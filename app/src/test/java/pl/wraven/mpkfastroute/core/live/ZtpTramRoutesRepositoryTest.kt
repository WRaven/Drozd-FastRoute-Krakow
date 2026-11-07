package pl.wraven.mpkfastroute.core.live

import com.google.transit.realtime.GtfsRealtime
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class ZtpTramRoutesRepositoryTest {
    @Test
    fun resolvesTramWithoutRouteIdUsingTheMatchingStaticFeed() = runBlocking {
        val routes = parseTramRouteShortNames(archive().inputStream())
        val feed = GtfsRealtime.FeedMessage.newBuilder()
            .setHeader(
                GtfsRealtime.FeedHeader.newBuilder()
                    .setGtfsRealtimeVersion("2.0")
            )
            .addEntity(
                GtfsRealtime.FeedEntity.newBuilder()
                    .setId("306")
                    .setVehicle(
                        GtfsRealtime.VehiclePosition.newBuilder()
                            .setTrip(
                                GtfsRealtime.TripDescriptor.newBuilder()
                                    .setTripId("block_138_trip_21_service_2")
                            )
                            .setPosition(
                                GtfsRealtime.Position.newBuilder()
                                    .setLatitude(50.066246f)
                                    .setLongitude(19.958647f)
                            )
                    )
            )
            .build()

        val vehicle = resolveMissingVehicleRouteShortNames(
            mapVehiclePositions(feed, TransitVehicleMode.TRAM, mapOf("159980" to "1")),
            routes
        ).single()

        assertEquals("17", vehicle.routeShortName)
        assertNull(vehicle.routeId)
    }

    @Test
    fun fetchesTheMatchingArchiveAndCachesItsMappingForOneHour() = runBlocking {
        var requests = 0
        val client = HttpClient(MockEngine { request ->
            requests++
            assertEquals(ZtpTramRoutesRepository.TRAM_GTFS_URL, request.url.toString())
            respond(archive(), headers = headersOf(HttpHeaders.ContentType, "application/zip"))
        })
        try {
            val repository = ZtpTramRoutesRepository(client)
            val first = repository.getRouteShortNamesByTripId(nowMillis = 0)
            assertEquals(mapOf("block_138_trip_21_service_2" to "17"), first)
            assertEquals(first, repository.getRouteShortNamesByTripId(nowMillis = 59 * 60 * 1_000L))
            assertEquals(1, requests)
            assertEquals(first, repository.getRouteShortNamesByTripId(nowMillis = 60 * 60 * 1_000L))
            assertEquals(2, requests)
        } finally {
            client.close()
        }
    }

    @Test
    fun reportsHttpFailuresAndRetriesInsteadOfCachingAnEmptyMapping() = runBlocking {
        var requests = 0
        val client = HttpClient(MockEngine {
            requests++
            if (requests == 1) {
                respond("", status = HttpStatusCode.ServiceUnavailable)
            } else {
                respond(archive(), headers = headersOf(HttpHeaders.ContentType, "application/zip"))
            }
        })
        try {
            val repository = ZtpTramRoutesRepository(client)
            assertThrows(IOException::class.java) {
                runBlocking { repository.getRouteShortNamesByTripId(nowMillis = 0) }
            }
            assertEquals(
                mapOf("block_138_trip_21_service_2" to "17"),
                repository.getRouteShortNamesByTripId(nowMillis = 1)
            )
            assertEquals(2, requests)
        } finally {
            client.close()
        }
    }

    @Test
    fun rejectsArchivesMissingRouteOrTripTables() {
        assertThrows(IOException::class.java) {
            runBlocking {
                parseTramRouteShortNames(archive(trips = "").inputStream())
            }
        }
    }

    @Test
    fun rejectsTripsReferencingUnknownRoutes() {
        assertThrows(IOException::class.java) {
            runBlocking {
                parseTramRouteShortNames(
                    archive(trips = "trip_id,route_id\nblock_1_trip_1_service_2,missing").inputStream()
                )
            }
        }
    }

    @Test
    fun reportsInvalidCsvAsAnIoFailure() {
        assertThrows(IOException::class.java) {
            runBlocking {
                parseTramRouteShortNames(archive(routes = "\"route_id,route_short_name").inputStream())
            }
        }
    }

    private fun archive(
        routes: String = "route_id,agency_id,route_short_name\nroute_958,agency_1,\"17\"",
        trips: String = "trip_id,route_id,service_id,trip_headsign\n" +
            "block_138_trip_21_service_2,route_958,service_2,\"Kierunek, z przecinkiem\""
    ): ByteArray {
        val bytes = ByteArrayOutputStream()
        ZipOutputStream(bytes).use { zip ->
            listOf("nested/trips.txt" to trips, "nested/routes.txt" to routes).forEach { (name, csv) ->
                if (csv.isNotEmpty()) {
                    zip.putNextEntry(ZipEntry(name))
                    zip.write(csv.toByteArray(Charsets.UTF_8))
                    zip.closeEntry()
                }
            }
        }
        return bytes.toByteArray()
    }
}
