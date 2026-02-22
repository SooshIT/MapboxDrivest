package com.drivest.navigation.speed

import kotlin.math.abs

object SpeedLimitNormalizer {

    private val ukStandardMphLimits = listOf(20, 30, 40, 50, 60, 70)
    private const val ukSnapMaxDeltaMph = 3

    /**
     * Snaps raw UK mph values to canonical limits when they are close enough.
     * Examples: 19 -> 20, 31 -> 30.
     */
    fun normalizeSpeedLimit(rawMph: Int): Int {
        val nearest = ukStandardMphLimits.minByOrNull { candidate ->
            abs(candidate - rawMph)
        } ?: return rawMph
        return if (abs(nearest - rawMph) <= ukSnapMaxDeltaMph) {
            nearest
        } else {
            rawMph
        }
    }
}

