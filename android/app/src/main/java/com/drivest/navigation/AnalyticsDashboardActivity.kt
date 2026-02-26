package com.drivest.navigation

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.drivest.navigation.analytics.AnalyticsDashboardData
import com.drivest.navigation.analytics.DrivestAnalyticsRepository
import com.drivest.navigation.analytics.HazardInsight
import com.drivest.navigation.analytics.SessionExportRecord
import com.drivest.navigation.settings.PreferredUnitsSetting
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.text.NumberFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

class AnalyticsDashboardActivity : AppCompatActivity() {

    private val analyticsRepository by lazy { DrivestAnalyticsRepository(applicationContext) }

    private lateinit var loadingSpinner: ProgressBar
    private lateinit var errorCard: View
    private lateinit var errorMessageView: TextView
    private lateinit var retryButton: MaterialButton
    private lateinit var contentContainer: LinearLayout

    private lateinit var heroGaugeView: com.drivest.navigation.analytics.GaugeView
    private lateinit var heroMomentumView: TextView
    private lateinit var heroSessionsChip: TextView
    private lateinit var heroCompletionChip: TextView
    private lateinit var heroStreakChip: TextView
    private lateinit var heroDistanceChip: TextView
    private lateinit var heroSupportLineView: TextView

    private lateinit var gaugeConfidence: com.drivest.navigation.analytics.GaugeView
    private lateinit var gaugeCompletion: com.drivest.navigation.analytics.GaugeView
    private lateinit var gaugeTheoryReadiness: com.drivest.navigation.analytics.GaugeView
    private lateinit var gaugeTheoryAccuracy: com.drivest.navigation.analytics.GaugeView
    private lateinit var hazardRowsContainer: LinearLayout
    private lateinit var hazardEmptyView: TextView
    private lateinit var theorySummaryView: TextView
    private lateinit var weakestTopicsContainer: LinearLayout
    private lateinit var theoryEmptyView: TextView
    private lateinit var actionPlanContainer: LinearLayout
    private lateinit var recentSessionsContainer: LinearLayout
    private lateinit var recentSessionsEmptyView: TextView

    private lateinit var window5Button: MaterialButton
    private lateinit var window10Button: MaterialButton
    private lateinit var windowAllButton: MaterialButton

