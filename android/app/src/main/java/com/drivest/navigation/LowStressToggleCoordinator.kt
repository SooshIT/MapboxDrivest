package com.drivest.navigation

/**
 * Root-cause note:
 * The low-stress switch used to be controlled by both direct tap mutations and async
 * DataStore emissions. During route re-prioritization + store write latency, stale store
 * emissions could temporarily overwrite the user's latest tap, causing visible flicker.
 *
 * This coordinator keeps the latest user intent as a pending optimistic value until the
 * store emits the same value (confirmation). Stale emissions are ignored for display state.
 */
class LowStressToggleCoordinator(
    initialStoredValue: Boolean = false
) {
    private var storedValue: Boolean = initialStoredValue
    private var pendingPersistValue: Boolean? = null

    fun onUserToggled(requestedEnabled: Boolean): LowStressToggleUiState {
        pendingPersistValue = requestedEnabled
        return snapshot()
    }

    fun onStoreValue(storedEnabled: Boolean): LowStressToggleUiState {
        storedValue = storedEnabled
        if (pendingPersistValue == storedEnabled) {
            pendingPersistValue = null
        }
        return snapshot()
    }

    private fun snapshot(): LowStressToggleUiState {
        val effective = pendingPersistValue ?: storedValue
        return LowStressToggleUiState(
            displayedChecked = effective,
            effectiveEnabled = effective,
            pendingPersistValue = pendingPersistValue
        )
    }
}

data class LowStressToggleUiState(
    val displayedChecked: Boolean,
    val effectiveEnabled: Boolean,
    val pendingPersistValue: Boolean?
)
