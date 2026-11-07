package pl.wraven.mpkfastroute.core.gtfs

import android.content.Context
import androidx.room.withTransaction
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.BufferedReader
import java.io.File
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.time.LocalDate
import java.time.format.DateTimeParseException
import java.time.format.DateTimeFormatter
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import pl.wraven.mpkfastroute.core.database.AppDatabase
import pl.wraven.mpkfastroute.core.database.CalendarDateEntity
import pl.wraven.mpkfastroute.core.database.RouteEntity
import pl.wraven.mpkfastroute.core.database.ServiceCalendarEntity
import pl.wraven.mpkfastroute.core.database.StopEntity
import pl.wraven.mpkfastroute.core.database.StopTimeEntity
import pl.wraven.mpkfastroute.core.database.TripEntity

const val GTFS_KRAKOW_URL = "https://gtfs.ztp.krakow.pl/GTFS_KRK.zip"
internal const val GTFS_UPDATE_PREFERENCES = "gtfs_update_metadata"
internal const val GTFS_LAST_CHECKED_AT_KEY = "last_checked_at"

private const val BATCH_SIZE = 5_000
private const val PROGRESS_BATCH_SIZE = 50_000
private const val DOWNLOAD_PROGRESS_INTERVAL_BYTES = 5_000_000L
private const val CONNECT_TIMEOUT_MILLIS = 30_000
private const val READ_TIMEOUT_MILLIS = 120_000
private const val GTFS_ETAG_KEY = "etag"
private const val GTFS_LAST_MODIFIED_KEY = "last_modified"
private const val DOWNLOAD_START_PERCENT = 0
private const val DOWNLOAD_END_PERCENT = 25
private const val STOP_TIMES_START_PERCENT = 40
private const val STOP_TIMES_END_PERCENT = 82

data class GtfsScheduleProgress(
    val percent: Int,
    val message: String
)

