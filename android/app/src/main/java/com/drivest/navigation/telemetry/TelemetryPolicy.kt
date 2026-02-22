package com.drivest.navigation.telemetry

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged

enum class TelemetryLevel {
    MINIMAL,
    FULL
}

class TelemetryPolicy(
    consentAnalyticsEnabled: Flow<Boolean>,
    settingsAnalyticsEnabled: Flow<Boolean>
) {

    val level: Flow<TelemetryLevel> = combine(
        consentAnalyticsEnabled,
        settingsAnalyticsEnabled
    ) { consentEnabled, _ ->
        if (consentEnabled) {
            TelemetryLevel.FULL
        } else {
            TelemetryLevel.MINIMAL
        }
    }.distinctUntilChanged()
}
