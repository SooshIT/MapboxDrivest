package com.drivest.navigation.subscription

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.drivest.navigation.subscription.billing.BillingRestoreResult
import com.drivest.navigation.subscription.billing.BillingService
import com.drivest.navigation.subscription.billing.FakeBillingClientFacade
import com.drivest.navigation.subscription.billing.ResolvedEntitlement
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class RestoreCoordinatorTest {

    @Test
    fun defaultRestoreReturnsDisabledWhenBillingOff() {
        runBlocking {
            val coordinator = SubscriptionRestoreCoordinator(
                billingService = null,
                billingEnabledProvider = { false }
            )
            val restored = coordinator.restore()
            assertTrue(restored is BillingRestoreResult.Failed)
            assertTrue(
                (restored as BillingRestoreResult.Failed).reason.contains(
                    "disabled",
                    ignoreCase = true
                )
            )
        }
    }

    @Test
    fun enabledRestoreUsesBillingServicePath() {
        runBlocking {
            val nowMs = 9_000_000L
            val harness = createHarness(nowMs = nowMs)
            harness.fakeFacade.restoreResult = BillingRestoreResult.Restored(
                active = true,
                entitlement = ResolvedEntitlement(
                    tier = SubscriptionTier.PRACTICE_MONTHLY,
                    expiryMs = nowMs + 123_456L,
                    provider = StoreProvider.PLAY
                )
            )
            val billingService = BillingService(
                subscriptionRepository = harness.subscriptionRepository,
                billingClientFacade = harness.fakeFacade,
                clock = object : BillingService.Clock {
                    override fun nowMs(): Long = nowMs
                },
                billingEnabledProvider = { true }
            )
            val coordinator = SubscriptionRestoreCoordinator(
                billingService = billingService,
                billingEnabledProvider = { true }
            )

            val restored = coordinator.restore()
            val state = harness.subscriptionRepository.subscriptionState.first()

            assertTrue(restored is BillingRestoreResult.Restored)
            assertEquals(1, harness.fakeFacade.restoreCalls)
            assertEquals(SubscriptionTier.PRACTICE_MONTHLY, state.tier)
            harness.dispose()
        }
    }

    private fun createHarness(nowMs: Long): Harness {
        val tempDir = Files.createTempDirectory("restore_coordinator_test").toFile()
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
}
