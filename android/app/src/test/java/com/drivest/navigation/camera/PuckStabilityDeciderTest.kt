package com.drivest.navigation.camera

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PuckStabilityDeciderTest {

    @Test
    fun tinyStationaryJitterIsIgnoredBeforeTimeout() {
        val decision = PuckStabilityDecider.decide(
            input = PuckStabilityDecider.Input(
                elapsedMsSinceLastApply = 900L,
                locationUpdateIntervalMs = 500L,
                movementMeters = 0.5,
                bearingDeltaDegrees = 2.0,
                speedMetersPerSecond = 0.3,
                gpsAccuracyMeters = 6f
            ),
            minTransitionMs = 180L,
            maxTransitionMs = 900L
        )

        assertFalse(decision.shouldApply)
    }

    @Test
    fun timeoutForcesApplyEvenWhenMovementIsTiny() {
        val decision = PuckStabilityDecider.decide(
            input = PuckStabilityDecider.Input(
                elapsedMsSinceLastApply = 1_600L,
                locationUpdateIntervalMs = 500L,
                movementMeters = 0.4,
                bearingDeltaDegrees = 1.0,
                speedMetersPerSecond = 0.2,
                gpsAccuracyMeters = 5f
            ),
            minTransitionMs = 180L,
            maxTransitionMs = 900L
        )

        assertTrue(decision.shouldApply)
    }

    @Test
    fun drivingBearingChangeAppliesWhenSpeedIsHighEnough() {
        val decision = PuckStabilityDecider.decide(
            input = PuckStabilityDecider.Input(
                elapsedMsSinceLastApply = 400L,
                locationUpdateIntervalMs = 500L,
                movementMeters = 0.3,
                bearingDeltaDegrees = 9.0,
                speedMetersPerSecond = 6.0,
                gpsAccuracyMeters = 6f
            ),
            minTransitionMs = 180L,
            maxTransitionMs = 900L
        )

        assertTrue(decision.shouldApply)
    }

    @Test
    fun transitionDurationIsBounded() {
        val minTransitionMs = 180L
        val maxTransitionMs = 900L
        val low = PuckStabilityDecider.decide(
            input = PuckStabilityDecider.Input(
                elapsedMsSinceLastApply = 100L,
                locationUpdateIntervalMs = 100L,
                movementMeters = 0.2,
                bearingDeltaDegrees = 0.0,
                speedMetersPerSecond = 20.0,
                gpsAccuracyMeters = 3f
            ),
            minTransitionMs = minTransitionMs,
            maxTransitionMs = maxTransitionMs
        )
        val high = PuckStabilityDecider.decide(
            input = PuckStabilityDecider.Input(
                elapsedMsSinceLastApply = 100L,
                locationUpdateIntervalMs = 4_000L,
                movementMeters = 100.0,
                bearingDeltaDegrees = 0.0,
                speedMetersPerSecond = 0.0,
                gpsAccuracyMeters = 3f
            ),
            minTransitionMs = minTransitionMs,
            maxTransitionMs = maxTransitionMs
        )

        assertTrue(low.transitionDurationMs in minTransitionMs..maxTransitionMs)
        assertEquals(maxTransitionMs, high.transitionDurationMs)
    }

    @Test
    fun bearingDeltaWrapsAcrossNorthCorrectly() {
        val delta = PuckStabilityDecider.bearingDeltaDegrees(previousBearing = 359.0, currentBearing = 1.0)
        assertEquals(2.0, delta, 0.0001)
    }
}
