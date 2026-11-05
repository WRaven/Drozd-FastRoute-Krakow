package pl.wraven.mpkfastroute.core.csa

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import pl.wraven.mpkfastroute.core.database.AppDatabase
import pl.wraven.mpkfastroute.core.gtfs.ServiceCalendarResolver

class RoutePlanner @Inject constructor(
    private val database: AppDatabase,
    private val csaEngine: CsaEngine,
    private val footpathGenerator: FootpathGenerator
) {
    suspend fun findEarliestJourney(
        originStopId: String,
        destinationStopId: String,
        serviceDate: LocalDate,
        startTimeSeconds: Int
    ): CsaJourney? {
        require(startTimeSeconds >= 0) { "Start time must not be negative." }
        val endTimeSeconds = Math.addExact(startTimeSeconds, SEARCH_WINDOW_SECONDS)
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

        val connections = database.stopTimeDao()
            .getConnectionsBetween(startTimeSeconds, endTimeSeconds)
            .asSequence()
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
        if (connections.isEmpty()) {
            return null
        }

        val footpaths = footpathGenerator.generate(database.stopDao().getAll())
        return csaEngine.findEarliestJourney(
            connections = connections,
            footpaths = footpaths,
            originStopId = originStopId,
            destinationStopId = destinationStopId,
            startTime = startTimeSeconds
        )
    }

    private companion object {
        const val SEARCH_WINDOW_SECONDS = 2 * 60 * 60
    }
}
