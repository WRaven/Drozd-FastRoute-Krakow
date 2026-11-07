package pl.wraven.mpkfastroute.core.live

import com.google.transit.realtime.GtfsRealtime
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.request.get
import io.ktor.http.isSuccess
import java.io.IOException
import java.time.LocalDateTime
import javax.inject.Inject

data class LiveTripStopUpdate(
    val stopSequence: Int?,
    val stopId: String?,
    val arrivalDelaySeconds: Int?,
    val departureDelaySeconds: Int?,
    val departureTimeEpochSeconds: Long?
)

data class LiveTripUpdate(
    val tripId: String,
    val stopTimeUpdates: List<LiveTripStopUpdate>,
    val tripDelaySeconds: Int?,
    val fetchedAt: LocalDateTime
)

class ZtpTripUpdatesRepository @Inject constructor(
    private val httpClient: HttpClient
) {
    suspend fun getTripUpdate(
        mode: TransitVehicleMode,
        tripId: String
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
        return mapTripUpdate(feed, tripId)
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
                    update.hasArrival() && update.arrival.hasDelay()
                },
                departureDelaySeconds = update.departure.delay.takeIf {
                    update.hasDeparture() && update.departure.hasDelay()
                },
                departureTimeEpochSeconds = update.departure.time.takeIf {
                    update.hasDeparture() && update.departure.hasTime()
                }
            )
        },
        tripDelaySeconds = tripUpdate.delay.takeIf { tripUpdate.hasDelay() },
        fetchedAt = LocalDateTime.now()
    )
}
