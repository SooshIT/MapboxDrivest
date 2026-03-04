package com.drivest.navigation.parking

internal object ParkingSpotRules {

    fun feeFlagFor(tags: Map<String, String>): ParkingFeeFlag {
        val fee = tags["fee"]?.trim()?.lowercase().orEmpty()
        return when (fee) {
            "no", "false" -> ParkingFeeFlag.LIKELY_FREE
            "yes", "true" -> ParkingFeeFlag.LIKELY_PAID
            else -> ParkingFeeFlag.UNKNOWN
        }
    }

    fun rulesSummaryFor(tags: Map<String, String>): String {
        val openingHours = tags["opening_hours"]?.trim().orEmpty()
        val maxStay = tags["maxstay"]?.trim().orEmpty()
        val parts = mutableListOf<String>()
        if (openingHours.isNotBlank()) {
            parts += "Hours: $openingHours"
        }
        if (maxStay.isNotBlank()) {
            parts += "Max stay: $maxStay"
        }
        return if (parts.isEmpty()) {
            "Rules unclear. Check signage."
        } else {
            parts.joinToString(separator = " | ")
        }
    }

    fun confidenceScoreFor(tags: Map<String, String>): Int {
        var score = 30
        if (tags["fee"].isNullOrBlank().not()) score += 25
        if (tags["opening_hours"].isNullOrBlank().not()) score += 20
        if (tags["maxstay"].isNullOrBlank().not()) score += 15
        if (tags["name"].isNullOrBlank().not()) score += 10
        return score.coerceAtMost(95)
    }

    fun isAccessible(tags: Map<String, String>): Boolean {
        val wheelchair = tags["wheelchair"]?.trim()?.lowercase()
        val disabled = tags["disabled"]?.trim()?.lowercase()
        return wheelchair == "yes" || disabled == "yes"
    }

    fun hasPrivateAccess(tags: Map<String, String>): Boolean {
        return tags["access"]?.trim()?.lowercase() == "private"
    }
}
