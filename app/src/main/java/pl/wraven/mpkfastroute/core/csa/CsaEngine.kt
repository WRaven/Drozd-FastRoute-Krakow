package pl.wraven.mpkfastroute.core.csa

import java.util.ArrayDeque
import javax.inject.Inject

data class CsaJourney(
    val departureTime: Int,
    val arrivalTime: Int,
    val transferPenaltySeconds: Int,
    val steps: List<JourneyStep>
) {
    val generalizedArrivalTime: Int
        get() = arrivalTime + transferPenaltySeconds
}

sealed interface JourneyStep {
    data class Transit(val connection: Connection) : JourneyStep

    data class Walk(val footpath: Footpath) : JourneyStep
}

class CsaEngine @Inject constructor() {
    fun findEarliestJourney(
        connections: List<Connection>,
        footpaths: List<Footpath>,
        originStopId: String,
        destinationStopId: String,
        startTime: Int
    ): CsaJourney? {
        require(startTime >= 0) { "Start time must not be negative." }
        require(connections.all { it.departureTime >= 0 && it.arrivalTime >= it.departureTime }) {
            "Connections must have non-negative, ordered times."
        }
        require(footpaths.all { it.durationSeconds >= 0 }) {
            "Footpath durations must not be negative."
        }

        if (originStopId == destinationStopId) {
            return CsaJourney(startTime, startTime, 0, emptyList())
        }

        val labelsByStop = mutableMapOf<String, MutableMap<String?, MutableList<JourneyState>>>()
        val footpathsByStop = footpaths.groupBy(Footpath::fromStopId)

        fun addLabel(candidate: JourneyState): Boolean {
            val labelsByTrip = labelsByStop.getOrPut(candidate.stopId) { mutableMapOf() }
            val labels = labelsByTrip.getOrPut(candidate.lastTripId) { mutableListOf() }
            if (labels.any { it.dominates(candidate) }) {
                return false
            }
            labels.removeAll { candidate.dominates(it) }
            labels += candidate
            return true
        }

        fun propagateFootpaths(initial: JourneyState) {
            val pending = ArrayDeque<JourneyState>()
            if (addLabel(initial)) {
                pending.addLast(initial)
            }

            while (pending.isNotEmpty()) {
                val current = pending.removeFirst()
                if (labelsByStop[current.stopId]?.get(current.lastTripId)?.contains(current) != true) {
                    continue
                }

                for (footpath in footpathsByStop[current.stopId].orEmpty()) {
                    val arrivalTime = Math.addExact(current.arrivalTime, footpath.durationSeconds)
                    val madeTransfer = current.lastTripId != null
                    val transferCount = current.transferCount + if (madeTransfer) 1 else 0
                    val readyTime = Math.addExact(
                        Math.addExact(current.readyTime, footpath.durationSeconds),
                        if (madeTransfer) TRANSFER_PENALTY_SECONDS else 0
                    )
                    val walked = JourneyState(
                        stopId = footpath.toStopId,
                        arrivalTime = arrivalTime,
                        readyTime = readyTime,
                        transferCount = transferCount,
                        lastTripId = null,
                        previous = current,
                        step = JourneyStep.Walk(footpath)
                    )
                    if (addLabel(walked)) {
                        pending.addLast(walked)
                    }
                }
            }
        }

        propagateFootpaths(
            JourneyState(
                stopId = originStopId,
                arrivalTime = startTime,
                readyTime = startTime,
                transferCount = 0,
                lastTripId = null,
                previous = null,
                step = null
            )
        )

        val sortedConnections = connections.sortedWith(
            compareBy<Connection> { it.departureTime }
                .thenBy { it.arrivalTime }
                .thenBy { it.tripId }
        )
        for (connection in sortedConnections) {
            val sourceLabels = labelsByStop[connection.departureStopId]
                ?.values
                ?.flatMap { it.toList() }
                .orEmpty()

            for (label in sourceLabels) {
                val changedTrip = label.lastTripId != null &&
                    label.lastTripId != connection.tripId
                val transferCount = label.transferCount + if (changedTrip) 1 else 0
                val readyTime = if (changedTrip) {
                    Math.addExact(label.arrivalTime, TRANSFER_PENALTY_SECONDS)
                } else {
                    label.readyTime
                }
                if (readyTime > connection.departureTime) {
                    continue
                }

                propagateFootpaths(
                    JourneyState(
                        stopId = connection.arrivalStopId,
                        arrivalTime = connection.arrivalTime,
                        readyTime = connection.arrivalTime,
                        transferCount = transferCount,
                        lastTripId = connection.tripId,
                        previous = label,
                        step = JourneyStep.Transit(connection)
                    )
                )
            }
        }

        val bestJourney = labelsByStop[destinationStopId]
            ?.values
            ?.flatten()
            ?.minWithOrNull(
                compareBy<JourneyState> { it.generalizedArrivalTime }
                    .thenBy { it.arrivalTime }
                    .thenBy { it.transferCount }
            ) ?: return null

        val steps = mutableListOf<JourneyStep>()
        var current: JourneyState? = bestJourney
        while (current != null) {
            current.step?.let(steps::add)
            current = current.previous
        }
        steps.reverse()

        return CsaJourney(
            departureTime = startTime,
            arrivalTime = bestJourney.arrivalTime,
            transferPenaltySeconds = bestJourney.transferCount * TRANSFER_PENALTY_SECONDS,
            steps = steps
        )
    }

    private data class JourneyState(
        val stopId: String,
        val arrivalTime: Int,
        val readyTime: Int,
        val transferCount: Int,
        val lastTripId: String?,
        val previous: JourneyState?,
        val step: JourneyStep?
    ) {
        val generalizedArrivalTime: Long
            get() = arrivalTime.toLong() + transferCount * TRANSFER_PENALTY_SECONDS

        fun dominates(other: JourneyState): Boolean =
            arrivalTime <= other.arrivalTime &&
                readyTime <= other.readyTime &&
                transferCount <= other.transferCount
    }

    private companion object {
        const val TRANSFER_PENALTY_SECONDS = 180
    }
}
