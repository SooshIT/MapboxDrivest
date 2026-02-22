package com.drivest.navigation.settings

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

class NotificationsEffectiveStateTest {

    @Test
    fun effectiveNotificationsRequirePreferenceAndPermission() {
        runBlocking {
            val tempDir = Files.createTempDirectory("notifications_effective_state_test").toFile()
            val storeFile = File(tempDir, "drivest_settings.preferences_pb")
            val checker = MutableNotificationPermissionChecker(initialGranted = false)
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            val dataStore = PreferenceDataStoreFactory.create(
                scope = scope,
                produceFile = { storeFile }
            )
            val repository = SettingsRepository(
                dataStore = dataStore,
                notificationPermissionChecker = checker
            )

            repository.setNotificationsPreference(true)
            repository.refreshNotificationsPermission()
            assertFalse(repository.notificationsEnabled.first())

            checker.granted = true
            repository.refreshNotificationsPermission()
            assertTrue(repository.notificationsEnabled.first())

            repository.setNotificationsPreference(false)
            assertFalse(repository.notificationsEnabled.first())

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
