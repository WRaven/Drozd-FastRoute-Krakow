package pl.wraven.mpkfastroute.core.csa

import javax.inject.Inject

data class CsaJourney(
    val departureTime: Int,
    val arrivalTime: Int,
    val transferPenaltySeconds: Int,
    val steps: List<JourneyStep>,
    val originAccess: StopAccess,
    val destinationAccess: StopAccess,
    val transferWalkingDistanceMeters: Double
) {
    val generalizedArrivalTime: Int
        get() = arrivalTime + transferPenaltySeconds

    val estimatedArrivalTime: Int
        get() = Math.addExact(generalizedArrivalTime, destinationAccess.walkingDurationSeconds)

    val totalDurationSeconds: Int
        get() = estimatedArrivalTime - departureTime

    val totalWalkingDistanceMeters: Double
        get() = originAccess.walkingDistanceMeters +
            transferWalkingDistanceMeters +
            destinationAccess.walkingDistanceMeters
}

data class StopAccess(
    val stopId: String,
    val walkingDistanceMeters: Double,
    val walkingDurationSeconds: Int
) {
    init {
        require(stopId.isNotBlank()) { "Stop ID must not be blank." }
        require(walkingDistanceMeters.isFinite() && walkingDistanceMeters >= 0.0) {
            "Walking distance must be finite and non-negative."
        }
        require(walkingDurationSeconds >= 0) { "Walking duration must not be negative." }
    }
}

