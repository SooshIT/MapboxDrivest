package com.drivest.navigation.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore by preferencesDataStore(name = "drivest_settings")

class SettingsRepository(
    private val dataStore: DataStore<Preferences>,
    private val notificationPermissionChecker: NotificationPermissionChecker
) {

    constructor(
        context: Context,
        notificationPermissionChecker: NotificationPermissionChecker = AndroidNotificationPermissionChecker(context)
    ) : this(
        dataStore = context.settingsDataStore,
        notificationPermissionChecker = notificationPermissionChecker
    )

    private val notificationsOsPermissionState =
        MutableStateFlow(notificationPermissionChecker.isPermissionGranted())

    val voiceMode: Flow<VoiceModeSetting> = dataStore.data.map { preferences ->
        VoiceModeSetting.fromStorage(preferences[KEY_VOICE_MODE])
    }

    val preferredUnits: Flow<PreferredUnitsSetting> = dataStore.data.map { preferences ->
        PreferredUnitsSetting.fromStorage(preferences[KEY_UNITS])
    }

    val units: Flow<PreferredUnitsSetting> = preferredUnits

    val lastSelectedCentreId: Flow<String> = dataStore.data.map { preferences ->
        preferences[KEY_LAST_CENTRE_ID] ?: DEFAULT_CENTRE_ID
    }

    val practiceCentreId: Flow<String?> = dataStore.data.map { preferences ->
        preferences[KEY_PRACTICE_CENTRE_ID]
    }

    val lastMode: Flow<String> = dataStore.data.map { preferences ->
        preferences[KEY_LAST_MODE] ?: DEFAULT_MODE
    }

    val dataSourceMode: Flow<DataSourceMode> = dataStore.data.map { preferences ->
        DataSourceMode.fromStorage(preferences[KEY_DATA_SOURCE_MODE])
    }

    val useBackendPacks: Flow<Boolean> = dataSourceMode.map { mode ->
        mode != DataSourceMode.ASSETS_ONLY
    }

    val hazardsEnabled: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[KEY_HAZARDS_ENABLED] ?: preferences[KEY_VISUAL_PROMPTS_ENABLED] ?: true
    }

    // Backward-compatibility alias.
    val visualPromptsEnabled: Flow<Boolean> = hazardsEnabled

    val userSetHazardsEnabled: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[KEY_USER_SET_HAZARDS_ENABLED]
            ?: (preferences[KEY_HAZARDS_ENABLED] != null || preferences[KEY_VISUAL_PROMPTS_ENABLED] != null)
    }

    val promptSensitivity: Flow<PromptSensitivity> = dataStore.data.map { preferences ->
        PromptSensitivity.fromStorage(preferences[KEY_PROMPT_SENSITIVITY])
    }

    val userSetPromptSensitivity: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[KEY_USER_SET_PROMPT_SENSITIVITY] ?: (preferences[KEY_PROMPT_SENSITIVITY] != null)
    }

    val lowStressRoutingEnabled: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[KEY_LOW_STRESS_MODE_ENABLED] ?: false
    }

    // Backward-compatibility alias.
    val lowStressModeEnabled: Flow<Boolean> = lowStressRoutingEnabled

    val userSetLowStressRouting: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[KEY_USER_SET_LOW_STRESS_ROUTING] ?: (preferences[KEY_LOW_STRESS_MODE_ENABLED] != null)
    }

    val analyticsEnabled: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[KEY_ANALYTICS_ENABLED] ?: false
    }

    val notificationsPreference: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[KEY_NOTIFICATIONS_PREFERENCE] ?: true
    }

    val notificationsOsPermissionGranted: StateFlow<Boolean> =
        notificationsOsPermissionState.asStateFlow()

    val notificationsEnabled: Flow<Boolean> = combine(
        notificationsPreference,
        notificationsOsPermissionGranted
    ) { preference, osPermissionGranted ->
        preference && osPermissionGranted
    }

    val lastFallbackUsedEpochMs: Flow<Long> = dataStore.data.map { preferences ->
        preferences[KEY_LAST_FALLBACK_USED_EPOCH_MS] ?: 0L
    }

    val lastBackendErrorSummary: Flow<String> = dataStore.data.map { preferences ->
        preferences[KEY_LAST_BACKEND_ERROR_SUMMARY].orEmpty()
    }

    suspend fun setVoiceMode(mode: VoiceModeSetting) {
        dataStore.edit { prefs ->
            prefs[KEY_VOICE_MODE] = mode.storageValue
        }
    }

    suspend fun setPreferredUnits(units: PreferredUnitsSetting) {
        dataStore.edit { prefs ->
            prefs[KEY_UNITS] = units.storageValue
        }
    }

    suspend fun setUnits(units: PreferredUnitsSetting) {
        setPreferredUnits(units)
    }

    suspend fun setLastSelectedCentreId(centreId: String) {
        dataStore.edit { prefs ->
            prefs[KEY_LAST_CENTRE_ID] = centreId
        }
    }

    suspend fun setPracticeCentreId(centreId: String?) {
        dataStore.edit { prefs ->
            val normalized = centreId?.trim().orEmpty()
            if (normalized.isBlank()) {
                prefs.remove(KEY_PRACTICE_CENTRE_ID)
            } else {
                prefs[KEY_PRACTICE_CENTRE_ID] = normalized
            }
        }
    }

    suspend fun setLastMode(mode: String) {
        dataStore.edit { prefs ->
            prefs[KEY_LAST_MODE] = mode
        }
    }

    suspend fun setDataSourceMode(mode: DataSourceMode) {
        dataStore.edit { prefs ->
            prefs[KEY_DATA_SOURCE_MODE] = mode.storageValue
        }
    }

    suspend fun setUseBackendPacks(enabled: Boolean) {
        setDataSourceMode(
            if (enabled) {
                DataSourceMode.BACKEND_THEN_CACHE_THEN_ASSETS
            } else {
                DataSourceMode.ASSETS_ONLY
            }
        )
    }

    suspend fun setHazardsEnabled(
        enabled: Boolean,
        markUserSet: Boolean = false
    ) {
        dataStore.edit { prefs ->
            prefs[KEY_HAZARDS_ENABLED] = enabled
            prefs[KEY_VISUAL_PROMPTS_ENABLED] = enabled
            if (markUserSet) {
                prefs[KEY_USER_SET_HAZARDS_ENABLED] = true
            }
        }
    }

    suspend fun setVisualPromptsEnabled(enabled: Boolean) {
        setHazardsEnabled(enabled)
    }

    suspend fun setPromptSensitivity(
        sensitivity: PromptSensitivity,
        markUserSet: Boolean = false
    ) {
        dataStore.edit { prefs ->
            prefs[KEY_PROMPT_SENSITIVITY] = sensitivity.storageValue
            if (markUserSet) {
                prefs[KEY_USER_SET_PROMPT_SENSITIVITY] = true
            }
        }
    }

    suspend fun setLowStressRoutingEnabled(
        enabled: Boolean,
        markUserSet: Boolean = false
    ) {
        dataStore.edit { prefs ->
            prefs[KEY_LOW_STRESS_MODE_ENABLED] = enabled
            if (markUserSet) {
                prefs[KEY_USER_SET_LOW_STRESS_ROUTING] = true
            }
        }
    }

    suspend fun setLowStressModeEnabled(
        enabled: Boolean,
        markUserSet: Boolean = false
    ) {
        setLowStressRoutingEnabled(enabled, markUserSet = markUserSet)
    }

    suspend fun setAnalyticsEnabled(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[KEY_ANALYTICS_ENABLED] = enabled
        }
    }

    suspend fun setNotificationsPreference(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[KEY_NOTIFICATIONS_PREFERENCE] = enabled
        }
    }

    fun refreshNotificationsPermission() {
        notificationsOsPermissionState.value = notificationPermissionChecker.isPermissionGranted()
    }

    suspend fun autoAdjustPromptSensitivity(
        confidenceScore: Int,
        dismissRate: Float
    ) {
        val target = when {
            confidenceScore < 40 -> PromptSensitivity.EXTRA_HELP
            confidenceScore > 75 && dismissRate > 0.45f -> PromptSensitivity.MINIMAL
            else -> PromptSensitivity.STANDARD
        }
        setPromptSensitivity(target)
    }

    suspend fun recordFallbackUsed(nowEpochMs: Long = System.currentTimeMillis()) {
        dataStore.edit { prefs ->
            prefs[KEY_LAST_FALLBACK_USED_EPOCH_MS] = nowEpochMs
        }
    }

    suspend fun recordBackendErrorSummary(summary: String) {
        dataStore.edit { prefs ->
            prefs[KEY_LAST_BACKEND_ERROR_SUMMARY] = summary.take(MAX_ERROR_SUMMARY_LENGTH)
        }
    }

    suspend fun clearBackendErrorSummary() {
        dataStore.edit { prefs ->
            prefs.remove(KEY_LAST_BACKEND_ERROR_SUMMARY)
        }
    }

    suspend fun consumeOfflineDataSourceBannerSlot(
        nowEpochMs: Long = System.currentTimeMillis()
    ): Boolean {
        var shouldShow = false
        dataStore.edit { prefs ->
            val lastFallbackAt = prefs[KEY_LAST_FALLBACK_USED_EPOCH_MS] ?: 0L
            val lastShownAt = prefs[KEY_LAST_OFFLINE_BANNER_SHOWN_EPOCH_MS] ?: 0L
            val hasNewFallback = lastFallbackAt > 0L && lastFallbackAt >= lastShownAt
            val cooldownElapsed = nowEpochMs - lastShownAt >= OFFLINE_BANNER_COOLDOWN_MS
            if (hasNewFallback && cooldownElapsed) {
                shouldShow = true
                prefs[KEY_LAST_OFFLINE_BANNER_SHOWN_EPOCH_MS] = nowEpochMs
            }
        }
        return shouldShow
    }

    private companion object {
        val KEY_VOICE_MODE: Preferences.Key<String> = stringPreferencesKey("voice_mode")
        val KEY_UNITS: Preferences.Key<String> = stringPreferencesKey("preferred_units")
        val KEY_LAST_CENTRE_ID: Preferences.Key<String> = stringPreferencesKey("last_selected_centre_id")
        val KEY_PRACTICE_CENTRE_ID: Preferences.Key<String> = stringPreferencesKey("practice_centre_id")
        val KEY_LAST_MODE: Preferences.Key<String> = stringPreferencesKey("last_mode")
        val KEY_DATA_SOURCE_MODE: Preferences.Key<String> = stringPreferencesKey("data_source_mode")
        val KEY_VISUAL_PROMPTS_ENABLED: Preferences.Key<Boolean> =
            booleanPreferencesKey("visual_prompts_enabled")
        val KEY_HAZARDS_ENABLED: Preferences.Key<Boolean> =
            booleanPreferencesKey("hazards_enabled")
        val KEY_USER_SET_HAZARDS_ENABLED: Preferences.Key<Boolean> =
            booleanPreferencesKey("user_set_hazards_enabled")
        val KEY_PROMPT_SENSITIVITY: Preferences.Key<String> =
            stringPreferencesKey("prompt_sensitivity")
        val KEY_USER_SET_PROMPT_SENSITIVITY: Preferences.Key<Boolean> =
            booleanPreferencesKey("user_set_prompt_sensitivity")
        val KEY_LOW_STRESS_MODE_ENABLED: Preferences.Key<Boolean> =
            booleanPreferencesKey("low_stress_mode_enabled")
        val KEY_USER_SET_LOW_STRESS_ROUTING: Preferences.Key<Boolean> =
            booleanPreferencesKey("user_set_low_stress_routing")
        val KEY_ANALYTICS_ENABLED: Preferences.Key<Boolean> =
            booleanPreferencesKey("analytics_enabled")
        val KEY_NOTIFICATIONS_PREFERENCE: Preferences.Key<Boolean> =
            booleanPreferencesKey("notifications_preference")
        val KEY_LAST_FALLBACK_USED_EPOCH_MS: Preferences.Key<Long> =
            longPreferencesKey("last_fallback_used_epoch_ms")
        val KEY_LAST_BACKEND_ERROR_SUMMARY: Preferences.Key<String> =
            stringPreferencesKey("last_backend_error_summary")
        val KEY_LAST_OFFLINE_BANNER_SHOWN_EPOCH_MS: Preferences.Key<Long> =
            longPreferencesKey("last_offline_banner_shown_epoch_ms")

        const val DEFAULT_CENTRE_ID = "colchester"
        const val DEFAULT_MODE = "practice"
        const val OFFLINE_BANNER_COOLDOWN_MS = 6L * 60L * 60L * 1000L
        const val MAX_ERROR_SUMMARY_LENGTH = 240
    }
}
