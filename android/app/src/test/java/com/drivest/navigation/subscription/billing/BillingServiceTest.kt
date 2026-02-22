package com.drivest.navigation.subscription.billing

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.drivest.navigation.subscription.StoreProvider
import com.drivest.navigation.subscription.SubscriptionRepository
import com.drivest.navigation.subscription.SubscriptionTier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class BillingServiceTest {

    @Test
    fun BillingServicePurchaseSuccessSetsSubscription() {
        runBlocking {
            val nowMs = 5_000_000L
            val harness = createHarness(nowMs = nowMs)
            val entitlement = ResolvedEntitlement(
                tier = SubscriptionTier.PRACTICE_MONTHLY,
                expiryMs = nowMs + 86_400_000L,
                provider = StoreProvider.PLAY
            )
            harness.fakeFacade.purchaseResult = BillingPurchaseResult.Success(entitlement)
            val billingService = BillingService(
                subscriptionRepository = harness.subscriptionRepository,
                billingClientFacade = harness.fakeFacade,
                clock = FixedClock(nowMs),
                billingEnabledProvider = { true }
            )

            val purchaseResult = billingService.purchase(
                activity = null,
                productId = "drivest_practice_monthly"
            )
            val state = harness.subscriptionRepository.subscriptionState.first()

            assertTrue(purchaseResult is BillingPurchaseResult.Success)
            assertEquals(SubscriptionTier.PRACTICE_MONTHLY, state.tier)
            assertEquals(nowMs + 86_400_000L, state.expiryMs)
            assertEquals(nowMs, state.lastVerifiedAtMs)
            assertEquals(StoreProvider.PLAY, state.storeProvider)
            harness.dispose()
        }
    }

    @Test
    fun BillingServiceRestoreSuccessSetsSubscription() {
        runBlocking {
            val nowMs = 5_000_000L
            val harness = createHarness(nowMs = nowMs)
            val entitlement = ResolvedEntitlement(
                tier = SubscriptionTier.GLOBAL_ANNUAL,
                expiryMs = nowMs + 30L * 24L * 60L * 60L * 1000L,
                provider = StoreProvider.PLAY
            )
            harness.fakeFacade.restoreResult = BillingRestoreResult.Restored(
                active = true,
                entitlement = entitlement
            )
            val billingService = BillingService(
                subscriptionRepository = harness.subscriptionRepository,
                billingClientFacade = harness.fakeFacade,
                clock = FixedClock(nowMs),
                billingEnabledProvider = { true }
            )

            val restoreResult = billingService.restore()
            val state = harness.subscriptionRepository.subscriptionState.first()

            assertTrue(restoreResult is BillingRestoreResult.Restored)
            assertEquals(SubscriptionTier.GLOBAL_ANNUAL, state.tier)
            assertEquals(entitlement.expiryMs, state.expiryMs)
            assertEquals(nowMs, state.lastVerifiedAtMs)
            harness.dispose()
        }
    }

    @Test
    fun BillingServiceRestoreNoPurchaseClearsSubscription() {
        runBlocking {
            val nowMs = 5_000_000L
            val harness = createHarness(nowMs = nowMs)
            harness.subscriptionRepository.setActiveSubscription(
                tier = SubscriptionTier.GLOBAL_ANNUAL,
                expiryMs = nowMs + 999_999L,
                provider = StoreProvider.PLAY
            )
            harness.fakeFacade.restoreResult = BillingRestoreResult.Restored(
                active = false,
                entitlement = null
            )
            val billingService = BillingService(
                subscriptionRepository = harness.subscriptionRepository,
                billingClientFacade = harness.fakeFacade,
                clock = FixedClock(nowMs),
                billingEnabledProvider = { true }
            )

            billingService.restore()
            val state = harness.subscriptionRepository.subscriptionState.first()

            assertEquals(SubscriptionTier.FREE, state.tier)
            assertEquals(0L, state.expiryMs)
            assertEquals(StoreProvider.NONE, state.storeProvider)
            harness.dispose()
        }
    }

    @Test
    fun BillingDisabledBlocksPurchaseAndRestore() {
        runBlocking {
            val nowMs = 5_000_000L
            val harness = createHarness(nowMs = nowMs)
            val billingService = BillingService(
                subscriptionRepository = harness.subscriptionRepository,
                billingClientFacade = harness.fakeFacade,
                clock = FixedClock(nowMs),
                billingEnabledProvider = { false }
            )

            val purchaseResult = billingService.purchase(
                activity = null,
                productId = "drivest_global_annual"
            )
            val restoreResult = billingService.restore()
            val state = harness.subscriptionRepository.subscriptionState.first()

            assertTrue(purchaseResult is BillingPurchaseResult.Failed)
            assertTrue(
                (purchaseResult as BillingPurchaseResult.Failed).reason.contains(
                    "disabled",
                    ignoreCase = true
                )
            )
            assertTrue(restoreResult is BillingRestoreResult.Failed)
            assertTrue(
                (restoreResult as BillingRestoreResult.Failed).reason.contains(
                    "disabled",
                    ignoreCase = true
                )
            )
            assertEquals(0, harness.fakeFacade.connectCalls)
            assertFalse(state.isActive(nowMs))
            harness.dispose()
        }
    }

    private fun createHarness(nowMs: Long): Harness {
        val tempDir = Files.createTempDirectory("billing_service_test").toFile()
        val storeFile = File(tempDir, "drivest_subscription.preferences_pb")
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val dataStore = PreferenceDataStoreFactory.create(
            scope = scope,
            produceFile = { storeFile }
        )
        return Harness(
            scope = scope,
            tempDir = tempDir,
            subscriptionRepository = SubscriptionRepository(
                dataStore = dataStore,
                nowProvider = { nowMs }
            ),
            fakeFacade = FakeBillingClientFacade()
        )
    }

    private data class Harness(
        val scope: CoroutineScope,
        val tempDir: File,
        val subscriptionRepository: SubscriptionRepository,
        val fakeFacade: FakeBillingClientFacade
    ) {
        fun dispose() {
            scope.cancel()
            tempDir.deleteRecursively()
        }
    }

    private data class FixedClock(private val nowMs: Long) : BillingService.Clock {
        override fun nowMs(): Long = nowMs
    }
}
