package pl.wraven.mpkfastroute.core.csa

import java.time.LocalDateTime

enum class JourneyTimeMode {
    DEPART_AFTER,
    ARRIVE_BY
}

data class JourneyTimePlan(
    val mode: JourneyTimeMode,
    val dateTime: LocalDateTime
)
