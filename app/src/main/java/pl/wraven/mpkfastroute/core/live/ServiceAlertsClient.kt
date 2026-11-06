package pl.wraven.mpkfastroute.core.live

import com.google.transit.realtime.GtfsRealtime
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.request.get
import io.ktor.http.isSuccess
import java.io.IOException
import javax.inject.Inject

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
    ): ServiceAlertsResult {
        val matchingAlerts = mutableListOf<RouteServiceAlert>()
        val unavailableFeeds = mutableListOf<String>()

        for ((feedName, url) in FEED_URLS) {
            try {
                val response = httpClient.get(url)
                if (!response.status.isSuccess()) {
                    unavailableFeeds += feedName
                    continue
                }
                val feed = GtfsRealtime.FeedMessage.parseFrom(response.body<ByteArray>())
                feed.entityList.forEach { entity ->
                    if (!entity.hasAlert()) return@forEach
                    val alert = entity.alert
                    if (!isActive(alert.activePeriodList, nowEpochSeconds)) return@forEach
                    val routeServiceAlert = alert.toRouteServiceAlert(
                        id = "$feedName:${entity.id}"
                    )
                    if (
                        routeServiceAlert.affectsRoute(
                            routeIds = routeIds,
                            routeShortNames = routeShortNames,
                            stopIds = stopIds
                        )
                    ) {
                        matchingAlerts += routeServiceAlert
                    }
                }
            } catch (_: IOException) {
                unavailableFeeds += feedName
            } catch (_: HttpRequestTimeoutException) {
                unavailableFeeds += feedName
            }
        }

        return ServiceAlertsResult(
            alerts = matchingAlerts.distinctBy(RouteServiceAlert::id),
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
        return RouteServiceAlert(
            id = id,
            title = headerText.bestTranslation().ifBlank { id },
            description = descriptionText.bestTranslation().takeIf(String::isNotBlank),
            routeIds = informedEntities.mapNotNullTo(mutableSetOf()) { selector ->
                selector.routeId.takeIf { selector.hasRouteId() }
            },
            stopIds = informedEntities.mapNotNullTo(mutableSetOf()) { selector ->
                selector.stopId.takeIf { selector.hasStopId() }
            }
        )
    }

    private fun GtfsRealtime.TranslatedString.bestTranslation(): String =
        translationList.firstOrNull {
            it.language.startsWith(POLISH_LANGUAGE_CODE, ignoreCase = true) &&
                it.text.isNotBlank()
        }?.text
            ?: translationList.firstOrNull { it.text.isNotBlank() }?.text
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
