package com.drivest.navigation.intelligence

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StressAdjustmentPolicyTest {

    @Test
    fun switchesWhenStressLowerAndEtaIncreaseWithinThreeMinutes() {
        val current = StressAdjustmentPolicy.RouteStressSnapshot(
            stressIndex = 72,
            etaSeconds = 1_200.0,
            distanceMeters = 14_000.0
        )
        val candidate = StressAdjustmentPolicy.RouteStressSnapshot(
            stressIndex = 60,
            etaSeconds = 1_360.0, // +160s
            distanceMeters = 16_100.0 // +15%
        )

        assertTrue(
            StressAdjustmentPolicy.shouldSwitchToLowerStressRoute(
                current = current,
                candidate = candidate
            )
        )
    }

    @Test
    fun switchesWhenStressLowerAndDistanceIncreaseWithinTenPercent() {
        val current = StressAdjustmentPolicy.RouteStressSnapshot(
            stressIndex = 68,
            etaSeconds = 1_200.0,
            distanceMeters = 14_000.0
        )
        val candidate = StressAdjustmentPolicy.RouteStressSnapshot(
            stressIndex = 55,
            etaSeconds = 1_500.0, // +300s
            distanceMeters = 15_200.0 // +8.57%
        )

        assertTrue(
            StressAdjustmentPolicy.shouldSwitchToLowerStressRoute(
                current = current,
                candidate = candidate
            )
        )
    }

    @Test
    fun doesNotSwitchWhenStressLowerButBothThresholdsExceeded() {
        val current = StressAdjustmentPolicy.RouteStressSnapshot(
            stressIndex = 70,
            etaSeconds = 1_000.0,
            distanceMeters = 10_000.0
        )
        val candidate = StressAdjustmentPolicy.RouteStressSnapshot(
            stressIndex = 62,
            etaSeconds = 1_220.0, // +220s
            distanceMeters = 11_400.0 // +14%
        )

        assertFalse(
            StressAdjustmentPolicy.shouldSwitchToLowerStressRoute(
                current = current,
                candidate = candidate
            )
        )
    }

    @Test
    fun doesNotSwitchWhenStressIsNotLower() {
        val current = StressAdjustmentPolicy.RouteStressSnapshot(
            stressIndex = 40,
            etaSeconds = 1_100.0,
            distanceMeters = 11_000.0
        )
        val candidate = StressAdjustmentPolicy.RouteStressSnapshot(
            stressIndex = 41,
            etaSeconds = 1_050.0,
            distanceMeters = 10_500.0
        )

        assertFalse(
            StressAdjustmentPolicy.shouldSwitchToLowerStressRoute(
                current = current,
                candidate = candidate
            )
        )
    }

    @Test
    fun announcementCooldownEnforcesFiveMinutes() {
        val nowMs = 1_000_000L
        assertFalse(
            StressAdjustmentPolicy.canAnnounceAdjustment(
                nowMs = nowMs,
                lastAnnouncementAtMs = nowMs - (StressAdjustmentPolicy.ANNOUNCEMENT_COOLDOWN_MS - 1_000L)
            )
        )
        assertTrue(
            StressAdjustmentPolicy.canAnnounceAdjustment(
                nowMs = nowMs,
                lastAnnouncementAtMs = nowMs - StressAdjustmentPolicy.ANNOUNCEMENT_COOLDOWN_MS
            )
        )
    }
}

