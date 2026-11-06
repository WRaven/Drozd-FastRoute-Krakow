package pl.wraven.mpkfastroute.core.settings

import kotlinx.serialization.Serializable
import pl.wraven.mpkfastroute.core.location.GeoPlace

@Serializable
data class SavedRoute(
    val origin: GeoPlace,
    val destination: GeoPlace
)
