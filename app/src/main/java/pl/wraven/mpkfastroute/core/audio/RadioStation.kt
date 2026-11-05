package pl.wraven.mpkfastroute.core.audio

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
        )
    )
}
