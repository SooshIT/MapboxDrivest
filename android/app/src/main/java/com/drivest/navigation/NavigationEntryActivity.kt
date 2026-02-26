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
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class NavigationEntryActivity : AppCompatActivity() {

    private val settingsRepository by lazy { SettingsRepository(applicationContext) }
    private lateinit var rootView: androidx.constraintlayout.widget.ConstraintLayout
    private lateinit var logoView: ImageView
    private lateinit var appearanceModeButton: ImageButton
    private lateinit var titleView: TextView
    private lateinit var descriptionView: TextView
    private lateinit var openNavigationMapButton: MaterialButton

    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val granted = grants[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) {
            openDestinationSearch()
        } else {
            Toast.makeText(
                this,
                getString(R.string.location_permission_required_for_routing),
                Toast.LENGTH_LONG
            ).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_navigation_entry)
        rootView = findViewById(R.id.navigationEntryRoot)
        logoView = findViewById(R.id.navigationLogo)
        appearanceModeButton = findViewById(R.id.navigationAppearanceModeButton)
        titleView = findViewById(R.id.navigationTitle)
        descriptionView = findViewById(R.id.navigationDescription)
        openNavigationMapButton = findViewById(R.id.openNavigationMapButton)

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

        openNavigationMapButton.setOnClickListener {
            if (LocationPrePromptDialog.hasLocationPermission(this)) {
                openDestinationSearch()
                return@setOnClickListener
            }
            LocationPrePromptDialog.show(
                activity = this,
                onAllow = { requestLocationPermission() }
            )
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
            Toast.makeText(this@NavigationEntryActivity, getString(messageRes), Toast.LENGTH_SHORT).show()
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

    private fun requestLocationPermission() {
        locationPermissionLauncher.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        )
    }

    private fun openDestinationSearch() {
        startActivity(Intent(this, DestinationSearchActivity::class.java))
    }
}
