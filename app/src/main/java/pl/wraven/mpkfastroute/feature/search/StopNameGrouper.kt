package pl.wraven.mpkfastroute.feature.search

import pl.wraven.mpkfastroute.core.database.StopEntity
import java.util.Locale

data class StopGroup(
    val name: String,
    val stops: List<StopEntity>
)

object StopNameGrouper {
    private val platformSuffix = Regex("""\s+(\d{1,3})$""")

    fun mainName(stopName: String): String =
        stopName.trim().replace(platformSuffix, "").trim()

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
