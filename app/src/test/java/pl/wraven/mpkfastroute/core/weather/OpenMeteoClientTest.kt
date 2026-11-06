package pl.wraven.mpkfastroute.core.weather

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import java.io.IOException
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenMeteoClientTest {
    @Test
    fun requestsCurrentAndHourlyWeatherAndCachesForTheSameApproximateLocation() = runBlocking {
        var requestCount = 0
        val client = HttpClient(
            MockEngine { request ->
                requestCount += 1
                assertEquals("50.0614", request.url.parameters["latitude"])
                assertEquals("19.9366", request.url.parameters["longitude"])
                assertEquals("24", request.url.parameters["forecast_hours"])
                assertEquals("auto", request.url.parameters["timezone"])
                assertTrue(request.url.parameters["current"].orEmpty().contains("weather_code"))
                assertTrue(request.url.parameters["hourly"].orEmpty().contains("precipitation_probability"))
                respond(
                    content = FORECAST_RESPONSE,
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
            }
        )
        try {
            val weatherClient = OpenMeteoClient(client, Json { ignoreUnknownKeys = true })
            val first = weatherClient.getForecast(50.0614, 19.9366, nowMillis = 1_000L)
            val cached = weatherClient.getForecast(50.06141, 19.93661, nowMillis = 2_000L)

            assertEquals(1, requestCount)
            assertEquals(12.5, first.current.temperatureCelsius, 0.0)
            assertEquals(2, first.hourly.size)
            assertEquals("Europe/Warsaw", first.timezone)
            assertEquals(first, cached)
        } finally {
            client.close()
        }
    }

    @Test
    fun reportsUnsuccessfulResponsesAsAnExplicitError() = runBlocking {
        val client = HttpClient(
            MockEngine {
                respond(
                    content = "unavailable",
                    status = HttpStatusCode.ServiceUnavailable
                )
            }
        )
        try {
            val weatherClient = OpenMeteoClient(client, Json { ignoreUnknownKeys = true })
            val error = try {
                weatherClient.getForecast(50.0, 19.0)
                null
            } catch (exception: OpenMeteoRequestException) {
                exception
            }
            assertTrue(error is IOException)
            assertTrue(error?.message.orEmpty().contains("503"))
        } finally {
            client.close()
        }
    }

    @Test
    fun mapsWeatherCodesToPolishDescriptions() {
        assertEquals("Bezchmurnie", weatherConditionForCode(0).description)
        assertEquals("Deszcz", weatherConditionForCode(63).description)
        assertEquals("Burza", weatherConditionForCode(95).description)
    }

    private companion object {
        const val FORECAST_RESPONSE = """
            {
              "timezone": "Europe/Warsaw",
              "current": {
                "time": "2026-11-07T12:00",
                "temperature_2m": 12.5,
                "relative_humidity_2m": 73,
                "apparent_temperature": 11.2,
                "precipitation": 0.1,
                "weather_code": 2,
                "wind_speed_10m": 8.4
              },
              "hourly": {
                "time": ["2026-11-07T12:00", "2026-11-07T13:00"],
                "temperature_2m": [12.5, 13.0],
                "precipitation_probability": [20, 35],
                "weather_code": [2, 61],
                "wind_speed_10m": [8.4, 9.0]
              }
            }
        """
    }
}
