package com.drivest.navigation.practice

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PracticeFlowDecisionsTest {

    @Test
    fun arrivalImmediateRequiresWithin40mAndLowSpeed() {
        val arrived = PracticeFlowDecisions.evaluateArrival(
            nowMs = 10_000L,
            distanceToStartMeters = 35.0,
            speedMps = 3.2,
            withinRadiusSinceMs = null,
            immediateRadiusMeters = 40.0,
            dwellRadiusMeters = 60.0,
            dwellWindowMs = 10_000L,
            maxArrivalSpeedMps = 3.57632
        )

        assertTrue(arrived.arrived)
        assertNotNull(arrived.withinRadiusSinceMs)
    }

    @Test
    fun dwellArrivalTriggersAfter10SecondsWithin60m() {
        val first = PracticeFlowDecisions.evaluateArrival(
            nowMs = 10_000L,
            distanceToStartMeters = 55.0,
            speedMps = 6.0,
            withinRadiusSinceMs = null,
            immediateRadiusMeters = 40.0,
            dwellRadiusMeters = 60.0,
            dwellWindowMs = 10_000L,
            maxArrivalSpeedMps = 3.57632
        )
        assertFalse(first.arrived)

        val second = PracticeFlowDecisions.evaluateArrival(
            nowMs = 20_001L,
            distanceToStartMeters = 54.0,
            speedMps = 6.0,
            withinRadiusSinceMs = first.withinRadiusSinceMs,
            immediateRadiusMeters = 40.0,
            dwellRadiusMeters = 60.0,
            dwellWindowMs = 10_000L,
            maxArrivalSpeedMps = 3.57632
        )
        assertTrue(second.arrived)
    }

    @Test
    fun dwellWindowResetsWhenLeavingRadius() {
        val inside = PracticeFlowDecisions.evaluateArrival(
            nowMs = 10_000L,
            distanceToStartMeters = 59.0,
            speedMps = 4.0,
            withinRadiusSinceMs = null,
            immediateRadiusMeters = 40.0,
            dwellRadiusMeters = 60.0,
            dwellWindowMs = 10_000L,
            maxArrivalSpeedMps = 3.57632
        )
        assertNotNull(inside.withinRadiusSinceMs)

        val outside = PracticeFlowDecisions.evaluateArrival(
            nowMs = 12_000L,
            distanceToStartMeters = 80.0,
            speedMps = 4.0,
            withinRadiusSinceMs = inside.withinRadiusSinceMs,
            immediateRadiusMeters = 40.0,
            dwellRadiusMeters = 60.0,
            dwellWindowMs = 10_000L,
            maxArrivalSpeedMps = 3.57632
        )

        assertFalse(outside.arrived)
        assertNull(outside.withinRadiusSinceMs)
    }

    @Test
    fun transitionRequiresArrivalAndNoInProgressFlags() {
        assertFalse(
            PracticeFlowDecisions.shouldTransitionToPracticeRoute(
                arrived = false,
                transitionInProgress = false,
                routeStartInProgress = false
            )
        )
        assertFalse(
            PracticeFlowDecisions.shouldTransitionToPracticeRoute(
                arrived = true,
                transitionInProgress = true,
                routeStartInProgress = false
            )
        )
        assertFalse(
            PracticeFlowDecisions.shouldTransitionToPracticeRoute(
                arrived = true,
                transitionInProgress = false,
                routeStartInProgress = true
            )
        )
        assertTrue(
            PracticeFlowDecisions.shouldTransitionToPracticeRoute(
                arrived = true,
                transitionInProgress = false,
                routeStartInProgress = false
            )
        )
    }

    @Test
    fun rerouteTriggersWhenUserPassesStartThenMovesAway() {
        val shouldReroute = PracticeFlowDecisions.shouldRerouteToStart(
            nowMs = 30_000L,
            lastRerouteAtMs = 0L,
            rerouteCooldownMs = 8_000L,
            distanceToStartMeters = 95.0,
            closestDistanceSeenMeters = 25.0,
            missedDeltaMeters = 30.0,
            routeDistanceRemainingMeters = 220.0,
            completionPercent = 60,
            routeArrivalDistanceMeters = 40.0
        )

        assertTrue(shouldReroute)
    }

    @Test
    fun rerouteRespectsCooldown() {
        val shouldReroute = PracticeFlowDecisions.shouldRerouteToStart(
            nowMs = 30_000L,
            lastRerouteAtMs = 25_500L,
            rerouteCooldownMs = 8_000L,
            distanceToStartMeters = 120.0,
            closestDistanceSeenMeters = 20.0,
            missedDeltaMeters = 30.0,
            routeDistanceRemainingMeters = 100.0,
            completionPercent = 99,
            routeArrivalDistanceMeters = 40.0
        )

        assertFalse(shouldReroute)
    }
}
