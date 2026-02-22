package com.drivest.navigation.legal

object SessionSafetyGatekeeper {
    fun shouldStartSession(
        needsSafetyAck: Boolean,
        userAcceptedDialog: Boolean
    ): Boolean {
        return !needsSafetyAck || userAcceptedDialog
    }
}
