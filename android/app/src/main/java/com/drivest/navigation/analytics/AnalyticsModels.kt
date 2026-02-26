package com.drivest.navigation.analytics

import com.drivest.navigation.profile.DriverProfile
import com.drivest.navigation.settings.PreferredUnitsSetting
import com.drivest.navigation.theory.content.TheoryReadiness

data class SessionExportRecord(
    val exportedAtMs: Long,
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

data class HazardInsight(
    val id: String,
    val label: String,
    val count: Int,
    val relativePercent: Int
)

data class TheoryTopicInsight(
    val topicId: String,
    val topicTitle: String,
    val attempts: Int,
    val accuracyPercent: Int,
    val masteryPercent: Int
)

data class AnalyticsDashboardData(
    val profile: DriverProfile,
    val preferredUnits: PreferredUnitsSetting,
    val theoryReadiness: TheoryReadiness,
    val theoryStreakDays: Int,
    val totalTheoryAttempts: Int,
    val totalTheoryAccuracyPercent: Int,
    val masteredTopicsCount: Int,
    val totalTheoryTopics: Int,
    val weakestTheoryTopics: List<TheoryTopicInsight>,
    val strongestTheoryTopics: List<TheoryTopicInsight>,
    val sessions: List<SessionExportRecord>,
    val recentSessions: List<SessionExportRecord>,
    val completionRatePercent: Int,
    val avgRecentStress: Int,
    val avgRecentConfidence: Int,
    val avgRecentComplexity: Int,
    val totalDrivenDistanceMeters: Long,
    val combinedReadinessScore: Int,
    val momentumLabel: String,
    val hazardInsights: List<HazardInsight>,
    val actionPlan: List<String>
)
