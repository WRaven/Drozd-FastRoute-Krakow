package pl.wraven.mpkfastroute.feature.search

import java.text.Normalizer
import pl.wraven.mpkfastroute.core.database.StopEntity
import java.util.Locale

data class StopGroup(
    val name: String,
    val stops: List<StopEntity>
)

object StopNameGrouper {
    private val platformSuffix = Regex("""\s+(\d{1,3})$""")
    private val combiningMarks = Regex("""\p{M}+""")

    fun mainName(stopName: String): String =
        stopName.trim().replace(platformSuffix, "").trim()

    fun displayName(stop: StopEntity): String {
        val name = mainName(stop.stopName)
        val platform = platformSuffix.find(stop.stopName)?.groupValues?.get(1)
        return if (platform == null) name else "$name $platform"
    }

    fun matches(query: String, candidate: String): Boolean {
        val normalizedQuery = normalizeForSearch(query)
        return normalizedQuery.isNotEmpty() &&
            normalizeForSearch(candidate).contains(normalizedQuery)
    }

    fun sameNameForSearch(first: String, second: String): Boolean =
        normalizeForSearch(first) == normalizeForSearch(second)

    fun normalizeForSearch(value: String): String =
        Normalizer.normalize(
            value.replace('ł', 'l').replace('Ł', 'L'),
            Normalizer.Form.NFD
        ).replace(combiningMarks, "").lowercase(Locale.ROOT)

    fun platformLabel(stop: StopEntity): String? =
        platformSuffix.find(stop.stopName)?.groupValues?.get(1)
            ?: stop.stopCode?.takeIf(String::isNotBlank)

    fun group(stops: List<StopEntity>): List<StopGroup> =
        stops.groupBy { mainName(it.stopName) }
            .entries
            .sortedBy { it.key.lowercase(Locale.ROOT) }
            .map { (name, groupedStops) ->
                StopGroup(
                    name = name,
                    stops = groupedStops.sortedWith(
                        compareBy<StopEntity> { platformLabel(it).orEmpty() }
                            .thenBy(StopEntity::stopId)
                    )
                )
            }
}
