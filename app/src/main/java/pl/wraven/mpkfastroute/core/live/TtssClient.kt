package pl.wraven.mpkfastroute.core.live

import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.url
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import java.io.IOException
import java.time.LocalTime
import javax.inject.Inject
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import pl.wraven.mpkfastroute.core.database.StopEntity

class TtssClient @Inject constructor(
    private val httpClient: HttpClient,
    private val json: Json,
    private val mapper: TtssLiveMapper
) {
    suspend fun getDepartures(
        stop: StopEntity,
        staticDepartures: List<StaticDeparture>,
        ttssStopIdsByName: Map<String, String> = emptyMap(),
        nowTimeSeconds: Int = LocalTime.now().toSecondOfDay()
    ): LiveDeparturesResult {
        if (staticDepartures.isEmpty()) {
            return LiveDeparturesResult(emptyList(), null)
        }
        val ttssStopId = mapper.resolveStopId(stop, ttssStopIdsByName)
            ?: return mapper.staticFallback(
                staticDepartures,
                "No TTSS stop mapping is available for this stop."
            )

        val response = try {
            httpClient.get {
                url(TTSS_STOP_PASSAGES_URL)
                parameter("stop", ttssStopId)
                parameter("mode", "departure")
                parameter("language", "pl")
            }
        } catch (exception: HttpRequestTimeoutException) {
            return mapper.staticFallback(
                staticDepartures,
                "TTSS request timed out: ${exception.message.orEmpty()}"
            )
        } catch (exception: IOException) {
            return mapper.staticFallback(
                staticDepartures,
                "TTSS request failed: ${exception.message.orEmpty()}"
            )
        }

        if (!response.status.isSuccess()) {
            return mapper.staticFallback(
                staticDepartures,
                "TTSS returned HTTP ${response.status.value}."
            )
        }

        val responseBody = try {
            response.bodyAsText()
        } catch (exception: IOException) {
            return mapper.staticFallback(
                staticDepartures,
                "TTSS response could not be read: ${exception.message.orEmpty()}"
            )
        }
        val passageResponse = try {
            json.decodeFromString<TtssStopPassagesResponse>(responseBody)
        } catch (exception: SerializationException) {
            return mapper.staticFallback(
                staticDepartures,
                "TTSS returned an invalid response: ${exception.message.orEmpty()}"
            )
        }

        return mapper.mapDepartures(
            staticDepartures = staticDepartures,
            passages = passageResponse.actual,
            nowTimeSeconds = nowTimeSeconds
        )
    }

    private companion object {
        const val TTSS_STOP_PASSAGES_URL =
            "http://www.ttss.krakow.pl/internetservice/services/passageInfo/stopPassages/stop"
    }
}
