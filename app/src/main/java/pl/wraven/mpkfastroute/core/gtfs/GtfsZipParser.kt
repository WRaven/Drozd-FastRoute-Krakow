package pl.wraven.mpkfastroute.core.gtfs

import androidx.room.withTransaction
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.util.zip.ZipInputStream
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import pl.wraven.mpkfastroute.core.database.AppDatabase
import pl.wraven.mpkfastroute.core.database.RouteEntity
import pl.wraven.mpkfastroute.core.database.StopEntity
import pl.wraven.mpkfastroute.core.database.StopTimeEntity
import pl.wraven.mpkfastroute.core.database.TripEntity

const val GTFS_KRAKOW_URL = "https://gtfs.ztp.krakow.pl/GTFS_KRK.zip"

private const val BATCH_SIZE = 5_000
private const val CONNECT_TIMEOUT_MILLIS = 30_000
private const val READ_TIMEOUT_MILLIS = 120_000

class GtfsZipParser @Inject constructor(
    private val database: AppDatabase
) {
    suspend fun importSchedule(
        onProgress: (String) -> Unit = {}
    ) = withContext(Dispatchers.IO) {
        onProgress("Pobieranie rozkładu ZTP Kraków...")
        val archive = downloadArchive()

        onProgress("Indeksowanie bazy...")
        val stops = mutableListOf<StopEntity>()
        val routes = mutableListOf<RouteEntity>()
        val trips = mutableListOf<TripEntity>()
        readLookupTables(archive, stops, routes, trips)

        require(stops.isNotEmpty()) { "GTFS archive contains no stops." }
        require(routes.isNotEmpty()) { "GTFS archive contains no routes." }
        require(trips.isNotEmpty()) { "GTFS archive contains no trips." }

        val stopIds = stops.mapTo(HashSet(stops.size)) { it.stopId }
        val tripIds = trips.mapTo(HashSet(trips.size)) { it.tripId }
        val sqliteDatabase = database.openHelper.writableDatabase
        sqliteDatabase.execSQL("PRAGMA foreign_keys = OFF")

        try {
            database.withTransaction {
                val stopDao = database.stopDao()
                val routeDao = database.routeDao()
                val tripDao = database.tripDao()
                val stopTimeDao = database.stopTimeDao()

                stopTimeDao.deleteAll()
                tripDao.deleteAll()
                routeDao.deleteAll()
                stopDao.deleteAll()

                stops.chunked(BATCH_SIZE).forEach(stopDao::insertAll)
                routes.chunked(BATCH_SIZE).forEach(routeDao::insertAll)
                trips.chunked(BATCH_SIZE).forEach(tripDao::insertAll)

                val stopTimeCount = readStopTimes(archive, stopIds, tripIds, stopTimeDao::insertAll)
                check(stopTimeCount > 0) { "GTFS archive contains no stop times." }
            }
        } finally {
            sqliteDatabase.execSQL("PRAGMA foreign_keys = ON")
        }
    }

    private fun downloadArchive(): ByteArray {
        val connection = URL(GTFS_KRAKOW_URL).openConnection() as HttpURLConnection
        connection.connectTimeout = CONNECT_TIMEOUT_MILLIS
        connection.readTimeout = READ_TIMEOUT_MILLIS
        connection.instanceFollowRedirects = true

        return try {
            val responseCode = connection.responseCode
            if (responseCode !in 200..299) {
                throw IOException("GTFS download failed with HTTP $responseCode.")
            }
            connection.inputStream.use { it.readBytes() }
        } finally {
            connection.disconnect()
        }
    }

    private suspend fun readLookupTables(
        archive: ByteArray,
        stops: MutableList<StopEntity>,
        routes: MutableList<RouteEntity>,
        trips: MutableList<TripEntity>
    ) {
        ZipInputStream(archive.inputStream(), StandardCharsets.UTF_8).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                when (entry.name.substringAfterLast('/')) {
                    "stops.txt" -> readCsv(zip, entry.name) { columns, values, rowNumber ->
                        stops += StopEntity(
                            stopId = values.required(columns, "stop_id", entry.name, rowNumber),
                            stopCode = values.optional(columns, "stop_code"),
                            stopName = values.required(columns, "stop_name", entry.name, rowNumber),
                            stopLat = values.required(columns, "stop_lat", entry.name, rowNumber)
                                .toDoubleOrInvalid(entry.name, "stop_lat", rowNumber),
                            stopLon = values.required(columns, "stop_lon", entry.name, rowNumber)
                                .toDoubleOrInvalid(entry.name, "stop_lon", rowNumber)
                        )
                    }

                    "routes.txt" -> readCsv(zip, entry.name) { columns, values, rowNumber ->
                        routes += RouteEntity(
                            routeId = values.required(columns, "route_id", entry.name, rowNumber),
                            routeShortName = values.optional(columns, "route_short_name").orEmpty(),
                            routeLongName = values.optional(columns, "route_long_name").orEmpty(),
                            routeType = values.required(columns, "route_type", entry.name, rowNumber)
                                .toIntOrInvalid(entry.name, "route_type", rowNumber)
                        )
                    }

                    "trips.txt" -> readCsv(zip, entry.name) { columns, values, rowNumber ->
                        trips += TripEntity(
                            tripId = values.required(columns, "trip_id", entry.name, rowNumber),
                            routeId = values.required(columns, "route_id", entry.name, rowNumber),
                            serviceId = values.required(columns, "service_id", entry.name, rowNumber),
                            tripHeadsign = values.optional(columns, "trip_headsign")
                        )
                    }
                }
                zip.closeEntry()
            }
        }
    }

    private suspend fun readStopTimes(
        archive: ByteArray,
        stopIds: Set<String>,
        tripIds: Set<String>,
        insertBatch: suspend (List<StopTimeEntity>) -> Unit
    ): Int {
        var foundStopTimes = false
        var recordCount = 0
        val batch = ArrayList<StopTimeEntity>(BATCH_SIZE)

        ZipInputStream(archive.inputStream(), StandardCharsets.UTF_8).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (entry.name.substringAfterLast('/') == "stop_times.txt") {
                    foundStopTimes = true
                    readCsv(zip, entry.name) { columns, values, rowNumber ->
                        val tripId = values.required(columns, "trip_id", entry.name, rowNumber)
                        val stopId = values.required(columns, "stop_id", entry.name, rowNumber)
                        if (tripId !in tripIds) {
                            throw IOException("${entry.name} row $rowNumber references unknown trip '$tripId'.")
                        }
                        if (stopId !in stopIds) {
                            throw IOException("${entry.name} row $rowNumber references unknown stop '$stopId'.")
                        }

                        val arrivalText = values.optional(columns, "arrival_time")
                        val departureText = values.optional(columns, "departure_time")
                        if (arrivalText == null && departureText == null) {
                            throw IOException(
                                "${entry.name} row $rowNumber has neither arrival_time nor departure_time."
                            )
                        }
                        val arrivalTime = (arrivalText ?: departureText!!).toGtfsSeconds(entry.name, rowNumber)
                        val departureTime = (departureText ?: arrivalText!!).toGtfsSeconds(entry.name, rowNumber)

                        batch += StopTimeEntity(
                            tripId = tripId,
                            arrivalTime = arrivalTime,
                            departureTime = departureTime,
                            stopId = stopId,
                            stopSequence = values.required(
                                columns,
                                "stop_sequence",
                                entry.name,
                                rowNumber
                            ).toIntOrInvalid(entry.name, "stop_sequence", rowNumber)
                        )
                        recordCount++
                        if (batch.size == BATCH_SIZE) {
                            insertBatch(batch.toList())
                            batch.clear()
                        }
                    }
                }
                zip.closeEntry()
            }
        }

        if (!foundStopTimes) {
            throw IOException("GTFS archive is missing stop_times.txt.")
        }
        if (batch.isNotEmpty()) {
            insertBatch(batch)
        }
        return recordCount
    }

    private suspend fun readCsv(
        input: InputStream,
        entryName: String,
        onRecord: suspend (Map<String, Int>, List<String>, Int) -> Unit
    ) {
        val reader = BufferedReader(InputStreamReader(input, StandardCharsets.UTF_8))
        val headerLine = reader.readLine() ?: throw IOException("$entryName is empty.")
        val headers = parseCsvLine(headerLine.removePrefix("\uFEFF")).map(String::trim)
        val columns = headers.withIndex().associate { it.value to it.index }
        if (columns.size != headers.size) {
            throw IOException("$entryName contains duplicate column names.")
        }

        var rowNumber = 1
        while (true) {
            val line = reader.readLine() ?: break
            rowNumber++
            if (line.isBlank()) continue
            val values = try {
                parseCsvLine(line)
            } catch (exception: IllegalArgumentException) {
                throw IOException("$entryName row $rowNumber is not valid CSV.", exception)
            }
            if (values.size != headers.size) {
                throw IOException(
                    "$entryName row $rowNumber has ${values.size} fields; expected ${headers.size}."
                )
            }
            onRecord(columns, values, rowNumber)
        }
    }
}

