package com.drivest.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LowStressToggleCoordinatorTest {

    @Test
    fun pendingUserToggleMasksStaleStoreEmission() {
        val coordinator = LowStressToggleCoordinator(initialStoredValue = false)
        coordinator.onStoreValue(false)

        val pendingState = coordinator.onUserToggled(true)
        assertTrue(pendingState.displayedChecked)
        assertTrue(pendingState.effectiveEnabled)
        assertEquals(true, pendingState.pendingPersistValue)

        // Store emits stale old value while write is in-flight.
        val staleState = coordinator.onStoreValue(false)
        assertTrue(staleState.displayedChecked)
        assertTrue(staleState.effectiveEnabled)
        assertEquals(true, staleState.pendingPersistValue)

        // Store confirms latest user choice; pending can clear.
        val confirmedState = coordinator.onStoreValue(true)
        assertTrue(confirmedState.displayedChecked)
        assertTrue(confirmedState.effectiveEnabled)
        assertNull(confirmedState.pendingPersistValue)
    }

    @Test
    fun rapidToggleKeepsLastIntentStableUntilStoreConfirmation() {
        val coordinator = LowStressToggleCoordinator(initialStoredValue = false)
        coordinator.onStoreValue(false)

        coordinator.onUserToggled(true)
        val lastTapState = coordinator.onUserToggled(false)
        assertFalse(lastTapState.displayedChecked)
        assertFalse(lastTapState.effectiveEnabled)
        assertEquals(false, lastTapState.pendingPersistValue)

        // Out-of-order store value should not bounce UI back.
        val outOfOrderStoreState = coordinator.onStoreValue(true)
        assertFalse(outOfOrderStoreState.displayedChecked)
        assertFalse(outOfOrderStoreState.effectiveEnabled)
        assertEquals(false, outOfOrderStoreState.pendingPersistValue)

        val finalStoreState = coordinator.onStoreValue(false)
        assertFalse(finalStoreState.displayedChecked)
        assertFalse(finalStoreState.effectiveEnabled)
        assertNull(finalStoreState.pendingPersistValue)
    }
}
