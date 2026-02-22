package com.drivest.navigation.report

import kotlin.math.max

data class StressSegment(
    val title: String,
    val score: Int,
    val detail: String
)

data class CoachingReport(
    val topStressSegments: List<StressSegment>,
    val suggestions: List<String>
)

class CoachingReportEngine {

    fun build(summary: SessionSummaryPayload): CoachingReport {
        val candidates = mutableListOf<StressSegment>()
        if (summary.roundaboutCount > 0) {
            candidates += StressSegment(
                title = "Roundabout cluster",
                score = summary.roundaboutCount * 8,
                detail = "${summary.roundaboutCount} roundabouts encountered."
            )
        }
        if (summary.trafficSignalCount > 0) {
            candidates += StressSegment(
                title = "Traffic-light sequence",
                score = summary.trafficSignalCount * 5,
                detail = "${summary.trafficSignalCount} traffic signal events."
            )
        }
        if (summary.zebraCount > 0) {
            candidates += StressSegment(
                title = "Pedestrian crossings",
                score = summary.zebraCount * 4,
                detail = "${summary.zebraCount} zebra crossings on route."
            )
        }
        if (summary.schoolCount > 0) {
            candidates += StressSegment(
                title = "School-zone awareness",
                score = summary.schoolCount * 7,
                detail = "${summary.schoolCount} school-zone prompts."
            )
        }
        if (summary.offRouteCount > 0) {
            candidates += StressSegment(
                title = "Route tracking drift",
                score = summary.offRouteCount * 10,
                detail = "${summary.offRouteCount} off-route corrections."
            )
        }

        if (candidates.isEmpty()) {
            candidates += StressSegment(
                title = "General route pressure",
                score = max(5, summary.stressIndex),
                detail = "Overall stress index ${summary.stressIndex}."
            )
        }

        val topSegments = candidates.sortedByDescending { it.score }.take(3)
        val suggestions = buildSuggestions(summary, topSegments)
        return CoachingReport(topStressSegments = topSegments, suggestions = suggestions)
    }

    private fun buildSuggestions(
        summary: SessionSummaryPayload,
        segments: List<StressSegment>
    ): List<String> {
        val advice = mutableListOf<String>()
        if (segments.any { it.title.contains("Roundabout", ignoreCase = true) }) {
            advice += "Rehearse lane choice and mirror checks before each roundabout entry."
        }
        if (summary.schoolCount > 0 || summary.zebraCount > 0) {
            advice += "Scan early for pedestrian hazards and plan smoother speed reduction."
        }
        if (summary.offRouteCount > 0) {
            advice += "Use earlier mirror-signal-position checks to reduce late direction changes."
        }
        if (advice.size < 3) {
            advice += "Repeat this route at lower traffic hours, then retest during busier periods."
        }
        if (advice.size < 3) {
            advice += "Call out the next two maneuvers aloud to build anticipation consistency."
        }
        return advice.take(3)
    }
}

