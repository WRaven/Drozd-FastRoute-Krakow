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

private const val GTFS_TRAM_ROUTE_TYPE = 0
private const val GTFS_BUS_ROUTE_TYPE = 3

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
        val feed = fetchFeed(mode)
        return mapTripUpdate(feed, tripId)?.takeIf {
            serviceDate == null || it.serviceDate == null || it.serviceDate == serviceDate
        }
    }

    suspend fun getTripUpdates(
        mode: TransitVehicleMode,
        tripIds: Set<String>
    ): Map<String, LiveTripUpdate> = if (tripIds.isEmpty()) {
        emptyMap()
    } else {
        mapTripUpdates(fetchFeed(mode), tripIds)
    }

    private suspend fun fetchFeed(mode: TransitVehicleMode): GtfsRealtime.FeedMessage {
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
        return try {
            GtfsRealtime.FeedMessage.parseFrom(response.body<ByteArray>())
        } catch (exception: IOException) {
            throw IOException(
                "Nie udało się odczytać aktualizacji kursu z ZTP: ${exception.message.orEmpty()}",
                exception
            )
        }
    }

    companion object {
        const val TRIP_UPDATES_URL = "https://gtfs.ztp.krakow.pl/TripUpdates_"
    }
}

internal fun transitVehicleModeForGtfsRouteType(routeType: Int?): TransitVehicleMode? =
    when (routeType) {
        GTFS_TRAM_ROUTE_TYPE -> TransitVehicleMode.TRAM
        GTFS_BUS_ROUTE_TYPE -> TransitVehicleMode.BUS
        else -> null
    }

internal fun mapTripUpdate(
    feed: GtfsRealtime.FeedMessage,
    tripId: String
): LiveTripUpdate? = mapTripUpdates(feed, setOf(tripId))[tripId]

internal fun mapTripUpdates(
    feed: GtfsRealtime.FeedMessage,
    tripIds: Set<String>
): Map<String, LiveTripUpdate> {
    val sourceTimestamp = feed.header.timestamp.takeIf {
        feed.header.hasTimestamp() && it > 0
    }?.let(Instant::ofEpochSecond)
    return feed.entityList
        .asSequence()
        .filter { it.hasTripUpdate() && it.tripUpdate.hasTrip() }
        .map { it.tripUpdate }
        .filter { update -> update.trip.hasTripId() && update.trip.tripId in tripIds }
        .distinctBy { it.trip.tripId }
        .associate { update ->
            val tripId = update.trip.tripId
            tripId to mapTripUpdate(update, tripId, sourceTimestamp)
        }
}

private fun mapTripUpdate(
    tripUpdate: GtfsRealtime.TripUpdate,
    tripId: String,
    sourceTimestamp: Instant?
): LiveTripUpdate = LiveTripUpdate(
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
        sourceTimestamp = sourceTimestamp
    )
