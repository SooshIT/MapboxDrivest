package com.drivest.navigation.profile

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File
import java.nio.file.Files

class DriverProfileRepositoryModeTest {

    @Test
    fun defaultModeIsLearnerAndPersistsAfterSet() {
        runBlocking {
            val tempDir = Files.createTempDirectory("driver_profile_mode_test").toFile()
            val storeFile = File(tempDir, "drivest_profile.preferences_pb")
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            val dataStore = PreferenceDataStoreFactory.create(
                scope = scope,
                produceFile = { storeFile }
            )

            val first = DriverProfileRepository(dataStore = dataStore)
            assertEquals(DriverMode.LEARNER, first.driverMode.first())

            first.setDriverMode(DriverMode.NEW_DRIVER)

            val second = DriverProfileRepository(dataStore = dataStore)
            assertEquals(DriverMode.NEW_DRIVER, second.driverMode.first())

            scope.cancel()
            tempDir.deleteRecursively()
        }
    }
}