data class CsaProgress(
    val scannedConnections: Int,
    val totalConnections: Int,
    val reachedStops: Int,
    val propagatedStates: Int
)

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
    ): CsaJourney? = findEarliestJourney(
        connections = connections,
        footpaths = footpaths,
        originStopIds = setOf(originStopId),
        destinationStopIds = setOf(destinationStopId),
        startTime = startTime
    )

    fun findEarliestJourney(
        connections: List<Connection>,
        footpaths: List<Footpath>,
        originStopIds: Set<String>,
        destinationStopIds: Set<String>,
        startTime: Int
    ): CsaJourney? = findJourneys(
        connections = connections,
        footpaths = footpaths,
        originAccesses = originStopIds.map { StopAccess(it, 0.0, 0) },
        destinationAccesses = destinationStopIds.map { StopAccess(it, 0.0, 0) },
        startTime = startTime,
        maxResults = 1
    ).firstOrNull()

    fun findJourneys(
        connections: List<Connection>,
        footpaths: List<Footpath>,
        originAccesses: List<StopAccess>,
        destinationAccesses: List<StopAccess>,
        startTime: Int,
        maxResults: Int = DEFAULT_MAX_RESULTS,
        arrivalDeadline: Int? = null,
        onProgress: (CsaProgress) -> Unit = {}
    ): List<CsaJourney> {
        require(startTime >= 0) { "Start time must not be negative." }
        require(originAccesses.isNotEmpty()) { "At least one origin stop is required." }
        require(destinationAccesses.isNotEmpty()) { "At least one destination stop is required." }
        require(maxResults > 0) { "At least one journey result is required." }
        require(arrivalDeadline == null || arrivalDeadline >= startTime) {
            "Arrival deadline must not be before the search start."
        }
        require(connections.all { it.departureTime >= 0 && it.arrivalTime >= it.departureTime }) {
            "Connections must have non-negative, ordered times."
        }
        require(footpaths.all {
            it.durationSeconds >= 0 && it.distanceMeters.isFinite() && it.distanceMeters >= 0.0
        }) {
            "Footpath durations and distances must be finite and non-negative."
        }

        val uniqueOrigins = bestAccessByStop(originAccesses)
        val uniqueDestinations = bestAccessByStop(destinationAccesses)
        val minOriginReadyTime = uniqueOrigins.minOf {
            Math.addExact(startTime, it.walkingDurationSeconds)
        }

        val labelsByStop = mutableMapOf<String, MutableMap<String?, MutableList<JourneyState>>>()
        val footpathsByStop = footpaths.groupBy(Footpath::fromStopId)
        val preferLatestDeparture = arrivalDeadline != null
        var scannedConnections = 0
        var propagatedStates = 0

        fun reportProgress() {
            onProgress(
                CsaProgress(
                    scannedConnections = scannedConnections,
                    totalConnections = connections.size,
                    reachedStops = labelsByStop.size,
                    propagatedStates = propagatedStates
                )
            )
        }

        fun addLabel(candidate: JourneyState): Boolean {
            val labelsByTrip = labelsByStop.getOrPut(candidate.stopId) { mutableMapOf() }
            val labels = labelsByTrip.getOrPut(candidate.lastTripId) { mutableListOf() }
            for (existing in labels) {
                if (existing.dominates(candidate, preferLatestDeparture)) {
                    return false
                }
            }
            var writeIndex = 0
            for (index in labels.indices) {
                val existing = labels[index]
                if (!candidate.dominates(existing, preferLatestDeparture)) {
                    labels[writeIndex++] = existing
                }
            }
            while (labels.size > writeIndex) {
                labels.removeAt(labels.lastIndex)
            }
            labels += candidate
            return true
        }

        fun propagateFootpaths(initial: JourneyState) {
            if (!addLabel(initial)) {
                return
            }
            propagatedStates += 1
            if (propagatedStates % FOOTPATH_PROGRESS_INTERVAL == 0) {
                reportProgress()
            }
            // Direct links already cover transfers up to 500 m; chaining them is unbounded.
            if (initial.hasWalkedSinceTransit) {
                return
            }

            val outgoingFootpaths = footpathsByStop[initial.stopId] ?: return
            val startingTransferWalk = initial.lastTripId != null
            for (footpath in outgoingFootpaths) {
                val arrivalTime = Math.addExact(initial.arrivalTime, footpath.durationSeconds)
                val readyTime = Math.addExact(
                    Math.addExact(initial.readyTime, footpath.durationSeconds),
                    if (startingTransferWalk) TRANSFER_PENALTY_SECONDS else 0
                )
                val walked = JourneyState(
                    stopId = footpath.toStopId,
                    arrivalTime = arrivalTime,
                    readyTime = readyTime,
                    transferCount = initial.transferCount,
                    lastTripId = initial.lastTripId,
                    hasWalkedSinceTransit = true,
                    originAccess = initial.originAccess,
                    walkedDistanceMeters = initial.walkedDistanceMeters +
                        footpath.distanceMeters,
                    transferWalkingDistanceMeters = initial.transferWalkingDistanceMeters +
                        footpath.distanceMeters,
                    originDepartureTime = initial.originDepartureTime,
                    initialFootpathDurationSeconds = if (initial.originDepartureTime == null) {
                        Math.addExact(
                            initial.initialFootpathDurationSeconds,
                            footpath.durationSeconds
                        )
                    } else {
                        initial.initialFootpathDurationSeconds
                    },
                    previous = initial,
                    step = JourneyStep.Walk(footpath)
                )
                if (addLabel(walked)) {
                    propagatedStates += 1
                    if (propagatedStates % FOOTPATH_PROGRESS_INTERVAL == 0) {
                        reportProgress()
                    }
                }
            }
        }

        uniqueOrigins.forEach { originAccess ->
            val stopArrivalTime = Math.addExact(startTime, originAccess.walkingDurationSeconds)
            propagateFootpaths(
                JourneyState(
                    stopId = originAccess.stopId,
                    arrivalTime = stopArrivalTime,
                    readyTime = stopArrivalTime,
                    transferCount = 0,
                    lastTripId = null,
                    hasWalkedSinceTransit = false,
                    originAccess = originAccess,
                    walkedDistanceMeters = originAccess.walkingDistanceMeters,
                    transferWalkingDistanceMeters = 0.0,
                    originDepartureTime = null,
                    initialFootpathDurationSeconds = 0,
                    previous = null,
                    step = null
                )
            )
        }

        val sortedConnections = if (isSortedByDepartureTime(connections)) {
            connections
        } else {
            connections.sortedWith(
                compareBy<Connection> { it.departureTime }
                    .thenBy { it.arrivalTime }
                    .thenBy { it.tripId }
            )
        }
        val sourceBuffer = ArrayList<JourneyState>()
        for (connection in sortedConnections) {
            if (arrivalDeadline != null && connection.departureTime > arrivalDeadline) {
                break
            }
            scannedConnections += 1
            if (scannedConnections % CONNECTION_PROGRESS_INTERVAL == 0) {
                reportProgress()
            }
            if (connection.departureTime < minOriginReadyTime) {
                continue
            }
            val labelsByTrip = labelsByStop[connection.departureStopId] ?: continue
            sourceBuffer.clear()
            for (tripLabels in labelsByTrip.values) {
                sourceBuffer.addAll(tripLabels)
            }

            for (index in sourceBuffer.indices) {
                val label = sourceBuffer[index]
                val changedTrip = label.lastTripId != null &&
                    label.lastTripId != connection.tripId
                val transferCount = label.transferCount + if (changedTrip) 1 else 0
                val readyTime = if (changedTrip) {
                    if (label.hasWalkedSinceTransit) {
                        label.readyTime
                    } else {
                        Math.addExact(label.arrivalTime, TRANSFER_PENALTY_SECONDS)
                    }
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
                        hasWalkedSinceTransit = false,
                        originAccess = label.originAccess,
                        walkedDistanceMeters = label.walkedDistanceMeters,
                        transferWalkingDistanceMeters = label.transferWalkingDistanceMeters,
                        originDepartureTime = label.originDepartureTime ?: Math.subtractExact(
                            Math.subtractExact(
                                connection.departureTime,
                                label.originAccess.walkingDurationSeconds
                            ),
                            label.initialFootpathDurationSeconds
                        ),
                        initialFootpathDurationSeconds = label.initialFootpathDurationSeconds,
                        previous = label,
                        step = JourneyStep.Transit(connection)
                    )
                )
            }
        }
        reportProgress()

        val candidates = uniqueDestinations.flatMap { destinationAccess ->
            labelsByStop[destinationAccess.stopId]
                ?.values
                ?.flatMap { it }
                .orEmpty()
                .map { state ->
                    JourneyCandidate(
                        state = state,
                        destinationAccess = destinationAccess,
                        actualArrivalTime = state.arrivalTime.toLong() +
                            destinationAccess.walkingDurationSeconds,
                        estimatedArrivalTime = state.generalizedArrivalTime +
                            destinationAccess.walkingDurationSeconds,
                        departureFromOriginTime = state.originDepartureTime ?: startTime,
                        totalWalkingDistanceMeters = state.walkedDistanceMeters +
                            destinationAccess.walkingDistanceMeters
                    )
                }
        }
        val eligibleCandidates = candidates.filter { candidate ->
            arrivalDeadline == null ||
                candidate.actualArrivalTime <= arrivalDeadline.toLong()
        }
        val candidateComparator: Comparator<JourneyCandidate> =
            if (arrivalDeadline == null) {
                compareBy<JourneyCandidate> { it.estimatedArrivalTime }
                    .thenBy { it.totalWalkingDistanceMeters }
                    .thenBy { it.state.transferCount }
            } else {
                compareByDescending<JourneyCandidate> { it.departureFromOriginTime }
                    .thenBy {
                        it.estimatedArrivalTime - it.departureFromOriginTime
                    }
                    .thenByDescending { it.estimatedArrivalTime }
                    .thenBy { it.totalWalkingDistanceMeters }
                    .thenBy { it.state.transferCount }
            }
        val orderedCandidates = eligibleCandidates.sortedWith(candidateComparator)
        if (orderedCandidates.isEmpty()) {
            return emptyList()
        }

        val selectedCandidates = orderedCandidates.take(maxResults).toMutableList()
        val leastWalkingComparator = if (arrivalDeadline == null) {
            compareBy<JourneyCandidate> { it.totalWalkingDistanceMeters }
                .thenBy { it.estimatedArrivalTime }
                .thenBy { it.state.transferCount }
        } else {
            compareBy<JourneyCandidate> { it.totalWalkingDistanceMeters }
                .thenByDescending { it.departureFromOriginTime }
                .thenBy { it.estimatedArrivalTime - it.departureFromOriginTime }
                .thenBy { it.state.transferCount }
        }
        val leastWalkingCandidate = eligibleCandidates.minWithOrNull(leastWalkingComparator)
        if (
            maxResults > 1 &&
            leastWalkingCandidate != null &&
            leastWalkingCandidate !in selectedCandidates
        ) {
            selectedCandidates[selectedCandidates.lastIndex] = leastWalkingCandidate
        }

        return selectedCandidates
            .distinct()
            .sortedWith(candidateComparator)
            .map { candidate ->
                val steps = mutableListOf<JourneyStep>()
                var current: JourneyState? = candidate.state
                while (current != null) {
                    current.step?.let(steps::add)
                    current = current.previous
                }
                steps.reverse()

                CsaJourney(
                    departureTime = if (arrivalDeadline == null) {
                        startTime
                    } else {
                        candidate.departureFromOriginTime
                    },
                    arrivalTime = candidate.state.arrivalTime,
                    transferPenaltySeconds =
                        candidate.state.transferCount * TRANSFER_PENALTY_SECONDS,
                    steps = steps,
                    originAccess = candidate.state.originAccess,
                    destinationAccess = candidate.destinationAccess,
                    transferWalkingDistanceMeters =
                        candidate.state.transferWalkingDistanceMeters
                )
            }
    }

    private fun bestAccessByStop(accesses: List<StopAccess>): List<StopAccess> =
        accesses.groupBy(StopAccess::stopId).values.map { options ->
            options.minWithOrNull(
                compareBy<StopAccess> { it.walkingDurationSeconds }
                    .thenBy { it.walkingDistanceMeters }
            ) ?: throw IllegalStateException("A stop access group cannot be empty.")
        }

    private fun isSortedByDepartureTime(connections: List<Connection>): Boolean {
        for (index in 1 until connections.size) {
            if (connections[index - 1].departureTime > connections[index].departureTime) {
                return false
            }
        }
        return true
    }

    private data class JourneyCandidate(
        val state: JourneyState,
        val destinationAccess: StopAccess,
        val actualArrivalTime: Long,
        val estimatedArrivalTime: Long,
        val departureFromOriginTime: Int,
        val totalWalkingDistanceMeters: Double
    )

    private data class JourneyState(
        val stopId: String,
        val arrivalTime: Int,
        val readyTime: Int,
        val transferCount: Int,
        val lastTripId: String?,
        val hasWalkedSinceTransit: Boolean,
        val originAccess: StopAccess,
        val walkedDistanceMeters: Double,
        val transferWalkingDistanceMeters: Double,
        val originDepartureTime: Int?,
        val initialFootpathDurationSeconds: Int,
        val previous: JourneyState?,
        val step: JourneyStep?
    ) {
        val generalizedArrivalTime: Long
            get() = arrivalTime.toLong() + transferCount * TRANSFER_PENALTY_SECONDS

        fun dominates(other: JourneyState, preferLatestDeparture: Boolean): Boolean =
            arrivalTime <= other.arrivalTime &&
                readyTime <= other.readyTime &&
                transferCount <= other.transferCount &&
                walkedDistanceMeters <= other.walkedDistanceMeters &&
                (!hasWalkedSinceTransit || other.hasWalkedSinceTransit) &&
                (!preferLatestDeparture ||
                    (originDepartureTime ?: Int.MIN_VALUE) >=
                    (other.originDepartureTime ?: Int.MIN_VALUE))
    }

    private companion object {
        const val TRANSFER_PENALTY_SECONDS = 180
        const val CONNECTION_PROGRESS_INTERVAL = 5_000
        const val FOOTPATH_PROGRESS_INTERVAL = 100_000
        const val DEFAULT_MAX_RESULTS = 20
    }
}
