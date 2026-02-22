package com.drivest.navigation.subscription.billing

import android.app.Activity

data class BillingProduct(
    val productId: String,
    val title: String,
    val price: String,
    val billingPeriod: String?
)

sealed class BillingPurchaseResult {
    data class Success(val entitlement: ResolvedEntitlement? = null) : BillingPurchaseResult()
    data object Cancelled : BillingPurchaseResult()
    data class Failed(val reason: String) : BillingPurchaseResult()
}

sealed class BillingRestoreResult {
    data class Restored(
        val active: Boolean,
        val entitlement: ResolvedEntitlement? = null
    ) : BillingRestoreResult()

    data class Failed(val reason: String) : BillingRestoreResult()
}

interface BillingClientFacade {
    suspend fun connect(): Boolean
    suspend fun queryProducts(): List<BillingProduct>
    suspend fun launchPurchase(activity: Activity?, productId: String): BillingPurchaseResult
    suspend fun restorePurchases(): BillingRestoreResult
    fun endConnection()
}
