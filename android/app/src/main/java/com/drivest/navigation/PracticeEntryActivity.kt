package com.drivest.navigation

import android.Manifest
import android.content.Intent
import android.os.Bundle
import android.widget.ImageView
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.content.res.AppCompatResources
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.button.MaterialButton
import com.drivest.navigation.settings.AppearanceModeManager
import com.drivest.navigation.settings.AppearanceModeSetting
import com.drivest.navigation.settings.SettingsRepository
import com.drivest.navigation.subscription.FeatureAccessManager
import com.drivest.navigation.subscription.requiresPracticeCentreSelection
import com.drivest.navigation.subscription.SubscriptionRepository
import com.drivest.navigation.subscription.SubscriptionTier
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class PracticeEntryActivity : AppCompatActivity() {

    private val settingsRepository by lazy { SettingsRepository(applicationContext) }
    private val subscriptionRepository by lazy { SubscriptionRepository(applicationContext) }
    private val featureAccessManager by lazy {
        FeatureAccessManager(
            subscriptionRepository = subscriptionRepository,
            settingsRepository = settingsRepository,
            debugFreePracticeBypassEnabled = BuildConfig.DEBUG
        )
    }

    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val granted = grants[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) {
            openPracticeList()
        } else {
            Toast.makeText(
                this,
                getString(R.string.location_permission_required_for_routing),
                Toast.LENGTH_LONG
            ).show()
        }
    }
    private val practiceCentreSelectionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            openPracticeFlow()
        }
    }
    private lateinit var rootView: androidx.constraintlayout.widget.ConstraintLayout
    private lateinit var logoView: ImageView
    private lateinit var appearanceModeButton: ImageButton
    private lateinit var titleView: TextView
    private lateinit var descriptionView: TextView
    private lateinit var openPracticeMapButton: MaterialButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_practice_entry)
        rootView = findViewById(R.id.practiceEntryRoot)
        logoView = findViewById(R.id.practiceLogo)
        appearanceModeButton = findViewById(R.id.practiceAppearanceModeButton)
        titleView = findViewById(R.id.practiceTitle)
        descriptionView = findViewById(R.id.practiceDescription)
        openPracticeMapButton = findViewById(R.id.openPracticeMapButton)

        appearanceModeButton.setOnClickListener {
            cycleAppearanceMode()
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                settingsRepository.appearanceMode.collectLatest { mode ->
                    renderAppearanceMode(mode)
                }
            }
        }

        openPracticeMapButton.setOnClickListener {
            lifecycleScope.launch {
                if (BuildConfig.DEBUG) {
                    openPracticeFlow()
                    return@launch
                }
                val tier = featureAccessManager.getTier().first()
                val practiceCentreId = settingsRepository.practiceCentreId.first()
                if (requiresPracticeCentreSelection(tier, practiceCentreId)) {
                    launchPracticeCentreSelection()
                    return@launch
                }
                val hasPracticeAccess = featureAccessManager.hasPracticeAccess(null).first()
                if (tier == SubscriptionTier.FREE && !hasPracticeAccess) {
                    openPaywall(getString(R.string.paywall_feature_practice_access))
                    return@launch
                }
                openPracticeFlow()
            }
        }
    }

    private fun cycleAppearanceMode() {
        lifecycleScope.launch {
            val current = settingsRepository.appearanceMode.first()
            val next = AppearanceModeManager.next(current)
            settingsRepository.setAppearanceMode(next)
            val messageRes = when (next) {
                AppearanceModeSetting.AUTO -> R.string.appearance_mode_changed_auto
                AppearanceModeSetting.DAY -> R.string.appearance_mode_changed_day
                AppearanceModeSetting.NIGHT -> R.string.appearance_mode_changed_night
            }
            Toast.makeText(this@PracticeEntryActivity, getString(messageRes), Toast.LENGTH_SHORT).show()
        }
    }

    private fun renderAppearanceMode(mode: AppearanceModeSetting) {
        val iconRes = when (mode) {
            AppearanceModeSetting.AUTO -> R.drawable.ic_appearance_auto
            AppearanceModeSetting.DAY -> R.drawable.ic_appearance_day
            AppearanceModeSetting.NIGHT -> R.drawable.ic_appearance_night
        }
        val tintRes = when (mode) {
            AppearanceModeSetting.AUTO -> R.color.map_control_appearance_icon_auto
            AppearanceModeSetting.DAY -> R.color.map_control_appearance_icon_day
            AppearanceModeSetting.NIGHT -> R.color.map_control_appearance_icon_night
        }
        val contentDescriptionRes = when (mode) {
            AppearanceModeSetting.AUTO -> R.string.appearance_mode_auto
            AppearanceModeSetting.DAY -> R.string.appearance_mode_day
            AppearanceModeSetting.NIGHT -> R.string.appearance_mode_night
        }
        appearanceModeButton.setImageResource(iconRes)
        appearanceModeButton.imageTintList = ContextCompat.getColorStateList(this, tintRes)
        appearanceModeButton.contentDescription = getString(contentDescriptionRes)
        applyEntryAppearancePreview(mode)
    }

    private fun applyEntryAppearancePreview(mode: AppearanceModeSetting) {
        val useNight = AppearanceModeManager.isNightActive(this, mode)
        val bgColor = ContextCompat.getColor(
            this,
            if (useNight) R.color.entry_screen_bg_night else R.color.entry_screen_bg_day
        )
        val titleColor = ContextCompat.getColor(
            this,
            if (useNight) R.color.entry_screen_text_primary_night else R.color.entry_screen_text_primary_day
        )
        val descriptionColor = ContextCompat.getColor(
            this,
            if (useNight) R.color.entry_screen_text_secondary_night else R.color.entry_screen_text_secondary_day
        )
        rootView.setBackgroundColor(bgColor)
        titleView.setTextColor(titleColor)
        descriptionView.setTextColor(descriptionColor)

        val logoPadding = if (useNight) (8 * resources.displayMetrics.density).toInt() else 0
        logoView.setPadding(logoPadding, logoPadding / 2, logoPadding, logoPadding / 2)
        logoView.background = if (useNight) {
            AppCompatResources.getDrawable(this, R.drawable.bg_settings_logo_chip)
        } else {
            null
        }

        val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
        windowInsetsController.isAppearanceLightStatusBars = !useNight
        windowInsetsController.isAppearanceLightNavigationBars = !useNight
        window.statusBarColor = bgColor
        window.navigationBarColor = bgColor
    }

    private fun openPracticeFlow() {
        if (LocationPrePromptDialog.hasLocationPermission(this)) {
            openPracticeList()
            return
        }
        LocationPrePromptDialog.show(
            activity = this,
            onAllow = { requestLocationPermission() }
        )
    }

    private fun openPaywall(featureLabel: String) {
        startActivity(
            Intent(this, PaywallActivity::class.java).putExtra(
                PaywallActivity.EXTRA_FEATURE_LABEL,
                featureLabel
            )
        )
    }

    private fun requestLocationPermission() {
        locationPermissionLauncher.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        )
    }

    private fun launchPracticeCentreSelection() {
        val selectionIntent = Intent(this, CentrePickerActivity::class.java).apply {
            putExtra(CentrePickerActivity.EXTRA_SELECT_PRACTICE_CENTRE_ONLY, true)
        }
        practiceCentreSelectionLauncher.launch(selectionIntent)
    }

    private fun openPracticeList() {
        startActivity(Intent(this, CentrePickerActivity::class.java))
    }
}
