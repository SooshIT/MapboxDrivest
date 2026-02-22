package com.drivest.navigation.profile

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.driverProfileDataStore by preferencesDataStore(name = "drivest_profile")

data class DriverProfile(
    val totalSessions: Int,
    val totalMetersDriven: Long,
    val averageStressIndex: Float,
    val totalOffRouteEvents: Int,
    val practiceCompletions: Int,
    val practiceSessionsCompletedCount: Int,
    val confidenceScore: Int,
    val driverMode: DriverMode,
    val learnerModeSeen: Boolean,
    val newDriverModeSeen: Boolean,
    val standardModeSeen: Boolean,
    val practicalPassPromptShown: Boolean,
    val instructorModeEnabled: Boolean,
    val subscriptionTier: SubscriptionTier,
    val organisationCode: String
)

class DriverProfileRepository(
    private val dataStore: DataStore<Preferences>
) {
    private val confidenceEngine = ConfidenceEngine()

    constructor(
        context: Context
    ) : this(
        dataStore = context.driverProfileDataStore
    )

    val profile: Flow<DriverProfile> = dataStore.data.map { preferences ->
        DriverProfile(
            totalSessions = preferences[KEY_TOTAL_SESSIONS] ?: 0,
            totalMetersDriven = preferences[KEY_TOTAL_METERS_DRIVEN] ?: 0L,
            averageStressIndex = preferences[KEY_AVERAGE_STRESS_INDEX] ?: 0f,
            totalOffRouteEvents = preferences[KEY_TOTAL_OFF_ROUTE_EVENTS] ?: 0,
            practiceCompletions = preferences[KEY_PRACTICE_COMPLETIONS] ?: 0,
            practiceSessionsCompletedCount = preferences[KEY_PRACTICE_SESSIONS_COMPLETED_COUNT] ?: 0,
            confidenceScore = preferences[KEY_CONFIDENCE_SCORE] ?: 0,
            driverMode = DriverMode.fromStorage(preferences[KEY_DRIVER_MODE]),
            learnerModeSeen = preferences[KEY_LEARNER_MODE_SEEN] ?: false,
            newDriverModeSeen = preferences[KEY_NEW_DRIVER_MODE_SEEN] ?: false,
            standardModeSeen = preferences[KEY_STANDARD_MODE_SEEN] ?: false,
            practicalPassPromptShown = preferences[KEY_PRACTICAL_PASS_PROMPT_SHOWN] ?: false,
            instructorModeEnabled = (preferences[KEY_INSTRUCTOR_MODE] ?: 0) == 1,
            subscriptionTier = SubscriptionTier.fromStorage(preferences[KEY_SUBSCRIPTION_TIER]),
            organisationCode = preferences[KEY_ORGANISATION_CODE].orEmpty()
        )
    }

    val driverMode: Flow<DriverMode> = profile.map { it.driverMode }
    val practiceSessionsCompletedCount: Flow<Int> = profile.map { it.practiceSessionsCompletedCount }
    val practicalPassPromptShown: Flow<Boolean> = profile.map { it.practicalPassPromptShown }
    val practicalPassPromptEligible: Flow<Boolean> = profile.map { currentProfile ->
        PracticalPassPromptEligibility.isEligible(
            mode = currentProfile.driverMode,
            practiceSessionsCompletedCount = currentProfile.practiceSessionsCompletedCount,
            promptAlreadyShown = currentProfile.practicalPassPromptShown
        )
    }

    suspend fun recordSession(
        distanceMetersDriven: Int,
        stressIndex: Int,
        offRouteEvents: Int,
        isPracticeCompletion: Boolean
    ) {
        dataStore.edit { prefs ->
            val previousSessions = prefs[KEY_TOTAL_SESSIONS] ?: 0
            val nextSessions = previousSessions + 1

            val previousAverageStress = prefs[KEY_AVERAGE_STRESS_INDEX] ?: 0f
            val nextAverageStress = if (previousSessions <= 0) {
                stressIndex.toFloat()
            } else {
                ((previousAverageStress * previousSessions) + stressIndex.toFloat()) / nextSessions.toFloat()
            }

            val nextProfile = DriverProfile(
                totalSessions = nextSessions,
                totalMetersDriven = (prefs[KEY_TOTAL_METERS_DRIVEN] ?: 0L) +
                    distanceMetersDriven.coerceAtLeast(0).toLong(),
                averageStressIndex = nextAverageStress.coerceIn(0f, 100f),
                totalOffRouteEvents = (prefs[KEY_TOTAL_OFF_ROUTE_EVENTS] ?: 0) +
                    offRouteEvents.coerceAtLeast(0),
                practiceCompletions = (prefs[KEY_PRACTICE_COMPLETIONS] ?: 0) + if (isPracticeCompletion) 1 else 0,
                practiceSessionsCompletedCount = (prefs[KEY_PRACTICE_SESSIONS_COMPLETED_COUNT] ?: 0) +
                    if (isPracticeCompletion) 1 else 0,
                confidenceScore = 0,
                driverMode = DriverMode.fromStorage(prefs[KEY_DRIVER_MODE]),
                learnerModeSeen = prefs[KEY_LEARNER_MODE_SEEN] ?: false,
                newDriverModeSeen = prefs[KEY_NEW_DRIVER_MODE_SEEN] ?: false,
                standardModeSeen = prefs[KEY_STANDARD_MODE_SEEN] ?: false,
                practicalPassPromptShown = prefs[KEY_PRACTICAL_PASS_PROMPT_SHOWN] ?: false,
                instructorModeEnabled = (prefs[KEY_INSTRUCTOR_MODE] ?: 0) == 1,
                subscriptionTier = SubscriptionTier.fromStorage(prefs[KEY_SUBSCRIPTION_TIER]),
                organisationCode = prefs[KEY_ORGANISATION_CODE].orEmpty()
            )
            val computedConfidence = confidenceEngine.compute(nextProfile)

            prefs[KEY_TOTAL_SESSIONS] = nextProfile.totalSessions
            prefs[KEY_TOTAL_METERS_DRIVEN] = nextProfile.totalMetersDriven
            prefs[KEY_AVERAGE_STRESS_INDEX] = nextProfile.averageStressIndex
            prefs[KEY_TOTAL_OFF_ROUTE_EVENTS] = nextProfile.totalOffRouteEvents
            prefs[KEY_PRACTICE_COMPLETIONS] = nextProfile.practiceCompletions
            prefs[KEY_PRACTICE_SESSIONS_COMPLETED_COUNT] = nextProfile.practiceSessionsCompletedCount
            prefs[KEY_CONFIDENCE_SCORE] = computedConfidence
        }
    }

    suspend fun setDriverMode(mode: DriverMode) {
        dataStore.edit { prefs ->
            prefs[KEY_DRIVER_MODE] = mode.storageValue
        }
    }

    suspend fun markModeSeen(mode: DriverMode) {
        dataStore.edit { prefs ->
            when (mode) {
                DriverMode.LEARNER -> prefs[KEY_LEARNER_MODE_SEEN] = true
                DriverMode.NEW_DRIVER -> prefs[KEY_NEW_DRIVER_MODE_SEEN] = true
                DriverMode.STANDARD -> prefs[KEY_STANDARD_MODE_SEEN] = true
            }
        }
    }

    fun isModeSeen(mode: DriverMode): Flow<Boolean> {
        return dataStore.data.map { prefs ->
            when (mode) {
                DriverMode.LEARNER -> prefs[KEY_LEARNER_MODE_SEEN] ?: false
                DriverMode.NEW_DRIVER -> prefs[KEY_NEW_DRIVER_MODE_SEEN] ?: false
                DriverMode.STANDARD -> prefs[KEY_STANDARD_MODE_SEEN] ?: false
            }
        }
    }

    suspend fun setPracticalPassPromptShown(shown: Boolean = true) {
        dataStore.edit { prefs ->
            prefs[KEY_PRACTICAL_PASS_PROMPT_SHOWN] = shown
        }
    }

    suspend fun setInstructorModeEnabled(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[KEY_INSTRUCTOR_MODE] = if (enabled) 1 else 0
        }
    }

    suspend fun setSubscriptionTier(tier: SubscriptionTier) {
        dataStore.edit { prefs ->
            prefs[KEY_SUBSCRIPTION_TIER] = tier.storageValue
        }
    }

    suspend fun setOrganisationCode(code: String) {
        dataStore.edit { prefs ->
            prefs[KEY_ORGANISATION_CODE] = code.trim()
        }
    }

    private companion object {
        val KEY_TOTAL_SESSIONS: Preferences.Key<Int> = intPreferencesKey("total_sessions")
        val KEY_TOTAL_METERS_DRIVEN: Preferences.Key<Long> = longPreferencesKey("total_meters_driven")
        val KEY_AVERAGE_STRESS_INDEX: Preferences.Key<Float> = floatPreferencesKey("average_stress_index")
        val KEY_TOTAL_OFF_ROUTE_EVENTS: Preferences.Key<Int> = intPreferencesKey("total_off_route_events")
        val KEY_PRACTICE_COMPLETIONS: Preferences.Key<Int> = intPreferencesKey("practice_completions")
        val KEY_PRACTICE_SESSIONS_COMPLETED_COUNT: Preferences.Key<Int> =
            intPreferencesKey("practice_sessions_completed_count")
        val KEY_CONFIDENCE_SCORE: Preferences.Key<Int> = intPreferencesKey("confidence_score")
        val KEY_DRIVER_MODE: Preferences.Key<String> = stringPreferencesKey("driver_mode")
        val KEY_LEARNER_MODE_SEEN: Preferences.Key<Boolean> = booleanPreferencesKey("learner_mode_seen")
        val KEY_NEW_DRIVER_MODE_SEEN: Preferences.Key<Boolean> = booleanPreferencesKey("new_driver_mode_seen")
        val KEY_STANDARD_MODE_SEEN: Preferences.Key<Boolean> = booleanPreferencesKey("standard_mode_seen")
        val KEY_PRACTICAL_PASS_PROMPT_SHOWN: Preferences.Key<Boolean> =
            booleanPreferencesKey("practical_pass_prompt_shown")
        val KEY_INSTRUCTOR_MODE: Preferences.Key<Int> = intPreferencesKey("instructor_mode_enabled")
        val KEY_SUBSCRIPTION_TIER: Preferences.Key<String> = stringPreferencesKey("subscription_tier")
        val KEY_ORGANISATION_CODE: Preferences.Key<String> = stringPreferencesKey("organisation_code")
    }
}
