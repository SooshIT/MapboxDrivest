package com.drivest.navigation.camera

import kotlin.math.abs

object PuckStabilityDecider {

    private const val FORCE_APPLY_AFTER_MS = 1_500L
    private const val MIN_BEARING_DELTA_DEGREES = 6.0
    private const val MIN_SPEED_FOR_BEARING_UPDATES_MPS = 1.5

    data class Input(
        val elapsedMsSinceLastApply: Long,
        val locationUpdateIntervalMs: Long,
        val movementMeters: Double,
        val bearingDeltaDegrees: Double,
        val speedMetersPerSecond: Double,
        val gpsAccuracyMeters: Float
    )

    data class Decision(
        val shouldApply: Boolean,
        val transitionDurationMs: Long
    )

    fun decide(input: Input, minTransitionMs: Long, maxTransitionMs: Long): Decision {
        val speedMetersPerSecond = input.speedMetersPerSecond.coerceAtLeast(0.0)
        val jitterThresholdMeters = jitterDistanceThresholdMeters(
            speedMetersPerSecond = speedMetersPerSecond,
            gpsAccuracyMeters = input.gpsAccuracyMeters
        )
        val movedEnough = input.movementMeters >= jitterThresholdMeters
        val rotatedEnough =
            speedMetersPerSecond >= MIN_SPEED_FOR_BEARING_UPDATES_MPS &&
                input.bearingDeltaDegrees >= MIN_BEARING_DELTA_DEGREES
        val forceApply = input.elapsedMsSinceLastApply >= FORCE_APPLY_AFTER_MS
        val shouldApply = forceApply || movedEnough || rotatedEnough

        val normalizedIntervalMs = input.locationUpdateIntervalMs.coerceIn(250L, 2_200L)
        val speedFactor = when {
            speedMetersPerSecond < 1.0 -> 1.10
            speedMetersPerSecond < 5.0 -> 0.98
            else -> 0.92
        }
        val movementFactor = when {
            input.movementMeters < jitterThresholdMeters -> 1.08
            input.movementMeters < jitterThresholdMeters * 2.0 -> 0.95
            else -> 0.90
        }
        val transitionDurationMs = (normalizedIntervalMs * speedFactor * movementFactor)
            .toLong()
            .coerceIn(minTransitionMs, maxTransitionMs)

        return Decision(
            shouldApply = shouldApply,
            transitionDurationMs = transitionDurationMs
        )
    }

    fun bearingDeltaDegrees(previousBearing: Double, currentBearing: Double): Double {
        val normalizedPrevious = normalizeBearing(previousBearing)
        val normalizedCurrent = normalizeBearing(currentBearing)
        return abs((normalizedCurrent - normalizedPrevious + 540.0) % 360.0 - 180.0)
    }

    private fun jitterDistanceThresholdMeters(speedMetersPerSecond: Double, gpsAccuracyMeters: Float): Double {
        val speedThresholdMeters = when {
            speedMetersPerSecond < 0.8 -> 2.0
            speedMetersPerSecond < 3.0 -> 1.4
            else -> 0.8
        }
        val gpsNoiseAllowance = if (gpsAccuracyMeters > 18f) 0.7 else 0.0
        return speedThresholdMeters + gpsNoiseAllowance
    }

    private fun normalizeBearing(rawBearing: Double): Double {
        val normalized = rawBearing % 360.0
        return if (normalized < 0.0) normalized + 360.0 else normalized
    }
}
