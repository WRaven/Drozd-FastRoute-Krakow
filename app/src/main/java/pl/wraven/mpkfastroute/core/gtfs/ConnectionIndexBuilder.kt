package pl.wraven.mpkfastroute.core.gtfs

import androidx.room.withTransaction
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import pl.wraven.mpkfastroute.core.database.AppDatabase
import pl.wraven.mpkfastroute.core.database.ConnectionEntity

class ConnectionIndexBuilder @Inject constructor(
    private val database: AppDatabase
) {
    suspend fun rebuild(onProgress: (String) -> Unit = {}) =
        rebuildWithProgress { _, message -> onProgress(message) }

    suspend fun rebuildWithProgress(
        onProgress: suspend (Int, String) -> Unit
    ) = withContext(Dispatchers.IO) {
        database.withTransaction {
            rebuildWithinTransaction(
                totalStopTimes = database.stopTimeDao().count(),
                onProgress = onProgress
            )
        }
    }

    suspend fun rebuildWithinTransaction(onProgress: (String) -> Unit = {}) {
        rebuildWithinTransaction(database.stopTimeDao().count()) { _, message ->
            onProgress(message)
        }
    }

    suspend fun rebuildWithinTransaction(
        totalStopTimes: Int,
        onProgress: suspend (Int, String) -> Unit
    ) {
        val startedAt = System.nanoTime()
        onProgress(INDEX_START_PERCENT, "Budowanie lokalnej siatki połączeń...")
        val connectionDao = database.connectionDao()
        val sqliteDatabase = database.openHelper.writableDatabase
        sqliteDatabase.execSQL("DROP INDEX IF EXISTS `$CONNECTION_INDEX_NAME`")
        connectionDao.deleteAll()

        val cursor = sqliteDatabase.query(SOURCE_QUERY)
        var previousTripId: String? = null
        var previousStopId: String? = null
        var previousDepartureTime = 0
        var previousServiceId = ""
        var previousRouteShortName = ""
        var processedStopTimes = 0
        var insertedConnections = 0
        val batch = ArrayList<ConnectionEntity>(INSERT_BATCH_SIZE)

        try {
            while (cursor.moveToNext()) {
                val tripId = cursor.getString(0)
                val serviceId = cursor.getString(1)
                val routeShortName = cursor.getString(2)
                val stopId = cursor.getString(3)
                val departureTime = cursor.getInt(4)

                if (tripId == previousTripId) {
                    batch += ConnectionEntity(
                        tripId = tripId,
                        serviceId = previousServiceId,
                        routeShortName = previousRouteShortName,
                        departureStopId = checkNotNull(previousStopId),
                        arrivalStopId = stopId,
                        departureTime = previousDepartureTime,
                        arrivalTime = cursor.getInt(5)
                    )
                }

                previousTripId = tripId
                previousStopId = stopId
                previousDepartureTime = departureTime
                previousServiceId = serviceId
                previousRouteShortName = routeShortName
                processedStopTimes += 1

                if (batch.size == INSERT_BATCH_SIZE) {
                    connectionDao.insertAll(batch)
                    insertedConnections += batch.size
                    batch.clear()
                }
                if (processedStopTimes % PROGRESS_INTERVAL == 0) {
                    val progressPercent = scaleProgress(
                        INDEX_START_PERCENT,
                        INDEX_END_PERCENT,
                        processedStopTimes.toLong(),
                        totalStopTimes.toLong()
                    )
                    val progress = "Indeksowanie: $processedStopTimes wpisów stop_times, " +
                        "$insertedConnections segmentów."
                    onProgress(progressPercent, progress)
                }
            }
        } finally {
            cursor.close()
        }

        if (batch.isNotEmpty()) {
            connectionDao.insertAll(batch)
            insertedConnections += batch.size
        }
        check(insertedConnections > 0) {
            "The GTFS schedule does not contain any adjacent stop connections."
        }

        onProgress(INDEX_END_PERCENT, "Tworzenie indeksu SQLite do wyszukiwania połączeń...")
        val indexStartedAt = System.nanoTime()
        sqliteDatabase.execSQL(CREATE_CONNECTION_INDEX_SQL)
        val indexElapsedSeconds =
            (System.nanoTime() - indexStartedAt) / NANOS_PER_SECOND
        val elapsedSeconds = (System.nanoTime() - startedAt) / NANOS_PER_SECOND
        val completion = "Indeks połączeń gotowy: $insertedConnections segmentów " +
            "w ${elapsedSeconds}s (indeks SQLite: ${indexElapsedSeconds}s)."
        onProgress(99, completion)
    }

    private companion object {
        const val CONNECTION_INDEX_NAME =
            "index_connections_departureTime_serviceId_tripId"
        const val INSERT_BATCH_SIZE = 5_000
        const val PROGRESS_INTERVAL = 50_000
        const val INDEX_START_PERCENT = 82
        const val INDEX_END_PERCENT = 98
        const val NANOS_PER_SECOND = 1_000_000_000L
        const val CREATE_CONNECTION_INDEX_SQL = """
            CREATE INDEX IF NOT EXISTS `index_connections_departureTime_serviceId_tripId`
            ON `connections` (`departureTime`, `serviceId`, `tripId`)
        """

        const val SOURCE_QUERY = """
            SELECT stopTime.tripId,
                   trip.serviceId,
                   route.routeShortName,
                   stopTime.stopId,
                   stopTime.departureTime,
                   stopTime.arrivalTime
            FROM stop_times AS stopTime
            JOIN trips AS trip ON trip.tripId = stopTime.tripId
            JOIN routes AS route ON route.routeId = trip.routeId
            ORDER BY stopTime.tripId, stopTime.stopSequence
        """
    }
}
