package com.drivest.navigation.settings

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsAnalyticsMismatchTest {

    @Test
    fun consentFalseKeepsSettingsOffUntilConsentIsUpdated() {
        val enableDecision = AnalyticsConsentGate.onUserToggleRequested(
            consentEnabled = false,
            requestedEnabled = true
        )
        assertFalse(enableDecision.settingsEnabled)
        assertTrue(enableDecision.requiresConsentReview)

        val effectiveBeforeConsentUpdate = AnalyticsConsentGate.resolveEffectiveSetting(
            consentEnabled = false,
            settingsEnabled = true
        )
        assertFalse(effectiveBeforeConsentUpdate)

        val effectiveAfterConsentUpdate = AnalyticsConsentGate.resolveEffectiveSetting(
            consentEnabled = true,
            settingsEnabled = false
        )
        assertTrue(effectiveAfterConsentUpdate)
    }
}
