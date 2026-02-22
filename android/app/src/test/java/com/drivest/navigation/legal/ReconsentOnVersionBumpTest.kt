package com.drivest.navigation.legal

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class ReconsentOnVersionBumpTest {

    @Test
    fun oldAcceptedVersionsRequireConsentAgain() {
        runBlocking {
            val tempDir = Files.createTempDirectory("reconsent_version_bump_test").toFile()
            val storeFile = File(tempDir, "drivest_consent.preferences_pb")
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            val dataStore = PreferenceDataStoreFactory.create(
                scope = scope,
                produceFile = { storeFile }
            )
            dataStore.edit { preferences ->
                preferences[stringPreferencesKey("terms_accepted_version")] = "0.9"
                preferences[stringPreferencesKey("privacy_accepted_version")] = "0.9"
                preferences[longPreferencesKey("terms_accepted_at_ms")] = 1_000L
                preferences[longPreferencesKey("privacy_accepted_at_ms")] = 1_000L
            }
            val repository = ConsentRepository(dataStore = dataStore)

            assertTrue(repository.needsConsent.first())

            scope.cancel()
            tempDir.deleteRecursively()
        }
    }
}
