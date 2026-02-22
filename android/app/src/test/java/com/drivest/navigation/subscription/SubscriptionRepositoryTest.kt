package com.drivest.navigation.subscription

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
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

class SubscriptionRepositoryTest {

    @Test
    fun defaultTierIsFree() {
        runBlocking {
            val harness = createHarness(nowMs = 1_000_000L)
            assertEquals(SubscriptionTier.FREE, harness.repository.subscriptionState.first().tier)
            assertEquals(SubscriptionTier.FREE, harness.repository.effectiveTier.first())
            assertFalse(harness.repository.isActive.first())
            harness.dispose()
        }
    }

    @Test
    fun setActiveSubscriptionPersistsTierAndExpiry() {
        runBlocking {
            val nowMs = 1_000_000L
            val harness = createHarness(nowMs = nowMs)
            harness.repository.setActiveSubscription(
                tier = SubscriptionTier.GLOBAL_ANNUAL,
                expiryMs = nowMs + 86_400_000L,
                provider = StoreProvider.PLAY
            )

            val state = harness.repository.subscriptionState.first()
            assertEquals(SubscriptionTier.GLOBAL_ANNUAL, state.tier)
            assertEquals(nowMs + 86_400_000L, state.expiryMs)
            assertEquals(StoreProvider.PLAY, state.storeProvider)
            assertTrue(harness.repository.isActive.first())
            assertEquals(SubscriptionTier.GLOBAL_ANNUAL, harness.repository.effectiveTier.first())
            harness.dispose()
        }
    }

    @Test
    fun pastExpiryMakesEffectiveTierFree() {
        runBlocking {
            val nowMs = 1_000_000L
            val harness = createHarness(nowMs = nowMs)
            harness.repository.setActiveSubscription(
                tier = SubscriptionTier.PRACTICE_MONTHLY,
                expiryMs = nowMs - 1L,
                provider = StoreProvider.PLAY
            )

            val state = harness.repository.subscriptionState.first()
            assertEquals(SubscriptionTier.PRACTICE_MONTHLY, state.tier)
            assertEquals(SubscriptionTier.FREE, harness.repository.effectiveTier.first())
            assertFalse(harness.repository.isActive.first())
            harness.dispose()
        }
    }

    private fun createHarness(nowMs: Long): Harness {
        val tempDir = Files.createTempDirectory("subscription_repo_test").toFile()
        val storeFile = File(tempDir, "drivest_subscription.preferences_pb")
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val dataStore = PreferenceDataStoreFactory.create(
            scope = scope,
            produceFile = { storeFile }
        )
        val repository = SubscriptionRepository(
            dataStore = dataStore,
            nowProvider = { nowMs }
        )
        return Harness(scope, tempDir, repository)
    }

    private data class Harness(
        val scope: CoroutineScope,
        val tempDir: File,
        val repository: SubscriptionRepository
    ) {
        fun dispose() {
            scope.cancel()
            tempDir.deleteRecursively()
        }
    }
}
