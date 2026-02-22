package com.drivest.navigation.subscription

fun requiresPracticeCentreSelection(
    tier: SubscriptionTier,
    practiceCentreId: String?
): Boolean {
    return tier == SubscriptionTier.PRACTICE_MONTHLY && practiceCentreId.isNullOrBlank()
}
