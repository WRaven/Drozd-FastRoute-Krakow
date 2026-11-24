package pl.wraven.mpkfastroute.core.live

import com.google.transit.realtime.GtfsRealtime
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.http.isSuccess
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

data class RouteServiceAlert(
    val id: String,
    val title: String,
    val description: String?,
    val routeIds: Set<String>,
    val stopIds: Set<String>
) {
    fun affectsRoute(
        routeIds: Set<String>,
        routeShortNames: Set<String>,
        stopIds: Set<String>
    ): Boolean {
        if (this.routeIds.isEmpty() && this.stopIds.isEmpty()) return true
        return this.routeIds.any { it in routeIds || it in routeShortNames } ||
            this.stopIds.any { it in stopIds }
    }
}

data class ServiceAlertsResult(
    val alerts: List<RouteServiceAlert>,
    val unavailableFeeds: List<String>
) {
    val isComplete: Boolean
        get() = unavailableFeeds.isEmpty()
}

class ServiceAlertsClient @Inject constructor(
    private val httpClient: HttpClient
) {
    suspend fun getAlerts(
        routeIds: Set<String>,
        routeShortNames: Set<String>,
        stopIds: Set<String>,
        nowEpochSeconds: Long = System.currentTimeMillis() / MILLIS_PER_SECOND
    ): ServiceAlertsResult = fetchAlerts(nowEpochSeconds) { alert ->
        alert.affectsRoute(
            routeIds = routeIds,
            routeShortNames = routeShortNames,
            stopIds = stopIds
        )
    }

    suspend fun getAllAlerts(
        nowEpochSeconds: Long = System.currentTimeMillis() / MILLIS_PER_SECOND
    ): ServiceAlertsResult = fetchAlerts(nowEpochSeconds) { true }

    private suspend fun fetchAlerts(
        nowEpochSeconds: Long,
        shouldInclude: (RouteServiceAlert) -> Boolean
    ): ServiceAlertsResult {
        val feedResults = coroutineScope {
            FEED_URLS.map { (feedName, url) ->
                async {
                    try {
                        val response = httpClient.get(url)
                        if (!response.status.isSuccess()) {
                            return@async feedName to null
                        }
                        val feed = GtfsRealtime.FeedMessage.parseFrom(response.body<ByteArray>())
                        val alerts = buildList {
                            feed.entityList.forEach { entity ->
                                if (!entity.hasAlert()) return@forEach
                                val alert = entity.alert
                                if (!isActive(alert.activePeriodList, nowEpochSeconds)) return@forEach
                                val routeServiceAlert = alert.toRouteServiceAlert(
                                    id = "$feedName:${entity.id}"
                                )
                                if (shouldInclude(routeServiceAlert)) {
                                    add(routeServiceAlert)
                                }
                            }
                        }
                        feedName to alerts
                    } catch (_: IOException) {
                        feedName to null
                    }
                }
            }.awaitAll()
        }

        val matchingAlerts = mutableListOf<RouteServiceAlert>()
        val unavailableFeeds = mutableListOf<String>()
        feedResults.forEach { (feedName, alerts) ->
            if (alerts == null) {
                unavailableFeeds += feedName
            } else {
                matchingAlerts += alerts
            }
        }

        return ServiceAlertsResult(
            alerts = matchingAlerts.distinctBy { alert ->
                Triple(alert.title, alert.description, alert.routeIds to alert.stopIds)
            },
            unavailableFeeds = unavailableFeeds
        )
    }

    private fun isActive(
        activePeriods: List<GtfsRealtime.TimeRange>,
        nowEpochSeconds: Long
    ): Boolean = activePeriods.isEmpty() || activePeriods.any { period ->
        (!period.hasStart() || period.start <= nowEpochSeconds) &&
            (!period.hasEnd() || period.end >= nowEpochSeconds)
    }

    private fun GtfsRealtime.Alert.toRouteServiceAlert(id: String): RouteServiceAlert {
        val informedEntities = informedEntityList
        val routeIds = mutableSetOf<String>()
        val stopIds = mutableSetOf<String>()
        informedEntities.forEach { selector ->
            if (selector.hasRouteId() && selector.routeId.isNotBlank()) {
                routeIds += selector.routeId.trim()
            }
            if (selector.hasTrip() && selector.trip.hasRouteId() && selector.trip.routeId.isNotBlank()) {
                routeIds += selector.trip.routeId.trim()
            }
            if (selector.hasStopId() && selector.stopId.isNotBlank()) {
                stopIds += selector.stopId.trim()
            }
        }
        return RouteServiceAlert(
            id = id,
            title = headerText.bestTranslation().ifBlank { id },
            description = descriptionText.bestTranslation().takeIf(String::isNotBlank),
            routeIds = routeIds,
            stopIds = stopIds
        )
    }

    private fun GtfsRealtime.TranslatedString.bestTranslation(): String =
        (translationList.firstOrNull {
            it.language.startsWith(POLISH_LANGUAGE_CODE, ignoreCase = true) &&
                it.text.isNotBlank()
        }?.text ?: translationList.firstOrNull { it.text.isNotBlank() }?.text)
            ?.trim()
            .orEmpty()

    private companion object {
        const val BASE_URL = "https://gtfs.ztp.krakow.pl"
        const val MILLIS_PER_SECOND = 1_000L
        const val POLISH_LANGUAGE_CODE = "pl"
        val FEED_URLS = listOf(
            "A" to "$BASE_URL/ServiceAlerts_A.pb",
            "M" to "$BASE_URL/ServiceAlerts_M.pb",
            "T" to "$BASE_URL/ServiceAlerts_T.pb"
        )
    }
}
