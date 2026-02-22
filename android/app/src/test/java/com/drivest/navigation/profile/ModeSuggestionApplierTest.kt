package com.drivest.navigation.profile

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.drivest.navigation.settings.NotificationPermissionChecker
import com.drivest.navigation.settings.SettingsRepository
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

class ModeSuggestionApplierTest {

    @Test
    fun firstTimeNewDriverAppliesLowStressWhenUserHasNotSetIt() {
        runBlocking {
            val tempDir = Files.createTempDirectory("mode_suggestion_applier_test_1").toFile()
            val profileStoreFile = File(tempDir, "drivest_profile.preferences_pb")
            val settingsStoreFile = File(tempDir, "drivest_settings.preferences_pb")
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

            val profileDataStore = PreferenceDataStoreFactory.create(
                scope = scope,
                produceFile = { profileStoreFile }
            )
            val settingsDataStore = PreferenceDataStoreFactory.create(
                scope = scope,
                produceFile = { settingsStoreFile }
            )
            val driverProfileRepository = DriverProfileRepository(dataStore = profileDataStore)
            val settingsRepository = SettingsRepository(
                dataStore = settingsDataStore,
                notificationPermissionChecker = AlwaysGrantedPermissionChecker()
            )
            val applier = ModeSuggestionApplier(
                driverProfileRepository = driverProfileRepository,
                settingsRepository = settingsRepository
            )

            driverProfileRepository.setDriverMode(DriverMode.NEW_DRIVER)
            applier.applySuggestionsIfNeeded(DriverMode.NEW_DRIVER)

            assertTrue(settingsRepository.lowStressRoutingEnabled.first())
            assertTrue(driverProfileRepository.isModeSeen(DriverMode.NEW_DRIVER).first())

            scope.cancel()
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun userSetFlagsPreventModeSuggestionsFromOverriding() {
        runBlocking {
            val tempDir = Files.createTempDirectory("mode_suggestion_applier_test_2").toFile()
            val profileStoreFile = File(tempDir, "drivest_profile.preferences_pb")
            val settingsStoreFile = File(tempDir, "drivest_settings.preferences_pb")
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

            val profileDataStore = PreferenceDataStoreFactory.create(
                scope = scope,
                produceFile = { profileStoreFile }
            )
            val settingsDataStore = PreferenceDataStoreFactory.create(
                scope = scope,
                produceFile = { settingsStoreFile }
            )
            val driverProfileRepository = DriverProfileRepository(dataStore = profileDataStore)
            val settingsRepository = SettingsRepository(
                dataStore = settingsDataStore,
                notificationPermissionChecker = AlwaysGrantedPermissionChecker()
            )
            val applier = ModeSuggestionApplier(
                driverProfileRepository = driverProfileRepository,
                settingsRepository = settingsRepository
            )

            settingsRepository.setLowStressRoutingEnabled(false, markUserSet = true)
            driverProfileRepository.setDriverMode(DriverMode.NEW_DRIVER)
            applier.applySuggestionsIfNeeded(DriverMode.NEW_DRIVER)

            assertTrue(settingsRepository.userSetLowStressRouting.first())
            assertFalse(settingsRepository.lowStressRoutingEnabled.first())

            scope.cancel()
            tempDir.deleteRecursively()
        }
    }

    private class AlwaysGrantedPermissionChecker : NotificationPermissionChecker {
        override fun isPermissionGranted(): Boolean = true
    }
}
