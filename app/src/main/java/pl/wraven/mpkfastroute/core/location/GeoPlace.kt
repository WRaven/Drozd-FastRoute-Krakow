package pl.wraven.mpkfastroute.core.location

data class GeoPlace(
    val label: String,
    val latitude: Double,
    val longitude: Double
) {
    init {
        require(label.isNotBlank()) { "A place label must not be blank." }
        require(latitude.isFinite() && latitude in -90.0..90.0) {
            "Latitude must be between -90 and 90 degrees."
        }
        require(longitude.isFinite() && longitude in -180.0..180.0) {
            "Longitude must be between -180 and 180 degrees."
        }
    }
}
