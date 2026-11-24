package pl.wraven.mpkfastroute.core.csa

import androidx.room.withTransaction
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import pl.wraven.mpkfastroute.core.database.AppDatabase
import pl.wraven.mpkfastroute.core.database.GtfsFastConnectionRow
import pl.wraven.mpkfastroute.core.database.StopEntity
import pl.wraven.mpkfastroute.core.diagnostics.SearchDiagnostics
import pl.wraven.mpkfastroute.core.gtfs.GtfsServiceClock
import pl.wraven.mpkfastroute.core.gtfs.ServiceCalendarResolver

@Singleton
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
        startDateTime: LocalDateTime,
        searchWindowSeconds: Int = DEFAULT_SEARCH_WINDOW_SECONDS,
        maxResults: Int = DEFAULT_MAX_RESULTS,
        arrivalDeadline: LocalDateTime? = null
    ): List<CsaJourney> {
        require(searchWindowSeconds in 1..MAX_SEARCH_WINDOW_SECONDS) {
            "Search window must be between 1 second and 2 hours."
        }
        val serviceWindows = GtfsServiceClock.window(
            startDateTime = startDateTime,
            durationSeconds = searchWindowSeconds,
            arrivalDeadline = arrivalDeadline
        )
        val journeys = mutableListOf<CsaJourney>()
        for (window in serviceWindows) {
            val serviceDayOffset = window.serviceDayOffsetSeconds
            findJourneys(
                originAccesses = originAccesses,
                destinationAccesses = destinationAccesses,
                serviceDate = window.serviceDate,
                startTimeSeconds = window.startTimeSeconds,
                searchWindowSeconds = window.durationSeconds,
                maxResults = maxResults,
                arrivalDeadline = window.arrivalDeadlineSeconds
            ).forEach { journey ->
                journeys += shiftJourneyTimes(journey, serviceDayOffset)
            }
        }
        val journeyComparator = if (arrivalDeadline == null) {
            compareBy<CsaJourney> { it.estimatedArrivalTime }
                .thenBy { it.totalWalkingDistanceMeters }
                .thenBy { it.transferPenaltySeconds }
        } else {
            compareByDescending<CsaJourney> { it.departureTime }
                .thenBy { it.totalDurationSeconds }
                .thenBy { it.totalWalkingDistanceMeters }
                .thenBy { it.transferPenaltySeconds }
        }
        return journeys.sortedWith(journeyComparator).take(maxResults)
    }

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
        val connectionQueryStartedAt = System.nanoTime()
        val snapshot = database.withTransaction {
            val calendarDao = database.serviceCalendarDao()
            val activeServiceIds = ServiceCalendarResolver.activeServiceIds(
                date = serviceDate,
                calendars = calendarDao.getForDate(serviceDateString),
                exceptions = calendarDao.getExceptionsForDate(serviceDateString)
            )
            if (activeServiceIds.isEmpty()) {
                return@withTransaction null
            }
            val rows = if (activeServiceIds.size <= SQL_IN_CLAUSE_BATCH_SIZE) {
                database.connectionDao().getFastConnectionsBetweenForServices(
                    startTime = startTimeSeconds,
                    endTime = endTimeSeconds,
                    serviceIds = activeServiceIds.toList()
                )
            } else {
                database.connectionDao().getFastConnectionsBetween(
                    startTime = startTimeSeconds,
                    endTime = endTimeSeconds
                )
            }
            val stops = database.stopDao().getAll()
            SearchSnapshot(activeServiceIds, rows, stops)
        }
        if (snapshot == null) {
            diagnostics.record("Brak aktywnych usług dla wybranego dnia.")
            return emptyList()
        }
        diagnostics.record("Aktywne usługi: ${snapshot.activeServiceIds.size}.")
        diagnostics.record(
            "Indeks zwrócił ${snapshot.rows.size} połączeń w " +
                "${elapsedMilliseconds(connectionQueryStartedAt)} ms."
        )
        var skippedInvalidConnections = 0
        val connections = withContext(Dispatchers.Default) {
            val result = ArrayList<Connection>(snapshot.rows.size)
            for (row in snapshot.rows) {
                if (row.serviceId !in snapshot.activeServiceIds) continue
                if (row.departureTime < 0 || row.arrivalTime < row.departureTime) {
                    skippedInvalidConnections += 1
                    continue
                }
                result += Connection(
                    departureStopId = row.departureStopId,
                    arrivalStopId = row.arrivalStopId,
                    departureTime = row.departureTime,
                    arrivalTime = row.arrivalTime,
                    tripId = row.tripId,
                    routeShortName = row.routeShortName,
                    direction = ""
                )
            }
            result
        }
        if (skippedInvalidConnections > 0) {
            diagnostics.record(
                "Pominięto $skippedInvalidConnections nieprawidłowych segmentów w rozkładzie."
            )
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
        val stops = snapshot.stops
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
        val rawJourneys = withContext(Dispatchers.Default) {
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
        return enrichJourneyDirections(rawJourneys)
    }

    private suspend fun enrichJourneyDirections(journeys: List<CsaJourney>): List<CsaJourney> {
        val tripIds = journeys.asSequence()
            .flatMap { it.steps.asSequence() }
            .filterIsInstance<JourneyStep.Transit>()
            .map { it.connection.tripId }
            .distinct()
            .toList()
        if (tripIds.isEmpty()) return journeys

        val directionsByTripId = tripIds
            .chunked(SQL_IN_CLAUSE_BATCH_SIZE)
            .flatMap { chunk -> database.connectionDao().getTripDirectionsByTripIds(chunk) }
            .associate { it.tripId to it.direction.orEmpty() }
        if (directionsByTripId.isEmpty()) return journeys

        return journeys.map { journey ->
            journey.copy(
                steps = journey.steps.map { step ->
                    when (step) {
                        is JourneyStep.Transit -> {
                            val direction = directionsByTripId[step.connection.tripId]
                            if (direction.isNullOrEmpty()) {
                                step
                            } else {
                                step.copy(
                                    connection = step.connection.copy(direction = direction)
                                )
                            }
                        }

                        is JourneyStep.Walk -> step
                    }
                }
            )
        }
    }

    private fun shiftJourneyTimes(journey: CsaJourney, offsetSeconds: Int): CsaJourney {
        if (offsetSeconds == 0) return journey
        return journey.copy(
            departureTime = Math.addExact(journey.departureTime, offsetSeconds),
            arrivalTime = Math.addExact(journey.arrivalTime, offsetSeconds),
            steps = journey.steps.map { step ->
                when (step) {
                    is JourneyStep.Transit -> step.copy(
                        connection = step.connection.copy(
                            departureTime = Math.addExact(
                                step.connection.departureTime,
                                offsetSeconds
                            ),
                            arrivalTime = Math.addExact(
                                step.connection.arrivalTime,
                                offsetSeconds
                            )
                        )
                    )

                    is JourneyStep.Walk -> step
                }
            }
        )
    }

    private fun elapsedMilliseconds(startedAt: Long): Long =
        (System.nanoTime() - startedAt) / NANOS_PER_MILLISECOND

    private data class SearchSnapshot(
        val activeServiceIds: Set<String>,
        val rows: List<GtfsFastConnectionRow>,
        val stops: List<StopEntity>
    )

    private data class FootpathCache(
        val stops: List<StopEntity>,
        val footpaths: List<Footpath>
    )

    private companion object {
        const val NANOS_PER_MILLISECOND = 1_000_000L
        const val DEFAULT_SEARCH_WINDOW_SECONDS = 2 * 60 * 60
        const val MAX_SEARCH_WINDOW_SECONDS = DEFAULT_SEARCH_WINDOW_SECONDS
        const val DEFAULT_MAX_RESULTS = 20
        const val SQL_IN_CLAUSE_BATCH_SIZE = 500
    }
}
