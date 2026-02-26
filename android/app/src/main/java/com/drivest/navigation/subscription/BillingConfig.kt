package com.drivest.navigation.subscription

import com.drivest.navigation.BuildConfig

object BillingConfig {
    val ENABLE_BILLING: Boolean = true

    const val SKU_PRACTICE_MONTHLY: String = "drivest_practice_monthly"
    const val SKU_GLOBAL_ANNUAL: String = "drivest_global_annual"

    fun tierForProduct(productId: String): SubscriptionTier? {
        return when (productId) {
            SKU_PRACTICE_MONTHLY -> SubscriptionTier.PRACTICE_MONTHLY
            SKU_GLOBAL_ANNUAL -> SubscriptionTier.GLOBAL_ANNUAL
            else -> null
        }
    }
}
