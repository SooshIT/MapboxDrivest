package com.drivest.navigation.legal

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StartBlockedWithoutSafetyAckTest {

    @Test
    fun startBlockedWhenSafetyRequiredAndDialogDeclined() {
        assertFalse(
            SessionSafetyGatekeeper.shouldStartSession(
                needsSafetyAck = true,
                userAcceptedDialog = false
            )
        )
    }

    @Test
    fun startAllowedWhenSafetyRequiredAndDialogAccepted() {
        assertTrue(
            SessionSafetyGatekeeper.shouldStartSession(
                needsSafetyAck = true,
                userAcceptedDialog = true
            )
        )
    }

    @Test
    fun startAllowedWhenSafetyNotRequired() {
        assertTrue(
            SessionSafetyGatekeeper.shouldStartSession(
                needsSafetyAck = false,
                userAcceptedDialog = false
            )
        )
    }
}
