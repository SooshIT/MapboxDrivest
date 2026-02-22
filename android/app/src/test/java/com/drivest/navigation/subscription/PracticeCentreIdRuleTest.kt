package com.drivest.navigation.subscription

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

class PracticeCentreIdRuleTest {

    @Test
    fun practiceTierRequiresMatchingPracticeCentreId() {
        runBlocking {
            val nowMs = 3_000_000L
            val tempDir = Files.createTempDirectory("practice_centre_rule_test").toFile()
            val subscriptionStoreFile = File(tempDir, "drivest_subscription.preferences_pb")
            val settingsStoreFile = File(tempDir, "drivest_settings.preferences_pb")
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

            val subscriptionDataStore = PreferenceDataStoreFactory.create(
                scope = scope,
                produceFile = { subscriptionStoreFile }
            )
            val settingsDataStore = PreferenceDataStoreFactory.create(
                scope = scope,
                produceFile = { settingsStoreFile }
            )

            val settingsRepository = SettingsRepository(
                dataStore = settingsDataStore,
                notificationPermissionChecker = AlwaysGrantedNotificationPermissionChecker()
            )
            val subscriptionRepository = SubscriptionRepository(
                dataStore = subscriptionDataStore,
                nowProvider = { nowMs }
            )
            val manager = FeatureAccessManager(
                subscriptionRepository = subscriptionRepository,
                settingsRepository = settingsRepository
            )

            settingsRepository.setPracticeCentreId("colchester")
            subscriptionRepository.setActiveSubscription(
                tier = SubscriptionTier.PRACTICE_MONTHLY,
                expiryMs = nowMs + 30L * 24L * 60L * 60L * 1000L,
                provider = StoreProvider.PLAY
            )

            assertFalse(manager.hasPracticeAccess("london").first())
            assertTrue(manager.hasPracticeAccess("colchester").first())

            scope.cancel()
            tempDir.deleteRecursively()
        }
    }

    private class AlwaysGrantedNotificationPermissionChecker : NotificationPermissionChecker {
        override fun isPermissionGranted(): Boolean = true
    }
}
