package com.drivest.navigation.subscription.billing

import android.app.Activity
import com.drivest.navigation.subscription.BillingConfig
import com.drivest.navigation.subscription.StoreProvider
import com.drivest.navigation.subscription.SubscriptionRepository
import com.drivest.navigation.subscription.SubscriptionTier

class BillingService(
    private val subscriptionRepository: SubscriptionRepository,
    private val billingClientFacade: BillingClientFacade,
    private val clock: Clock = SystemClock,
    private val billingEnabledProvider: () -> Boolean = { BillingConfig.ENABLE_BILLING }
) {

    suspend fun getAvailableProducts(): List<BillingProduct> {
        if (!billingEnabledProvider()) return emptyList()
        if (!billingClientFacade.connect()) return emptyList()
        return billingClientFacade.queryProducts()
    }

    suspend fun purchase(activity: Activity?, productId: String): BillingPurchaseResult {
        if (!billingEnabledProvider()) {
            return BillingPurchaseResult.Failed("Billing disabled")
        }
        if (!billingClientFacade.connect()) {
            return BillingPurchaseResult.Failed("Billing unavailable")
        }

        val purchaseResult = billingClientFacade.launchPurchase(activity, productId)
        if (purchaseResult is BillingPurchaseResult.Success) {
            val entitlement = purchaseResult.entitlement ?: fallbackEntitlementForProduct(productId)
            if (entitlement != null) {
                subscriptionRepository.setActiveSubscription(
                    tier = entitlement.tier,
                    expiryMs = entitlement.expiryMs,
                    provider = entitlement.provider,
                    verifiedAtMs = clock.nowMs()
                )
            }
        }
        return purchaseResult
    }

    suspend fun restore(): BillingRestoreResult {
        if (!billingEnabledProvider()) {
            return BillingRestoreResult.Failed("Billing disabled")
        }
        if (!billingClientFacade.connect()) {
            return BillingRestoreResult.Failed("Billing unavailable")
        }
        val restoreResult = billingClientFacade.restorePurchases()
        when (restoreResult) {
            is BillingRestoreResult.Restored -> {
                if (restoreResult.active && restoreResult.entitlement != null) {
                    subscriptionRepository.setActiveSubscription(
                        tier = restoreResult.entitlement.tier,
                        expiryMs = restoreResult.entitlement.expiryMs,
                        provider = restoreResult.entitlement.provider,
                        verifiedAtMs = clock.nowMs()
                    )
                } else {
                    subscriptionRepository.clearSubscription()
                }
            }
            is BillingRestoreResult.Failed -> Unit
        }
        return restoreResult
    }

    fun endConnection() {
        billingClientFacade.endConnection()
    }

    interface Clock {
        fun nowMs(): Long
    }

    private fun fallbackEntitlementForProduct(productId: String): ResolvedEntitlement? {
        val tier = BillingConfig.tierForProduct(productId) ?: return null
        val expiryMs = when (tier) {
            SubscriptionTier.PRACTICE_MONTHLY -> clock.nowMs() + DAYS_30_MS
            SubscriptionTier.GLOBAL_ANNUAL -> clock.nowMs() + DAYS_365_MS
            SubscriptionTier.FREE -> return null
        }
        return ResolvedEntitlement(
            tier = tier,
            expiryMs = expiryMs,
            provider = StoreProvider.PLAY
        )
    }

    private object SystemClock : Clock {
        override fun nowMs(): Long = System.currentTimeMillis()
    }

    private companion object {
        const val DAYS_30_MS: Long = 30L * 24L * 60L * 60L * 1000L
        const val DAYS_365_MS: Long = 365L * 24L * 60L * 60L * 1000L
    }
}
