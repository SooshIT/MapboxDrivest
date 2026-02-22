package com.drivest.navigation.legal

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files
import com.drivest.navigation.settings.NotificationPermissionChecker
import com.drivest.navigation.settings.SettingsRepository

class AnalyticsSettingsSyncTest {

    @Test
    fun skipAnalyticsInOnboardingKeepsSettingsDisabled() {
        runBlocking {
            val tempDir = Files.createTempDirectory("analytics_settings_sync_test").toFile()
            val consentStoreFile = File(tempDir, "drivest_consent.preferences_pb")
            val settingsStoreFile = File(tempDir, "drivest_settings.preferences_pb")
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

            val consentDataStore = PreferenceDataStoreFactory.create(
                scope = scope,
                produceFile = { consentStoreFile }
            )
            val settingsDataStore = PreferenceDataStoreFactory.create(
                scope = scope,
                produceFile = { settingsStoreFile }
            )

            val consentRepository = ConsentRepository(dataStore = consentDataStore)
            val settingsRepository = SettingsRepository(
                dataStore = settingsDataStore,
                notificationPermissionChecker = AlwaysGrantedNotificationPermissionChecker()
            )

            settingsRepository.setAnalyticsEnabled(true)

            consentRepository.setAnalyticsConsent(false)
            settingsRepository.setAnalyticsEnabled(false)

            assertFalse(settingsRepository.analyticsEnabled.first())
            assertTrue(consentRepository.analyticsConsentAtMs.first() > 0L)

            scope.cancel()
            tempDir.deleteRecursively()
        }
    }

    private class AlwaysGrantedNotificationPermissionChecker : NotificationPermissionChecker {
        override fun isPermissionGranted(): Boolean = true
    }
}
