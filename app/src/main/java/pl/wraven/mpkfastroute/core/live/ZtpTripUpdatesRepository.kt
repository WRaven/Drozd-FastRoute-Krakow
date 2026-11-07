package pl.wraven.mpkfastroute.core.live

import com.google.transit.realtime.GtfsRealtime
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.request.get
import io.ktor.http.isSuccess
import java.io.IOException
import java.time.LocalDateTime
import java.time.LocalDate
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import javax.inject.Inject

data class LiveTripStopUpdate(
    val stopSequence: Int?,
    val stopId: String?,
    val arrivalDelaySeconds: Int?,
    val departureDelaySeconds: Int?,
    val departureTimeEpochSeconds: Long?,
    val arrivalTimeEpochSeconds: Long? = null,
    val isSkipped: Boolean = false,
    val hasNoData: Boolean = false
)

data class LiveTripUpdate(
    val tripId: String,
    val stopTimeUpdates: List<LiveTripStopUpdate>,
    val tripDelaySeconds: Int?,
    val fetchedAt: LocalDateTime,
    val isCanceled: Boolean = false,
    val serviceDate: LocalDate? = null,
    val sourceTimestamp: Instant? = null
)

class ZtpTripUpdatesRepository @Inject constructor(
    private val httpClient: HttpClient
) {
    suspend fun getTripUpdate(
        mode: TransitVehicleMode,
        tripId: String,
        serviceDate: LocalDate? = null
    ): LiveTripUpdate? {
        val response = try {
            httpClient.get("${TRIP_UPDATES_URL}${mode.feedSuffix}.pb")
        } catch (exception: HttpRequestTimeoutException) {
            throw IOException("Przekroczono czas oczekiwania na aktualizacje kursów ZTP.", exception)
        } catch (exception: IOException) {
            throw IOException(
                "Nie udało się pobrać aktualizacji kursów ZTP: ${exception.message.orEmpty()}",
                exception
            )
        }
        if (!response.status.isSuccess()) {
            throw IOException(
                "ZTP zwrócił HTTP ${response.status.value} dla aktualizacji kursów."
            )
        }

        val feed = try {
            GtfsRealtime.FeedMessage.parseFrom(response.body<ByteArray>())
        } catch (exception: IOException) {
            throw IOException(
                "Nie udało się odczytać aktualizacji kursu z ZTP: ${exception.message.orEmpty()}",
                exception
            )
        }
        return mapTripUpdate(feed, tripId)?.takeIf {
            serviceDate == null || it.serviceDate == null || it.serviceDate == serviceDate
        }
    }

    companion object {
        const val TRIP_UPDATES_URL = "https://gtfs.ztp.krakow.pl/TripUpdates_"
    }
}

internal fun mapTripUpdate(
    feed: GtfsRealtime.FeedMessage,
    tripId: String
): LiveTripUpdate? {
    val tripUpdate = feed.entityList
        .asSequence()
        .filter { it.hasTripUpdate() && it.tripUpdate.hasTrip() }
        .map { it.tripUpdate }
        .firstOrNull { update ->
            update.trip.hasTripId() && update.trip.tripId == tripId
        } ?: return null

    return LiveTripUpdate(
        tripId = tripId,
        stopTimeUpdates = tripUpdate.stopTimeUpdateList.map { update ->
            LiveTripStopUpdate(
                stopSequence = update.stopSequence.takeIf { update.hasStopSequence() },
                stopId = update.stopId.takeIf { update.hasStopId() && it.isNotBlank() },
                arrivalDelaySeconds = update.arrival.delay.takeIf {
                    update.hasArrival() && update.arrival.hasDelay() &&
                        (!update.arrival.hasTime() || update.arrival.time > 0)
                },
                departureDelaySeconds = update.departure.delay.takeIf {
                    update.hasDeparture() && update.departure.hasDelay() &&
                        (!update.departure.hasTime() || update.departure.time > 0)
                },
                departureTimeEpochSeconds = update.departure.time.takeIf {
                    update.hasDeparture() && update.departure.hasTime() && it > 0
                },
                arrivalTimeEpochSeconds = update.arrival.time.takeIf {
                    update.hasArrival() && update.arrival.hasTime() && it > 0
                },
                isSkipped = update.scheduleRelationship ==
                    GtfsRealtime.TripUpdate.StopTimeUpdate.ScheduleRelationship.SKIPPED,
                hasNoData = update.scheduleRelationship ==
                    GtfsRealtime.TripUpdate.StopTimeUpdate.ScheduleRelationship.NO_DATA
            )
        },
        tripDelaySeconds = tripUpdate.delay.takeIf { tripUpdate.hasDelay() },
        fetchedAt = LocalDateTime.now(),
        isCanceled = tripUpdate.trip.scheduleRelationship ==
            GtfsRealtime.TripDescriptor.ScheduleRelationship.CANCELED,
        serviceDate = tripUpdate.trip.startDate.takeIf {
            tripUpdate.trip.hasStartDate() && it.isNotBlank()
        }?.let {
            try {
                LocalDate.parse(it, DateTimeFormatter.BASIC_ISO_DATE)
            } catch (exception: DateTimeParseException) {
                throw IOException("ZTP przekazał nieprawidłową datę prognozy kursu.", exception)
            }
        },
        sourceTimestamp = feed.header.timestamp.takeIf { feed.header.hasTimestamp() && it > 0 }
            ?.let(Instant::ofEpochSecond)
    )
}
