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

class ConsentRepositorySafetyTest {

    @Test
    fun safetyAcknowledgementTransitionsFromRequiredToSatisfied() {
        runBlocking {
            val tempDir = Files.createTempDirectory("consent_safety_test").toFile()
            val storeFile = File(tempDir, "drivest_consent.preferences_pb")
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            val dataStore = PreferenceDataStoreFactory.create(
                scope = scope,
                produceFile = { storeFile }
            )
            val repository = ConsentRepository(
                dataStore = dataStore,
                nowProvider = { 5_000L }
            )

            assertTrue(repository.needsSafetyAcknowledgement.first())

            repository.acknowledgeSafety()

            assertFalse(repository.needsSafetyAcknowledgement.first())
            assertTrue(repository.safetyAcknowledged.first())
            assertTrue(repository.safetyAcknowledgedAtMs.first() > 0L)

            scope.cancel()
            tempDir.deleteRecursively()
        }
    }
}
