package pl.wraven.mpkfastroute.core.csa

data class Connection(
    val departureStopId: String,
    val arrivalStopId: String,
    val departureTime: Int,
    val arrivalTime: Int,
    val tripId: String,
    val routeShortName: String
)
