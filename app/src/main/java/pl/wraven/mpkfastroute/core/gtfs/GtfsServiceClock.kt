package pl.wraven.mpkfastroute.core.gtfs

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

data class GtfsServiceTime(
    val serviceDate: LocalDate,
    val timeSeconds: Int
)

object GtfsServiceClock {
    fun from(localDateTime: LocalDateTime): GtfsServiceTime {
        val localTime = localDateTime.toLocalTime()
        val afterMidnightServiceDay = localTime < SERVICE_DAY_CUTOFF
        return GtfsServiceTime(
            serviceDate = if (afterMidnightServiceDay) {
                localDateTime.toLocalDate().minusDays(1)
            } else {
                localDateTime.toLocalDate()
            },
            timeSeconds = localTime.toSecondOfDay() +
                if (afterMidnightServiceDay) SECONDS_PER_DAY else 0
        )
    }

    private val SERVICE_DAY_CUTOFF: LocalTime = LocalTime.of(3, 0)
    private const val SECONDS_PER_DAY = 24 * 60 * 60
}
