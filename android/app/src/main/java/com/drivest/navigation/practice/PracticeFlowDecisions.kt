package com.drivest.navigation.practice

data class PracticeArrivalEvaluation(
    val arrived: Boolean,
    val withinRadiusSinceMs: Long?
)

object PracticeFlowDecisions {

    fun evaluateArrival(
        nowMs: Long,
        distanceToStartMeters: Double,
        speedMps: Double,
        withinRadiusSinceMs: Long?,
        immediateRadiusMeters: Double,
        dwellRadiusMeters: Double,
        dwellWindowMs: Long,
        maxArrivalSpeedMps: Double
    ): PracticeArrivalEvaluation {
        val safeDistance = distanceToStartMeters.coerceAtLeast(0.0)
        val safeSpeed = speedMps.coerceAtLeast(0.0)
        val updatedWithinRadiusSinceMs = when {
            safeDistance > dwellRadiusMeters -> null
            withinRadiusSinceMs == null -> nowMs
            else -> withinRadiusSinceMs
        }

        val immediateArrival = safeDistance <= immediateRadiusMeters && safeSpeed <= maxArrivalSpeedMps
        val dwellArrival = updatedWithinRadiusSinceMs?.let { enteredAtMs ->
            (nowMs - enteredAtMs).coerceAtLeast(0L) >= dwellWindowMs
        } ?: false

        return PracticeArrivalEvaluation(
            arrived = immediateArrival || dwellArrival,
            withinRadiusSinceMs = updatedWithinRadiusSinceMs
        )
    }

    fun shouldTransitionToPracticeRoute(
        arrived: Boolean,
        transitionInProgress: Boolean,
        routeStartInProgress: Boolean
    ): Boolean {
        return arrived && !transitionInProgress && !routeStartInProgress
    }

    fun shouldRerouteToStart(
        nowMs: Long,
        lastRerouteAtMs: Long,
        rerouteCooldownMs: Long,
        distanceToStartMeters: Double,
        closestDistanceSeenMeters: Double,
        missedDeltaMeters: Double,
        routeDistanceRemainingMeters: Double,
        completionPercent: Int,
        routeArrivalDistanceMeters: Double
    ): Boolean {
        if (nowMs - lastRerouteAtMs < rerouteCooldownMs) {
            return false
        }

        if (
            !distanceToStartMeters.isFinite() ||
            !closestDistanceSeenMeters.isFinite() ||
            !routeDistanceRemainingMeters.isFinite()
        ) {
            return false
        }

        val passedStartThenMovedAway = closestDistanceSeenMeters <= routeArrivalDistanceMeters &&
            (distanceToStartMeters - closestDistanceSeenMeters) >= missedDeltaMeters

        val routeThinksArrivedButStillFar =
            routeDistanceRemainingMeters <= routeArrivalDistanceMeters &&
                distanceToStartMeters > routeArrivalDistanceMeters + missedDeltaMeters

        val highCompletionButStillFar =
            completionPercent >= 98 &&
                distanceToStartMeters > routeArrivalDistanceMeters + missedDeltaMeters

        return passedStartThenMovedAway || routeThinksArrivedButStillFar || highCompletionButStillFar
    }
}
