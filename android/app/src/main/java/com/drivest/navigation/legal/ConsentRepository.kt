package com.drivest.navigation.legal

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

private val Context.consentDataStore by preferencesDataStore(name = "drivest_consent")

class ConsentRepository(
    private val dataStore: DataStore<Preferences>,
    private val nowProvider: () -> Long = { System.currentTimeMillis() }
) {

    constructor(
        context: Context,
        nowProvider: () -> Long = { System.currentTimeMillis() }
    ) : this(
        dataStore = context.consentDataStore,
        nowProvider = nowProvider
    )

    val termsAcceptedVersion: Flow<String> = dataStore.data.map { preferences ->
        preferences[KEY_TERMS_ACCEPTED_VERSION].orEmpty()
    }

    val termsAcceptedAtMs: Flow<Long> = dataStore.data.map { preferences ->
        preferences[KEY_TERMS_ACCEPTED_AT_MS] ?: 0L
    }

    val privacyAcceptedVersion: Flow<String> = dataStore.data.map { preferences ->
        preferences[KEY_PRIVACY_ACCEPTED_VERSION].orEmpty()
    }

    val privacyAcceptedAtMs: Flow<Long> = dataStore.data.map { preferences ->
        preferences[KEY_PRIVACY_ACCEPTED_AT_MS] ?: 0L
    }

    val ageConfirmedAtMs: Flow<Long> = dataStore.data.map { preferences ->
        preferences[KEY_AGE_CONFIRMED_AT_MS] ?: 0L
    }

    val analyticsConsentEnabled: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[KEY_ANALYTICS_CONSENT_ENABLED] ?: false
    }

    val analyticsConsentAtMs: Flow<Long> = dataStore.data.map { preferences ->
        preferences[KEY_ANALYTICS_CONSENT_AT_MS] ?: 0L
    }

    val notificationsPreference: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[KEY_NOTIFICATIONS_PREFERENCE] ?: false
    }

    val notificationsConsentAtMs: Flow<Long> = dataStore.data.map { preferences ->
        preferences[KEY_NOTIFICATIONS_CONSENT_AT_MS] ?: 0L
    }

    val safetyAcknowledged: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[KEY_SAFETY_ACKNOWLEDGED_FLAG] ?: (
            (preferences[KEY_SAFETY_ACKNOWLEDGED_AT_MS] ?: 0L) > 0L
        )
    }

    val safetyAcknowledgedAtMs: Flow<Long> = dataStore.data.map { preferences ->
        preferences[KEY_SAFETY_ACKNOWLEDGED_AT_MS] ?: 0L
    }

    val needsConsent: Flow<Boolean> = combine(
        termsAcceptedVersion,
        termsAcceptedAtMs,
        privacyAcceptedVersion,
        privacyAcceptedAtMs
    ) { termsVersion, termsAcceptedAtMs, privacyVersion, privacyAcceptedAtMs ->
        termsVersion != LegalConstants.TERMS_VERSION ||
            privacyVersion != LegalConstants.PRIVACY_VERSION ||
            termsAcceptedAtMs <= 0L ||
            privacyAcceptedAtMs <= 0L
    }

    val needsAge: Flow<Boolean> = ageConfirmedAtMs.map { it <= 0L }

    val onboardingComplete: Flow<Boolean> = combine(
        needsConsent,
        needsAge,
        analyticsConsentAtMs,
        notificationsConsentAtMs
    ) { needsConsent, needsAge, analyticsConsentAtMs, notificationsConsentAtMs ->
        !needsConsent &&
            !needsAge &&
            analyticsConsentAtMs > 0L &&
            notificationsConsentAtMs > 0L
    }

    val needsSafetyAcknowledgement: Flow<Boolean> = safetyAcknowledged.map { acknowledged ->
        !acknowledged
    }

    suspend fun acceptTermsAndPrivacy(nowMs: Long = nowProvider()) {
        dataStore.edit { preferences ->
            preferences[KEY_TERMS_ACCEPTED_VERSION] = LegalConstants.TERMS_VERSION
            preferences[KEY_PRIVACY_ACCEPTED_VERSION] = LegalConstants.PRIVACY_VERSION
            preferences[KEY_TERMS_ACCEPTED_AT_MS] = nowMs
            preferences[KEY_PRIVACY_ACCEPTED_AT_MS] = nowMs
        }
    }

    suspend fun confirmAge(nowMs: Long = nowProvider()) {
        dataStore.edit { preferences ->
            preferences[KEY_AGE_CONFIRMED_AT_MS] = nowMs
        }
    }

    suspend fun setAnalyticsConsent(
        enabled: Boolean,
        nowMs: Long = nowProvider()
    ) {
        dataStore.edit { preferences ->
            preferences[KEY_ANALYTICS_CONSENT_ENABLED] = enabled
            preferences[KEY_ANALYTICS_CONSENT_AT_MS] = nowMs
        }
    }

    suspend fun setNotificationsPreference(
        enabled: Boolean,
        nowMs: Long = nowProvider()
    ) {
        dataStore.edit { preferences ->
            preferences[KEY_NOTIFICATIONS_PREFERENCE] = enabled
            preferences[KEY_NOTIFICATIONS_CONSENT_AT_MS] = nowMs
        }
    }

    suspend fun acknowledgeSafety(nowMs: Long = nowProvider()) {
        dataStore.edit { preferences ->
            preferences[KEY_SAFETY_ACKNOWLEDGED_FLAG] = true
            preferences[KEY_SAFETY_ACKNOWLEDGED_AT_MS] = nowMs
        }
    }

    private companion object {
        val KEY_TERMS_ACCEPTED_VERSION: Preferences.Key<String> =
            stringPreferencesKey("terms_accepted_version")
        val KEY_TERMS_ACCEPTED_AT_MS: Preferences.Key<Long> =
            longPreferencesKey("terms_accepted_at_ms")
        val KEY_PRIVACY_ACCEPTED_VERSION: Preferences.Key<String> =
            stringPreferencesKey("privacy_accepted_version")
        val KEY_PRIVACY_ACCEPTED_AT_MS: Preferences.Key<Long> =
            longPreferencesKey("privacy_accepted_at_ms")
        val KEY_AGE_CONFIRMED_AT_MS: Preferences.Key<Long> =
            longPreferencesKey("age_confirmed_at_ms")
        val KEY_ANALYTICS_CONSENT_ENABLED: Preferences.Key<Boolean> =
            booleanPreferencesKey("analytics_consent_enabled")
        val KEY_ANALYTICS_CONSENT_AT_MS: Preferences.Key<Long> =
            longPreferencesKey("analytics_consent_at_ms")
        val KEY_NOTIFICATIONS_PREFERENCE: Preferences.Key<Boolean> =
            booleanPreferencesKey("notifications_preference")
        val KEY_NOTIFICATIONS_CONSENT_AT_MS: Preferences.Key<Long> =
            longPreferencesKey("notifications_consent_at_ms")
        val KEY_SAFETY_ACKNOWLEDGED_FLAG: Preferences.Key<Boolean> =
            booleanPreferencesKey("safety_acknowledged_flag")
        val KEY_SAFETY_ACKNOWLEDGED_AT_MS: Preferences.Key<Long> =
            longPreferencesKey("safety_acknowledged_at_ms")
    }
}
