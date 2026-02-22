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

class FeatureAccessManagerMatrixTest {

    @Test
    fun freeTierAccessMatrix() {
        runBlocking {
            val harness = createHarness(nowMs = 2_000_000L)
            assertFalse(harness.manager.hasPracticeAccess("colchester").first())
            assertFalse(harness.manager.hasGlobalNavigationAccess().first())
            assertFalse(harness.manager.hasTrainingPlanAccess().first())
            assertFalse(harness.manager.hasInstructorAccess().first())
            assertFalse(harness.manager.hasLowStressRoutingAccess().first())
            assertFalse(harness.manager.hasOfflineAccess().first())
            harness.dispose()
        }
    }

    @Test
    fun practiceMonthlyAccessMatrix() {
        runBlocking {
            val nowMs = 2_000_000L
            val harness = createHarness(nowMs = nowMs)
            harness.settingsRepository.setPracticeCentreId("colchester")
            harness.subscriptionRepository.setActiveSubscription(
                tier = SubscriptionTier.PRACTICE_MONTHLY,
                expiryMs = nowMs + 7L * 24L * 60L * 60L * 1000L,
                provider = StoreProvider.PLAY
            )

            assertTrue(harness.manager.hasPracticeAccess("colchester").first())
            assertFalse(harness.manager.hasGlobalNavigationAccess().first())
            assertTrue(harness.manager.hasTrainingPlanAccess().first())
            assertFalse(harness.manager.hasInstructorAccess().first())
            assertTrue(harness.manager.hasLowStressRoutingAccess().first())
            assertTrue(harness.manager.hasOfflineAccess().first())
            harness.dispose()
        }
    }

    @Test
    fun globalAnnualAccessMatrix() {
        runBlocking {
            val nowMs = 2_000_000L
            val harness = createHarness(nowMs = nowMs)
            harness.subscriptionRepository.setActiveSubscription(
                tier = SubscriptionTier.GLOBAL_ANNUAL,
                expiryMs = nowMs + 365L * 24L * 60L * 60L * 1000L,
                provider = StoreProvider.PLAY
            )

            assertTrue(harness.manager.hasPracticeAccess("any-centre").first())
            assertTrue(harness.manager.hasGlobalNavigationAccess().first())
            assertTrue(harness.manager.hasTrainingPlanAccess().first())
            assertTrue(harness.manager.hasInstructorAccess().first())
            assertTrue(harness.manager.hasLowStressRoutingAccess().first())
            assertTrue(harness.manager.hasOfflineAccess().first())
            harness.dispose()
        }
    }

    private fun createHarness(nowMs: Long): Harness {
        val tempDir = Files.createTempDirectory("feature_access_matrix_test").toFile()
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

        return Harness(
            scope = scope,
            tempDir = tempDir,
            settingsRepository = settingsRepository,
            subscriptionRepository = subscriptionRepository,
            manager = manager
        )
    }

    private data class Harness(
        val scope: CoroutineScope,
        val tempDir: File,
        val settingsRepository: SettingsRepository,
        val subscriptionRepository: SubscriptionRepository,
        val manager: FeatureAccessManager
    ) {
        fun dispose() {
            scope.cancel()
            tempDir.deleteRecursively()
        }
    }

    private class AlwaysGrantedNotificationPermissionChecker : NotificationPermissionChecker {
        override fun isPermissionGranted(): Boolean = true
    }
}
