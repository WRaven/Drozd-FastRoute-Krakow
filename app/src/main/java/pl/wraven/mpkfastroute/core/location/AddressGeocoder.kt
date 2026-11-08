package pl.wraven.mpkfastroute.core.location

import android.content.Context
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
                ?.getAddressLine(0)
                ?.trim()
                ?.takeIf(String::isNotBlank)
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

            Geocoder(context, Locale.forLanguageTag("pl-PL"))
                .getFromLocationName(normalizedQuery, MAX_RESULTS)
                .orEmpty()
                .mapNotNull { address ->
                    if (!address.hasLatitude() || !address.hasLongitude()) {
                        null
                    } else {
                        GeoPlace(
                            label = address.getAddressLine(0)?.takeIf(String::isNotBlank)
                                ?: normalizedQuery,
                            latitude = address.latitude,
                            longitude = address.longitude
                        )
                    }
                }
                .distinctBy { Triple(it.label, it.latitude, it.longitude) }
        }
    }

    private companion object {
        const val MAX_RESULTS = 5
    }
}
