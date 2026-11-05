package pl.wraven.mpkfastroute.core.live

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import pl.wraven.mpkfastroute.core.database.StopEntity

data class StaticDeparture(
    val routeShortName: String,
    val direction: String,
    val departureTime: Int
)

data class DepartureEstimate(
    val routeShortName: String,
    val direction: String,
    val scheduledDepartureTime: Int,
    val estimatedDepartureTime: Int,
    val delaySeconds: Int?,
    val isLive: Boolean
)

data class LiveDeparturesResult(
    val departures: List<DepartureEstimate>,
    val fallbackReason: String?
)

@Serializable
data class TtssStopPassagesResponse(
    @SerialName("stopName")
    val stopName: String? = null,
    @SerialName("actual")
    val actual: List<TtssDeparture> = emptyList()
)

@Serializable
data class TtssDeparture(
    @SerialName("patternText")
    val routeShortName: String? = null,
    @SerialName("direction")
    val direction: String? = null,
    @SerialName("status")
    val status: String? = null,
    @SerialName("actualRelativeTime")
    val actualRelativeTime: Int? = null,
    @SerialName("plannedRelativeTime")
    val plannedRelativeTime: Int? = null
)

class TtssLiveMapper {
    fun resolveStopId(
        stop: StopEntity,
        ttssStopIdsByName: Map<String, String> = emptyMap()
    ): String? {
        stop.stopCode?.trim()?.takeIf(String::isNotEmpty)?.let { return it }
        val normalizedName = stop.stopName.normalize()
        return ttssStopIdsByName.entries
            .firstOrNull { it.key.normalize() == normalizedName }
            ?.value
            ?.trim()
            ?.takeIf(String::isNotEmpty)
    }

    fun mapDepartures(
        staticDepartures: List<StaticDeparture>,
        passages: List<TtssDeparture>,
        nowTimeSeconds: Int
    ): LiveDeparturesResult {
        require(nowTimeSeconds >= 0) { "Current time must not be negative." }
        if (staticDepartures.isEmpty()) {
            return LiveDeparturesResult(emptyList(), null)
        }

        val availablePassages = passages.withIndex()
            .filter { (_, passage) ->
                passage.status.equals(PREDICTED_STATUS, ignoreCase = true) &&
                    passage.actualRelativeTime != null &&
                    passage.plannedRelativeTime != null &&
                    !passage.routeShortName.isNullOrBlank()
            }
            .toMutableList()
        val usedPassageIndexes = mutableSetOf<Int>()
        var matchedCount = 0

        val estimates = staticDepartures.map { departure ->
            val expectedRelativeTime = Math.floorMod(
                departure.departureTime - nowTimeSeconds,
                SECONDS_PER_DAY
            )
            val bestMatch = availablePassages.asSequence()
                .filter { it.index !in usedPassageIndexes }
                .filter { (_, passage) ->
                    passage.routeShortName.orEmpty().normalize() ==
                        departure.routeShortName.normalize() &&
                        directionsMatch(departure.direction, passage.direction)
                }
                .map { indexedPassage ->
                    val passage = indexedPassage.value
                    indexedPassage to kotlin.math.abs(
                        passage.plannedRelativeTime!! - expectedRelativeTime
                    )
                }
                .filter { (_, timeDifference) ->
                    timeDifference <= MAX_MATCH_DIFFERENCE_SECONDS
                }
                .minByOrNull { (_, timeDifference) -> timeDifference }
                ?.first

            if (bestMatch == null) {
                departure.toEstimate()
            } else {
                val passage = bestMatch.value
                val delaySeconds = passage.actualRelativeTime!! - passage.plannedRelativeTime!!
                usedPassageIndexes += bestMatch.index
                matchedCount += 1
                DepartureEstimate(
                    routeShortName = departure.routeShortName,
                    direction = departure.direction,
                    scheduledDepartureTime = departure.departureTime,
                    estimatedDepartureTime = Math.addExact(departure.departureTime, delaySeconds),
                    delaySeconds = delaySeconds,
                    isLive = true
                )
            }
        }

        val fallbackReason = when {
            matchedCount == 0 -> "TTSS returned no matching live departures."
            matchedCount < staticDepartures.size ->
                "Some departures have no matching live prediction."
            else -> null
        }
        return LiveDeparturesResult(estimates, fallbackReason)
    }

    fun staticFallback(
        staticDepartures: List<StaticDeparture>,
        reason: String
    ): LiveDeparturesResult = LiveDeparturesResult(
        departures = staticDepartures.map { it.toEstimate() },
        fallbackReason = reason
    )

    private fun directionsMatch(staticDirection: String, liveDirection: String?): Boolean {
        if (staticDirection.isBlank() || liveDirection.isNullOrBlank()) {
            return true
        }
        return staticDirection.normalize() == liveDirection.normalize()
    }

    private fun StaticDeparture.toEstimate() = DepartureEstimate(
        routeShortName = routeShortName,
        direction = direction,
        scheduledDepartureTime = departureTime,
        estimatedDepartureTime = departureTime,
        delaySeconds = null,
        isLive = false
    )

    private fun String.normalize(): String = lowercase().filter(Char::isLetterOrDigit)

    private companion object {
        const val PREDICTED_STATUS = "PREDICTED"
        const val SECONDS_PER_DAY = 24 * 60 * 60
        const val MAX_MATCH_DIFFERENCE_SECONDS = 10 * 60
    }
}