@Singleton
class GtfsZipParser @Inject constructor(
    private val database: AppDatabase,
    private val connectionIndexBuilder: ConnectionIndexBuilder,
    @param:ApplicationContext private val context: Context
) {
    private val updateMutex = Mutex()

    suspend fun importSchedule(
        onProgress: (String) -> Unit = {}
    ) {
        updateSchedule(forceDownload = true) { onProgress(it.message) }
    }

    suspend fun importScheduleIfUpdated(
        onProgress: (String) -> Unit = {}
    ): Boolean = updateSchedule(forceDownload = false) { onProgress(it.message) }

    suspend fun updateSchedule(
        forceDownload: Boolean,
        onProgress: suspend (GtfsScheduleProgress) -> Unit
    ): Boolean = updateMutex.withLock {
        val hasUsableSchedule = database.stopDao().count() > 0 &&
            database.stopTimeDao().count() > 0 &&
            database.connectionDao().count() > 0
        importScheduleInternal(
            onlyIfUpdated = !forceDownload && hasUsableSchedule,
            onProgress = onProgress
        )
    }

    suspend fun rebuildConnectionIndexWithProgress(
        onProgress: suspend (GtfsScheduleProgress) -> Unit
    ) = updateMutex.withLock {
        connectionIndexBuilder.rebuildWithProgress { percent, message ->
            onProgress(GtfsScheduleProgress(percent, message))
        }
    }

    private suspend fun importScheduleInternal(
        onlyIfUpdated: Boolean,
        onProgress: suspend (GtfsScheduleProgress) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
        onProgress(GtfsScheduleProgress(0, "Sprawdzanie rozkładu ZTP Kraków..."))
        val downloadedArchive = downloadArchive(onProgress, onlyIfUpdated)
        if (downloadedArchive == null) {
            markScheduleChecked()
            onProgress(GtfsScheduleProgress(100, "Rozkład GTFS jest aktualny."))
            return@withContext false
        }
        val archive = downloadedArchive.file
        try {
            onProgress(GtfsScheduleProgress(27, "Odczytywanie przystanków, tras i kalendarza..."))
            val stops = mutableListOf<StopEntity>()
            val routes = mutableListOf<RouteEntity>()
            val trips = mutableListOf<TripEntity>()
            val calendars = mutableListOf<ServiceCalendarEntity>()
            val calendarDates = mutableListOf<CalendarDateEntity>()
            readLookupTables(archive, stops, routes, trips, calendars, calendarDates)

            require(stops.isNotEmpty()) { "GTFS archive contains no stops." }
            require(routes.isNotEmpty()) { "GTFS archive contains no routes." }
            require(trips.isNotEmpty()) { "GTFS archive contains no trips." }
            require(calendars.isNotEmpty() || calendarDates.isNotEmpty()) {
                "GTFS archive contains no service calendar data."
            }
            onProgress(GtfsScheduleProgress(35, "Zapisuję dane rozkładu..."))

            val stopIds = stops.mapTo(HashSet(stops.size)) { it.stopId }
            val tripIds = trips.mapTo(HashSet(trips.size)) { it.tripId }
            val sqliteDatabase = database.openHelper.writableDatabase
            sqliteDatabase.execSQL("PRAGMA foreign_keys = OFF")

            try {
                onProgress(GtfsScheduleProgress(35, "Zapisywanie danych i indeksowanie bazy..."))
                database.withTransaction {
                    val stopDao = database.stopDao()
                    val routeDao = database.routeDao()
                    val tripDao = database.tripDao()
                    val calendarDao = database.serviceCalendarDao()
                    val stopTimeDao = database.stopTimeDao()

                    stopTimeDao.deleteAll()
                    tripDao.deleteAll()
                    routeDao.deleteAll()
                    stopDao.deleteAll()
                    calendarDao.deleteAllExceptions()
                    calendarDao.deleteAll()

                    for (batch in stops.chunked(BATCH_SIZE)) {
                        stopDao.insertAll(batch)
                    }
                    for (batch in routes.chunked(BATCH_SIZE)) {
                        routeDao.insertAll(batch)
                    }
                    for (batch in trips.chunked(BATCH_SIZE)) {
                        tripDao.insertAll(batch)
                    }
                    for (batch in calendars.chunked(BATCH_SIZE)) {
                        calendarDao.insertAll(batch)
                    }
                    for (batch in calendarDates.chunked(BATCH_SIZE)) {
                        calendarDao.insertAllExceptions(batch)
                    }
                    onProgress(GtfsScheduleProgress(40, "Importuję godziny odjazdów..."))

                    val stopTimeCount = readStopTimes(
                        archive = archive,
                        stopIds = stopIds,
                        tripIds = tripIds,
                        totalBytes = archiveEntrySize(archive, "stop_times.txt"),
                        insertBatch = stopTimeDao::insertAll
                    ) { count, percent ->
                        onProgress(
                            GtfsScheduleProgress(
                                percent,
                                "Import GTFS: $count wpisów stop_times."
                            )
                        )
                    }
                    check(stopTimeCount > 0) { "GTFS archive contains no stop times." }
                    onProgress(GtfsScheduleProgress(82, "Buduję indeks połączeń..."))
                    connectionIndexBuilder.rebuildWithinTransaction(stopTimeCount) { percent, message ->
                        onProgress(GtfsScheduleProgress(percent, message))
                    }
                }
            } finally {
                sqliteDatabase.execSQL("PRAGMA foreign_keys = ON")
            }
            onProgress(GtfsScheduleProgress(99, "Kończę aktualizację rozkładu..."))
            saveDownloadMetadata(downloadedArchive)
            onProgress(GtfsScheduleProgress(100, "Rozkład GTFS jest gotowy."))
            true
        } finally {
            archive.delete()
        }
    }

    suspend fun rebuildConnectionIndex(onProgress: (String) -> Unit = {}) {
        updateMutex.withLock { connectionIndexBuilder.rebuild(onProgress) }
    }

    private suspend fun downloadArchive(
        onProgress: suspend (GtfsScheduleProgress) -> Unit,
        onlyIfUpdated: Boolean
    ): DownloadedArchive? {
        val archive = File.createTempFile("gtfs-krakow-", ".zip", context.cacheDir)
        var downloadCompleted = false
        try {
            val connection = URL(GTFS_KRAKOW_URL).openConnection() as HttpURLConnection
            connection.connectTimeout = CONNECT_TIMEOUT_MILLIS
            connection.readTimeout = READ_TIMEOUT_MILLIS
            connection.instanceFollowRedirects = true
            if (onlyIfUpdated) {
                val preferences = context.getSharedPreferences(
                    GTFS_UPDATE_PREFERENCES,
                    Context.MODE_PRIVATE
                )
                preferences.getString(GTFS_ETAG_KEY, null)?.let {
                    connection.setRequestProperty("If-None-Match", it)
                }
                val lastModified = preferences.getLong(GTFS_LAST_MODIFIED_KEY, 0L)
                if (lastModified > 0L) {
                    connection.ifModifiedSince = lastModified
                }
            }

            var eTag: String? = null
            var lastModified = 0L
            try {
                val responseCode = connection.responseCode
                if (responseCode == HttpURLConnection.HTTP_NOT_MODIFIED) {
                    return null
                }
                if (responseCode !in 200..299) {
                    throw IOException("GTFS download failed with HTTP $responseCode.")
                }

                eTag = connection.getHeaderField("ETag")
                lastModified = connection.getHeaderFieldDate("Last-Modified", 0L)
                val contentLength = connection.contentLengthLong
                var downloadedBytes = 0L
                var nextDownloadPercent = 5
                var nextProgressBytes = DOWNLOAD_PROGRESS_INTERVAL_BYTES
                onProgress(GtfsScheduleProgress(0, "Pobieranie rozkładu GTFS z ZTP..."))
                connection.inputStream.buffered().use { input ->
                    archive.outputStream().buffered().use { output ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        while (true) {
                            val bytesRead = input.read(buffer)
                            if (bytesRead < 0) break
                            output.write(buffer, 0, bytesRead)
                            downloadedBytes += bytesRead

                            if (contentLength > 0) {
                                val progressPercent =
                                    (downloadedBytes * 100L / contentLength)
                                        .toInt()
                                        .coerceAtMost(100)
                                if (progressPercent >= nextDownloadPercent) {
                                    val overallPercent = scaleProgress(
                                        DOWNLOAD_START_PERCENT,
                                        DOWNLOAD_END_PERCENT,
                                        downloadedBytes,
                                        contentLength
                                    )
                                    onProgress(
                                        GtfsScheduleProgress(
                                            overallPercent,
                                            "Pobieranie rozkładu GTFS z ZTP..."
                                        )
                                    )
                                    nextDownloadPercent = (progressPercent / 5 + 1) * 5
                                }
                            } else if (downloadedBytes >= nextProgressBytes) {
                                onProgress(
                                    GtfsScheduleProgress(
                                        DOWNLOAD_START_PERCENT,
                                        "Pobrano ${downloadedBytes / 1_000_000} MB rozkładu GTFS..."
                                    )
                                )
                                nextProgressBytes =
                                    downloadedBytes + DOWNLOAD_PROGRESS_INTERVAL_BYTES
                            }
                        }
                    }
                }
                if (downloadedBytes == 0L) {
                    throw IOException("GTFS download returned an empty archive.")
                }
                onProgress(
                    GtfsScheduleProgress(
                        DOWNLOAD_END_PERCENT,
                        "Rozkład pobrany. Przygotowuję import..."
                    )
                )
            } finally {
                connection.disconnect()
            }

            downloadCompleted = true
            return DownloadedArchive(archive, eTag, lastModified)
        } finally {
            if (!downloadCompleted) {
                archive.delete()
            }
        }
    }

    private fun markScheduleChecked() {
        context.getSharedPreferences(GTFS_UPDATE_PREFERENCES, Context.MODE_PRIVATE)
            .edit()
            .putLong(GTFS_LAST_CHECKED_AT_KEY, System.currentTimeMillis())
            .apply()
    }

    private fun saveDownloadMetadata(downloadedArchive: DownloadedArchive) {
        context.getSharedPreferences(GTFS_UPDATE_PREFERENCES, Context.MODE_PRIVATE)
            .edit()
            .putString(GTFS_ETAG_KEY, downloadedArchive.eTag)
            .putLong(GTFS_LAST_MODIFIED_KEY, downloadedArchive.lastModified)
            .putLong(GTFS_LAST_CHECKED_AT_KEY, System.currentTimeMillis())
            .apply()
    }

    private data class DownloadedArchive(
        val file: File,
        val eTag: String?,
        val lastModified: Long
    )

    private suspend fun readLookupTables(
        archive: File,
        stops: MutableList<StopEntity>,
        routes: MutableList<RouteEntity>,
        trips: MutableList<TripEntity>,
        calendars: MutableList<ServiceCalendarEntity>,
        calendarDates: MutableList<CalendarDateEntity>
    ) {
        ZipInputStream(archive.inputStream().buffered(), StandardCharsets.UTF_8).use { zip ->
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

                    "calendar.txt" -> readCsv(zip, entry.name) { columns, values, rowNumber ->
                        calendars += ServiceCalendarEntity(
                            serviceId = values.required(columns, "service_id", entry.name, rowNumber),
                            monday = values.required(columns, "monday", entry.name, rowNumber)
                                .toGtfsFlag(entry.name, "monday", rowNumber),
                            tuesday = values.required(columns, "tuesday", entry.name, rowNumber)
                                .toGtfsFlag(entry.name, "tuesday", rowNumber),
                            wednesday = values.required(columns, "wednesday", entry.name, rowNumber)
                                .toGtfsFlag(entry.name, "wednesday", rowNumber),
                            thursday = values.required(columns, "thursday", entry.name, rowNumber)
                                .toGtfsFlag(entry.name, "thursday", rowNumber),
                            friday = values.required(columns, "friday", entry.name, rowNumber)
                                .toGtfsFlag(entry.name, "friday", rowNumber),
                            saturday = values.required(columns, "saturday", entry.name, rowNumber)
                                .toGtfsFlag(entry.name, "saturday", rowNumber),
                            sunday = values.required(columns, "sunday", entry.name, rowNumber)
                                .toGtfsFlag(entry.name, "sunday", rowNumber),
                            startDate = values.required(columns, "start_date", entry.name, rowNumber)
                                .validateGtfsDate(entry.name, "start_date", rowNumber),
                            endDate = values.required(columns, "end_date", entry.name, rowNumber)
                                .validateGtfsDate(entry.name, "end_date", rowNumber)
                        )
                    }

                    "calendar_dates.txt" -> readCsv(zip, entry.name) { columns, values, rowNumber ->
                        val exceptionType = values.required(
                            columns,
                            "exception_type",
                            entry.name,
                            rowNumber
                        ).toIntOrInvalid(entry.name, "exception_type", rowNumber)
                        if (exceptionType !in 1..2) {
                            throw IOException(
                                "${entry.name} row $rowNumber has an invalid 'exception_type' value."
                            )
                        }
                        calendarDates += CalendarDateEntity(
                            serviceId = values.required(columns, "service_id", entry.name, rowNumber),
                            date = values.required(columns, "date", entry.name, rowNumber)
                                .validateGtfsDate(entry.name, "date", rowNumber),
                            exceptionType = exceptionType
                        )
                    }
                }
                zip.closeEntry()
            }
        }
    }

    private suspend fun readStopTimes(
        archive: File,
        stopIds: Set<String>,
        tripIds: Set<String>,
        totalBytes: Long,
        insertBatch: suspend (List<StopTimeEntity>) -> Unit,
        onBatchProgress: suspend (Int, Int) -> Unit
    ): Int {
        var foundStopTimes = false
        var recordCount = 0
        val batch = ArrayList<StopTimeEntity>(BATCH_SIZE)

        ZipInputStream(archive.inputStream().buffered(), StandardCharsets.UTF_8).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (entry.name.substringAfterLast('/') == "stop_times.txt") {
                    foundStopTimes = true
                    val countingInput = CountingInputStream(zip)
                    readCsv(countingInput, entry.name) { columns, values, rowNumber ->
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
                            if (recordCount % PROGRESS_BATCH_SIZE == 0) {
                                onBatchProgress(
                                    recordCount,
                                    scaleProgress(
                                        STOP_TIMES_START_PERCENT,
                                        STOP_TIMES_END_PERCENT,
                                        countingInput.bytesRead,
                                        totalBytes
                                    )
                                )
                            }
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
        onBatchProgress(recordCount, STOP_TIMES_END_PERCENT)
        return recordCount
    }

    private fun archiveEntrySize(archive: File, entryFileName: String): Long =
        ZipFile(archive).use { zip ->
            val entries = zip.entries()
            var size = -1L
            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                if (entry.name.substringAfterLast('/') == entryFileName) {
                    size = entry.size
                    break
                }
            }
            size
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

internal fun scaleProgress(
    startPercent: Int,
    endPercent: Int,
    completed: Long,
    total: Long
): Int {
    require(startPercent in 0..100 && endPercent in startPercent..100)
    if (total <= 0L) return startPercent
    val boundedCompleted = completed.coerceIn(0L, total)
    return startPercent +
        ((endPercent - startPercent) * boundedCompleted / total).toInt()
}

private class CountingInputStream(input: InputStream) : FilterInputStream(input) {
    var bytesRead: Long = 0
        private set

    override fun read(): Int {
        val result = super.read()
        if (result >= 0) bytesRead += 1
        return result
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        val count = super.read(buffer, offset, length)
        if (count > 0) bytesRead += count
        return count
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

private fun String.toGtfsFlag(entryName: String, column: String, rowNumber: Int): Int =
    toIntOrInvalid(entryName, column, rowNumber).also {
        if (it !in 0..1) {
            throw IOException("$entryName row $rowNumber has an invalid '$column' value.")
        }
    }

private fun String.validateGtfsDate(entryName: String, column: String, rowNumber: Int): String =
    try {
        LocalDate.parse(this, DateTimeFormatter.BASIC_ISO_DATE)
        this
    } catch (exception: DateTimeParseException) {
        throw IOException("$entryName row $rowNumber has an invalid '$column' date.", exception)
    }

private fun String.toDoubleOrInvalid(entryName: String, column: String, rowNumber: Int): Double =
    toDoubleOrNull()?.takeIf(Double::isFinite)
        ?: throw IOException("$entryName row $rowNumber has an invalid '$column' value.")

private fun String.toGtfsSeconds(entryName: String, rowNumber: Int): Int =
    try {
        parseGtfsTime(this)
    } catch (exception: IllegalArgumentException) {
        throw IOException("$entryName row $rowNumber has an invalid time value.", exception)
    }
