package com.drivest.navigation.subscription.billing

import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.drivest.navigation.subscription.BillingConfig
import com.drivest.navigation.subscription.StoreProvider
import com.drivest.navigation.subscription.SubscriptionTier

data class ResolvedEntitlement(
    val tier: SubscriptionTier,
    val expiryMs: Long,
    val provider: StoreProvider
)

class EntitlementResolver(
    private val nowProvider: () -> Long = { System.currentTimeMillis() }
) {
    @Suppress("UNUSED_PARAMETER")
    fun resolve(
        purchases: List<Purchase>,
        productDetailsById: Map<String, ProductDetails> = emptyMap()
    ): ResolvedEntitlement? {
        if (purchases.isEmpty()) return null

        val purchasedProductIds = purchases
            .asSequence()
            .filter { purchase ->
                purchase.purchaseState == Purchase.PurchaseState.PURCHASED
            }
            .flatMap { purchase -> purchase.products.asSequence() }
            .toSet()

        if (purchasedProductIds.isEmpty()) return null

        // This is a temporary local entitlement model for internal testing only.
        // A server-side receipt validation pipeline will replace this in production.
        val nowMs = nowProvider()
        return when {
            purchasedProductIds.contains(BillingConfig.SKU_GLOBAL_ANNUAL) -> ResolvedEntitlement(
                tier = SubscriptionTier.GLOBAL_ANNUAL,
                expiryMs = nowMs + DAYS_365_MS,
                provider = StoreProvider.PLAY
            )
            purchasedProductIds.contains(BillingConfig.SKU_PRACTICE_MONTHLY) -> ResolvedEntitlement(
                tier = SubscriptionTier.PRACTICE_MONTHLY,
                expiryMs = nowMs + DAYS_30_MS,
                provider = StoreProvider.PLAY
            )
            else -> null
        }
    }

    private companion object {
        const val DAYS_30_MS: Long = 30L * 24L * 60L * 60L * 1000L
        const val DAYS_365_MS: Long = 365L * 24L * 60L * 60L * 1000L
    }
}
