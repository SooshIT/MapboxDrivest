package com.drivest.navigation.settings

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class SettingsRepositoryPersistenceTest {

    @Test
    fun valuesPersistAfterRepositoryRecreation() {
        runBlocking {
            val tempDir = Files.createTempDirectory("settings_repo_persist_test").toFile()
            val storeFile = File(tempDir, "drivest_settings.preferences_pb")
            val permissionChecker = MutableNotificationPermissionChecker(initialGranted = true)

            val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            val dataStore = PreferenceDataStoreFactory.create(
                scope = scope,
                produceFile = { storeFile }
            )

            val first = SettingsRepository(
                dataStore = dataStore,
                notificationPermissionChecker = permissionChecker
            )
            first.setVoiceMode(VoiceModeSetting.ALERTS)
            first.setPromptSensitivity(PromptSensitivity.EXTRA_HELP)
            first.setUnits(PreferredUnitsSetting.METRIC_KMH)
            first.setLowStressRoutingEnabled(true)
            first.setHazardsEnabled(false)
            first.setAnalyticsEnabled(true)
            first.setNotificationsPreference(true)
            first.refreshNotificationsPermission()

            val second = SettingsRepository(
                dataStore = dataStore,
                notificationPermissionChecker = permissionChecker
            )
            assertEquals(VoiceModeSetting.ALERTS, second.voiceMode.first())
            assertEquals(PromptSensitivity.EXTRA_HELP, second.promptSensitivity.first())
            assertEquals(PreferredUnitsSetting.METRIC_KMH, second.units.first())
            assertTrue(second.lowStressRoutingEnabled.first())
            assertEquals(false, second.hazardsEnabled.first())
            assertTrue(second.analyticsEnabled.first())
            assertTrue(second.notificationsPreference.first())
            assertTrue(second.notificationsEnabled.first())

            scope.cancel()
            tempDir.deleteRecursively()
        }
    }

    private class MutableNotificationPermissionChecker(
        initialGranted: Boolean
    ) : NotificationPermissionChecker {
        var granted: Boolean = initialGranted

        override fun isPermissionGranted(): Boolean {
            return granted
        }
    }
}
