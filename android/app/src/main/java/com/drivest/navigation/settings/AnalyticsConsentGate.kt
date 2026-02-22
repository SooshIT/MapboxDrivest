package com.drivest.navigation.settings

data class AnalyticsToggleDecision(
    val settingsEnabled: Boolean,
    val requiresConsentReview: Boolean
)

object AnalyticsConsentGate {

    fun resolveEffectiveSetting(
        consentEnabled: Boolean,
        settingsEnabled: Boolean
    ): Boolean {
        return if (consentEnabled != settingsEnabled) {
            consentEnabled
        } else {
            settingsEnabled
        }
    }

    fun onUserToggleRequested(
        consentEnabled: Boolean,
        requestedEnabled: Boolean
    ): AnalyticsToggleDecision {
        if (!requestedEnabled) {
            return AnalyticsToggleDecision(
                settingsEnabled = false,
                requiresConsentReview = false
            )
        }
        if (!consentEnabled) {
            return AnalyticsToggleDecision(
                settingsEnabled = false,
                requiresConsentReview = true
            )
        }
        return AnalyticsToggleDecision(
            settingsEnabled = true,
            requiresConsentReview = false
        )
    }
}
