package com.drivest.navigation.subscription

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PracticePurchaseRequiresCentreSelectionTest {

    @Test
    fun PracticePurchaseRequiresCentreSelection() {
        assertTrue(
            requiresPracticeCentreSelection(
                tier = SubscriptionTier.PRACTICE_MONTHLY,
                practiceCentreId = null
            )
        )
        assertTrue(
            requiresPracticeCentreSelection(
                tier = SubscriptionTier.PRACTICE_MONTHLY,
                practiceCentreId = "   "
            )
        )
        assertFalse(
            requiresPracticeCentreSelection(
                tier = SubscriptionTier.PRACTICE_MONTHLY,
                practiceCentreId = "colchester"
            )
        )
        assertFalse(
            requiresPracticeCentreSelection(
                tier = SubscriptionTier.GLOBAL_ANNUAL,
                practiceCentreId = null
            )
        )
        assertFalse(
            requiresPracticeCentreSelection(
                tier = SubscriptionTier.FREE,
                practiceCentreId = null
            )
        )
    }
}
