package pl.wraven.mpkfastroute.core.audio

import org.junit.Assert.assertEquals
import org.junit.Test

class RadioStationsTest {
    @Test
    fun exposesConfiguredStationsInStableOrder() {
        assertEquals(
            listOf(
                "https://waw.ic.smcdn.pl/6070-1.mp3",
                "https://ant.cdn.eurozet.pl/ant-waw.mp3",
                "https://radiostream.pl/tuba140-1.aac"
            ),
            RadioStations.all.map(RadioStation::streamUrl)
        )
    }

    @Test
    fun removesRadioKrakowFromPreviouslySavedStationLists() {
        val configuredStations = RadioStations.all + RadioStation(
            id = "radio-krakow",
            name = "Radio Kraków",
            streamUrl = "http://stream3.nadaje.com:9118/radiokrakow"
        )

        assertEquals(
            RadioStations.all,
            filterUnavailableRadioStations(configuredStations)
        )
    }
}
