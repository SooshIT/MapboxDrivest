package com.drivest.navigation.subscription.billing

import android.app.Activity

class FakeBillingClientFacade(
    var connectResult: Boolean = true,
    var productsResult: List<BillingProduct> = emptyList(),
    var purchaseResult: BillingPurchaseResult = BillingPurchaseResult.Failed("not configured"),
    var restoreResult: BillingRestoreResult = BillingRestoreResult.Failed("not configured")
) : BillingClientFacade {

    var connectCalls: Int = 0
        private set
    var queryProductsCalls: Int = 0
        private set
    var purchaseCalls: Int = 0
        private set
    var restoreCalls: Int = 0
        private set
    var lastPurchaseProductId: String? = null
        private set

    override suspend fun connect(): Boolean {
        connectCalls += 1
        return connectResult
    }

    override suspend fun queryProducts(): List<BillingProduct> {
        queryProductsCalls += 1
        return productsResult
    }

    override suspend fun launchPurchase(activity: Activity?, productId: String): BillingPurchaseResult {
        purchaseCalls += 1
        lastPurchaseProductId = productId
        return purchaseResult
    }

    override suspend fun restorePurchases(): BillingRestoreResult {
        restoreCalls += 1
        return restoreResult
    }

    override fun endConnection() = Unit
}