internal fun parseGtfsTime(value: String): Int {
    val parts = value.split(':')
    require(parts.size == 3) { "Invalid GTFS time '$value'; expected HH:mm:ss." }

    val hours = parts[0].toLongOrNull()
    val minutes = parts[1].toLongOrNull()
    val seconds = parts[2].toLongOrNull()
    require(hours != null && hours >= 0) { "Invalid hour in GTFS time '$value'." }
    require(hours <= Int.MAX_VALUE / 3_600L) { "GTFS time '$value' exceeds the supported range." }
    require(minutes != null && minutes in 0..59) { "Invalid minute in GTFS time '$value'." }
    require(seconds != null && seconds in 0..59) { "Invalid second in GTFS time '$value'." }

    val totalSeconds = hours * 3_600 + minutes * 60 + seconds
    require(totalSeconds <= Int.MAX_VALUE) { "GTFS time '$value' exceeds the supported range." }
    return totalSeconds.toInt()
}

internal fun parseCsvLine(line: String): List<String> {
    val values = mutableListOf<String>()
    val value = StringBuilder()
    var insideQuotes = false
    var index = 0

    while (index < line.length) {
        val character = line[index]
        when {
            character == '"' && insideQuotes && index + 1 < line.length && line[index + 1] == '"' -> {
                value.append('"')
                index++
            }

            character == '"' -> insideQuotes = !insideQuotes
            character == ',' && !insideQuotes -> {
                values += value.toString()
                value.setLength(0)
            }

            else -> value.append(character)
        }
        index++
    }

    require(!insideQuotes) { "Unclosed quoted CSV field." }
    values += value.toString()
    return values
}

