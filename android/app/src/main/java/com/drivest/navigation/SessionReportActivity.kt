package com.drivest.navigation

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.drivest.navigation.databinding.ActivitySessionReportBinding
import com.drivest.navigation.report.CoachingReportEngine
import com.drivest.navigation.report.SessionSummaryExporter
import com.drivest.navigation.report.SessionSummaryPayload
import com.drivest.navigation.theory.TheoryFeatureFlags
import com.drivest.navigation.theory.navigation.TheoryNavigation
import com.drivest.navigation.theory.screens.TheoryTopicDetailActivity
import com.drivest.navigation.theory.services.MapRouteTagsToTheoryTopics
import com.google.android.material.button.MaterialButton
import com.google.android.material.snackbar.Snackbar
import java.util.Locale

class SessionReportActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySessionReportBinding
    private val coachingReportEngine = CoachingReportEngine()
    private val summaryExporter by lazy { SessionSummaryExporter(applicationContext) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySessionReportBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val summary = readSummaryFromIntent() ?: run {
            finish()
            return
        }
        renderSummary(summary)

        binding.sessionReportExportButton.setOnClickListener {
            val exported = summaryExporter.export(summary)
            Snackbar.make(
                binding.root,
                getString(R.string.session_report_exported, exported.absolutePath),
                Snackbar.LENGTH_LONG
            ).show()
        }

        binding.sessionReportDoneButton.setOnClickListener {
            finish()
        }
    }

    private fun renderSummary(summary: SessionSummaryPayload) {
        binding.sessionReportTitle.text = getString(
            R.string.session_report_title,
            summary.routeId ?: getString(R.string.session_report_unknown_route)
        )
        binding.sessionReportMeta.text = getString(
            R.string.session_report_meta,
            summary.centreId ?: getString(R.string.session_report_unknown_centre),
            summary.durationSeconds / 60,
            summary.offRouteCount
        )

        val coachingReport = coachingReportEngine.build(summary)
        val segmentLines = coachingReport.topStressSegments.mapIndexed { index, segment ->
            "${index + 1}. ${segment.title} (${segment.score}) - ${segment.detail}"
        }
        binding.sessionReportSegments.text = segmentLines.joinToString("\n")

        val suggestions = coachingReport.suggestions.mapIndexed { index, suggestion ->
            "${index + 1}. $suggestion"
        }
        binding.sessionReportSuggestions.text = suggestions.joinToString("\n")

        binding.sessionReportHazardCounts.text = String.format(
            Locale.UK,
            "R:%d TL:%d Z:%d S:%d B:%d",
            summary.roundaboutCount,
            summary.trafficSignalCount,
            summary.zebraCount,
            summary.schoolCount,
            summary.busLaneCount
        )
        renderTheoryRecommendations(summary)
    }

    private fun renderTheoryRecommendations(summary: SessionSummaryPayload) {
        if (!TheoryFeatureFlags.isTheoryModuleEnabled()) {
            binding.sessionReportTheoryHeading.visibility = android.view.View.GONE
            binding.sessionReportTheoryContainer.visibility = android.view.View.GONE
            return
        }
        val tags = MapRouteTagsToTheoryTopics.inferTagsFromSessionSummary(summary)
        val topicIds = MapRouteTagsToTheoryTopics.mapTags(tags)
        if (topicIds.isEmpty()) {
            binding.sessionReportTheoryHeading.visibility = android.view.View.GONE
            binding.sessionReportTheoryContainer.visibility = android.view.View.GONE
            return
        }
        binding.sessionReportTheoryContainer.removeAllViews()
        binding.sessionReportTheoryHeading.visibility = android.view.View.VISIBLE
        binding.sessionReportTheoryContainer.visibility = android.view.View.VISIBLE
        topicIds.forEach { topicId ->
            val button = MaterialButton(this).apply {
                text = formatTopicLabel(topicId)
                isAllCaps = false
                setOnClickListener {
                    startActivity(
                        Intent(this@SessionReportActivity, TheoryTopicDetailActivity::class.java).apply {
                            putExtra(TheoryNavigation.EXTRA_TOPIC_ID, topicId)
                            putExtra(TheoryNavigation.EXTRA_ENTRY_SOURCE, "session_report")
                        }
                    )
                }
            }
            binding.sessionReportTheoryContainer.addView(button)
        }
    }

    private fun formatTopicLabel(topicId: String): String {
        return topicId
            .split('_')
            .filter { it.isNotBlank() }
            .joinToString(" ") { token ->
                token.replaceFirstChar { first ->
                    if (first.isLowerCase()) first.titlecase(Locale.UK) else first.toString()
                }
            }
    }

    private fun readSummaryFromIntent(): SessionSummaryPayload? {
        val stressIndex = intent.getIntExtra(EXTRA_STRESS_INDEX, Int.MIN_VALUE)
        if (stressIndex == Int.MIN_VALUE) return null

        return SessionSummaryPayload(
            centreId = intent.getStringExtra(EXTRA_CENTRE_ID),
            routeId = intent.getStringExtra(EXTRA_ROUTE_ID),
            stressIndex = stressIndex,
            complexityScore = intent.getIntExtra(EXTRA_COMPLEXITY_SCORE, 0),
            confidenceScore = intent.getIntExtra(EXTRA_CONFIDENCE_SCORE, 0),
            offRouteCount = intent.getIntExtra(EXTRA_OFF_ROUTE_COUNT, 0),
            completionFlag = intent.getBooleanExtra(EXTRA_COMPLETION_FLAG, false),
            durationSeconds = intent.getIntExtra(EXTRA_DURATION_SECONDS, 0),
            distanceMetersDriven = intent.getIntExtra(EXTRA_DISTANCE_METERS, 0),
            roundaboutCount = intent.getIntExtra(EXTRA_ROUNDABOUT_COUNT, 0),
            trafficSignalCount = intent.getIntExtra(EXTRA_TRAFFIC_SIGNAL_COUNT, 0),
            zebraCount = intent.getIntExtra(EXTRA_ZEBRA_COUNT, 0),
            schoolCount = intent.getIntExtra(EXTRA_SCHOOL_COUNT, 0),
            busLaneCount = intent.getIntExtra(EXTRA_BUS_LANE_COUNT, 0)
        )
    }

    companion object {
        const val EXTRA_CENTRE_ID = "session_report_centre_id"
        const val EXTRA_ROUTE_ID = "session_report_route_id"
        const val EXTRA_STRESS_INDEX = "session_report_stress_index"
        const val EXTRA_COMPLEXITY_SCORE = "session_report_complexity_score"
        const val EXTRA_CONFIDENCE_SCORE = "session_report_confidence_score"
        const val EXTRA_OFF_ROUTE_COUNT = "session_report_off_route_count"
        const val EXTRA_COMPLETION_FLAG = "session_report_completion_flag"
        const val EXTRA_DURATION_SECONDS = "session_report_duration_seconds"
        const val EXTRA_DISTANCE_METERS = "session_report_distance_meters"
        const val EXTRA_ROUNDABOUT_COUNT = "session_report_roundabout_count"
        const val EXTRA_TRAFFIC_SIGNAL_COUNT = "session_report_traffic_signal_count"
        const val EXTRA_ZEBRA_COUNT = "session_report_zebra_count"
        const val EXTRA_SCHOOL_COUNT = "session_report_school_count"
        const val EXTRA_BUS_LANE_COUNT = "session_report_bus_lane_count"
    }
}
