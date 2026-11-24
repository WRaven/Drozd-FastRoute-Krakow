package pl.wraven.mpkfastroute.core.live

import androidx.room.withTransaction
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import pl.wraven.mpkfastroute.core.database.AppDatabase
import pl.wraven.mpkfastroute.core.gtfs.ServiceCalendarResolver

class TtssDepartureRepository @Inject constructor(
    private val database: AppDatabase,
    private val ttssClient: TtssClient
) {
    suspend fun getDepartures(
        stopId: String,
        serviceDate: LocalDate,
        startTimeSeconds: Int,
        nowTimeSeconds: Int
    ): LiveDeparturesResult {
        require(startTimeSeconds >= 0) { "Start time must not be negative." }
        val endTimeSeconds = Math.addExact(startTimeSeconds, SEARCH_WINDOW_SECONDS)
        val dateString = serviceDate.format(DateTimeFormatter.BASIC_ISO_DATE)
        val (stop, staticDepartures) = database.withTransaction {
            val stop = database.stopDao().getById(stopId)
                ?: throw IllegalStateException("The selected departure stop no longer exists.")
            val calendarDao = database.serviceCalendarDao()
            val activeServiceIds = ServiceCalendarResolver.activeServiceIds(
                date = serviceDate,
                calendars = calendarDao.getForDate(dateString),
                exceptions = calendarDao.getExceptionsForDate(dateString)
            )
            if (activeServiceIds.isEmpty()) {
                return@withTransaction stop to emptyList()
            }
            val departures = database.stopTimeDao()
                .getDeparturesAtStopBetween(stopId, startTimeSeconds, endTimeSeconds)
                .asSequence()
                .filter { it.serviceId in activeServiceIds }
                .map {
                    StaticDeparture(
                        routeShortName = it.routeShortName,
                        direction = it.direction.orEmpty(),
                        departureTime = it.departureTime
                    )
                }
                .toList()
            stop to departures
        }
        if (staticDepartures.isEmpty()) {
            return LiveDeparturesResult(emptyList(), null)
        }

        return ttssClient.getDepartures(
            stop = stop,
            staticDepartures = staticDepartures,
            nowTimeSeconds = nowTimeSeconds
        )
    }

    private companion object {
        const val SEARCH_WINDOW_SECONDS = 2 * 60 * 60
    }
}
