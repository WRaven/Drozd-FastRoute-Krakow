package pl.wraven.mpkfastroute.core.live

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class VehicleTripScheduleRepositoryTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test
    fun resolvesTheExactRealtimeTramTripWithAllStopsAndItsShape() = runBlocking {
        val archive = temporary.newFile().apply { writeBytes(archive()) }
        val schedule = parseVehicleTripSchedule(archive, vehicle(), LocalDateTime.of(2026, 11, 8, 0, 15))
        assertEquals("22", schedule.routeShortName)
        assertEquals("Kierunek, końcowy", schedule.direction)
        assertEquals(LocalDate.of(2026, 11, 7), schedule.serviceDate)
        assertEquals(12, schedule.stops.size)
        assertEquals((1..12).toList(), schedule.stops.map { it.stopSequence })
        assertEquals("stop_live_1", schedule.stops.first().stop.stopId)
        assertEquals("stop_live_12", schedule.stops.last().stop.stopId)
        assertEquals(listOf(1, 2), schedule.shapePoints.map { it.shapePtSequence })
        assertEquals(25 * 3_600, schedule.stops.last().arrivalTime)
    }

    @Test
    fun usesTheFeedServiceDateRatherThanThePhonesThreeAmCutoff() = runBlocking {
        val archive = temporary.newFile().apply { writeBytes(archive()) }
        val schedule = parseVehicleTripSchedule(archive,
            vehicle().copy(serviceDate = LocalDate.of(2026, 11, 7)), LocalDateTime.of(2026, 11, 8, 4, 0))
        assertEquals(LocalDate.of(2026, 11, 7), schedule.serviceDate)
    }

    @Test
    fun supportsCalendarWithoutCalendarDatesAndAllowsMissingGeometry() = runBlocking {
        val archive = temporary.newFile().apply {
            writeBytes(archive(calendarDates = "", calendar =
                "service_id,monday,tuesday,wednesday,thursday,friday,saturday,sunday,start_date,end_date\n" +
                    "service_2,1,1,1,1,1,1,1,20261101,20261130", shapes = ""))
        }
        val schedule = parseVehicleTripSchedule(archive, vehicle(), LocalDateTime.of(2026, 11, 8, 0, 15))
        assertEquals(12, schedule.stops.size)
        assertTrue(schedule.shapePoints.isEmpty())
    }

    @Test
    fun refreshesTheMatchingArchiveOnceIfTheTripIsMissingFromTheCache() = runBlocking {
        var requests = 0
        val client = HttpClient(MockEngine { request ->
            assertEquals(ZtpGtfsArchiveRepository.gtfsUrl(TransitVehicleMode.BUS), request.url.toString())
            requests++
            respond(archive(tripId = if (requests == 1) "old" else TRIP_ID))
        })
        try {
            val repository = VehicleTripScheduleRepository(ZtpGtfsArchiveRepository(client, temporary.newFolder()))
            val schedule = repository.getSchedule(vehicle().copy(mode = TransitVehicleMode.BUS),
                LocalDateTime.of(2026, 11, 8, 0, 15))
            assertEquals(TRIP_ID, schedule.tripId)
            assertEquals(2, requests)
        } finally {
            client.close()
        }
    }

    @Test
    fun failsExplicitlyForAMissingStopOrInvalidTime() {
        listOf(
            archive(stopTimes = "trip_id,arrival_time,departure_time,stop_id,stop_sequence\n$TRIP_ID,24:00:00,24:00:00,missing,1"),
            archive(stopTimes = "trip_id,arrival_time,departure_time,stop_id,stop_sequence\n$TRIP_ID,24:60:00,24:00:00,stop_live_1,1")
        ).forEach { bytes ->
            val file = temporary.newFile().apply { writeBytes(bytes) }
            assertThrows(IOException::class.java) {
                runBlocking { parseVehicleTripSchedule(file, vehicle(), LocalDateTime.of(2026, 11, 8, 0, 15)) }
            }
        }
    }

    private fun vehicle() = LiveVehiclePosition("T:721", TransitVehicleMode.TRAM, 50.0, 20.0,
        routeShortName = "22", vehicleLabel = "721", tripId = TRIP_ID)

    private fun archive(
        tripId: String = TRIP_ID,
        stopTimes: String? = null,
        calendar: String = "",
        calendarDates: String = "service_id,date,exception_type\nservice_2,20261107,1",
        shapes: String = "shape_id,shape_pt_lat,shape_pt_lon,shape_pt_sequence\nshape_2,50.2,20.2,2\nshape_2,50.1,20.1,1"
    ): ByteArray {
        val bytes = ByteArrayOutputStream()
        val stops = "stop_id,stop_code,stop_name,stop_lat,stop_lon\n" +
            (1..12).joinToString("\n") { "stop_live_$it,100-$it,Przystanek $it,50.0,20.0" }
        val times = stopTimes ?: "trip_id,arrival_time,departure_time,stop_id,stop_sequence\n" +
            (12 downTo 1).joinToString("\n") {
                val time = "%02d:%02d:00".format(24 + it / 12, it % 12 * 5)
                "$tripId,$time,$time,stop_live_$it,$it"
            }
        ZipOutputStream(bytes).use { zip ->
            listOf(
                "shapes.txt" to shapes, "stops.txt" to stops, "stop_times.txt" to times,
                "routes.txt" to "route_id,route_short_name\nroute_2,22",
                "trips.txt" to "trip_id,route_id,service_id,trip_headsign,shape_id\n$tripId,route_2,service_2,\"Kierunek, końcowy\",shape_2",
                "calendar.txt" to calendar, "calendar_dates.txt" to calendarDates
            ).filter { it.second.isNotEmpty() }.forEach { (name, content) ->
                zip.putNextEntry(ZipEntry("nested/$name"))
                zip.write(content.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
        }
        return bytes.toByteArray()
    }

    companion object {
        private const val TRIP_ID = "block_138_trip_21_service_2"
    }
}
