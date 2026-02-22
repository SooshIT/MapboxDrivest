package com.drivest.navigation.intelligence

object StressAdjustmentPolicy {

    const val MAX_ETA_INCREASE_SECONDS = 3 * 60.0
    const val MAX_DISTANCE_INCREASE_RATIO = 0.10
    const val ANNOUNCEMENT_COOLDOWN_MS = 5 * 60 * 1000L

    data class RouteStressSnapshot(
        val stressIndex: Int,
        val etaSeconds: Double,
        val distanceMeters: Double
    )

    fun shouldSwitchToLowerStressRoute(
        current: RouteStressSnapshot,
        candidate: RouteStressSnapshot
    ): Boolean {
        if (candidate.stressIndex >= current.stressIndex) return false

        val etaIncreaseSeconds = candidate.etaSeconds - current.etaSeconds
        val distanceIncreaseRatio = if (current.distanceMeters <= 0.0) {
            0.0
        } else {
            (candidate.distanceMeters - current.distanceMeters) / current.distanceMeters
        }

        val etaWithinThreshold = etaIncreaseSeconds <= MAX_ETA_INCREASE_SECONDS
        val distanceWithinThreshold = distanceIncreaseRatio <= MAX_DISTANCE_INCREASE_RATIO
        return etaWithinThreshold || distanceWithinThreshold
    }

    fun canAnnounceAdjustment(
        nowMs: Long,
        lastAnnouncementAtMs: Long
    ): Boolean {
        if (lastAnnouncementAtMs <= 0L) return true
        return nowMs - lastAnnouncementAtMs >= ANNOUNCEMENT_COOLDOWN_MS
    }
}

