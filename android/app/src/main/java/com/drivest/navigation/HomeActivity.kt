package com.drivest.navigation

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.drivest.navigation.profile.DriverMode
import com.drivest.navigation.profile.DriverProfileRepository
import com.drivest.navigation.profile.ModeSuggestionApplier
import com.drivest.navigation.practice.AssetsPracticeRouteStore
import com.drivest.navigation.practice.DataSourcePracticeRouteStore
import com.drivest.navigation.settings.SettingsRepository
import com.drivest.navigation.subscription.FeatureAccessManager
import com.drivest.navigation.subscription.SubscriptionRepository
import com.drivest.navigation.theory.TheoryFeatureFlags
import com.drivest.navigation.theory.content.TheoryReadinessCalculator
import com.drivest.navigation.theory.content.TheoryReadinessLabel
import com.drivest.navigation.theory.storage.TheoryProgressStore
import com.drivest.navigation.theory.screens.TheoryHomeActivity
import com.drivest.navigation.training.TrainingPlanEngine
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class HomeActivity : AppCompatActivity() {

    private val settingsRepository by lazy { SettingsRepository(applicationContext) }
    private val driverProfileRepository by lazy { DriverProfileRepository(applicationContext) }
    private val subscriptionRepository by lazy { SubscriptionRepository(applicationContext) }
    private val modeSuggestionApplier by lazy {
        ModeSuggestionApplier(
            driverProfileRepository = driverProfileRepository,
            settingsRepository = settingsRepository
        )
    }
    private val featureAccessManager by lazy {
        FeatureAccessManager(
            subscriptionRepository = subscriptionRepository,
            settingsRepository = settingsRepository,
            driverProfileRepository = driverProfileRepository,
            debugFreePracticeBypassEnabled = BuildConfig.DEBUG
        )
    }
    private val practiceRouteStore by lazy {
        DataSourcePracticeRouteStore(
            context = applicationContext,
            settingsRepository = settingsRepository,
            assetsPracticeRouteStore = AssetsPracticeRouteStore(this)
        )
    }
    private val trainingPlanEngine = TrainingPlanEngine()
    private val theoryProgressStore by lazy { TheoryProgressStore(applicationContext) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)

        val practiceModeButton = findViewById<MaterialButton>(R.id.practiceModeButton)
        val navigationModeButton = findViewById<MaterialButton>(R.id.navigationModeButton)
        val theoryModeButton = findViewById<MaterialButton>(R.id.theoryModeButton)
        val theoryTileContainer = findViewById<View>(R.id.homeTheoryTileContainer)
        val theoryProgressBadgeView = findViewById<TextView>(R.id.homeTheoryProgressBadge)
        val theoryReadinessView = findViewById<TextView>(R.id.homeTheoryReadinessValue)
        val modeRow = findViewById<LinearLayout>(R.id.homeModeRow)

        practiceModeButton.setOnClickListener {
            lifecycleScope.launch {
                settingsRepository.setLastMode(AppFlow.MODE_PRACTICE)
            }
            startActivity(Intent(this, PracticeEntryActivity::class.java))
        }

        navigationModeButton.setOnClickListener {
            lifecycleScope.launch {
                settingsRepository.setLastMode(AppFlow.MODE_NAV)
            }
            startActivity(Intent(this, NavigationEntryActivity::class.java))
        }

        val theoryEnabled = TheoryFeatureFlags.isTheoryModuleEnabled()
        if (theoryEnabled) {
            theoryTileContainer.visibility = View.VISIBLE
            modeRow.weightSum = 3f
            theoryModeButton.setOnClickListener {
                startActivity(Intent(this, TheoryHomeActivity::class.java))
            }
            lifecycleScope.launch {
                repeatOnLifecycle(Lifecycle.State.STARTED) {
                    theoryProgressStore.progress.collectLatest { progress ->
                        val readiness = TheoryReadinessCalculator.calculate(
                            progress = progress,
                            totalTopics = THEORY_TOPIC_COUNT_FALLBACK
                        )
                        theoryProgressBadgeView.text = getString(
                            R.string.home_theory_progress_value,
                            readiness.masteredTopicsPercent
                        )
                        val readinessLabel = when (readiness.label) {
                            TheoryReadinessLabel.BUILDING -> getString(R.string.theory_readiness_building)
                            TheoryReadinessLabel.ALMOST_READY -> getString(R.string.theory_readiness_almost_ready)
                            TheoryReadinessLabel.READY -> getString(R.string.theory_readiness_ready)
                        }
                        theoryReadinessView.text = getString(
                            R.string.home_theory_readiness_value,
                            readinessLabel
                        )
                    }
                }
            }
        } else {
            theoryTileContainer.visibility = View.GONE
            theoryReadinessView.visibility = View.GONE
            modeRow.weightSum = 2f
        }

        findViewById<ImageButton>(R.id.homeSettingsButton).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        findViewById<ImageView>(R.id.homeLogo).setOnLongClickListener {
            startActivity(Intent(this, DebugSessionActivity::class.java))
            true
        }

        val confidenceScoreView = findViewById<TextView>(R.id.homeConfidenceValue)
        val driverModeView = findViewById<TextView>(R.id.homeDriverModeValue)
        val recommendedLabelView = findViewById<TextView>(R.id.homeRecommendedLabel)
        val recommendedValueView = findViewById<TextView>(R.id.homeRecommendedValue)
        lifecycleScope.launch {
            modeSuggestionApplier.applySuggestionsIfNeeded(driverProfileRepository.driverMode.first())
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                driverProfileRepository.profile.collectLatest { profile ->
                    driverModeView.text = getString(
                        R.string.home_driver_mode,
                        formatDriverModeLabel(profile.driverMode)
                    )
                    practiceModeButton.text = if (profile.driverMode == DriverMode.LEARNER) {
                        getString(R.string.home_practice_button_recommended)
                    } else {
                        getString(R.string.home_practice_button)
                    }
                    confidenceScoreView.text = getString(
                        R.string.home_confidence_score,
                        profile.confidenceScore
                    )
                }
            }
        }

        lifecycleScope.launch {
            val profile = driverProfileRepository.profile.first()
            val centreId = settingsRepository.lastSelectedCentreId.first()
            val hasTrainingPlanAccess = featureAccessManager.hasTrainingPlanAccess().first()
            if (!hasTrainingPlanAccess) {
                recommendedLabelView.text = getString(R.string.home_recommended_today_label)
                recommendedLabelView.visibility = View.VISIBLE
                recommendedValueView.text = getString(R.string.home_recommended_locked_value)
                recommendedValueView.visibility = View.VISIBLE
                recommendedValueView.setOnClickListener {
                    openPaywall(getString(R.string.paywall_feature_training_plan))
                }
                return@launch
            }

            val routes = runCatching { practiceRouteStore.loadRoutesForCentre(centreId) }
                .getOrDefault(emptyList())
            val plan = trainingPlanEngine.generatePlan(
                centreId = centreId,
                routes = routes,
                profile = profile
            )
            val recommendation = trainingPlanEngine.recommendedToday(plan)
            if (recommendation != null) {
                recommendedLabelView.text = getString(R.string.home_recommended_today_label)
                recommendedLabelView.visibility = android.view.View.VISIBLE
                recommendedValueView.text = getString(
                    R.string.home_recommended_today_value,
                    recommendation.routeName
                )
                recommendedValueView.visibility = android.view.View.VISIBLE
                recommendedValueView.setOnClickListener {
                    startActivity(
                        Intent(this@HomeActivity, MainActivity::class.java)
                            .putExtra(AppFlow.EXTRA_APP_MODE, AppFlow.MODE_PRACTICE)
                            .putExtra(AppFlow.EXTRA_CENTRE_ID, centreId)
                            .putExtra(AppFlow.EXTRA_ROUTE_ID, recommendation.routeId)
                    )
                }
            } else {
                recommendedLabelView.visibility = View.GONE
                recommendedValueView.visibility = View.GONE
            }
        }
    }

    private fun openPaywall(featureLabel: String) {
        startActivity(
            Intent(this, PaywallActivity::class.java).putExtra(
                PaywallActivity.EXTRA_FEATURE_LABEL,
                featureLabel
            )
        )
    }

    private fun formatDriverModeLabel(mode: DriverMode): String {
        return when (mode) {
            DriverMode.LEARNER -> getString(R.string.driver_mode_learner)
            DriverMode.NEW_DRIVER -> getString(R.string.driver_mode_new_driver)
            DriverMode.STANDARD -> getString(R.string.driver_mode_standard)
        }
    }

    private companion object {
        const val THEORY_TOPIC_COUNT_FALLBACK = 12
    }
}
