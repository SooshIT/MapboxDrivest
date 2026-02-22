package com.drivest.navigation

import android.Manifest
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.drivest.navigation.settings.SettingsRepository
import com.drivest.navigation.subscription.FeatureAccessManager
import com.drivest.navigation.subscription.requiresPracticeCentreSelection
import com.drivest.navigation.subscription.SubscriptionRepository
import com.drivest.navigation.subscription.SubscriptionTier
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_practice_entry)

        findViewById<MaterialButton>(R.id.openPracticeMapButton).setOnClickListener {
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
