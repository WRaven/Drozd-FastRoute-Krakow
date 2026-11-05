package pl.wraven.mpkfastroute.core.csa

data class Footpath(
    val fromStopId: String,
    val toStopId: String,
    val durationSeconds: Int
)
