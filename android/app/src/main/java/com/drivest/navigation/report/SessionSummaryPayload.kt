package com.drivest.navigation.report

data class SessionSummaryPayload(
    val centreId: String?,
    val routeId: String?,
    val stressIndex: Int,
    val complexityScore: Int,
    val confidenceScore: Int,
    val offRouteCount: Int,
    val completionFlag: Boolean,
    val durationSeconds: Int,
    val distanceMetersDriven: Int,
    val roundaboutCount: Int,
    val trafficSignalCount: Int,
    val zebraCount: Int,
    val schoolCount: Int,
    val busLaneCount: Int
)

