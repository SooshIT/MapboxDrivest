package com.drivest.navigation.subscription

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.subscriptionDataStore by preferencesDataStore(name = "drivest_subscription")

class SubscriptionRepository(
    private val dataStore: DataStore<Preferences>,
    private val nowProvider: () -> Long = { System.currentTimeMillis() }
) {

    constructor(
        context: Context,
        nowProvider: () -> Long = { System.currentTimeMillis() }
    ) : this(
        dataStore = context.subscriptionDataStore,
        nowProvider = nowProvider
    )

    val subscriptionState: Flow<SubscriptionState> = dataStore.data.map { preferences ->
        SubscriptionState(
            tier = SubscriptionTier.fromStorage(preferences[KEY_SUBSCRIPTION_TIER]),
            expiryMs = preferences[KEY_SUBSCRIPTION_EXPIRY_MS] ?: 0L,
            lastVerifiedAtMs = preferences[KEY_SUBSCRIPTION_LAST_VERIFIED_AT_MS] ?: 0L,
            storeProvider = StoreProvider.fromStorage(preferences[KEY_SUBSCRIPTION_STORE_PROVIDER])
        )
    }

    val effectiveTier: Flow<SubscriptionTier> = subscriptionState.map { state ->
        state.effectiveTier(nowProvider())
    }

    val isActive: Flow<Boolean> = subscriptionState.map { state ->
        state.isActive(nowProvider())
    }

    suspend fun setActiveSubscription(
        tier: SubscriptionTier,
        expiryMs: Long,
        provider: StoreProvider,
        verifiedAtMs: Long = nowProvider()
    ) {
        dataStore.edit { preferences ->
            preferences[KEY_SUBSCRIPTION_TIER] = tier.storageValue
            preferences[KEY_SUBSCRIPTION_EXPIRY_MS] = expiryMs
            preferences[KEY_SUBSCRIPTION_LAST_VERIFIED_AT_MS] = verifiedAtMs
            preferences[KEY_SUBSCRIPTION_STORE_PROVIDER] = provider.storageValue
        }
    }

    suspend fun clearSubscription() {
        dataStore.edit { preferences ->
            preferences[KEY_SUBSCRIPTION_TIER] = SubscriptionTier.FREE.storageValue
            preferences[KEY_SUBSCRIPTION_EXPIRY_MS] = 0L
            preferences[KEY_SUBSCRIPTION_LAST_VERIFIED_AT_MS] = 0L
            preferences[KEY_SUBSCRIPTION_STORE_PROVIDER] = StoreProvider.NONE.storageValue
        }
    }

    fun refreshEffectiveTier() {
        // Effective tier is computed lazily from current time and persisted state.
    }

    private companion object {
        val KEY_SUBSCRIPTION_TIER: Preferences.Key<String> =
            stringPreferencesKey("subscription_tier")
        val KEY_SUBSCRIPTION_EXPIRY_MS: Preferences.Key<Long> =
            longPreferencesKey("subscription_expiry_ms")
        val KEY_SUBSCRIPTION_LAST_VERIFIED_AT_MS: Preferences.Key<Long> =
            longPreferencesKey("subscription_last_verified_at_ms")
        val KEY_SUBSCRIPTION_STORE_PROVIDER: Preferences.Key<String> =
            stringPreferencesKey("subscription_store_provider")
    }
}
