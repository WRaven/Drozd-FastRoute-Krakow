package pl.wraven.mpkfastroute.core.audio

import kotlinx.serialization.Serializable

@Serializable
data class RadioStation(
    val id: String,
    val name: String,
    val streamUrl: String
)

object RadioStations {
    val all = listOf(
        RadioStation(
            id = "eska-rock",
            name = "Eska Rock",
            streamUrl = "https://waw.ic.smcdn.pl/6070-1.mp3"
        ),
        RadioStation(
            id = "antyradio",
            name = "Antyradio",
            streamUrl = "https://ant.cdn.eurozet.pl/ant-waw.mp3"
        ),
        RadioStation(
            id = "radio-pogoda",
            name = "Radio Pogoda",
            streamUrl = "https://radiostream.pl/tuba140-1.aac"
        ),
        RadioStation(
            id = "radio-krakow",
            name = "Radio Kraków",
            streamUrl = "http://stream3.nadaje.com:9118/radiokrakow"
        )
    )
}
