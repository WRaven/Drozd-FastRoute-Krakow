package pl.wraven.mpkfastroute.core.csa

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import pl.wraven.mpkfastroute.core.database.AppDatabase
import pl.wraven.mpkfastroute.core.database.StopEntity
import pl.wraven.mpkfastroute.core.diagnostics.SearchDiagnostics
import pl.wraven.mpkfastroute.core.gtfs.ServiceCalendarResolver

class RoutePlanner @Inject constructor(
    private val database: AppDatabase,
    private val csaEngine: CsaEngine,
    private val footpathGenerator: FootpathGenerator,
    private val diagnostics: SearchDiagnostics
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
    ): CsaJourney? = findJourneys(
        originAccesses = originStopIds.map { StopAccess(it, 0.0, 0) },
        destinationAccesses = destinationStopIds.map { StopAccess(it, 0.0, 0) },
        serviceDate = serviceDate,
        startTimeSeconds = startTimeSeconds,
        searchWindowSeconds = searchWindowSeconds,
        maxResults = 1
    ).firstOrNull()

    suspend fun findJourneys(
        originAccesses: List<StopAccess>,
        destinationAccesses: List<StopAccess>,
        serviceDate: LocalDate,
        startTimeSeconds: Int,
        searchWindowSeconds: Int = DEFAULT_SEARCH_WINDOW_SECONDS,
        maxResults: Int = DEFAULT_MAX_RESULTS,
        arrivalDeadline: Int? = null
    ): List<CsaJourney> {
        require(startTimeSeconds >= 0) { "Start time must not be negative." }
        require(originAccesses.isNotEmpty()) { "At least one origin stop is required." }
        require(destinationAccesses.isNotEmpty()) { "At least one destination stop is required." }
        require(maxResults > 0) { "At least one journey result is required." }
        require(arrivalDeadline == null || arrivalDeadline >= startTimeSeconds) {
            "Arrival deadline must not be before the search start."
        }
        require(searchWindowSeconds in 1..MAX_SEARCH_WINDOW_SECONDS) {
            "Search window must be between 1 second and 2 hours."
        }
        val endTimeSeconds = Math.addExact(startTimeSeconds, searchWindowSeconds)
        val serviceDateString = serviceDate.format(DateTimeFormatter.BASIC_ISO_DATE)
        val searchStartedAt = System.nanoTime()
        diagnostics.record(
            "Wyszukiwanie rozpoczęte: ${originAccesses.size} początkowych i " +
                "${destinationAccesses.size} końcowych przystanków; okno " +
                "$searchWindowSeconds s."
        )
        val calendarDao = database.serviceCalendarDao()
        val activeServiceIds = ServiceCalendarResolver.activeServiceIds(
            date = serviceDate,
            calendars = calendarDao.getForDate(serviceDateString),
            exceptions = calendarDao.getExceptionsForDate(serviceDateString)
        )
        if (activeServiceIds.isEmpty()) {
            diagnostics.record("Brak aktywnych usług dla wybranego dnia.")
            return emptyList()
        }
        diagnostics.record("Aktywne usługi: ${activeServiceIds.size}.")

        diagnostics.record(
            "Odczyt indeksu połączeń: $startTimeSeconds–$endTimeSeconds s."
        )
        val connectionQueryStartedAt = System.nanoTime()
        val rows = database.connectionDao()
            .getConnectionsBetween(startTimeSeconds, endTimeSeconds)
        diagnostics.record(
            "Indeks zwrócił ${rows.size} połączeń w " +
                "${elapsedMilliseconds(connectionQueryStartedAt)} ms."
        )
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
        diagnostics.record("Po filtrze kalendarza pozostało ${connections.size} połączeń.")
        if (connections.isEmpty() && originAccesses.none { origin ->
                destinationAccesses.any { it.stopId == origin.stopId }
            }
        ) {
            diagnostics.record("Brak połączeń w oknie czasowym; zakończono wyszukiwanie.")
            return emptyList()
        }

        diagnostics.record("Budowanie sieci pieszych przejść.")
        val footpathStartedAt = System.nanoTime()
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
        diagnostics.record(
            "Przystanki: ${stops.size}; przejścia piesze: ${footpaths.size}; " +
                "${elapsedMilliseconds(footpathStartedAt)} ms."
        )
        diagnostics.record("Rozpoczęto skanowanie CSA.")
        return withContext(Dispatchers.Default) {
            val journeys = csaEngine.findJourneys(
                connections = connections,
                footpaths = footpaths,
                originAccesses = originAccesses,
                destinationAccesses = destinationAccesses,
                startTime = startTimeSeconds,
                maxResults = maxResults,
                arrivalDeadline = arrivalDeadline,
                onProgress = { progress ->
                    diagnostics.record(
                        "CSA: ${progress.scannedConnections}/${progress.totalConnections} " +
                            "połączeń, ${progress.reachedStops} osiągniętych przystanków, " +
                            "${progress.propagatedStates} stanów po przejściu pieszym."
                    )
                }
            )
            diagnostics.record(
                "CSA zakończone: ${journeys.size} tras w " +
                    "${elapsedMilliseconds(searchStartedAt)} ms."
            )
            journeys
        }
    }

    private fun elapsedMilliseconds(startedAt: Long): Long =
        (System.nanoTime() - startedAt) / NANOS_PER_MILLISECOND

    private data class FootpathCache(
        val stops: List<StopEntity>,
        val footpaths: List<Footpath>
    )

    private companion object {
        const val NANOS_PER_MILLISECOND = 1_000_000L
        const val DEFAULT_SEARCH_WINDOW_SECONDS = 2 * 60 * 60
        const val MAX_SEARCH_WINDOW_SECONDS = DEFAULT_SEARCH_WINDOW_SECONDS
        const val DEFAULT_MAX_RESULTS = 20
    }
}