private fun List<String>.required(
    columns: Map<String, Int>,
    column: String,
    entryName: String,
    rowNumber: Int
): String {
    val index = columns[column] ?: throw IOException("$entryName is missing the '$column' column.")
    return getOrNull(index)?.trim()?.takeIf(String::isNotEmpty)
        ?: throw IOException("$entryName row $rowNumber has an empty '$column' value.")
}

private fun List<String>.optional(
    columns: Map<String, Int>,
    column: String
): String? = columns[column]?.let { getOrNull(it)?.trim()?.takeIf(String::isNotEmpty) }

private fun String.toIntOrInvalid(entryName: String, column: String, rowNumber: Int): Int =
    toIntOrNull() ?: throw IOException("$entryName row $rowNumber has an invalid '$column' value.")

private fun String.toDoubleOrInvalid(entryName: String, column: String, rowNumber: Int): Double =
    toDoubleOrNull()?.takeIf(Double::isFinite)
        ?: throw IOException("$entryName row $rowNumber has an invalid '$column' value.")

private fun String.toGtfsSeconds(entryName: String, rowNumber: Int): Int =
    try {
        parseGtfsTime(this)
    } catch (exception: IllegalArgumentException) {
        throw IOException("$entryName row $rowNumber has an invalid time value.", exception)
    }
