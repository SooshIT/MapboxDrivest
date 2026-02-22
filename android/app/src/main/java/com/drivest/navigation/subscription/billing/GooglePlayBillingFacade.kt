package com.drivest.navigation.subscription.billing

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.drivest.navigation.subscription.BillingConfig
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

class GooglePlayBillingFacade(
    context: Context,
    private val entitlementResolver: EntitlementResolver = EntitlementResolver()
) : BillingClientFacade {

    private val appContext = context.applicationContext
    private var billingClient: BillingClient? = null
    private var connected: Boolean = false
    private var pendingPurchaseUpdate: CompletableDeferred<Pair<BillingResult, List<Purchase>?>>? = null
    private var cachedProductDetailsById: Map<String, ProductDetails> = emptyMap()

    private val purchasesUpdatedListener = PurchasesUpdatedListener { billingResult, purchases ->
        val pending = pendingPurchaseUpdate
        if (pending != null && !pending.isCompleted) {
            pending.complete(billingResult to purchases)
        }
        pendingPurchaseUpdate = null
    }

    override suspend fun connect(): Boolean {
        if (connected && billingClient?.isReady == true) return true
        val client = ensureBillingClient()
        return suspendCancellableCoroutine { continuation ->
            client.startConnection(object : BillingClientStateListener {
                override fun onBillingServiceDisconnected() {
                    connected = false
                }

                override fun onBillingSetupFinished(billingResult: BillingResult) {
                    connected = billingResult.responseCode == BillingClient.BillingResponseCode.OK
                    continuation.resume(connected)
                }
            })
        }
    }

    override suspend fun queryProducts(): List<BillingProduct> {
        if (!connect()) return emptyList()
        val client = billingClient ?: return emptyList()
        val products = listOf(
            BillingConfig.SKU_PRACTICE_MONTHLY,
            BillingConfig.SKU_GLOBAL_ANNUAL
        ).map { productId ->
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(productId)
                .setProductType(BillingClient.ProductType.SUBS)
                .build()
        }
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(products)
            .build()
        val result = suspendCancellableCoroutine<Pair<BillingResult, List<ProductDetails>>> { continuation ->
            client.queryProductDetailsAsync(params) { billingResult, productDetailsList ->
                continuation.resume(billingResult to productDetailsList)
            }
        }
        if (result.first.responseCode != BillingClient.BillingResponseCode.OK) {
            return emptyList()
        }
        val details = result.second
        cachedProductDetailsById = details.associateBy { it.productId }
        return details.map { detail ->
            val firstPhase = detail.subscriptionOfferDetails
                ?.firstOrNull()
                ?.pricingPhases
                ?.pricingPhaseList
                ?.firstOrNull()
            BillingProduct(
                productId = detail.productId,
                title = detail.title,
                price = firstPhase?.formattedPrice.orEmpty(),
                billingPeriod = firstPhase?.billingPeriod
            )
        }
    }

    override suspend fun launchPurchase(
        activity: Activity?,
        productId: String
    ): BillingPurchaseResult {
        if (activity == null) {
            return BillingPurchaseResult.Failed("Activity is required for purchase flow")
        }
        if (!connect()) {
            return BillingPurchaseResult.Failed("Billing unavailable")
        }
        val client = billingClient ?: return BillingPurchaseResult.Failed("Billing unavailable")
        val productDetails = cachedProductDetailsById[productId]
            ?: queryProducts().let { cachedProductDetailsById[productId] }
            ?: return BillingPurchaseResult.Failed("Product not found")

        val offerToken = productDetails.subscriptionOfferDetails
            ?.firstOrNull()
            ?.offerToken
            ?: return BillingPurchaseResult.Failed("No offer token available")

        val detailsParams = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(productDetails)
            .setOfferToken(offerToken)
            .build()
        val billingFlowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(detailsParams))
            .build()

        val purchaseDeferred = CompletableDeferred<Pair<BillingResult, List<Purchase>?>>()
        pendingPurchaseUpdate = purchaseDeferred
        val launchResult = client.launchBillingFlow(activity, billingFlowParams)
        if (launchResult.responseCode != BillingClient.BillingResponseCode.OK) {
            pendingPurchaseUpdate = null
            return if (launchResult.responseCode == BillingClient.BillingResponseCode.USER_CANCELED) {
                BillingPurchaseResult.Cancelled
            } else {
                BillingPurchaseResult.Failed(launchResult.debugMessage.ifBlank { "Launch failed" })
            }
        }

        val update = withTimeoutOrNull(PURCHASE_TIMEOUT_MS) { purchaseDeferred.await() }
            ?: return BillingPurchaseResult.Failed("Purchase timed out")
        return when (update.first.responseCode) {
            BillingClient.BillingResponseCode.OK -> {
                val purchases = update.second.orEmpty()
                if (purchases.isEmpty()) {
                    BillingPurchaseResult.Failed("Purchase returned without records")
                } else {
                    val acknowledgeFailure = acknowledgePurchases(client, purchases)
                    if (acknowledgeFailure != null) {
                        BillingPurchaseResult.Failed(acknowledgeFailure)
                    } else {
                        val entitlement = entitlementResolver.resolve(
                            purchases = purchases,
                            productDetailsById = cachedProductDetailsById
                        )
                        BillingPurchaseResult.Success(entitlement)
                    }
                }
            }
            BillingClient.BillingResponseCode.USER_CANCELED -> BillingPurchaseResult.Cancelled
            else -> BillingPurchaseResult.Failed(
                update.first.debugMessage.ifBlank { "Purchase failed" }
            )
        }
    }

    override suspend fun restorePurchases(): BillingRestoreResult {
        if (!connect()) {
            return BillingRestoreResult.Failed("Billing unavailable")
        }
        val client = billingClient ?: return BillingRestoreResult.Failed("Billing unavailable")
        if (cachedProductDetailsById.isEmpty()) {
            queryProducts()
        }
        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.SUBS)
            .build()
        val restoreResult = suspendCancellableCoroutine<Pair<BillingResult, List<Purchase>>> { continuation ->
            client.queryPurchasesAsync(params) { billingResult, purchases ->
                continuation.resume(billingResult to purchases)
            }
        }
        if (restoreResult.first.responseCode != BillingClient.BillingResponseCode.OK) {
            return BillingRestoreResult.Failed(
                restoreResult.first.debugMessage.ifBlank { "Restore failed" }
            )
        }
        val entitlement = entitlementResolver.resolve(
            purchases = restoreResult.second,
            productDetailsById = cachedProductDetailsById
        )
        return BillingRestoreResult.Restored(
            active = entitlement != null,
            entitlement = entitlement
        )
    }

    override fun endConnection() {
        pendingPurchaseUpdate?.cancel()
        pendingPurchaseUpdate = null
        billingClient?.endConnection()
        billingClient = null
        connected = false
    }

    private suspend fun acknowledgePurchases(
        client: BillingClient,
        purchases: List<Purchase>
    ): String? {
        for (purchase in purchases) {
            if (purchase.purchaseState != Purchase.PurchaseState.PURCHASED) continue
            if (purchase.isAcknowledged) continue
            val params = AcknowledgePurchaseParams.newBuilder()
                .setPurchaseToken(purchase.purchaseToken)
                .build()
            val result = suspendCancellableCoroutine<BillingResult> { continuation ->
                client.acknowledgePurchase(params) { billingResult ->
                    continuation.resume(billingResult)
                }
            }
            if (result.responseCode != BillingClient.BillingResponseCode.OK) {
                return result.debugMessage.ifBlank { "Could not acknowledge purchase" }
            }
        }
        return null
    }

    private fun ensureBillingClient(): BillingClient {
        val existing = billingClient
        if (existing != null) return existing
        return BillingClient.newBuilder(appContext)
            .setListener(purchasesUpdatedListener)
            .enablePendingPurchases()
            .build()
            .also { billingClient = it }
    }

    private companion object {
        const val PURCHASE_TIMEOUT_MS = 120_000L
    }
}
