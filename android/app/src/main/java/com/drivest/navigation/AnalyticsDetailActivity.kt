package com.drivest.navigation

import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.drivest.navigation.analytics.AnalyticsDashboardData
import com.drivest.navigation.analytics.DrivestAnalyticsRepository
import com.drivest.navigation.analytics.GaugeView
import com.drivest.navigation.analytics.HazardInsight
import com.drivest.navigation.analytics.TheoryTopicInsight
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/**
 * Full-detail drill-down for either the Hazard or Theory analytics section.
 * Pass [EXTRA_MODE] = [MODE_HAZARDS] or [MODE_THEORY] from the calling activity.
 */
class AnalyticsDetailActivity : AppCompatActivity() {

    private val analyticsRepository by lazy { DrivestAnalyticsRepository(applicationContext) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_analytics_detail)

        val mode = intent.getStringExtra(EXTRA_MODE) ?: finish().let { return }

        val titleView   = findViewById<TextView>(R.id.analyticsDetailTitle)
        val spinner     = findViewById<ProgressBar>(R.id.analyticsDetailSpinner)
        val container   = findViewById<LinearLayout>(R.id.analyticsDetailContainer)

        titleView.text = when (mode) {
            MODE_HAZARDS -> getString(R.string.analytics_detail_hazards_title)
            else         -> getString(R.string.analytics_detail_theory_title)
        }

        findViewById<ImageButton>(R.id.analyticsDetailBackButton).setOnClickListener { finish() }

        lifecycleScope.launch {
            spinner.isVisible = true
            val result = withContext(Dispatchers.IO) {
                runCatching { analyticsRepository.loadDashboard() }
            }
            spinner.isVisible = false
            result.onSuccess { data ->
                container.isVisible = true
                when (mode) {
                    MODE_HAZARDS -> renderHazards(container, data)
                    else         -> renderTheory(container, data)
                }
            }.onFailure {
                val tv = TextView(this@AnalyticsDetailActivity).apply {
                    text = it.message ?: getString(R.string.analytics_load_failed)
                    textSize = 13f
                    setTextColor(android.graphics.Color.parseColor("#686271"))
                }
                container.isVisible = true
                container.addView(tv)
            }
        }
    }

    // ── Hazards ──────────────────────────────────────────────────────────────

    private fun renderHazards(container: LinearLayout, data: AnalyticsDashboardData) {
        val hazards = data.hazardInsights
            .filter { it.count > 0 }
            .sortedByDescending(HazardInsight::count)

        if (hazards.isEmpty()) {
            container.addView(buildMutedText(getString(R.string.analytics_hazard_empty)))
            return
        }

        addSectionLabel(container, getString(R.string.analytics_focus_areas_title))
        addGaugeGrid(
            container = container,
            items = hazards.map { GaugeItem(it.label, it.relativePercent, inverted = true) }
        )

        // Ranked count list below the gauges
        addSectionLabel(container, "Encountered counts", marginTop = 16)
        hazards.forEach { hazard ->
            val row = buildCountRow(hazard.label, "${hazard.count}×  (${hazard.relativePercent}% relative)")
            container.addView(row)
        }
    }

    // ── Theory ───────────────────────────────────────────────────────────────

    private fun renderTheory(container: LinearLayout, data: AnalyticsDashboardData) {
        if (data.totalTheoryAttempts == 0) {
            container.addView(buildMutedText(getString(R.string.analytics_theory_empty)))
            return
        }

        val allTopics = (data.weakestTheoryTopics + data.strongestTheoryTopics)
            .distinctBy { it.topicId }
            .sortedBy { it.accuracyPercent }

        // Summary chips row
        addSectionLabel(container, getString(R.string.analytics_theory_title))
        container.addView(buildMutedText(
            getString(
                R.string.analytics_theory_summary_value,
                data.totalTheoryAttempts,
                data.totalTheoryAccuracyPercent,
                data.masteredTopicsCount,
                data.totalTheoryTopics
            )
        ).also {
            (it.layoutParams as LinearLayout.LayoutParams).topMargin =
                (6f * resources.displayMetrics.density).roundToInt()
        })

        // All topics as gauges (weakest first)
        addSectionLabel(container, "All topics — weakest first", marginTop = 14)
        addGaugeGrid(
            container = container,
            items = allTopics.map { GaugeItem(it.topicTitle.truncLabel(), it.accuracyPercent, inverted = false) }
        )

        // Full accuracy list
        addSectionLabel(container, "Full topic breakdown", marginTop = 16)
        allTopics.forEach { topic ->
            val row = buildCountRow(
                topic.topicTitle,
                "${topic.accuracyPercent}% accuracy  |  ${topic.attempts} answers  |  mastery ${topic.masteryPercent}%"
            )
            container.addView(row)
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

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
                val gauge = GaugeView(this).apply {
                    progress = item.progress
                    label    = item.label
                    inverted = item.inverted
                    layoutParams = lp
                }
                row.addView(gauge)
            }
            if (pair.size == 1) {
                row.addView(View(this).apply {
                    layoutParams = LinearLayout.LayoutParams(0, 1, 1f)
                })
            }
            container.addView(row)
        }
    }

    private fun addSectionLabel(container: LinearLayout, text: String, marginTop: Int = 0) {
        val dp = resources.displayMetrics.density
        val tv = TextView(this).apply {
            this.text = text
            textSize = 13f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setTextColor(android.graphics.Color.parseColor("#1F1B26"))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.topMargin = (marginTop * dp).roundToInt() }
        }
        container.addView(tv)
    }

    private fun buildCountRow(label: String, detail: String): View {
        val dp = resources.displayMetrics.density
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.topMargin = (8f * dp).roundToInt() }
            background = getDrawable(R.drawable.bg_app_card)
            setPadding(
                (12f * dp).roundToInt(), (10f * dp).roundToInt(),
                (12f * dp).roundToInt(), (10f * dp).roundToInt()
            )
        }
        val labelView = TextView(this).apply {
            text = label
            textSize = 13f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setTextColor(android.graphics.Color.parseColor("#1F1B26"))
        }
        val detailView = TextView(this).apply {
            text = detail
            textSize = 12f
            setTextColor(android.graphics.Color.parseColor("#686271"))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.topMargin = (3f * dp).roundToInt() }
        }
        row.addView(labelView)
        row.addView(detailView)
        return row
    }

    private fun buildMutedText(text: String): TextView =
        TextView(this).apply {
            this.text = text
            textSize = 13f
            setTextColor(android.graphics.Color.parseColor("#686271"))
        }

    private fun String.truncLabel(): String =
        if (length > 11) take(10) + "…" else this

    companion object {
        const val EXTRA_MODE    = "analytics_detail_mode"
        const val MODE_HAZARDS  = "hazards"
        const val MODE_THEORY   = "theory"
    }
}
