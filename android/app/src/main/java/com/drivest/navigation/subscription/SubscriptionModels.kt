package com.drivest.navigation.subscription

enum class SubscriptionTier(val storageValue: String) {
    FREE("free"),
    PRACTICE_MONTHLY("practice_monthly"),
    GLOBAL_ANNUAL("global_annual");

    companion object {
        fun fromStorage(value: String?): SubscriptionTier {
            return entries.firstOrNull { it.storageValue == value } ?: FREE
        }
    }
}

enum class StoreProvider(val storageValue: String) {
    NONE("none"),
    PLAY("play"),
    APPLE("apple");

    companion object {
        fun fromStorage(value: String?): StoreProvider {
            return entries.firstOrNull { it.storageValue == value } ?: NONE
        }
    }
}

data class SubscriptionState(
    val tier: SubscriptionTier = SubscriptionTier.FREE,
    val expiryMs: Long = 0L,
    val lastVerifiedAtMs: Long = 0L,
    val storeProvider: StoreProvider = StoreProvider.NONE
) {
    fun isActive(nowMs: Long): Boolean {
        return tier != SubscriptionTier.FREE && expiryMs > nowMs
    }

    fun effectiveTier(nowMs: Long): SubscriptionTier {
        return if (isActive(nowMs)) tier else SubscriptionTier.FREE
    }
}
