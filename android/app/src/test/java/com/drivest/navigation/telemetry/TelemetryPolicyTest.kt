package com.drivest.navigation.telemetry

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class TelemetryPolicyTest {

    @Test
    fun consentFalseUsesMinimalTelemetry() {
        runBlocking {
            val policy = TelemetryPolicy(
                consentAnalyticsEnabled = MutableStateFlow(false),
                settingsAnalyticsEnabled = MutableStateFlow(true)
            )

            assertEquals(TelemetryLevel.MINIMAL, policy.level.first())
        }
    }

    @Test
    fun consentTrueUsesFullTelemetry() {
        runBlocking {
            val policy = TelemetryPolicy(
                consentAnalyticsEnabled = MutableStateFlow(true),
                settingsAnalyticsEnabled = MutableStateFlow(false)
            )

            assertEquals(TelemetryLevel.FULL, policy.level.first())
        }
    }
}
