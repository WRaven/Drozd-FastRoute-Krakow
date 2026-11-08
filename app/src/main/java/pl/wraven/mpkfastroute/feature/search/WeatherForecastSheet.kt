package pl.wraven.mpkfastroute.feature.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.time.format.DateTimeFormatter
import java.util.Locale
import pl.wraven.mpkfastroute.core.weather.WeatherForecast
import pl.wraven.mpkfastroute.core.weather.WeatherCondition
import pl.wraven.mpkfastroute.core.weather.weatherConditionForCode
import pl.wraven.mpkfastroute.core.weather.weatherConditionForTime

@Composable
internal fun WeatherForecastSheet(
    forecast: WeatherForecast?,
    isLoading: Boolean,
    error: String?,
    onRetry: () -> Unit,
    onOpenAttribution: () -> Unit,
    onOpenLicense: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 680.dp)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Pogoda w Twojej okolicy", style = MaterialTheme.typography.titleLarge)
        when {
            isLoading -> {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
                Text(
                    "Pobieranie lokalizacji i prognozy…",
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
            }
            error != null -> {
                Text(error, color = MaterialTheme.colorScheme.error)
                Button(onClick = onRetry, modifier = Modifier.fillMaxWidth()) {
                    Text("Spróbuj ponownie")
                }
            }
            forecast == null -> {
                Text("Naciśnij „Pogoda”, aby pobrać prognozę dla bieżącej lokalizacji.")
            }
            else -> {
                CurrentWeatherCard(forecast)
                Text("Najbliższe 24 godziny", style = MaterialTheme.typography.titleMedium)
                forecast.hourly.forEach { hour ->
                    HourlyWeatherRow(
                        time = hour.time.format(HOURLY_TIME_FORMATTER),
                        condition = weatherConditionForTime(
                            code = hour.weatherCode,
                            isDay = hour.isDay,
                            time = hour.time,
                            timezone = forecast.timezone
                        ),
                        temperature = hour.temperatureCelsius,
                        precipitationProbability = hour.precipitationProbabilityPercent,
                        precipitationMillimeters = hour.precipitationMillimeters,
                        windSpeed = hour.windSpeedKilometersPerHour,
                        soilTemperatureCelsius = hour.soilTemperatureCelsius
                    )
                    HorizontalDivider()
                }
                Text(
                    "Legenda: mm — suma opadu w godzinie · % — szansa opadu · " +
                        "grunt 0 cm — temperatura gruntu. " +
                        "⛈️ burza · 🌫️ mgła.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            "Dane pogodowe: Open-Meteo.com. Udostępniane na licencji CC BY 4.0.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row {
            TextButton(onClick = onOpenAttribution) {
                Text("Źródło: Open-Meteo")
            }
            TextButton(onClick = onOpenLicense) {
                Text("Licencja CC BY 4.0")
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun CurrentWeatherCard(forecast: WeatherForecast) {
    val current = forecast.current
    val condition = weatherConditionForTime(
        code = current.weatherCode,
        isDay = current.isDay,
        time = current.time,
        timezone = forecast.timezone
    )
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                current.time.format(CURRENT_TIME_FORMATTER),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            BoxWithConstraints {
                if (maxWidth >= 320.dp) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        CurrentWeatherConditions(
                            condition = condition,
                            temperatureCelsius = current.temperatureCelsius,
                            modifier = Modifier.weight(1f)
                        )
                        CurrentWeatherMetrics(forecast, Modifier.weight(1f))
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        CurrentWeatherConditions(
                            condition = condition,
                            temperatureCelsius = current.temperatureCelsius
                        )
                        CurrentWeatherMetrics(forecast)
                    }
                }
            }
            Text(
                formatCurrentWeatherDetails(
                    precipitationMillimeters = current.precipitationMillimeters,
                    soilTemperatureCelsius =
                        forecast.hourly.firstOrNull()?.soilTemperatureCelsius,
                    windSpeedKilometersPerHour = current.windSpeedKilometersPerHour
                ),
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun CurrentWeatherConditions(
    condition: WeatherCondition,
    temperatureCelsius: Double,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(condition.symbol, style = MaterialTheme.typography.headlineLarge)
        Column {
            Text(
                "${temperatureCelsius.toInt()}°C",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            Text(condition.description)
        }
    }
}

@Composable
private fun CurrentWeatherMetrics(
    forecast: WeatherForecast,
    modifier: Modifier = Modifier
) {
    val current = forecast.current
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text("Odczuwalna ${current.apparentTemperatureCelsius.toInt()}°C")
        Text("Wilgotność ${current.relativeHumidityPercent}%")
    }
}

@Composable
private fun HourlyWeatherRow(
    time: String,
    condition: WeatherCondition,
    temperature: Double,
    precipitationProbability: Int,
    precipitationMillimeters: Double,
    windSpeed: Double,
    soilTemperatureCelsius: Double?
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(time, modifier = Modifier.width(88.dp), style = MaterialTheme.typography.bodyMedium)
            Text(condition.symbol, style = MaterialTheme.typography.titleMedium)
            Text(
                condition.description,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall
            )
            Text("${temperature.toInt()}°")
        }
        Text(
            formatHourlyWeatherDetails(
                precipitationMillimeters = precipitationMillimeters,
                precipitationProbability = precipitationProbability,
                soilTemperatureCelsius = soilTemperatureCelsius,
                windSpeed = windSpeed
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

internal fun formatHourlyWeatherDetails(
    precipitationMillimeters: Double,
    precipitationProbability: Int,
    soilTemperatureCelsius: Double?,
    windSpeed: Double
): String =
    "Opady ${formatWeatherValue(precipitationMillimeters)} mm · " +
        "szansa $precipitationProbability% · " +
        "grunt ${soilTemperatureCelsius?.let(::formatWeatherValue) ?: "—"}°C · " +
        "wiatr ${windSpeed.toInt()} km/h"

internal fun formatCurrentWeatherDetails(
    precipitationMillimeters: Double,
    soilTemperatureCelsius: Double?,
    windSpeedKilometersPerHour: Double
): String =
    "Opady ${formatWeatherValue(precipitationMillimeters)} mm · " +
        "grunt ${soilTemperatureCelsius?.let(::formatWeatherValue) ?: "—"}°C · " +
        "wiatr ${windSpeedKilometersPerHour.toInt()} km/h"

private fun formatWeatherValue(value: Double): String =
    String.format(Locale.forLanguageTag("pl-PL"), "%.1f", value)

private val CURRENT_TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm")
private val HOURLY_TIME_FORMATTER =
    DateTimeFormatter.ofPattern("EEE HH:mm", Locale.forLanguageTag("pl"))
