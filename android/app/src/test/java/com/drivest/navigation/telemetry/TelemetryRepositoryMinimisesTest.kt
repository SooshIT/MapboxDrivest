package com.drivest.navigation.telemetry

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.drivest.navigation.intelligence.RouteDifficultyLabel
import com.drivest.navigation.intelligence.RouteIntelligenceSummary
import com.drivest.navigation.legal.ConsentRepository
import com.drivest.navigation.settings.NotificationPermissionChecker
import com.drivest.navigation.settings.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.io.File
import java.nio.file.Files

class TelemetryRepositoryMinimisesTest {

    @Test
    fun minimalTelemetryBlocksEventsAndStripsSessionSummaryPayload() {
        runBlocking {
            val tempDir = Files.createTempDirectory("telemetry_minimises_test").toFile()
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
            val fakeBackend = FakeTelemetryBackendClient()
            val repository = TelemetryRepository(
                settingsRepository = settingsRepository,
                consentRepository = consentRepository,
                backendClient = fakeBackend,
                appVersionProvider = { "1.0.0-test" }
            )

            settingsRepository.setAnalyticsEnabled(true)
            consentRepository.setAnalyticsConsent(false)

            repository.sendEvent(
                TelemetryEvent.App(
                    eventType = "prompt_fired",
                    payload = mapOf("source" to "test")
                )
            )
            assertEquals(0, fakeBackend.calls.size)

            repository.sendSessionSummary(
                SessionSummaryTelemetry(
                    mode = "practice",
                    centreId = "colchester",
                    routeId = "route-1",
                    completed = true,
                    durationSeconds = 600,
                    distanceMetersDriven = 1800,
                    offRouteCount = 2,
                    averageStressIndex = 42,
                    averageConfidenceScore = 61,
                    intelligenceSummary = RouteIntelligenceSummary(
                        roundaboutCount = 4,
                        trafficSignalCount = 6,
                        zebraCount = 2,
                        schoolCount = 1,
                        busLaneCount = 3,
                        complexityScore = 55,
                        stressIndex = 42,
                        difficultyLabel = RouteDifficultyLabel.MEDIUM
                    ),
                    centresPackVersion = "centres-v1",
                    routesPackVersion = "routes-v1",
                    hazardsPackVersion = "hazards-v1"
                )
            )

            assertEquals(1, fakeBackend.calls.size)
            val call = fakeBackend.calls.first()
            assertEquals("/telemetry", call.path)
            val payloadJson = call.payload.getJSONObject("payload_json")
            val keys = mutableSetOf<String>()
            val iterator = payloadJson.keys()
            while (iterator.hasNext()) {
                keys += iterator.next()
            }

            val allowedKeys = setOf(
                "duration_seconds",
                "distance_meters",
                "completion_flag",
                "off_route_count",
                "avg_stress_index",
                "avg_confidence_score",
                "app_version",
                "pack_versions"
            )
            assertEquals(allowedKeys, keys)
            assertFalse(payloadJson.has("mode"))
            assertFalse(payloadJson.has("roundabout_count"))

            scope.cancel()
            tempDir.deleteRecursively()
        }
    }

    private class AlwaysGrantedNotificationPermissionChecker : NotificationPermissionChecker {
        override fun isPermissionGranted(): Boolean = true
    }

    private class FakeTelemetryBackendClient : TelemetryBackendClient {
        val calls = mutableListOf<BackendCall>()

        override suspend fun post(path: String, payload: JSONObject): Boolean {
            calls += BackendCall(path = path, payload = JSONObject(payload.toString()))
            return true
        }
    }

    private data class BackendCall(
        val path: String,
        val payload: JSONObject
    )
}
