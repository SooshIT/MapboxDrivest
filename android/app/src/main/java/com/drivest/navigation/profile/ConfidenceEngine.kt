package com.drivest.navigation.profile

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

class ConfidenceEngine {

    fun compute(profile: DriverProfile): Int {
        val sessionFactor = min(45.0, profile.totalSessions * 4.5)
        val completionBonus = min(20.0, profile.practiceCompletions * 2.0)
        val stressFactor = max(0.0, 35.0 - (profile.averageStressIndex * 0.35))
        val offRoutePenalty = min(30.0, profile.totalOffRouteEvents * 1.5)

        val score = sessionFactor + completionBonus + stressFactor - offRoutePenalty
        return score.roundToInt().coerceIn(0, 100)
    }
}
