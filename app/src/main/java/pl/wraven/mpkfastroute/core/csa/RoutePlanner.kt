package pl.wraven.mpkfastroute.core.csa

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import pl.wraven.mpkfastroute.core.database.AppDatabase
import pl.wraven.mpkfastroute.core.database.StopEntity
import pl.wraven.mpkfastroute.core.gtfs.ServiceCalendarResolver

class RoutePlanner @Inject constructor(
    private val database: AppDatabase,
    private val csaEngine: CsaEngine,
    private val footpathGenerator: FootpathGenerator
) {
    @Volatile
    private var footpathCache: FootpathCache? = null

    private val footpathCacheLock = Any()

    suspend fun findEarliestJourney(
        originStopId: String,
        destinationStopId: String,
        serviceDate: LocalDate,
        startTimeSeconds: Int,
        searchWindowSeconds: Int = DEFAULT_SEARCH_WINDOW_SECONDS
    ): CsaJourney? = findEarliestJourney(
        originStopIds = setOf(originStopId),
        destinationStopIds = setOf(destinationStopId),
        serviceDate = serviceDate,
        startTimeSeconds = startTimeSeconds,
        searchWindowSeconds = searchWindowSeconds
    )

    suspend fun findEarliestJourney(
        originStopIds: Set<String>,
        destinationStopIds: Set<String>,
        serviceDate: LocalDate,
        startTimeSeconds: Int,
        searchWindowSeconds: Int = DEFAULT_SEARCH_WINDOW_SECONDS
    ): CsaJourney? {
        require(startTimeSeconds >= 0) { "Start time must not be negative." }
        require(originStopIds.isNotEmpty()) { "At least one origin stop is required." }
        require(destinationStopIds.isNotEmpty()) { "At least one destination stop is required." }
        require(searchWindowSeconds in 1..MAX_SEARCH_WINDOW_SECONDS) {
            "Search window must be between 1 second and 2 hours."
        }
        val endTimeSeconds = Math.addExact(startTimeSeconds, searchWindowSeconds)
        val serviceDateString = serviceDate.format(DateTimeFormatter.BASIC_ISO_DATE)
        val calendarDao = database.serviceCalendarDao()
        val activeServiceIds = ServiceCalendarResolver.activeServiceIds(
            date = serviceDate,
            calendars = calendarDao.getForDate(serviceDateString),
            exceptions = calendarDao.getExceptionsForDate(serviceDateString)
        )
        if (activeServiceIds.isEmpty()) {
            return null
        }

        val rows = database.stopTimeDao()
            .getConnectionsBetween(startTimeSeconds, endTimeSeconds)
        val connections = withContext(Dispatchers.Default) {
            rows.asSequence()
                .filter { it.serviceId in activeServiceIds }
                .map {
                    Connection(
                        departureStopId = it.departureStopId,
                        arrivalStopId = it.arrivalStopId,
                        departureTime = it.departureTime,
                        arrivalTime = it.arrivalTime,
                        tripId = it.tripId,
                        routeShortName = it.routeShortName
                    )
                }
                .toList()
        }
        if (connections.isEmpty()) {
            return null
        }

        val stops = database.stopDao().getAll()
        val footpaths = withContext(Dispatchers.Default) {
            val cached = footpathCache
            if (cached?.stops == stops) {
                cached.footpaths
            } else {
                synchronized(footpathCacheLock) {
                    val latest = footpathCache
                    if (latest?.stops == stops) {
                        latest.footpaths
                    } else {
                        footpathGenerator.generate(stops).also { generated ->
                            footpathCache = FootpathCache(stops, generated)
                        }
                    }
                }
            }
        }
        return withContext(Dispatchers.Default) {
            csaEngine.findEarliestJourney(
                connections = connections,
                footpaths = footpaths,
                originStopIds = originStopIds,
                destinationStopIds = destinationStopIds,
                startTime = startTimeSeconds
            )
        }
    }

    private data class FootpathCache(
        val stops: List<StopEntity>,
        val footpaths: List<Footpath>
    )

    private companion object {
        const val DEFAULT_SEARCH_WINDOW_SECONDS = 2 * 60 * 60
        const val MAX_SEARCH_WINDOW_SECONDS = DEFAULT_SEARCH_WINDOW_SECONDS
    }
}