    private var dashboardData: AnalyticsDashboardData? = null
    private var sessionWindowPreset: SessionWindowPreset = SessionWindowPreset.FIVE

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_analytics_dashboard)

        bindViews()
        setupTopBar()
        setupWindowButtons()
        loadDashboard()
    }

    private fun bindViews() {
        loadingSpinner = findViewById(R.id.analyticsLoadingSpinner)
        errorCard = findViewById(R.id.analyticsErrorCard)
        errorMessageView = findViewById(R.id.analyticsErrorMessage)
        retryButton = findViewById(R.id.analyticsRetryButton)
        contentContainer = findViewById(R.id.analyticsContentContainer)

        heroGaugeView = findViewById(R.id.analyticsHeroGauge)
        heroMomentumView = findViewById(R.id.analyticsHeroMomentumValue)
        heroSessionsChip = findViewById(R.id.analyticsHeroSessionsChip)
        heroCompletionChip = findViewById(R.id.analyticsHeroCompletionChip)
        heroStreakChip = findViewById(R.id.analyticsHeroStreakChip)
        heroDistanceChip = findViewById(R.id.analyticsHeroDistanceChip)
        heroSupportLineView = findViewById(R.id.analyticsHeroSupportLine)

        gaugeConfidence = findViewById(R.id.analyticsGaugeConfidence)
        gaugeCompletion = findViewById(R.id.analyticsGaugeCompletion)
        gaugeTheoryReadiness = findViewById(R.id.analyticsGaugeTheoryReadiness)
        gaugeTheoryAccuracy = findViewById(R.id.analyticsGaugeTheoryAccuracy)
        hazardRowsContainer = findViewById(R.id.analyticsHazardRowsContainer)
        hazardEmptyView = findViewById(R.id.analyticsHazardEmpty)
        theorySummaryView = findViewById(R.id.analyticsTheorySummary)
        weakestTopicsContainer = findViewById(R.id.analyticsWeakTopicsContainer)
        theoryEmptyView = findViewById(R.id.analyticsTheoryEmpty)
        actionPlanContainer = findViewById(R.id.analyticsActionPlanContainer)
        recentSessionsContainer = findViewById(R.id.analyticsRecentSessionsContainer)
        recentSessionsEmptyView = findViewById(R.id.analyticsRecentSessionsEmpty)

        window5Button = findViewById(R.id.analyticsWindowFiveButton)
        window10Button = findViewById(R.id.analyticsWindowTenButton)
        windowAllButton = findViewById(R.id.analyticsWindowAllButton)

        retryButton.setOnClickListener { loadDashboard() }
    }

    private fun setupTopBar() {
        findViewById<ImageButton>(R.id.analyticsBackButton).setOnClickListener { finish() }
        findViewById<ImageButton>(R.id.analyticsRefreshButton).setOnClickListener { loadDashboard() }
        findViewById<TextView>(R.id.analyticsHazardSeeAllButton).setOnClickListener {
            startActivity(
                Intent(this, AnalyticsDetailActivity::class.java)
                    .putExtra(AnalyticsDetailActivity.EXTRA_MODE, AnalyticsDetailActivity.MODE_HAZARDS)
            )
        }
        findViewById<TextView>(R.id.analyticsTheorySeeAllButton).setOnClickListener {
            startActivity(
                Intent(this, AnalyticsDetailActivity::class.java)
                    .putExtra(AnalyticsDetailActivity.EXTRA_MODE, AnalyticsDetailActivity.MODE_THEORY)
            )
        }
    }

    private fun setupWindowButtons() {
        window5Button.setOnClickListener {
            sessionWindowPreset = SessionWindowPreset.FIVE
            updateWindowButtons()
            renderRecentSessions()
        }
        window10Button.setOnClickListener {
            sessionWindowPreset = SessionWindowPreset.TEN
            updateWindowButtons()
            renderRecentSessions()
        }
        windowAllButton.setOnClickListener {
            sessionWindowPreset = SessionWindowPreset.ALL
            updateWindowButtons()
            renderRecentSessions()
        }
        updateWindowButtons()
    }

    private fun loadDashboard() {
        lifecycleScope.launch {
            renderLoadingState(isLoading = true)
            val result = withContext(Dispatchers.IO) {
                runCatching { analyticsRepository.loadDashboard() }
            }
            result.onSuccess { data ->
                dashboardData = data
                renderDashboard(data)
                renderLoadingState(isLoading = false)
            }.onFailure { error ->
                renderLoadingState(isLoading = false)
                errorCard.isVisible = true
                contentContainer.isVisible = false
                errorMessageView.text = error.message ?: getString(R.string.analytics_load_failed)
            }
        }
    }

    private fun renderLoadingState(isLoading: Boolean) {
        loadingSpinner.isVisible = isLoading
        if (isLoading) {
            errorCard.isVisible = false
            contentContainer.isVisible = false
        }
    }

    private fun renderDashboard(data: AnalyticsDashboardData) {
        errorCard.isVisible = false
        contentContainer.isVisible = true

        renderHero(data)
        renderSnapshotRows(data)
        renderHazards(data)
        renderTheoryInsights(data)
        renderActionPlan(data)
        renderRecentSessions()
    }

    private fun renderHero(data: AnalyticsDashboardData) {
        heroGaugeView.progress = data.combinedReadinessScore
        heroMomentumView.text = data.momentumLabel
        heroSessionsChip.text = getString(R.string.analytics_metric_sessions_value, data.sessions.size)
        heroCompletionChip.text = getString(R.string.analytics_metric_completion_value, data.completionRatePercent)
        heroStreakChip.text = getString(R.string.analytics_metric_streak_value, data.theoryStreakDays)
        heroDistanceChip.text = getString(
            R.string.analytics_metric_distance_value,
            formatDistance(
                meters = data.totalDrivenDistanceMeters,
                units = data.preferredUnits,
                short = true
            )
        )
        heroSupportLineView.text = getString(
            R.string.analytics_support_line_value,
            data.masteredTopicsCount,
            data.totalTheoryTopics,
            data.totalTheoryAccuracyPercent,
            data.recentSessions.size
        )
    }

    private fun renderSnapshotRows(data: AnalyticsDashboardData) {
        gaugeConfidence.progress = data.avgRecentConfidence
        gaugeCompletion.progress = data.completionRatePercent
        gaugeTheoryReadiness.progress = data.theoryReadiness.score
        gaugeTheoryAccuracy.progress = data.totalTheoryAccuracyPercent
    }

    private fun renderHazards(data: AnalyticsDashboardData) {
        hazardRowsContainer.removeAllViews()
        val activeHazards = data.hazardInsights.filter { it.count > 0 }
            .sortedByDescending(HazardInsight::count)
            .take(4)

        hazardEmptyView.isVisible = activeHazards.isEmpty()
        if (activeHazards.isEmpty()) {
            hazardEmptyView.text = getString(R.string.analytics_hazard_empty)
            return
        }

        addGaugeGrid(
            container = hazardRowsContainer,
            items = activeHazards.map { hazard ->
                GaugeItem(
                    label    = hazard.label.truncLabel(),
                    progress = hazard.relativePercent,
                    inverted = true
                )
            }
        )
    }

    private fun renderTheoryInsights(data: AnalyticsDashboardData) {
        val hasTheoryAttempts = data.totalTheoryAttempts > 0
        theoryEmptyView.isVisible = !hasTheoryAttempts
        theorySummaryView.text = getString(
            R.string.analytics_theory_summary_value,
            data.totalTheoryAttempts,
            data.totalTheoryAccuracyPercent,
            data.masteredTopicsCount,
            data.totalTheoryTopics
        )

        weakestTopicsContainer.removeAllViews()
        if (!hasTheoryAttempts) {
            theoryEmptyView.text = getString(R.string.analytics_theory_empty)
            return
        }

        val weakest = data.weakestTheoryTopics.take(4)
        if (weakest.isEmpty()) {
            weakestTopicsContainer.addView(buildMutedText(getString(R.string.analytics_topics_empty_weakest)))
        } else {
            addGaugeGrid(
                container = weakestTopicsContainer,
                items = weakest.map { GaugeItem(it.topicTitle.truncLabel(), it.accuracyPercent, inverted = false) }
            )
        }
    }

    // ── Gauge grid helpers ────────────────────────────────────────────────

    private data class GaugeItem(val label: String, val progress: Int, val inverted: Boolean)

    private fun addGaugeGrid(container: LinearLayout, items: List<GaugeItem>) {
        val dp = resources.displayMetrics.density
        items.chunked(2).forEachIndexed { rowIdx, pair ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).also { if (rowIdx > 0) it.topMargin = (6f * dp).roundToInt() }
            }
            pair.forEachIndexed { idx, item ->
                val lp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                if (idx > 0) lp.marginStart = (8f * dp).roundToInt()
                row.addView(com.drivest.navigation.analytics.GaugeView(this).apply {
                    progress = item.progress
                    label    = item.label
                    inverted = item.inverted
                    layoutParams = lp
                })
            }
            if (pair.size == 1) {
                row.addView(View(this).apply {
                    layoutParams = LinearLayout.LayoutParams(0, 1, 1f)
                })
            }
            container.addView(row)
        }
    }

    private fun String.truncLabel(): String =
        if (length > 11) take(10) + "…" else this

    private fun renderActionPlan(data: AnalyticsDashboardData) {
        actionPlanContainer.removeAllViews()
        val actions = data.actionPlan.ifEmpty { listOf(getString(R.string.analytics_action_plan_empty)) }
        actions.forEachIndexed { index, action ->
            val text = TextView(this).apply {
                text = getString(R.string.analytics_action_plan_item, index + 1, action)
                setTextColor(ContextCompat.getColor(context, R.color.settings_text_primary))
                textSize = 13f
                setLineSpacing(0f, 1.1f)
                if (index > 0) {
                    val top = (6f * resources.displayMetrics.density).roundToInt()
                    setPadding(0, top, 0, 0)
                }
            }
            actionPlanContainer.addView(text)
        }
    }

    private fun renderRecentSessions() {
        val data = dashboardData ?: return
        recentSessionsContainer.removeAllViews()

        val source = when (sessionWindowPreset) {
            SessionWindowPreset.FIVE -> data.sessions.takeLast(5)
            SessionWindowPreset.TEN -> data.sessions.takeLast(10)
            SessionWindowPreset.ALL -> data.sessions
        }
        val visibleSessions = source.asReversed()

        recentSessionsEmptyView.isVisible = visibleSessions.isEmpty()
        if (visibleSessions.isEmpty()) {
            recentSessionsEmptyView.text = getString(R.string.analytics_recent_sessions_empty)
            return
        }

        visibleSessions.forEach { session ->
            recentSessionsContainer.addView(buildSessionRow(session, data.preferredUnits))
        }
    }

    private fun buildSessionRow(session: SessionExportRecord, units: PreferredUnitsSetting): View {
        val view = LayoutInflater.from(this)
            .inflate(R.layout.item_analytics_session_row, recentSessionsContainer, false)

        view.findViewById<TextView>(R.id.analyticsSessionDate).text =
            DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, Locale.getDefault())
                .format(Date(session.exportedAtMs))

        val badgeView = view.findViewById<TextView>(R.id.analyticsSessionBadge)
        badgeView.text = getString(
            if (session.completionFlag) R.string.analytics_session_status_completed
            else R.string.analytics_session_status_partial
        )

        val durationMinutes = (session.durationSeconds / 60f).roundToInt()
        val statsLine = getString(
            R.string.analytics_session_stats_line,
            formatDistance(session.distanceMetersDriven.toLong(), units, short = true),
            durationMinutes.coerceAtLeast(0),
            session.complexityScore,
            session.offRouteCount
        )
        view.findViewById<TextView>(R.id.analyticsSessionStatsLine).text = statsLine
        view.findViewById<ProgressBar>(R.id.analyticsSessionConfidenceBar).progress = session.confidenceScore
        view.findViewById<ProgressBar>(R.id.analyticsSessionStressBar).progress = session.stressIndex
        view.findViewById<TextView>(R.id.analyticsSessionBarsLegend).text = getString(
            R.string.analytics_session_bars_legend,
            session.confidenceScore,
            session.stressIndex
        )
        return view
    }


    private fun buildMutedText(textValue: String): TextView {
        return TextView(this).apply {
            text = textValue
            setTextColor(ContextCompat.getColor(context, R.color.settings_text_secondary))
            textSize = 13f
        }
    }

    private fun updateWindowButtons() {
        styleWindowButton(window5Button, sessionWindowPreset == SessionWindowPreset.FIVE)
        styleWindowButton(window10Button, sessionWindowPreset == SessionWindowPreset.TEN)
        styleWindowButton(windowAllButton, sessionWindowPreset == SessionWindowPreset.ALL)
    }

    private fun styleWindowButton(button: MaterialButton, selected: Boolean) {
        val accent = ContextCompat.getColor(this, R.color.settings_accent)
        val textPrimary = ContextCompat.getColor(this, R.color.settings_text_primary)
        val background = if (selected) accent else Color.WHITE
        button.backgroundTintList = ColorStateList.valueOf(background)
        button.strokeColor = ColorStateList.valueOf(
            if (selected) accent else ContextCompat.getColor(this, R.color.app_card_stroke)
        )
        button.setTextColor(if (selected) Color.WHITE else textPrimary)
    }

    private fun formatDistance(
        meters: Long,
        units: PreferredUnitsSetting,
        short: Boolean
    ): String {
        if (meters <= 0L) {
            return if (units == PreferredUnitsSetting.METRIC_KMH) {
                getString(R.string.analytics_distance_km_zero)
            } else {
                getString(R.string.analytics_distance_mi_zero)
            }
        }
        val value = when (units) {
            PreferredUnitsSetting.UK_MPH -> meters / 1609.344
            PreferredUnitsSetting.METRIC_KMH -> meters / 1000.0
        }
        val number = NumberFormat.getNumberInstance(Locale.getDefault()).apply {
            maximumFractionDigits = if (short) 1 else 2
            minimumFractionDigits = 0
        }.format(value)
        return when (units) {
            PreferredUnitsSetting.UK_MPH -> getString(R.string.analytics_distance_mi_value, number)
            PreferredUnitsSetting.METRIC_KMH -> getString(R.string.analytics_distance_km_value, number)
        }
    }

    private enum class SessionWindowPreset {
        FIVE,
        TEN,
        ALL
    }
}

