package com.drivest.navigation.parking

data class ParkingSpot(
    val id: String,
    val title: String,
    val lat: Double,
    val lng: Double,
    val distanceMetersToDestination: Int,
    val source: String,
    val feeFlag: ParkingFeeFlag,
    val rulesSummary: String,
    val confidenceScore: Int,
    val isAccessible: Boolean
)

data class ParkingSpotRecord(
    val id: String,
    val title: String,
    val lat: Double,
    val lng: Double,
    val source: String,
    val feeFlag: ParkingFeeFlag,
    val rulesSummary: String,
    val confidenceScore: Int,
    val isAccessible: Boolean
)

enum class ParkingFeeFlag {
    LIKELY_FREE,
    LIKELY_PAID,
    UNKNOWN
}
