package pl.wraven.mpkfastroute.core.gtfs

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.Duration
import java.time.temporal.ChronoUnit

data class GtfsServiceTime(
    val serviceDate: LocalDate,
    val timeSeconds: Int
)

data class GtfsServiceTimeWindow(
    val serviceDate: LocalDate,
    val startTimeSeconds: Int,
    val durationSeconds: Int,
    val serviceDayOffsetSeconds: Int,
    val arrivalDeadlineSeconds: Int?
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

    fun window(
        startDateTime: LocalDateTime,
        durationSeconds: Int,
        arrivalDeadline: LocalDateTime? = null
    ): List<GtfsServiceTimeWindow> {
        require(durationSeconds > 0) { "Search window must be positive." }
        require(arrivalDeadline == null || !arrivalDeadline.isBefore(startDateTime)) {
            "Arrival deadline must not be before the search start."
        }

        val windowEnd = startDateTime.plusSeconds(durationSeconds.toLong())
        val baseServiceDate = from(startDateTime).serviceDate
        val targetServiceTime = arrivalDeadline?.let(::from)
        val windows = mutableListOf<GtfsServiceTimeWindow>()
        var segmentStart = startDateTime

        while (segmentStart.isBefore(windowEnd)) {
            val serviceTime = from(segmentStart)
            val nextServiceDay = if (segmentStart.toLocalTime() < SERVICE_DAY_CUTOFF) {
                segmentStart.toLocalDate()
            } else {
                segmentStart.toLocalDate().plusDays(1)
            }
            val serviceBoundary = nextServiceDay.atTime(SERVICE_DAY_CUTOFF)
            val segmentEnd = minOf(windowEnd, serviceBoundary)
            val segmentDuration = Duration.between(segmentStart, segmentEnd).seconds
            val segmentArrivalDeadline = targetServiceTime?.let { target ->
                val serviceDaysUntilDeadline =
                    ChronoUnit.DAYS.between(serviceTime.serviceDate, target.serviceDate)
                Math.toIntExact(
                    Math.addExact(
                        Math.multiplyExact(
                            serviceDaysUntilDeadline,
                            SECONDS_PER_DAY.toLong()
                        ),
                        target.timeSeconds.toLong()
                    )
                )
            }
            val serviceDayOffset = Math.toIntExact(
                Math.multiplyExact(
                    ChronoUnit.DAYS.between(baseServiceDate, serviceTime.serviceDate),
                    SECONDS_PER_DAY.toLong()
                )
            )
            windows += GtfsServiceTimeWindow(
                serviceDate = serviceTime.serviceDate,
                startTimeSeconds = serviceTime.timeSeconds,
                durationSeconds = Math.toIntExact(segmentDuration),
                serviceDayOffsetSeconds = serviceDayOffset,
                arrivalDeadlineSeconds = segmentArrivalDeadline
            )
            segmentStart = segmentEnd
        }

        return windows
    }

    private val SERVICE_DAY_CUTOFF: LocalTime = LocalTime.of(3, 0)
    private const val SECONDS_PER_DAY = 24 * 60 * 60
}
