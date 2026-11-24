package pl.wraven.mpkfastroute.core.location

import android.content.Context
import android.location.Address
import android.location.Geocoder
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AddressGeocoder @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    @Suppress("DEPRECATION")
    suspend fun reverseGeocode(place: GeoPlace): String? {
        return withContext(Dispatchers.IO) {
            if (!Geocoder.isPresent()) {
                throw IOException("Usługa wyszukiwania adresów jest niedostępna na tym urządzeniu.")
            }

            Geocoder(context, Locale.forLanguageTag("pl-PL"))
                .getFromLocation(place.latitude, place.longitude, 1)
                .orEmpty()
                .firstOrNull()
                ?.formatDisplayLabel()
        }
    }

    @Suppress("DEPRECATION")
    suspend fun search(query: String): List<GeoPlace> {
        val normalizedQuery = query.trim()
        require(normalizedQuery.isNotEmpty()) { "Address must not be blank." }

        return withContext(Dispatchers.IO) {
            if (!Geocoder.isPresent()) {
                throw IOException("Usługa wyszukiwania adresów jest niedostępna na tym urządzeniu.")
            }

            val geocoder = Geocoder(context, Locale.forLanguageTag("pl-PL"))
            val boundedResults = geocoder.getFromLocationName(
                normalizedQuery,
                MAX_RESULTS,
                KRAKOW_MIN_LATITUDE,
                KRAKOW_MIN_LONGITUDE,
                KRAKOW_MAX_LATITUDE,
                KRAKOW_MAX_LONGITUDE
            ).orEmpty()
            val results = boundedResults.ifEmpty {
                geocoder.getFromLocationName(normalizedQuery, MAX_RESULTS).orEmpty()
            }
            results.mapNotNull { address ->
                if (!address.hasLatitude() || !address.hasLongitude()) {
                    null
                } else {
                    GeoPlace(
                        label = address.formatDisplayLabel() ?: normalizedQuery,
                        latitude = address.latitude,
                        longitude = address.longitude
                    )
                }
            }.distinctBy { Triple(it.label, it.latitude, it.longitude) }
        }
    }

    private fun Address.formatDisplayLabel(): String? {
        getAddressLine(0)?.trim()?.takeIf(String::isNotBlank)?.let { return it }
        val street = listOfNotNull(
            thoroughfare?.trim()?.takeIf(String::isNotBlank),
            subThoroughfare?.trim()?.takeIf(String::isNotBlank)
        ).joinToString(" ").takeIf(String::isNotBlank)
        return listOfNotNull(
            street,
            locality?.trim()?.takeIf(String::isNotBlank)
        ).joinToString(", ").takeIf(String::isNotBlank)
    }

    private companion object {
        const val MAX_RESULTS = 5
        const val KRAKOW_MIN_LATITUDE = 49.80
        const val KRAKOW_MIN_LONGITUDE = 19.55
        const val KRAKOW_MAX_LATITUDE = 50.28
        const val KRAKOW_MAX_LONGITUDE = 20.35
    }
}
