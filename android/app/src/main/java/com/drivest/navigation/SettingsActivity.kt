package com.drivest.navigation

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.drivest.navigation.databinding.ActivitySettingsBinding
import com.drivest.navigation.legal.ConsentRepository
import com.drivest.navigation.legal.LegalConstants
import com.drivest.navigation.legal.LegalIntentUtils
import com.drivest.navigation.pack.PackStore
import com.drivest.navigation.pack.PackType
import com.drivest.navigation.profile.DriverMode
import com.drivest.navigation.profile.DriverProfileRepository
import com.drivest.navigation.profile.ModeSuggestionApplier
import com.drivest.navigation.report.SessionSummaryExporter
import com.drivest.navigation.settings.AnalyticsConsentGate
import com.drivest.navigation.settings.PreferredUnitsSetting
import com.drivest.navigation.settings.PromptSensitivity
import com.drivest.navigation.settings.SettingsRepository
import com.drivest.navigation.settings.VoiceModeSetting
import com.drivest.navigation.subscription.SubscriptionRepository
import com.drivest.navigation.subscription.SubscriptionState
import com.drivest.navigation.subscription.SubscriptionTier
import com.drivest.navigation.theory.TheoryFeatureFlags
import com.drivest.navigation.theory.screens.TheorySettingsActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.json.JSONObject

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var consentRepository: ConsentRepository
    private lateinit var driverProfileRepository: DriverProfileRepository
    private lateinit var modeSuggestionApplier: ModeSuggestionApplier
    private lateinit var subscriptionRepository: SubscriptionRepository
    private lateinit var packStore: PackStore
    private val summaryExporter by lazy { SessionSummaryExporter(applicationContext) }
    private var isSuppressingCallbacks: Boolean = false
    private val analyticsConsentReviewLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        lifecycleScope.launch {
            syncAnalyticsSettingToConsent()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        settingsRepository = SettingsRepository(applicationContext)
        consentRepository = ConsentRepository(applicationContext)
        driverProfileRepository = DriverProfileRepository(applicationContext)
        modeSuggestionApplier = ModeSuggestionApplier(
            driverProfileRepository = driverProfileRepository,
            settingsRepository = settingsRepository
        )
        subscriptionRepository = SubscriptionRepository(applicationContext)
        packStore = PackStore(applicationContext)
        binding.theorySettingsButton.isVisible = TheoryFeatureFlags.isTheoryModuleEnabled()
        settingsRepository.refreshNotificationsPermission()
        lifecycleScope.launch {
            syncAnalyticsSettingToConsent()
            modeSuggestionApplier.applySuggestionsIfNeeded(driverProfileRepository.driverMode.first())
        }
        observeSettings()
        bindListeners()
    }

    override fun onResume() {
        super.onResume()
        settingsRepository.refreshNotificationsPermission()
        lifecycleScope.launch {
            syncAnalyticsSettingToConsent()
        }
    }

    private fun observeSettings() {
        lifecycleScope.launch {
            val navigationSettingsFlow = combine(
                settingsRepository.voiceMode,
                settingsRepository.units,
                settingsRepository.hazardsEnabled,
                settingsRepository.promptSensitivity,
                settingsRepository.lowStressRoutingEnabled
            ) { voiceMode, units, hazardsEnabled, promptSensitivity, lowStressRoutingEnabled ->
                NavigationSettingsSnapshot(
                    voiceMode = voiceMode,
                    units = units,
                    hazardsEnabled = hazardsEnabled,
                    promptSensitivity = promptSensitivity,
                    lowStressRoutingEnabled = lowStressRoutingEnabled
                )
            }

            val privacyCoreFlow = combine(
                settingsRepository.analyticsEnabled,
                consentRepository.analyticsConsentEnabled,
                settingsRepository.notificationsPreference,
                settingsRepository.notificationsEnabled,
                settingsRepository.notificationsOsPermissionGranted
            ) {
                analyticsSettingEnabled,
                analyticsConsentEnabled,
                notificationsPreference,
                notificationsEnabled,
                notificationsPermissionGranted ->
                val effectiveAnalyticsEnabled = AnalyticsConsentGate.resolveEffectiveSetting(
                    consentEnabled = analyticsConsentEnabled,
                    settingsEnabled = analyticsSettingEnabled
                )
                PrivacyCoreSettingsSnapshot(
                    analyticsEnabled = effectiveAnalyticsEnabled,
                    analyticsConsentEnabled = analyticsConsentEnabled,
                    analyticsSettingEnabled = analyticsSettingEnabled,
                    notificationsPreference = notificationsPreference,
                    notificationsEnabled = notificationsEnabled,
                    notificationsPermissionGranted = notificationsPermissionGranted
                )
            }
            val privacySettingsFlow = combine(
                privacyCoreFlow,
                settingsRepository.lastSelectedCentreId
            ) { core, lastSelectedCentreId ->
                PrivacySettingsSnapshot(
                    analyticsEnabled = core.analyticsEnabled,
                    analyticsConsentEnabled = core.analyticsConsentEnabled,
                    analyticsSettingEnabled = core.analyticsSettingEnabled,
                    notificationsPreference = core.notificationsPreference,
                    notificationsEnabled = core.notificationsEnabled,
                    notificationsPermissionGranted = core.notificationsPermissionGranted,
                    lastSelectedCentreId = lastSelectedCentreId
                )
            }

            navigationSettingsFlow
                .combine(privacySettingsFlow) { navigation, privacy ->
                    BaseSettingsSnapshot(
                        voiceMode = navigation.voiceMode,
                        units = navigation.units,
                        hazardsEnabled = navigation.hazardsEnabled,
                        promptSensitivity = navigation.promptSensitivity,
                        lowStressRoutingEnabled = navigation.lowStressRoutingEnabled,
                        analyticsEnabled = privacy.analyticsEnabled,
                        analyticsConsentEnabled = privacy.analyticsConsentEnabled,
                        analyticsSettingEnabled = privacy.analyticsSettingEnabled,
                        notificationsPreference = privacy.notificationsPreference,
                        notificationsEnabled = privacy.notificationsEnabled,
                        notificationsPermissionGranted = privacy.notificationsPermissionGranted,
                        lastSelectedCentreId = privacy.lastSelectedCentreId
                    )
                }
                .combine(driverProfileRepository.profile) { base, profile ->
                    ProfileSettingsSnapshot(
                        voiceMode = base.voiceMode,
                        units = base.units,
                        hazardsEnabled = base.hazardsEnabled,
                        promptSensitivity = base.promptSensitivity,
                        lowStressRoutingEnabled = base.lowStressRoutingEnabled,
                        analyticsEnabled = base.analyticsEnabled,
                        analyticsConsentEnabled = base.analyticsConsentEnabled,
                        analyticsSettingEnabled = base.analyticsSettingEnabled,
                        notificationsPreference = base.notificationsPreference,
                        notificationsEnabled = base.notificationsEnabled,
                        notificationsPermissionGranted = base.notificationsPermissionGranted,
                        driverMode = profile.driverMode,
                        confidenceScore = profile.confidenceScore,
                        lastSelectedCentreId = base.lastSelectedCentreId
                    )
                }
                .combine(subscriptionRepository.subscriptionState) { settings, subscription ->
                    SettingsSnapshot(
                        voiceMode = settings.voiceMode,
                        units = settings.units,
                        hazardsEnabled = settings.hazardsEnabled,
                        promptSensitivity = settings.promptSensitivity,
                        lowStressRoutingEnabled = settings.lowStressRoutingEnabled,
                        analyticsEnabled = settings.analyticsEnabled,
                        analyticsConsentEnabled = settings.analyticsConsentEnabled,
                        analyticsSettingEnabled = settings.analyticsSettingEnabled,
                        notificationsPreference = settings.notificationsPreference,
                        notificationsEnabled = settings.notificationsEnabled,
                        notificationsPermissionGranted = settings.notificationsPermissionGranted,
                        driverMode = settings.driverMode,
                        confidenceScore = settings.confidenceScore,
                        lastSelectedCentreId = settings.lastSelectedCentreId,
                        subscriptionState = subscription
                    )
                }
                .collectLatest { settings ->
                    if (settings.analyticsSettingEnabled != settings.analyticsEnabled) {
                        lifecycleScope.launch {
                            settingsRepository.setAnalyticsEnabled(settings.analyticsEnabled)
                        }
                    }
                    renderVoiceMode(settings.voiceMode)
                    renderUnits(settings.units)
                    renderHazardsEnabled(settings.hazardsEnabled)
                    renderPromptSensitivity(settings.promptSensitivity)
                    renderLowStressRouting(settings.lowStressRoutingEnabled)
                    renderAnalytics(settings.analyticsEnabled)
                    renderNotifications(settings.notificationsEnabled)
                    renderNotificationsStatus(
                        preference = settings.notificationsPreference,
                        effective = settings.notificationsEnabled,
                        permissionGranted = settings.notificationsPermissionGranted
                    )
                    renderDriverMode(settings.driverMode)
                    binding.settingsConfidenceValue.text = getString(
                        R.string.settings_confidence_score,
                        settings.confidenceScore
                    )
                    binding.settingsSubscriptionValue.text = formatSubscriptionValue(settings.subscriptionState)
                    binding.settingsFreshnessWarning.isVisible =
                        hasStalePackData(settings.lastSelectedCentreId)
                }
        }
    }

    private fun bindListeners() {
        binding.voiceModeRadioGroup.setOnCheckedChangeListener { _, checkedId ->
            if (isSuppressingCallbacks) return@setOnCheckedChangeListener
            lifecycleScope.launch {
                when (checkedId) {
                    R.id.voiceModeAllRadio -> settingsRepository.setVoiceMode(VoiceModeSetting.ALL)
                    R.id.voiceModeAlertsRadio -> settingsRepository.setVoiceMode(VoiceModeSetting.ALERTS)
                    R.id.voiceModeMuteRadio -> settingsRepository.setVoiceMode(VoiceModeSetting.MUTE)
                }
            }
        }

        binding.unitsRadioGroup.setOnCheckedChangeListener { _, checkedId ->
            if (isSuppressingCallbacks) return@setOnCheckedChangeListener
            lifecycleScope.launch {
                when (checkedId) {
                    R.id.unitsMphRadio -> settingsRepository.setUnits(PreferredUnitsSetting.UK_MPH)
                    R.id.unitsKmhRadio -> settingsRepository.setUnits(PreferredUnitsSetting.METRIC_KMH)
                }
            }
        }

        binding.driverModeRadioGroup.setOnCheckedChangeListener { _, checkedId ->
            if (isSuppressingCallbacks) return@setOnCheckedChangeListener
            val selectedMode = when (checkedId) {
                R.id.driverModeLearnerRadio -> DriverMode.LEARNER
                R.id.driverModeNewDriverRadio -> DriverMode.NEW_DRIVER
                R.id.driverModeStandardRadio -> DriverMode.STANDARD
                else -> return@setOnCheckedChangeListener
            }
            lifecycleScope.launch {
                driverProfileRepository.setDriverMode(selectedMode)
                modeSuggestionApplier.applySuggestionsIfNeeded(selectedMode)
            }
        }

        binding.hazardsEnabledSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (isSuppressingCallbacks) return@setOnCheckedChangeListener
            lifecycleScope.launch {
                settingsRepository.setHazardsEnabled(isChecked, markUserSet = true)
            }
        }

        binding.promptSensitivityRadioGroup.setOnCheckedChangeListener { _, checkedId ->
            if (isSuppressingCallbacks) return@setOnCheckedChangeListener
            lifecycleScope.launch {
                when (checkedId) {
                    R.id.promptSensitivityMinimalRadio ->
                        settingsRepository.setPromptSensitivity(
                            PromptSensitivity.MINIMAL,
                            markUserSet = true
                        )
                    R.id.promptSensitivityStandardRadio ->
                        settingsRepository.setPromptSensitivity(
                            PromptSensitivity.STANDARD,
                            markUserSet = true
                        )
                    R.id.promptSensitivityExtraHelpRadio ->
                        settingsRepository.setPromptSensitivity(
                            PromptSensitivity.EXTRA_HELP,
                            markUserSet = true
                        )
                }
            }
        }

        binding.lowStressRoutingSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (isSuppressingCallbacks) return@setOnCheckedChangeListener
            lifecycleScope.launch {
                settingsRepository.setLowStressRoutingEnabled(isChecked, markUserSet = true)
            }
        }

        binding.analyticsSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (isSuppressingCallbacks) return@setOnCheckedChangeListener
            lifecycleScope.launch {
                val consentEnabled = consentRepository.analyticsConsentEnabled.first()
                val decision = AnalyticsConsentGate.onUserToggleRequested(
                    consentEnabled = consentEnabled,
                    requestedEnabled = isChecked
                )
                settingsRepository.setAnalyticsEnabled(decision.settingsEnabled)
                if (!isChecked) {
                    consentRepository.setAnalyticsConsent(false)
                    return@launch
                }
                if (decision.requiresConsentReview) {
                    showAnalyticsConsentRequiredDialog()
                    return@launch
                }
                showEnableAnalyticsDialog()
            }
        }

        binding.notificationsSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (isSuppressingCallbacks) return@setOnCheckedChangeListener
            lifecycleScope.launch {
                settingsRepository.setNotificationsPreference(isChecked)
                settingsRepository.refreshNotificationsPermission()
                if (isChecked && !settingsRepository.notificationsOsPermissionGranted.value) {
                    showNotificationsPermissionDialog()
                }
            }
        }

        binding.dataRightsButton.setOnClickListener {
            startActivity(Intent(this, DataRightsActivity::class.java))
        }
        binding.paymentsSubscriptionsButton.setOnClickListener {
            startActivity(Intent(this, PaymentsSubscriptionsActivity::class.java))
        }

        binding.termsButton.setOnClickListener {
            openExternalUrl(LegalConstants.TERMS_URL)
        }
        binding.privacyButton.setOnClickListener {
            openExternalUrl(LegalConstants.PRIVACY_URL)
        }
        binding.faqButton.setOnClickListener {
            openExternalUrl(LegalConstants.FAQ_URL)
        }
        binding.contentAccuracyButton.setOnClickListener {
            startActivity(Intent(this, ContentAccuracyActivity::class.java))
        }
        binding.serviceAvailabilityButton.setOnClickListener {
            startActivity(Intent(this, ServiceAvailabilityActivity::class.java))
        }
        binding.safetyNoticeButton.setOnClickListener {
            startActivity(Intent(this, SafetyNoticeActivity::class.java))
        }
        binding.theorySettingsButton.setOnClickListener {
            startActivity(Intent(this, TheorySettingsActivity::class.java))
        }
        binding.contactSupportButton.setOnClickListener {
            startActivity(Intent(this, ContactSupportActivity::class.java))
        }
        binding.aboutDrivestButton.setOnClickListener {
            startActivity(Intent(this, AboutDrivestActivity::class.java))
        }
        binding.exportDriveLogButton.setOnClickListener {
            exportLatestDriveLog()
        }
    }

    private fun renderVoiceMode(mode: VoiceModeSetting) {
        val targetId = when (mode) {
            VoiceModeSetting.ALL -> R.id.voiceModeAllRadio
            VoiceModeSetting.ALERTS -> R.id.voiceModeAlertsRadio
            VoiceModeSetting.MUTE -> R.id.voiceModeMuteRadio
        }
        if (binding.voiceModeRadioGroup.checkedRadioButtonId != targetId) {
            withSuppressedCallbacks {
                binding.voiceModeRadioGroup.check(targetId)
            }
        }
    }

    private fun renderUnits(units: PreferredUnitsSetting) {
        val targetId = when (units) {
            PreferredUnitsSetting.UK_MPH -> R.id.unitsMphRadio
            PreferredUnitsSetting.METRIC_KMH -> R.id.unitsKmhRadio
        }
        if (binding.unitsRadioGroup.checkedRadioButtonId != targetId) {
            withSuppressedCallbacks {
                binding.unitsRadioGroup.check(targetId)
            }
        }
    }

    private fun renderHazardsEnabled(enabled: Boolean) {
        if (binding.hazardsEnabledSwitch.isChecked != enabled) {
            withSuppressedCallbacks {
                binding.hazardsEnabledSwitch.isChecked = enabled
            }
        }
    }

    private fun renderPromptSensitivity(sensitivity: PromptSensitivity) {
        val targetId = when (sensitivity) {
            PromptSensitivity.MINIMAL -> R.id.promptSensitivityMinimalRadio
            PromptSensitivity.STANDARD -> R.id.promptSensitivityStandardRadio
            PromptSensitivity.EXTRA_HELP -> R.id.promptSensitivityExtraHelpRadio
        }
        if (binding.promptSensitivityRadioGroup.checkedRadioButtonId != targetId) {
            withSuppressedCallbacks {
                binding.promptSensitivityRadioGroup.check(targetId)
            }
        }
    }

    private fun renderLowStressRouting(enabled: Boolean) {
        if (binding.lowStressRoutingSwitch.isChecked != enabled) {
            withSuppressedCallbacks {
                binding.lowStressRoutingSwitch.isChecked = enabled
            }
        }
    }

    private fun renderDriverMode(mode: DriverMode) {
        binding.settingsDriverModeValue.text = getString(
            R.string.settings_driver_mode_value,
            formatDriverModeLabel(mode)
        )
        binding.settingsDriverModeDescription.text = when (mode) {
            DriverMode.LEARNER -> getString(R.string.settings_driver_mode_desc_learner)
            DriverMode.NEW_DRIVER -> getString(R.string.settings_driver_mode_desc_new_driver)
            DriverMode.STANDARD -> getString(R.string.settings_driver_mode_desc_standard)
        }
        val targetId = when (mode) {
            DriverMode.LEARNER -> R.id.driverModeLearnerRadio
            DriverMode.NEW_DRIVER -> R.id.driverModeNewDriverRadio
            DriverMode.STANDARD -> R.id.driverModeStandardRadio
        }
        if (binding.driverModeRadioGroup.checkedRadioButtonId != targetId) {
            withSuppressedCallbacks {
                binding.driverModeRadioGroup.check(targetId)
            }
        }
    }

    private fun renderAnalytics(enabled: Boolean) {
        if (binding.analyticsSwitch.isChecked != enabled) {
            withSuppressedCallbacks {
                binding.analyticsSwitch.isChecked = enabled
            }
        }
    }

    private fun renderNotifications(enabled: Boolean) {
        if (binding.notificationsSwitch.isChecked != enabled) {
            withSuppressedCallbacks {
                binding.notificationsSwitch.isChecked = enabled
            }
        }
    }

    private fun renderNotificationsStatus(
        preference: Boolean,
        effective: Boolean,
        permissionGranted: Boolean
    ) {
        binding.notificationsStatusValue.text = when {
            !permissionGranted && preference -> getString(R.string.settings_notifications_status_blocked)
            !permissionGranted -> getString(R.string.settings_notifications_status_denied)
            effective -> getString(R.string.settings_notifications_status_enabled)
            else -> getString(R.string.settings_notifications_status_disabled)
        }
    }

    private fun showEnableAnalyticsDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.settings_analytics_dialog_title)
            .setMessage(R.string.settings_analytics_dialog_message)
            .setPositiveButton(R.string.settings_enable) { _, _ ->
                lifecycleScope.launch {
                    consentRepository.setAnalyticsConsent(true)
                    settingsRepository.setAnalyticsEnabled(true)
                }
            }
            .setNegativeButton(R.string.settings_cancel) { _, _ ->
                lifecycleScope.launch {
                    consentRepository.setAnalyticsConsent(false)
                    settingsRepository.setAnalyticsEnabled(false)
                }
            }
            .setOnCancelListener {
                lifecycleScope.launch {
                    consentRepository.setAnalyticsConsent(false)
                    settingsRepository.setAnalyticsEnabled(false)
                }
            }
            .show()
    }

    private fun showAnalyticsConsentRequiredDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.settings_analytics_consent_required_title)
            .setMessage(R.string.settings_analytics_consent_required_message)
            .setPositiveButton(R.string.settings_analytics_review_consent) { _, _ ->
                val reviewIntent = Intent(this, AnalyticsConsentActivity::class.java).apply {
                    putExtra(AnalyticsConsentActivity.EXTRA_REVIEW_ONLY, true)
                }
                analyticsConsentReviewLauncher.launch(reviewIntent)
            }
            .setNegativeButton(R.string.settings_cancel, null)
            .show()
    }

    private fun showNotificationsPermissionDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.settings_notifications_permission_title)
            .setMessage(R.string.settings_notifications_permission_message)
            .setPositiveButton(R.string.settings_open_system_settings) { _, _ ->
                openSystemNotificationSettings()
            }
            .setNegativeButton(R.string.settings_cancel, null)
            .show()
    }

    private fun openSystemNotificationSettings() {
        val notificationSettingsIntent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
            putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
        }
        if (notificationSettingsIntent.resolveActivity(packageManager) != null) {
            startActivity(notificationSettingsIntent)
            return
        }
        startActivity(
            Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", packageName, null)
            )
        )
    }

    private fun openExternalUrl(url: String) {
        val launched = LegalIntentUtils.openExternalUrl(this, url)
        if (!launched) {
            Toast.makeText(this, getString(R.string.settings_no_browser_app), Toast.LENGTH_SHORT).show()
        }
    }

    private suspend fun syncAnalyticsSettingToConsent() {
        val consentEnabled = consentRepository.analyticsConsentEnabled.first()
        val settingsEnabled = settingsRepository.analyticsEnabled.first()
        val effective = AnalyticsConsentGate.resolveEffectiveSetting(
            consentEnabled = consentEnabled,
            settingsEnabled = settingsEnabled
        )
        if (effective != settingsEnabled) {
            settingsRepository.setAnalyticsEnabled(effective)
        }
    }

    private fun withSuppressedCallbacks(block: () -> Unit) {
        isSuppressingCallbacks = true
        block()
        isSuppressingCallbacks = false
    }

    private fun exportLatestDriveLog() {
        lifecycleScope.launch {
            runCatching {
                val latestExport = summaryExporter.latestExport()
                val exportFile = latestExport ?: summaryExporter.exportDeveloperSnapshot(
                    snapshot = buildDeveloperSnapshot()
                )
                if (latestExport == null) {
                    Toast.makeText(
                        this@SettingsActivity,
                        getString(R.string.drive_log_exported_fallback),
                        Toast.LENGTH_LONG
                    ).show()
                }
                shareExportFile(exportFile)
            }.onFailure {
                Toast.makeText(
                    this@SettingsActivity,
                    getString(R.string.drive_log_export_failed),
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private suspend fun buildDeveloperSnapshot(): JSONObject {
        val nowMs = System.currentTimeMillis()
        val selectedCentreId = settingsRepository.lastSelectedCentreId.first()
        val driverProfile = driverProfileRepository.profile.first()
        val subscriptionState = subscriptionRepository.subscriptionState.first()
        val effectiveSubscriptionTier = subscriptionState.effectiveTier(nowMs)

        val routesPackVersion = packStore.readPackVersion(PackType.ROUTES, selectedCentreId).orEmpty()
        val hazardsPackVersion = packStore.readPackVersion(PackType.HAZARDS, selectedCentreId).orEmpty()
        val routesPackOffline = packStore.isOfflineAvailable(PackType.ROUTES, selectedCentreId)
        val hazardsPackOffline = packStore.isOfflineAvailable(PackType.HAZARDS, selectedCentreId)

        return JSONObject().apply {
            put(
                "app",
                JSONObject().apply {
                    put("packageName", packageName)
                    put("versionName", BuildConfig.VERSION_NAME)
                    put("versionCode", BuildConfig.VERSION_CODE)
                    put("buildType", BuildConfig.BUILD_TYPE)
                    put("exportedAtMs", nowMs)
                }
            )
            put(
                "device",
                JSONObject().apply {
                    put("manufacturer", Build.MANUFACTURER)
                    put("model", Build.MODEL)
                    put("androidVersion", Build.VERSION.RELEASE)
                    put("sdkInt", Build.VERSION.SDK_INT)
                    put("fingerprint", Build.FINGERPRINT)
                }
            )
            put(
                "settings",
                JSONObject().apply {
                    put("voiceMode", settingsRepository.voiceMode.first().name)
                    put("promptSensitivity", settingsRepository.promptSensitivity.first().name)
                    put("units", settingsRepository.units.first().name)
                    put("hazardsEnabled", settingsRepository.hazardsEnabled.first())
                    put("lowStressRoutingEnabled", settingsRepository.lowStressRoutingEnabled.first())
                    put("analyticsEnabled", settingsRepository.analyticsEnabled.first())
                    put("notificationsPreference", settingsRepository.notificationsPreference.first())
                    put("notificationsEnabledEffective", settingsRepository.notificationsEnabled.first())
                    put("notificationsPermissionGranted", settingsRepository.notificationsOsPermissionGranted.value)
                    put("dataSourceMode", settingsRepository.dataSourceMode.first().name)
                    put("lastSelectedCentreId", selectedCentreId)
                    put("practiceCentreId", settingsRepository.practiceCentreId.first().orEmpty())
                    put("lastMode", settingsRepository.lastMode.first())
                    put("lastBackendErrorSummary", settingsRepository.lastBackendErrorSummary.first())
                    put("lastFallbackUsedEpochMs", settingsRepository.lastFallbackUsedEpochMs.first())
                }
            )
            put(
                "consent",
                JSONObject().apply {
                    put("termsVersionAccepted", consentRepository.termsAcceptedVersion.first())
                    put("termsAcceptedAtMs", consentRepository.termsAcceptedAtMs.first())
                    put("privacyVersionAccepted", consentRepository.privacyAcceptedVersion.first())
                    put("privacyAcceptedAtMs", consentRepository.privacyAcceptedAtMs.first())
                    put("ageConfirmedAtMs", consentRepository.ageConfirmedAtMs.first())
                    put("analyticsConsentEnabled", consentRepository.analyticsConsentEnabled.first())
                    put("analyticsConsentAtMs", consentRepository.analyticsConsentAtMs.first())
                    put("notificationsConsentPreference", consentRepository.notificationsPreference.first())
                    put("notificationsConsentAtMs", consentRepository.notificationsConsentAtMs.first())
                    put("needsConsent", consentRepository.needsConsent.first())
                    put("needsAge", consentRepository.needsAge.first())
                    put("needsSafetyAcknowledgement", consentRepository.needsSafetyAcknowledgement.first())
                    put("safetyAcknowledged", consentRepository.safetyAcknowledged.first())
                    put("safetyAcknowledgedAtMs", consentRepository.safetyAcknowledgedAtMs.first())
                    put("onboardingComplete", consentRepository.onboardingComplete.first())
                }
            )
            put(
                "subscription",
                JSONObject().apply {
                    put("tierStored", subscriptionState.tier.name)
                    put("tierEffective", effectiveSubscriptionTier.name)
                    put("expiryMs", subscriptionState.expiryMs)
                    put("lastVerifiedAtMs", subscriptionState.lastVerifiedAtMs)
                    put("storeProvider", subscriptionState.storeProvider.name)
                    put("isActive", subscriptionState.isActive(nowMs))
                }
            )
            put(
                "profile",
                JSONObject().apply {
                    put("driverMode", driverProfile.driverMode.name)
                    put("confidenceScore", driverProfile.confidenceScore)
                    put("practiceSessionsCompletedCount", driverProfile.practiceSessionsCompletedCount)
                    put("practicalPassPromptShown", driverProfile.practicalPassPromptShown)
                    put("learnerModeSeen", driverProfile.learnerModeSeen)
                    put("newDriverModeSeen", driverProfile.newDriverModeSeen)
                    put("standardModeSeen", driverProfile.standardModeSeen)
                    put("instructorModeEnabled", driverProfile.instructorModeEnabled)
                    put("organisationCode", driverProfile.organisationCode)
                }
            )
            put(
                "packs",
                JSONObject().apply {
                    put("centreId", selectedCentreId)
                    put("routesVersion", routesPackVersion)
                    put("hazardsVersion", hazardsPackVersion)
                    put("routesOfflineAvailable", routesPackOffline)
                    put("hazardsOfflineAvailable", hazardsPackOffline)
                }
            )
            put("latestSessionSummaryFile", summaryExporter.latestExport()?.name.orEmpty())
        }
    }

    private fun shareExportFile(exportFile: java.io.File) {
        val fileUri = FileProvider.getUriForFile(
            this,
            "${BuildConfig.APPLICATION_ID}.fileprovider",
            exportFile
        )
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "application/json"
            putExtra(Intent.EXTRA_STREAM, fileUri)
            putExtra(Intent.EXTRA_SUBJECT, "Drivest Drive Log")
            putExtra(Intent.EXTRA_TEXT, getString(R.string.drive_log_exported_path, exportFile.name))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(shareIntent, getString(R.string.drive_log_export_chooser)))
    }

    private fun hasStalePackData(centreId: String): Boolean {
        if (centreId.isBlank()) return false
        return packStore.isPackOlderThanDays(PackType.ROUTES, centreId, days = 90L) ||
            packStore.isPackOlderThanDays(PackType.HAZARDS, centreId, days = 90L)
    }

    private fun formatSubscriptionValue(subscriptionState: SubscriptionState): String {
        val nowMs = System.currentTimeMillis()
        val effectiveTier = subscriptionState.effectiveTier(nowMs)
        if (effectiveTier == SubscriptionTier.FREE) {
            return getString(R.string.settings_subscription_value_default)
        }
        val tierLabel = when (effectiveTier) {
            SubscriptionTier.PRACTICE_MONTHLY -> "Practice Monthly"
            SubscriptionTier.GLOBAL_ANNUAL -> "Global Annual"
            SubscriptionTier.FREE -> "Free"
        }
        val expiryLabel = if (subscriptionState.expiryMs > 0L) {
            SimpleDateFormat("yyyy-MM-dd", Locale.UK).format(Date(subscriptionState.expiryMs))
        } else {
            "-"
        }
        return getString(R.string.settings_subscription_value_active, tierLabel, expiryLabel)
    }

    private fun formatDriverModeLabel(mode: DriverMode): String {
        return when (mode) {
            DriverMode.LEARNER -> getString(R.string.driver_mode_learner)
            DriverMode.NEW_DRIVER -> getString(R.string.driver_mode_new_driver)
            DriverMode.STANDARD -> getString(R.string.driver_mode_standard)
        }
    }

    private data class SettingsSnapshot(
        val voiceMode: VoiceModeSetting,
        val units: PreferredUnitsSetting,
        val hazardsEnabled: Boolean,
        val promptSensitivity: PromptSensitivity,
        val lowStressRoutingEnabled: Boolean,
        val analyticsEnabled: Boolean,
        val analyticsConsentEnabled: Boolean,
        val analyticsSettingEnabled: Boolean,
        val notificationsPreference: Boolean,
        val notificationsEnabled: Boolean,
        val notificationsPermissionGranted: Boolean,
        val driverMode: DriverMode,
        val confidenceScore: Int,
        val lastSelectedCentreId: String,
        val subscriptionState: SubscriptionState
    )

    private data class ProfileSettingsSnapshot(
        val voiceMode: VoiceModeSetting,
        val units: PreferredUnitsSetting,
        val hazardsEnabled: Boolean,
        val promptSensitivity: PromptSensitivity,
        val lowStressRoutingEnabled: Boolean,
        val analyticsEnabled: Boolean,
        val analyticsConsentEnabled: Boolean,
        val analyticsSettingEnabled: Boolean,
        val notificationsPreference: Boolean,
        val notificationsEnabled: Boolean,
        val notificationsPermissionGranted: Boolean,
        val driverMode: DriverMode,
        val confidenceScore: Int,
        val lastSelectedCentreId: String
    )

    private data class BaseSettingsSnapshot(
        val voiceMode: VoiceModeSetting,
        val units: PreferredUnitsSetting,
        val hazardsEnabled: Boolean,
        val promptSensitivity: PromptSensitivity,
        val lowStressRoutingEnabled: Boolean,
        val analyticsEnabled: Boolean,
        val analyticsConsentEnabled: Boolean,
        val analyticsSettingEnabled: Boolean,
        val notificationsPreference: Boolean,
        val notificationsEnabled: Boolean,
        val notificationsPermissionGranted: Boolean,
        val lastSelectedCentreId: String
    )

    private data class NavigationSettingsSnapshot(
        val voiceMode: VoiceModeSetting,
        val units: PreferredUnitsSetting,
        val hazardsEnabled: Boolean,
        val promptSensitivity: PromptSensitivity,
        val lowStressRoutingEnabled: Boolean
    )

    private data class PrivacyCoreSettingsSnapshot(
        val analyticsEnabled: Boolean,
        val analyticsConsentEnabled: Boolean,
        val analyticsSettingEnabled: Boolean,
        val notificationsPreference: Boolean,
        val notificationsEnabled: Boolean,
        val notificationsPermissionGranted: Boolean
    )

    private data class PrivacySettingsSnapshot(
        val analyticsEnabled: Boolean,
        val analyticsConsentEnabled: Boolean,
        val analyticsSettingEnabled: Boolean,
        val notificationsPreference: Boolean,
        val notificationsEnabled: Boolean,
        val notificationsPermissionGranted: Boolean,
        val lastSelectedCentreId: String
    )
}
