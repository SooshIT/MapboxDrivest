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

class ConsentRepositoryTest {

    @Test
    fun onboardingFlagsTransitionAsExpected() {
        runBlocking {
            val tempDir = Files.createTempDirectory("consent_repository_test").toFile()
            val storeFile = File(tempDir, "drivest_consent.preferences_pb")
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            val dataStore = PreferenceDataStoreFactory.create(
                scope = scope,
                produceFile = { storeFile }
            )
            val repository = ConsentRepository(
                dataStore = dataStore,
                nowProvider = { 1_000L }
            )

            assertTrue(repository.needsConsent.first())
            assertTrue(repository.needsAge.first())
            assertFalse(repository.onboardingComplete.first())

            repository.acceptTermsAndPrivacy()
            assertFalse(repository.needsConsent.first())

            repository.confirmAge()
            assertFalse(repository.needsAge.first())

            repository.setAnalyticsConsent(false)
            assertTrue(repository.analyticsConsentAtMs.first() > 0L)

            repository.setNotificationsPreference(false)
            assertTrue(repository.notificationsConsentAtMs.first() > 0L)

            assertTrue(repository.onboardingComplete.first())

            scope.cancel()
            tempDir.deleteRecursively()
        }
    }
}
