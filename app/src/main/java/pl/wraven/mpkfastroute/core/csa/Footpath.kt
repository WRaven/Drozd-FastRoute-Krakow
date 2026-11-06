package pl.wraven.mpkfastroute.core.csa

data class Footpath(
    val fromStopId: String,
    val toStopId: String,
    val durationSeconds: Int,
    val distanceMeters: Double = durationSeconds * FootpathGenerator.WALK_SPEED_METERS_PER_SECOND
)
