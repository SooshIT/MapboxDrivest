package com.drivest.navigation.analytics

import android.content.Context
import com.drivest.navigation.profile.DriverProfileRepository
import com.drivest.navigation.report.SessionSummaryExporter
import com.drivest.navigation.settings.PreferredUnitsSetting
import com.drivest.navigation.settings.SettingsRepository
import com.drivest.navigation.theory.content.TheoryPackLoader
import com.drivest.navigation.theory.content.TheoryReadinessCalculator
import com.drivest.navigation.theory.storage.TheoryProgressStore
import kotlinx.coroutines.flow.first
import org.json.JSONObject
import java.io.File
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import kotlin.math.roundToInt

class DrivestAnalyticsRepository(
    context: Context
) {
    private val appContext = context.applicationContext
    private val driverProfileRepository = DriverProfileRepository(appContext)
    private val settingsRepository = SettingsRepository(appContext)
    private val theoryProgressStore = TheoryProgressStore(appContext)
    private val theoryPackLoader = TheoryPackLoader(appContext)
    private val sessionSummaryExporter = SessionSummaryExporter(appContext)

    suspend fun loadDashboard(): AnalyticsDashboardData {
        val profile = driverProfileRepository.profile.first()
        val preferredUnits = settingsRepository.preferredUnits.first()
        val theoryProgress = theoryProgressStore.progress.first()
        val theoryPack = theoryPackLoader.load()
        val totalTheoryTopics = theoryPack.topics.size.coerceAtLeast(1)
        val theoryReadiness = TheoryReadinessCalculator.calculate(
            progress = theoryProgress,
            totalTopics = totalTheoryTopics
        )

        val topicTitlesById = theoryPack.topics.associate { it.id to it.title }
        val topicInsights = theoryProgress.topicStats.map { (topicId, stat) ->
            val accuracy = if (stat.attempts > 0) {
                ((stat.correct * 100f) / stat.attempts.toFloat()).roundToInt().coerceIn(0, 100)
            } else {
                0
            }
            TheoryTopicInsight(
                topicId = topicId,
                topicTitle = topicTitlesById[topicId].orEmpty().ifBlank { humanizeTopicId(topicId) },
                attempts = stat.attempts,
                accuracyPercent = accuracy,
                masteryPercent = stat.masteryPercent
            )
        }

        val weakestTheoryTopics = topicInsights
            .filter { it.attempts > 0 }
            .sortedWith(
                compareBy<TheoryTopicInsight> { it.accuracyPercent }
                    .thenBy { it.masteryPercent }
                    .thenByDescending { it.attempts }
            )
            .take(5)
        val strongestTheoryTopics = topicInsights
            .filter { it.attempts > 0 }
            .sortedWith(
                compareByDescending<TheoryTopicInsight> { it.accuracyPercent }
                    .thenByDescending { it.masteryPercent }
                    .thenByDescending { it.attempts }
            )
            .take(5)

        val totalTheoryAttempts = theoryProgress.topicStats.values.sumOf { it.attempts }
        val totalTheoryCorrect = theoryProgress.topicStats.values.sumOf { it.correct }
        val totalTheoryAccuracyPercent = if (totalTheoryAttempts > 0) {
            ((totalTheoryCorrect * 100f) / totalTheoryAttempts.toFloat()).roundToInt().coerceIn(0, 100)
        } else {
            0
        }
        val masteredTopicsCount = theoryProgress.topicStats.values.count { it.masteryPercent >= 75 }

        val sessions = loadSessionExports()
        val recentSessions = sessions.takeLast(10)
        val completionRatePercent = if (sessions.isNotEmpty()) {
            ((sessions.count { it.completionFlag } * 100f) / sessions.size.toFloat()).roundToInt().coerceIn(0, 100)
        } else {
            0
        }
        val avgRecentStress = averageInt(recentSessions) { it.stressIndex }
        val avgRecentConfidence = averageInt(recentSessions) { it.confidenceScore }
        val avgRecentComplexity = averageInt(recentSessions) { it.complexityScore }

        val totalDrivenDistanceMeters = sessions.sumOf { it.distanceMetersDriven.toLong() }

        val hazardTotals = linkedMapOf(
            "roundabout" to (recentSessions.sumOf { it.roundaboutCount }),
            "traffic_signal" to (recentSessions.sumOf { it.trafficSignalCount }),
            "zebra" to (recentSessions.sumOf { it.zebraCount }),
            "school" to (recentSessions.sumOf { it.schoolCount }),
            "bus_lane" to (recentSessions.sumOf { it.busLaneCount }),
            "off_route" to (recentSessions.sumOf { it.offRouteCount })
        )
        val hazardMax = hazardTotals.values.maxOrNull()?.coerceAtLeast(1) ?: 1
        val hazardInsights = hazardTotals.map { (id, count) ->
            HazardInsight(
                id = id,
                label = when (id) {
                    "roundabout" -> "Roundabouts"
                    "traffic_signal" -> "Traffic signals"
                    "zebra" -> "Zebra crossings"
                    "school" -> "School zones"
                    "bus_lane" -> "Bus lanes"
                    else -> "Off-route events"
                },
                count = count,
                relativePercent = ((count * 100f) / hazardMax.toFloat()).roundToInt().coerceIn(0, 100)
            )
        }

        val drivingSignal = if (recentSessions.isEmpty()) {
            profile.confidenceScore
        } else {
            ((avgRecentConfidence * 0.55f) +
                ((100 - avgRecentStress) * 0.25f) +
                ((100 - (averageInt(recentSessions) { it.offRouteCount }.coerceAtMost(10) * 10)) * 0.20f))
                .roundToInt()
                .coerceIn(0, 100)
        }
        val combinedReadinessScore = ((drivingSignal * 0.6f) + (theoryReadiness.score * 0.4f))
            .roundToInt()
            .coerceIn(0, 100)

        val momentumLabel = when {
            combinedReadinessScore >= 80 -> "Hot streak"
            combinedReadinessScore >= 60 -> "Building momentum"
            combinedReadinessScore >= 35 -> "Steady progress"
            else -> "Starting out"
        }

        val actionPlan = buildActionPlan(
            profileConfidence = profile.confidenceScore,
            theoryReadinessScore = theoryReadiness.score,
            recentSessions = recentSessions,
            weakestTheoryTopics = weakestTheoryTopics,
            hazardInsights = hazardInsights
        )

        return AnalyticsDashboardData(
            profile = profile,
            preferredUnits = preferredUnits,
            theoryReadiness = theoryReadiness,
            theoryStreakDays = theoryProgress.streakDays,
            totalTheoryAttempts = totalTheoryAttempts,
            totalTheoryAccuracyPercent = totalTheoryAccuracyPercent,
            masteredTopicsCount = masteredTopicsCount,
            totalTheoryTopics = totalTheoryTopics,
            weakestTheoryTopics = weakestTheoryTopics,
            strongestTheoryTopics = strongestTheoryTopics,
            sessions = sessions,
            recentSessions = recentSessions,
            completionRatePercent = completionRatePercent,
            avgRecentStress = avgRecentStress,
            avgRecentConfidence = avgRecentConfidence,
            avgRecentComplexity = avgRecentComplexity,
            totalDrivenDistanceMeters = totalDrivenDistanceMeters,
            combinedReadinessScore = combinedReadinessScore,
            momentumLabel = momentumLabel,
            hazardInsights = hazardInsights,
            actionPlan = actionPlan
        )
    }

    private fun loadSessionExports(): List<SessionExportRecord> {
        val exportDir = File(sessionSummaryExporter.exportDirectoryPath())
        if (!exportDir.exists()) return emptyList()

        return exportDir.listFiles()
            ?.asSequence()
            ?.filter { it.isFile && it.extension.equals("json", ignoreCase = true) }
            ?.sortedBy { it.lastModified() }
            ?.mapNotNull { file -> parseSessionExport(file) }
            ?.toList()
            .orEmpty()
    }

    private fun parseSessionExport(file: File): SessionExportRecord? {
        val root = runCatching { JSONObject(file.readText()) }.getOrNull() ?: return null
        if (root.optString("type") == "developer_snapshot") return null
        if (!root.has("stressIndex")) return null

        val hazard = root.optJSONObject("hazardCounts") ?: JSONObject()
        return SessionExportRecord(
            exportedAtMs = parseIsoTimestamp(root.optString("exportedAt")).takeIf { it > 0L } ?: file.lastModified(),
            stressIndex = root.optInt("stressIndex", 0).coerceIn(0, 100),
            complexityScore = root.optInt("complexityScore", 0).coerceIn(0, 100),
            confidenceScore = root.optInt("confidenceScore", 0).coerceIn(0, 100),
            offRouteCount = root.optInt("offRouteCount", 0).coerceAtLeast(0),
            completionFlag = root.optBoolean("completionFlag", false),
            durationSeconds = root.optInt("durationSeconds", 0).coerceAtLeast(0),
            distanceMetersDriven = root.optInt("distanceMetersDriven", 0).coerceAtLeast(0),
            roundaboutCount = hazard.optInt("roundabout", 0).coerceAtLeast(0),
            trafficSignalCount = hazard.optInt("trafficSignal", 0).coerceAtLeast(0),
            zebraCount = hazard.optInt("zebraCrossing", 0).coerceAtLeast(0),
            schoolCount = hazard.optInt("schoolZone", 0).coerceAtLeast(0),
            busLaneCount = hazard.optInt("busLane", 0).coerceAtLeast(0)
        )
    }

    private fun parseIsoTimestamp(value: String?): Long {
        if (value.isNullOrBlank()) return 0L
        return try {
            ISO_UTC_FORMAT.parse(value)?.time ?: 0L
        } catch (_: ParseException) {
            0L
        }
    }

    private fun averageInt(values: List<SessionExportRecord>, selector: (SessionExportRecord) -> Int): Int {
        if (values.isEmpty()) return 0
        return (values.sumOf(selector).toFloat() / values.size.toFloat()).roundToInt()
    }

    private fun humanizeTopicId(topicId: String): String {
        return topicId
            .trim()
            .replace('_', ' ')
            .replace('-', ' ')
            .split(Regex("\\s+"))
            .filter { it.isNotBlank() }
            .joinToString(" ") { token ->
                token.lowercase().replaceFirstChar { ch ->
                    if (ch.isLowerCase()) ch.titlecase(Locale.getDefault()) else ch.toString()
                }
            }
            .ifBlank { topicId }
    }

    private fun buildActionPlan(
        profileConfidence: Int,
        theoryReadinessScore: Int,
        recentSessions: List<SessionExportRecord>,
        weakestTheoryTopics: List<TheoryTopicInsight>,
        hazardInsights: List<HazardInsight>
    ): List<String> {
        val actions = mutableListOf<String>()
        if (theoryReadinessScore < 60) {
            val weakest = weakestTheoryTopics.firstOrNull()?.topicTitle
            actions += if (!weakest.isNullOrBlank()) {
                "Do a 10-question theory quiz on $weakest today to lift your readiness score."
            } else {
                "Complete a quick theory quiz today to improve your readiness score."
            }
        }
        val offRouteCount = recentSessions.sumOf { it.offRouteCount }
        if (offRouteCount >= 3) {
            actions += "Run one low-stress navigation route and focus on lane positioning before turns."
        }
        val topHazard = hazardInsights.maxByOrNull { it.count }
        if (topHazard != null && topHazard.count > 0) {
            actions += "Practice ${topHazard.label.lowercase()} scenarios next; they appear most in your recent sessions."
        }
        if (profileConfidence < 55 && actions.none { it.contains("practice", ignoreCase = true) }) {
            actions += "Repeat a short practice route and aim for fewer off-route events than your recent average."
        }
        if (actions.isEmpty()) {
            actions += "Keep the streak going: mix one practice route, one navigation route, and one theory quiz this week."
        }
        return actions.take(3)
    }

    private companion object {
        val ISO_UTC_FORMAT = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
    }
}
