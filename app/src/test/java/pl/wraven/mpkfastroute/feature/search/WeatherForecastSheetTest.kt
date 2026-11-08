package pl.wraven.mpkfastroute.feature.search

import org.junit.Assert.assertEquals
import org.junit.Test

class WeatherForecastSheetTest {
    @Test
    fun combinesPrecipitationSoilTemperatureAndWindInTheHourlyDetailsLine() {
        assertEquals(
            "Opady 1,2 mm · szansa 35% · grunt 8,2°C · wiatr 8 km/h",
            formatHourlyWeatherDetails(1.2, 35, 8.2, 8.4)
        )
    }

    @Test
    fun preservesNegativeSoilTemperaturesAndStrongWind() {
        assertEquals(
            "Opady 0,0 mm · szansa 0% · grunt -12,3°C · wiatr 120 km/h",
            formatHourlyWeatherDetails(0.0, 0, -12.3, 120.9)
        )
    }

    @Test
    fun keepsTheMissingSoilTemperatureIndicator() {
        assertEquals(
            "Opady 0,0 mm · szansa 0% · grunt —°C · wiatr 0 km/h",
            formatHourlyWeatherDetails(0.0, 0, null, 0.0)
        )
    }

    @Test
    fun combinesCurrentPrecipitationSoilTemperatureAndWindOnOneLine() {
        assertEquals(
            "Opady 0,0 mm · grunt 8,2°C · wiatr 1 km/h",
            formatCurrentWeatherDetails(0.0, 8.2, 1.9)
        )
    }
}
