package com.drivest.navigation.theory.screens

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.setPadding
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.drivest.navigation.R
import com.drivest.navigation.legal.ConsentRepository
import com.drivest.navigation.settings.SettingsRepository
import com.drivest.navigation.telemetry.TelemetryEvent
import com.drivest.navigation.telemetry.TelemetryRepository
import com.drivest.navigation.theory.TheoryFeatureFlags
import com.drivest.navigation.theory.content.TheoryPackLoader
import com.drivest.navigation.theory.content.TheoryReadiness
import com.drivest.navigation.theory.content.TheoryReadinessCalculator
import com.drivest.navigation.theory.content.TheoryReadinessLabel
import com.drivest.navigation.theory.models.TheoryPack
import com.drivest.navigation.theory.navigation.TheoryNavigation
import com.drivest.navigation.theory.services.MapRouteTagsToTheoryTopics
import com.drivest.navigation.theory.storage.TheoryProgress
import com.drivest.navigation.theory.storage.TheoryProgressStore
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class TheoryHomeActivity : AppCompatActivity() {

    private val packLoader by lazy { TheoryPackLoader(applicationContext) }
    private val progressStore by lazy { TheoryProgressStore(applicationContext) }
    private val settingsRepository by lazy { SettingsRepository(applicationContext) }
    private val consentRepository by lazy { ConsentRepository(applicationContext) }
    private val telemetryRepository by lazy {
        TelemetryRepository(
            settingsRepository = settingsRepository,
            consentRepository = consentRepository
        )
    }

    private lateinit var pack: TheoryPack
    private lateinit var progressValueView: TextView
    private lateinit var readinessValueView: TextView
    private lateinit var weakestTopicsValueView: TextView
    private lateinit var recommendationsHeadingView: TextView
    private lateinit var recommendationsContainer: LinearLayout
    private lateinit var continueButton: MaterialButton
    private lateinit var quickQuizButton: MaterialButton
    private lateinit var mockButton: MaterialButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!TheoryFeatureFlags.isTheoryModuleEnabled()) {
            finish()
            return
        }

        setContentView(R.layout.activity_theory_home)
        pack = packLoader.load()
        if (pack.topics.isEmpty()) {
            Toast.makeText(this, R.string.theory_content_unavailable, Toast.LENGTH_SHORT).show()
        }

        progressValueView = findViewById(R.id.theoryProgressValue)
        readinessValueView = findViewById(R.id.theoryReadinessValue)
        weakestTopicsValueView = findViewById(R.id.theoryWeakestTopicsValue)
        recommendationsHeadingView = findViewById(R.id.theoryRouteRecommendationsHeading)
        recommendationsContainer = findViewById(R.id.theoryRouteRecommendationsContainer)
        continueButton = findViewById(R.id.theoryContinueButton)
        quickQuizButton = findViewById(R.id.theoryQuickQuizButton)
        mockButton = findViewById(R.id.theoryMockButton)

        bindStaticActions()
        observeProgress()
        logTheoryOpen()
    }

    private fun bindStaticActions() {
        continueButton.setOnClickListener {
            lifecycleScope.launch {
                val progress = progressStore.progress.first()
                openContinueLesson(progress)
            }
        }
        quickQuizButton.setOnClickListener {
            lifecycleScope.launch {
                val progress = progressStore.progress.first()
                val topicId = recommendedTopicId(progress) ?: pack.topics.firstOrNull()?.id
                if (topicId == null) {
                    Toast.makeText(
                        this@TheoryHomeActivity,
                        R.string.theory_content_unavailable,
                        Toast.LENGTH_SHORT
                    ).show()
                    return@launch
                }
                startActivity(
                    Intent(this@TheoryHomeActivity, TheoryQuizActivity::class.java).apply {
                        putExtra(TheoryNavigation.EXTRA_QUIZ_TOPIC_ID, topicId)
                        putExtra(TheoryNavigation.EXTRA_QUIZ_COUNT, 10)
                        putExtra(TheoryNavigation.EXTRA_QUIZ_MODE, TheoryNavigation.QUIZ_MODE_TOPIC)
                    }
                )
            }
        }
        mockButton.setOnClickListener {
            startActivity(Intent(this, TheoryMockTestActivity::class.java))
        }
        findViewById<MaterialButton>(R.id.theoryTopicsButton).setOnClickListener {
            startActivity(Intent(this, TheoryTopicListActivity::class.java))
        }
        findViewById<MaterialButton>(R.id.theoryBookmarksButton).setOnClickListener {
            startActivity(Intent(this, TheoryBookmarksActivity::class.java))
        }
        findViewById<MaterialButton>(R.id.theoryWrongAnswersButton).setOnClickListener {
            startActivity(Intent(this, TheoryWrongAnswersActivity::class.java))
        }
        findViewById<MaterialButton>(R.id.theorySettingsButton).setOnClickListener {
            startActivity(Intent(this, TheorySettingsActivity::class.java))
        }
    }

    private fun observeProgress() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                progressStore.progress.collectLatest { progress ->
                    renderProgress(progress)
                }
            }
        }
    }

    private fun renderProgress(progress: TheoryProgress) {
        val readiness = TheoryReadinessCalculator.calculate(progress, pack.topics.size)
        progressValueView.text = getString(
            R.string.theory_progress_value,
            readiness.masteredTopicsPercent
        )
        readinessValueView.text = getString(
            R.string.theory_readiness_value,
            readinessLabel(readiness)
        )

        val weakestTopics = progress.topicStats.entries
            .sortedBy { it.value.masteryPercent }
            .take(3)
            .mapNotNull { entry -> pack.topics.firstOrNull { it.id == entry.key }?.title }
        weakestTopicsValueView.text = if (weakestTopics.isEmpty()) {
            getString(R.string.theory_weakest_topics_default)
        } else {
            weakestTopics.joinToString(separator = "\n") { "- $it" }
        }

        renderRouteRecommendations(progress)
    }

    private fun renderRouteRecommendations(progress: TheoryProgress) {
        recommendationsContainer.removeAllViews()
        val snapshot = progress.lastRouteTagSnapshot
        if (snapshot == null) {
            recommendationsHeadingView.visibility = View.GONE
            recommendationsContainer.visibility = View.GONE
            return
        }
        val recommendedTopicIds = MapRouteTagsToTheoryTopics.mapTags(snapshot.tags)
        if (recommendedTopicIds.isEmpty()) {
            recommendationsHeadingView.visibility = View.GONE
            recommendationsContainer.visibility = View.GONE
            return
        }
        recommendationsHeadingView.visibility = View.VISIBLE
        recommendationsContainer.visibility = View.VISIBLE
        recommendedTopicIds.forEach { topicId ->
            val topic = pack.topics.firstOrNull { it.id == topicId } ?: return@forEach
            val button = MaterialButton(this).apply {
                text = getString(R.string.theory_route_recommendation_card, topic.title)
                isAllCaps = false
                setPadding(18)
                setOnClickListener {
                    logRouteCardClick(snapshot.tags.firstOrNull().orEmpty(), topic.id)
                    startActivity(
                        Intent(this@TheoryHomeActivity, TheoryTopicDetailActivity::class.java).apply {
                            putExtra(TheoryNavigation.EXTRA_TOPIC_ID, topic.id)
                            putExtra(TheoryNavigation.EXTRA_ENTRY_SOURCE, "route_recommendation")
                        }
                    )
                }
            }
            recommendationsContainer.addView(button)
        }
    }

    private fun openContinueLesson(progress: TheoryProgress) {
        val nextLesson = pack.lessons.firstOrNull { lesson ->
            !progress.completedLessons.contains(lesson.id)
        } ?: pack.lessons.firstOrNull()
        if (nextLesson == null) {
            Toast.makeText(this, R.string.theory_content_unavailable, Toast.LENGTH_SHORT).show()
            return
        }
        startActivity(
            Intent(this, TheoryLessonActivity::class.java).apply {
                putExtra(TheoryNavigation.EXTRA_LESSON_ID, nextLesson.id)
                putExtra(TheoryNavigation.EXTRA_ENTRY_SOURCE, "continue_learning")
            }
        )
    }

    private fun recommendedTopicId(progress: TheoryProgress): String? {
        val snapshot = progress.lastRouteTagSnapshot ?: return null
        return MapRouteTagsToTheoryTopics.mapTags(snapshot.tags).firstOrNull()
    }

    private fun readinessLabel(readiness: TheoryReadiness): String {
        return when (readiness.label) {
            TheoryReadinessLabel.BUILDING ->
                getString(R.string.theory_readiness_building)
            TheoryReadinessLabel.ALMOST_READY ->
                getString(R.string.theory_readiness_almost_ready)
            TheoryReadinessLabel.READY ->
                getString(R.string.theory_readiness_ready)
        }
    }

    private fun logTheoryOpen() {
        lifecycleScope.launch {
            telemetryRepository.sendEvent(
                TelemetryEvent.App(
                    eventType = "theory_open",
                    payload = mapOf("surface" to "theory_home")
                )
            )
        }
    }

    private fun logRouteCardClick(tag: String, topicId: String) {
        lifecycleScope.launch {
            telemetryRepository.sendEvent(
                TelemetryEvent.App(
                    eventType = "theory_route_card_click",
                    payload = mapOf(
                        "tag" to tag,
                        "topicId" to topicId,
                        "sourceSurface" to "theory_home"
                    )
                )
            )
        }
    }
}
