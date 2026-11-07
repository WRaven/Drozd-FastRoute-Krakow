package pl.wraven.mpkfastroute.feature.search

import org.junit.Assert.assertEquals
import org.junit.Test

class WeatherForecastSheetTest {
    @Test
    fun showsOnlySoilTemperatureAndWindInTheCompactDetailsLine() {
        assertEquals("Grunt 8,2°C · wiatr 8 km/h", formatHourlyWeatherDetails(8.2, 8.4))
    }

    @Test
    fun preservesNegativeSoilTemperaturesAndStrongWind() {
        assertEquals("Grunt -12,3°C · wiatr 120 km/h", formatHourlyWeatherDetails(-12.3, 120.9))
    }

    @Test
    fun keepsTheMissingSoilTemperatureIndicator() {
        assertEquals("Grunt —°C · wiatr 0 km/h", formatHourlyWeatherDetails(null, 0.0))
    }
}
