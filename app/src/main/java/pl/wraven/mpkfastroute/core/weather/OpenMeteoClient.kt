package pl.wraven.mpkfastroute.core.weather

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import java.io.IOException
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import pl.wraven.mpkfastroute.core.weather.OpenMeteoClient.Companion.CACHE_DURATION_MILLIS
import pl.wraven.mpkfastroute.core.weather.OpenMeteoClient.Companion.ENDPOINT
import kotlin.math.roundToInt

data class WeatherForecast(
    val current: CurrentWeather,
    val hourly: List<HourlyWeather>,
    val timezone: String
)

data class CurrentWeather(
    val time: LocalDateTime,
    val temperatureCelsius: Double,
    val apparentTemperatureCelsius: Double,
    val relativeHumidityPercent: Int,
    val precipitationMillimeters: Double,
    val weatherCode: Int,
    val windSpeedKilometersPerHour: Double
)

data class HourlyWeather(
    val time: LocalDateTime,
    val temperatureCelsius: Double,
    val precipitationProbabilityPercent: Int,
    val weatherCode: Int,
    val windSpeedKilometersPerHour: Double
)

class OpenMeteoRequestException(message: String) : IOException(message)

@Singleton
class OpenMeteoClient @Inject constructor(
    private val httpClient: HttpClient,
    private val json: Json
) {
    @Volatile
    private var cachedForecast: CachedForecast? = null

    suspend fun getForecast(
        latitude: Double,
        longitude: Double,
        nowMillis: Long = System.currentTimeMillis()
    ): WeatherForecast {
        require(latitude.isFinite() && latitude in -90.0..90.0) {
            "Latitude must be between -90 and 90 degrees."
        }
        require(longitude.isFinite() && longitude in -180.0..180.0) {
            "Longitude must be between -180 and 180 degrees."
        }

        val cacheKey = CacheKey(
            latitudeCell = (latitude * CACHE_COORDINATE_PRECISION).roundToInt(),
            longitudeCell = (longitude * CACHE_COORDINATE_PRECISION).roundToInt()
        )
        cachedForecast?.takeIf {
            it.key == cacheKey && nowMillis - it.fetchedAtMillis < CACHE_DURATION_MILLIS
        }?.let { return it.forecast }

        val response = httpClient.get(ENDPOINT) {
            parameter("latitude", latitude)
            parameter("longitude", longitude)
            parameter(
                "current",
                "temperature_2m,relative_humidity_2m,apparent_temperature,precipitation,weather_code,wind_speed_10m"
            )
            parameter(
                "hourly",
                "temperature_2m,precipitation_probability,weather_code,wind_speed_10m"
            )
            parameter("forecast_hours", FORECAST_HOURS)
            parameter("temperature_unit", "celsius")
            parameter("wind_speed_unit", "kmh")
            parameter("precipitation_unit", "mm")
            parameter("timezone", "auto")
        }
        if (!response.status.isSuccess()) {
            throw OpenMeteoRequestException(
                "Open-Meteo returned HTTP ${response.status.value}."
            )
        }

        val responseBody = response.bodyAsText()
        val forecast = try {
            json.decodeFromString<OpenMeteoResponse>(responseBody).toWeatherForecast()
        } catch (exception: SerializationException) {
            throw OpenMeteoRequestException(
                "Open-Meteo returned an invalid response: ${exception.message.orEmpty()}"
            )
        } catch (exception: java.time.DateTimeException) {
            throw OpenMeteoRequestException(
                "Open-Meteo returned invalid forecast times: ${exception.message.orEmpty()}"
            )
        } catch (exception: IllegalArgumentException) {
            throw OpenMeteoRequestException(
                "Open-Meteo returned invalid forecast values: ${exception.message.orEmpty()}"
            )
        }
        cachedForecast = CachedForecast(cacheKey, nowMillis, forecast)
        return forecast
    }

    private data class CacheKey(
        val latitudeCell: Int,
        val longitudeCell: Int
    )

    private data class CachedForecast(
        val key: CacheKey,
        val fetchedAtMillis: Long,
        val forecast: WeatherForecast
    )

    private fun OpenMeteoResponse.toWeatherForecast(): WeatherForecast {
        val hourlyCount = minOf(
            hourly.time.size,
            hourly.temperatureCelsius.size,
            hourly.precipitationProbabilityPercent.size,
            hourly.weatherCode.size,
            hourly.windSpeedKilometersPerHour.size
        )
        require(hourlyCount > 0) { "Hourly forecast is empty." }

        return WeatherForecast(
            current = CurrentWeather(
                time = LocalDateTime.parse(current.time),
                temperatureCelsius = current.temperatureCelsius,
                apparentTemperatureCelsius = current.apparentTemperatureCelsius,
                relativeHumidityPercent = current.relativeHumidityPercent,
                precipitationMillimeters = current.precipitationMillimeters,
                weatherCode = current.weatherCode,
                windSpeedKilometersPerHour = current.windSpeedKilometersPerHour
            ),
            hourly = (0 until hourlyCount).map { index ->
                HourlyWeather(
                    time = LocalDateTime.parse(hourly.time[index]),
                    temperatureCelsius = hourly.temperatureCelsius[index],
                    precipitationProbabilityPercent =
                        hourly.precipitationProbabilityPercent[index],
                    weatherCode = hourly.weatherCode[index],
                    windSpeedKilometersPerHour = hourly.windSpeedKilometersPerHour[index]
                )
            },
            timezone = timezone
        )
    }

    @Serializable
    private data class OpenMeteoResponse(
        val current: OpenMeteoCurrent,
        val hourly: OpenMeteoHourly,
        val timezone: String
    )

    @Serializable
    private data class OpenMeteoCurrent(
        val time: String,
        @SerialName("temperature_2m")
        val temperatureCelsius: Double,
        @SerialName("apparent_temperature")
        val apparentTemperatureCelsius: Double,
        @SerialName("relative_humidity_2m")
        val relativeHumidityPercent: Int,
        val precipitation: Double,
        @SerialName("weather_code")
        val weatherCode: Int,
        @SerialName("wind_speed_10m")
        val windSpeedKilometersPerHour: Double
    ) {
        val precipitationMillimeters: Double
            get() = precipitation
    }

    @Serializable
    private data class OpenMeteoHourly(
        val time: List<String>,
        @SerialName("temperature_2m")
        val temperatureCelsius: List<Double>,
        @SerialName("precipitation_probability")
        val precipitationProbabilityPercent: List<Int>,
        @SerialName("weather_code")
        val weatherCode: List<Int>,
        @SerialName("wind_speed_10m")
        val windSpeedKilometersPerHour: List<Double>
    )

    companion object {
        const val ATTRIBUTION_URL = "https://open-meteo.com/"
        const val LICENSE_URL = "https://creativecommons.org/licenses/by/4.0/"
        const val CACHE_DURATION_MILLIS = 15 * 60 * 1_000L
        const val FORECAST_HOURS = 24
        const val ENDPOINT = "https://api.open-meteo.com/v1/forecast"
        private const val CACHE_COORDINATE_PRECISION = 100.0
    }
}

data class WeatherCondition(
    val symbol: String,
    val description: String
)

fun weatherConditionForCode(code: Int): WeatherCondition = when (code) {
    0 -> WeatherCondition("☀️", "Bezchmurnie")
    1 -> WeatherCondition("🌤️", "Przeważnie pogodnie")
    2 -> WeatherCondition("⛅", "Częściowe zachmurzenie")
    3 -> WeatherCondition("☁️", "Pochmurno")
    45, 48 -> WeatherCondition("🌫️", "Mgła")
    in 51..57 -> WeatherCondition("🌦️", "Mżawka")
    in 61..67 -> WeatherCondition("🌧️", "Deszcz")
    in 71..77 -> WeatherCondition("❄️", "Śnieg")
    in 80..82 -> WeatherCondition("🌧️", "Przelotny deszcz")
    85, 86 -> WeatherCondition("🌨️", "Przelotny śnieg")
    in 95..99 -> WeatherCondition("⛈️", "Burza")
    else -> WeatherCondition("🌡️", "Warunki zmienne")
}
