package com.drivest.navigation.speed

import org.junit.Assert.assertEquals
import org.junit.Test

class SpeedLimitNormalizerTest {

    @Test
    fun mapsKnownMapboxOddValuesToExpectedUkLimits() {
        assertEquals(20, SpeedLimitNormalizer.normalizeSpeedLimit(19))
        assertEquals(30, SpeedLimitNormalizer.normalizeSpeedLimit(31))
    }

    @Test
    fun snapsToNearestUkStandardWhenWithinThreeMph() {
        assertEquals(20, SpeedLimitNormalizer.normalizeSpeedLimit(22))
        assertEquals(30, SpeedLimitNormalizer.normalizeSpeedLimit(27))
        assertEquals(40, SpeedLimitNormalizer.normalizeSpeedLimit(37))
        assertEquals(70, SpeedLimitNormalizer.normalizeSpeedLimit(73))
    }

    @Test
    fun keepsRawValueWhenFarFromUkStandards() {
        assertEquals(34, SpeedLimitNormalizer.normalizeSpeedLimit(34))
        assertEquals(66, SpeedLimitNormalizer.normalizeSpeedLimit(66))
        assertEquals(85, SpeedLimitNormalizer.normalizeSpeedLimit(85))
    }
}

