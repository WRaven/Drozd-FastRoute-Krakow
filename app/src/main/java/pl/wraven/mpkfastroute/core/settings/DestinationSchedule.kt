package pl.wraven.mpkfastroute.core.settings

import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.LocalTime

data class DestinationSchedule(
    val arrivalTime: LocalTime,
    val weekdays: Set<DayOfWeek>
) {
    init {
        require(weekdays.isNotEmpty()) { "At least one weekday must be selected." }
    }

    fun nextOccurrence(after: LocalDateTime): LocalDateTime =
        (0L..7L).asSequence()
            .map { offset -> LocalDateTime.of(after.toLocalDate().plusDays(offset), arrivalTime) }
            .firstOrNull { occurrence ->
                occurrence.dayOfWeek in weekdays && occurrence.isAfter(after)
            }
            ?: throw IllegalStateException("No upcoming occurrence found for the schedule.")

    fun encode(): String =
        "$arrivalTime|${weekdays.sortedBy(DayOfWeek::getValue).joinToString(",") { it.value.toString() }}"

    companion object {
        val DEFAULT = DestinationSchedule(
            arrivalTime = LocalTime.of(8, 0),
            weekdays = setOf(
                DayOfWeek.MONDAY,
                DayOfWeek.TUESDAY,
                DayOfWeek.WEDNESDAY,
                DayOfWeek.THURSDAY,
                DayOfWeek.FRIDAY
            )
        )

        fun decode(encoded: String?): DestinationSchedule? {
            if (encoded == null) {
                return null
            }
            val parts = encoded.split('|')
            require(parts.size == 2) { "Invalid destination schedule." }
            val time = LocalTime.parse(parts[0])
            val weekdays = parts[1].split(',')
                .map { part -> DayOfWeek.of(part.toInt()) }
                .toSet()
            return DestinationSchedule(time, weekdays)
        }
    }
}
